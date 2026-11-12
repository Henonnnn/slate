package dev.slate.launcher.widgets

import kotlinx.coroutines.flow.first
import dev.slate.launcher.container
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * The app's only network access: Open-Meteo's forecast API (and its geocoding API when the user
 * searches for a city). Requests carry the coordinates or the typed city name and nothing else:
 * no key, no identifiers. Nothing is fetched unless a weather widget is on the home screen.
 *
 * The last result is cached in `files/weather.json` so the widget shows something immediately
 * and offline; it's refreshed when older than [STALE_AFTER_MS].
 */
class WeatherRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile by lazy { File(context.filesDir, "weather.json") }
    private val mutex = Mutex()

    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot.asStateFlow()

    sealed interface Refresh {
        data object Fresh : Refresh
        data object Updated : Refresh
        data object Failed : Refresh
    }

    /** Loads the cache, then fetches if it's missing, stale, or for a different place. */
    suspend fun refresh(latitude: Double, longitude: Double, placeName: String, force: Boolean = false): Refresh = mutex.withLock {
        if (_snapshot.value == null) _snapshot.value = readCache()
        val current = _snapshot.value
        val samePlace = current != null && current.latitude == latitude && current.longitude == longitude
        val fahrenheit = context.container.settingsRepository.settings.first().clock.fahrenheit
        val fresh = samePlace && current.fahrenheit == fahrenheit &&
            System.currentTimeMillis() - current.fetchedAtMillis < STALE_AFTER_MS
        if (fresh && !force) return Refresh.Fresh
        if (!samePlace) _snapshot.value = null
        val fetched = fetch(latitude, longitude, placeName, fahrenheit) ?: return Refresh.Failed
        _snapshot.value = fetched
        writeCache(fetched)
        Refresh.Updated
    }

    private suspend fun fetch(latitude: Double, longitude: Double, placeName: String, fahrenheit: Boolean): WeatherSnapshot? {
        val url = FORECAST_URL.toUri().buildUpon()
            .appendQueryParameter("latitude", "%.4f".format(Locale.ROOT, latitude))
            .appendQueryParameter("longitude", "%.4f".format(Locale.ROOT, longitude))
            .appendQueryParameter("current", "temperature_2m,weather_code,is_day")
            .appendQueryParameter("daily", "temperature_2m_max,temperature_2m_min")
            .appendQueryParameter("timezone", "auto")
            .appendQueryParameter("forecast_days", "1")
            .apply { if (fahrenheit) appendQueryParameter("temperature_unit", "fahrenheit") }
            .build().toString()
        val body = get(url) ?: return null
        return runCatching {
            val r = json.decodeFromString(ForecastResponse.serializer(), body)
            WeatherSnapshot(
                placeName = placeName,
                latitude = latitude,
                longitude = longitude,
                temperature = r.current.temperature,
                high = r.daily.max.first(),
                low = r.daily.min.first(),
                code = r.current.code,
                isDay = r.current.isDay == 1,
                fahrenheit = fahrenheit,
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }.onFailure { Log.w(TAG, "Unexpected forecast response", it) }.getOrNull()
    }

    data class PlaceResult(val name: String, val description: String, val latitude: Double, val longitude: Double)

    /** City search, only when the user types a name. Null means the request failed. */
    suspend fun searchPlaces(query: String): List<PlaceResult>? {
        if (query.isBlank()) return emptyList()
        val url = GEOCODING_URL.toUri().buildUpon()
            .appendQueryParameter("name", query.trim())
            .appendQueryParameter("count", "6")
            .appendQueryParameter("language", context.resources.configuration.locales[0].language)
            .build().toString()
        val body = get(url) ?: return null
        return runCatching {
            json.decodeFromString(GeocodingResponse.serializer(), body).results.map {
                PlaceResult(it.name, it.description, it.latitude, it.longitude)
            }
        }.getOrNull()
    }

    /**
     * INTERNET is install-time on stock Android, but GrapheneOS turns it into a per-app "Network"
     * toggle that can be off, in which case it reports as denied here.
     */
    fun hasNetworkPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Coarse device location without any network lookup of our own: the newest last-known fix,
     * else one fresh fix (up to 15 s). Null if location is off or unavailable.
     */
    suspend fun deviceLocation(): Location? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java)
        return try {
            val providers = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
                add(LocationManager.GPS_PROVIDER)
                add(LocationManager.PASSIVE_PROVIDER)
            }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            val last = withContext(Dispatchers.IO) {
                providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            }
            if (last != null && System.currentTimeMillis() - last.time < LOCATION_MAX_AGE_MS) return last
            val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return last
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, locationExecutor) { cont.resume(it) }
                }
            } ?: last
        } catch (e: SecurityException) {
            null
        }
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Open-Meteo HTTP ${connection.responseCode}")
                return@withContext null
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.w(TAG, "Open-Meteo unreachable: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun readCache(): WeatherSnapshot? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(WeatherSnapshot.serializer(), cacheFile.readText()) }.getOrNull()
    }

    private suspend fun writeCache(snapshot: WeatherSnapshot) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(cacheFile.parentFile, "weather.json.tmp")
            tmp.writeText(json.encodeToString(WeatherSnapshot.serializer(), snapshot))
            tmp.renameTo(cacheFile)
        }
    }

    private companion object {
        const val TAG = "WeatherRepository"
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
        const val STALE_AFTER_MS = 30 * 60 * 1000L
        const val LOCATION_MAX_AGE_MS = 6 * 60 * 60 * 1000L
        val locationExecutor = Executors.newSingleThreadExecutor()
    }
}

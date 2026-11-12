package dev.slate.launcher.widgets

import androidx.annotation.StringRes
import dev.slate.launcher.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/** What the weather widget shows; also the on-disk cache. */
@Serializable
data class WeatherSnapshot(
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val temperature: Double,
    val high: Double,
    val low: Double,
    val code: Int,
    val isDay: Boolean,
    val fahrenheit: Boolean,
    val fetchedAtMillis: Long,
)

/** The subset of Open-Meteo's forecast response we ask for. */
@Serializable
internal data class ForecastResponse(val current: Current, val daily: Daily) {
    @Serializable
    data class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val code: Int,
        @SerialName("is_day") val isDay: Int = 1,
    )

    @Serializable
    data class Daily(
        @SerialName("temperature_2m_max") val max: List<Double>,
        @SerialName("temperature_2m_min") val min: List<Double>,
    )
}

@Serializable
internal data class GeocodingResponse(val results: List<Place> = emptyList()) {
    @Serializable
    data class Place(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val country: String? = null,
        val admin1: String? = null,
    ) {
        val description: String get() = listOfNotNull(admin1?.takeIf { it != name }, country).joinToString(", ")
    }
}

object WeatherCodes {
    /** WMO weather interpretation codes, as used by Open-Meteo. */
    @StringRes
    fun describe(code: Int): Int = when (code) {
        0 -> R.string.weather_clear
        1 -> R.string.weather_mostly_clear
        2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_overcast
        45, 48 -> R.string.weather_fog
        51, 53, 55, 56, 57 -> R.string.weather_drizzle
        61, 63, 66 -> R.string.weather_rain
        65, 67 -> R.string.weather_heavy_rain
        71, 73, 77 -> R.string.weather_snow
        75 -> R.string.weather_heavy_snow
        80, 81, 82 -> R.string.weather_showers
        85, 86 -> R.string.weather_snow_showers
        95, 96, 99 -> R.string.weather_thunderstorm
        else -> R.string.weather_unknown
    }

    /** Countries that use Fahrenheit day to day. */
    enum class Sky { Clear, Cloudy, Rain, Snow }

    /** The WMO code reduced to what a small glyph can show. */
    fun sky(code: Int): Sky = when (code) {
        0, 1 -> Sky.Clear
        71, 73, 75, 77, 85, 86 -> Sky.Snow
        2, 3, 45, 48 -> Sky.Cloudy
        else -> Sky.Rain
    }

    fun usesFahrenheit(locale: Locale): Boolean = locale.country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
}

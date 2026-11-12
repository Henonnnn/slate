package dev.slate.launcher.ui.widgets

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.slate.launcher.R
import dev.slate.launcher.container
import dev.slate.launcher.data.WeatherPlace
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import dev.slate.launcher.widgets.CalendarEvent
import dev.slate.launcher.widgets.CalendarFormat
import dev.slate.launcher.widgets.WeatherCodes
import dev.slate.launcher.widgets.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Slate's own widgets are typographic, drawn straight on the wallpaper like the clock.
 * A long press anywhere opens the widget menu.
 */
@Composable
private fun BuiltinFrame(onClick: () -> Unit, onLongPress: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) { content() }
}

@Composable
fun CalendarWidget(onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val source = context.container.calendarSource
    val palette = LocalSlatePalette.current
    var permissionVersion by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        permissionVersion++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionVersion++ }
    val granted = remember(permissionVersion) { source.hasPermission() }

    val events by produceState<List<CalendarEvent>?>(null, granted) {
        if (!granted) return@produceState
        // Reload on any calendar change, and every minute so finished events drop off.
        val ticks = flow { while (true) { delay(60_000); emit(Unit) } }
        merge(source.changes(), ticks).onStart { emit(Unit) }.collect { value = source.upcoming() }
    }

    BuiltinFrame(onClick = { openCalendar(context) }, onLongPress = onLongPress, modifier = modifier) {
        Column {
            Text(stringResource(R.string.widget_calendar).uppercase(), style = SlateText.sectionLabel, color = palette.onWallpaperMuted)
            Spacer(Modifier.size(8.dp))
            if (!granted) {
                Text(stringResource(R.string.calendar_permission_body), style = SlateText.hint, color = palette.onWallpaper)
                TextButton(
                    onClick = {
                        // After a permanent denial the dialog no longer appears; send people to app settings.
                        if (denied) openAppSettings(context) else permission.launch(Manifest.permission.READ_CALENDAR)
                    },
                ) { Text(stringResource(if (denied) R.string.open_settings else R.string.allow), color = palette.accent) }
                return@Column
            }
            val list = events ?: return@Column
            if (list.isEmpty()) {
                Text(stringResource(R.string.calendar_empty), style = SlateText.hint, color = palette.onWallpaperMuted)
                return@Column
            }
            val is24h = DateFormat.is24HourFormat(context)
            val locale = LocalConfiguration.current.locales[0]
            val time = remember(locale, is24h) {
                DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24h) "Hm" else "hm"), locale)
            }
            val labels = CalendarFormat.Labels(
                now = stringResource(R.string.calendar_now),
                inMinutes = stringResource(R.string.calendar_in_minutes),
                today = stringResource(R.string.calendar_today),
                tomorrow = stringResource(R.string.calendar_tomorrow),
                allDay = stringResource(R.string.calendar_all_day),
            )
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEach { event ->
                    EventRow(event, CalendarFormat.describe(event, now, zone, time, labels)) {
                        runCatching { context.startActivity(event.viewIntent()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, whenText: String, onClick: () -> Unit) {
    val palette = LocalSlatePalette.current
    Row(
        Modifier.fillMaxWidth().combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 8.dp).size(7.dp).background(Color(event.color or 0xFF000000.toInt()), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                event.title.ifEmpty { stringResource(R.string.calendar_untitled) },
                style = SlateText.appLabel.copy(fontSize = 17.sp),
                color = palette.onWallpaper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(whenText, style = SlateText.hint.copy(fontSize = 13.sp), color = palette.onWallpaperMuted, maxLines = 1)
        }
    }
}

@Composable
fun WeatherWidget(place: WeatherPlace?, onChoosePlace: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val repository: WeatherRepository = LocalContext.current.container.weatherRepository
    val palette = LocalSlatePalette.current
    val scope = rememberCoroutineScope()
    val snapshot by repository.snapshot.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf(false) }

    fun refresh(force: Boolean) {
        val p = place ?: return
        scope.launch { failed = repository.refresh(p.latitude, p.longitude, p.name, force) == WeatherRepository.Refresh.Failed }
    }
    // Refresh when the place changes and whenever home comes back into view (only if stale).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh(force = false) }
    androidx.compose.runtime.LaunchedEffect(place) { refresh(force = false) }

    BuiltinFrame(onClick = { if (place == null) onChoosePlace() else refresh(force = true) }, onLongPress = onLongPress, modifier = modifier) {
        Column {
            if (place == null) {
                Text(stringResource(R.string.widget_weather).uppercase(), style = SlateText.sectionLabel, color = palette.onWallpaperMuted)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.weather_setup_body), style = SlateText.hint, color = palette.onWallpaper)
                TextButton(onClick = onChoosePlace) { Text(stringResource(R.string.weather_choose_place), color = palette.accent) }
                return@Column
            }
            val s = snapshot?.takeIf { it.latitude == place.latitude && it.longitude == place.longitude }
            Text(place.name.uppercase(), style = SlateText.sectionLabel, color = palette.onWallpaperMuted, maxLines = 1)
            if (s == null && !repository.hasNetworkPermission()) {
                NetworkOffNote()
                return@Column
            }
            if (s == null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(if (failed) R.string.weather_unreachable else R.string.weather_loading),
                    style = SlateText.hint,
                    color = palette.onWallpaperMuted,
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.temperature.roundToInt()}°", style = SlateText.clock.copy(fontSize = 52.sp), color = palette.onWallpaper)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.padding(bottom = 10.dp)) {
                    Text(stringResource(WeatherCodes.describe(s.code)), style = SlateText.appLabel.copy(fontSize = 17.sp), color = palette.onWallpaper)
                    Text(
                        stringResource(R.string.weather_high_low, s.high.roundToInt(), s.low.roundToInt()) +
                            if (failed) " · " + stringResource(R.string.weather_offline) else "",
                        style = SlateText.hint.copy(fontSize = 13.sp),
                        color = palette.onWallpaperMuted,
                    )
                }
            }
        }
    }
}

/** Shown where weather needs the network but the OS has it switched off for Slate. */
@Composable
fun NetworkOffNote() {
    val context = LocalContext.current
    val palette = LocalSlatePalette.current
    Spacer(Modifier.size(8.dp))
    Text(stringResource(R.string.weather_network_off), style = SlateText.hint, color = palette.onWallpaper)
    TextButton(onClick = { openAppSettings(context) }) { Text(stringResource(R.string.open_settings), color = palette.accent) }
}

private fun openCalendar(context: Context) {
    val uri = android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

internal fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

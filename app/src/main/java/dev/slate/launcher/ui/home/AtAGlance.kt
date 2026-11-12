package dev.slate.launcher.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.MutableTransitionState
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.slate.launcher.R
import dev.slate.launcher.container
import dev.slate.launcher.data.ClockSettings
import dev.slate.launcher.data.WeatherPlace
import dev.slate.launcher.floating.nextAlarm
import dev.slate.launcher.ui.components.SlateIcon
import dev.slate.launcher.ui.components.SlateIconView
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.widgets.CalendarEvent
import dev.slate.launcher.widgets.CalendarFormat
import dev.slate.launcher.widgets.WeatherCodes
import dev.slate.launcher.widgets.WeatherSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private class BatteryLevel(val percent: Int, val charging: Boolean)

/** The sticky battery broadcast: no permission, and it repeats on every change. */
private fun batteryLevel(context: Context): Flow<BatteryLevel> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            trySend(BatteryLevel(level * 100 / scale, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL))
        }
    }
    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    awaitClose { context.unregisterReceiver(receiver) }
}

/** The small details beside the date. Each is null when it's off or has nothing to show. */
@Composable
fun GlanceDetails(clock: ClockSettings, place: WeatherPlace?, textStyle: TextStyle, interactive: Boolean) {
    val context = LocalContext.current
    val palette = LocalSlatePalette.current
    val repository = context.container.weatherRepository
    val snapshot by repository.snapshot.collectAsState()
    val weather: WeatherSnapshot? = snapshot?.takeIf { clock.showWeather && place != null && it.latitude == place.latitude && it.longitude == place.longitude }
    if (clock.showWeather && place != null) {
        var tick by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
        LaunchedEffect(place, tick) { repository.refresh(place.latitude, place.longitude, place.name) }
    }
    val battery by produceState<BatteryLevel?>(null, clock.showBattery) {
        if (clock.showBattery) batteryLevel(context).collect { value = it } else value = null
    }
    if (weather != null) {
        Spacer(Modifier.width(14.dp))
        SlateIconView(weatherIcon(weather), palette.onWallpaperMuted, Modifier.padding(end = 6.dp), size = 18.dp)
        val unit = if (weather.fahrenheit) "°F" else "°"
        Text("${weather.temperature.roundToInt()}$unit", style = textStyle, color = palette.onWallpaperMuted, maxLines = 1)
    }
    battery?.let { level ->
        Spacer(Modifier.width(14.dp))
        SlateIconView(if (level.charging) SlateIcon.Charging else SlateIcon.Battery, palette.onWallpaperMuted, Modifier.padding(end = 6.dp), size = 18.dp)
        val tap = if (interactive) {
            Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        } else {
            Modifier
        }
        Text("${level.percent}%", style = textStyle, color = palette.onWallpaperMuted, maxLines = 1, modifier = tap)
    }
}

private fun weatherIcon(w: WeatherSnapshot): SlateIcon = when (WeatherCodes.sky(w.code)) {
    WeatherCodes.Sky.Clear -> if (w.isDay) SlateIcon.Sun else SlateIcon.Cloud
    WeatherCodes.Sky.Cloudy -> SlateIcon.Cloud
    WeatherCodes.Sky.Rain -> SlateIcon.Rain
    WeatherCodes.Sky.Snow -> SlateIcon.Snow
}

/** What's coming up, under the date: the next event and the next alarm. */
@Composable
fun GlanceAgenda(clock: ClockSettings, textStyle: TextStyle, interactive: Boolean) {
    val context = LocalContext.current
    val source = context.container.calendarSource
    val locale = LocalConfiguration.current.locales[0]
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val canRead = remember(resumes, clock.showEvents) { clock.showEvents && source.hasPermission() }
    val events by produceState(emptyList<CalendarEvent>(), canRead) {
        if (!canRead) {
            value = emptyList()
            return@produceState
        }
        // Reload on any calendar change, and every minute so "in 5 min" stays true.
        val ticks = flow { while (true) { delay(60_000); emit(Unit) } }
        merge(source.changes(), ticks).onStart { emit(Unit) }.collect { value = source.upcoming() }
    }
    val alarm by produceState<Instant?>(null, clock.showAlarm) {
        if (clock.showAlarm) nextAlarm(context).collect { value = it } else value = null
    }
    val is24h = DateFormat.is24HourFormat(context)
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
    val event = remember(events, resumes) { CalendarFormat.nextForGlance(events, now, zone) }
    val soonAlarm = alarm?.takeIf { it.isBefore(now.plus(ALARM_HORIZON_HOURS, ChronoUnit.HOURS)) }
    // The event line opens the week ahead, as Niagara's does; each event there opens in the calendar.
    val agenda = remember { MutableTransitionState(false) }
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        event?.let { e ->
            Box {
                AgendaLine(SlateIcon.Calendar, e.title, CalendarFormat.describe(e, now, zone, time, labels), textStyle, interactive) {
                    agenda.targetState = true
                }
                AgendaPopup(agenda)
            }
        }
        soonAlarm?.let { at ->
            AgendaLine(SlateIcon.Bell, time.format(at.atZone(zone)), null, textStyle, interactive) {
                runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}

private const val ALARM_HORIZON_HOURS = 12L

@Composable
private fun AgendaLine(icon: SlateIcon, title: String, detail: String?, textStyle: TextStyle, interactive: Boolean, onClick: () -> Unit) {
    val palette = LocalSlatePalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (interactive) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SlateIconView(icon, palette.accent, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(title, style = textStyle, color = palette.onWallpaper, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (detail != null) {
            Text("  ·  $detail", style = textStyle, color = palette.onWallpaperMuted, maxLines = 1)
        }
    }
}

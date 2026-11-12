package dev.slate.launcher.ui.home

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.slate.launcher.R
import dev.slate.launcher.container
import dev.slate.launcher.ui.components.SlateIcon
import dev.slate.launcher.ui.components.SlateIconView
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import dev.slate.launcher.widgets.CalendarEvent
import dev.slate.launcher.widgets.CalendarFormat
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val AGENDA_DAYS = 7L
private const val AGENDA_LIMIT = 40
private val AgendaCorner = 20.dp
private val AgendaMaxWidth = 478.dp
private val AgendaMaxHeight = 460.dp
/** Room around the card inside its window, for the shadow. */
private val AgendaShadowRoom = 16.dp
/** The card starts this far left of the line it opens from, so its text lines up under it. */
private val AgendaInset = 16.dp
/** Kept clear on each side of the screen, as for pop-ups. */
private val AgendaScreenMargin = 40.dp
private val Grow = TransformOrigin(0.08f, 0f)

/**
 * The week ahead, opened from the event line under the clock: a card like a pop-up that grows
 * out of that line, with the events day by day. A row opens its event; the header opens the
 * calendar app at today; the last row starts a new event. [state] is shown while its target is
 * true and stays composed until it has shrunk away.
 */
@Composable
fun AgendaPopup(state: MutableTransitionState<Boolean>) {
    if (!state.currentState && !state.targetState) return
    val density = LocalDensity.current
    val context = LocalContext.current
    val source = context.container.calendarSource
    val wallpaperPalette = LocalSlatePalette.current
    val palette = remember(wallpaperPalette) { wallpaperPalette.onPanel() }
    val zone = ZoneId.systemDefault()
    val events by produceState<List<CalendarEvent>?>(null) {
        source.changes().onStart { emit(Unit) }.collect { value = source.upcoming(limit = AGENDA_LIMIT, days = AGENDA_DAYS) }
    }
    val room = with(density) { AgendaShadowRoom.roundToPx() }
    val inset = with(density) { AgendaInset.roundToPx() }
    val width = (with(density) { LocalWindowInfo.current.containerSize.width.toDp() } - AgendaScreenMargin * 2).coerceAtMost(AgendaMaxWidth)

    fun start(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        state.targetState = false
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(-room - inset, -room),
        onDismissRequest = { state.targetState = false },
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = state,
            enter = scaleIn(spring(dampingRatio = 0.78f, stiffness = 380f), initialScale = 0.6f, transformOrigin = Grow) + fadeIn(tween(140)),
            exit = scaleOut(tween(180), targetScale = 0.7f, transformOrigin = Grow) + fadeOut(tween(160)),
        ) {
            CompositionLocalProvider(LocalSlatePalette provides palette) {
                Column(
                    Modifier
                        .padding(AgendaShadowRoom)
                        .width(width)
                        .graphicsLayer {
                            shadowElevation = 7.dp.toPx()
                            shape = RoundedCornerShape(AgendaCorner)
                            clip = true
                        }
                        .background(palette.panel)
                        .heightIn(max = AgendaMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    val today = remember { LocalDate.now(zone) }
                    AgendaHeader {
                        // The calendar app's own day view, opened at now.
                        val time = ContentUris.appendId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time"), System.currentTimeMillis()).build()
                        start(Intent(Intent.ACTION_VIEW, time))
                    }
                    val days = remember(events) { CalendarFormat.byDay(events.orEmpty(), today, zone) }
                    if (events != null && days.isEmpty()) {
                        Text(
                            stringResource(R.string.agenda_empty),
                            style = SlateText.hint.copy(fontSize = 14.sp, shadow = null),
                            color = palette.onWallpaperMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    days.forEach { (day, list) ->
                        DayLabel(day, today)
                        list.forEach { event -> EventRow(event, zone) { start(event.viewIntent()) } }
                    }
                    Text(
                        stringResource(R.string.agenda_new_event),
                        style = SlateText.appLabel.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = null),
                        color = palette.accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { start(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaHeader(onOpen: () -> Unit) {
    val palette = LocalSlatePalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SlateIconView(SlateIcon.OpenExternal, palette.onWallpaper, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.agenda_title),
            style = SlateText.appLabel.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, shadow = null),
            color = palette.onWallpaper,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun DayLabel(day: LocalDate, today: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMM"), locale) }
    val text = when (day) {
        today -> stringResource(R.string.calendar_today)
        today.plusDays(1) -> stringResource(R.string.calendar_tomorrow)
        else -> format.format(day)
    }
    Text(
        text.uppercase(locale),
        style = SlateText.sectionLabel.copy(shadow = null),
        color = LocalSlatePalette.current.onWallpaperMuted,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun EventRow(event: CalendarEvent, zone: ZoneId, onClick: () -> Unit) {
    val palette = LocalSlatePalette.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24h = DateFormat.is24HourFormat(context)
    val time = remember(locale, is24h) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24h) "Hm" else "hm"), locale) }
    val now = stringResource(R.string.calendar_now)
    val span = if (event.allDay) {
        stringResource(R.string.calendar_all_day)
    } else {
        val range = "${time.format(event.begin.atZone(zone))} – ${time.format(event.end.atZone(zone))}"
        if (!event.begin.isAfter(Instant.now())) "$now · $range" else range
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The calendar's own colour, as a bar down the side.
        Box(Modifier.size(width = 4.dp, height = 34.dp).background(Color(event.color or 0xFF000000.toInt()), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                event.title.ifEmpty { stringResource(R.string.calendar_untitled) },
                style = SlateText.appLabel.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, shadow = null),
                color = palette.onWallpaper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(span, style = SlateText.hint.copy(fontSize = 14.sp, shadow = null), color = palette.onWallpaperMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

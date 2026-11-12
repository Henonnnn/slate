package dev.slate.launcher.ui.home

import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.slate.launcher.R
import dev.slate.launcher.data.ClockAlign
import dev.slate.launcher.data.ClockSettings
import dev.slate.launcher.data.ClockStyle
import dev.slate.launcher.data.WeatherPlace
import dev.slate.launcher.ui.components.SearchGlyph
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

/** The time, ticking on the minute. Null at first: see [HomeHeader]. */
@Composable
fun rememberClockTime(): ZonedDateTime? {
    // Reads the clock off the main thread: the first read may still be loading the tz database
    // (see SlateApp). The empty Text this leaves keeps the header's height.
    val now by produceState<ZonedDateTime?>(null) {
        while (true) {
            val current = withContext(Dispatchers.Default) { ZonedDateTime.now() }
            value = current
            delay(60_000L - (current.second * 1000L + current.nano / 1_000_000L))
        }
    }
    return now
}

/**
 * The clock, then an at-a-glance block: the date with the weather and battery beside it, and the
 * next event and alarm under it. [offsetPx] is the empty space above, read during layout so
 * dragging the home screen up or down doesn't recompose the header. With [interactive] off it's
 * a static preview: nothing inside opens anything.
 */
@Composable
fun HomeHeader(
    clock: ClockSettings,
    place: WeatherPlace?,
    offsetPx: () -> Int,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val palette = LocalSlatePalette.current
    val locale = LocalConfiguration.current.locales[0]
    val haptics = LocalHapticFeedback.current
    val time = rememberClockTime()
    val is24h = DateFormat.is24HourFormat(context)
    val formats = remember(locale, is24h, clock.date, clock.style) { ClockFormats(locale, is24h, clock.date, clock.style) }

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    val onLongClick = onLongPress?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } }
    val longPressLabel = onLongPress?.let { stringResource(R.string.home_menu) }
    val clockTap = if (interactive) {
        Modifier.combinedClickable(
            onClickLabel = stringResource(R.string.open_alarms),
            onLongClickLabel = longPressLabel,
            onLongClick = onLongClick,
        ) { open(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
    } else {
        Modifier
    }
    // Keeps end-aligned text out from under the search button.
    val endPadding = if (onSearch != null && clock.align == ClockAlign.End) 60.dp else 28.dp
    val isGlance = clock.style == ClockStyle.Glance
    val dateStyle = SlateText.date.copy(
        fontFamily = clock.fontFamily(),
        fontWeight = if (isGlance) clock.weight.fontWeight() else FontWeight.Medium,
        fontSize = if (isGlance) 26.sp * clock.size else 16.sp,
    )

    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val offset = offsetPx()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height + offset) { placeable.place(0, offset) }
            },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = endPadding, top = 48.dp, bottom = 28.dp),
            horizontalAlignment = clock.align.horizontal(),
        ) {
            ClockFace(clock, time, formats, clockTap)
            val dateFormat = formats.date.takeIf { formats.showsDateLine }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dateFormat != null) {
                    Text(
                        text = time?.let(dateFormat::format).orEmpty(),
                        style = dateStyle,
                        color = if (isGlance) palette.onWallpaper else palette.onWallpaperMuted,
                        maxLines = 1,
                        // In the glance style the date is also what gets long-pressed.
                        modifier = when {
                            isGlance -> clockTap
                            interactive -> Modifier.clickable(onClickLabel = stringResource(R.string.open_calendar)) {
                                open(Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()))
                            }
                            else -> Modifier
                        },
                    )
                }
                GlanceDetails(clock, place, dateStyle.copy(fontSize = 16.sp), interactive)
            }
            GlanceAgenda(clock, dateStyle.copy(fontSize = 15.sp), interactive)
            if (formats.date == null && clock.style == ClockStyle.None) {
                // Nothing left to press, but the home menu must stay reachable.
                Spacer(Modifier.fillMaxWidth().height(48.dp).then(clockTap))
            }
        }
        // Visible way into search for people who don't discover the pull-down gesture.
        if (onSearch != null) {
            val label = stringResource(R.string.search)
            IconButton(
                onClick = onSearch,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 12.dp)
                    .semantics { contentDescription = label },
            ) { SearchGlyph(palette.onWallpaperMuted) }
        }
    }
}

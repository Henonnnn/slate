package dev.slate.launcher.ui.appearance

import dev.slate.launcher.ui.components.backProgress
import dev.slate.launcher.ui.components.rememberBackProgress
import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.slate.launcher.R
import dev.slate.launcher.container
import dev.slate.launcher.data.ClockAlign
import dev.slate.launcher.data.ClockColour
import dev.slate.launcher.data.ClockDate
import dev.slate.launcher.data.ClockSettings
import dev.slate.launcher.data.ClockStyle
import dev.slate.launcher.data.ClockWeight
import dev.slate.launcher.data.FontChoice
import dev.slate.launcher.data.ThemeSettings
import dev.slate.launcher.data.WeatherPlace
import dev.slate.launcher.ui.components.SlateIcon
import dev.slate.launcher.ui.home.ClockFace
import dev.slate.launcher.ui.home.ClockFormats
import dev.slate.launcher.ui.home.HomeHeader
import dev.slate.launcher.ui.home.fontWeight
import dev.slate.launcher.ui.home.rememberClockTime
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import dev.slate.launcher.ui.theme.SlateTheme
import dev.slate.launcher.ui.theme.fontFamilyFor
import dev.slate.launcher.ui.theme.wallpaperPrimaryColor
import dev.slate.launcher.ui.widgets.openAppSettings

/** The home clock, with the real header as a live preview fixed above the options. */
@Composable
fun ClockScreen(
    clock: ClockSettings,
    theme: ThemeSettings,
    place: WeatherPlace?,
    onChoosePlace: () -> Unit,
    onChange: (ClockSettings) -> Unit,
    onMoveHome: () -> Unit,
    onClose: () -> Unit,
) {
    val backModifier = Modifier.backProgress(rememberBackProgress(onBack = onClose))
    val context = LocalContext.current
    var draft by remember { mutableStateOf(clock) }
    LaunchedEffect(clock) { draft = clock }
    val wallpaperColor by remember { wallpaperPrimaryColor(context) }.collectAsStateWithLifecycle(null)
    var calendarVersion by remember { mutableIntStateOf(0) }
    var calendarDenied by remember { mutableStateOf(false) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // After a permanent denial the dialog no longer appears, so the next tap goes to app settings.
        calendarDenied = !granted
        calendarVersion++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { calendarVersion++ }
    val calendarGranted = remember(calendarVersion) { context.container.calendarSource.hasPermission() }
    fun commit(next: ClockSettings) {
        draft = next
        onChange(next)
    }

    SlateTheme(theme, wallpaperColor) {
        PanelBackdrop(modifier = backModifier) {
            ScreenTitle(stringResource(R.string.clock_title), onClose)
            HomeHeader(draft, place, offsetPx = { 0 }, interactive = false)
            LazyColumn(Modifier.weight(1f)) {
                section("style", R.string.clock_style) {
                    StyleGallery(draft) { commit(draft.copy(style = it)) }
                }
                section("size", R.string.clock_size) {
                    AppearanceSlider(
                        draft.size,
                        ClockSettings.MIN_SIZE..ClockSettings.MAX_SIZE,
                        step = 0.05f,
                        format = { "${Math.round(it * 100)}%" },
                        onPreview = { draft = draft.copy(size = it) },
                    ) { commit(draft.copy(size = it)) }
                }
                section("align", R.string.clock_alignment) {
                    ChipRow {
                        ClockAlign.entries.forEach { align ->
                            Chip(stringResource(align.label()), selected = draft.align == align) { commit(draft.copy(align = align)) }
                        }
                    }
                }
                section("weight", R.string.clock_weight) {
                    ChipRow {
                        ClockWeight.entries.forEach { weight ->
                            Chip(
                                stringResource(weight.label()),
                                selected = draft.weight == weight,
                                style = SlateText.appLabel.copy(fontSize = 17.sp, fontWeight = weight.fontWeight(), shadow = null),
                            ) { commit(draft.copy(weight = weight)) }
                        }
                    }
                }
                section("font", R.string.clock_font) {
                    ChipRow {
                        FontChoice.entries.forEach { font ->
                            Chip(
                                fontLabel(font),
                                selected = (draft.font ?: FontChoice.Google) == font,
                                style = SlateText.appLabel.copy(fontFamily = fontFamilyFor(font), fontSize = 17.sp, shadow = null),
                            ) { commit(draft.copy(font = font)) }
                        }
                    }
                }
                section("glance", R.string.clock_glance) {
                    ToggleRow(stringResource(R.string.clock_glance_weather), draft.showWeather, stringResource(R.string.clock_glance_weather_summary), SlateIcon.Sun) { commit(draft.copy(showWeather = it)) }
                    if (draft.showWeather) {
                        val weather = context.container.weatherRepository
                        if (place == null) {
                            LinkRow(stringResource(R.string.weather_choose_place), icon = SlateIcon.Pin, onClick = onChoosePlace)
                        } else {
                            LinkRow(stringResource(R.string.clock_glance_place), trailing = place.name, icon = SlateIcon.Pin, onClick = onChoosePlace)
                            if (!weather.hasNetworkPermission()) Note(stringResource(R.string.clock_glance_no_network))
                        }
                    }
                    ToggleRow(stringResource(R.string.clock_glance_events), draft.showEvents, stringResource(R.string.clock_glance_events_summary), SlateIcon.Calendar) { commit(draft.copy(showEvents = it)) }
                    if (draft.showEvents && !calendarGranted) {
                        LinkRow(stringResource(R.string.clock_glance_allow_calendar), icon = SlateIcon.Lock) {
                            if (calendarDenied) openAppSettings(context) else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                        }
                    }
                    ToggleRow(stringResource(R.string.clock_glance_alarm), draft.showAlarm, stringResource(R.string.clock_glance_alarm_summary), SlateIcon.Bell) { commit(draft.copy(showAlarm = it)) }
                    ToggleRow(stringResource(R.string.clock_glance_battery), draft.showBattery, icon = SlateIcon.Battery) { commit(draft.copy(showBattery = it)) }
                }
                section("details", R.string.clock_details) {
                    ChoiceRow(
                        stringResource(R.string.clock_colour),
                        ClockColour.entries,
                        draft.colour,
                        { stringResource(it.label()) },
                        icon = SlateIcon.Droplet,
                    ) { commit(draft.copy(colour = it)) }
                    ChoiceRow(
                        stringResource(R.string.clock_date),
                        ClockDate.entries,
                        draft.date,
                        { stringResource(it.label()) },
                        icon = SlateIcon.Calendar,
                    ) { commit(draft.copy(date = it)) }
                    if (!DateFormat.is24HourFormat(context)) {
                        ToggleRow(stringResource(R.string.clock_am_pm), draft.showAmPm, icon = SlateIcon.Clock) { commit(draft.copy(showAmPm = it)) }
                    }
                }
                item(key = "move") {
                    Group { LinkRow(stringResource(R.string.move_home), icon = SlateIcon.Move, onClick = onMoveHome) }
                }
                item(key = "bottom") { Spacer(Modifier.height(48.dp)) }
            }
        }
    }
}

/** Every style, drawn for real with the current options at a small size. */
@Composable
private fun StyleGallery(clock: ClockSettings, onSelect: (ClockStyle) -> Unit) {
    val palette = LocalSlatePalette.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val time = rememberClockTime()
    val is24h = DateFormat.is24HourFormat(context)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ClockStyle.entries.forEach { style ->
            val selected = clock.style == style
            val sample = clock.copy(style = style, size = 0.42f, align = ClockAlign.Center)
            val formats = remember(locale, is24h, style) { ClockFormats(locale, is24h, ClockDate.Short, style) }
            Column(
                Modifier
                    .width(GalleryWidth)
                    .clip(GalleryShape)
                    .selectable(selected, role = Role.RadioButton) { onSelect(style) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(GalleryHeight)
                        .clip(GalleryShape)
                        .background(Color(0x33000000))
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) palette.accent else palette.onWallpaperMuted.copy(alpha = 0.25f), GalleryShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (style == ClockStyle.Glance) {
                        Text(time?.let { formats.date?.format(it) }.orEmpty(), style = SlateText.date.copy(fontSize = 15.sp, shadow = null), color = palette.onWallpaper, maxLines = 1)
                    } else {
                        ClockFace(sample, time, formats)
                    }
                }
                Text(
                    clockStyleLabel(style),
                    style = SlateText.hint.copy(fontSize = 13.sp, shadow = null),
                    color = if (selected) palette.accent else palette.onWallpaperMuted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

private val GalleryWidth = 128.dp
private val GalleryHeight = 96.dp
private val GalleryShape = RoundedCornerShape(16.dp)

@Composable
internal fun clockStyleLabel(style: ClockStyle): String = stringResource(
    when (style) {
        ClockStyle.Classic -> R.string.clock_style_classic
        ClockStyle.Glance -> R.string.clock_style_glance
        ClockStyle.Overlap -> R.string.clock_style_overlap
        ClockStyle.Flip -> R.string.clock_style_flip
        ClockStyle.Minimal -> R.string.clock_style_minimal
        ClockStyle.Split -> R.string.clock_style_split
        ClockStyle.Stacked -> R.string.clock_style_stacked
        ClockStyle.Day -> R.string.clock_style_day
        ClockStyle.Analog -> R.string.clock_style_analog
        ClockStyle.None -> R.string.clock_style_none
    },
)

private fun ClockAlign.label() = when (this) {
    ClockAlign.Start -> R.string.clock_align_start
    ClockAlign.Center -> R.string.clock_align_center
    ClockAlign.End -> R.string.clock_align_end
}

private fun ClockWeight.label() = when (this) {
    ClockWeight.Thin -> R.string.clock_weight_thin
    ClockWeight.Light -> R.string.clock_weight_light
    ClockWeight.Regular -> R.string.clock_weight_regular
    ClockWeight.Bold -> R.string.clock_weight_bold
}

private fun ClockColour.label() = when (this) {
    ClockColour.Text -> R.string.clock_colour_text
    ClockColour.Accent -> R.string.clock_colour_accent
    ClockColour.TwoTone -> R.string.clock_colour_two_tone
}

private fun ClockDate.label() = when (this) {
    ClockDate.Long -> R.string.clock_date_long
    ClockDate.Short -> R.string.clock_date_short
    ClockDate.Hidden -> R.string.clock_date_hidden
}

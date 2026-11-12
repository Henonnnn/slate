package dev.slate.launcher.ui.floating

import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.animateColorAsState
import android.app.PendingIntent
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.slate.launcher.R
import dev.slate.launcher.floating.FloatingItem
import dev.slate.launcher.floating.FloatingSources
import dev.slate.launcher.floating.LiveActivity
import dev.slate.launcher.floating.NowPlaying
import dev.slate.launcher.ui.components.NextGlyph
import dev.slate.launcher.ui.components.PauseGlyph
import dev.slate.launcher.ui.components.PlayGlyph
import dev.slate.launcher.ui.components.PreviousGlyph
import dev.slate.launcher.ui.components.SlateIcon
import dev.slate.launcher.ui.components.SlateIconView
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Height the list reserves at its bottom while The Pill shows, so it never covers the last row. */
val FloatingButtonSpace = 80.dp

/** What The Pill can do; the view model's functions, bundled so they travel as one. */
class PillControls(
    val dismiss: (FloatingItem) -> Unit,
    val togglePlay: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seekTo: (Long) -> Unit,
    val openMedia: () -> Unit,
    val openLive: (LiveActivity) -> Unit,
    val liveAction: (PendingIntent) -> Unit,
    val setTorch: (Boolean) -> Unit,
    val pressPlay: () -> Unit,
    val openApp: (String) -> Unit,
)

private val PillHeight = 56.dp
private val CollapsedCorner = 28.dp
private val ExpandedCorner = 30.dp
private val CollapsedMargin = 20.dp
private val ExpandedMargin = 12.dp
// The Pill sits near the bottom edge, so there isn't much room to drag.
private val DismissDistance = 40.dp
private val ExpandDistance = 18.dp
private const val UP_RESISTANCE = 0.35f
private const val DOWN_RESISTANCE = 0.25f
/** One spring moves everything that changes shape, so the parts stay together. */
private val Morph = spring<Float>(dampingRatio = 0.78f, stiffness = 380f)
private const val CONTENT_IN_MS = 220
private const val CONTENT_OUT_MS = 90

/**
 * The Pill: what's happening now, near the bottom edge. It shows one activity, or two side by
 * side: the main one wide, the other as a bubble beside it. Tapping it (or swiping up) grows it
 * into a card with everything about both; tapping outside, back, or swiping down shrinks it
 * again. Swiping the small pill down dismisses its main activity until something new comes up.
 */
@Composable
fun FloatingButton(
    items: List<FloatingItem>,
    controls: PillControls,
    options: FloatingSources,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Locking the phone or leaving home puts it away: it never waits open behind the lock screen.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { expanded = false }
    // What was last there, so The Pill can leave with its content still in it.
    var shown by remember { mutableStateOf(items) }
    if (items.isNotEmpty()) shown = items
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) expanded = false }
    BackHandler(expanded) { expanded = false }

    Box(modifier.fillMaxSize()) {
        if (expanded) {
            val collapseLabel = stringResource(R.string.floating_collapse)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { expanded = false } }
                    .semantics { customActions = listOf(CustomAccessibilityAction(collapseLabel) { expanded = false; true }) },
            )
        }
        AnimatedVisibility(
            visible = items.isNotEmpty(),
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { it } +
                scaleIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), initialScale = 0.7f),
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 } + scaleOut(tween(200), targetScale = 0.8f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding),
        ) {
            Pill(shown, options, expanded, { expanded = it }, controls)
        }
    }
}

@Composable
private fun Pill(items: List<FloatingItem>, options: FloatingSources, expanded: Boolean, onExpanded: (Boolean) -> Unit, controls: PillControls) {
    val primary = items.firstOrNull() ?: return
    val secondary = items.getOrNull(1)
    val wallpaperPalette = LocalSlatePalette.current
    val palette = remember(wallpaperPalette) { wallpaperPalette.onPanel() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissPx = with(density) { DismissDistance.toPx() }
    val expandPx = with(density) { ExpandDistance.toPx() }
    // Plain state while the finger moves: nothing queued can land after the release and fight the settle.
    var drag by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && !expanded) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "pillPress")
    val margin by animateDpAsState(if (expanded) ExpandedMargin else CollapsedMargin, spring(dampingRatio = 0.78f, stiffness = 380f), label = "pillMargin")
    val corner by animateDpAsState(if (expanded) ExpandedCorner else CollapsedCorner, spring(dampingRatio = 0.78f, stiffness = 380f), label = "pillCorner")
    val dismissLabel = stringResource(R.string.floating_dismiss)
    val expandLabel = stringResource(R.string.floating_expand)
    val expandedState = stringResource(R.string.floating_state_expanded)

    fun setExpanded(value: Boolean) {
        if (value != expanded) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onExpanded(value)
    }

    CompositionLocalProvider(LocalSlatePalette provides palette) {
        Row(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = margin),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        // Down follows the finger; up only gives a little before it opens.
                        val y = drag
                        // The card only gives a little either way; the small pill follows the finger down.
                        translationY = if (y < 0f) y * UP_RESISTANCE else if (expanded) y * DOWN_RESISTANCE else y
                        if (!expanded) alpha = 1f - (y / (dismissPx * 1.5f)).coerceIn(0f, 0.8f)
                        scaleX = pressScale
                        scaleY = pressScale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        shadowElevation = 6.dp.toPx()
                        shape = RoundedCornerShape(corner)
                        clip = true
                    }
                    .background(palette.panel)
                    .semantics {
                        if (expanded) stateDescription = expandedState
                        customActions = listOf(CustomAccessibilityAction(dismissLabel) { controls.dismiss(primary); true })
                    }
                    .draggable(
                        state = rememberDraggableState { delta -> drag += delta },
                        orientation = Orientation.Vertical,
                        onDragStarted = { settle?.cancel() },
                        onDragStopped = { velocity ->
                            val y = drag
                            when {
                                expanded && (y > dismissPx || velocity > FLING) -> {
                                    // Carry over where the card was drawn, so the pill doesn't jump under the finger.
                                    drag = y * DOWN_RESISTANCE
                                    setExpanded(false)
                                }
                                !expanded && y > dismissPx -> {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    controls.dismiss(primary)
                                }
                                !expanded && (y < -expandPx || velocity < -FLING) -> setExpanded(true)
                            }
                            // Whatever happened, it comes to rest where it belongs. The fling's speed is left out:
                            // a hard swipe would throw it off the screen first.
                            settle = scope.launch {
                                animate(drag, 0f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { v, _ -> drag = v }
                            }
                        },
                    )
                    .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = expandLabel.takeIf { !expanded }) {
                        when {
                            expanded -> Unit
                            // An offer of music has nothing to unfold: it goes straight to the app.
                            primary is FloatingItem.Music -> controls.openApp(primary.packageName)
                            primary is FloatingItem.Media && options.tapOpensApp -> controls.openMedia()
                            else -> setExpanded(true)
                        }
                    },
            ) {
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        (fadeIn(tween(CONTENT_IN_MS, delayMillis = CONTENT_OUT_MS)) + scaleIn(tween(CONTENT_IN_MS, delayMillis = CONTENT_OUT_MS), initialScale = 0.94f))
                            .togetherWith(fadeOut(tween(CONTENT_OUT_MS)))
                            .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.78f, stiffness = 380f) })
                    },
                    contentAlignment = Alignment.BottomCenter,
                    label = "pillMorph",
                ) { open ->
                    if (open) {
                        Expanded(items, options.wavyProgress, controls)
                    } else {
                        Row(Modifier.fillMaxWidth().heightIn(min = PillHeight).padding(start = 9.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            when (primary) {
                                is FloatingItem.Media -> MediaCollapsed(primary.media, compact = secondary != null, options.equalizer, controls)
                                is FloatingItem.Music -> MusicCollapsed(primary, controls)
                                is FloatingItem.Live -> LiveCollapsed(primary.activity)
                                is FloatingItem.Torch -> TorchCollapsed(primary, controls)
                                is FloatingItem.Alarm -> AlarmCollapsed(primary)
                            }
                        }
                    }
                }
            }
            // The second activity: a bubble that the main pill makes room for, and swallows on opening.
            AnimatedVisibility(
                visible = secondary != null && !expanded,
                enter = expandHorizontally(spring(dampingRatio = 0.78f, stiffness = 380f), expandFrom = Alignment.Start) +
                    scaleIn(spring(dampingRatio = 0.6f, stiffness = 380f), initialScale = 0.4f) + fadeIn(tween(160)),
                exit = shrinkHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f), shrinkTowards = Alignment.Start) +
                    scaleOut(tween(140), targetScale = 0.4f) + fadeOut(tween(100)),
            ) {
                val bubble = secondary ?: items.getOrNull(1)
                Row {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .graphicsLayer {
                                shadowElevation = 6.dp.toPx()
                                shape = CircleShape
                                clip = true
                            }
                            .background(palette.panel)
                            .then(
                                // The flashlight's bubble is its switch; any other opens the card.
                                if (bubble is FloatingItem.Torch) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = expandLabel) { setExpanded(true) },
                            )
                            .heightIn(min = PillHeight)
                            .widthIn(min = PillHeight)
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (bubble) {
                            is FloatingItem.Media -> Cover(bubble.media, 38.dp, CircleShape)
                            is FloatingItem.Live -> {
                                val count = rememberLiveCount(bubble.activity)
                                if (count != null) {
                                    LiveCount(count, SlateText.appLabel.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = null), palette.onWallpaper)
                                } else if (bubble.activity.title.isCount()) {
                                    // Clock apps that write the count as their title (Fossify).
                                    Text(
                                        bubble.activity.title,
                                        style = SlateText.appLabel.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = null, fontFeatureSettings = "tnum"),
                                        color = palette.onWallpaper,
                                        maxLines = 1,
                                    )
                                } else {
                                    SlateIconView(SlateIcon.Hourglass, palette.onWallpaper, size = 22.dp)
                                }
                            }
                            is FloatingItem.Alarm -> SlateIconView(SlateIcon.Bell, palette.onWallpaper, size = 22.dp)
                            is FloatingItem.Torch -> TorchSwitch(bubble, controls)
                            is FloatingItem.Music -> SlateIconView(SlateIcon.Note, palette.onWallpaper, size = 22.dp)
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}

private const val FLING = 900f

// ---------------------------------------------------------------------------------------------
// The small pill

@Composable
private fun RowScope.MediaCollapsed(m: NowPlaying, compact: Boolean, equalizer: Boolean, controls: PillControls) {
    val palette = LocalSlatePalette.current
    Cover(m, 38.dp, RoundedCornerShape(10.dp))
    Spacer(Modifier.width(12.dp))
    TrackText(m, Modifier.weight(1f), titleSize = 16, marquee = false)
    if (equalizer) Equalizer(m.playing, palette.accent, Modifier.padding(horizontal = 6.dp))
    PlayPause(m.playing, palette.onWallpaper, controls.togglePlay)
    // With a second activity beside it, the pill keeps to the one control that matters.
    if (m.canSkip && !compact) {
        val nextLabel = stringResource(R.string.media_next)
        IconButton(onClick = controls.next, modifier = Modifier.semantics { contentDescription = nextLabel }) { NextGlyph(palette.onWallpaperMuted) }
    }
}

@Composable
private fun RowScope.LiveCollapsed(a: LiveActivity) {
    val palette = LocalSlatePalette.current
    Badge(SlateIcon.Hourglass)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f).padding(end = 14.dp)) {
        val count = rememberLiveCount(a)
        if (count != null) {
            LiveCount(count, SlateText.appLabel.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, shadow = null), palette.onWallpaper)
        } else {
            Line(a.title.ifBlank { a.appLabel }, 16, palette.onWallpaper)
        }
        val detail = liveDetail(a, counted = count != null)
        if (detail.isNotBlank()) Line(detail, 13, palette.onWallpaperMuted, hint = true)
    }
}

/** Music on offer: the app's name, and a play button when the user wants one. */
@Composable
private fun RowScope.MusicCollapsed(item: FloatingItem.Music, controls: PillControls) {
    val palette = LocalSlatePalette.current
    Badge(SlateIcon.Note)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f).padding(end = if (item.player) 0.dp else 14.dp)) {
        Line(item.label, 16, palette.onWallpaper)
        Line(stringResource(if (item.player) R.string.floating_music_ready else R.string.floating_music_open), 13, palette.onWallpaperMuted, hint = true)
    }
    if (item.player) PlayPause(playing = false, palette.onWallpaper, controls.pressPlay)
}

@Composable
private fun RowScope.TorchCollapsed(item: FloatingItem.Torch, controls: PillControls) {
    val palette = LocalSlatePalette.current
    TorchSwitch(item, controls)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f).padding(end = 14.dp)) {
        Line(stringResource(R.string.floating_torch), 16, palette.onWallpaper)
        Line(stringResource(if (item.on) R.string.torch_on else R.string.floating_torch_off), 13, palette.onWallpaperMuted, hint = true)
    }
}

/**
 * The flashlight's own switch: lit in the accent while it's on, dim once it's out. Out, it stays
 * a few seconds more, so a slip is one tap from undone.
 */
@Composable
private fun TorchSwitch(item: FloatingItem.Torch, controls: PillControls, size: Dp = 38.dp) {
    val palette = LocalSlatePalette.current
    val view = LocalView.current
    val ground by animateColorAsState(if (item.on) palette.accent else palette.panelRaised, tween(220), label = "torchGround")
    val mark by animateColorAsState(if (item.on) palette.panel else palette.onWallpaperMuted, tween(220), label = "torchMark")
    val scale by animateFloatAsState(if (item.on) 1f else 0.9f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "torchScale")
    val label = stringResource(R.string.floating_torch)
    val state = stringResource(if (item.on) R.string.torch_on else R.string.torch_off)
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(ground)
            .toggleable(item.on, role = Role.Switch) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                controls.setTorch(it)
            }
            .semantics {
                contentDescription = label
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        SlateIconView(SlateIcon.Torch, mark, size = size * 0.58f)
    }
}

@Composable
private fun RowScope.AlarmCollapsed(item: FloatingItem.Alarm) {
    val palette = LocalSlatePalette.current
    Badge(SlateIcon.Bell)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f).padding(end = 14.dp)) {
        Line(alarmTime(item.time), 16, palette.onWallpaper)
        Line(stringResource(R.string.floating_next_alarm), 13, palette.onWallpaperMuted, hint = true)
    }
}

// ---------------------------------------------------------------------------------------------
// The card it grows into

@Composable
private fun Expanded(items: List<FloatingItem>, wavy: Boolean, controls: PillControls) {
    val palette = LocalSlatePalette.current
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEachIndexed { index, item ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.onWallpaper.copy(alpha = 0.1f)))
            when (item) {
                is FloatingItem.Media -> MediaExpanded(item.media, wavy, controls)
                is FloatingItem.Live -> LiveExpanded(item.activity, controls)
                is FloatingItem.Music -> ActivityRow(
                    icon = SlateIcon.Note,
                    onOpen = { controls.openApp(item.packageName) },
                    headline = { Line(item.label, 18, palette.onWallpaper, weight = FontWeight.SemiBold) },
                    detail = stringResource(R.string.floating_music_open),
                )
                is FloatingItem.Torch -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TorchSwitch(item, controls, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Line(stringResource(R.string.floating_torch), 18, palette.onWallpaper, weight = FontWeight.SemiBold)
                        Line(stringResource(if (item.on) R.string.torch_on else R.string.floating_torch_off), 14, palette.onWallpaperMuted, hint = true)
                    }
                }
                is FloatingItem.Alarm -> ActivityRow(
                    icon = SlateIcon.Bell,
                    onOpen = { runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    headline = { Line(alarmTime(item.time), 20, palette.onWallpaper, weight = FontWeight.SemiBold) },
                    detail = stringResource(R.string.floating_next_alarm),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaExpanded(m: NowPlaying, wavy: Boolean, controls: PillControls) {
    val palette = LocalSlatePalette.current
    val openLabel = stringResource(R.string.floating_open_player)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The cover and the titles are the way into the app that's playing.
        Row(
            Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = openLabel, role = Role.Button, onClick = controls.openMedia),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(m, 64.dp, RoundedCornerShape(16.dp))
            Spacer(Modifier.width(14.dp))
            TrackText(m, Modifier.weight(1f), titleSize = 18, marquee = true)
            Spacer(Modifier.width(8.dp))
            SlateIconView(SlateIcon.OpenExternal, palette.onWallpaperMuted, Modifier.padding(end = 6.dp), size = 20.dp)
        }
        if (m.durationMs > 0) MediaProgress(m, wavy, controls.seekTo)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val previousLabel = stringResource(R.string.media_previous)
            val nextLabel = stringResource(R.string.media_next)
            IconButton(onClick = controls.previous, enabled = m.canPrevious, modifier = Modifier.size(52.dp).semantics { contentDescription = previousLabel }) {
                PreviousGlyph(palette.onWallpaper.copy(alpha = if (m.canPrevious) 1f else 0.3f), Modifier.size(30.dp))
            }
            Spacer(Modifier.width(20.dp))
            Box(Modifier.size(60.dp).clip(CircleShape).background(palette.onWallpaper), contentAlignment = Alignment.Center) {
                PlayPause(m.playing, palette.panel, controls.togglePlay, glyph = 32.dp, button = 60.dp)
            }
            Spacer(Modifier.width(20.dp))
            IconButton(onClick = controls.next, enabled = m.canSkip, modifier = Modifier.size(52.dp).semantics { contentDescription = nextLabel }) {
                NextGlyph(palette.onWallpaper.copy(alpha = if (m.canSkip) 1f else 0.3f), Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun LiveExpanded(a: LiveActivity, controls: PillControls) {
    val palette = LocalSlatePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val count = rememberLiveCount(a)
        ActivityRow(
            icon = SlateIcon.Hourglass,
            onOpen = { controls.openLive(a) },
            headline = {
                if (count != null) {
                    LiveCount(count, SlateText.appLabel.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, shadow = null), palette.onWallpaper)
                } else {
                    Line(a.title.ifBlank { a.appLabel }, 18, palette.onWallpaper, weight = FontWeight.SemiBold)
                }
            },
            detail = liveDetail(a, counted = count != null),
        )
        // The activity's own buttons, as its notification offers them: pause, add a minute, dismiss.
        val actions = remember(a) { a.source?.actions.orEmpty().filter { it.actionIntent != null && it.remoteInputs.isNullOrEmpty() && !it.title.isNullOrBlank() }.take(MAX_ACTIONS) }
        if (actions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    Text(
                        action.title.toString(),
                        style = SlateText.appLabel.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = null),
                        color = palette.onWallpaper,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(CircleShape)
                            .background(palette.panelRaised)
                            .clickable(role = Role.Button) { controls.liveAction(action.actionIntent) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private const val MAX_ACTIONS = 3

/** A title that is nothing but a running time, like "12:10" or "1:02:03". */
private fun String.isCount(): Boolean = length in 4..10 && all { it.isDigit() || it == ':' || it == '.' } && any { it == ':' }

/** The line under a live activity's headline: whatever the headline didn't already say. */
private fun liveDetail(a: LiveActivity, counted: Boolean): String {
    val name = a.title.ifBlank { a.appLabel }
    return if (counted) a.title.ifBlank { a.text }.ifBlank { a.appLabel } else a.text.ifBlank { a.appLabel.takeIf { it != name }.orEmpty() }
}

/** A badge, a headline over a detail line, and the way into the app. */
@Composable
private fun ActivityRow(icon: SlateIcon, onOpen: () -> Unit, headline: @Composable () -> Unit, detail: String) {
    val palette = LocalSlatePalette.current
    val openLabel = stringResource(R.string.floating_open)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(icon, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            headline()
            if (detail.isNotBlank()) Line(detail, 14, palette.onWallpaperMuted, hint = true)
        }
        SlateIconView(SlateIcon.OpenExternal, palette.onWallpaperMuted, Modifier.padding(end = 6.dp), size = 20.dp)
    }
}

// ---------------------------------------------------------------------------------------------
// Parts

@Composable
private fun Badge(icon: SlateIcon, size: Dp = 38.dp) {
    val palette = LocalSlatePalette.current
    Box(Modifier.size(size).background(palette.panelRaised, CircleShape), contentAlignment = Alignment.Center) {
        SlateIconView(icon, palette.onWallpaper, size = size * 0.55f)
    }
}

@Composable
private fun Line(text: String, size: Int, color: Color, hint: Boolean = false, weight: FontWeight? = null) {
    val base = if (hint) SlateText.hint else SlateText.appLabel
    Text(text, style = base.copy(fontSize = size.sp, shadow = null, fontWeight = weight ?: base.fontWeight), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The cover fades to the next track's instead of snapping. */
@Composable
private fun Cover(m: NowPlaying, size: Dp, shape: androidx.compose.ui.graphics.Shape) {
    val palette = LocalSlatePalette.current
    Box(Modifier.size(size).clip(shape).background(palette.panelRaised), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = m,
            // The same track keeps its cover while only its position changes.
            contentKey = { it.packageName + it.title + (it.art != null) },
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 1.12f)).togetherWith(fadeOut(tween(200))) },
            label = "cover",
        ) { track ->
            val art = remember(track.art) { track.art?.asImageBitmap() }
            if (art != null) {
                Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
            } else {
                Box(Modifier.size(size), contentAlignment = Alignment.Center) { PlayGlyph(palette.onWallpaperMuted) }
            }
        }
    }
}

/** Title over artist; a new track's lines rise into place. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackText(m: NowPlaying, modifier: Modifier, titleSize: Int, marquee: Boolean) {
    val palette = LocalSlatePalette.current
    AnimatedContent(
        targetState = m.title to m.artist,
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 })
                .togetherWith(fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 })
        },
        modifier = modifier,
        label = "track",
    ) { (title, artist) ->
        Column {
            Text(
                title,
                style = SlateText.appLabel.copy(fontSize = titleSize.sp, shadow = null, fontWeight = if (marquee) FontWeight.SemiBold else SlateText.appLabel.fontWeight),
                color = palette.onWallpaper,
                maxLines = 1,
                overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (marquee) Modifier.basicMarquee() else Modifier,
            )
            if (artist.isNotEmpty()) Line(artist, titleSize - 3, palette.onWallpaperMuted, hint = true)
        }
    }
}

/** Play and pause swap with a small pop. */
@Composable
private fun PlayPause(playing: Boolean, color: Color, onClick: () -> Unit, glyph: Dp = 24.dp, button: Dp = 48.dp) {
    val label = stringResource(if (playing) R.string.media_pause else R.string.media_play)
    IconButton(onClick = onClick, modifier = Modifier.size(button).semantics { contentDescription = label }) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.5f) + fadeIn(tween(120)))
                    .togetherWith(scaleOut(tween(100), targetScale = 0.5f) + fadeOut(tween(100)))
            },
            label = "playPause",
        ) { isPlaying ->
            if (isPlaying) PauseGlyph(color, Modifier.size(glyph)) else PlayGlyph(color, Modifier.size(glyph))
        }
    }
}

/** Three bars that move while something plays and settle when it stops. Drawn, never recomposed. */
@Composable
private fun Equalizer(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(if (playing) 1f else 0f, tween(300), label = "eqLevel")
    val phase = if (playing) {
        rememberInfiniteTransition(label = "eq").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "eqPhase")
    } else {
        null
    }
    Canvas(modifier.size(14.dp).graphicsLayer { alpha = 0.35f + 0.65f * level }.clearAndSetSemantics {}) {
        val bar = size.width / 5f
        repeat(3) { i ->
            val wave = abs(sin(2f * PI.toFloat() * ((phase?.value ?: 0f) * (i + 1) + i * 0.31f)))
            val h = size.height * (0.25f + 0.75f * wave * level)
            drawRoundRect(color, Offset(i * bar * 2f, size.height - h), Size(bar, h), CornerRadius(bar / 2f))
        }
    }
}

/**
 * Where the track is, following the player's own clock every frame, drawn as Material 3
 * Expressive's wavy line; drag or tap it to seek when the player allows. The bar reads the time while drawing, so only the two labels recompose, once
 * a second.
 */
@Composable
private fun MediaProgress(m: NowPlaying, wavy: Boolean, onSeek: (Long) -> Unit) {
    val palette = LocalSlatePalette.current
    val frame = remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(m.playing, m.positionAt) {
        frame.longValue = android.os.SystemClock.elapsedRealtime()
        while (m.playing) {
            withFrameMillis { }
            frame.longValue = android.os.SystemClock.elapsedRealtime()
        }
    }
    var scrub by remember { mutableStateOf<Float?>(null) }
    val thickness by animateDpAsState(if (scrub != null) 6.dp else 4.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "seekThickness")
    val waveLevel by animateFloatAsState(if (wavy && m.playing && scrub == null) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 200f), label = "wave")
    val wavePath = remember { Path() }
    val seconds by remember(m) { derivedStateOf { (scrub?.let { (it * m.durationMs).toLong() } ?: m.positionAt(frame.longValue)) / 1000 } }
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clearAndSetSemantics {}
                .then(
                    if (!m.canSeek) {
                        Modifier
                    } else {
                        Modifier
                            .pointerInput(m.durationMs) {
                                detectTapGestures { onSeek(((it.x / size.width).coerceIn(0f, 1f) * m.durationMs).toLong()) }
                            }
                            .pointerInput(m.durationMs) {
                                detectHorizontalDragGestures(
                                    onDragStart = { scrub = (it.x / size.width).coerceIn(0f, 1f) },
                                    onDragCancel = { scrub = null },
                                    onDragEnd = {
                                        scrub?.let { onSeek((it * m.durationMs).toLong()) }
                                        scrub = null
                                    },
                                ) { change, _ -> scrub = (change.position.x / size.width).coerceIn(0f, 1f) }
                            }
                    },
                ),
        ) {
            val t = thickness.toPx()
            val cy = size.height / 2f
            val fraction = scrub ?: (m.positionAt(frame.longValue).toFloat() / m.durationMs).coerceIn(0f, 1f)
            val x = size.width * fraction
            val gap = WaveGap.toPx()
            // What's left to play: a plain line, after a gap.
            if (x + gap < size.width) drawLine(palette.onWallpaper.copy(alpha = 0.18f), Offset(x + gap, cy), Offset(size.width, cy), t, StrokeCap.Round)
            // What's played: a wave that runs while the music does and lies flat when it stops.
            val amplitude = WaveAmplitude.toPx() * waveLevel
            val length = WaveLength.toPx()
            val phase = (frame.longValue % WAVE_PERIOD_MS) / WAVE_PERIOD_MS.toFloat() * 2f * PI.toFloat()
            val end = x - gap
            if (end > 0f) {
                wavePath.rewind()
                wavePath.moveTo(0f, cy + amplitude * sin(-phase))
                var px = WAVE_STEP
                while (px < end) {
                    wavePath.lineTo(px, cy + amplitude * sin(2f * PI.toFloat() * px / length - phase))
                    px += WAVE_STEP
                }
                wavePath.lineTo(end, cy + amplitude * sin(2f * PI.toFloat() * end / length - phase))
                drawPath(wavePath, palette.accent, style = Stroke(t, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val thumb = ThumbHeight.toPx()
            drawRoundRect(palette.onWallpaper, Offset(x - t / 2f, cy - thumb / 2f), Size(t, thumb), CornerRadius(t / 2f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Line(clock(seconds), 12, palette.onWallpaperMuted, hint = true)
            Line(clock(m.durationMs / 1000), 12, palette.onWallpaperMuted, hint = true)
        }
    }
}

private val WaveAmplitude = 3.dp
private val WaveLength = 26.dp
private val WaveGap = 5.dp
private val ThumbHeight = 18.dp
private const val WAVE_PERIOD_MS = 1300L
private const val WAVE_STEP = 3f

private fun clock(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val min = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, min, s) else String.format(Locale.ROOT, "%d:%02d", min, s)
}

@Composable
private fun alarmTime(time: Instant): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24h = DateFormat.is24HourFormat(context)
    val format = remember(locale, is24h) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24h) "EHm" else "Ehm"), locale)
    }
    return format.format(time.atZone(ZoneId.systemDefault()))
}

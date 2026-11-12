package dev.slate.launcher.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.slate.launcher.R
import dev.slate.launcher.apps.AlphabetSection
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText

private val ColumnWidth = 52.dp
private val RightStrip = 44.dp
/** Stops short of the app icons, which start 28 dp in. */
private val LeftStrip = 24.dp
private val BottomGap = 64.dp
private val PressedOffset = 64.dp
private val ListTopOffset = 150.dp
private val ListTopMin = 48.dp
private val ListTopMax = 260.dp
private val MaxStep = 19.sp
private val LetterSize = 16.sp
private val TightLetterSize = 14.sp
private val SectionIconSize = 13.sp
private val HandleDiameter = 44.sp
private val HandleMargin = 12.sp
private val HandleText = 28.sp
private val HandleIcon = 26.sp
private const val PRESS_MS = 150
private const val ENTRANCE_MS = 720

/** How far down the list a section lands when the alphabet jumps to it, in px. */
fun alphabetJumpOffset(screenHeight: Float, density: androidx.compose.ui.unit.Density): Float = with(density) {
    AlphabetWave.listTop(screenHeight, ListTopOffset.toPx(), ListTopMin.toPx(), ListTopMax.toPx())
}

/**
 * The list's sections as a wave down the screen edge: letters with apps, plus icons for favourites
 * and the private space. Spread over at least half the free height and centred under the list.
 * Touching either edge pulls a wave out toward the finger: its peak follows the finger sideways,
 * the block follows it up and down, and a handle beside the peak names the section, sticking
 * to each one until the finger nears the next. Each change ticks and jumps the list.
 *
 * Everything is drawn in one Canvas and all motion is read while drawing, so a moving finger
 * redraws without recomposing.
 */
@Composable
fun AlphabetScrubber(
    sections: List<AlphabetSection>,
    onSection: (AlphabetSection) -> Unit,
    modifier: Modifier = Modifier,
    /** True while a finger is on the index. */
    onActiveChange: (Boolean) -> Unit = {},
) {
    val palette = LocalSlatePalette.current
    val view = LocalView.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentOnSection by rememberUpdatedState(onSection)

    var activeIndex by remember { mutableIntStateOf(-1) }
    var shownIndex by remember { mutableIntStateOf(0) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var followTop by remember { mutableFloatStateOf(0f) }
    var fromFarEdge by remember { mutableStateOf(false) }
    val active by remember { derivedStateOf { activeIndex >= 0 } }
    LaunchedEffect(active) { onActiveChange(active) }

    // Both ease over the same short time, in and out: nothing here bounces except the entrance.
    val press by animateFloatAsState(if (active) 1f else 0f, tween(PRESS_MS, easing = FastOutSlowInEasing), label = "scrubberPress")
    val handle by animateFloatAsState(if (active) 1f else 0f, tween(PRESS_MS, easing = FastOutSlowInEasing), label = "scrubberHandle")
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(ENTRANCE_MS, easing = LinearEasing)) }

    val measurer = rememberTextMeasurer(cacheSize = 0)
    val indexDescription = stringResource(R.string.alphabet_index)
    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val stepLimit = with(density) { MaxStep.toPx() }
        val count = sections.size.coerceAtLeast(1)
        val blockPx = AlphabetWave.blockHeight(count, stepLimit, heightPx)
        val listTop = alphabetJumpOffset(heightPx, density)
        val restTop = AlphabetWave.restingTop(listTop, 0f, heightPx, blockPx, heightPx, with(density) { BottomGap.toPx() })
        val step = blockPx / count

        val fits = blockPx >= count * with(density) { LetterSize.toPx() }
        val letterStyle = SlateText.scrubberLetter.copy(fontSize = if (fits) LetterSize else TightLetterSize)
        val handleStyle = SlateText.bubbleLetter.copy(fontSize = HandleText)
        val letters = remember(sections, letterStyle) { sections.map { s -> (s as? AlphabetSection.Letter)?.let { measurer.measure(it.char.toString(), letterStyle) } } }
        val handleLetters = remember(sections, handleStyle) { sections.map { s -> (s as? AlphabetSection.Letter)?.let { measurer.measure(it.char.toString(), handleStyle) } } }

        fun update(x: Float, y: Float) {
            fingerX = x
            fingerY = y
            followTop = AlphabetWave.follow(followTop, blockPx, y, 0f, heightPx)
            if (sections.isEmpty()) return
            val index = AlphabetWave.indexAt(y, followTop, step, sections.size)
            if (index != activeIndex) {
                activeIndex = index
                shownIndex = index
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                currentOnSection(sections[index])
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val column = ColumnWidth.toPx()
            val centre = column / 2f
            val handleSize = HandleDiameter.toPx()
            val handleMargin = HandleMargin.toPx()
            val limit = widthPx - centre - handleMargin * 2f - handleSize
            val top = restTop + (followTop - restTop) * press
            // Measured from the screen edge the letters sit on.
            val edgeDistance = if (rtl) fingerX else widthPx - fingerX
            val peak = if (fromFarEdge) PressedOffset.toPx() else AlphabetWave.peak(PressedOffset.toPx(), edgeDistance, limit)
            val height = peak * press
            val spread = height / blockPx
            val offscreen = centre + column
            letters.forEachIndexed { i, layout ->
                val y = top + step * (i + 0.5f)
                val lean = AlphabetWave.bulge(i, count, fingerY, top, blockPx, spread) * height
                val ripple = AlphabetWave.entrance(entrance.value, i, count) * offscreen
                val fromEdge = centre + lean - ripple
                val x = if (rtl) fromEdge else size.width - fromEdge
                if (layout != null) {
                    drawText(layout, color = palette.onWallpaper, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
                } else {
                    sections[i].icon()?.let { icon ->
                        val iconPx = SectionIconSize.toPx()
                        drawSlateIcon(icon, palette.onWallpaper, iconPx, Offset(x - iconPx / 2f, y - iconPx / 2f))
                    }
                }
            }
            if (handle > 0.01f) {
                val radius = handleSize / 2f
                val fromEdge = centre + peak + handleMargin + radius
                val x = if (rtl) fromEdge else size.width - fromEdge
                val y = AlphabetWave.handleY(fingerY, top, step, shownIndex)
                scale(handle, pivot = Offset(x, y)) {
                    drawCircle(palette.onWallpaper, radius, Offset(x, y))
                    val layout = handleLetters.getOrNull(shownIndex)
                    if (layout != null) {
                        drawText(layout, color = palette.accent, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
                    } else {
                        sections.getOrNull(shownIndex)?.icon()?.let { icon ->
                            val iconPx = HandleIcon.toPx()
                            drawSlateIcon(icon, palette.accent, iconPx, Offset(x - iconPx / 2f, y - iconPx / 2f))
                        }
                    }
                }
            }
        }

        // Both edges work, so either thumb can reach it. Only these strips take touches, and
        // once down the finger is tracked anywhere on screen.
        @Composable
        fun Strip(alignment: Alignment, width: androidx.compose.ui.unit.Dp, farEdge: Boolean) {
            val stripLeft = if (alignment == Alignment.CenterEnd) widthPx - with(density) { width.toPx() } else 0f
            Box(
                Modifier
                    .align(alignment)
                    .fillMaxHeight()
                    .width(width)
                    .systemGestureExclusion()
                    .semantics { if (!farEdge) contentDescription = indexDescription }
                    .pointerInput(sections, restTop, step, stripLeft) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            fromFarEdge = farEdge
                            followTop = restTop
                            update(stripLeft + down.position.x, down.position.y)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                update(stripLeft + change.position.x, change.position.y)
                            }
                            activeIndex = -1
                        }
                    },
            )
        }
        Strip(Alignment.CenterEnd, RightStrip, farEdge = rtl)
        Strip(Alignment.CenterStart, LeftStrip, farEdge = !rtl)
    }
}

private fun AlphabetSection.icon(): SlateIcon? = when (this) {
    AlphabetSection.Favourites -> SlateIcon.Star
    AlphabetSection.Folders -> SlateIcon.Diamond
    AlphabetSection.PrivateSpace -> SlateIcon.Shield
    is AlphabetSection.Letter -> null
}

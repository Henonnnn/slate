package dev.slate.launcher.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Horizontal row gestures. "End" is right in LTR and left in RTL. */
class RowSwipe(val toEnd: Action?, val toStart: Action?) {
    /** [label] is per row: the same gesture can read "Pop-up" on one app and "Actions" on another. */
    class Action(
        val label: (AppEntry) -> String,
        /** Fires as soon as the swipe goes far enough, finger still down, instead of on release. */
        val immediate: Boolean = false,
        val run: (AppEntry, at: Offset) -> Unit,
    )
}

private val TriggerDistance = 104.dp
private val ImmediateTriggerDistance = 60.dp
private val MaxTravel = 148.dp
private const val DRAG_RESISTANCE = 0.7f

/**
 * Rubber-banded horizontal drag. Past [TriggerDistance] the row ticks; releasing there runs the
 * action while the row springs home. An [RowSwipe.Action.immediate] action runs sooner, at
 * [ImmediateTriggerDistance], without waiting for the finger to lift. Vertical scrolling is untouched: the drag only claims the
 * gesture after horizontal touch slop. The same actions are exposed as accessibility actions.
 *
 * The offset is plain state written synchronously from the drag callback. (Queuing a coroutine
 * per drag delta let a late write land after the release and cancel the spring-back, leaving
 * the row and its label stuck part-way.)
 */
@Composable
fun SwipeableRow(swipe: RowSwipe, entry: AppEntry, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val trigger = with(density) { TriggerDistance.toPx() }
    val max = with(density) { MaxTravel.toPx() }
    val drag = remember { SwipeState() }

    // Positive = toward the end edge, whatever the layout direction.
    fun towardEnd(px: Float) = if (rtl) -px else px

    val immediateTrigger = with(density) { ImmediateTriggerDistance.toPx() }

    // In our own scope so nothing the action triggers can cancel it.
    fun settleHome() {
        drag.settle = scope.launch {
            animate(drag.offset, 0f, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) { v, _ ->
                drag.offset = v
            }
        }
    }

    val state = rememberDraggableState { delta ->
        if (drag.spent) return@rememberDraggableState
        val next = (drag.offset + delta * DRAG_RESISTANCE).coerceIn(-max, max)
        val action = if (towardEnd(next) > 0) swipe.toEnd else swipe.toStart
        // A direction set to "do nothing" doesn't move at all.
        drag.offset = if (action == null) 0f else next
        if (action?.immediate == true && abs(drag.offset) >= immediateTrigger) {
            val at = drag.point()
            drag.spent = true
            drag.armed = false
            settleHome()
            action.run(entry, at)
            return@rememberDraggableState
        }
        val nowArmed = abs(drag.offset) >= trigger
        if (nowArmed != drag.armed) {
            drag.armed = nowArmed
            if (nowArmed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Box(
        modifier
            .semantics {
                customActions = listOfNotNull(swipe.toEnd, swipe.toStart).map { action ->
                    CustomAccessibilityAction(action.label(entry)) { action.run(entry, drag.point()); true }
                }
            }
            .onPlaced { drag.coordinates = it }
            .draggable(
                state = state,
                orientation = Orientation.Horizontal,
                onDragStarted = { start ->
                    drag.settle?.cancel()
                    drag.startX = start.x
                    drag.spent = false
                },
                onDragStopped = {
                    if (!drag.spent) {
                        val fired = drag.armed
                        val toEnd = towardEnd(drag.offset) > 0
                        val at = drag.point()
                        drag.armed = false
                        settleHome()
                        if (fired) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            (if (toEnd) swipe.toEnd else swipe.toStart)?.run?.invoke(entry, at)
                        }
                    }
                },
            ),
    ) {
        // Labels sit in the gap the row leaves behind. Only composed while the row is off
        // centre, so the hundreds of resting rows don't each carry two hidden Texts.
        if (drag.moved) {
            swipe.toEnd?.let { SwipeLabel(it.label(entry), Alignment.CenterStart) { towardEnd(drag.offset) } }
            swipe.toStart?.let { SwipeLabel(it.label(entry), Alignment.CenterEnd) { -towardEnd(drag.offset) } }
        }
        content(Modifier.graphicsLayer { translationX = drag.offset })
    }
}

private class SwipeState {
    var offset by mutableFloatStateOf(0f)
    /** Read in composition; flips only at the start and end of a swipe, not per frame. */
    val moved by derivedStateOf { offset != 0f }
    var armed = false
    /** An immediate action already ran; the rest of this drag does nothing. */
    var spent = false
    var settle: Job? = null
    var startX = 0f
    var coordinates: LayoutCoordinates? = null

    /** Where the finger is in the window, on the row's centre line. */
    fun point(): Offset {
        val bounds = coordinates?.takeIf { it.isAttached }?.boundsInWindow() ?: return Offset.Unspecified
        return Offset(bounds.left + startX + offset, bounds.center.y)
    }
}

/** [revealed] is the width of the gap the row has left at this label's edge, in px. */
@Composable
private fun BoxScope.SwipeLabel(text: String, alignment: Alignment, revealed: () -> Float) {
    Text(
        text = text.uppercase(),
        style = SlateText.sectionLabel,
        color = LocalSlatePalette.current.accent,
        maxLines = 1,
        modifier = Modifier
            .align(alignment)
            .padding(horizontal = LabelInset)
            .graphicsLayer {
                // Only once the whole word fits in the gap, so it never shows cut off or under the row.
                val room = revealed() - LabelInset.toPx() - size.width
                alpha = (room / LabelFade.toPx()).coerceIn(0f, 1f)
            },
    )
}

private val LabelInset = 20.dp
private val LabelFade = 16.dp

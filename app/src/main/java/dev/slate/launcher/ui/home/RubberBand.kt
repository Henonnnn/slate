package dev.slate.launcher.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

private val MaxOverscroll = 500.dp
/** Pulling a list up this far past its end and letting go opens search. */
private val SearchPull = 72.dp
private const val RETURN_DAMPING = 0.75f
private const val RETURN_STIFFNESS = 525f

/** How far content moves for a given overscroll: quick at first, then ever harder to pull. */
fun rubberBand(overscroll: Float, max: Float): Float {
    val a = abs(overscroll / max)
    return overscroll.sign * max * if (a > 1f) 0.2f else 0.8f * (1.25f * a - a.pow(1.25f))
}

/**
 * Lists stretch past their ends on this curve and spring back, instead of the platform's stretch.
 * [shown] is read in `graphicsLayer {}`, so dragging never recomposes.
 */
class RubberBandState internal constructor(
    private val maxPx: Float,
    private val searchPx: Float,
    private val scope: CoroutineScope,
    private val canSearch: () -> Boolean,
    private val acceptsDown: () -> Boolean,
    private val onArmed: () -> Unit,
    private val onSearch: () -> Unit,
) {
    private val overscroll: Animatable<Float, AnimationVector1D> = Animatable(0f)
    private var armed = false

    val shown: Float get() = rubberBand(overscroll.value, maxPx)

    private fun set(value: Float) {
        val next = value.coerceIn(-maxPx, maxPx)
        scope.launch { overscroll.snapTo(next) }
        val nowArmed = canSearch() && shownFor(next) <= -searchPx
        if (nowArmed && !armed) onArmed()
        armed = nowArmed
    }

    private fun shownFor(value: Float) = rubberBand(value, maxPx)

    private suspend fun release(velocity: Float) {
        if (armed) onSearch()
        armed = false
        overscroll.animateTo(0f, spring(RETURN_DAMPING, RETURN_STIFFNESS), initialVelocity = velocity)
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val current = overscroll.value
            // Reversing undoes the stretch before the list scrolls again.
            if (current == 0f || source != NestedScrollSource.UserInput || available.y.sign == current.sign) return Offset.Zero
            val next = if (abs(available.y) >= abs(current)) 0f else current + available.y
            set(next)
            return Offset(0f, next - current)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
            if (available.y > 0f && !acceptsDown()) return Offset.Zero
            set(overscroll.value + available.y)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (overscroll.value == 0f) return Velocity.Zero
            release(available.y)
            return available
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // A fling that runs into the end of the list bounces off it.
            if (available.y != 0f) overscroll.animateTo(0f, spring(RETURN_DAMPING, RETURN_STIFFNESS), initialVelocity = available.y)
            return available
        }
    }
}

@Composable
fun rememberRubberBand(canSearch: Boolean, acceptsDown: Boolean, onSearch: () -> Unit): RubberBandState {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentCanSearch by rememberUpdatedState(canSearch)
    val currentAcceptsDown by rememberUpdatedState(acceptsDown)
    val currentOnSearch by rememberUpdatedState(onSearch)
    return remember(density) {
        RubberBandState(
            maxPx = with(density) { MaxOverscroll.toPx() },
            searchPx = with(density) { SearchPull.toPx() },
            scope = scope,
            canSearch = { currentCanSearch },
            acceptsDown = { currentAcceptsDown },
            onArmed = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
            onSearch = { currentOnSearch() },
        )
    }
}

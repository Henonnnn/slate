package dev.slate.launcher.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

private val TriggerDistance = 88.dp
private val MaxPull = 140.dp
private const val PULL_RESISTANCE = 0.5f

/**
 * Pull down on the home list, when it's already at the top, to open search. The list
 * follows the finger with resistance; past the trigger it ticks, and releasing opens search.
 * Flings never trigger it, only a deliberate drag.
 */
@Stable
class PullToSearchState internal constructor(
    private val triggerPx: Float,
    private val maxPx: Float,
    private val onArmed: () -> Unit,
    private val trigger: () -> Unit,
    private val isEnabled: () -> Boolean,
) {
    var distance by mutableFloatStateOf(0f)
        private set

    /** 0 → 1 as the pull approaches the trigger point. */

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Pushing back up while pulled: shrink the pull before the list scrolls.
            if (distance > 0f && available.y < 0f && source == NestedScrollSource.UserInput) {
                val used = maxOf(available.y, -distance / PULL_RESISTANCE)
                set(distance + used * PULL_RESISTANCE)
                return Offset(0f, used)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Leftover downward drag means the list is at its top.
            if (isEnabled() && available.y > 0f && source == NestedScrollSource.UserInput) {
                set(distance + available.y * PULL_RESISTANCE)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (distance <= 0f) return Velocity.Zero
            if (distance >= triggerPx) trigger()
            animate(distance, 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) { v, _ ->
                distance = v
            }
            // Swallow the fling so the list doesn't jump after the release.
            return available
        }
    }

    private fun set(value: Float) {
        val wasArmed = distance >= triggerPx
        distance = value.coerceIn(0f, maxPx)
        if (!wasArmed && distance >= triggerPx) onArmed()
    }
}

@Composable
fun rememberPullToSearch(enabled: Boolean, onTrigger: () -> Unit): PullToSearchState {
    val view = LocalView.current
    val density = LocalDensity.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentTrigger by rememberUpdatedState(onTrigger)
    return remember(density) {
        PullToSearchState(
            triggerPx = with(density) { TriggerDistance.toPx() },
            maxPx = with(density) { MaxPull.toPx() },
            onArmed = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
            trigger = { currentTrigger() },
            isEnabled = { currentEnabled },
        )
    }
}

package dev.slate.launcher.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles back for a full screen and returns how far the gesture has come, for [backProgress]:
 * while the finger holds the back gesture the screen shrinks and fades a little, so it's clear
 * what letting go will do. Cancelling springs it back.
 */
@Composable
fun rememberBackProgress(enabled: Boolean = true, onBack: () -> Unit): State<Float> {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { progress.snapTo(it.progress) }
            currentOnBack()
            // Whatever comes next (the previous page, or this screen fading out) starts at rest.
            scope.launch { progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f)) }
        } catch (e: CancellationException) {
            scope.launch { progress.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f)) }
            throw e
        }
    }
    return progress.asState()
}

/** Draws a screen as far gone as the back gesture from [rememberBackProgress] says. */
fun Modifier.backProgress(progress: State<Float>): Modifier =
    graphicsLayer {
        val p = progress.value
        if (p > 0f) {
            val scale = 1f - BACK_SHRINK * p
            scaleX = scale
            scaleY = scale
            alpha = 1f - BACK_FADE * p
            shape = RoundedCornerShape(28.dp * p)
            clip = true
        }
    }

private const val BACK_SHRINK = 0.1f
private const val BACK_FADE = 0.25f

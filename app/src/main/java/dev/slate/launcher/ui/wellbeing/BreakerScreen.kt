package dev.slate.launcher.ui.wellbeing

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.slate.launcher.R
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.ui.home.AppIcon
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import dev.slate.launcher.wellbeing.Breaker
import dev.slate.launcher.wellbeing.BreakerDecision
import kotlinx.coroutines.delay

/**
 * Usage Breaker's pause: a slow breathing circle for a few seconds, then a choice of how long
 * to open the app for. "Not now" is there from the start, and it's never a hard block.
 */
@Composable
fun BreakerScreen(
    label: String,
    entry: AppEntry?,
    pause: BreakerDecision.Pause,
    usageText: (Long) -> String,
    onOpenFor: (minutes: Int) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val palette = LocalSlatePalette.current
    var remaining by remember { mutableIntStateOf(Breaker.PAUSE_SECONDS) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }
    // With system animations off, the circle simply holds still.
    val context = LocalContext.current
    val animate = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val breath = rememberInfiniteTransition(label = "breath")
    // Four seconds in, four out.
    val scale by breath.animateFloat(if (animate) 0.72f else 1f, 1f, infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.searchScrim)
            // Swallow taps so nothing underneath reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(220.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .background(palette.accent.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, palette.accent.copy(alpha = 0.5f), CircleShape),
                )
                if (entry != null) AppIcon(entry, 64.dp)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(if (pause.overLimit) R.string.breaker_over_limit_title else R.string.breaker_title),
                style = SlateText.sectionLabel,
                color = if (pause.overLimit) palette.accent else palette.onWallpaperMuted,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.breaker_open_question, label), style = SlateText.clock.copy(fontSize = 30.sp), color = palette.onWallpaper, textAlign = TextAlign.Center)
            val used = pause.usedTodayMs
            if (used != null) {
                Spacer(Modifier.height(8.dp))
                val line = if (pause.limitMinutes != null) {
                    stringResource(R.string.breaker_used_with_limit, usageText(used), usageText(pause.limitMinutes * 60_000L))
                } else {
                    stringResource(R.string.breaker_used, usageText(used))
                }
                Text(line, style = SlateText.hint, color = palette.onWallpaperMuted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
            Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                if (remaining > 0) {
                    Text(stringResource(R.string.breaker_breathe, remaining), style = SlateText.hint, color = palette.onWallpaperMuted)
                }
                if (remaining == 0) {
                    val shown = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { shown.animateTo(1f, tween(400)) }
                    // Announced when the wait ends, so screen-reader users know the choices arrived.
                    Row(
                        Modifier.graphicsLayer { alpha = shown.value }.semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Breaker.GRANTS.forEach { minutes ->
                            Text(
                                pluralStringResource(R.plurals.breaker_minutes, minutes, minutes),
                                style = SlateText.hint.copy(shadow = null),
                                color = palette.onWallpaper,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(1.dp, palette.onWallpaperMuted.copy(alpha = 0.5f), RoundedCornerShape(50))
                                    .clickable(role = Role.Button) { onOpenFor(minutes) }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.breaker_not_now), color = palette.accent, fontSize = 17.sp) }
        }
    }
}

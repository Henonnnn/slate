package dev.slate.launcher.ui.widgets

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.slate.launcher.R
import dev.slate.launcher.container
import dev.slate.launcher.data.WeatherPlace
import dev.slate.launcher.data.WidgetRef
import dev.slate.launcher.data.WidgetStack
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import dev.slate.launcher.widgets.SlateWidgetHostView
import dev.slate.launcher.widgets.WidgetRepository

/** Horizontal inset of the widget area; app widgets are told the width that's left. */
val WidgetInset = 16.dp

/**
 * One slot of the widget area. A single widget shows as is; a stack pages sideways with a
 * row of dots under it. Long-pressing any widget calls [onLongPress] with that widget.
 */
@Composable
fun WidgetStackView(
    stack: WidgetStack,
    heightDp: Int,
    weatherPlace: WeatherPlace?,
    onLongPress: (WidgetRef) -> Unit,
    onChooseWeatherPlace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = WidgetInset, vertical = 8.dp)) {
        if (stack.items.size == 1) {
            WidgetContent(stack.items.single(), heightDp, weatherPlace, onLongPress, onChooseWeatherPlace)
        } else {
            val pager = rememberPagerState { stack.items.size }
            HorizontalPager(state = pager, pageSpacing = 12.dp, key = { stack.items[it].key }) { page ->
                WidgetContent(stack.items[page], heightDp, weatherPlace, onLongPress, onChooseWeatherPlace)
            }
            PageDots(count = stack.items.size, current = pager.currentPage)
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val palette = LocalSlatePalette.current
    val label = stringResource(R.string.widget_page, current + 1, count)
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(5.dp)
                    .background(if (i == current) palette.accent else palette.onWallpaperMuted.copy(alpha = 0.35f), CircleShape),
            )
        }
    }
}

@Composable
private fun WidgetContent(
    ref: WidgetRef,
    heightDp: Int,
    weatherPlace: WeatherPlace?,
    onLongPress: (WidgetRef) -> Unit,
    onChooseWeatherPlace: () -> Unit,
) {
    val sized = Modifier.fillMaxWidth().height(heightDp.dp)
    when (ref.kind) {
        WidgetRef.Kind.App -> AppWidget(ref.appWidgetId, heightDp, { onLongPress(ref) }, sized)
        WidgetRef.Kind.Calendar -> CalendarWidget(onLongPress = { onLongPress(ref) }, modifier = sized)
        WidgetRef.Kind.Weather -> WeatherWidget(weatherPlace, onChoosePlace = onChooseWeatherPlace, onLongPress = { onLongPress(ref) }, modifier = sized)
    }
}

@Composable
private fun AppWidget(appWidgetId: Int, heightDp: Int, onLongPress: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val repository = context.container.widgetRepository
    val info = remember(appWidgetId) { repository.info(appWidgetId) }
    if (info == null) {
        // Provider uninstalled or the binding was lost: say so, keep it removable.
        val haptics = LocalHapticFeedback.current
        Box(
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(LocalSlatePalette.current.onWallpaper.copy(alpha = 0.06f))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.widget_unavailable), style = SlateText.hint, color = LocalSlatePalette.current.onWallpaperMuted)
        }
        return
    }
    BoxWithConstraints(modifier) {
        val widthDp = maxWidth.value
        AndroidView(
            factory = { ctx ->
                (repository.host.createView(ctx, appWidgetId, info) as SlateWidgetHostView).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { view ->
                (view as SlateWidgetHostView).onLongPress = onLongPress
                // Each size update is a broadcast to the provider; only send real changes.
                val size = widthDp to heightDp.toFloat()
                if (view.tag != size) {
                    view.tag = size
                    WidgetRepository.updateSize(view, size.first, size.second)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

package dev.slate.launcher.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.slate.launcher.R
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.container
import dev.slate.launcher.icons.LocalIconAppearance
import dev.slate.launcher.notifications.AppNotification
import dev.slate.launcher.notifications.NotificationRules
import dev.slate.launcher.ui.components.SlateIcon
import dev.slate.launcher.ui.components.SlateIconView
import dev.slate.launcher.ui.theme.LocalAppearance
import dev.slate.launcher.ui.components.PopupHintGlyph
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText

/**
 * Keeps the row's coordinates (a live reference, set once per placement) and only computes
 * window bounds when a launch needs them. onGloballyPositioned would recompute bounds for
 * every visible row on every scroll frame.
 */
internal class RowBounds {
    var coordinates: LayoutCoordinates? = null

    fun bounds(): Rect = coordinates?.takeIf { it.isAttached }?.boundsInWindow() ?: Rect.Zero
}

private const val PAUSED_ALPHA = 0.45f
private const val CONVERSATION_PREVIEW_LINES = 3

@Composable
fun AppRow(
    entry: AppEntry,
    onClick: (AppEntry, Rect) -> Unit,
    onLongClick: ((AppEntry) -> Unit)?,
    modifier: Modifier = Modifier,
    swipe: RowSwipe? = null,
    hasPopup: Boolean = false,
    /** Newest notification: draws the dot. */
    notification: AppNotification? = null,
    /** Also show that notification's text under the label (favourites). */
    showPreview: Boolean = false,
    /** Opens the notification in the pop-up, from the chevron beside its text. */
    onExpandNotification: ((AppEntry, Rect) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    // Silent ones keep their dot but aren't worth a line on the home screen.
    val preview = notification.takeIf { showPreview && it?.rank != NotificationRules.RANK_SILENT }
    if (swipe == null) {
        AppRowContent(entry, onClick, onLongClick, modifier, hasPopup, notification != null, preview, onExpandNotification, trailing)
    } else {
        SwipeableRow(swipe, entry, modifier) { contentModifier ->
            AppRowContent(entry, onClick, onLongClick, contentModifier, hasPopup, notification != null, preview, onExpandNotification, trailing)
        }
    }
}

@Composable
private fun AppRowContent(
    entry: AppEntry,
    onClick: (AppEntry, Rect) -> Unit,
    onLongClick: ((AppEntry) -> Unit)?,
    modifier: Modifier,
    hasPopup: Boolean,
    hasDot: Boolean,
    preview: AppNotification?,
    onExpandNotification: ((AppEntry, Rect) -> Unit)?,
    trailing: (@Composable () -> Unit)?,
) {
    val holder = remember { RowBounds() }
    val view = LocalView.current
    val longClickLabel = onLongClick?.let { stringResource(R.string.app_actions, entry.label) }
    // The dot and chevron are drawn, so screen readers get them as a state.
    val status = listOfNotNull(
        stringResource(R.string.state_new_notifications).takeIf { hasDot },
        stringResource(R.string.state_has_popup).takeIf { hasPopup },
    ).joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (status.isNotEmpty()) stateDescription = status }
            .onPlaced { holder.coordinates = it }
            .combinedClickable(
                onClickLabel = stringResource(R.string.open_app, entry.label),
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick?.let {
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        it(entry)
                    }
                },
                onClick = { onClick(entry, holder.bounds()) },
            )
            .padding(start = 28.dp, end = if (trailing != null) 16.dp else 56.dp, top = LocalAppearance.current.rowGap.dp, bottom = LocalAppearance.current.rowGap.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentAlpha = if (entry.isQuiet) PAUSED_ALPHA else 1f
        val palette = LocalSlatePalette.current
        if (LocalAppearance.current.showIcons) {
            Box(Modifier.alpha(contentAlpha)) {
                AppIcon(entry, size = 44.dp * LocalAppearance.current.iconScale)
                if (hasDot) NotificationDot(Modifier.align(Alignment.TopEnd))
            }
            Spacer(Modifier.width(20.dp))
        } else if (hasDot) {
            // No icon to sit on: the dot leads the label instead.
            NotificationDot(Modifier.alpha(contentAlpha))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f, fill = trailing != null || preview != null).alpha(contentAlpha)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.label,
                    style = SlateText.appLabel,
                    color = palette.onWallpaper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasPopup) {
                    Spacer(Modifier.width(6.dp))
                    PopupHintGlyph(palette.onWallpaperMuted)
                }
            }
            if (preview != null) {
                val previewStyle = SlateText.hint.copy(fontSize = 14.sp)
                if (preview.title.isNotBlank()) {
                    Text(text = preview.title, style = previewStyle, color = palette.onWallpaper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (preview.text.isNotBlank()) {
                    Text(
                        text = preview.text,
                        style = previewStyle,
                        color = palette.onWallpaperMuted,
                        maxLines = if (preview.isConversation) CONVERSATION_PREVIEW_LINES else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (preview != null && onExpandNotification != null) {
            val expandLabel = stringResource(R.string.notification_expand)
            IconButton(
                onClick = { onExpandNotification(entry, holder.bounds()) },
                modifier = Modifier.size(36.dp).semantics { contentDescription = expandLabel },
            ) { SlateIconView(SlateIcon.ChevronDown, palette.onWallpaper, size = 20.dp) }
        }
        trailing?.invoke()
    }
}

/** Accent dot with a dark rim so it reads on light and dark icons alike. */
@Composable
private fun NotificationDot(modifier: Modifier) {
    Box(
        modifier
            .size(13.dp)
            .background(NotificationDotRim, CircleShape)
            .padding(2.dp)
            .background(LocalSlatePalette.current.accent, CircleShape),
    )
}

private val NotificationDotRim = androidx.compose.ui.graphics.Color(0xFF151412)

@Composable
private fun FolderIcon(size: Dp, modifier: Modifier) {
    val palette = LocalSlatePalette.current
    Box(modifier.size(size).background(palette.onWallpaper.copy(alpha = 0.26f), CircleShape), contentAlignment = Alignment.Center) {
        SlateIconView(SlateIcon.Folder, palette.onWallpaper, size = size * 0.55f)
    }
}

@Composable
fun AppIcon(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    if (entry.isFolder) {
        FolderIcon(size, modifier)
        return
    }
    val cache = LocalContext.current.container.iconCache
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    // The look is part of the key: changing icon style or an override re-renders.
    val look = LocalIconAppearance.current
    val bitmap: ImageBitmap? by produceState(cache.peek(entry, sizePx), entry.key, entry.versionStamp, sizePx, look.tag, look.overrideTag(entry)) {
        // Restarts whenever a key changes (including the look); load() answers from memory when it can.
        value = cache.load(entry, sizePx)
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier.size(size))
    } else {
        // Faint disc instead of an empty gap, so rows don't pop when the icon lands.
        Box(
            modifier
                .size(size)
                .padding(size / 10)
                .background(LocalSlatePalette.current.onWallpaper.copy(alpha = 0.08f), CircleShape),
        )
    }
}

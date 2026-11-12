package dev.slate.launcher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.slate.launcher.R
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.apps.AppShortcut
import dev.slate.launcher.container
import dev.slate.launcher.notifications.AppNotification
import dev.slate.launcher.ui.components.ClearGlyph
import android.text.format.DateUtils
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** What the sheet knows about the app's shortcuts; null while loading. */
private sealed interface ShortcutState {
    data object NoAccess : ShortcutState
    data class Loaded(val shortcuts: List<AppShortcut>) : ShortcutState
}

/** Long-press actions: the app's own shortcuts first, then launcher actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsSheet(
    entry: AppEntry,
    isFavourite: Boolean,
    hasPopup: Boolean,
    notifications: List<AppNotification>,
    onOpenNotification: (AppNotification) -> Unit,
    onDismissNotification: (AppNotification) -> Unit,
    onDismiss: () -> Unit,
    onShortcut: (AppShortcut, Rect) -> Unit,
    onNeedHomeRole: () -> Unit,
    onToggleFavourite: () -> Unit,
    onReorderFavourites: () -> Unit,
    onEditPopup: () -> Unit,
    onAddToPopup: () -> Unit,
    onRename: () -> Unit,
    onChangeIcon: () -> Unit,
    onBreaker: () -> Unit,
    onHide: () -> Unit,
    onAppInfo: (Rect) -> Unit,
    onUninstall: () -> Unit,
) {
    val repository = LocalContext.current.container.shortcutRepository
    val shortcuts by produceState<ShortcutState?>(null, entry.key, entry.isQuiet) {
        value = if (!repository.hasAccess()) ShortcutState.NoAccess else ShortcutState.Loaded(repository.forApp(entry))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        // The sheet's own window would otherwise pick dark status-bar icons, invisible over the scrim.
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = false,
            isAppearanceLightNavigationBars = !isSystemInDarkTheme(),
        ),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            val headerBounds = remember { BoundsRef() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { headerBounds.value = it.boundsInWindow() }
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(entry, size = 40.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(entry.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.isWorkProfile) {
                        Text(
                            stringResource(if (entry.isQuiet) R.string.work_profile_paused else R.string.work_profile),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Divider()

            if (notifications.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                notifications.take(MAX_NOTIFICATIONS).forEach { n ->
                    NotificationRow(n, onOpen = { onOpenNotification(n) }, onClear = { onDismissNotification(n) })
                }
                if (notifications.size > MAX_NOTIFICATIONS) {
                    Text(
                        pluralStringResource(R.plurals.more_notifications, notifications.size - MAX_NOTIFICATIONS, notifications.size - MAX_NOTIFICATIONS),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Divider()
            }

            when (val state = shortcuts) {
                ShortcutState.NoAccess -> {
                    SheetNote(stringResource(R.string.shortcuts_need_home), onNeedHomeRole)
                    Divider()
                }
                is ShortcutState.Loaded -> if (state.shortcuts.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    state.shortcuts.forEach { shortcut -> ShortcutRow(shortcut, onShortcut) }
                    Spacer(Modifier.height(4.dp))
                    Divider()
                }
                // Loading: a few ms at most; don't flash a placeholder.
                null -> Unit
            }

            Spacer(Modifier.height(4.dp))
            // Private apps stay in their own section: nothing that would pull them out of it.
            if (!entry.isPrivate) SheetAction(stringResource(if (isFavourite) R.string.remove_from_favourites else R.string.add_to_favourites), onToggleFavourite)
            if (isFavourite) SheetAction(stringResource(R.string.reorder_favourites), onReorderFavourites)
            if (!entry.isPrivate) SheetAction(stringResource(if (hasPopup) R.string.popup_edit else R.string.popup_create), onEditPopup)
            if (!entry.isPrivate) SheetAction(stringResource(R.string.add_to_popup), onAddToPopup)
            SheetAction(stringResource(R.string.rename), onRename)
            SheetAction(stringResource(R.string.change_icon), onChangeIcon)
            SheetAction(stringResource(R.string.breaker_action), onBreaker)
            if (!entry.isPrivate) SheetAction(stringResource(R.string.hide), onHide)
            SheetAction(stringResource(R.string.app_info)) { onAppInfo(headerBounds.value) }
            when (entry.uninstall) {
                AppEntry.Uninstall.App -> SheetAction(stringResource(R.string.uninstall), onUninstall)
                AppEntry.Uninstall.Updates -> SheetAction(stringResource(R.string.uninstall_updates), onUninstall)
                AppEntry.Uninstall.None -> Unit
            }
        }
    }
}

private class BoundsRef {
    var value: Rect = Rect.Zero
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

@Composable
private fun ShortcutRow(shortcut: AppShortcut, onClick: (AppShortcut, Rect) -> Unit) {
    val bounds = remember { BoundsRef() }
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds.value = it.boundsInWindow() }
            .clickable(role = Role.Button) { onClick(shortcut, bounds.value) }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShortcutIcon(shortcut, 28.dp)
        Spacer(Modifier.width(16.dp))
        Text(shortcut.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ShortcutIcon(shortcut: AppShortcut, size: Dp, modifier: Modifier = Modifier) {
    val cache = LocalContext.current.container.iconCache
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap: ImageBitmap? by produceState(cache.peekShortcut(shortcut, sizePx), shortcut.key, sizePx) {
        if (value == null) value = cache.loadShortcut(shortcut, sizePx)
    }
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size).padding(size / 10).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape))
    }
}

private const val MAX_NOTIFICATIONS = 3

@Composable
private fun NotificationRow(n: AppNotification, onOpen: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    n.title.ifBlank { n.text },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    DateUtils.getRelativeTimeSpanString(n.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (n.title.isNotBlank() && n.text.isNotBlank()) {
                Text(n.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (n.clearable) {
            val label = stringResource(R.string.clear_notification)
            IconButton(onClick = onClear, modifier = Modifier.semantics { contentDescription = label }) {
                ClearGlyph(MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SheetNote(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

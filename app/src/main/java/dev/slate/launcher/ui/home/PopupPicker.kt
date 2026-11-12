package dev.slate.launcher.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.slate.launcher.R
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.apps.AppShortcut
import dev.slate.launcher.container
import dev.slate.launcher.data.PopupItem
import dev.slate.launcher.search.FuzzyMatcher
import dev.slate.launcher.ui.components.CheckGlyph

/** Shortcut matches need a substring-or-better score, as in search. */
private const val STRONG_MATCH = 600

/**
 * Chooses what a pop-up holds. Tapping toggles an item in or out, so several can be added in
 * one go. The owner's own shortcuts come first, then every app; typing filters apps and
 * also searches all shortcuts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopupPickerSheet(
    owner: AppEntry,
    apps: List<AppEntry>,
    selected: Set<PopupItem>,
    onToggle: (PopupItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val repository = LocalContext.current.container.shortcutRepository
    var query by rememberSaveable { mutableStateOf("") }
    // Null until loaded: the list waits for it, so these rows never get inserted above the apps
    // after the fact (the list would keep the apps in view and push them off the top).
    val ownShortcuts by produceState<List<AppShortcut>?>(null, owner.key) {
        value = if (!owner.isFolder && repository.hasAccess()) repository.forApp(owner) else emptyList()
    }
    val allShortcuts by produceState(emptyList<AppShortcut>()) {
        value = if (repository.hasAccess()) repository.all() else emptyList()
    }
    val labels = remember(apps) { apps.associate { it.packageName to it.label } }

    val q = FuzzyMatcher.normalize(query)
    val shownApps = remember(apps, q) {
        val candidates = apps.filter { it.key != owner.key }
        if (q.isEmpty()) {
            candidates
        } else {
            candidates.mapNotNull { a -> FuzzyMatcher.scoreNormalized(q, FuzzyMatcher.normalize(a.label))?.let { a to it } }
                .sortedByDescending { it.second }
                .map { it.first }
        }
    }
    val shownShortcuts = remember(ownShortcuts, allShortcuts, q) {
        if (q.isEmpty()) {
            ownShortcuts.orEmpty()
        } else {
            allShortcuts.filter { (FuzzyMatcher.score(q, it.label) ?: 0) >= STRONG_MATCH }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = false,
            isAppearanceLightNavigationBars = !isSystemInDarkTheme(),
        ),
    ) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding().imePadding()) {
            Text(
                stringResource(R.string.popup_for, owner.label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.popup_picker_hint)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            val shortcutsLabel = if (q.isEmpty()) stringResource(R.string.popup_from_app, owner.label) else stringResource(R.string.popup_shortcuts)
            val appsLabel = stringResource(R.string.all_apps)
            if (ownShortcuts != null) LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (shownShortcuts.isNotEmpty()) {
                    pickerLabel("shortcuts-label", shortcutsLabel)
                    items(shownShortcuts, key = { "s:" + it.key }) { shortcut ->
                        val item = PopupItem.shortcut(shortcut.key)
                        PickerRow(
                            title = shortcut.label,
                            subtitle = if (q.isEmpty()) null else labels[shortcut.packageName],
                            checked = item in selected,
                            onClick = { onToggle(item) },
                            icon = { ShortcutIcon(shortcut, 32.dp) },
                        )
                    }
                }
                pickerLabel("apps-label", appsLabel)
                items(shownApps, key = { "a:" + it.key }) { app ->
                    val item = PopupItem.app(app.key)
                    PickerRow(
                        title = app.label,
                        subtitle = null,
                        checked = item in selected,
                        onClick = { onToggle(item) },
                        icon = { AppIcon(app, 32.dp) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.pickerLabel(key: String, text: String) {
    item(key = key) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun PickerRow(title: String, subtitle: String?, checked: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { selected = checked }
            .clickable(role = Role.Checkbox, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (checked) CheckGlyph(MaterialTheme.colorScheme.primary)
    }
}

/** "Add to pop-up…" from the long-press sheet: pick which favourite's pop-up gets [entry]. */
@Composable
fun AddToPopupDialog(entry: AppEntry, favourites: List<AppEntry>, onPick: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    val owners = favourites.filter { it.key != entry.key }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_popup_title, entry.label)) },
        text = {
            if (owners.isEmpty()) {
                Text(stringResource(R.string.add_to_popup_none))
            } else {
                LazyColumn {
                    items(owners, key = { it.key }) { owner ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onPick(owner) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(owner, 32.dp)
                            Spacer(Modifier.width(16.dp))
                            Text(owner.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface) }
        },
    )
}

package dev.slate.launcher.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.slate.launcher.R
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.ui.widgets.SheetRow
import dev.slate.launcher.ui.widgets.SheetTitle
import dev.slate.launcher.ui.widgets.SlateSheet

/** Names a new pop-up folder and picks where it goes. */
@Composable
fun CreateFolderDialog(onCreate: (name: String, inFavourites: Boolean) -> Unit, onDismiss: () -> Unit) {
    val fallback = stringResource(R.string.folder_default_name)
    var name by rememberSaveable { mutableStateOf("") }
    var inFavourites by rememberSaveable { mutableStateOf(true) }
    fun create() = onCreate(name.trim().ifEmpty { fallback }, inFavourites)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.folder_create)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(fallback) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                )
                LocationRow(stringResource(R.string.favourites), inFavourites) { inFavourites = true }
                LocationRow(stringResource(R.string.all_apps), !inFavourites) { inFavourites = false }
            }
        },
        confirmButton = { TextButton(onClick = ::create) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface) } },
    )
}

@Composable
private fun LocationRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.padding(top = 8.dp).selectable(selected, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Long-press on a folder's row. */
@Composable
fun FolderSheet(entry: AppEntry, onEdit: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    SlateSheet(onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            SheetTitle(entry.label)
            SheetRow(stringResource(R.string.popup_edit), onEdit)
            SheetRow(stringResource(R.string.rename), onRename)
            SheetRow(stringResource(R.string.folder_delete), onDelete)
        }
    }
}

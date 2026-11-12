package dev.slate.launcher.ui.appearance

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.slate.launcher.R
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.container
import dev.slate.launcher.data.IconOverride
import dev.slate.launcher.icons.IconPack
import dev.slate.launcher.icons.IconPackInfo
import dev.slate.launcher.search.FuzzyMatcher
import dev.slate.launcher.ui.home.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Icon Assistant: give one app an icon from any installed pack. The pack's own mapping for this
 * app and icons whose names match the app's label are suggested first; the rest of the pack is
 * searchable below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(
    entry: AppEntry,
    current: IconOverride?,
    preferredPack: String?,
    onPick: (IconOverride?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repository = context.container.iconPacks
    val packs by produceState<List<IconPackInfo>?>(null) { value = withContext(Dispatchers.IO) { repository.installed() } }
    var chosen by rememberSaveable { mutableStateOf(current?.pack ?: preferredPack) }
    var query by rememberSaveable { mutableStateOf("") }
    val packName = chosen?.takeIf { c -> packs?.any { it.packageName == c } == true } ?: packs?.firstOrNull()?.packageName
    val pack by produceState<IconPack?>(null, packName) { value = packName?.let { withContext(Dispatchers.IO) { repository.load(it) } } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        properties = ModalBottomSheetProperties(isAppearanceLightStatusBars = false, isAppearanceLightNavigationBars = !isSystemInDarkTheme()),
    ) {
        Column(Modifier.fillMaxHeight(0.88f).navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(entry, 40.dp)
                Text(entry.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 16.dp))
                if (current != null) {
                    TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.icon_original), color = MaterialTheme.colorScheme.primary) }
                }
            }
            val list = packs
            if (list != null && list.isEmpty()) {
                Text(
                    stringResource(R.string.icon_needs_pack),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
                return@Column
            }
            if ((list?.size ?: 0) > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.orEmpty().forEach { p ->
                        FilterChip(
                            selected = p.packageName == packName,
                            onClick = { chosen = p.packageName },
                            label = { Text(p.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedLabelColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.icon_picker_hint)) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, cursorColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            val p = pack ?: return@Column
            val (suggested, rest) = remember(p, query, entry.key) { iconLists(p, entry, query) }
            LazyVerticalGrid(columns = GridCells.Adaptive(64.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                if (suggested.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "suggested") { GridLabel(stringResource(R.string.icon_suggested)) }
                    items(suggested, key = { "s:$it" }) { name -> IconCell(p, name, current?.drawable == name && current.pack == p.packageName) { onPick(IconOverride(p.packageName, name)) } }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "all") { GridLabel(stringResource(R.string.icon_all, list?.firstOrNull { it.packageName == p.packageName }?.label.orEmpty())) }
                items(rest, key = { "a:$it" }) { name -> IconCell(p, name, current?.drawable == name && current.pack == p.packageName) { onPick(IconOverride(p.packageName, name)) } }
            }
        }
    }
}

/** Suggested: the pack's own mapping for this app, then names matching its label. Rest: everything else (filtered). */
private fun iconLists(pack: IconPack, entry: AppEntry, query: String): Pair<List<String>, List<String>> {
    val mapped = pack.filter.drawableFor(entry.packageName, entry.component.className)
    val words = FuzzyMatcher.normalize(entry.systemLabel).split(' ').filter { it.length >= 3 }
    val byLabel = pack.allDrawables.filter { name -> words.any { name.contains(it) } }.take(24)
    val suggested = (listOfNotNull(mapped) + byLabel).distinct()
    val q = FuzzyMatcher.normalize(query).replace(' ', '_')
    val rest = pack.allDrawables.let { all -> if (q.isEmpty()) all else all.filter { it.contains(q) } }.filterNot { it in suggested }
    return (if (q.isEmpty()) suggested else suggested.filter { it.contains(q) }) to rest
}

@Composable
private fun GridLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun IconCell(pack: IconPack, name: String, selected: Boolean, onClick: () -> Unit) {
    val density = LocalResources.current.displayMetrics.densityDpi
    val px = with(LocalDensity.current) { 48.dp.roundToPx() }
    val image by produceState<ImageBitmap?>(null, pack, name) {
        value = withContext(Dispatchers.IO) { pack.drawable(name, density)?.toBitmap(px, px)?.asImageBitmap() }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = name.replace('_', ' ') }
            .then(if (selected) Modifier.padding(0.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
        if (selected) Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

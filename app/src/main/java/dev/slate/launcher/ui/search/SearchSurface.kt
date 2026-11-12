package dev.slate.launcher.ui.search

import androidx.compose.runtime.CompositionLocalProvider
import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.slate.launcher.R
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.search.SearchResult
import dev.slate.launcher.search.SettingsCatalog
import dev.slate.launcher.ui.components.ClearGlyph
import dev.slate.launcher.ui.home.AppRow
import dev.slate.launcher.ui.home.ShortcutIcon
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import kotlinx.coroutines.android.awaitFrame

/**
 * Full-screen search over the wallpaper. The field sits at the bottom, just above the
 * keyboard, and results grow upwards from it so the best match is nearest the thumb.
 * Enter opens the best match.
 */
@Composable
fun SearchSurface(
    viewModel: SearchViewModel,
    onClose: () -> Unit,
    onLaunchApp: (AppEntry, Rect) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val wallpaperPalette = LocalSlatePalette.current
    val palette = remember(wallpaperPalette) { wallpaperPalette.onDark() }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.onOpened()
        awaitFrame()
        focus.requestFocus()
        keyboard?.show()
    }
    BackHandler(onBack = onClose)

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onContactsPermission(granted)
    }

    fun open(result: SearchResult, bounds: Rect = Rect.Zero) {
        val handled = when (result) {
            is SearchResult.App -> {
                onLaunchApp(result.entry, bounds)
                true
            }
            is SearchResult.Shortcut -> viewModel.launchShortcut(result.shortcut)
            is SearchResult.Setting -> if (result.item.id == SettingsCatalog.TORCH) {
                // Stays open: the row shows the new state.
                if (!viewModel.toggleTorch()) toast(context, R.string.torch_busy)
                return
            } else {
                start(context, result.item.intent!!())
            }
            is SearchResult.Contact -> start(context, result.hit.viewIntent())
            is SearchResult.Web -> webSearch(context, result.query)
            is SearchResult.Calculation -> {
                copy(context, result.value)
                return
            }
            is SearchResult.Conversion -> {
                copy(context, result.value)
                return
            }
        }
        if (handled) onClose() else toast(context, R.string.action_failed)
    }

    // Search brings its own dark ground, so its text is light whatever the wallpaper's is.
    CompositionLocalProvider(LocalSlatePalette provides remember(palette) { palette.onDark() }) {
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.searchScrim),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
            val listState = rememberLazyListState()
            // Keep the best match in view as results change under the user's typing.
            LaunchedEffect(state.results.firstOrNull()?.key) { listState.scrollToItem(0) }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                items(state.results, key = { it.key }) { result ->
                    ResultRow(
                        result = result,
                        torchOn = state.torchOn,
                        onOpen = ::open,
                        onAppLongPress = onAppLongPress,
                        modifier = Modifier.animateItem(),
                    )
                }
                // Reverse layout: last item sits furthest from the field, below the results' priority.
                if (state.askForContacts) {
                    item(key = "contacts-prompt") {
                        ContactsPrompt(
                            onAllow = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                            onDismiss = viewModel::dismissContactsPrompt,
                        )
                    }
                }
            }
            SearchField(
                query = query,
                onQuery = viewModel::setQuery,
                onGo = { state.results.firstOrNull()?.let { open(it) } },
                modifier = Modifier.focusRequester(focus),
            )
        }
    }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onGo: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalSlatePalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.search_hint), style = SearchFieldStyle, color = palette.onWallpaperMuted)
            }
            val label = stringResource(R.string.search)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = SearchFieldStyle.copy(color = palette.onWallpaper),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { contentDescription = label },
            )
        }
        if (query.isNotEmpty()) {
            val clear = stringResource(R.string.clear_search)
            IconButton(onClick = { onQuery("") }, modifier = Modifier.semantics { contentDescription = clear }) {
                ClearGlyph(palette.onWallpaperMuted)
            }
        }
    }
}

private val SearchFieldStyle: TextStyle
    @Composable get() = SlateText.appLabel.copy(fontSize = 24.sp, shadow = null)

@Composable
private fun ResultRow(
    result: SearchResult,
    torchOn: Boolean?,
    onOpen: (SearchResult, Rect) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (result) {
        is SearchResult.App -> AppRow(
            entry = result.entry,
            onClick = { _, bounds -> onOpen(result, bounds) },
            onLongClick = onAppLongPress,
            modifier = modifier,
        )
        is SearchResult.Calculation -> AnswerRow(result.expression, result.value, { onOpen(result, Rect.Zero) }, modifier)
        is SearchResult.Conversion -> AnswerRow(result.source, result.value, { onOpen(result, Rect.Zero) }, modifier)
        is SearchResult.Shortcut -> TextResult(
            title = result.shortcut.label,
            subtitle = result.appLabel,
            onClick = { onOpen(result, Rect.Zero) },
            modifier = modifier,
            leading = { ShortcutIcon(result.shortcut, 32.dp, Modifier.padding(6.dp)) },
        )
        is SearchResult.Setting -> {
            val isTorch = result.item.id == SettingsCatalog.TORCH
            TextResult(
                title = result.label,
                subtitle = when {
                    isTorch && torchOn == true -> stringResource(R.string.torch_on)
                    isTorch -> stringResource(R.string.torch_off)
                    else -> stringResource(R.string.system_setting)
                },
                onClick = { onOpen(result, Rect.Zero) },
                modifier = modifier,
                leading = { Monogram(result.label, highlighted = isTorch && torchOn == true) },
            )
        }
        is SearchResult.Contact -> TextResult(
            title = result.hit.name,
            subtitle = stringResource(R.string.contact),
            onClick = { onOpen(result, Rect.Zero) },
            modifier = modifier,
            leading = { Monogram(result.hit.name) },
        )
        is SearchResult.Web -> TextResult(
            title = stringResource(R.string.search_web_for, result.query),
            subtitle = null,
            onClick = { onOpen(result, Rect.Zero) },
            modifier = modifier,
            leading = { Spacer(Modifier.size(44.dp)) },
        )
    }
}

/** Calculator and conversion answers: the number is the headline, the question sits above it. */
@Composable
private fun AnswerRow(question: String, answer: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalSlatePalette.current
    val copyLabel = stringResource(R.string.copy_result)
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = copyLabel, role = Role.Button, onClick = onCopy)
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Text(question, style = SlateText.hint, color = palette.onWallpaperMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "= $answer",
            style = SlateText.clock.copy(fontSize = 40.sp),
            color = palette.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TextResult(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
) {
    val palette = LocalSlatePalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 28.dp, end = 28.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = SlateText.appLabel, color = palette.onWallpaper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = SlateText.hint.copy(fontSize = 13.sp), color = palette.onWallpaperMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Monogram(text: String, highlighted: Boolean = false) {
    val palette = LocalSlatePalette.current
    Box(
        Modifier
            .size(44.dp)
            .padding(4.dp)
            .background(if (highlighted) palette.accent else palette.onWallpaper.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.trim().take(1).uppercase(),
            style = SlateText.sectionLabel.copy(fontSize = 15.sp, shadow = null),
            color = if (highlighted) Color.Black else palette.onWallpaper,
            // Decorative initial: the row's title already says it.
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun ContactsPrompt(onAllow: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalSlatePalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.contacts_prompt_title), style = SlateText.appLabel.copy(fontSize = 17.sp), color = palette.onWallpaper)
        Text(stringResource(R.string.contacts_prompt_body), style = SlateText.hint.copy(fontSize = 14.sp), color = palette.onWallpaperMuted)
        Row(Modifier.padding(top = 4.dp)) {
            TextButton(onClick = onAllow) { Text(stringResource(R.string.allow), color = palette.accent) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.no_thanks), color = palette.onWallpaperMuted) }
        }
    }
}

private fun start(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** The user's search app if one handles web search, else their browser. */
private fun webSearch(context: Context, query: String): Boolean =
    start(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)) ||
        start(context, Intent(Intent.ACTION_VIEW, ("https://duckduckgo.com/?q=" + Uri.encode(query)).toUri()))

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
    // Android 13+ shows its own copy confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast(context, R.string.copied)
}

private fun toast(context: Context, res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

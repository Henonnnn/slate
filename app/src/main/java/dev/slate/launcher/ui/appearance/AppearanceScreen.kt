package dev.slate.launcher.ui.appearance

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import dev.slate.launcher.data.TextTone
import dev.slate.launcher.data.PanelTone
import dev.slate.launcher.data.SavedTheme
import dev.slate.launcher.ui.components.backProgress
import dev.slate.launcher.ui.components.rememberBackProgress
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import dev.slate.launcher.ui.components.SlateIcon
import dev.slate.launcher.ui.components.SlateIconView
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.slate.launcher.R
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.container
import dev.slate.launcher.data.AccentChoice
import dev.slate.launcher.data.FontChoice
import dev.slate.launcher.data.IconShape
import dev.slate.launcher.data.IconStyle
import dev.slate.launcher.data.Presets
import dev.slate.launcher.data.ThemeSettings
import dev.slate.launcher.floating.FloatingSources
import dev.slate.launcher.icons.IconAppearance
import dev.slate.launcher.icons.IconPackInfo
import dev.slate.launcher.icons.LocalIconAppearance
import dev.slate.launcher.ui.home.AppRow
import dev.slate.launcher.ui.theme.Accent
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import dev.slate.launcher.ui.theme.SlateTheme
import dev.slate.launcher.ui.theme.fontFamilyFor
import dev.slate.launcher.ui.theme.wallpaperPrimaryColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Appearance, drawn over the wallpaper with the home screen's own scrim so dimming, accent and
 * type are previewed exactly. The screen wears the draft theme; taps save at once, sliders when
 * the finger lifts.
 */
@Composable
fun AppearanceScreen(
    theme: ThemeSettings,
    savedThemes: List<SavedTheme>,
    onSaveTheme: (String) -> Unit,
    onDeleteTheme: (String) -> Unit,
    sample: List<AppEntry>,
    onChange: (ThemeSettings) -> Unit,
    onPreset: (String) -> Unit,
    onClock: () -> Unit,
    onClose: () -> Unit,
) {
    val backModifier = Modifier.backProgress(rememberBackProgress(onBack = onClose))
    val context = LocalContext.current
    var draft by remember { mutableStateOf(theme) }
    LaunchedEffect(theme) { draft = theme }
    val wallpaperColor by remember { wallpaperPrimaryColor(context) }.collectAsStateWithLifecycle(null)
    val packs by produceState<List<IconPackInfo>?>(null) {
        value = withContext(Dispatchers.IO) { context.container.iconPacks.installed() }
    }

    fun commit(next: ThemeSettings) {
        draft = next
        onChange(next)
    }
    fun edit(transform: (ThemeSettings) -> ThemeSettings) = commit(Presets.edited(transform(draft)))

    var naming by remember { mutableStateOf(false) }
    if (naming) {
        NameLookDialog(onDismiss = { naming = false }) { name ->
            onSaveTheme(name)
            naming = false
        }
    }

    val outerIcons = LocalIconAppearance.current
    SlateTheme(draft, wallpaperColor) {
        val palette = LocalSlatePalette.current
        // Preview icons with the draft's style too; overrides stay as they are.
        val previewIcons = IconAppearance(draft.iconStyle, draft.iconPack, draft.iconShape, Accent.resolve(draft.accent, wallpaperColor, draft.accentHue), outerIcons.overrides)
        androidx.compose.runtime.CompositionLocalProvider(LocalIconAppearance provides previewIcons) {
            PanelBackdrop(modifier = backModifier) {
                ScreenTitle(stringResource(R.string.appearance), onClose)
                LazyColumn(Modifier.weight(1f)) {
                    item(key = "preview") {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            sample.take(3).forEach { AppRow(entry = it, onClick = { _, _ -> }, onLongClick = null) }
                        }
                    }
                    section("presets", R.string.appearance_presets) {
                        ChipRow {
                            Presets.all.forEach { p ->
                                Chip(presetLabel(p.id), selected = draft.preset == p.id) { onPreset(p.id) }
                            }
                        }
                    }
                    section("saved", R.string.appearance_saved) {
                        // Looks kept under a name, as Niagara's "My themes". Tapping one puts it on.
                        val worn = savedThemes.firstOrNull { it.theme == draft }
                        ChipRow {
                            savedThemes.forEach { saved ->
                                Chip(saved.name, selected = saved === worn) { commit(saved.theme) }
                            }
                            Chip(stringResource(R.string.appearance_save_look), selected = false) { naming = true }
                        }
                        if (worn != null) {
                            LinkRow(stringResource(R.string.appearance_delete_look, worn.name), icon = SlateIcon.Eye) { onDeleteTheme(worn.name) }
                        }
                    }
                    section("accent", R.string.appearance_accent) {
                        ChipRow {
                            AccentChoice.entries.forEach { choice ->
                                val argb = Accent.resolve(choice, wallpaperColor, draft.accentHue)
                                Swatch(Color(argb), wallpaper = choice == AccentChoice.Wallpaper, selected = draft.accent == choice, label = accentLabel(choice)) {
                                    edit { it.copy(accent = choice) }
                                }
                            }
                        }
                        if (draft.accent == AccentChoice.Custom) {
                            AppearanceSlider(draft.accentHue, 0f..360f, step = 5f, format = { "${it.toInt()}°" }, onPreview = { draft = draft.copy(accentHue = it) }) { v ->
                                edit { it.copy(accentHue = v) }
                            }
                        }
                    }
                    section("panel", R.string.appearance_panel) {
                        ChipRow {
                            PanelTone.entries.forEach { tone ->
                                Chip(panelLabel(tone), selected = draft.panel == tone) { edit { it.copy(panel = tone) } }
                            }
                        }
                        Note(stringResource(R.string.appearance_panel_note))
                    }
                    section("text-tone", R.string.appearance_text_tone) {
                        ChipRow {
                            TextTone.entries.forEach { tone ->
                                Chip(textToneLabel(tone), selected = draft.wallpaperText == tone) { edit { it.copy(wallpaperText = tone) } }
                            }
                        }
                    }
                    section("font", R.string.appearance_font) {
                        ChipRow {
                            FontChoice.entries.forEach { f ->
                                Chip(fontLabel(f), selected = draft.font == f, style = SlateText.appLabel.copy(fontFamily = fontFamilyFor(f), fontSize = 17.sp, shadow = null)) {
                                    edit { it.copy(font = f) }
                                }
                            }
                        }
                    }
                    section("size", R.string.appearance_text_size) {
                        AppearanceSlider(draft.textScale, ThemeSettings.MIN_SCALE..ThemeSettings.MAX_SCALE, step = 0.05f, format = { "${(it * 100).toInt()}%" }, onPreview = { draft = draft.copy(textScale = it) }) { v ->
                            edit { it.copy(textScale = v) }
                        }
                    }
                    section("spacing", R.string.appearance_spacing) {
                        AppearanceSlider(draft.rowGap, ThemeSettings.MIN_ROW_GAP..ThemeSettings.MAX_ROW_GAP, step = 1f, format = { "${it.toInt()} dp" }, onPreview = { draft = draft.copy(rowGap = it) }) { v ->
                            edit { it.copy(rowGap = v) }
                        }
                    }
                    section("dim", R.string.appearance_dimming) {
                        AppearanceSlider(draft.dimming, 0f..ThemeSettings.MAX_DIMMING, step = 0.05f, format = { "${(it * 100).toInt()}%" }, onPreview = { draft = draft.copy(dimming = it) }) { v ->
                            edit { it.copy(dimming = v) }
                        }
                    }
                    section("icons", R.string.appearance_icons) {
                        ToggleRow(stringResource(R.string.appearance_show_icons), draft.showIcons) { on -> edit { it.copy(showIcons = on) } }
                        if (draft.showIcons) {
                            AppearanceSlider(draft.iconScale, ThemeSettings.MIN_ICON_SCALE..ThemeSettings.MAX_ICON_SCALE, step = 0.05f, format = { "${(it * 100).toInt()}%" }, onPreview = { draft = draft.copy(iconScale = it) }) { v ->
                                edit { it.copy(iconScale = v) }
                            }
                        }
                        ChipRow {
                            IconStyle.entries.forEach { s ->
                                Chip(iconStyleLabel(s), selected = draft.iconStyle == s) {
                                    edit { t ->
                                        // Picking "Icon pack" with none chosen selects the first installed one.
                                        val pack = if (s == IconStyle.Pack) t.iconPack ?: packs?.firstOrNull()?.packageName else t.iconPack
                                        t.copy(iconStyle = s, iconPack = pack)
                                    }
                                }
                            }
                        }
                        when {
                            draft.iconStyle == IconStyle.Themed && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                                Note(stringResource(R.string.appearance_themed_needs_13))
                            draft.iconStyle == IconStyle.Themed -> Note(stringResource(R.string.appearance_themed_note))
                            draft.iconStyle == IconStyle.Pack -> {
                                val list = packs
                                if (list != null && list.isEmpty()) {
                                    Note(stringResource(R.string.appearance_no_packs))
                                } else {
                                    list?.forEach { pack ->
                                        RadioRow(pack.label, selected = draft.iconPack == pack.packageName) {
                                            edit { it.copy(iconPack = pack.packageName) }
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                    section("shape", R.string.appearance_icon_shape) {
                        ChipRow {
                            IconShape.entries.forEach { s ->
                                ShapeChip(s, selected = draft.iconShape == s, label = shapeLabel(s)) { edit { it.copy(iconShape = s) } }
                            }
                        }
                        Note(stringResource(R.string.appearance_shape_note))
                    }
                    section("clock", R.string.clock_title) {
                        LinkRow(stringResource(R.string.clock_customise), icon = SlateIcon.Clock, onClick = onClock)
                    }
                    item(key = "bottom") { Spacer(Modifier.height(48.dp)) }
            }
            }
        }
    }
}

/**
 * Behind every settings-style screen. Screens that preview something over the wallpaper keep it
 * showing, with a floor of darkness for legibility; [solid] ones stand on the accent's own dark ground.
 */
@Composable
internal fun PanelBackdrop(modifier: Modifier = Modifier, solid: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalSlatePalette.current
    Box(modifier.fillMaxSize().background(palette.scrim)) {
        Box(Modifier.fillMaxSize().background(if (solid) palette.ground else Color.Black.copy(alpha = 0.45f)))
        // These screens bring their own dark ground, so their text is light whatever the wallpaper's is.
        CompositionLocalProvider(LocalFlatRows provides solid, LocalSlatePalette provides remember(palette) { palette.onDark() }) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) { content() }
        }
    }
}

/** Title with Done, fixed above a scrolling list so the way out never scrolls away. */
@Composable
internal fun ScreenTitle(title: String, onClose: () -> Unit, closeLabel: String = stringResource(R.string.done), subtitle: String? = null) {
    val palette = LocalSlatePalette.current
    Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = SlateText.clock.copy(fontSize = 34.sp, shadow = null),
                color = palette.onWallpaper,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(
                closeLabel,
                style = RowLabel.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = palette.ground,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(palette.accent)
                    .clickable(role = Role.Button, onClick = onClose)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        if (subtitle != null) {
            Text(subtitle, style = SlateText.hint.copy(shadow = null), color = palette.onWallpaperMuted, modifier = Modifier.padding(end = 16.dp, top = 2.dp))
        }
    }
}

private val GroupShape = RoundedCornerShape(26.dp)
private val PieceShape = RoundedCornerShape(6.dp)
private val PieceGap = 2.dp

/** True on a solid ground; over the wallpaper the cards are a little see-through so the preview stays in sight. */
private val LocalFlatRows = staticCompositionLocalOf { false }

/**
 * The surface of one piece of a group. A group is a stack of these with hairline gaps, clipped
 * to one rounded card, so every row reads as its own tile.
 */
@Composable
private fun Modifier.piece(): Modifier {
    val palette = LocalSlatePalette.current
    return fillMaxWidth().clip(PieceShape).background(if (LocalFlatRows.current) palette.groundRaised else palette.groundRaised.copy(alpha = 0.88f))
}

/** A card of rows with an optional label above it. */
@Composable
internal fun Group(title: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp)) {
        if (title != null) {
            Text(
                title,
                style = SlateText.hint.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = null),
                color = LocalSlatePalette.current.accent,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp).semantics { heading() },
            )
        }
        Column(Modifier.fillMaxWidth().clip(GroupShape), verticalArrangement = Arrangement.spacedBy(PieceGap)) { content() }
    }
}

internal fun LazyListScope.section(key: String, title: Int, content: @Composable () -> Unit) {
    item(key = key) { Group(stringResource(title), content) }
}

/** The row every setting shares: an icon on its own disc, a title with an optional summary, and something at the end. */
@Composable
private fun RowBody(label: String, summary: String?, icon: SlateIcon?, modifier: Modifier, tint: Color? = null, trailing: @Composable () -> Unit) {
    val palette = LocalSlatePalette.current
    val colour = tint ?: palette.accent
    Row(
        Modifier
            .piece()
            .then(modifier)
            .heightIn(min = if (summary != null) 76.dp else 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(40.dp).background(colour.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                SlateIconView(icon, colour, size = 22.dp)
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = RowLabel, color = palette.onWallpaper)
            if (summary != null) {
                Text(summary, style = SlateText.hint.copy(fontSize = 13.5.sp, shadow = null), color = palette.onWallpaperMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing()
    }
}

/** The current value of a row, in a small tonal pill. */
@Composable
private fun ValuePill(text: String, lit: Boolean = true) {
    val palette = LocalSlatePalette.current
    Text(
        text,
        style = SlateText.hint.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, shadow = null),
        color = if (lit) palette.accent else palette.onWallpaperMuted,
        maxLines = 1,
        modifier = Modifier.padding(start = 12.dp).clip(CircleShape).background(palette.groundHigh).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** A setting with several answers: shows the current one and unfolds the rest as chips on tap. */
@Composable
internal fun <T> ChoiceRow(label: String, options: List<T>, selected: T, optionLabel: @Composable (T) -> String, icon: SlateIcon? = null, summary: String? = null, tint: Color? = null, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.piece().animateContentSize(spring(dampingRatio = 0.85f, stiffness = 500f))) {
        RowBody(label, summary, icon, Modifier.clickable(role = Role.Button, onClick = { open = !open }), tint) {
            ValuePill(optionLabel(selected))
        }
        if (open) {
            ChipRow {
                options.forEach { option ->
                    Chip(optionLabel(option), selected = option == selected) {
                        onSelect(option)
                        open = false
                    }
                }
            }
        }
    }
}

/** A row that goes somewhere, with an optional value on the right. */
@Composable
internal fun LinkRow(label: String, summary: String? = null, trailing: String? = null, icon: SlateIcon? = null, tint: Color? = null, onClick: () -> Unit) {
    val palette = LocalSlatePalette.current
    RowBody(label, summary, icon, Modifier.clickable(role = Role.Button, onClick = onClick), tint) {
        if (trailing != null) ValuePill(trailing, lit = false)
        SlateIconView(SlateIcon.ChevronRight, palette.onWallpaperMuted.copy(alpha = 0.5f), Modifier.padding(start = 6.dp), size = 18.dp)
    }
}

/** A permission or status row: the state sits at the end, lit when it's on. */
@Composable
internal fun StatusRow(label: String, summary: String, on: Boolean, onText: String, offText: String, icon: SlateIcon, onClick: () -> Unit) {
    RowBody(label, summary, icon, Modifier.clickable(role = Role.Button, onClick = onClick)) {
        ValuePill(if (on) onText else offText, lit = on)
    }
}

private val RowInset = 16.dp
private val RowLabel: TextStyle
    @Composable get() = SlateText.appLabel.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium, shadow = null)

@Composable
internal fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.piece().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** A filled chip that squares its corners a little when chosen, as Material 3 Expressive's do. */
@Composable
internal fun Chip(label: String, selected: Boolean, style: TextStyle? = null, onClick: () -> Unit) {
    val palette = LocalSlatePalette.current
    val corner by animateFloatAsState(if (selected) 30f else 50f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "chipCorner")
    val ground by animateColorAsState(if (selected) palette.accent else palette.groundHigh, tween(160), label = "chipGround")
    val shape = RoundedCornerShape(corner.toInt().coerceIn(0, 50))
    Text(
        label,
        style = (style ?: SlateText.hint.copy(shadow = null)).copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
        color = if (selected) palette.ground else palette.onWallpaper,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(ground)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

@Composable
private fun Swatch(color: Color, wallpaper: Boolean, selected: Boolean, label: String, onClick: () -> Unit) {
    val palette = LocalSlatePalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick).padding(4.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .border(2.dp, if (selected) palette.onWallpaper else Color.Transparent, CircleShape)
                .padding(5.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative: the label below already says it.
            if (wallpaper) Text("W", style = SlateText.sectionLabel.copy(shadow = null), color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.clearAndSetSemantics {})
        }
        Text(label, style = SlateText.scrubberLetter.copy(shadow = null), color = palette.onWallpaperMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ShapeChip(shape: IconShape, selected: Boolean, label: String, onClick: () -> Unit) {
    val palette = LocalSlatePalette.current
    val outline: Shape = when (shape) {
        IconShape.System -> RoundedCornerShape(30)
        IconShape.Circle -> CircleShape
        IconShape.Squircle -> RoundedCornerShape(38)
        IconShape.RoundedSquare -> RoundedCornerShape(24)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick).padding(4.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .background(if (selected) palette.accent else palette.onWallpaper.copy(alpha = 0.25f), outline),
        )
        Text(label, style = SlateText.scrubberLetter.copy(shadow = null), color = palette.onWallpaperMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun AppearanceSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    val palette = LocalSlatePalette.current
    var current by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { current = value }
    Row(Modifier.piece().padding(horizontal = RowInset, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = current,
            onValueChange = {
                current = (Math.round(it / step) * step).coerceIn(range)
                onPreview(current)
            },
            onValueChangeFinished = { onCommit(current) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent, inactiveTrackColor = palette.groundHigh),
            modifier = Modifier.weight(1f),
        )
        ValuePill(format(current))
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, summary: String? = null, icon: SlateIcon? = null, tint: Color? = null, onChange: (Boolean) -> Unit) {
    RowBody(label, summary, icon, Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange), tint) {
        SlateSwitch(checked, Modifier.padding(start = 12.dp))
    }
}

/** Drawn state only; the whole row is the toggle. The thumb grows and the track fills as it turns on. */
@Composable
private fun SlateSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalSlatePalette.current
    val on by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 450f), label = "switch")
    Canvas(modifier.size(width = 52.dp, height = 32.dp)) {
        val radius = size.height / 2
        val t = on.coerceIn(0f, 1f)
        drawRoundRect(lerp(palette.groundHigh, palette.accent, t), cornerRadius = CornerRadius(radius))
        // Off, a rim keeps the empty track from disappearing into the card.
        if (t < 1f) drawRoundRect(palette.onWallpaperMuted.copy(alpha = 0.45f * (1f - t)), cornerRadius = CornerRadius(radius), style = Stroke(2.dp.toPx()))
        val knob = 8.dp.toPx() + 4.dp.toPx() * on
        drawCircle(lerp(palette.onWallpaperMuted, palette.ground, t), knob, Offset(radius + (size.width - radius * 2) * on, radius))
    }
}

@Composable
internal fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalSlatePalette.current
    Row(
        Modifier.piece().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = RowInset, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).border(2.dp, if (selected) palette.accent else palette.onWallpaperMuted.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).background(palette.accent, CircleShape))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, style = RowLabel, color = if (selected) palette.onWallpaper else palette.onWallpaperMuted)
    }
}

@Composable
internal fun Note(text: String) {
    Text(
        text,
        style = SlateText.hint.copy(fontSize = 13.sp, shadow = null),
        color = LocalSlatePalette.current.onWallpaperMuted,
        modifier = Modifier.piece().padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

@Composable
internal fun presetLabel(id: String): String = stringResource(
    when (id) {
        "paper" -> R.string.preset_paper
        "mono" -> R.string.preset_mono
        "night" -> R.string.preset_night
        "bold" -> R.string.preset_bold
        "quiet" -> R.string.preset_quiet
        else -> R.string.app_name
    },
)

@Composable
private fun accentLabel(c: AccentChoice): String = stringResource(
    when (c) {
        AccentChoice.Ember -> R.string.accent_ember
        AccentChoice.Moss -> R.string.accent_moss
        AccentChoice.Tide -> R.string.accent_tide
        AccentChoice.Plum -> R.string.accent_plum
        AccentChoice.Sand -> R.string.accent_sand
        AccentChoice.Chalk -> R.string.accent_chalk
        AccentChoice.Wallpaper -> R.string.accent_wallpaper
        AccentChoice.Custom -> R.string.accent_custom
    },
)

@Composable
private fun panelLabel(t: PanelTone): String = stringResource(
    when (t) {
        PanelTone.Dark -> R.string.tone_dark
        PanelTone.Light -> R.string.tone_light
        PanelTone.System -> R.string.tone_system
        PanelTone.Black -> R.string.tone_black
    },
)

@Composable
private fun textToneLabel(t: TextTone): String = stringResource(
    when (t) {
        TextTone.Light -> R.string.tone_light
        TextTone.Dark -> R.string.tone_dark
        TextTone.Auto -> R.string.tone_auto
    },
)

/** Asks for the name a look is saved under. */
@Composable
private fun NameLookDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_save_look)) },
        text = { OutlinedTextField(name, { name = it.take(24) }, singleLine = true, label = { Text(stringResource(R.string.appearance_look_name)) }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(stringResource(R.string.appearance_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun fontLabel(f: FontChoice): String = stringResource(
    when (f) {
        FontChoice.Google -> R.string.font_google
        FontChoice.Kumbh -> R.string.font_kumbh
        FontChoice.Sans -> R.string.font_sans
        FontChoice.Condensed -> R.string.font_condensed
        FontChoice.Serif -> R.string.font_serif
        FontChoice.Mono -> R.string.font_mono
    },
)

@Composable
private fun iconStyleLabel(s: IconStyle): String = stringResource(
    when (s) {
        IconStyle.System -> R.string.icons_system
        IconStyle.Themed -> R.string.icons_themed
        IconStyle.Pack -> R.string.icons_pack
    },
)

@Composable
private fun shapeLabel(s: IconShape): String = stringResource(
    when (s) {
        IconShape.System -> R.string.shape_system
        IconShape.Circle -> R.string.shape_circle
        IconShape.Squircle -> R.string.shape_squircle
        IconShape.RoundedSquare -> R.string.shape_rounded
    },
)

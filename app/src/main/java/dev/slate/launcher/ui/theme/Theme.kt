package dev.slate.launcher.ui.theme

import dev.slate.launcher.data.TextTone
import dev.slate.launcher.data.PanelTone
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.slate.launcher.data.AccentChoice
import dev.slate.launcher.data.ThemeSettings

private val Ink = Color(0xFF151412)
private val InkRaised = Color(0xFF201F1C)
private val Paper = Color(0xFFF3EFE7)
private val PaperSunk = Color(0xFFE8E3D9)
private val Stone = Color(0xFF8C877E)
private val StoneDeep = Color(0xFF5E5950)

/**
 * Colours for content drawn directly on the wallpaper. Kept separate from the
 * Material scheme, which only styles raised surfaces (cards, menus, sheets).
 * Built from [ThemeSettings] (accent, dimming) and, for the wallpaper accent, the wallpaper.
 */
@Immutable
data class SlatePalette(
    val onWallpaper: Color,
    val onWallpaperMuted: Color,
    /** Even wallpaper dimming the user chose; transparent by default. */
    val scrim: Color,
    /** Behind the search surface: dark enough to read over any wallpaper. */
    val searchScrim: Color,
    val accent: Color,
    /** Cards floating over the home screen (pop-ups): the accent's hue, nearly black. */
    val panel: Color,
    /** A tile raised on [panel]. */
    val panelRaised: Color,
    val onPanel: Color,
    /** The accent as it reads on [panel]: deepened on a light one. */
    val panelAccent: Color,
    /** Settings screens: the ground, a card on it, and a control on a card. */
    val ground: Color,
    val groundRaised: Color,
    val groundHigh: Color,
) {
    /** For content drawn on [panel]: the same palette with the panel's text colours. */
    fun onPanel(): SlatePalette = copy(onWallpaper = onPanel, onWallpaperMuted = onPanel.copy(alpha = 0.7f), accent = panelAccent)

    /** For screens with their own dark background (settings, search), whatever the wallpaper's text colour. */
    fun onDark(): SlatePalette = copy(onWallpaper = Paper, onWallpaperMuted = Paper.copy(alpha = 0.6f))
}

private fun palette(accent: Color, dimming: Float, tone: PanelTone = PanelTone.Dark, darkText: Boolean = false) = SlatePalette(
    onWallpaper = if (darkText) Ink else Paper,
    onWallpaperMuted = (if (darkText) Ink else Paper).copy(alpha = if (darkText) 0.7f else 0.6f),
    scrim = Color.Black.copy(alpha = dimming.coerceIn(0f, 1f)),
    searchScrim = Ink.copy(alpha = 0.985f),
    accent = accent,
    panel = Color(Accent.panel(accent.toArgb(), tone)),
    panelRaised = Color(Accent.panelRaised(accent.toArgb(), tone)),
    onPanel = Color(Accent.onPanel(accent.toArgb(), tone)),
    panelAccent = if (tone == PanelTone.Light) Color(Accent.deep(accent.toArgb())) else accent,
    ground = Color(Accent.ground(accent.toArgb(), 0)),
    groundRaised = Color(Accent.ground(accent.toArgb(), 1)),
    groundHigh = Color(Accent.ground(accent.toArgb(), 2)),
)

val LocalSlatePalette = staticCompositionLocalOf { palette(Color(Accent.EMBER), ThemeSettings().dimming) }

/** The full look settings, for things that aren't colours or type (icon visibility). */
val LocalAppearance = staticCompositionLocalOf { ThemeSettings() }

/** Whether the wallpaper says dark text reads on it; set once at the top and inherited by previews. */
val LocalWallpaperDarkText = staticCompositionLocalOf { false }

/**
 * [wallpaperColor] is the wallpaper's primary colour, used when the accent is
 * [AccentChoice.Wallpaper]; it's lifted into a readable range first.
 */
@Composable
fun SlateTheme(
    theme: ThemeSettings = ThemeSettings(),
    wallpaperColor: Int? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    wallpaperDarkText: Boolean = LocalWallpaperDarkText.current,
    content: @Composable () -> Unit,
) {
    val accentArgb = Accent.resolve(theme.accent, wallpaperColor, theme.accentHue)
    val family = fontFamilyFor(theme.font)
    val tone = if (theme.panel == PanelTone.System) (if (darkTheme) PanelTone.Dark else PanelTone.Light) else theme.panel
    val darkText = when (theme.wallpaperText) {
        TextTone.Light -> false
        TextTone.Dark -> true
        TextTone.Auto -> wallpaperDarkText
    }
    val slatePalette = remember(accentArgb, theme.dimming, tone, darkText) { palette(Color(accentArgb), theme.dimming, tone, darkText) }
    val text = remember(family, theme.textScale, darkText) {
        SlateTextStyles(family, theme.textScale.coerceIn(ThemeSettings.MIN_SCALE, ThemeSettings.MAX_SCALE), darkText)
    }
    val scheme = remember(accentArgb, darkTheme) {
        if (darkTheme) {
            darkColorScheme(
                primary = Color(accentArgb),
                onPrimary = Ink,
                background = Ink,
                onBackground = Paper,
                surface = InkRaised,
                onSurface = Paper,
                onSurfaceVariant = Stone,
                outline = Stone,
            )
        } else {
            lightColorScheme(
                // The accent darkened so it reads as text on the light sheets.
                primary = Color(Accent.deep(accentArgb)),
                onPrimary = Paper,
                background = Paper,
                onBackground = Ink,
                surface = PaperSunk,
                onSurface = Ink,
                onSurfaceVariant = StoneDeep,
                outline = Stone,
            )
        }
    }
    CompositionLocalProvider(
        LocalSlatePalette provides slatePalette,
        LocalSlateText provides text,
        LocalAppearance provides theme,
        LocalWallpaperDarkText provides wallpaperDarkText,
    ) {
        MaterialTheme(colorScheme = scheme, typography = remember(family) { slateTypography(family) }, content = content)
    }
}

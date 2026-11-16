package dev.slate.launcher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AccentChoice {
    @SerialName("ember") Ember,
    @SerialName("moss") Moss,
    @SerialName("tide") Tide,
    @SerialName("plum") Plum,
    @SerialName("sand") Sand,
    @SerialName("chalk") Chalk,
    /** Derived from the wallpaper's dominant colour. */
    @SerialName("wallpaper") Wallpaper,
    /** Any hue, from [ThemeSettings.accentHue]. */
    @SerialName("custom") Custom,
}

/** The surface of pop-ups and The Pill, as Niagara's "Dark theme" and "Pitch-black theme". */
@Serializable
enum class PanelTone {
    @SerialName("dark") Dark,
    @SerialName("light") Light,
    /** Light or dark with the system. */
    @SerialName("system") System,
    @SerialName("black") Black,
}

/** The colour of text drawn on the wallpaper, as Niagara's "Text color". */
@Serializable
enum class TextTone {
    @SerialName("light") Light,
    @SerialName("dark") Dark,
    /** Dark when the wallpaper says dark text reads on it (Android 12+), else light. */
    @SerialName("auto") Auto,
}

/** Two bundled families (see third_party/fonts), then the device's own. Nothing is downloaded. */
@Serializable
enum class FontChoice {
    /** Google Sans Flex, bundled. */
    @SerialName("google") Google,
    /** Kumbh Sans, bundled. */
    @SerialName("kumbh") Kumbh,
    @SerialName("sans") Sans,
    @SerialName("condensed") Condensed,
    @SerialName("serif") Serif,
    @SerialName("mono") Mono,
}

@Serializable
enum class IconStyle {
    /** The app's own icon. */
    @SerialName("system") System,
    /** A one-colour glyph in the accent on a dark plate: the app's monochrome layer, or one worked out from its icon. */
    @SerialName("themed") Themed,
    /** The same glyph with no plate, drawn straight on the wallpaper. */
    @SerialName("glyph") Glyph,
    /** The app's own icon recoloured into shades of the accent. */
    @SerialName("tinted") Tinted,
    /** An installed ADW/Nova-format icon pack ([ThemeSettings.iconPack]). */
    @SerialName("pack") Pack,
}

/** Re-masks adaptive icons; legacy (non-adaptive) icons are left as drawn. */
@Serializable
enum class IconShape {
    @SerialName("system") System,
    @SerialName("circle") Circle,
    @SerialName("squircle") Squircle,
    @SerialName("rounded") RoundedSquare,
}

@Serializable
data class ThemeSettings(
    /** The preset these values came from; "custom" once the user changes one. */
    val preset: String = Presets.DEFAULT,
    val accent: AccentChoice = AccentChoice.Ember,
    val font: FontChoice = FontChoice.Sans,
    /** Multiplier for app labels and the clock, 0.85–1.25. */
    val textScale: Float = 1f,
    /** Optional even darkening of the wallpaper behind the list, 0–0.8. Off by default. */
    val dimming: Float = 0f,
    val showIcons: Boolean = true,
    val iconStyle: IconStyle = IconStyle.System,
    /** Package of the icon pack used when [iconStyle] is [IconStyle.Pack]. */
    val iconPack: String? = null,
    val iconShape: IconShape = IconShape.System,
    /** Hue in degrees for [AccentChoice.Custom]. */
    val accentHue: Float = 265f,
    val panel: PanelTone = PanelTone.Dark,
    val wallpaperText: TextTone = TextTone.Light,
    /** Multiplier for app icons in the list, 0.8–1.3. */
    val iconScale: Float = 1f,
    /** Space above and below each app row, in dp, 4–18. */
    val rowGap: Float = 10f,
) {
    companion object {
        const val MIN_SCALE = 0.85f
        const val MAX_SCALE = 1.25f
        const val MAX_DIMMING = 0.8f
        const val MIN_ICON_SCALE = 0.8f
        const val MAX_ICON_SCALE = 1.3f
        const val MIN_ROW_GAP = 4f
        const val MAX_ROW_GAP = 18f
    }
}

/** A generated wallpaper: a fall from [top] to [bottom] with soft glows of [glow]. ARGB. */
data class WallpaperSpec(val top: Int, val bottom: Int, val glow: Int)

/** A look the user saved under a name (Niagara's "My themes"). */
@Serializable
data class SavedTheme(val name: String, val theme: ThemeSettings)

/** One app's icon replaced by a drawable from an icon pack (Icon Assistant). */
@Serializable
data class IconOverride(val pack: String, val drawable: String)

/**
 * Named starting points. Applying one replaces the look but keeps the icon pack choice, since
 * a pack is a separate install the user picked on purpose.
 */
object Presets {
    const val DEFAULT = "slate"
    const val CUSTOM = "custom"

    /** A look, and the drawn stand-in for its wallpaper; the picture itself is `WallpaperArt.picture(id)`. Set only when the user asks. */
    data class Preset(val id: String, val theme: ThemeSettings, val wallpaper: WallpaperSpec)

    private fun spec(top: Long, bottom: Long, glow: Long) = WallpaperSpec(top.toInt(), bottom.toInt(), glow.toInt())

    val all: List<Preset> = listOf(
        Preset(DEFAULT, ThemeSettings(), spec(0xFF2B2A33, 0xFF14131A, 0xFFE0714A)),
        Preset(
            "paper",
            // Text follows the wallpaper: dark on this pale one, light if the user keeps a dark one.
            ThemeSettings(preset = "paper", accent = AccentChoice.Sand, font = FontChoice.Serif, textScale = 1.05f, wallpaperText = TextTone.Auto, panel = PanelTone.Light, iconStyle = IconStyle.Tinted, iconShape = IconShape.RoundedSquare),
            spec(0xFFF4ECDD, 0xFFDCCFB6, 0xFFE9B96E),
        ),
        Preset(
            "mono",
            ThemeSettings(preset = "mono", accent = AccentChoice.Chalk, font = FontChoice.Mono, textScale = 0.9f, dimming = 0.25f, iconStyle = IconStyle.Glyph, panel = PanelTone.Black),
            spec(0xFF2A2A2A, 0xFF0A0A0A, 0xFF6B6B6B),
        ),
        Preset("night", ThemeSettings(preset = "night", accent = AccentChoice.Tide, dimming = 0.5f, iconStyle = IconStyle.Themed, iconShape = IconShape.Circle), spec(0xFF0E1A33, 0xFF050912, 0xFF3F7FBF)),
        Preset(
            "bold",
            ThemeSettings(preset = "bold", accent = AccentChoice.Ember, font = FontChoice.Condensed, textScale = 1.2f, iconStyle = IconStyle.Tinted, iconShape = IconShape.Squircle),
            spec(0xFF7A1F12, 0xFF1E0806, 0xFFFF8A4C),
        ),
        Preset("quiet", ThemeSettings(preset = "quiet", accent = AccentChoice.Moss, textScale = 1.1f, showIcons = false), spec(0xFF2E4034, 0xFF101A14, 0xFF8FBF8A)),
        Preset(
            "dusk",
            ThemeSettings(preset = "dusk", accent = AccentChoice.Plum, font = FontChoice.Google, iconStyle = IconStyle.Tinted, iconShape = IconShape.Circle),
            spec(0xFF3B2456, 0xFF140B22, 0xFFE58BB8),
        ),
        Preset(
            "ocean",
            ThemeSettings(preset = "ocean", accent = AccentChoice.Custom, accentHue = 175f, font = FontChoice.Kumbh, iconStyle = IconStyle.Glyph, wallpaperText = TextTone.Auto),
            spec(0xFF0F4C5C, 0xFF06202A, 0xFF5ED0C0),
        ),
        Preset(
            "dawn",
            ThemeSettings(preset = "dawn", accent = AccentChoice.Ember, font = FontChoice.Google, wallpaperText = TextTone.Auto, panel = PanelTone.Light, iconShape = IconShape.RoundedSquare),
            spec(0xFFFFE0CC, 0xFFF4AE96, 0xFFFFF3DC),
        ),
    )

    fun apply(current: ThemeSettings, id: String): ThemeSettings {
        val preset = all.firstOrNull { it.id == id }?.theme ?: return current
        val keepPack = current.iconStyle == IconStyle.Pack && current.iconPack != null && preset.iconStyle == IconStyle.System
        return if (keepPack) preset.copy(iconStyle = IconStyle.Pack, iconPack = current.iconPack) else preset.copy(iconPack = current.iconPack)
    }

    /** Any manual change detaches the theme from its preset. */
    fun edited(theme: ThemeSettings): ThemeSettings = theme.copy(preset = CUSTOM)
}

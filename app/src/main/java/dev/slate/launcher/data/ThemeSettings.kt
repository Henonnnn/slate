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
    /** Android 13+ monochrome layer, tinted; apps without one keep their normal icon. */
    @SerialName("themed") Themed,
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

    data class Preset(val id: String, val theme: ThemeSettings)

    val all: List<Preset> = listOf(
        Preset(DEFAULT, ThemeSettings()),
        Preset("paper", ThemeSettings(preset = "paper", accent = AccentChoice.Sand, font = FontChoice.Serif, textScale = 1.05f)),
        Preset("mono", ThemeSettings(preset = "mono", accent = AccentChoice.Chalk, font = FontChoice.Mono, textScale = 0.9f, dimming = 0.25f, iconStyle = IconStyle.Themed)),
        Preset("night", ThemeSettings(preset = "night", accent = AccentChoice.Tide, dimming = 0.5f, iconShape = IconShape.Circle)),
        Preset("bold", ThemeSettings(preset = "bold", accent = AccentChoice.Ember, font = FontChoice.Condensed, textScale = 1.2f, iconShape = IconShape.Squircle)),
        Preset("quiet", ThemeSettings(preset = "quiet", accent = AccentChoice.Moss, textScale = 1.1f, showIcons = false)),
    )

    fun apply(current: ThemeSettings, id: String): ThemeSettings {
        val preset = all.firstOrNull { it.id == id }?.theme ?: return current
        val keepPack = current.iconStyle == IconStyle.Pack && current.iconPack != null && preset.iconStyle == IconStyle.System
        return if (keepPack) preset.copy(iconStyle = IconStyle.Pack, iconPack = current.iconPack) else preset.copy(iconPack = current.iconPack)
    }

    /** Any manual change detaches the theme from its preset. */
    fun edited(theme: ThemeSettings): ThemeSettings = theme.copy(preset = CUSTOM)
}

package dev.slate.launcher.ui.theme

import dev.slate.launcher.data.PanelTone
import dev.slate.launcher.data.AccentChoice
import kotlin.math.max
import kotlin.math.min

/**
 * Accent colours as ARGB ints, plus the colour maths, kept free of Android/Compose types so it
 * can be unit-tested.
 */
object Accent {
    const val EMBER = 0xFFE07A5F.toInt()

    fun swatch(choice: AccentChoice): Int? = when (choice) {
        AccentChoice.Ember -> EMBER
        AccentChoice.Moss -> 0xFF8DB07A.toInt()
        AccentChoice.Tide -> 0xFF6FA8C8.toInt()
        AccentChoice.Plum -> 0xFFB98BC7.toInt()
        AccentChoice.Sand -> 0xFFD6B47E.toInt()
        AccentChoice.Chalk -> 0xFFE9E4DA.toInt()
        AccentChoice.Wallpaper -> null
        AccentChoice.Custom -> null
    }

    /** The accent actually in use: the swatch, or the wallpaper's colour made readable, or Ember. */
    fun resolve(choice: AccentChoice, wallpaperColor: Int?, hue: Float = 0f): Int =
        if (choice == AccentChoice.Custom) custom(hue) else swatch(choice) ?: wallpaperColor?.let(::readableOnDark) ?: EMBER

    /** Any hue at the saturation and lightness the swatches share, so it reads on the wallpaper. */
    fun custom(hue: Float): Int = fromHsl(((hue % 360f) + 360f) % 360f, 0.6f, 0.7f)

    /**
     * Makes any colour usable as text on the dark scrim: keeps its hue, gives it some
     * saturation, and lifts lightness into a readable band. Near-greys stay grey.
     */
    fun readableOnDark(argb: Int): Int {
        val (h, s, l) = toHsl(argb)
        val sat = if (s < 0.12f) s else s.coerceIn(0.35f, 0.75f)
        return fromHsl(h, sat, l.coerceIn(0.62f, 0.78f))
    }

    /** A darker version for text on light surfaces (sheets in light mode). */
    fun deep(argb: Int): Int {
        val (h, s, l) = toHsl(argb)
        return fromHsl(h, s.coerceAtMost(0.7f), (l * 0.55f).coerceIn(0.22f, 0.4f))
    }

    /**
     * Niagara's pop-up surface: the accent's hue, nearly black. Its card measured #160E27 for a
     * violet accent, the tile raised on it #302844 and its text #E8DFFE. Greys stay grey.
     */
    fun panel(argb: Int, tone: PanelTone = PanelTone.Dark): Int = when (tone) {
        PanelTone.Light -> shade(argb, 0.5f, 0.955f)
        PanelTone.Black -> 0xFF000000.toInt()
        else -> shade(argb, 0.47f, 0.104f)
    }

    /** A tile raised on [panel]. */
    fun panelRaised(argb: Int, tone: PanelTone = PanelTone.Dark): Int = when (tone) {
        PanelTone.Light -> shade(argb, 0.4f, 0.89f)
        PanelTone.Black -> shade(argb, 0.3f, 0.11f)
        else -> shade(argb, 0.26f, 0.212f)
    }

    /** Text on [panel]: almost white (or almost black on a light one), with a breath of the accent. */
    fun onPanel(argb: Int, tone: PanelTone = PanelTone.Dark): Int =
        if (tone == PanelTone.Light) shade(argb, 0.5f, 0.12f) else shade(argb, 0.9f, 0.935f)

    /**
     * The ground of settings screens and what sits on it, in three steps (0 the ground, 1 a card,
     * 2 a control on a card): the accent's hue, kept dark so every accent reads on it.
     */
    fun ground(argb: Int, level: Int): Int = when (level) {
        0 -> shade(argb, 0.3f, 0.062f)
        1 -> shade(argb, 0.24f, 0.118f)
        else -> shade(argb, 0.22f, 0.185f)
    }

    private fun shade(argb: Int, saturation: Float, lightness: Float): Int {
        val (h, s, _) = toHsl(argb)
        return fromHsl(h, if (s < 0.12f) 0f else saturation, lightness)
    }

    /** The hue of a colour, in degrees. */
    fun toHue(argb: Int): Float = toHsl(argb).first

    internal fun toHsl(argb: Int): Triple<Float, Float, Float> {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        if (mx == mn) return Triple(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } * 60f
        return Triple(h, s, l)
    }

    internal fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}

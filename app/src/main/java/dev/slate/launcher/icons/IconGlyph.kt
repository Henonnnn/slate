package dev.slate.launcher.icons

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pixel work behind the one-colour and recoloured icon styles. Apps that ship no monochrome layer
 * still get a glyph: it's read off their icon, either its outline or whatever stands out from its plate.
 */
internal object IconGlyph {

    /**
     * A white glyph whose alpha carries the shape, or null when the icon gives nothing to go on.
     * [behind] is the colour under an opaque picture, when known.
     */
    fun derive(source: Bitmap, behind: Int?): Bitmap? {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val alpha = mask(pixels, w, h, behind) ?: return null
        for (i in pixels.indices) pixels[i] = (alpha[i] shl 24) or 0xFFFFFF
        return createBitmap(w, h).apply { setPixels(pixels, 0, w, 0, 0, w, h) }
    }

    /** Alpha per pixel, 0–255. Pure, so it's tested. */
    fun mask(pixels: IntArray, w: Int, h: Int, behind: Int?): IntArray? {
        var solid = 0
        val histogram = IntArray(256)
        for (p in pixels) {
            if (p ushr 24 >= 128) {
                solid++
                histogram[luma(p)]++
            }
        }
        if (solid == 0) return null
        val share = solid.toFloat() / pixels.size
        // The tone most of the icon is painted in: its plate, or the body of its logo.
        val main = dominant(histogram)
        val reference = if (share > OPAQUE_SHARE) behind?.let(::luma) ?: edgeLuma(pixels, w, h) ?: main else main
        var far = 1
        for (p in pixels) if (p ushr 24 >= 128) far = max(far, abs(luma(p) - reference))

        val out = IntArray(pixels.size)
        if (share > PLATE_SHARE) {
            // A filled plate: the glyph is what differs from it.
            if (far < MIN_CONTRAST) return null
            var covered = 0
            for (i in pixels.indices) {
                val p = pixels[i]
                val a = p ushr 24
                if (a == 0) continue
                val t = ((abs(luma(p) - reference).toFloat() / far - 0.18f) / 0.5f).coerceIn(0f, 1f)
                out[i] = (a * t).roundToInt()
                if (out[i] > 96) covered++
            }
            if (covered < pixels.size * MIN_COVER) return null
        } else {
            // A logo on nothing: its outline, with tones away from its body let through a little.
            for (i in pixels.indices) {
                val p = pixels[i]
                val a = p ushr 24
                if (a == 0) continue
                val away = abs(luma(p) - reference).toFloat() / far
                out[i] = (a * (1f - DETAIL * away)).roundToInt()
            }
        }
        return out
    }

    /** Shades of [tint] by the icon's own light and dark, in place. Transparency is kept. */
    fun recolour(bitmap: Bitmap, tint: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(tint, hsl)
        val ramp = IntArray(256) { l ->
            val t = l / 255f
            ColorUtils.HSLToColor(floatArrayOf(hsl[0], (hsl[1] * (0.85f - 0.3f * t)).coerceIn(0f, 1f), 0.12f + 0.78f * t)) and 0xFFFFFF
        }
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr 24
            if (a != 0) pixels[i] = (a shl 24) or ramp[luma(p)]
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /** The plate under a themed glyph: the tint's hue, nearly black. */
    fun plate(tint: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(tint, hsl)
        return ColorUtils.HSLToColor(floatArrayOf(hsl[0], (hsl[1] * 0.35f).coerceAtMost(0.3f), 0.15f))
    }

    private fun luma(p: Int): Int = (((p shr 16) and 0xFF) * 54 + ((p shr 8) and 0xFF) * 183 + (p and 0xFF) * 19) shr 8

    private fun dominant(histogram: IntArray): Int {
        // Smoothed over a few steps, so a gradient plate still counts as one tone.
        var best = 0
        var bestSum = -1
        for (l in 0..255) {
            var sum = 0
            for (d in -6..6) sum += histogram[(l + d).coerceIn(0, 255)]
            if (sum > bestSum) {
                bestSum = sum
                best = l
            }
        }
        return best
    }

    /** The tone round the rim of an opaque picture. */
    private fun edgeLuma(pixels: IntArray, w: Int, h: Int): Int? {
        var sum = 0L
        var n = 0
        fun take(x: Int, y: Int) {
            val p = pixels[y * w + x]
            if (p ushr 24 >= 128) {
                sum += luma(p)
                n++
            }
        }
        // The seen part of a layer starts a sixth in; the rim is sampled there.
        val inset = w / 5
        for (x in inset until w - inset) {
            take(x, inset)
            take(x, h - 1 - inset)
        }
        for (y in inset until h - inset) {
            take(inset, y)
            take(w - 1 - inset, y)
        }
        return if (n == 0) null else (sum / n).toInt()
    }

    private const val OPAQUE_SHARE = 0.9f
    private const val PLATE_SHARE = 0.5f
    private const val MIN_CONTRAST = 24
    private const val MIN_COVER = 0.015f
    private const val DETAIL = 0.55f
}

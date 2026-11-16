package dev.slate.launcher.icons

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.data.IconOverride
import dev.slate.launcher.data.IconShape
import dev.slate.launcher.data.IconStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Provided at the root so icons re-render when the look changes. */
val LocalIconAppearance = androidx.compose.runtime.staticCompositionLocalOf { IconAppearance() }

/** Everything that changes how icons look. [tag] goes into cache keys. */
data class IconAppearance(
    val style: IconStyle = IconStyle.System,
    val pack: String? = null,
    val shape: IconShape = IconShape.System,
    /** Foreground colour for themed icons. */
    val themedTint: Int = 0,
    val overrides: Map<String, IconOverride> = emptyMap(),
) {
    private val IconStyle.tinted get() = this == IconStyle.Themed || this == IconStyle.Glyph || this == IconStyle.Tinted

    val tag: String get() = "${style.name}|${if (style == IconStyle.Pack) pack else ""}|${shape.name}|${if (style.tinted) themedTint else 0}|v2"

    fun overrideTag(entry: AppEntry): String = overrides[entry.key]?.let { "${it.pack}:${it.drawable}" }.orEmpty()
}

/**
 * Draws an app's icon for the current [IconAppearance]: an Icon Assistant override, else the
 * pack's icon (or the pack's back/mask/overlay treatment of the app's own icon), else a themed
 * monochrome icon, else the app's icon; then optionally re-masks adaptive icons to a shape and
 * adds the work-profile badge.
 */
class IconRenderer(private val context: Context, private val packs: IconPackRepository) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val density = context.resources.displayMetrics.densityDpi

    fun render(entry: AppEntry, sizePx: Int, look: IconAppearance): Bitmap? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component)
        val info = runCatching { launcherApps.resolveActivity(intent, entry.user) }.getOrNull() ?: return null
        val own = runCatching { info.getIcon(density) }.getOrNull() ?: return null

        val override = look.overrides[entry.key]?.let { o -> packs.load(o.pack)?.drawable(o.drawable, density) }
        val drawable: Drawable = override ?: when (look.style) {
            IconStyle.Pack -> look.pack?.let(packs::load)?.let { pack ->
                pack.forComponent(entry.packageName, entry.component.className, density) ?: packTreatment(own, pack, entry, sizePx)
            } ?: own
            IconStyle.Themed -> themed(own, look.themedTint) ?: own
            IconStyle.Glyph -> glyph(own, look.themedTint, sizePx) ?: own
            IconStyle.Tinted, IconStyle.System -> own
        }

        val bitmap = if (drawable is AdaptiveIconDrawable && look.shape != IconShape.System) {
            shaped(drawable, sizePx, look.shape)
        } else {
            drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        }
        if (look.style == IconStyle.Tinted && override == null) IconGlyph.recolour(bitmap, look.themedTint)
        if (!entry.isWorkProfile) return bitmap
        val badged = context.packageManager.getUserBadgedIcon(bitmap.toDrawable(context.resources), entry.user)
        return badged.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    /** The one-colour glyph on a dark plate of the accent's own hue. */
    private fun themed(own: Drawable, tint: Int): Drawable? {
        val mono = monoLayer(own) ?: return null
        mono.setTint(tint)
        return AdaptiveIconDrawable(IconGlyph.plate(tint).toDrawable(), mono)
    }

    /** The glyph alone, larger, since no plate frames it. */
    private fun glyph(own: Drawable, tint: Int, size: Int): Drawable? {
        val mono = monoLayer(own) ?: return null
        mono.setTint(tint)
        val out = createBitmap(size, size)
        val pad = (size * GLYPH_BLEED).toInt()
        mono.setBounds(-pad, -pad, size + pad, size + pad)
        mono.draw(Canvas(out))
        return out.toDrawable(context.resources)
    }

    /**
     * A tintable drawable laid out as an adaptive-icon layer (108 units, the middle 72 seen): the app's
     * own monochrome layer where it ships one (Android 13+), else a glyph worked out from its icon, so
     * every app gets one and the list looks all of a piece.
     */
    private fun monoLayer(own: Drawable): Drawable? {
        if (own is AdaptiveIconDrawable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) own.monochrome?.let { return it.mutate() }
            val foreground = own.foreground ?: return null
            val source = createBitmap(LAYER_PX, LAYER_PX)
            foreground.setBounds(0, 0, LAYER_PX, LAYER_PX)
            foreground.draw(Canvas(source))
            // The plate is the background layer, so an opaque foreground is measured against it.
            val behind = (own.background as? android.graphics.drawable.ColorDrawable)?.color
            return IconGlyph.derive(source, behind)?.toDrawable(context.resources)
        }
        val mask = IconGlyph.derive(own.toBitmap(LAYER_PX, LAYER_PX, Bitmap.Config.ARGB_8888), null) ?: return null
        // A legacy icon fills its square, so it's set into the middle like a foreground layer.
        return InsetDrawable(mask.toDrawable(context.resources), LEGACY_INSET)
    }

    /** The pack's look for apps it has no icon for: back plate, scaled icon, mask cut-out, overlay. */
    private fun packTreatment(own: Drawable, pack: IconPack, entry: AppEntry, size: Int): Drawable? {
        val f = pack.filter
        if (f.backs.isEmpty() && f.mask == null && f.upon == null) return null
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        // Stable per app, so its plate doesn't change between renders.
        f.backs.takeIf { it.isNotEmpty() }?.let { backs ->
            pack.drawable(backs[abs(entry.packageName.hashCode()) % backs.size], density)?.apply { setBounds(0, 0, size, size); draw(canvas) }
        }
        val inset = ((1f - f.scale) * size / 2f).toInt()
        own.toBitmap(size - 2 * inset, size - 2 * inset).let { canvas.drawBitmap(it, inset.toFloat(), inset.toFloat(), null) }
        f.mask?.let { pack.drawable(it, density) }?.let { mask ->
            val maskBitmap = mask.toBitmap(size, size)
            canvas.drawBitmap(maskBitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
        f.upon?.let { pack.drawable(it, density) }?.apply { setBounds(0, 0, size, size); draw(canvas) }
        return out.toDrawable(context.resources)
    }

    /**
     * Re-masks an adaptive icon. Layers are 108 units with the visible 72 in the middle, so they're
     * drawn at 1.5× and offset; the shape is drawn anti-aliased and the layers composited into it.
     */
    private fun shaped(icon: AdaptiveIconDrawable, size: Int, shape: IconShape): Bitmap {
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawPath(shapePath(shape, size.toFloat()), paint)
        val layers = createBitmap(size, size)
        Canvas(layers).let { c ->
            val pad = (size * 0.25f).toInt()
            listOfNotNull(icon.background, icon.foreground).forEach { layer ->
                layer.setBounds(-pad, -pad, size + pad, size + pad)
                layer.draw(c)
            }
        }
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(layers, 0f, 0f, paint)
        layers.recycle()
        return out
    }

    private fun shapePath(shape: IconShape, s: Float): Path = Path().apply {
        when (shape) {
            IconShape.Circle -> addOval(RectF(0f, 0f, s, s), Path.Direction.CW)
            IconShape.RoundedSquare -> addRoundRect(RectF(0f, 0f, s, s), s * 0.24f, s * 0.24f, Path.Direction.CW)
            IconShape.Squircle -> {
                // Superellipse |x|^4 + |y|^4 = 1.
                val r = s / 2f
                for (i in 0..96) {
                    val t = 2.0 * Math.PI * i / 96
                    val c = cos(t)
                    val sn = sin(t)
                    val x = r + r * sign(c) * abs(c).pow(0.5)
                    val y = r + r * sign(sn) * abs(sn).pow(0.5)
                    if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                }
                close()
            }
            IconShape.System -> addRect(0f, 0f, s, s, Path.Direction.CW)
        }
    }

    private companion object {
        const val LAYER_PX = 216
        const val GLYPH_BLEED = 0.42f
        const val LEGACY_INSET = 0.24f
    }
}

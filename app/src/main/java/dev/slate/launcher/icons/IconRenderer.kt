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
    val tag: String get() = "${style.name}|${if (style == IconStyle.Pack) pack else ""}|${shape.name}|${if (style == IconStyle.Themed) themedTint else 0}"

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
            IconStyle.System -> own
        }

        val bitmap = if (drawable is AdaptiveIconDrawable && look.shape != IconShape.System) {
            shaped(drawable, sizePx, look.shape)
        } else {
            drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        }
        if (!entry.isWorkProfile) return bitmap
        val badged = context.packageManager.getUserBadgedIcon(bitmap.toDrawable(context.resources), entry.user)
        return badged.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    /** Android 13+ monochrome layer on a dark plate, tinted with the accent. */
    private fun themed(own: Drawable, tint: Int): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || own !is AdaptiveIconDrawable) return null
        val mono = own.monochrome?.mutate() ?: return null
        mono.setTint(tint)
        return AdaptiveIconDrawable(THEMED_PLATE.toDrawable(), mono)
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
        const val THEMED_PLATE = 0xFF2A2825.toInt()
    }
}

package dev.slate.launcher.ui.theme

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import dev.slate.launcher.R
import dev.slate.launcher.data.WallpaperSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The wallpapers that go with Slate's looks: a bundled public-domain picture each ([picture]),
 * and a drawing for any look without one, a fall of colour from top to bottom with two soft glows.
 */
object WallpaperArt {

    /** The same drawing serves the small preview on a card and the real wallpaper. */
    fun render(spec: WallpaperSpec, width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, spec.top, spec.bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        // A glow high on one side and a fainter one low on the other, clear of where the clock sits.
        glow(canvas, paint, w * 0.88f, h * 0.2f, w * 0.95f, spec.glow, 0xB0)
        glow(canvas, paint, w * 0.05f, h * 0.86f, w * 0.85f, spec.glow, 0x55)
        glow(canvas, paint, w * 0.55f, h * 0.55f, w * 0.6f, spec.top, 0x40)
        return bitmap
    }

    private fun glow(canvas: Canvas, paint: Paint, x: Float, y: Float, radius: Float, colour: Int, alpha: Int) {
        val solid = (colour and 0x00FFFFFF) or (alpha shl 24)
        paint.shader = RadialGradient(x, y, radius, solid, colour and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, paint)
    }

    /**
     * The picture bundled for a look (see third_party/wallpapers: old paintings, prints and one
     * photograph, all in the public domain), or null for a look that only has its drawn fall of colour.
     */
    @DrawableRes
    fun picture(preset: String): Int? = when (preset) {
        "slate" -> R.drawable.wall_slate
        "paper" -> R.drawable.wall_paper
        "mono" -> R.drawable.wall_mono
        "night" -> R.drawable.wall_night
        "bold" -> R.drawable.wall_bold
        "quiet" -> R.drawable.wall_quiet
        "dusk" -> R.drawable.wall_dusk
        "ocean" -> R.drawable.wall_ocean
        "dawn" -> R.drawable.wall_dawn
        else -> null
    }

    /** A look's wallpaper small enough for its card: the picture sampled down, else the drawing. */
    suspend fun thumbnail(context: Context, preset: String, spec: WallpaperSpec): Bitmap = withContext(Dispatchers.IO) {
        picture(preset)?.let { id ->
            BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inSampleSize = 4; inScaled = false })
        } ?: render(spec, 132, 220)
    }

    /** Whether dark text reads on the top of it, where the clock sits. */
    fun paleOnTop(bitmap: Bitmap): Boolean {
        var sum = 0L
        var n = 0
        for (y in 0 until bitmap.height / 3 step 4) {
            for (x in 0 until bitmap.width step 4) {
                val p = bitmap[x, y]
                sum += (((p shr 16) and 0xFF) * 54 + ((p shr 8) and 0xFF) * 183 + (p and 0xFF) * 19) shr 8
                n++
            }
        }
        return n > 0 && sum / n > 150
    }

    /** Sets it as the home-screen wallpaper (the lock screen keeps its own). False if the system refuses. */
    suspend fun apply(context: Context, preset: String, spec: WallpaperSpec): Boolean = withContext(Dispatchers.IO) {
        val metrics = context.resources.displayMetrics
        val manager = WallpaperManager.getInstance(context)
        try {
            if (!manager.isSetWallpaperAllowed) return@withContext false
            val picture = picture(preset)
            if (picture != null) {
                // A drawable-nodpi WebP, read as it is: the system decodes and crops it.
                @SuppressLint("ResourceType")
                val stream = context.resources.openRawResource(picture)
                stream.use { manager.setStream(it, null, true, WallpaperManager.FLAG_SYSTEM) }
            } else {
                manager.setBitmap(render(spec, metrics.widthPixels, metrics.heightPixels), null, true, WallpaperManager.FLAG_SYSTEM)
            }
            true
        } catch (e: IOException) {
            Log.w("WallpaperArt", "Cannot set the wallpaper", e)
            false
        } catch (e: SecurityException) {
            Log.w("WallpaperArt", "Cannot set the wallpaper", e)
            false
        }
    }
}

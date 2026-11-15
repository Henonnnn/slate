package dev.slate.launcher.ui.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Log
import androidx.core.graphics.createBitmap
import dev.slate.launcher.data.WallpaperSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The wallpapers that go with Slate's looks. They're drawn here, not shipped as pictures: a
 * fall of colour from top to bottom with two soft glows, so the list reads on them, they weigh
 * nothing in the APK, and they come out sharp at any screen size.
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

    /** Sets it as the home-screen wallpaper (the lock screen keeps its own). False if the system refuses. */
    suspend fun apply(context: Context, spec: WallpaperSpec): Boolean = withContext(Dispatchers.Default) {
        val metrics = context.resources.displayMetrics
        val manager = WallpaperManager.getInstance(context)
        try {
            if (!manager.isSetWallpaperAllowed) return@withContext false
            manager.setBitmap(render(spec, metrics.widthPixels, metrics.heightPixels), null, true, WallpaperManager.FLAG_SYSTEM)
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

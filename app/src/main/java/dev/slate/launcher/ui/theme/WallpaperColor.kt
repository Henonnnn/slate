package dev.slate.launcher.ui.theme

import android.app.WallpaperColors
import android.os.Build
import android.app.WallpaperManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * The home-screen wallpaper's primary colour as reported by the system (no permission needed,
 * no pixels read), updating when the wallpaper changes. Null for live wallpapers that don't
 * report colours. [context] must be a UI context (an activity): it's a per-display API.
 */
fun wallpaperPrimaryColor(context: Context): Flow<Int?> = callbackFlow {
    val manager = WallpaperManager.getInstance(context)
    fun read() = runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb() }.getOrNull()
    launch(Dispatchers.IO) { send(read()) }
    val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) trySend(colors?.primaryColor?.toArgb())
    }
    manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
    awaitClose { manager.removeOnColorsChangedListener(listener) }
}.flowOn(Dispatchers.Main)

/**
 * Whether the wallpaper says dark text reads on it (a light wallpaper). The hint exists from
 * Android 12; before that, and for wallpapers that report nothing, it's false.
 */
fun wallpaperSupportsDarkText(context: Context): Flow<Boolean> = callbackFlow {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        send(false)
        awaitClose { }
        return@callbackFlow
    }
    val manager = WallpaperManager.getInstance(context)
    fun dark(colors: WallpaperColors?) = colors != null && colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
    launch(Dispatchers.IO) { send(runCatching { dark(manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)) }.getOrDefault(false)) }
    val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) trySend(dark(colors))
    }
    manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
    awaitClose { manager.removeOnColorsChangedListener(listener) }
}.flowOn(Dispatchers.Main)

package dev.slate.launcher.icons

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.apps.AppShortcut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Two-tier icon cache.
 *
 * - Memory: LruCache of GPU-prepared bitmaps, keyed by app key + pixel size.
 * - Disk: PNGs in `cacheDir/icons/look-<hash of IconAppearance.tag>/`, named `<package>@<hash>.png`, the hash covering
 *   app key (component + user), version stamp, size and density. An app update changes
 *   the stamp, so stale files are never read; package callbacks delete them.
 *
 * All loading happens on a bounded IO dispatcher; nothing touches disk on the main thread.
 */
class IconCache(private val context: Context, private val packs: IconPackRepository) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val renderer = IconRenderer(context, packs)
    private val io = Dispatchers.IO.limitedParallelism(4)
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val density = context.resources.displayMetrics.densityDpi
    // Lazy: getCacheDir() touches disk, so resolve it on the first IO-thread load.
    private val rootDir by lazy { File(context.cacheDir, "icons") }

    /**
     * Current look. Part of every key, so a change simply misses the cache; each look gets its own
     * disk folder and the folders of other looks are deleted when it changes.
     */
    @Volatile
    var appearance: IconAppearance = IconAppearance()
        set(value) {
            if (value == field) return
            val tagChanged = value.tag != field.tag
            field = value
            if (tagChanged) background.launch { rootDir.listFiles()?.filter { it.isDirectory && it.name != dirName(value) }?.forEach { it.deleteRecursively() } }
        }

    private fun dirName(look: IconAppearance) = "look-" + hash(look.tag)

    private val memory = object : LruCache<String, ImageBitmap>(maxBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(entry: AppEntry, sizePx: Int): ImageBitmap? = memory.get(memoryKey(entry, sizePx))

    suspend fun load(entry: AppEntry, sizePx: Int): ImageBitmap? {
        peek(entry, sizePx)?.let { return it }
        return withContext(io) {
            val look = appearance
            val file = diskFile(entry, sizePx, look)
            val bitmap = readDisk(file) ?: renderer.render(entry, sizePx, look)?.also { writeDisk(file, it) }
            bitmap?.let {
                it.prepareToDraw()
                it.asImageBitmap().also { image -> memory.put(memoryKey(entry, sizePx), image) }
            }
        }
    }

    fun peekShortcut(shortcut: AppShortcut, sizePx: Int): ImageBitmap? = memory.get(shortcutKey(shortcut, sizePx))

    /** Memory tier only: shortcut icons change with the app's own state, so they aren't persisted. */
    suspend fun loadShortcut(shortcut: AppShortcut, sizePx: Int): ImageBitmap? {
        peekShortcut(shortcut, sizePx)?.let { return it }
        return withContext(io) {
            val drawable = runCatching { launcherApps.getShortcutIconDrawable(shortcut.info, density) }.getOrNull()
                ?: return@withContext null
            val bitmap = drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { it.prepareToDraw() }
            bitmap.asImageBitmap().also { memory.put(shortcutKey(shortcut, sizePx), it) }
        }
    }

    fun evictPackage(packageName: String) {
        // The icon pack itself changed: every icon may be different.
        if (packageName == appearance.pack || appearance.overrides.values.any { it.pack == packageName }) {
            packs.invalidate(packageName)
            memory.evictAll()
            background.launch { rootDir.deleteRecursively() }
            return
        }
        val prefix = "$packageName/"
        memory.snapshot().keys.filter { it.startsWith(prefix) }.forEach(memory::remove)
        background.launch {
            rootDir.walkTopDown().filter { it.isFile && it.name.startsWith("$packageName@") }.forEach { it.delete() }
        }
    }

    private fun readDisk(file: File): Bitmap? =
        if (file.isFile) BitmapFactory.decodeFile(file.path) else null

    private fun writeDisk(file: File, bitmap: Bitmap) {
        try {
            file.parentFile?.mkdirs()
            // Write-then-rename so a crash never leaves a truncated PNG behind.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(file)) tmp.delete()
        } catch (e: IOException) {
            Log.w(TAG, "Icon disk write failed", e)
        }
    }

    private fun diskFile(entry: AppEntry, sizePx: Int, look: IconAppearance): File {
        val h = hash("${entry.key}|${entry.versionStamp}|$sizePx|$density|${look.overrideTag(entry)}")
        return File(File(rootDir, dirName(look)), "${entry.packageName}@$h.png")
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).take(10).joinToString("") { "%02x".format(it) }

    // Starts with "<pkg>/" like app keys, so evictPackage() drops these too.
    private fun shortcutKey(shortcut: AppShortcut, sizePx: Int) =
        "${shortcut.key}#shortcut@${shortcut.info.lastChangedTimestamp}@$sizePx"

    private fun memoryKey(entry: AppEntry, sizePx: Int): String {
        val look = appearance
        return "${entry.key}@${entry.versionStamp.hashCode()}@$sizePx@${look.tag}@${look.overrideTag(entry)}"
    }

    private fun maxBytes(): Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private companion object {
        const val TAG = "IconCache"
    }
}

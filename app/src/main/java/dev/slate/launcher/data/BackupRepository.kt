package dev.slate.launcher.data

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.slate.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Reads and writes backup files at locations the user picked in the system file dialog. */
class BackupRepository(private val context: Context, private val settings: SettingsRepository) {

    suspend fun export(uri: Uri): Boolean {
        val text = Backup.encode(settings.settings.first(), BuildConfig.VERSION_NAME, System.currentTimeMillis())
        return withContext(Dispatchers.IO) {
            try {
                // "wt": replace the file if the user picked an existing one.
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
            } catch (e: IOException) {
                Log.w(TAG, "Backup write failed", e)
                false
            } catch (e: SecurityException) {
                Log.w(TAG, "Backup write refused", e)
                false
            }
        }
    }

    suspend fun read(uri: Uri): Backup.Decoded = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (out.size() <= Backup.MAX_BYTES) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                if (out.size() > Backup.MAX_BYTES) null else out.toByteArray().decodeToString()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Backup read failed", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Backup read refused", e)
            null
        }
        text?.let(Backup::decode) ?: Backup.Decoded.NotABackup
    }

    suspend fun restore(file: BackupFile) {
        settings.update { current -> Backup.restoreOnto(current, file.settings) }
    }

    private companion object {
        const val TAG = "Backup"
    }
}

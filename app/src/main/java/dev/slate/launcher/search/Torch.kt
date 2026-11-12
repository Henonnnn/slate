package dev.slate.launcher.search

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Torch on/off through [CameraManager.setTorchMode]; needs no permission. */
class Torch(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)

    /** First back-facing camera with a flash, or null on devices without one. */
    suspend fun cameraId(): String? = withContext(Dispatchers.IO) {
        runCatching {
            cameras.cameraIdList.firstOrNull { id ->
                val c = cameras.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
    }

    fun state(cameraId: String?): Flow<Boolean> = if (cameraId == null) flowOf(false) else callbackFlow {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == cameraId) trySend(enabled)
            }
            override fun onTorchModeUnavailable(id: String) {
                if (id == cameraId) trySend(false)
            }
        }
        // The callback fires once immediately with the current state.
        cameras.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { cameras.unregisterTorchCallback(callback) }
    }

    /** False when the camera is in use by another app. */
    fun set(cameraId: String, on: Boolean): Boolean = try {
        cameras.setTorchMode(cameraId, on)
        true
    } catch (e: CameraAccessException) {
        Log.w("Torch", "Torch unavailable", e)
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}

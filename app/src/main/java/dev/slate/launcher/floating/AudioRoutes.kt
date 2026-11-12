package dev.slate.launcher.floating

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/** When The Pill offers music by itself. */
@kotlinx.serialization.Serializable
enum class AudioPrompt {
    @kotlinx.serialization.SerialName("off") Off,
    /** While Bluetooth is switched on. */
    @kotlinx.serialization.SerialName("bluetooth") Bluetooth,
    /** While earbuds, headphones or a headset are connected, wired or wireless. */
    @kotlinx.serialization.SerialName("headset") Headset,
}

/**
 * Where sound would come out, for offering music at the right moment. Both signals are public
 * and need no runtime permission: the audio device list, and the Bluetooth adapter's state.
 */
class AudioRoutes(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    /** True while something worn is connected: wired, USB or Bluetooth headphones, or hearing aids. */
    fun headset(): Flow<Boolean> = callbackFlow {
        fun read() = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in WORN }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                trySend(read())
            }

            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                trySend(read())
            }
        }
        // Registering reports the devices already there.
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        trySend(read())
        awaitClose { audio.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()

    /** True while the Bluetooth adapter is on. False where its state can't be read. */
    fun bluetoothOn(): Flow<Boolean> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        fun read() = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF) == BluetoothAdapter.STATE_ON)
            }
        }
        trySend(read())
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    /**
     * Presses "play" as a headset button would: the system hands it to whichever app played
     * last and wakes it if need be. There's no way to start a particular app playing.
     */
    fun pressPlay() {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
    }

    /** Apps that say they play music, by package, with their names. */
    suspend fun musicApps(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launchers = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC), 0) }
            .getOrDefault(emptyList()).map { it.activityInfo.packageName }
        val browsers = runCatching { pm.queryIntentServices(Intent(MEDIA_BROWSER), 0) }
            .getOrDefault(emptyList()).map { it.serviceInfo.packageName }
        (launchers + browsers).distinct()
            .filter { pm.getLaunchIntentForPackage(it) != null }
            .map { it to label(it) }
            .sortedBy { it.second.lowercase() }
    }

    /** The app to offer: the chosen one, else the last that played, else the system's music app. */
    suspend fun resolve(chosen: String?, lastPlayer: String?): Pair<String, String>? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val fallback = runCatching {
            pm.resolveActivity(Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER), 0)?.activityInfo?.packageName
        }.getOrNull()
        listOfNotNull(chosen, lastPlayer, fallback).firstOrNull { pm.getLaunchIntentForPackage(it) != null }?.let { it to label(it) }
    }

    fun open(packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    private fun label(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private companion object {
        const val MEDIA_BROWSER = "android.media.browse.MediaBrowserService"
        val WORN = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            // AudioDeviceInfo.TYPE_BLE_HEADSET, a constant only from API 31.
            26,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}

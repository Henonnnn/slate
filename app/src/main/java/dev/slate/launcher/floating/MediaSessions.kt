package dev.slate.launcher.floating

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.graphics.scale
import dev.slate.launcher.notifications.NotificationService
import dev.slate.launcher.sendFromForeground
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The top active media session. Android only lists other apps' sessions to apps holding
 * notification access, so without it this emits null and the button skips media.
 */
class MediaSessions(private val context: Context) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, NotificationService::class.java)

    @Volatile
    private var current: MediaController? = null

    fun nowPlaying(): Flow<NowPlaying?> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        var watched: MediaController? = null
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = emitFrom(watched)
            override fun onPlaybackStateChanged(state: PlaybackState?) = emitFrom(watched)
            override fun onSessionDestroyed() = emitFrom(null)
        }

        fun watch(controllers: List<MediaController>?) {
            // The system orders sessions by priority; prefer one that's actually playing.
            val top = controllers.orEmpty().let { list -> list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull() }
            if (top?.sessionToken != watched?.sessionToken) {
                watched?.unregisterCallback(callback)
                watched = top
                top?.registerCallback(callback, handler)
            }
            current = top
            emitFrom(top)
        }

        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
        val registered = try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            watch(manager.getActiveSessions(listener))
            true
        } catch (e: SecurityException) {
            // No notification access.
            trySend(null)
            false
        }
        awaitClose {
            watched?.unregisterCallback(callback)
            if (registered) manager.removeOnActiveSessionsChangedListener(sessionsListener)
            current = null
        }
    }

    private fun kotlinx.coroutines.channels.ProducerScope<NowPlaying?>.emitFrom(controller: MediaController?) {
        trySend(controller?.let(::describe))
    }

    private fun describe(c: MediaController): NowPlaying? {
        val state = c.playbackState?.state ?: return null
        // Stopped/none/error sessions linger in the list; they're not "playing" anything.
        if (state == PlaybackState.STATE_STOPPED || state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_ERROR) return null
        val m = c.metadata ?: return null
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val art = (m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))
            ?.let(::thumbnail)
        val playback = c.playbackState
        val actions = playback?.actions ?: 0L
        return NowPlaying(
            packageName = c.packageName,
            title = title,
            artist = artist,
            playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING,
            canSkip = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            art = art,
            canPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
            durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
            positionMs = playback?.position?.coerceAtLeast(0L) ?: 0L,
            positionAt = playback?.lastPositionUpdateTime ?: 0L,
            speed = playback?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
    }

    private fun thumbnail(b: Bitmap): Bitmap {
        val px = (THUMB_DP * context.resources.displayMetrics.density).toInt()
        return if (b.width <= px * 2) b else b.scale(px, px * b.height / b.width.coerceAtLeast(1))
    }

    /**
     * Controls go in as media-button key presses, like headset buttons and the system's own media
     * keys. Some players (media3-based ones, for example) ignore direct transport calls from other
     * apps but always honour key events; the transport call is the fallback.
     */
    fun togglePlay() {
        val c = current ?: return
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        if (!press(c, if (playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY)) {
            if (playing) c.transportControls.pause() else c.transportControls.play()
        }
    }

    fun next() {
        val c = current ?: return
        if (!press(c, KeyEvent.KEYCODE_MEDIA_NEXT)) c.transportControls.skipToNext()
    }

    fun previous() {
        val c = current ?: return
        if (!press(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS)) c.transportControls.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        current?.transportControls?.seekTo(positionMs)
    }

    private fun press(c: MediaController, keyCode: Int): Boolean {
        val down = c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        val up = c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return down && up
    }

    /** The player's own UI, if the session offers one. */
    fun open(context: Context): Boolean {
        val c = current ?: return false
        val intent = c.sessionActivity
        return if (intent != null) {
            intent.sendFromForeground(context)
        } else {
            val launch = context.packageManager.getLaunchIntentForPackage(c.packageName) ?: return false
            runCatching { context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }

    private companion object {
        // Large enough for the expanded Pill's cover.
        const val THUMB_DP = 96
    }
}

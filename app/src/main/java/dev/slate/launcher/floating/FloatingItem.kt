package dev.slate.launcher.floating

import android.app.Notification
import android.app.PendingIntent
import androidx.compose.runtime.Immutable
import java.time.Duration
import java.time.Instant

/** What The Pill can show; the chooser picks at most two. */
@Immutable
sealed interface FloatingItem {
    /** Identity for "swiped away": a dismissed item stays hidden until this changes. */
    val key: String

    data class Media(val media: NowPlaying) : FloatingItem {
        // Per track, so a new song brings The Pill back after a dismissal.
        override val key get() = "media:${media.packageName}:${media.title}"
    }

    /** A timer, stopwatch, ringing alarm or other live activity an app is showing right now. */
    data class Live(val activity: LiveActivity) : FloatingItem {
        override val key get() = "live:${activity.key}"
    }

    /**
     * The flashlight, from the moment it's lit ([since], which makes each lighting its own item).
     * It lingers a few seconds after going out, so a slip can be undone with one tap.
     */
    data class Torch(val on: Boolean, val since: Long) : FloatingItem {
        override val key get() = "torch:$since"
    }

    /**
     * Music offered because sound has somewhere to go (see [AudioPrompt]) and nothing is playing:
     * the way into [packageName], with a play button when [player].
     */
    data class Music(val packageName: String, val label: String, val player: Boolean, val since: Long) : FloatingItem {
        override val key get() = "music:$since"
    }

    data class Alarm(val time: Instant) : FloatingItem {
        override val key get() = "alarm:${time.toEpochMilli()}"
    }
}

/** A media session as The Pill shows it. Controls go through [MediaSessions]. */
@Immutable
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val canSkip: Boolean,
    /** Cover art, already scaled; null when the app gives none. */
    val art: android.graphics.Bitmap?,
    val canPrevious: Boolean = false,
    val canSeek: Boolean = false,
    /** 0 when the player doesn't say (live streams). */
    val durationMs: Long = 0,
    /** Where playback was at [positionAt] (elapsed realtime), moving at [speed] while playing. */
    val positionMs: Long = 0,
    val positionAt: Long = 0,
    val speed: Float = 1f,
) {
    /** Where playback is at [elapsedRealtime], following the player's own clock. */
    fun positionAt(elapsedRealtime: Long): Long {
        val moved = if (playing) ((elapsedRealtime - positionAt) * speed).toLong() else 0L
        val position = (positionMs + moved).coerceAtLeast(0L)
        return if (durationMs > 0) position.coerceAtMost(durationMs) else position
    }
}

/**
 * An ongoing notification worth a place in The Pill: one that counts time (a timer or stopwatch),
 * a ringing alarm, or one Android itself promotes as a live update. The UI reads the count from
 * [source] when it draws it.
 */
@Immutable
class LiveActivity(
    /** The platform's notification key. */
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
    val source: Notification?,
) {
    // A re-post is a new state of the same activity; the notification object itself never compares.
    override fun equals(other: Any?): Boolean =
        other is LiveActivity && other.key == key && other.postTime == postTime && other.title == title && other.text == text

    override fun hashCode(): Int = 31 * key.hashCode() + postTime.hashCode()
}

/** Which sources may use The Pill; stored in settings. */
@kotlinx.serialization.Serializable
data class FloatingSources(
    val media: Boolean = true,
    val timers: Boolean = true,
    val alarm: Boolean = true,
    val torch: Boolean = true,
    /** How long the flashlight stays after going out. */
    val torchLingerSeconds: Int = 5,
    /** How far ahead an alarm shows. */
    val alarmWindowHours: Int = 12,
    /** Tapping the small pill opens the player's app instead of the card; swiping up still opens the card. */
    val tapOpensApp: Boolean = false,
    val equalizer: Boolean = true,
    val audioPrompt: AudioPrompt = AudioPrompt.Off,
    /** The offer has a play button; off, it's only a way into the app. */
    val audioPromptPlayer: Boolean = true,
    /** The app offered; null means whichever played last. */
    val musicApp: String? = null,
    /** Kept by The Pill itself as players come and go. */
    val lastPlayer: String? = null,
    /** The seek line waves while music plays (Material 3 Expressive); off draws it straight. */
    val wavyProgress: Boolean = true,
    /** A second activity may sit beside the first. */
    val two: Boolean = true,
)

/**
 * Picks what The Pill shows, pure so it's tested: playing (or paused) media first, then the
 * flashlight, then live activities, newest first, then an alarm in the next 12 hours. Dismissed keys are skipped. The
 * first is the main one; a second sits beside it. Calendar events aren't here: they're under
 * the clock.
 */
object FloatingChooser {
    const val MAX_ITEMS = 2

    fun choose(
        now: Instant,
        sources: FloatingSources,
        media: NowPlaying?,
        live: List<LiveActivity>,
        nextAlarm: Instant?,
        dismissed: Set<String>,
        torch: FloatingItem.Torch? = null,
        music: FloatingItem.Music? = null,
    ): List<FloatingItem> {
        val candidates = buildList {
            if (sources.media && media != null) add(FloatingItem.Media(media))
            // Only while nothing is playing: once there's a player, that's the better thing to show.
            else if (sources.audioPrompt != AudioPrompt.Off && music != null) add(music)
            if (sources.torch && torch != null) add(torch)
            if (sources.timers) live.sortedByDescending { it.postTime }.forEach { add(FloatingItem.Live(it)) }
            if (sources.alarm && nextAlarm != null && nextAlarm.isAfter(now) && !nextAlarm.isAfter(now + Duration.ofHours(sources.alarmWindowHours.toLong()))) {
                add(FloatingItem.Alarm(nextAlarm))
            }
        }
        return candidates.filter { it.key !in dismissed }.take(if (sources.two) MAX_ITEMS else 1)
    }
}

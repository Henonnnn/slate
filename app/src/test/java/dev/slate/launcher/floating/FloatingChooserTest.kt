package dev.slate.launcher.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FloatingChooserTest {
    private val now = Instant.parse("2026-10-30T18:00:00Z")
    private val song = NowPlaying("player", "Song", "Artist", playing = true, canSkip = true, art = null)
    private fun timer(id: Int, postTime: Long = id.toLong()) = LiveActivity("k$id", "clock", "Clock", "Timer $id", "", postTime, null, null)
    private fun choose(
        media: NowPlaying? = null,
        live: List<LiveActivity> = emptyList(),
        alarm: Instant? = null,
        dismissed: Set<String> = emptySet(),
        sources: FloatingSources = FloatingSources(),
    ) = FloatingChooser.choose(now, sources, media, live, alarm, dismissed)

    @Test fun mediaLeadsThenLiveThenAlarmAndTwoShow() {
        val alarm = now + Duration.ofHours(8)
        val both = choose(song, listOf(timer(1)), alarm)
        assertEquals(2, both.size)
        assertTrue(both[0] is FloatingItem.Media)
        assertTrue(both[1] is FloatingItem.Live)
        val noMedia = choose(null, listOf(timer(1)), alarm)
        assertTrue(noMedia[0] is FloatingItem.Live)
        assertTrue(noMedia[1] is FloatingItem.Alarm)
        assertTrue(choose(alarm = alarm).single() is FloatingItem.Alarm)
        assertTrue(choose().isEmpty())
    }

    @Test fun flashlightSitsBesideMediaAheadOfTimers() {
        val light = FloatingItem.Torch(on = true, since = 5)
        val items = FloatingChooser.choose(now, FloatingSources(), song, listOf(timer(1)), null, emptySet(), light)
        assertEquals(listOf(FloatingItem.Media(song), light), items)
        // Out but lingering, it's the same item; a new lighting is a new one, so a dismissal doesn't stick.
        assertEquals(light.key, light.copy(on = false).key)
        assertTrue(FloatingChooser.choose(now, FloatingSources(), null, emptyList(), null, setOf(light.key), light.copy(since = 9)).isNotEmpty())
        assertTrue(FloatingChooser.choose(now, FloatingSources(torch = false), null, emptyList(), null, emptySet(), light).isEmpty())
    }

    @Test fun musicIsOfferedOnlyWhileNothingPlays() {
        val offer = FloatingItem.Music("player", "Player", player = true, since = 3)
        val on = FloatingSources(audioPrompt = AudioPrompt.Headset)
        assertEquals(listOf<FloatingItem>(offer), FloatingChooser.choose(now, on, null, emptyList(), null, emptySet(), null, offer))
        assertTrue(FloatingChooser.choose(now, on, song, emptyList(), null, emptySet(), null, offer).single() is FloatingItem.Media)
        assertTrue(FloatingChooser.choose(now, FloatingSources(), null, emptyList(), null, emptySet(), null, offer).isEmpty())
        // The alarm window is the user's.
        val alarm = now + Duration.ofHours(8)
        assertTrue(FloatingChooser.choose(now, FloatingSources(alarmWindowHours = 6), null, emptyList(), alarm, emptySet()).isEmpty())
    }

    @Test fun newestLiveActivityFirst() {
        val items = choose(live = listOf(timer(1, postTime = 10), timer(2, postTime = 20)))
        assertEquals("k2", (items[0] as FloatingItem.Live).activity.key)
    }

    @Test fun alarmWindow() {
        assertTrue(choose(alarm = now + Duration.ofHours(13)).isEmpty())
        assertTrue(choose(alarm = now - Duration.ofMinutes(1)).isEmpty())
    }

    @Test fun dismissedFallsThroughAndSourcesCanBeOff() {
        val media = FloatingItem.Media(song)
        assertTrue(choose(song, listOf(timer(1)), dismissed = setOf(media.key)).single() is FloatingItem.Live)
        assertTrue(choose(song, listOf(timer(1)), sources = FloatingSources(media = false)).single() is FloatingItem.Live)
        assertTrue(choose(song, listOf(timer(1)), sources = FloatingSources(timers = false)).single() is FloatingItem.Media)
        // A new track is a new key, so it shows again.
        assertTrue(choose(song.copy(title = "Next"), dismissed = setOf(media.key)).single() is FloatingItem.Media)
    }

    @Test fun positionFollowsThePlayersClock() {
        val track = song.copy(durationMs = 100_000, positionMs = 10_000, positionAt = 1_000, speed = 2f)
        assertEquals(14_000, track.positionAt(3_000))
        assertEquals(100_000, track.positionAt(1_000_000))
        assertEquals(10_000, track.copy(playing = false).positionAt(3_000))
    }
}

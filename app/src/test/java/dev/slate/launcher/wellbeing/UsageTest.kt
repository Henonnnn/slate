package dev.slate.launcher.wellbeing

import dev.slate.launcher.wellbeing.UsageEvent.Kind.Paused
import dev.slate.launcher.wellbeing.UsageEvent.Kind.Resumed
import dev.slate.launcher.wellbeing.UsageEvent.Kind.ScreenOff
import dev.slate.launcher.wellbeing.UsageEvent.Kind.Unlocked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageTest {
    private val min = 60_000L
    private fun e(pkg: String, kind: UsageEvent.Kind, atMin: Long, cls: String = "Main") = UsageEvent(pkg, cls, kind, atMin * min)

    @Test fun sessionsMergeAcrossOwnActivitiesAndClipToWindow() {
        val events = listOf(
            // Started before the window (minute 0), runs into it.
            e("a", Resumed, -10), e("a", Paused, 5),
            // Moving between two of b's activities is one session.
            e("b", Resumed, 10, "One"), e("b", Resumed, 12, "Two"), e("b", Paused, 12, "One"), e("b", Paused, 20, "Two"),
            // Screen off ends it; still open at the end counts to the end.
            e("c", Resumed, 30), e("c", ScreenOff, 40),
            e("d", Resumed, 55),
        )
        val t = UsageMath.foregroundTime(events, 0, 60 * min)
        assertEquals(5 * min, t["a"])
        assertEquals(10 * min, t["b"])
        assertEquals(10 * min, t["c"])
        assertEquals(5 * min, t["d"])
    }

    @Test fun unlocksAndFormatting() {
        assertEquals(2, UsageMath.unlocks(listOf(e("x", Unlocked, 1), e("x", Unlocked, 2), e("x", Unlocked, 99)), 0, 60 * min))
        assertEquals("< 1 min", UsageMath.format(30_000))
        assertEquals("42 min", UsageMath.format(42 * min))
        assertEquals("1 h 05 min", UsageMath.format(65 * min))
    }

    @Test fun breakerDecisions() {
        val rule = BreakerRule(dailyLimitMinutes = 30)
        assertEquals(BreakerDecision.Open, Breaker.decide(null, 0, null, null))
        assertEquals(BreakerDecision.Open, Breaker.decide(rule, 100, grantedUntilMs = 200, usedTodayMs = 0))
        val pause = Breaker.decide(rule, 300, grantedUntilMs = 200, usedTodayMs = 31 * min) as BreakerDecision.Pause
        assertTrue(pause.overLimit)
        // Without usage access there's no number, so never "over".
        assertTrue(!(Breaker.decide(rule, 0, null, null) as BreakerDecision.Pause).overLimit)
    }
}

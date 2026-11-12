package dev.slate.launcher.wellbeing

import kotlinx.serialization.Serializable

/** Usage Breaker settings for one app (by package: usage is counted per package). */
@Serializable
data class BreakerRule(
    /** Optional daily budget; past it the pause says so more plainly. Null = no limit. */
    val dailyLimitMinutes: Int? = null,
)

/** What happens when a breaker app is opened from Slate. */
sealed interface BreakerDecision {
    data object Open : BreakerDecision

    data class Pause(
        /** Null without usage access. */
        val usedTodayMs: Long?,
        val limitMinutes: Int?,
    ) : BreakerDecision {
        val overLimit: Boolean get() = limitMinutes != null && usedTodayMs != null && usedTodayMs >= limitMinutes * 60_000L
    }
}

object Breaker {
    /** Choices offered on the pause screen, in minutes. */
    val GRANTS = listOf(1, 5, 15)

    /** Seconds of breathing before the open choices unlock. */
    const val PAUSE_SECONDS = 5

    fun decide(rule: BreakerRule?, nowMs: Long, grantedUntilMs: Long?, usedTodayMs: Long?): BreakerDecision = when {
        rule == null -> BreakerDecision.Open
        grantedUntilMs != null && nowMs < grantedUntilMs -> BreakerDecision.Open
        else -> BreakerDecision.Pause(usedTodayMs, rule.dailyLimitMinutes)
    }
}

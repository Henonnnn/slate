package dev.slate.launcher.wellbeing

/** A usage event reduced to what the maths needs. */
data class UsageEvent(val packageName: String, val className: String, val kind: Kind, val timeMs: Long) {
    enum class Kind { Resumed, Paused, ScreenOff, Unlocked }
}

/**
 * Turns the platform's usage events into foreground time, pure so it's tested.
 *
 * An app is in the foreground while any of its activities is resumed; switching between its own
 * activities doesn't split the time. Screen-off ends every session. Time is clipped to the
 * window, so pass events from a little before [startMs] to catch sessions already running.
 * A session still open at [endMs] counts up to [endMs].
 */
object UsageMath {
    fun foregroundTime(events: List<UsageEvent>, startMs: Long, endMs: Long): Map<String, Long> {
        val open = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()
        val total = HashMap<String, Long>()

        fun close(pkg: String, at: Long) {
            val from = since.remove(pkg) ?: return
            val clipped = minOf(at, endMs) - maxOf(from, startMs)
            if (clipped > 0) total[pkg] = (total[pkg] ?: 0L) + clipped
        }

        for (e in events.sortedBy { it.timeMs }) {
            if (e.timeMs > endMs) break
            when (e.kind) {
                UsageEvent.Kind.Resumed -> {
                    val set = open.getOrPut(e.packageName) { HashSet() }
                    if (set.isEmpty()) since[e.packageName] = e.timeMs
                    set += e.className
                }
                UsageEvent.Kind.Paused -> {
                    val set = open[e.packageName] ?: continue
                    set -= e.className
                    if (set.isEmpty()) close(e.packageName, e.timeMs)
                }
                UsageEvent.Kind.ScreenOff -> {
                    since.keys.toList().forEach { close(it, e.timeMs) }
                    open.clear()
                }
                UsageEvent.Kind.Unlocked -> Unit
            }
        }
        since.keys.toList().forEach { close(it, endMs) }
        return total
    }

    fun unlocks(events: List<UsageEvent>, startMs: Long, endMs: Long): Int =
        events.count { it.kind == UsageEvent.Kind.Unlocked && it.timeMs in startMs until endMs }

    /** "1 h 05 min", "42 min", "< 1 min". */
    fun format(ms: Long, hourUnit: String = "h", minuteUnit: String = "min", lessThanMinute: String = "< 1 min"): String {
        val minutes = ms / 60_000
        if (minutes < 1) return lessThanMinute
        val h = minutes / 60
        val m = minutes % 60
        return if (h == 0L) "$m $minuteUnit" else "$h $hourUnit %02d $minuteUnit".format(m)
    }
}

package dev.slate.launcher.widgets

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * How an upcoming event's time is described. Pure, so it's unit-tested; strings come in as
 * [Labels] so tests don't need resources.
 *
 * All-day events are stored at UTC midnight by the provider, so their dates are read in UTC.
 */
object CalendarFormat {

    data class Labels(
        val now: String,
        /** "In %d min". */
        val inMinutes: String,
        val today: String,
        val tomorrow: String,
        val allDay: String,
    )

    fun startDate(e: CalendarEvent, zone: ZoneId): LocalDate =
        if (e.allDay) e.begin.atZone(ZoneOffset.UTC).toLocalDate() else e.begin.atZone(zone).toLocalDate()

    fun hasEnded(e: CalendarEvent, now: Instant, zone: ZoneId): Boolean =
        if (e.allDay) {
            // Ends at local midnight of its (exclusive) end date.
            !e.end.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().isAfter(now)
        } else {
            !e.end.isAfter(now)
        }

    /**
     * Events grouped by the day they show under, in order. One that began before [today] and is
     * still running shows under today.
     */
    fun byDay(events: List<CalendarEvent>, today: LocalDate, zone: ZoneId): List<Pair<LocalDate, List<CalendarEvent>>> =
        events.groupBy { maxOf(startDate(it, zone), today) }.toSortedMap().map { (day, list) ->
            day to list.sortedWith(compareBy({ !it.allDay }, { it.begin }))
        }

    /**
     * The event worth a line on the home screen: the first timed one running now or starting
     * within [horizonHours], else an all-day one today.
     */
    fun nextForGlance(events: List<CalendarEvent>, now: Instant, zone: ZoneId, horizonHours: Long = 12): CalendarEvent? {
        val live = events.filterNot { hasEnded(it, now, zone) }
        val horizon = now.plus(horizonHours, ChronoUnit.HOURS)
        return live.firstOrNull { !it.allDay && it.begin.isBefore(horizon) }
            ?: live.firstOrNull { it.allDay && startDate(it, zone) == now.atZone(zone).toLocalDate() }
    }

    fun describe(e: CalendarEvent, now: Instant, zone: ZoneId, time: DateTimeFormatter, labels: Labels): String {
        val today = now.atZone(zone).toLocalDate()
        val day = startDate(e, zone)
        val dayPrefix = when {
            day.isAfter(today.plusDays(1)) -> null
            day.isAfter(today) -> labels.tomorrow
            else -> null
        }
        if (e.allDay) return listOfNotNull(dayPrefix ?: labels.today, labels.allDay).joinToString(" · ")
        val start = e.begin.atZone(zone)
        val end = e.end.atZone(zone)
        if (!e.begin.isAfter(now)) return "${labels.now} · ${time.format(start)} – ${time.format(end)}"
        val minutes = ChronoUnit.MINUTES.between(now, e.begin)
        if (minutes < 60 && dayPrefix == null) return labels.inMinutes.format(minutes.coerceAtLeast(1))
        val range = "${time.format(start)} – ${time.format(end)}"
        return if (dayPrefix != null) "$dayPrefix · $range" else range
    }
}

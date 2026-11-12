package dev.slate.launcher.widgets

import dev.slate.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarFormatTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val labels = CalendarFormat.Labels("Now", "In %d min", "Today", "Tomorrow", "All day")
    private val now = at(2026, 10, 30, 10, 0)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Instant = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()
    private fun utcMidnight(y: Int, m: Int, d: Int): Instant = LocalDateTime.of(y, m, d, 0, 0).toInstant(ZoneOffset.UTC)
    private fun event(begin: Instant, end: Instant, allDay: Boolean = false) = CalendarEvent(1, "x", begin, end, allDay, 0)
    private fun describe(e: CalendarEvent) = CalendarFormat.describe(e, now, zone, time, labels)

    @Test fun groupsByDayWithRunningEventsUnderToday() {
        val today = LocalDate.of(2026, 10, 30)
        val running = event(at(2026, 10, 28, 9, 0), at(2026, 10, 31, 9, 0))
        val later = event(at(2026, 10, 30, 15, 0), at(2026, 10, 30, 16, 0))
        val allDay = event(utcMidnight(2026, 10, 30), utcMidnight(2026, 10, 31), allDay = true)
        val tomorrow = event(at(2026, 10, 31, 8, 0), at(2026, 10, 31, 9, 0))
        val days = CalendarFormat.byDay(listOf(tomorrow, later, running, allDay), today, zone)
        assertEquals(listOf(today, today.plusDays(1)), days.map { it.first })
        // All-day first, then by start.
        assertEquals(listOf(allDay, running, later), days[0].second)
    }

    @Test fun timedEvents() {
        assertEquals("Now · 09:30 – 10:30", describe(event(at(2026, 10, 30, 9, 30), at(2026, 10, 30, 10, 30))))
        assertEquals("In 25 min", describe(event(at(2026, 10, 30, 10, 25), at(2026, 10, 30, 11, 0))))
        assertEquals("14:00 – 15:00", describe(event(at(2026, 10, 30, 14, 0), at(2026, 10, 30, 15, 0))))
        assertEquals("Tomorrow · 09:00 – 09:30", describe(event(at(2026, 10, 31, 9, 0), at(2026, 10, 31, 9, 30))))
    }

    @Test fun allDayEventsUseUtcDates() {
        val today = event(utcMidnight(2026, 10, 30), utcMidnight(2026, 10, 31), allDay = true)
        val tomorrow = event(utcMidnight(2026, 10, 31), utcMidnight(2026, 11, 1), allDay = true)
        assertEquals("Today · All day", describe(today))
        assertEquals("Tomorrow · All day", describe(tomorrow))
        assertFalse(CalendarFormat.hasEnded(today, now, zone))
        val yesterday = event(utcMidnight(2026, 10, 29), utcMidnight(2026, 10, 30), allDay = true)
        assertTrue(CalendarFormat.hasEnded(yesterday, now, zone))
    }

    @Test fun weatherCodesAndUnits() {
        assertEquals(R.string.weather_clear, WeatherCodes.describe(0))
        assertEquals(R.string.weather_thunderstorm, WeatherCodes.describe(99))
        assertEquals(R.string.weather_unknown, WeatherCodes.describe(42))
        assertTrue(WeatherCodes.usesFahrenheit(Locale.US))
        assertFalse(WeatherCodes.usesFahrenheit(Locale.GERMANY))
    }

    @Test fun glanceSkipsFinishedAndFarEvents() {
        val past = event(at(2026, 10, 30, 8, 0), at(2026, 10, 30, 9, 0))
        val next = event(at(2026, 10, 30, 11, 0), at(2026, 10, 30, 12, 0))
        val far = event(at(2026, 10, 31, 9, 0), at(2026, 10, 31, 10, 0))
        assertEquals(next, CalendarFormat.nextForGlance(listOf(past, next, far), now, zone))
        assertEquals(null, CalendarFormat.nextForGlance(listOf(past, far), now, zone))
    }

    @Test fun glancePrefersTimedOverAllDay() {
        val allDay = event(utcMidnight(2026, 10, 30), utcMidnight(2026, 10, 31), allDay = true)
        val timed = event(at(2026, 10, 30, 15, 0), at(2026, 10, 30, 16, 0))
        assertEquals(timed, CalendarFormat.nextForGlance(listOf(allDay, timed), now, zone))
        assertEquals(allDay, CalendarFormat.nextForGlance(listOf(allDay), now, zone))
    }

    @Test fun skyGroupsWeatherCodes() {
        assertEquals(WeatherCodes.Sky.Clear, WeatherCodes.sky(0))
        assertEquals(WeatherCodes.Sky.Cloudy, WeatherCodes.sky(3))
        assertEquals(WeatherCodes.Sky.Rain, WeatherCodes.sky(61))
        assertEquals(WeatherCodes.Sky.Rain, WeatherCodes.sky(95))
        assertEquals(WeatherCodes.Sky.Snow, WeatherCodes.sky(73))
    }
}

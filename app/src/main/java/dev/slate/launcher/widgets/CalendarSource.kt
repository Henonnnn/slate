package dev.slate.launcher.widgets

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Immutable
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Instant,
    val end: Instant,
    val allDay: Boolean,
    val color: Int,
) {
    fun viewIntent(): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toEpochMilli())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Upcoming events from the platform calendar provider (READ_CALENDAR). Read on-device only. */
class CalendarSource(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events that haven't ended yet, through the end of tomorrow, or for [days] days counting today. */
    suspend fun upcoming(now: Instant = Instant.now(), limit: Int = 6, days: Long = 2): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        return withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val until = ZonedDateTime.ofInstant(now, zone).toLocalDate().plusDays(days).atStartOfDay(zone).toInstant()
            // Look back a day so all-day events (stored at UTC midnight) aren't missed.
            val uri = Instances.CONTENT_URI.buildUpon()
                .appendPath((now - Duration.ofDays(1)).toEpochMilli().toString())
                .appendPath(until.toEpochMilli().toString())
                .build()
            val projection = arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.DISPLAY_COLOR)
            try {
                context.contentResolver.query(uri, projection, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            val allDay = c.getInt(4) == 1
                            val event = CalendarEvent(
                                eventId = c.getLong(0),
                                title = c.getString(1)?.trim().orEmpty(),
                                begin = Instant.ofEpochMilli(c.getLong(2)),
                                end = Instant.ofEpochMilli(c.getLong(3)),
                                allDay = allDay,
                                color = c.getInt(5),
                            )
                            if (!CalendarFormat.hasEnded(event, now, zone)) add(event)
                        }
                    }
                }.orEmpty()
                    .sortedWith(compareBy({ CalendarFormat.startDate(it, zone) }, { !it.allDay }, { it.begin }))
                    .take(limit)
            } catch (e: SecurityException) {
                emptyList()
            } catch (e: IllegalArgumentException) {
                Log.w("CalendarSource", "Calendar query failed", e)
                emptyList()
            }
        }
    }

    /** Emits whenever any calendar data changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        }.isSuccess
        awaitClose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }
}

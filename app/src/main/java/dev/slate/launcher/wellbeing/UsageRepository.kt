package dev.slate.launcher.wellbeing

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** One day of screen time. */
data class DayUsage(val date: LocalDate, val totalMs: Long, val byPackage: Map<String, Long>, val unlocks: Int)

/**
 * Screen time from the platform's usage events. Needs "Usage access" (a special app-op the user
 * grants in settings); without it every query returns null and the UI explains why.
 * Everything is computed on the device and never stored or sent.
 */
class UsageRepository(private val context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)

    fun hasAccess(): Boolean =
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    /** Straight to Slate's switch where supported, else the list. */
    fun settingsIntents(): List<Intent> = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
    )

    fun openSettings() {
        for (intent in settingsIntents()) {
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    suspend fun today(): DayUsage? = days(1)?.lastOrNull()

    /** Today's foreground time for one package; null without access. */
    suspend fun usedToday(packageName: String): Long? = today()?.byPackage?.get(packageName) ?: if (hasAccess()) 0L else null

    /** The last [count] days, oldest first, today last (up to now). */
    suspend fun days(count: Int): List<DayUsage>? = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val first = today.minusDays(count - 1L)
        val windowStart = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        // A few hours earlier so sessions already running at midnight are caught.
        val events = read(windowStart - LOOKBACK_MS, now)
        (0 until count).map { i ->
            val date = first.plusDays(i.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
            val byPackage = UsageMath.foregroundTime(events, start, end).filterKeys { it !in ignored }
            DayUsage(date, byPackage.values.sum(), byPackage, UsageMath.unlocks(events, start, end))
        }
    }

    /** True if [packageName] has been in the foreground within the last minute. */
    suspend fun isForeground(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext true // Can't tell; err on reminding.
        val now = System.currentTimeMillis()
        val t = UsageMath.foregroundTime(read(now - LOOKBACK_MS, now), now - 60_000, now)
        (t[packageName] ?: 0L) > 0
    }

    private fun read(from: Long, to: Long): List<UsageEvent> {
        val out = ArrayList<UsageEvent>()
        val events = runCatching { usage.queryEvents(from, to) }.getOrNull() ?: return out
        val e = UsageEvents.Event()
        while (events.getNextEvent(e)) {
            val kind = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Kind.Resumed
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> UsageEvent.Kind.Paused
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEvent.Kind.ScreenOff
                UsageEvents.Event.KEYGUARD_HIDDEN -> UsageEvent.Kind.Unlocked
                else -> continue
            }
            out += UsageEvent(e.packageName.orEmpty(), e.className.orEmpty(), kind, e.timeStamp)
        }
        return out
    }

    /** Home screens aren't "screen time". */
    private val ignored: Set<String> by lazy {
        val pm = context.packageManager
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }
        homes.toSet() + context.packageName + "com.android.systemui"
    }

    private companion object {
        const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
    }
}

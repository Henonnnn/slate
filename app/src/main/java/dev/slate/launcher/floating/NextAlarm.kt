package dev.slate.launcher.floating

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant

/** The next alarm-clock alarm any app has set (public, no permission), updated when it changes. */
fun nextAlarm(context: Context): Flow<Instant?> = callbackFlow {
    val alarms = context.getSystemService(AlarmManager::class.java)
    fun read() = alarms.nextAlarmClock?.triggerTime?.let(Instant::ofEpochMilli)
    trySend(read())
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            trySend(read())
        }
    }
    ContextCompat.registerReceiver(context, receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    awaitClose { context.unregisterReceiver(receiver) }
}

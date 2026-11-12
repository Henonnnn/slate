package dev.slate.launcher.wellbeing

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.slate.launcher.MainActivity
import dev.slate.launcher.R
import dev.slate.launcher.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The "time's up" nudge after a Usage Breaker grant. An inexact alarm fires when the granted
 * minutes end; if the app is still on screen, a quiet notification says so and taps through to
 * home. Slate can't close or cover another app (that would need an overlay or accessibility
 * service), so this is a reminder, not a lock.
 */
object BreakerReminder {
    private const val CHANNEL = "breaker"
    private const val EXTRA_PACKAGE = "package"
    private const val EXTRA_LABEL = "label"

    fun schedule(context: Context, packageName: String, label: String, atMs: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        // A short window instead of an exact alarm: no special permission, a few seconds of slack.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, atMs, 30_000L, pendingIntent(context, packageName, label))
    }

    fun cancel(context: Context, packageName: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, packageName, ""))
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(context: Context, packageName: String, label: String): PendingIntent {
        val intent = Intent(context, Receiver::class.java)
            .setData(android.net.Uri.fromParts("breaker", packageName, null))
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_LABEL, label)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
            val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    if (canNotify(context) && context.container.usageRepository.isForeground(pkg)) notify(context, pkg, label)
                } finally {
                    pending.finish()
                }
            }
        }

        private fun notify(context: Context, pkg: String, label: String) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.breaker_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
            val home = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.breaker_times_up, label))
                .setContentText(context.getString(R.string.breaker_times_up_body))
                .setContentIntent(home)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(pkg.hashCode(), notification) }
        }
    }
}

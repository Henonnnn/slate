package dev.slate.launcher.notifications

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationAccess {
    fun isGranted(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Straight to Slate's own switch where the platform supports it, else the list of listeners. */
    fun settingsIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, NotificationService::class.java).flattenToString(),
            ),
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    )

    fun open(context: Context) {
        for (intent in settingsIntents(context)) {
            val ok = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return
        }
    }

    /** Android doesn't reconnect a listener by itself after a crash or force stop. */
    fun reconnect(context: Context) {
        runCatching { NotificationListenerService.requestRebind(ComponentName(context, NotificationService::class.java)) }
    }
}

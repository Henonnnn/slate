package dev.slate.launcher

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Sends a PendingIntent that opens another app's screen (a notification's tap target, a media
 * player). Slate is in the foreground, but since Android 14 the sender must also opt in to the
 * activity start or it's silently blocked.
 */
fun PendingIntent.sendFromForeground(context: Context, fillIn: Intent? = null): Boolean {
    val options = ActivityOptions.makeBasic()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        @Suppress("DEPRECATION")
        options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
    }
    return try {
        send(context, 0, fillIn, null, null, null, options.toBundle())
        true
    } catch (e: PendingIntent.CanceledException) {
        false
    }
}

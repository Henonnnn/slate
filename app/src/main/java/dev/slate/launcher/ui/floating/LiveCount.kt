package dev.slate.launcher.ui.floating

import android.app.Notification
import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import dev.slate.launcher.floating.LiveActivity

/**
 * The running count of a timer or stopwatch, or null when the activity doesn't count.
 *
 * Android has no API for other apps' timers, so the count comes from the notification itself:
 * either it uses the standard chronometer (its `when` is the base), or, like the AOSP clock, it
 * carries its own layout with a [Chronometer] inside. That one is inflated and its chronometer
 * lifted out and restyled; it keeps ticking, or stays still while paused, exactly as its app set it.
 */
@Composable
fun rememberLiveCount(activity: LiveActivity): Chronometer? {
    val context = LocalContext.current
    return remember(activity.key, activity.postTime) { chronometerFor(context, activity) }
}

/** Shows a count from [rememberLiveCount] in Slate's type. Each one belongs in one place only. */
@Composable
fun LiveCount(chronometer: Chronometer, style: TextStyle, color: Color) {
    val typeface = LocalFontFamilyResolver.current.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal).value as? Typeface
    AndroidView(
        factory = { chronometer },
        update = { view ->
            view.setTextColor(color.toArgb())
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
            if (typeface != null) view.typeface = typeface
            // Digits of equal width, so the count doesn't shiver as it ticks.
            view.fontFeatureSettings = "tnum"
        },
    )
}

private fun chronometerFor(context: Context, activity: LiveActivity): Chronometer? {
    val n = activity.source ?: return null
    val view = if (n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) {
        Chronometer(context).apply {
            // `when` is wall-clock time; a chronometer counts from elapsed realtime.
            base = n.`when` - (System.currentTimeMillis() - SystemClock.elapsedRealtime())
            isCountDown = n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
            start()
        }
    } else {
        @Suppress("DEPRECATION") // Still how a custom notification layout is read.
        val remote = n.contentView ?: n.bigContentView ?: return null
        try {
            val root = remote.apply(context.applicationContext, FrameLayout(context))
            findChronometer(root)?.also { (it.parent as? ViewGroup)?.removeView(it) }
        } catch (e: RuntimeException) {
            // The other app's layout wouldn't inflate here; its title and text still show.
            Log.w("LiveCount", "Cannot read the count of ${activity.packageName}", e)
            null
        }
    } ?: return null
    return view.apply {
        background = null
        setPadding(0, 0, 0, 0)
        includeFontPadding = false
        visibility = View.VISIBLE
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

private fun findChronometer(view: View): Chronometer? = when (view) {
    is Chronometer -> view
    is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findChronometer(view.getChildAt(it)) }
    else -> null
}

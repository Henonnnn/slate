package dev.slate.launcher.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot

class SlateWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        SlateWidgetHostView(context)
}

/**
 * Host view that notices a long press anywhere on the widget, even over the widget's own
 * buttons, and steals the gesture from them (like launchers do) so the widget menu can open.
 */
@SuppressLint("ClickableViewAccessibility") // Only swallows the rest of a long press; clicks stay the widget's own.
class SlateWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val timeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false

    private val check = Runnable {
        if (isAttachedToWindow && onLongPress != null) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = ev.x
                downY = ev.y
                postDelayed(check, timeout)
            }
            MotionEvent.ACTION_MOVE -> if (hypot(ev.x - downX, ev.y - downY) > slop) removeCallbacks(check)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(check)
        }
        // Once the long press fired, take the rest of the gesture so the widget doesn't also click.
        return longPressed
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (longPressed) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) longPressed = false
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        removeCallbacks(check)
    }
}

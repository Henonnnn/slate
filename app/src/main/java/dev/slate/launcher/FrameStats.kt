package dev.slate.launcher

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics

/**
 * Opt-in frame timing for measuring smoothness on a real device, in any build type:
 *
 *     adb shell setprop log.tag.SlateFrames DEBUG
 *     adb logcat -s SlateFrames
 *
 * Logs percentiles and the share of frames that missed their own deadline (which follows the
 * display's current refresh rate, 60–120 Hz) every [WINDOW] frames.
 * Off (no listener at all) unless the log tag is enabled when the activity starts.
 */
object FrameStats {
    private const val TAG = "SlateFrames"
    private const val WINDOW = 240

    fun attachIfEnabled(activity: Activity) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        val thread = HandlerThread("frame-stats").apply { start() }
        val durations = LongArray(WINDOW)
        val deadlines = LongArray(WINDOW)
        var count = 0
        activity.window.addOnFrameMetricsAvailableListener({ _, metrics, _ ->
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@addOnFrameMetricsAvailableListener
            durations[count] = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            // DEADLINE arrived in Android 12; before that, assume 60 Hz.
            deadlines[count] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) metrics.getMetric(FrameMetrics.DEADLINE) else 16_666_667L
            count++
            if (count == WINDOW) {
                val sorted = durations.sorted()
                fun p(q: Double) = sorted[((sorted.size - 1) * q).toInt()] / 1e6
                val missed = (0 until WINDOW).count { durations[it] > deadlines[it] }
                Log.d(TAG, "frames=$WINDOW deadline~%.1fms p50=%.1f p90=%.1f p99=%.1f missed=%d (%.0f%%)".format(
                    deadlines.sorted()[WINDOW / 2] / 1e6, p(0.5), p(0.9), p(0.99), missed, 100.0 * missed / WINDOW,
                ))
                count = 0
            }
        }, Handler(thread.looper))
    }
}

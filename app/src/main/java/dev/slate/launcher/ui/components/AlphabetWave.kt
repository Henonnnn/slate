package dev.slate.launcher.ui.components

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The alphabet's geometry and motion, free of Android types so it can be tested. All lengths in px. */
object AlphabetWave {
    /** Sections stretch over at least half the free height, however few there are. */
    fun blockHeight(sections: Int, maxStep: Float, available: Float): Float =
        (sections * maxStep).coerceIn(available / 2f, available)

    /** How far down the list starts, which is also where a jump puts the chosen section. */
    fun listTop(screenHeight: Float, offset: Float, min: Float, max: Float): Float =
        (screenHeight * 0.5f - offset).coerceIn(min, max)

    /**
     * Where the block rests: centred between the list and the bottom gap when it fits, otherwise
     * as low as it can go, or centred in the band if that leaves less room below than above.
     */
    fun restingTop(listTop: Float, bandTop: Float, bandBottom: Float, block: Float, screenHeight: Float, bottomGap: Float): Float {
        val start = bandTop + listTop * 1.1f
        val end = min(screenHeight - bottomGap, bandBottom)
        if (start + block <= end) return (start + end - block) / 2f
        val low = max(end - block, bandTop)
        return if (low - bandTop > bandBottom - (low + block)) low else (bandTop + bandBottom - block) / 2f
    }

    /** The block slides just enough to stay under a finger that leaves it. */
    fun follow(top: Float, block: Float, finger: Float, bandTop: Float, bandBottom: Float): Float = when {
        finger < top -> max(finger, bandTop)
        finger > top + block -> min(finger, bandBottom) - block
        else -> top
    }

    fun indexAt(y: Float, top: Float, step: Float, count: Int): Int =
        ((y - top) / step).toInt().coerceIn(0, count - 1)

    /**
     * How far each letter leans out, 0–1. [spread] is the wave's height over the block's, so
     * pulling the peak further out also widens it.
     */
    fun bulge(index: Int, count: Int, finger: Float, top: Float, block: Float, spread: Float): Float {
        if (spread <= 0f) return 0f
        val step = block / count
        val d = (index + 1f) / count - (step / 2f + finger - top) / block
        return 2f.pow(-4f * d * d / spread)
    }

    /** The wave's peak: past the finger when it drags inward, never into the handle's margin. */
    fun peak(pressedOffset: Float, fingerFromEdge: Float, limit: Float): Float =
        (pressedOffset + fingerFromEdge).coerceAtMost(limit).coerceAtLeast(0f)

    /** The handle sticks to the current letter's centre and only moves on near the next one. */
    fun handleY(finger: Float, top: Float, step: Float, index: Int): Float {
        val centre = (index + 0.5f) * step + top
        val offset = (finger - centre) / step
        if (offset !in -0.5f..0.5f) return finger
        return (offset * 2f).pow(3) / 2f * step + centre
    }

    /** One letter's distance from home during the entrance ripple, as a share of the way off screen. */
    fun entrance(progress: Float, index: Int, count: Int): Float {
        if (progress >= 1f) return 0f
        val last = (count - 1).toFloat()
        val along = if (count > 1) index / last else 0f
        val pace = 1f / (last * STAGGER + 1f)
        val t = ((progress - STAGGER * pace * index) / (pace * (1f - 0.7f * along))).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t)
        val u = t - 1f
        val overshoot = (11f * u + 10f) * u * u + 1f
        val wobble = if (t <= 0.5f) 0f else sin((t - 0.5f) / 0.5f * PI.toFloat()).pow(2) * -0.45f
        return (eased - (overshoot + wobble)) * (0.6f - 0.6f * along) + (1f - eased)
    }

    private const val STAGGER = 0.05f
}

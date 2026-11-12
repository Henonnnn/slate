package dev.slate.launcher.ui

import dev.slate.launcher.ui.home.rubberBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RubberBandTest {
    @Test fun `nothing pulled, nothing moved`() = assertEquals(0f, rubberBand(0f, 500f), 0f)

    @Test fun `direction is kept`() = assertEquals(rubberBand(100f, 500f), -rubberBand(-100f, 500f), 0.001f)

    @Test fun `it gets harder to pull the further it goes`() {
        val first = rubberBand(100f, 500f)
        val second = rubberBand(200f, 500f) - first
        assertTrue(second < first)
    }

    @Test fun `stops at a fifth of the limit`() {
        assertEquals(100f, rubberBand(500f, 500f), 0.01f)
        assertEquals(100f, rubberBand(900f, 500f), 0.01f)
    }
}

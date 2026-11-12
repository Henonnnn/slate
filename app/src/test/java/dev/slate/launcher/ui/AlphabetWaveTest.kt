package dev.slate.launcher.ui

import dev.slate.launcher.ui.components.AlphabetWave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphabetWaveTest {
    @Test fun `few sections still spread over half the free height`() {
        assertEquals(500f, AlphabetWave.blockHeight(5, 19f, 1000f), 0f)
        assertEquals(700f, AlphabetWave.blockHeight(35, 20f, 1000f), 0f)
        assertEquals(1000f, AlphabetWave.blockHeight(90, 20f, 1000f), 0f)
    }

    @Test fun `block rests centred between the list and the bottom gap`() {
        val top = AlphabetWave.restingTop(listTop = 260f, bandTop = 40f, bandBottom = 960f, block = 460f, screenHeight = 1000f, bottomGap = 64f)
        val start = 40f + 260f * 1.1f
        assertEquals((start + 936f - 460f) / 2f, top, 0.01f)
    }

    @Test fun `a block too tall for that space stays on screen`() {
        val top = AlphabetWave.restingTop(260f, 40f, 960f, 800f, 1000f, 64f)
        assertTrue(top >= 40f && top + 800f <= 960f)
    }

    @Test fun `block follows a finger that leaves it, up to the band`() {
        assertEquals(300f, AlphabetWave.follow(300f, 400f, 500f, 0f, 1000f), 0f)
        assertEquals(100f, AlphabetWave.follow(300f, 400f, 100f, 0f, 1000f), 0f)
        assertEquals(500f, AlphabetWave.follow(300f, 400f, 900f, 0f, 1000f), 0f)
        assertEquals(600f, AlphabetWave.follow(300f, 400f, 2000f, 0f, 1000f), 0f)
    }

    @Test fun `letter under the finger leans out fully and it falls off evenly`() {
        val y = 100f + 40f * 3.5f
        assertEquals(1f, AlphabetWave.bulge(3, 10, y, 100f, 400f, 0.2f), 0.0001f)
        val near = AlphabetWave.bulge(2, 10, y, 100f, 400f, 0.2f)
        assertEquals(near, AlphabetWave.bulge(4, 10, y, 100f, 400f, 0.2f), 0.0001f)
        assertTrue(near < 1f && AlphabetWave.bulge(0, 10, y, 100f, 400f, 0.2f) < near)
    }

    @Test fun `a higher peak is a wider wave`() {
        assertTrue(AlphabetWave.bulge(2, 10, 240f, 100f, 400f, 0.4f) > AlphabetWave.bulge(2, 10, 240f, 100f, 400f, 0.1f))
    }

    @Test fun `no pull means no wave`() {
        assertEquals(0f, AlphabetWave.bulge(3, 10, 200f, 100f, 400f, 0f), 0f)
    }

    @Test fun `peak follows the finger inward up to its limit`() {
        assertEquals(100f, AlphabetWave.peak(64f, 36f, 300f), 0f)
        assertEquals(300f, AlphabetWave.peak(64f, 900f, 300f), 0f)
    }

    @Test fun `handle sticks to a letter and meets the finger at the edges`() {
        assertEquals(120f, AlphabetWave.handleY(120f, 100f, 40f, 0), 0.01f)
        assertTrue(AlphabetWave.handleY(128f, 100f, 40f, 0) < 122f)
        assertEquals(140f, AlphabetWave.handleY(140f, 100f, 40f, 0), 0.01f)
        assertEquals(500f, AlphabetWave.handleY(500f, 100f, 40f, 0), 0f)
    }

    @Test fun `index stays on the alphabet past both ends`() {
        assertEquals(0, AlphabetWave.indexAt(-50f, 0f, 10f, 27))
        assertEquals(26, AlphabetWave.indexAt(999f, 0f, 10f, 27))
        assertEquals(3, AlphabetWave.indexAt(35f, 0f, 10f, 27))
    }

    @Test fun `entrance settles every letter home`() {
        (0 until 27).forEach { assertEquals(0f, AlphabetWave.entrance(1f, it, 27), 0f) }
        assertTrue(AlphabetWave.entrance(0f, 26, 27) > 0f)
    }
}

package dev.slate.launcher.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconGlyphTest {
    private val red = 0xFFCC2222.toInt()
    private val white = 0xFFFFFFFF.toInt()

    /** A 10×10 picture: [ground] everywhere, [mark] in the middle 4×4. */
    private fun picture(ground: Int, mark: Int) = IntArray(100) { i ->
        val x = i % 10
        val y = i / 10
        if (x in 3..6 && y in 3..6) mark else ground
    }

    @Test fun aLogoOnNothingKeepsItsOutline() {
        val mask = IconGlyph.mask(picture(0, white), 10, 10, null)!!
        assertEquals(0, mask[0])
        assertEquals(255, mask[44])
    }

    @Test fun onAPlateTheMarkIsTheGlyph() {
        val mask = IconGlyph.mask(picture(red, white), 10, 10, null)!!
        assertEquals(0, mask[0])
        assertTrue(mask[44] > 200)
    }

    @Test fun aFlatPictureGivesNothing() {
        assertNull(IconGlyph.mask(picture(red, red), 10, 10, null))
        assertNull(IconGlyph.mask(IntArray(100), 10, 10, null))
    }
}

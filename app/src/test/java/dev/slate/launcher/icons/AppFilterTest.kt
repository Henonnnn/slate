package dev.slate.launcher.icons

import dev.slate.launcher.icons.AppFilter.Companion.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppFilterTest {
    @Test fun parsesItemsAndFallbackTreatment() {
        val filter = AppFilter.parse(
            sequenceOf(
                Tag("resources", emptyMap()),
                Tag("iconback", mapOf("img2" to "back_b", "img1" to "back_a")),
                Tag("iconmask", mapOf("img1" to "mask")),
                Tag("iconupon", mapOf("img1" to "upon")),
                Tag("scale", mapOf("factor" to "0.8")),
                Tag("item", mapOf("component" to "ComponentInfo{com.android.deskclock/com.android.deskclock.DeskClock}", "drawable" to "clock")),
                Tag("item", mapOf("component" to "ComponentInfo{org.app/.Main}", "drawable" to "app")),
                // First mapping wins; broken entries are skipped.
                Tag("item", mapOf("component" to "ComponentInfo{org.app/.Main}", "drawable" to "other")),
                Tag("item", mapOf("component" to "ComponentInfo{broken}", "drawable" to "x")),
                Tag("item", mapOf("component" to "ComponentInfo{org.app/.Second}", "drawable" to "")),
            ),
        )
        assertEquals("clock", filter.drawableFor("com.android.deskclock", "com.android.deskclock.DeskClock"))
        assertEquals("app", filter.drawableFor("org.app", "org.app.Main"))
        assertNull(filter.drawableFor("org.app", "org.app.Second"))
        assertEquals(listOf("back_a", "back_b"), filter.backs)
        assertEquals("mask", filter.mask)
        assertEquals("upon", filter.upon)
        assertEquals(0.8f, filter.scale)
    }

    @Test fun drawableListAndComponents() {
        val names = AppFilter.parseDrawableList(sequenceOf(Tag("category", mapOf("title" to "A")), Tag("item", mapOf("drawable" to "a")), Tag("item", mapOf("drawable" to "a")), Tag("item", mapOf("drawable" to "b"))))
        assertEquals(listOf("a", "b"), names)
        assertEquals("p/p.X", AppFilter.parseComponent("ComponentInfo{p/.X}"))
        assertNull(AppFilter.parseComponent("ComponentInfo{p/}"))
    }
}

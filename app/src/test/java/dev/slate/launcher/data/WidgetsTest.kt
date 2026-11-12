package dev.slate.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WidgetsTest {
    private val a = WidgetRef.app(1)
    private val b = WidgetRef.app(2)
    private val cal = WidgetRef.calendar

    private fun ids(stacks: List<WidgetStack>) = stacks.map { s -> s.items.map { it.key } }

    @Test fun addClampsAndRefusesDuplicates() {
        var s = Widgets.add(emptyList(), a, 10)
        assertEquals(Widgets.MIN_HEIGHT_DP, s.single().heightDp)
        assertSame(s, Widgets.add(s, a, 200))
        s = Widgets.add(s, cal, 9999)
        assertEquals(Widgets.MAX_HEIGHT_DP, s[1].heightDp)
    }

    @Test fun stackAndUnstack() {
        var s = listOf(WidgetStack(listOf(a), 120), WidgetStack(listOf(b), 200), WidgetStack(listOf(cal), 90))
        s = Widgets.stackWithAbove(s, b)
        assertEquals(listOf(listOf("app:1", "app:2"), listOf("calendar")), ids(s))
        assertEquals(120, s[0].heightDp)
        s = Widgets.unstack(s, a)
        assertEquals(listOf(listOf("app:2"), listOf("app:1"), listOf("calendar")), ids(s))
        // Top stack can't merge upwards.
        assertSame(s, Widgets.stackWithAbove(s, b))
    }

    @Test fun removeDropsEmptyStacksAndMoveStaysInBounds() {
        var s = listOf(WidgetStack(listOf(a, b), 120), WidgetStack(listOf(cal), 90))
        s = Widgets.move(s, cal, -1)
        assertEquals(listOf(listOf("calendar"), listOf("app:1", "app:2")), ids(s))
        assertSame(s, Widgets.move(s, cal, -1))
        s = Widgets.remove(Widgets.remove(s, a), b)
        assertEquals(listOf(listOf("calendar")), ids(s))
        assertEquals(setOf<Int>(), Widgets.appWidgetIds(s))
    }
}

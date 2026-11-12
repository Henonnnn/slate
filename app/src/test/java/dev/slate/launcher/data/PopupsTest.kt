package dev.slate.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class PopupsTest {
    private val a = PopupItem.app("a")
    private val b = PopupItem.app("b")
    private val s = PopupItem.shortcut("pkg/id/0")

    @Test fun addDeduplicatesAndRefusesSelf() {
        var p = Popups.add(emptyMap(), "owner", a)
        p = Popups.add(p, "owner", a)
        p = Popups.add(p, "owner", s)
        assertEquals(listOf(a, s), p["owner"])
        assertSame(p, Popups.add(p, "owner", PopupItem.app("owner")))
    }

    @Test fun removingLastItemDropsThePopup() {
        val p = Popups.remove(mapOf("owner" to listOf(a)), "owner", a)
        assertFalse("owner" in p)
    }

    @Test fun reorderKeepsHiddenItemsAtTheEnd() {
        val p = mapOf("owner" to listOf(a, b, s))
        // `b` is missing (not shown), so the visible order only has a and s.
        assertEquals(listOf(s, a, b), Popups.reorder(p, "owner", listOf(s, a))["owner"])
    }

    @Test fun resolveSkipsMissingTargets() {
        val resolved = Popups.resolve(listOf(a, s, b), app = { if (it == "a") "App A" else null }, shortcut = { "Shortcut" })
        assertEquals(listOf(a, s), resolved.map { it.item })
    }

    @Test fun roundTripsThroughSettingsJson() {
        val settings = LauncherSettings(popups = mapOf("owner" to listOf(a, s)))
        val json = SettingsJson.encodeToString(LauncherSettings.serializer(), settings)
        assertEquals(settings, SettingsJson.decodeFromString(LauncherSettings.serializer(), json))
        // Older files without the field still decode.
        assertEquals(emptyMap<String, List<PopupItem>>(), SettingsJson.decodeFromString(LauncherSettings.serializer(), "{}").popups)
    }

    @Test fun `a folder in favourites joins them and leaves everything when deleted`() {
        val created = Folders.create(LauncherSettings(favourites = listOf("a")), PopupFolder("1", "Work"), inFavourites = true)
        assertEquals(listOf("a", "folder:1"), created.favourites)
        val filled = created.copy(popups = mapOf("folder:1" to listOf(PopupItem.app("b"))), labelOverrides = mapOf("folder:1" to "Job"))
        val gone = Folders.delete(filled, "1")
        assertEquals(listOf("a"), gone.favourites)
        assertEquals(emptyList<PopupFolder>(), gone.folders)
        assertEquals(emptyMap<String, List<PopupItem>>(), gone.popups)
        assertEquals(emptyMap<String, String>(), gone.labelOverrides)
    }
}

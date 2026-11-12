package dev.slate.launcher.apps

import android.content.ComponentName
import android.os.UserHandle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class HomeListBuilderTest {

    private val collator = AlphabetIndex.collatorFor(Locale.ENGLISH)

    private fun app(label: String) = AppEntry(
        key = "pkg.$label/.Main/0",
        component = ComponentName("pkg.$label", ".Main"),
        user = USER,
        label = label,
        systemLabel = label,
        isWorkProfile = false,
        isQuiet = false,
        versionStamp = "1",
        bucket = AlphabetIndex.bucketOf(label),
    )

    private val maps = app("Maps")
    private val camera = app("Camera")
    private val zoom = app("Zoom")
    private val installed = listOf(zoom, maps, camera)

    @Test
    fun privateAppsStayOutOfTheMainLists() {
        val secret = app("Secret").copy(isPrivate = true)
        val lists = HomeListBuilder.build(installed + secret, listOf(secret.key), emptySet(), emptyMap(), collator)
        assertEquals(listOf("Camera", "Maps", "Zoom"), lists.apps.map { it.label })
        assertEquals(emptyList<AppEntry>(), lists.favourites)
        assertEquals(listOf("Secret"), lists.privateApps.map { it.label })
    }

    @Test
    fun favouritesKeepUserOrderAndSkipMissingApps() {
        val lists = HomeListBuilder.build(installed, listOf(zoom.key, "gone/.X/0", camera.key, zoom.key), emptySet(), emptyMap(), collator)
        assertEquals(listOf("Zoom", "Camera"), lists.favourites.map { it.label })
        assertEquals(listOf("Camera", "Maps", "Zoom"), lists.apps.map { it.label })
    }

    @Test
    fun hiddenAppsLeaveListAndFavourites() {
        val lists = HomeListBuilder.build(installed, listOf(maps.key), setOf(maps.key), emptyMap(), collator)
        assertEquals(emptyList<AppEntry>(), lists.favourites)
        assertEquals(listOf("Camera", "Zoom"), lists.apps.map { it.label })
        assertEquals(listOf("Maps"), lists.hidden.map { it.label })
        assertEquals(mapOf('C' to 0, 'Z' to 1), lists.letterIndex)
    }

    @Test
    fun renamesResortAndRebucket() {
        val lists = HomeListBuilder.build(installed, emptyList(), emptySet(), mapOf(zoom.key to "  Atlas ", maps.key to "   "), collator)
        assertEquals(listOf("Atlas", "Camera", "Maps"), lists.apps.map { it.label })
        assertEquals('A', lists.apps.first().bucket)
        assertEquals("Zoom", lists.apps.first().systemLabel)
    }

    private companion object {
        // Android stubs return defaults in unit tests (isReturnDefaultValues); the builder never touches the handle.
        val USER = UserHandle(null as android.os.Parcel?)
    }
}

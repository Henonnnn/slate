package dev.slate.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationStoreTest {
    private fun raw(
        key: String,
        app: String = "chat/0",
        time: Long = 0,
        summary: Boolean = false,
        ongoing: Boolean = false,
        badge: Boolean = true,
        title: String = "t",
    ) = RawNotification(AppNotification(key, app, title, "", time, clearable = true, autoCancel = false, contentIntent = null), summary, ongoing, badge)

    @Test fun filtersLikeTheSystemDoes() {
        val index = NotificationStore.index(
            listOf(
                raw("summary", summary = true),
                raw("music", app = "player/0", ongoing = true),
                raw("muted", app = "news/0", badge = false),
                raw("empty", app = "blank/0", title = ""),
                raw("msg"),
            ),
        )
        assertEquals(setOf("chat/0"), index.keys)
        assertEquals(listOf("msg"), index.getValue("chat/0").map { it.key })
    }

    @Test fun groupsPerAppAndProfileNewestFirst() {
        val index = NotificationStore.index(
            listOf(raw("a", time = 1), raw("b", time = 3), raw("c", time = 2), raw("w", app = "chat/10")),
        )
        assertEquals(listOf("b", "c", "a"), index.getValue("chat/0").map { it.key })
        assertEquals(listOf("w"), index.getValue("chat/10").map { it.key })
    }
}

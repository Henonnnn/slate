package dev.slate.launcher.notifications

import android.app.Notification
import dev.slate.launcher.notifications.NotificationRules.GroupMember
import dev.slate.launcher.notifications.NotificationRules.Signals
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRulesTest {
    private fun signals(
        template: String? = null,
        category: String? = null,
        importance: Int = 3,
        conversation: Boolean = false,
        ongoing: Boolean = false,
        suspended: Boolean = false,
        matches: Boolean = true,
        autogroup: Boolean = false,
    ) = Signals(template, category, importance, conversation, ongoing, suspended, false, autogroup, matches)

    private fun note(key: String, rank: Int = 2, importance: Int = 3, category: String? = "msg", time: Long = 0, group: String? = null) =
        AppNotification(key, "a/0", "t", "", time, clearable = true, autoCancel = false, contentIntent = null, rank = rank, importance = importance, category = category, groupKey = group)

    @Test fun `conversations rank above everything`() {
        assertEquals(NotificationRules.RANK_CONVERSATION, NotificationRules.rank(signals(conversation = true)))
        assertEquals(NotificationRules.RANK_ALERTING, NotificationRules.rank(signals()))
    }

    @Test fun `background categories and ongoing ones stay quiet`() {
        assertEquals(NotificationRules.RANK_QUIET, NotificationRules.rank(signals(category = Notification.CATEGORY_PROMO)))
        assertEquals(NotificationRules.RANK_QUIET, NotificationRules.rank(signals(ongoing = true, conversation = true)))
        assertEquals(NotificationRules.RANK_QUIET, NotificationRules.rank(signals(importance = 1)))
        assertEquals(NotificationRules.RANK_QUIET, NotificationRules.rank(signals(matches = false)))
    }

    @Test fun `custom views and suspended apps are silent`() {
        assertEquals(NotificationRules.RANK_SILENT, NotificationRules.rank(signals(template = "com.example.Style")))
        assertEquals(NotificationRules.RANK_SILENT, NotificationRules.rank(signals(suspended = true)))
        assertEquals(NotificationRules.RANK_SILENT, NotificationRules.rank(signals(autogroup = true)))
    }

    @Test fun `order is rank, importance, categorised, then newest`() {
        val sorted = listOf(
            note("old", time = 1),
            note("new", time = 2),
            note("uncategorised", category = null, time = 3),
            note("important", importance = 4),
            note("chat", rank = 3, importance = 1),
        ).sortedWith(NotificationRules.order)
        assertEquals(listOf("chat", "important", "new", "old", "uncategorised"), sorted.map { it.key })
    }

    @Test fun `titles prefer the conversation and skip the app's own name`() {
        assertEquals("Team", NotificationRules.title("Sam", "Team", "Chat"))
        assertEquals("", NotificationRules.title("Chat", null, "Chat"))
        assertEquals("Sam", NotificationRules.title("Sam", " ", "Chat"))
    }

    @Test fun `previews name the sender unless the title already does`() {
        assertEquals("Sam: hi", NotificationRules.messagePreview("Sam", "hi", "Team"))
        assertEquals("hi", NotificationRules.messagePreview("Sam", "hi", "Sam"))
        assertEquals("hi", NotificationRules.messagePreview(null, "hi", "Team"))
    }

    @Test fun `inbox shows the newest line`() {
        assertEquals("a", NotificationRules.inboxPreview(listOf("a", "b"), "mail"))
        assertEquals("b", NotificationRules.inboxPreview(listOf("a", "b"), "com.whatsapp"))
    }

    @Test fun `body that repeats the title is dropped`() {
        assertEquals("", NotificationRules.plainText("Sam", "Sam"))
        assertEquals("hi", NotificationRules.plainText("hi", "Sam"))
    }

    @Test fun `last child takes its summary with it`() {
        val group = listOf(GroupMember("sum", "g", true), GroupMember("a", "g", false))
        assertEquals(listOf("a", "sum"), NotificationRules.cancelKeys(note("a", group = "g"), group))
    }

    @Test fun `other children keep the summary alive`() {
        val group = listOf(GroupMember("sum", "g", true), GroupMember("a", "g", false), GroupMember("b", "g", false))
        assertEquals(listOf("a"), NotificationRules.cancelKeys(note("a", group = "g"), group))
        assertEquals(listOf("solo"), NotificationRules.cancelKeys(note("solo"), group))
    }
}

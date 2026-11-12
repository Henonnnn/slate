package dev.slate.launcher.notifications

import android.app.Notification

/** How notifications are ranked, worded and cleared. Pure, so it can be tested without Android. */
object NotificationRules {
    const val RANK_SILENT = 0
    const val RANK_QUIET = 1
    const val RANK_ALERTING = 2
    const val RANK_CONVERSATION = 3

    private const val IMPORTANCE_MIN = 1
    /** Templates with content worth showing; anything else draws its own custom view. */
    private val READABLE_TEMPLATES = setOf(
        "android.app.Notification\$Style",
        "android.app.Notification\$MessagingStyle",
        "android.app.Notification\$BigTextStyle",
        "android.app.Notification\$InboxStyle",
        "android.app.Notification\$BigPictureStyle",
        "android.app.Notification\$DecoratedCustomViewStyle",
    )
    /** Categories that never count as something a person needs to read now. */
    private val BACKGROUND_CATEGORIES = setOf(
        Notification.CATEGORY_PROMO,
        Notification.CATEGORY_SYSTEM,
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_ERROR,
        Notification.CATEGORY_SERVICE,
    )

    const val MESSAGING_TEMPLATE = "android.app.Notification\$MessagingStyle"
    const val INBOX_TEMPLATE = "android.app.Notification\$InboxStyle"

    data class Signals(
        val template: String?,
        val category: String?,
        val importance: Int,
        val conversation: Boolean,
        val ongoing: Boolean,
        val suspended: Boolean,
        val ambient: Boolean,
        val autogroupSummary: Boolean,
        val matchesFilter: Boolean,
    )

    fun rank(s: Signals): Int = when {
        s.template != null && s.template !in READABLE_TEMPLATES -> RANK_SILENT
        s.suspended || s.ambient || s.autogroupSummary -> RANK_SILENT
        !s.matchesFilter || s.ongoing || s.importance == IMPORTANCE_MIN -> RANK_QUIET
        s.conversation -> RANK_CONVERSATION
        s.category !in BACKGROUND_CATEGORIES -> RANK_ALERTING
        else -> RANK_QUIET
    }

    /** Highest rank, then the system's importance, then anything categorised, then newest. */
    val order: Comparator<AppNotification> = compareByDescending<AppNotification> { it.rank }
        .thenByDescending { it.importance }
        .thenBy { it.category == null }
        .thenByDescending { it.postTime }
        .thenBy { it.key }

    /** A conversation's name wins; a title that only repeats the app's name says nothing. */
    fun title(title: String?, conversationTitle: String?, appLabel: String?): String =
        conversationTitle?.takeIf { it.isNotBlank() } ?: title?.takeIf { it != appLabel }.orEmpty()

    /** "Sender: text", unless the title already is the sender. */
    fun messagePreview(sender: String?, text: String, title: String): String =
        if (sender.isNullOrBlank() || sender == title) text else "$sender: $text"

    /** Inbox notifications list newest first, except WhatsApp's, which lists newest last. */
    fun inboxPreview(lines: List<String>, packageName: String): String? =
        if (packageName == "com.whatsapp") lines.lastOrNull() else lines.firstOrNull()

    /** The body repeated as the title adds nothing. */
    fun plainText(text: String?, title: String): String = text?.takeUnless { it == title }.orEmpty()

    /**
     * What to cancel when [target] is dismissed. A child that's the last of its group takes the
     * group's summary with it, or an empty summary would stay behind.
     */
    fun cancelKeys(target: AppNotification, group: List<GroupMember>): List<String> {
        val groupKey = target.groupKey ?: return listOf(target.key)
        val members = group.filter { it.groupKey == groupKey }
        val children = members.filterNot { it.isSummary }
        if (children.singleOrNull()?.key != target.key) return listOf(target.key)
        return listOfNotNull(target.key, members.firstOrNull { it.isSummary }?.key)
    }

    data class GroupMember(val key: String, val groupKey: String?, val isSummary: Boolean)
}

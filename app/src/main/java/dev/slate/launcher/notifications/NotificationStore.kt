package dev.slate.launcher.notifications

import dev.slate.launcher.floating.LiveActivity
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Immutable
import dev.slate.launcher.sendFromForeground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One notification as the launcher shows it. */
@Immutable
data class AppNotification(
    /** The platform's notification key, for opening and dismissing. */
    val key: String,
    /** "pkg/userSerial", matching [dev.slate.launcher.apps.AppEntry.notificationKey]. */
    val appKey: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val clearable: Boolean,
    /** Tapping should also dismiss it, as the shade would. */
    val autoCancel: Boolean,
    val contentIntent: PendingIntent?,
    /** See [NotificationRules]: decides the order and whether it's worth a line on the home screen. */
    val rank: Int = NotificationRules.RANK_ALERTING,
    /** The system's importance for its channel. */
    val importance: Int = 0,
    val category: String? = null,
    val isConversation: Boolean = false,
    val groupKey: String? = null,
    /** Replies the system suggests for this one. */
    val smartReplies: List<String> = emptyList(),
    /** The original, read for [NotificationDetail] only when it's expanded. */
    val source: Notification? = null,
)

/** The listener's raw view of a notification, before filtering. Plain data so it's testable. */
data class RawNotification(
    val notification: AppNotification,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    /** The user's per-channel "show dot" setting, not the system-wide one. */
    val canShowBadge: Boolean,
    /** Counts time, rings, or is promoted by Android as a live update: The Pill's kind of thing. */
    val isLive: Boolean = false,
    val packageName: String = "",
    val appLabel: String = "",
)

/**
 * Notifications per app, fed by [NotificationService] while it's connected. Empty (no dots)
 * whenever notification access is off, so everything downstream degrades by itself.
 */
class NotificationStore {
    private val _byApp = MutableStateFlow<Map<String, List<AppNotification>>>(emptyMap())
    val byApp: StateFlow<Map<String, List<AppNotification>>> = _byApp.asStateFlow()

    private val _live = MutableStateFlow<List<LiveActivity>>(emptyList())

    /** Ongoing timers, stopwatches, ringing alarms and live updates, for The Pill. */
    val live: StateFlow<List<LiveActivity>> = _live.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Set by the service while bound; used to dismiss notifications. */
    @Volatile
    internal var service: NotificationService? = null

    /** Everything active, summaries included, for clearing a whole group. */
    @Volatile
    private var members: List<NotificationRules.GroupMember> = emptyList()

    internal fun publish(raw: List<RawNotification>) {
        members = raw.map { NotificationRules.GroupMember(it.notification.key, it.notification.groupKey, it.isGroupSummary) }
        _byApp.value = index(raw)
        _live.value = live(raw)
    }

    internal fun setConnected(connected: Boolean) {
        _connected.value = connected
        if (!connected) {
            _byApp.value = emptyMap()
            _live.value = emptyList()
        }
    }

    fun dismiss(notification: AppNotification) {
        NotificationRules.cancelKeys(notification, members).forEach { service?.dismiss(it) }
    }

    /** Sends [text] through the app's own reply action. */
    fun reply(context: Context, reply: NotificationReply, text: String): Boolean {
        val results = Bundle().apply { putString(reply.resultKey, text) }
        val fillIn = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(reply.inputs, fillIn, results)
        return reply.intent.sendFromForeground(context, fillIn)
    }

    /** Opens what the notification points at, like tapping it in the shade. */
    fun open(context: Context, notification: AppNotification): Boolean {
        val opened = notification.contentIntent?.sendFromForeground(context) ?: return false
        if (opened && notification.autoCancel) dismiss(notification)
        return opened
    }

    companion object {
        /** Group summaries repeat their children, so they're left out. */
        fun live(raw: List<RawNotification>): List<LiveActivity> =
            raw.filter { it.isLive && it.isOngoing && !it.isGroupSummary }.map {
                val n = it.notification
                LiveActivity(n.key, it.packageName, it.appLabel, n.title, n.text, n.postTime, n.contentIntent, n.source)
            }

        /**
         * What counts follows the system's rules for dots: no group summaries (their children
         * carry the content), no ongoing ones (music, downloads, navigation), and none on channels
         * where the user turned dots off. Per app they're in [NotificationRules.order].
         */
        fun index(raw: List<RawNotification>): Map<String, List<AppNotification>> =
            raw.asSequence()
                .filter { !it.isGroupSummary && !it.isOngoing && it.canShowBadge }
                .map { it.notification }
                .filter { it.title.isNotBlank() || it.text.isNotBlank() }
                .groupBy { it.appKey }
                .mapValues { (_, list) -> list.sortedWith(NotificationRules.order) }
    }
}

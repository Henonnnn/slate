package dev.slate.launcher.notifications

import android.content.Intent
import android.provider.AlarmClock
import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcelable
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.os.BundleCompat
import dev.slate.launcher.container

/**
 * Notification access, granted by the user in system settings. Everything stays in memory;
 * nothing is stored or sent. Replies go only through the app's own reply action.
 *
 * Every change re-snapshots the active list on a background thread (coalesced), which is
 * simpler and more robust than patching incrementally.
 */
class NotificationService : NotificationListenerService() {
    private val thread = HandlerThread("notifications").apply { start() }
    private val handler = Handler(thread.looper)
    private val refresh = Runnable { snapshot() }
    private val store get() = applicationContext.container.notificationStore
    private val userManager by lazy { getSystemService(UserManager::class.java) }
    /** Only touched on [thread]. */
    private val labels = HashMap<String, String?>()

    /** Apps that set timers or alarms; whatever they keep in the shade is a live activity. Read on [thread]. */
    @Volatile
    private var clockPackages: Set<String> = emptySet()

    override fun onListenerConnected() {
        store.service = this
        store.setConnected(true)
        handler.post { clockPackages = findClockPackages() }
        schedule()
    }

    override fun onListenerDisconnected() {
        store.service = null
        store.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) = schedule()

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) = schedule()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = schedule()

    override fun onDestroy() {
        thread.quitSafely()
        super.onDestroy()
    }

    fun dismiss(key: String) {
        runCatching { cancelNotification(key) }
    }

    private fun findClockPackages(): Set<String> =
        listOf(AlarmClock.ACTION_SET_TIMER, AlarmClock.ACTION_SET_ALARM, AlarmClock.ACTION_SHOW_ALARMS).flatMapTo(HashSet()) { action ->
            runCatching { packageManager.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList()).map { it.activityInfo.packageName }
        }

    private fun schedule() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 120)
    }

    private fun snapshot() {
        val active = try {
            activeNotifications.orEmpty()
        } catch (e: SecurityException) {
            // Access revoked between callbacks.
            Log.w(TAG, "Lost notification access", e)
            return
        }
        val ranking = currentRanking
        val scratch = Ranking()
        val raw = active.filter { it.packageName != packageName }.map { sbn ->
            val hasRanking = ranking?.getRanking(sbn.key, scratch) == true
            toRaw(sbn, scratch.takeIf { hasRanking })
        }
        store.publish(raw)
    }

    private fun toRaw(sbn: StatusBarNotification, ranking: Ranking?): RawNotification {
        val n = sbn.notification
        val extras = n.extras
        val label = appLabel(sbn.packageName)
        val template = extras.getString(Notification.EXTRA_TEMPLATE)
        val messages = BundleCompat.getParcelableArray(extras, Notification.EXTRA_MESSAGES, Parcelable::class.java)
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val title = NotificationRules.title(rawTitle, conversationTitle.takeIf { template == NotificationRules.MESSAGING_TEMPLATE }, label)
        val conversationShortcut = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ranking?.conversationShortcutInfo != null
        val conversation = conversationShortcut ||
            n.category == Notification.CATEGORY_MESSAGE ||
            template == NotificationRules.MESSAGING_TEMPLATE
        val signals = NotificationRules.Signals(
            template = template,
            category = n.category,
            importance = ranking?.importance ?: NotificationManager.IMPORTANCE_DEFAULT,
            conversation = conversation,
            ongoing = sbn.isOngoing,
            suspended = ranking?.isSuspended == true,
            ambient = ranking?.isAmbient == true,
            autogroupSummary = n.flags and FLAG_AUTOGROUP_SUMMARY != 0,
            matchesFilter = ranking?.matchesInterruptionFilter() != false,
        )
        return RawNotification(
            notification = AppNotification(
                key = sbn.key,
                appKey = "${sbn.packageName}/${userManager.getSerialNumberForUser(sbn.user)}",
                title = title,
                text = previewText(sbn.packageName, template, extras, messages, title).trim(),
                postTime = sbn.postTime,
                clearable = sbn.isClearable,
                autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
                contentIntent = n.contentIntent,
                rank = NotificationRules.rank(signals),
                importance = signals.importance,
                category = n.category,
                isConversation = conversation,
                groupKey = n.group,
                smartReplies = ranking?.smartReplies.orEmpty().map(CharSequence::toString),
                source = n,
            ),
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = sbn.isOngoing,
            // The channel's own switch. Ranking.canShowBadge() is also false for everything
            // when dots are off system-wide, and that must not empty the launcher.
            canShowBadge = ranking?.channel?.canShowBadge() != false,
            isLive = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) ||
                n.category in LIVE_CATEGORIES ||
                // Clock apps often mark nothing: Fossify's timer is a plain notification whose title is the count.
                sbn.packageName in clockPackages ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && n.flags and Notification.FLAG_PROMOTED_ONGOING != 0),
            packageName = sbn.packageName,
            appLabel = label.orEmpty(),
        )
    }

    private fun previewText(packageName: String, template: String?, extras: Bundle, messages: Array<Parcelable>?, title: String): String {
        val plain = NotificationRules.plainText(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(), title)
        return when (template) {
            NotificationRules.MESSAGING_TEMPLATE -> {
                val last = messages?.lastOrNull() as? Bundle ?: return plain
                val sender = BundleCompat.getParcelable(last, "sender_person", Person::class.java)?.name?.toString()
                    ?: last.getCharSequence("sender")?.toString()
                NotificationRules.messagePreview(sender, last.getCharSequence("text")?.toString().orEmpty(), title)
            }
            NotificationRules.INBOX_TEMPLATE -> {
                val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map(CharSequence::toString)
                NotificationRules.inboxPreview(lines, packageName) ?: plain
            }
            else -> plain
        }
    }

    private fun appLabel(packageName: String): String? = labels.getOrPut(packageName) {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrNull()
    }

    private companion object {
        // "stopwatch" and "workout" are Notification constants only from API 31.
        val LIVE_CATEGORIES = setOf(Notification.CATEGORY_ALARM, "stopwatch", "workout", Notification.CATEGORY_CALL, Notification.CATEGORY_NAVIGATION)
        const val TAG = "NotificationService"
        /** Notification.FLAG_AUTOGROUP_SUMMARY is hidden; it marks summaries the system made up. */
        const val FLAG_AUTOGROUP_SUMMARY = 1024
    }
}

package dev.slate.launcher.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.BundleCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One line of a conversation. */
class NotificationMessage(val sender: String?, val text: String, val avatar: ImageBitmap?)

class NotificationAction(val title: String, val intent: PendingIntent)

/** The app's own reply action: text sent here arrives as if typed in its notification. */
class NotificationReply(val label: String, val intent: PendingIntent, val inputs: Array<RemoteInput>, val choices: List<String>) {
    val resultKey: String get() = inputs.first().resultKey
}

/** Everything a notification shows once expanded. Read only when that happens. */
@Immutable
class NotificationDetail(
    val title: String,
    val subText: String?,
    val body: String?,
    val messages: List<NotificationMessage>,
    val picture: ImageBitmap?,
    val actions: List<NotificationAction>,
    val reply: NotificationReply?,
)

private const val MAX_ACTIONS = 3
private const val MAX_MESSAGES = 8
private const val AVATAR_PX = 96

/** Reads images and bundles, so it runs off the main thread. */
suspend fun readDetail(context: Context, n: AppNotification): NotificationDetail? = withContext(Dispatchers.Default) {
    val source = n.source ?: return@withContext null
    val extras = source.extras
    val actions = source.actions.orEmpty().take(MAX_ACTIONS)
    val replying = actions.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
    val reply = replying?.let { action ->
        val inputs = action.remoteInputs.orEmpty().toList().toTypedArray()
        NotificationReply(
            label = action.title?.toString().orEmpty(),
            intent = action.actionIntent,
            inputs = inputs,
            choices = (n.smartReplies + inputs.flatMap { it.choices.orEmpty().map(CharSequence::toString) }).distinct(),
        )
    }
    val messages = readMessages(context, extras)
    NotificationDetail(
        title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.takeIf { it.isNotBlank() && messages.isEmpty() } ?: n.title,
        subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        body = readBody(extras, n).takeIf { messages.isEmpty() },
        messages = messages,
        picture = readPicture(context, extras),
        actions = actions.filter { it !== replying && !it.title.isNullOrBlank() && it.actionIntent != null }
            .map { NotificationAction(it.title.toString(), it.actionIntent) },
        reply = reply,
    )
}

/** The notification's own picture (a contact's face, say), or null to use the app's icon. */
suspend fun readLargeIcon(context: Context, n: AppNotification): ImageBitmap? = withContext(Dispatchers.Default) {
    n.source?.getLargeIcon()?.let { loadIcon(context, it) }
}

private fun readBody(extras: Bundle, n: AppNotification): String? {
    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
    extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.takeIf { it.isNotEmpty() }?.let { lines -> return lines.joinToString("\n") }
    return n.text.takeIf { it.isNotBlank() }
}

private fun readMessages(context: Context, extras: Bundle): List<NotificationMessage> {
    val bundles = BundleCompat.getParcelableArray(extras, Notification.EXTRA_MESSAGES, Parcelable::class.java) ?: return emptyList()
    return bundles.filterIsInstance<Bundle>().takeLast(MAX_MESSAGES).mapNotNull { b ->
        val text = b.getCharSequence("text")?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val person = BundleCompat.getParcelable(b, "sender_person", Person::class.java)
        NotificationMessage(
            sender = person?.name?.toString() ?: b.getCharSequence("sender")?.toString(),
            text = text,
            avatar = person?.icon?.let { loadIcon(context, it) },
        )
    }
}

private fun readPicture(context: Context, extras: Bundle): ImageBitmap? {
    BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE, Bitmap::class.java)?.let { return it.asImageBitmap() }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val icon = BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE_ICON, Icon::class.java) ?: return null
    return loadIcon(context, icon, size = null)
}

private fun loadIcon(context: Context, icon: Icon, size: Int? = AVATAR_PX): ImageBitmap? = runCatching {
    val drawable = icon.loadDrawable(context) ?: return null
    (if (size != null) drawable.toBitmap(size, size) else drawable.toBitmap()).asImageBitmap()
}.getOrNull()

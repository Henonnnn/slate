package dev.slate.launcher.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.slate.launcher.R
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.notifications.AppNotification
import dev.slate.launcher.notifications.NotificationAction
import dev.slate.launcher.notifications.NotificationDetail
import dev.slate.launcher.notifications.NotificationMessage
import dev.slate.launcher.notifications.NotificationReply
import dev.slate.launcher.notifications.readLargeIcon
import dev.slate.launcher.ui.components.SlateIcon
import dev.slate.launcher.ui.components.SlateIconView
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import kotlinx.coroutines.launch
import kotlin.math.abs

private val IconSize = 36.dp
private val AvatarSize = 28.dp
private val ChipShape = RoundedCornerShape(16.dp)
private val ReplyShape = RoundedCornerShape(19.dp)
private const val COLLAPSED_LINES = 1
private const val CONVERSATION_LINES = 3
private const val EXPANDED_LINES = 30
private const val REPLY_LINES = 9
/** Share of the row's width a swipe must cross to dismiss. */
private const val DISMISS_AT = 0.35f
private const val DISMISS_VELOCITY = 1500f

/**
 * One notification in a pop-up. Collapsed it's a preview; the chevron expands it in place to the
 * full text, conversation, picture and actions. Tapping opens it and a sideways swipe clears it.
 */
@Composable
fun NotificationTile(
    owner: AppEntry,
    /** Named beside the time when notifications from several apps share the list. */
    appName: String? = null,
    n: AppNotification,
    expanded: Boolean,
    detail: NotificationDetail?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onClear: () -> Unit,
    onAction: (NotificationAction) -> Unit,
    onSmartReply: (NotificationReply, String) -> Unit,
) {
    val palette = LocalSlatePalette.current
    val context = LocalContext.current
    val secondary = SlateText.hint.copy(fontSize = 14.sp, shadow = null)
    val nowLabel = stringResource(R.string.calendar_now)
    val time = remember(n.postTime, nowLabel) {
        val now = System.currentTimeMillis()
        if (now - n.postTime < DateUtils.MINUTE_IN_MILLIS) {
            nowLabel
        } else {
            DateUtils.getRelativeTimeSpanString(n.postTime, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
        }
    }
    val largeIcon by produceState<ImageBitmap?>(null, n.key, n.postTime) { value = readLargeIcon(context, n) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, spring(stiffness = 600f), label = "chevron")
    val shown = detail.takeIf { expanded }
    val toggleLabel = stringResource(if (expanded) R.string.notification_collapse else R.string.notification_expand)
    val clearLabel = stringResource(R.string.notification_dismiss)
    SwipeToClear(enabled = n.clearable, onClear = onClear) {
        PopupRow(
            modifier = Modifier
                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 500f))
                .semantics { if (n.clearable) customActions = listOf(CustomAccessibilityAction(clearLabel) { onClear(); true }) },
            alignment = Alignment.Top,
            onLongClick = onLongClick,
            onClick = onOpen,
        ) {
            val icon = largeIcon
            if (icon != null) {
                Image(icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(IconSize).clip(CircleShape))
            } else {
                AppIcon(owner, IconSize)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (shown?.title ?: n.title).ifBlank { owner.label },
                        style = SlateText.appLabel.copy(fontSize = 16.sp, shadow = null),
                        color = palette.onWallpaper,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(listOfNotNull("", appName, time).joinToString(" · "), style = secondary, color = palette.onWallpaperMuted, maxLines = 1)
                }
                if (shown == null) {
                    if (n.text.isNotBlank()) {
                        Text(
                            n.text,
                            style = secondary,
                            color = palette.onWallpaperMuted,
                            maxLines = if (n.isConversation) CONVERSATION_LINES else COLLAPSED_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Expanded(shown, onAction, onSmartReply)
                }
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp).semantics { contentDescription = toggleLabel }) {
                SlateIconView(SlateIcon.ChevronDown, palette.onWallpaperMuted, Modifier.graphicsLayer { rotationZ = rotation }, size = 20.dp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Expanded(detail: NotificationDetail, onAction: (NotificationAction) -> Unit, onSmartReply: (NotificationReply, String) -> Unit) {
    val palette = LocalSlatePalette.current
    val secondary = SlateText.hint.copy(fontSize = 14.sp, shadow = null)
    detail.subText?.let { Text(it, style = secondary, color = palette.onWallpaperMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    detail.body?.let { Text(it, style = secondary, color = palette.onWallpaperMuted, maxLines = EXPANDED_LINES, overflow = TextOverflow.Ellipsis) }
    detail.messages.forEach { MessageRow(it) }
    detail.picture?.let { picture ->
        Image(
            picture,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        )
    }
    val reply = detail.reply
    if (detail.actions.isNotEmpty() || reply?.choices?.isNotEmpty() == true) {
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            detail.actions.forEach { action -> Chip(action.title, outlined = false) { onAction(action) } }
            reply?.choices?.forEach { choice -> Chip(choice, outlined = true) { onSmartReply(reply, choice) } }
        }
    }
}

@Composable
private fun MessageRow(message: NotificationMessage) {
    val palette = LocalSlatePalette.current
    val secondary = SlateText.hint.copy(fontSize = 14.sp, shadow = null)
    Row(Modifier.padding(vertical = 4.dp)) {
        val avatar = message.avatar
        if (avatar != null) {
            Image(avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.padding(end = 8.dp).size(AvatarSize).clip(CircleShape))
        }
        Column {
            message.sender?.let {
                Text(it, style = secondary.copy(fontWeight = FontWeight.Medium), color = palette.onWallpaper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(message.text, style = secondary, color = palette.onWallpaperMuted)
        }
    }
}

@Composable
private fun Chip(label: String, outlined: Boolean, onClick: () -> Unit) {
    val palette = LocalSlatePalette.current
    val fill = if (outlined) palette.accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.1f)
    Text(
        label,
        style = SlateText.hint.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, shadow = null),
        color = if (outlined) palette.accent else palette.onWallpaper,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(ChipShape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Slides the row sideways; past [DISMISS_AT] of its width, or on a quick fling, it clears. */
@Composable
private fun SwipeToClear(enabled: Boolean, onClear: () -> Unit, content: @Composable () -> Unit) {
    val palette = LocalSlatePalette.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } }
    val revealed by remember { derivedStateOf { offset.value != 0f } }
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        if (revealed) {
            Row(Modifier.matchParentSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                val toEnd = offset.value > 0
                if (!toEnd) Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.notification_dismiss),
                    style = SlateText.hint.copy(fontSize = 14.sp, shadow = null),
                    color = palette.onWallpaperMuted,
                )
            }
        }
        Box(
            Modifier
                .graphicsLayer {
                    translationX = offset.value
                    alpha = if (width > 0) 1f - (abs(offset.value) / width).coerceAtMost(1f) else 1f
                }
                .then(
                    if (enabled) {
                        Modifier.draggable(
                            state = state,
                            orientation = Orientation.Horizontal,
                            onDragStopped = { velocity ->
                                val far = abs(offset.value) > width * DISMISS_AT
                                val quick = abs(velocity) > DISMISS_VELOCITY
                                if (far || quick) {
                                    val direction = if (offset.value == 0f) velocity else offset.value
                                    offset.animateTo(if (direction > 0) width.toFloat() else -width.toFloat(), tween(160))
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    onClear()
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                                }
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
        ) { content() }
    }
}

/** The reply box, pinned under the list so it stays beside the keyboard. */
@Composable
fun ReplyBox(reply: NotificationReply, onSend: (String) -> Unit) {
    val palette = LocalSlatePalette.current
    var text by remember(reply) { mutableStateOf("") }
    val style = SlateText.appLabel.copy(fontSize = 16.sp, color = palette.onWallpaper, shadow = null)
    fun send() {
        val message = text.trim()
        if (message.isEmpty()) return
        onSend(message)
        text = ""
    }
    val sendLabel = stringResource(R.string.notification_reply_send)
    val hintLabel = reply.label.ifBlank { stringResource(R.string.notification_reply_hint) }
    Row(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(Color.White.copy(alpha = 0.08f), ReplyShape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
            if (text.isEmpty()) {
                Text(hintLabel, style = style.copy(color = palette.onWallpaperMuted), maxLines = 1)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = style,
                maxLines = REPLY_LINES,
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hintLabel },
            )
        }
        IconButton(onClick = ::send, enabled = text.isNotBlank(), modifier = Modifier.semantics { contentDescription = sendLabel }) {
            SlateIconView(SlateIcon.Send, if (text.isNotBlank()) palette.accent else palette.onWallpaperMuted, size = 22.dp)
        }
    }
}

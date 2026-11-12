package dev.slate.launcher.ui.home

import dev.slate.launcher.ui.components.SlateIconView
import dev.slate.launcher.ui.components.SlateIcon
import androidx.compose.runtime.CompositionLocalProvider
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.slate.launcher.R
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.apps.AppShortcut
import dev.slate.launcher.container
import dev.slate.launcher.data.PopupItem
import dev.slate.launcher.data.Popups
import dev.slate.launcher.data.WeatherPlace
import dev.slate.launcher.data.WidgetRef
import dev.slate.launcher.data.WidgetStack
import dev.slate.launcher.data.Widgets
import dev.slate.launcher.notifications.AppNotification
import dev.slate.launcher.notifications.NotificationAction
import dev.slate.launcher.notifications.NotificationDetail
import dev.slate.launcher.notifications.NotificationReply
import dev.slate.launcher.notifications.readDetail
import dev.slate.launcher.ui.components.ClearGlyph
import dev.slate.launcher.ui.components.DragHandleGlyph
import dev.slate.launcher.ui.components.RemoveGlyph
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import dev.slate.launcher.ui.widgets.WidgetStackView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

typealias ResolvedItem = Popups.Resolved<AppEntry, AppShortcut>

/**
 * Resolves a pop-up's items against installed apps and live shortcuts. Shortcuts are looked
 * up off the main thread; until they arrive, only the app items show.
 */
@Composable
fun rememberResolvedPopup(items: List<PopupItem>, findApp: (String) -> AppEntry?): List<ResolvedItem> {
    val repository = LocalContext.current.container.shortcutRepository
    val shortcutKeys = remember(items) { items.filter { it.kind == PopupItem.Kind.Shortcut }.map { it.key } }
    val shortcuts by produceState(emptyMap<String, AppShortcut>(), shortcutKeys) {
        value = if (shortcutKeys.isEmpty()) emptyMap() else repository.resolve(shortcutKeys)
    }
    return remember(items, shortcuts, findApp) { Popups.resolve(items, findApp) { shortcuts[it] } }
}

// Sizes from Niagara's popup_tile_default: 36 dp icon, 24 dp to the label, 8 dp padding.
private val ItemIconSize = 36.dp
private val ItemIconGap = 24.dp
private val CardElevation = 7.dp
private val CardCorner = 20.dp
private val RowShape = RoundedCornerShape(12.dp)
/** Niagara's card sits 40 dp from each edge on the phone (12 dp margin inside its padded list). */
private val CardMargin = 40.dp
private val CardMaxWidth = 478.dp
private val HubMargin = 24.dp
private val HubMaxWidth = 540.dp
private val ScreenMargin = 16.dp
/** Pulling the card this far and letting go dismisses it. */
private val DismissPull = 150.dp
private val MaxPull = 200.dp
private const val MIN_PULL_SCALE = 0.9f
private const val DISMISS_VELOCITY = 1800f
private const val REVEAL_MS = 300
private val RevealEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val DismissEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** The closing half of [PopupPanel]: the whole panel falls back into the point it grew from. */
fun popupExit(origin: Offset, screen: Size): ExitTransition {
    val pivot = TransformOrigin(
        if (screen.width > 0f) origin.x / screen.width else 0.5f,
        if (screen.height > 0f) origin.y / screen.height else 0.5f,
    )
    return scaleOut(tween(POPUP_CLOSE_MS, easing = DismissEasing), targetScale = 0.2f, transformOrigin = pivot) +
        fadeOut(tween(POPUP_CLOSE_MS, easing = DismissEasing))
}

const val POPUP_CLOSE_MS = 250

/** A rounded card seen through a circle growing from [centre], for the opening reveal. */
private class RevealShape(private val centre: Offset, private val progress: Float, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val card = Path().apply { addRoundRect(RoundRect(size.toRect(), CornerRadius(corner))) }
        if (progress >= 1f) return Outline.Generic(card)
        val farX = maxOf(centre.x, size.width - centre.x)
        val farY = maxOf(centre.y, size.height - centre.y)
        val radius = hypot(farX, farY) * progress
        val circle = Path().apply { addOval(Rect(centre, radius)) }
        return Outline.Generic(Path().apply { op(circle, card, PathOperation.Intersect) })
    }
}

/**
 * An app's pop-up: a card floating over the dimmed home screen, centred on [anchor] and kept clear
 * of the system bars. It waits for the app's shortcuts so it opens at its final size, grows out
 * of [anchor] in a circle, and can be pulled away in either direction with an elastic drag that
 * shrinks it as it goes. Every app has one: its notifications and shortcuts come by themselves,
 * then anything the user added. Long-pressing it starts edit mode, which shows only the added
 * items, with drag handles.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PopupPanel(
    owner: AppEntry,
    anchor: Offset,
    /** Message Hub: wider, every app's notifications, and nothing to edit. */
    hub: Boolean = false,
    /** How many of the app's own shortcuts are listed. */
    maxShortcuts: Int = 4,
    /** Open with the newest notification unfolded. */
    expandFirst: Boolean = true,
    /** Which app a notification came from, when it isn't [owner]'s own. */
    ownerOf: (AppNotification) -> AppEntry? = { null },
    items: List<ResolvedItem>,
    widgets: List<WidgetStack> = emptyList(),
    weatherPlace: WeatherPlace? = null,
    notifications: List<AppNotification>,
    editing: Boolean,
    onLaunchApp: (AppEntry, Rect) -> Unit,
    onLaunchShortcut: (AppShortcut, Rect) -> Unit,
    onOpenNotification: (AppNotification) -> Unit,
    onClearNotification: (AppNotification) -> Unit,
    onNotificationAction: (NotificationAction) -> Unit,
    onReply: (NotificationReply, String) -> Boolean,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (PopupItem) -> Unit,
    onReorder: (List<PopupItem>) -> Unit,
    onAddWidget: () -> Unit = {},
    onRemoveWidget: (WidgetRef) -> Unit = {},
    onWidgetHeight: (WidgetRef, Int) -> Unit = { _, _ -> },
    onChooseWeatherPlace: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val wallpaperPalette = LocalSlatePalette.current
    val palette = remember(wallpaperPalette) { wallpaperPalette.onPanel() }
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repository = context.container.shortcutRepository
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val ime = WindowInsets.ime
    val shortcuts by produceState<List<AppShortcut>?>(null, owner.key) {
        value = if (!owner.isFolder && repository.hasAccess()) repository.forApp(owner) else emptyList()
    }
    val loaded = shortcuts ?: return

    val reveal = remember { Animatable(0f) }
    val pull = remember { Animatable(0f) }
    var cardTop by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        reveal.animateTo(1f, tween(REVEAL_MS, easing = RevealEasing))
    }

    val pullConnection = remember(density) { PopupPullConnection(pull, scope, density, onDismissNow = onDismiss) }

    // Swiping to the pop-up reveals the top notification in full. One is open at a time.
    var expandedKey by remember { mutableStateOf(notifications.firstOrNull()?.key.takeIf { expandFirst }) }
    val expanded = notifications.firstOrNull { it.key == expandedKey }
    val detail by produceState<NotificationDetail?>(null, expanded?.key, expanded?.postTime) {
        value = expanded?.let { readDetail(context, it) }
    }
    val pendingReply = detail?.reply.takeIf { !editing }
    // Clearing the last thing here leaves nothing to show.
    var hadNotifications by remember { mutableStateOf(false) }
    LaunchedEffect(notifications.isEmpty()) {
        if (notifications.isNotEmpty()) {
            hadNotifications = true
        } else if (hadNotifications && !hub && items.isEmpty() && loaded.isEmpty() && widgets.isEmpty()) {
            onDismiss()
        }
    }
    val failedText = stringResource(R.string.notification_reply_failed)
    fun sendReply(reply: NotificationReply, text: String) {
        if (onReply(reply, text)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
        }
    }
    val editLabel = stringResource(R.string.popup_edit)
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            CompositionLocalProvider(LocalSlatePalette provides palette) {
            Column(
                Modifier
                    .graphicsLayer {
                        val max = MaxPull.toPx()
                        // Rubber band: the further it goes, the harder it pulls back.
                        translationY = pull.value.sign * max * (1f - exp(-abs(pull.value) / max))
                        val shrink = 1f - (1f - MIN_PULL_SCALE) * (abs(pull.value) / DismissPull.toPx()).coerceAtMost(1f)
                        scaleX = shrink
                        scaleY = shrink
                        shape = RevealShape(anchor - cardTop, reveal.value, CardCorner.toPx())
                        clip = true
                        shadowElevation = CardElevation.toPx()
                    }
                    .background(palette.panel)
                    .then(
                        if (editing || hub) {
                            Modifier
                        } else {
                            Modifier
                                .pointerInput(Unit) { detectTapGestures(onLongPress = { onEdit() }) }
                                .semantics { customActions = listOf(CustomAccessibilityAction(editLabel) { onEdit(); true }) }
                        },
                    )
                    .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 420f))
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            ) {
                if (editing) {
                    EditList(owner, items, onDone, onRemove, onReorder)
                    widgets.forEach { WidgetEditRow(it, onRemoveWidget, onWidgetHeight) }
                    ActionRow(stringResource(R.string.popup_add), onAdd)
                    ActionRow(stringResource(R.string.add_widget), onAddWidget)
                    return@Column
                }
                Column(Modifier.weight(1f, fill = false).nestedScroll(pullConnection).verticalScroll(rememberScrollState())) {
                    Header(
                        label = owner.label,
                        clearable = notifications.filter { it.clearable },
                        onClear = onClearNotification,
                        onOpen = if (hub || owner.isFolder) null else ({ bounds -> onLaunchApp(owner, bounds) }),
                        onLongClick = if (hub) ({}) else onEdit,
                    )
                    widgets.forEach { stack ->
                        WidgetStackView(
                            stack = stack,
                            heightDp = stack.heightDp,
                            weatherPlace = weatherPlace,
                            onLongPress = { onEdit() },
                            onChooseWeatherPlace = onChooseWeatherPlace,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    notifications.forEach { n ->
                        key(n.key) {
                            NotificationTile(
                                owner = ownerOf(n) ?: owner,
                                appName = ownerOf(n)?.label,
                                n = n,
                                expanded = n.key == expandedKey,
                                detail = detail.takeIf { n.key == expandedKey },
                                onToggle = { expandedKey = n.key.takeUnless { it == expandedKey } },
                                onOpen = { onOpenNotification(n) },
                                onLongClick = if (hub) ({}) else onEdit,
                                onClear = { onClearNotification(n) },
                                onAction = onNotificationAction,
                                onSmartReply = ::sendReply,
                            )
                        }
                    }
                    // Anything the user already pinned here shouldn't show twice.
                    val added = items.mapNotNull { (it as? Popups.Resolved.Shortcut)?.shortcut?.key }.toSet()
                    loaded.filterNot { it.key in added }.take(maxShortcuts.coerceAtLeast(0)).forEach { s ->
                        ItemRow(s.label, { ShortcutIcon(s, ItemIconSize) }, onEdit) { bounds -> onLaunchShortcut(s, bounds) }
                    }
                    items.forEach { resolved ->
                        ItemRow(rowLabel(resolved), { ItemIcon(resolved) }, onEdit) { bounds ->
                            when (resolved) {
                                is Popups.Resolved.App -> onLaunchApp(resolved.app, bounds)
                                is Popups.Resolved.Shortcut -> onLaunchShortcut(resolved.shortcut, bounds)
                            }
                        }
                    }
                    if (notifications.isEmpty() && loaded.isEmpty() && items.isEmpty() && widgets.isEmpty()) {
                        Text(
                            stringResource(if (hub) R.string.message_hub_empty else R.string.popup_empty),
                            style = SlateText.hint.copy(fontSize = 14.sp, shadow = null),
                            color = palette.onWallpaperMuted,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                        )
                    }
                }
                pendingReply?.let { reply -> ReplyBox(reply) { text -> sendReply(reply, text) } }
            }
            }
        },
    ) { measurables, constraints ->
        val margin = (if (hub) HubMargin else CardMargin).roundToPx()
        val screen = ScreenMargin.roundToPx()
        val top = insets.calculateTopPadding().roundToPx() + screen
        // The card rises with the keyboard, so the reply box stays in view.
        val bottom = constraints.maxHeight - max(insets.calculateBottomPadding().roundToPx(), ime.getBottom(this)) - screen
        val width = min(constraints.maxWidth - margin * 2, (if (hub) HubMaxWidth else CardMaxWidth).roundToPx())
        val card = measurables.single().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = (bottom - top).coerceAtLeast(0)))
        // The card's height animates, so this re-centres it smoothly as its content changes.
        val y = (anchor.y - card.height / 2f).coerceIn(top.toFloat(), (bottom - card.height).coerceAtLeast(top).toFloat())
        // Wide windows keep the card on the side it came from.
        val x = if (anchor.x > constraints.maxWidth / 2f) constraints.maxWidth - margin - width else margin
        layout(constraints.maxWidth, constraints.maxHeight) {
            cardTop = Offset(x.toFloat(), y)
            card.place(x, y.roundToInt())
        }
    }
}

/** Takes what the card's scroll leaves over and turns it into the elastic pull. */
private class PopupPullConnection(
    private val pull: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope,
    private val density: Density,
    private val onDismissNow: () -> Unit,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val current = pull.value
        // Reversing direction undoes the pull before the content starts scrolling again.
        if (current == 0f || source != NestedScrollSource.UserInput || available.y.sign == current.sign) return Offset.Zero
        val next = if (abs(available.y) >= abs(current)) 0f else current + available.y
        val used = next - current
        scope.launch { pull.snapTo(next) }
        return Offset(0f, used)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        scope.launch { pull.snapTo(pull.value + available.y) }
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val current = pull.value
        if (current == 0f) return Velocity.Zero
        val far = abs(current) >= with(density) { DismissPull.toPx() }
        if (far || abs(available.y) > DISMISS_VELOCITY) {
            onDismissNow()
        } else {
            pull.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 420f), initialVelocity = available.y)
        }
        return available
    }
}

@Composable
private fun ItemIcon(resolved: ResolvedItem) {
    when (resolved) {
        is Popups.Resolved.App -> AppIcon(resolved.app, ItemIconSize)
        is Popups.Resolved.Shortcut -> ShortcutIcon(resolved.shortcut, ItemIconSize)
    }
}

@Composable
private fun EditList(
    owner: AppEntry,
    items: List<ResolvedItem>,
    onDone: () -> Unit,
    onRemove: (PopupItem) -> Unit,
    onReorder: (List<PopupItem>) -> Unit,
) {
    val palette = LocalSlatePalette.current
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.popup_for, owner.label),
            style = SlateText.appLabel.copy(fontSize = 15.sp, shadow = null),
            color = palette.onWallpaper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDone) { Text(stringResource(R.string.done), color = palette.accent) }
    }
    // Local order so drags are instant; committed on drop.
    var order by remember { mutableStateOf(items) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(items) { if (!dragging) order = items }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val list = order.toMutableList()
        val fromIndex = list.indexOfFirst { listKey(it.item) == from.key }
        val toIndex = list.indexOfFirst { listKey(it.item) == to.key }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        list.add(toIndex, list.removeAt(fromIndex))
        order = list
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    LazyColumn(state = listState, modifier = Modifier.heightIn(max = MaxEditHeight)) {
        items(order, key = { listKey(it.item) }) { resolved ->
            ReorderableItem(reorderState, key = listKey(resolved.item)) { isDragging ->
                val label = rowLabel(resolved)
                val removeLabel = stringResource(R.string.remove_from_popup, label)
                val dragLabel = stringResource(R.string.drag_to_reorder, label)
                Row(
                    Modifier.fillMaxWidth().graphicsLayer { alpha = if (isDragging) 0.85f else 1f }.padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ItemIcon(resolved)
                    Spacer(Modifier.width(ItemIconGap))
                    ItemText(label, Modifier.weight(1f))
                    IconButton(
                        onClick = { onRemove(resolved.item) },
                        modifier = Modifier.semantics { contentDescription = removeLabel },
                    ) { RemoveGlyph(palette.onWallpaperMuted) }
                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .draggableHandle(
                                onDragStarted = {
                                    dragging = true
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                },
                                onDragStopped = {
                                    dragging = false
                                    onReorder(order.map { it.item })
                                },
                            )
                            .semantics { contentDescription = dragLabel },
                    ) { DragHandleGlyph(palette.onWallpaper) }
                }
            }
        }
    }
}

private val MaxEditHeight = 52.dp * 6

/** Lazy list keys must be Bundle-saveable, so not the data class itself. */
private fun listKey(item: PopupItem) = "${item.kind}:${item.key}"

private fun rowLabel(resolved: ResolvedItem): String = when (resolved) {
    is Popups.Resolved.App -> resolved.app.label
    is Popups.Resolved.Shortcut -> resolved.shortcut.label
}

@Composable
private fun ItemText(text: String, modifier: Modifier = Modifier, size: TextUnit = 16.sp) {
    Text(
        text,
        style = SlateText.appLabel.copy(fontSize = size, fontWeight = FontWeight.SemiBold, shadow = null),
        color = LocalSlatePalette.current.onWallpaper,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A widget in edit mode: its name, a way to remove it, and how tall it is. */
@Composable
private fun WidgetEditRow(stack: WidgetStack, onRemove: (WidgetRef) -> Unit, onHeight: (WidgetRef, Int) -> Unit) {
    val palette = LocalSlatePalette.current
    val repository = LocalContext.current.container.widgetRepository
    val ref = stack.items.first()
    val fallback = stringResource(R.string.add_widget)
    val label = when (ref.kind) {
        WidgetRef.Kind.Calendar -> stringResource(R.string.widget_calendar)
        WidgetRef.Kind.Weather -> stringResource(R.string.widget_weather)
        WidgetRef.Kind.App -> produceState(fallback, ref) {
            value = withContext(Dispatchers.Default) { repository.info(ref.appWidgetId)?.let(repository::providerLabel) } ?: fallback
        }.value
    }
    var height by remember(stack.heightDp) { mutableFloatStateOf(stack.heightDp.toFloat()) }
    val removeLabel = stringResource(R.string.remove_from_popup, label)
    Column(Modifier.padding(start = 8.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemText(label, Modifier.weight(1f))
            IconButton(onClick = { onRemove(ref) }, modifier = Modifier.semantics { contentDescription = removeLabel }) { RemoveGlyph(palette.onWallpaperMuted) }
        }
        Slider(
            value = height,
            onValueChange = { height = it },
            onValueChangeFinished = { onHeight(ref, height.roundToInt()) },
            valueRange = Widgets.MIN_HEIGHT_DP.toFloat()..Widgets.MAX_POPUP_HEIGHT_DP.toFloat(),
            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent, inactiveTrackColor = palette.onWallpaperMuted.copy(alpha = 0.3f)),
        )
    }
}

/**
 * Niagara's header: an "open" mark and the app's name, which opens the app, and a way to clear
 * everything when there's anything to clear.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Header(
    label: String,
    clearable: List<AppNotification>,
    onClear: (AppNotification) -> Unit,
    onOpen: ((Rect) -> Unit)?,
    onLongClick: () -> Unit,
) {
    val palette = LocalSlatePalette.current
    val bounds = remember { RowBounds() }
    val openLabel = stringResource(R.string.open_app, label)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .onPlaced { bounds.coordinates = it }
                .clip(RowShape)
                .then(
                    if (onOpen == null) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(onClickLabel = openLabel, onLongClick = onLongClick) { onOpen(bounds.bounds()) }
                    },
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onOpen != null) {
                SlateIconView(SlateIcon.OpenExternal, palette.onWallpaper, size = 16.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = SlateText.appLabel.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, shadow = null),
                color = palette.onWallpaper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (clearable.isNotEmpty()) {
            val clearLabel = stringResource(R.string.clear_notification)
            IconButton(onClick = { clearable.forEach(onClear) }, modifier = Modifier.semantics { contentDescription = clearLabel }) {
                ClearGlyph(palette.onWallpaperMuted)
            }
        }
    }
}

/** An inset row that shrinks a little under the finger. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PopupRow(
    modifier: Modifier = Modifier,
    alignment: Alignment.Vertical = Alignment.CenterVertically,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "rowPress")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RowShape)
            .combinedClickable(interactionSource = source, indication = ripple(), role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = alignment,
        content = content,
    )
}

@Composable
private fun ItemRow(label: String, icon: @Composable () -> Unit, onLongClick: () -> Unit, onClick: (Rect) -> Unit) {
    val bounds = remember { RowBounds() }
    PopupRow(Modifier.onPlaced { bounds.coordinates = it }, onLongClick = onLongClick, onClick = { onClick(bounds.bounds()) }) {
        icon()
        Spacer(Modifier.width(ItemIconGap))
        ItemText(label, Modifier.weight(1f))
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = SlateText.hint.copy(fontSize = 14.sp, shadow = null),
        color = LocalSlatePalette.current.accent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

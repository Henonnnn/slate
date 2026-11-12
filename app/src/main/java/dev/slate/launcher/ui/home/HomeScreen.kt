package dev.slate.launcher.ui.home

import android.app.ActivityOptions
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.slate.launcher.R
import dev.slate.launcher.apps.AlphabetIndex
import dev.slate.launcher.apps.AlphabetSection
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.apps.AppShortcut
import dev.slate.launcher.container
import dev.slate.launcher.data.BehaviourSettings
import dev.slate.launcher.data.ClockSettings
import dev.slate.launcher.data.IconOverride
import dev.slate.launcher.data.PopupItem
import dev.slate.launcher.data.RowSwipeAction
import dev.slate.launcher.data.ThemeSettings
import dev.slate.launcher.data.WeatherPlace
import dev.slate.launcher.data.WidgetRef
import dev.slate.launcher.floating.FloatingItem
import dev.slate.launcher.floating.FloatingSources
import dev.slate.launcher.notifications.AppNotification
import dev.slate.launcher.notifications.NotificationAccess
import dev.slate.launcher.notifications.NotificationAction
import dev.slate.launcher.notifications.NotificationReply
import dev.slate.launcher.notifications.NotificationRules
import dev.slate.launcher.sendFromForeground
import dev.slate.launcher.ui.appearance.AppearanceScreen
import dev.slate.launcher.ui.appearance.ClockScreen
import dev.slate.launcher.ui.appearance.IconPickerSheet
import dev.slate.launcher.ui.components.AlphabetScrubber
import dev.slate.launcher.ui.components.DragHandleGlyph
import dev.slate.launcher.ui.components.RemoveGlyph
import dev.slate.launcher.ui.components.SlateIcon
import dev.slate.launcher.ui.components.SlateIconView
import dev.slate.launcher.ui.components.alphabetJumpOffset
import dev.slate.launcher.ui.floating.FloatingButton
import dev.slate.launcher.ui.floating.FloatingButtonSpace
import dev.slate.launcher.ui.floating.FloatingViewModel
import dev.slate.launcher.ui.floating.PillControls
import dev.slate.launcher.ui.onboarding.DefaultHome
import dev.slate.launcher.ui.onboarding.DefaultHomeCard
import dev.slate.launcher.ui.onboarding.NotificationCard
import dev.slate.launcher.ui.search.SearchSurface
import dev.slate.launcher.ui.search.SearchViewModel
import dev.slate.launcher.ui.settings.SettingsScreen
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import dev.slate.launcher.ui.wellbeing.BreakerScreen
import dev.slate.launcher.ui.wellbeing.BreakerSheet
import dev.slate.launcher.ui.wellbeing.DashboardScreen
import dev.slate.launcher.ui.wellbeing.rememberUsageText
import dev.slate.launcher.ui.widgets.HomeMenuSheet
import dev.slate.launcher.ui.widgets.WeatherPlaceDialog
import dev.slate.launcher.ui.widgets.WidgetMenu
import dev.slate.launcher.ui.widgets.WidgetPickerSheet
import dev.slate.launcher.ui.widgets.WidgetSheet
import dev.slate.launcher.ui.widgets.WidgetStackView
import dev.slate.launcher.wellbeing.BreakerRule
import dev.slate.launcher.widgets.LocalWidgetBinder
import dev.slate.launcher.widgets.WidgetProvider
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Android's "medium" width class: unfolded foldables, small tablets, phones in landscape. */
private val TwoPaneMinWidth = 600.dp
/** How long the all-apps screen stays once nothing is touching it. */
private const val SCREEN_FADE_MS = 120
private const val FAV = "fav:"
private const val ALL = "all:"
private const val HID = "hid:"
private const val PRI = "pri:"
private const val FOL = "fol:"
private const val HUB = "hub"

/** Everything the home screen can ask for; keeps HomeScreen's signature stable as features land. */
class HomeActions(
    val launch: (AppEntry, Rect) -> Unit,
    val dismissDefaultHome: () -> Unit,
    val defaultHomeResult: (Boolean) -> Unit,
    val toggleFavourite: (AppEntry) -> Unit,
    val reorderFavourites: (List<String>) -> Unit,
    val hide: (AppEntry) -> Unit,
    val unhide: (AppEntry) -> Unit,
    val rename: (AppEntry, String) -> Unit,
    val appInfo: (AppEntry, Rect) -> Unit,
    val uninstall: (AppEntry) -> Unit,
    val launchShortcut: (AppShortcut, Rect) -> Unit,
    /** System "default home app" page; shortcuts need the home role. */
    val openHomeSettings: () -> Unit,
    val addToPopup: (owner: AppEntry, PopupItem) -> Unit,
    val removeFromPopup: (owner: AppEntry, PopupItem) -> Unit,
    val togglePopupItem: (owner: AppEntry, PopupItem) -> Unit,
    val reorderPopup: (owner: AppEntry, List<PopupItem>) -> Unit,
    val addAppWidget: (WidgetProvider, String?) -> Unit,
    val addPopupBuiltinWidget: (String, WidgetRef) -> Unit,
    val removePopupWidget: (String, WidgetRef) -> Unit,
    val setPopupWidgetHeight: (String, WidgetRef, Int) -> Unit,
    val addBuiltinWidget: (WidgetRef) -> Unit,
    val removeWidget: (WidgetRef) -> Unit,
    val moveWidget: (WidgetRef, Int) -> Unit,
    val stackWidgetWithAbove: (WidgetRef) -> Unit,
    val unstackWidget: (WidgetRef) -> Unit,
    val setWidgetHeight: (WidgetRef, Int) -> Unit,
    val reconfigureWidget: (Int) -> Unit,
    val setWeatherPlace: (WeatherPlace) -> Unit,
    val dismissNotificationPrompt: () -> Unit,
    val openNotification: (AppNotification) -> Unit,
    val dismissNotification: (AppNotification) -> Unit,
    val runNotificationAction: (NotificationAction) -> Unit,
    val replyToNotification: (NotificationReply, String) -> Boolean,
    val setTheme: (ThemeSettings) -> Unit,
    val saveTheme: (String) -> Unit,
    val deleteTheme: (String) -> Unit,
    val applyPreset: (String) -> Unit,
    val setIconOverride: (AppEntry, IconOverride?) -> Unit,
    val setFloatingSources: (FloatingSources) -> Unit,
    val setBreakerRule: (String, BreakerRule?) -> Unit,
    val grantBreaker: (BreakerPrompt, Int) -> Unit,
    val cancelBreaker: () -> Unit,
    val setBehaviour: (BehaviourSettings) -> Unit,
    val setClock: (ClockSettings) -> Unit,
    val createFolder: (String, Boolean) -> String,
    val deleteFolder: (AppEntry) -> Unit,
    val setPrivateSpaceLocked: (Boolean) -> Unit,
    val openPrivateSpaceSettings: () -> Unit,
)

/** The Pill's state and controls. */
class FloatingState(
    val items: List<FloatingItem>,
    val controls: PillControls,
)

@Composable
fun HomeRoute(
    homePresses: Flow<Unit>,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
    floatingViewModel: FloatingViewModel = viewModel(factory = FloatingViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val widgetBinder = LocalWidgetBinder.current

    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onDefaultHomeChecked(DefaultHome.isHeld(context))
        val notificationsAllowed = NotificationAccess.isGranted(context)
        viewModel.onNotificationAccessChecked(notificationsAllowed)
        if (notificationsAllowed && !context.container.notificationStore.connected.value) NotificationAccess.reconnect(context)
    }
    val floatingItems by floatingViewModel.items.collectAsStateWithLifecycle()
    val floating = FloatingState(
        items = floatingItems,
        controls = remember(floatingViewModel, context) {
            PillControls(
                dismiss = floatingViewModel::dismiss,
                togglePlay = floatingViewModel::togglePlay,
                next = floatingViewModel::next,
                previous = floatingViewModel::previous,
                seekTo = floatingViewModel::seekTo,
                openMedia = { floatingViewModel.openMedia(context) },
                openLive = { floatingViewModel.openLive(context, it) },
                liveAction = { floatingViewModel.liveAction(context, it) },
                pressPlay = floatingViewModel::pressPlay,
                openApp = { floatingViewModel.openApp(it) },
                setTorch = { on -> if (!floatingViewModel.setTorch(on)) Toast.makeText(context, R.string.torch_busy, Toast.LENGTH_SHORT).show() },
            )
        },
    )

    val actions = remember(viewModel) {
        fun failed(label: String) =
            Toast.makeText(context, context.getString(R.string.launch_failed, label), Toast.LENGTH_SHORT).show()
        fun launchApp(entry: AppEntry, bounds: Rect) {
            val rect = bounds.toAndroidRect()
            val options = ActivityOptions.makeScaleUpAnimation(view, rect.left, rect.top, rect.width(), rect.height())
            if (!viewModel.launch(entry, rect, options.toBundle())) failed(entry.label)
        }
        fun launchShortcut(shortcut: AppShortcut, bounds: Rect) {
            val rect = bounds.toAndroidRect()
            val options = ActivityOptions.makeScaleUpAnimation(view, rect.left, rect.top, rect.width(), rect.height())
            if (!viewModel.launchShortcut(shortcut, rect, options.toBundle())) failed(shortcut.label)
        }
        HomeActions(
            // Every launch path goes through the Usage Breaker check.
            launch = { entry, bounds ->
                viewModel.launchGuarded(entry.packageName, entry.label, BreakerTarget.App(entry)) { launchApp(entry, bounds) }
            },
            dismissDefaultHome = viewModel::dismissDefaultHomePrompt,
            defaultHomeResult = viewModel::onDefaultHomeChecked,
            toggleFavourite = viewModel::toggleFavourite,
            reorderFavourites = viewModel::reorderFavourites,
            hide = viewModel::hide,
            unhide = viewModel::unhide,
            rename = viewModel::rename,
            appInfo = { entry, bounds ->
                if (!viewModel.openAppInfo(entry, bounds.toAndroidRect())) failed(entry.label)
            },
            uninstall = { entry ->
                runCatching { context.startActivity(viewModel.uninstallIntent(entry)) }.onFailure { failed(entry.label) }
            },
            launchShortcut = { shortcut, bounds ->
                val label = viewModel.state.value.apps.firstOrNull { it.packageName == shortcut.packageName }?.label ?: shortcut.label
                viewModel.launchGuarded(shortcut.packageName, label, BreakerTarget.Shortcut(shortcut)) { launchShortcut(shortcut, bounds) }
            },
            openHomeSettings = {
                runCatching { context.startActivity(DefaultHome.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            addToPopup = { owner, item ->
                viewModel.addToPopup(owner, item)
                Toast.makeText(context, context.getString(R.string.added_to_popup, owner.label), Toast.LENGTH_SHORT).show()
            },
            removeFromPopup = viewModel::removeFromPopup,
            togglePopupItem = viewModel::togglePopupItem,
            reorderPopup = viewModel::reorderPopup,
            addAppWidget = { provider, popup -> widgetBinder?.add(provider, popup) },
            addPopupBuiltinWidget = viewModel::addPopupBuiltinWidget,
            removePopupWidget = viewModel::removePopupWidget,
            setPopupWidgetHeight = viewModel::setPopupWidgetHeight,
            addBuiltinWidget = viewModel::addBuiltinWidget,
            removeWidget = viewModel::removeWidget,
            moveWidget = viewModel::moveWidget,
            stackWidgetWithAbove = viewModel::stackWidgetWithAbove,
            unstackWidget = viewModel::unstackWidget,
            setWidgetHeight = viewModel::setWidgetHeight,
            reconfigureWidget = { id -> widgetBinder?.reconfigure(id) },
            setWeatherPlace = viewModel::setWeatherPlace,
            dismissNotificationPrompt = viewModel::dismissNotificationPrompt,
            openNotification = { n ->
                if (!context.container.notificationStore.open(context, n)) {
                    Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
                }
            },
            dismissNotification = viewModel::dismissNotification,
            runNotificationAction = { action ->
                if (!action.intent.sendFromForeground(context)) {
                    Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
                }
            },
            replyToNotification = { reply, text -> context.container.notificationStore.reply(context, reply, text) },
            setTheme = viewModel::setTheme,
            saveTheme = viewModel::saveTheme,
            deleteTheme = viewModel::deleteTheme,
            applyPreset = viewModel::applyPreset,
            setIconOverride = viewModel::setIconOverride,
            setFloatingSources = viewModel::setFloatingSources,
            setBreakerRule = viewModel::setBreakerRule,
            grantBreaker = { prompt, minutes ->
                viewModel.grant(context, prompt, minutes)
                when (val t = prompt.target) {
                    is BreakerTarget.App -> launchApp(t.entry, Rect.Zero)
                    is BreakerTarget.Shortcut -> launchShortcut(t.shortcut, Rect.Zero)
                }
            },
            cancelBreaker = viewModel::cancelBreaker,
            setBehaviour = viewModel::setBehaviour,
            setClock = viewModel::setClock,
            createFolder = viewModel::createFolder,
            deleteFolder = viewModel::deleteFolder,
            setPrivateSpaceLocked = viewModel::setPrivateSpaceLocked,
            openPrivateSpaceSettings = viewModel::openPrivateSpaceSettings,
        )
    }
    val breakerPrompt by viewModel.breakerPrompt.collectAsStateWithLifecycle()
    HomeScreen(state, notifications, floating, breakerPrompt, homePresses, actions, searchViewModel)
}

private fun Rect.toAndroidRect() =
    android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

@Composable
fun HomeScreen(
    state: HomeUiState,
    notifications: Map<String, List<AppNotification>>,
    floating: FloatingState,
    breakerPrompt: BreakerPrompt?,
    homePresses: Flow<Unit>,
    actions: HomeActions,
    search: SearchViewModel,
) {
    val palette = LocalSlatePalette.current
    val view = LocalView.current
    val listState = rememberLazyListState()
    // Home and all apps are separate screens, so each list keeps its own place. On a wide window
    // they sit side by side.
    val topListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var editing by rememberSaveable { mutableStateOf(false) }
    var sheetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var renameKey by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    // Open pop-up: owner app key, the point it grows from, and edit mode.
    var popupOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var popupAnchor by remember { mutableStateOf(Offset.Unspecified) }
    var popupEditing by rememberSaveable { mutableStateOf(false) }
    var pickerOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var addToPopupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var homeMenu by rememberSaveable { mutableStateOf(false) }
    var widgetPicker by rememberSaveable { mutableStateOf(false) }
    // The pop-up the picked widget goes in; null for the home screen.
    var widgetPickerOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var widgetMenuKey by rememberSaveable { mutableStateOf<String?>(null) }
    var weatherDialog by rememberSaveable { mutableStateOf(false) }
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var iconPickerKey by rememberSaveable { mutableStateOf<String?>(null) }
    var dashboardOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var clockOpen by rememberSaveable { mutableStateOf(false) }
    var createFolder by rememberSaveable { mutableStateOf(false) }
    var pendingFolderEdit by remember { mutableStateOf<String?>(null) }
    // Move mode: dragging the clock, widgets and favourites up or down together.
    var moving by remember { mutableStateOf(false) }
    // Which screen shows on a narrow window. Only the alphabet opens all apps.
    var allApps by rememberSaveable { mutableStateOf(false) }
    var moveDraft by remember { mutableFloatStateOf(0f) }
    // Stays on after Done until the saved value catches up, so the list doesn't hop back.
    var useMoveDraft by remember { mutableStateOf(false) }
    // Usage Breaker settings sheet: (package, label).
    var breakerSheet by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val usageText = rememberUsageText()
    // Live height while the widget menu's slider is dragged: (widget key, dp).
    var heightPreview by remember { mutableStateOf<Pair<String, Int>?>(null) }

    // Favourites order while editing: local so drags are instant, committed on drop.
    var editOrder by remember { mutableStateOf(state.favourites) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.favourites) { if (!dragging) editOrder = state.favourites }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = (from.key as? String)?.removePrefix(FAV) ?: return@rememberReorderableLazyListState
        val toKey = (to.key as? String)?.takeIf { it.startsWith(FAV) }?.removePrefix(FAV) ?: return@rememberReorderableLazyListState
        val list = editOrder.toMutableList()
        val fromIndex = list.indexOfFirst { it.key == fromKey }
        val toIndex = list.indexOfFirst { it.key == toKey }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        list.add(toIndex, list.removeAt(fromIndex))
        editOrder = list
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    val appsByNotificationKey = remember(state.apps, state.hidden) { (state.apps + state.hidden).associateBy { it.notificationKey } }
    // A folder gathers the notifications of the apps inside it.
    fun notificationsFor(entry: AppEntry): List<AppNotification> =
        if (!entry.isFolder) {
            notifications[entry.notificationKey].orEmpty()
        } else {
            state.popups[entry.key].orEmpty().filter { it.kind == PopupItem.Kind.App }
                .mapNotNull { state.find(it.key)?.notificationKey }.distinct()
                .flatMap { notifications[it].orEmpty() }
                .sortedWith(NotificationRules.order)
        }

    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val wideWindow = with(density) { windowSize.width.toDp() } >= TwoPaneMinWidth

    // Home pressed while already home → close search / leave edit mode and go to the top.
    // Back does the same and never exits the launcher.
    LaunchedEffect(homePresses) {
        homePresses.collect {
            appearanceOpen = false
            dashboardOpen = false
            settingsOpen = false
            clockOpen = false
            moving = false
            useMoveDraft = false
            searching = false
            editing = false
            popupOwner = null
            allApps = false
            topListState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(state.apps, state.hidden, state.privateApps, state.behaviour.searchHiddenApps) {
        // Private apps are only in the list while unlocked, so locking removes them from search too.
        search.setApps((if (state.behaviour.searchHiddenApps) state.apps + state.hidden else state.apps) + state.privateApps)
    }
    val openSearch = {
        sheetKey = null
        searching = true
    }
    val pull = rememberPullToSearch(enabled = !editing && !searching && state.behaviour.pullDownToSearch, onTrigger = openSearch)
    BackHandler {
        when {
            editing -> editing = false
            allApps -> allApps = false
            else -> scope.launch { topListState.animateScrollToItem(0) }
        }
    }

    var scrubbing by remember { mutableStateOf(false) }
    // All apps closes by itself once it's left alone. A pop-up or sheet open on a row counts as
    // in use, or the row would vanish under it.
    LaunchedEffect(allApps, state.behaviour.allAppsCloseSeconds) {
        if (!allApps) {
            listState.scrollToItem(0)
            return@LaunchedEffect
        }
        snapshotFlow { listState.isScrollInProgress || scrubbing || popupOwner != null || sheetKey != null }.collectLatest { active ->
            val seconds = state.behaviour.allAppsCloseSeconds
            if (!active && seconds > 0) {
                delay(seconds * 1000L)
                allApps = false
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { allApps = false }
    val appsHidden = !wideWindow && !allApps
    val allAppsFade by animateFloatAsState(if (allApps) 1f else 0f, tween(SCREEN_FADE_MS), label = "allAppsFade")
    val rubber = rememberRubberBand(
        canSearch = !editing && !searching && !allApps && state.behaviour.pullDownToSearch,
        acceptsDown = allApps || !state.behaviour.pullDownToSearch,
        onSearch = { openSearch() },
    )
    val startMoving: () -> Unit = {
        moveDraft = state.behaviour.homeOffset
        useMoveDraft = true
        allApps = false
        appearanceOpen = false
        settingsOpen = false
        clockOpen = false
        moving = true
        scope.launch { topListState.scrollToItem(0) }
    }
    val savedOffset by rememberUpdatedState(state.behaviour.homeOffset)
    LaunchedEffect(savedOffset) { if (!moving) useMoveDraft = false }
    // Read during layout, so dragging moves the list without recomposing it.
    val homeOffsetPx: () -> Int = { ((if (useMoveDraft) moveDraft else savedOffset) * windowSize.height).roundToInt() }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    // Appearance draws its own scrim over the bare wallpaper as a live preview, so home steps aside.
    val homeAlpha by animateFloatAsState(if (appearanceOpen || dashboardOpen || settingsOpen || clockOpen || breakerPrompt != null) 0f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "homeAlpha")
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = homeAlpha }
            .background(palette.scrim),
    ) {
        // Wait for the first load before composing the list so a restored scroll
        // position (rememberSaveable inside the state) isn't clamped to an empty list.
        if (state.loading) {
            HomeHeader(state.clock, state.weatherPlace, homeOffsetPx, Modifier.padding(top = insets.calculateTopPadding()))
            return@Box
        }

        val openSheet: (AppEntry) -> Unit = { sheetKey = it.key }
        val openPopup: (AppEntry, Offset) -> Unit = { entry, at ->
            popupAnchor = at
            popupOwner = entry.key
            popupEditing = false
        }
        val popupLabel = stringResource(R.string.popup_open)
        val appInfoLabel = stringResource(R.string.app_info)
        val hideLabel = stringResource(R.string.hide)
        val pinLabel = stringResource(R.string.swipe_favourite)
        val unpinLabel = stringResource(R.string.swipe_unfavourite)
        fun swipeAction(action: RowSwipeAction): RowSwipe.Action? = when (action) {
            RowSwipeAction.Popup -> RowSwipe.Action({ popupLabel }, immediate = true, run = openPopup)
            RowSwipeAction.AppInfo -> RowSwipe.Action({ appInfoLabel }) { entry, _ -> actions.appInfo(entry, Rect.Zero) }
            RowSwipeAction.Favourite -> RowSwipe.Action({ if (state.isFavourite(it)) unpinLabel else pinLabel }) { entry, _ -> actions.toggleFavourite(entry) }
            RowSwipeAction.Hide -> RowSwipe.Action({ hideLabel }) { entry, _ -> actions.hide(entry) }
            RowSwipeAction.None -> null
        }
        // App actions mean nothing on a folder, so any swipe just opens it.
        val folderSwipe = RowSwipe(RowSwipe.Action({ popupLabel }, immediate = true, run = openPopup), null)
        val rowSwipe = run {
            val end = swipeAction(state.behaviour.rowSwipeRight)
            val start = swipeAction(state.behaviour.rowSwipeLeft)
            if (end == null && start == null) null else RowSwipe(end, start)
        }
        val searchLabel = stringResource(R.string.search)
        // Search must stay reachable, so the button can only go if the pull gesture is on.
        val showSearchButton = state.behaviour.showSearchButton || !state.behaviour.pullDownToSearch
        val twoPane = !editing && wideWindow
        // Rows of the hidden screen stay laid out, so hide them from screen readers too.
        val restModifier = if (appsHidden) Modifier.clearAndSetSemantics {} else Modifier

        @Composable
        fun ListRow(entry: AppEntry, swipe: RowSwipe?, showPreview: Boolean, modifier: Modifier, hiddenAtRest: Boolean = false) {
            AppRow(
                entry = entry,
                onClick = { e, bounds ->
                    when {
                        hiddenAtRest -> Unit
                        e.isFolder -> openPopup(e, bounds.center)
                        else -> actions.launch(e, bounds)
                    }
                },
                onLongClick = openSheet.takeUnless { hiddenAtRest },
                swipe = (if (entry.isFolder) folderSwipe else swipe).takeUnless { hiddenAtRest },
                hasPopup = state.hasPopup(entry) && !entry.isFolder,
                notification = notificationsFor(entry).firstOrNull(),
                showPreview = showPreview && state.behaviour.notificationPreviews,
                onExpandNotification = { e, bounds -> openPopup(e, bounds.center) },
                modifier = modifier,
            )
        }

        fun LazyListScope.topItems() {
            item(key = "header", contentType = "header") {
                HomeHeader(state.clock, state.weatherPlace, homeOffsetPx, onSearch = openSearch.takeIf { showSearchButton }, onLongPress = { homeMenu = true })
            }
            items(state.widgets, key = { "widget:" + it.key }, contentType = { "widget" }) { stack ->
                val preview = heightPreview?.takeIf { (key, _) -> stack.items.any { it.key == key } }?.second
                WidgetStackView(
                    stack = stack,
                    heightDp = preview ?: stack.heightDp,
                    weatherPlace = state.weatherPlace,
                    onLongPress = { widgetMenuKey = it.key },
                    onChooseWeatherPlace = { weatherDialog = true },
                    modifier = Modifier.animateItem(),
                )
            }
            if (state.showDefaultHomePrompt) {
                item(key = "default-home", contentType = "card") {
                    DefaultHomeCard(onDismiss = actions.dismissDefaultHome, onResult = actions.defaultHomeResult)
                }
            }
            if (state.showNotificationPrompt) {
                item(key = "notification-access", contentType = "card") {
                    NotificationCard(onDismiss = actions.dismissNotificationPrompt)
                }
            }
            if (state.favourites.isEmpty()) {
                item(key = "favourites-hint", contentType = "hint") {
                    Text(
                        stringResource(R.string.favourites_hint),
                        style = SlateText.hint,
                        color = palette.onWallpaperMuted,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    )
                }
            }
            items(state.favourites, key = { FAV + it.key }, contentType = { "app" }) { entry ->
                ListRow(entry, rowSwipe, showPreview = true, Modifier.animateItem())
            }
        }

        fun LazyListScope.allAppItems() {
            items(state.folders, key = { FOL + it.key }, contentType = { "app" }) { entry ->
                ListRow(entry, null, showPreview = false, Modifier.animateItem().then(restModifier), hiddenAtRest = appsHidden)
            }
            items(state.apps, key = { ALL + it.key }, contentType = { "app" }) { entry ->
                ListRow(entry, rowSwipe, showPreview = false, Modifier.animateItem().then(restModifier), hiddenAtRest = appsHidden)
            }
            val privateSpace = state.privateSpace?.takeIf { state.behaviour.showPrivateSpace } ?: return
            sectionLabel("private-header", R.string.private_space, restModifier)
            if (privateSpace.locked) {
                item(key = "private-unlock", contentType = "private-action") {
                    PrivateSpaceRow(SlateIcon.Lock, R.string.private_space_unlock, restModifier) { actions.setPrivateSpaceLocked(false) }
                }
                return
            }
            items(state.privateApps, key = { PRI + it.key }, contentType = { "app" }) { entry ->
                ListRow(entry, null, showPreview = false, Modifier.animateItem().then(restModifier), hiddenAtRest = appsHidden)
            }
            item(key = "private-lock", contentType = "private-action") {
                PrivateSpaceRow(SlateIcon.Lock, R.string.private_space_lock, restModifier) { actions.setPrivateSpaceLocked(true) }
            }
            item(key = "private-settings", contentType = "private-action") {
                PrivateSpaceRow(SlateIcon.Shield, R.string.private_space_settings, restModifier, actions.openPrivateSpaceSettings)
            }
        }

        val allAppsLabel = stringResource(R.string.all_apps)
        val listPadding = PaddingValues(
            top = insets.calculateTopPadding(),
            bottom = insets.calculateBottomPadding() + 32.dp + if (floating.items.isNotEmpty()) FloatingButtonSpace else 0.dp,
        )
        val listsModifier = Modifier
            .fillMaxSize()
            .fadeAtEdges(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .nestedScroll(pull.connection)
            .nestedScroll(rubber.connection)
            .graphicsLayer { translationY = pull.distance + rubber.shown }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(searchLabel) { openSearch(); true },
                    CustomAccessibilityAction(allAppsLabel) { allApps = !allApps; true },
                )
            }
        if (twoPane) {
            // Landscape and unfolded screens: home on one side, every app on the other. Equal
            // weights put the split on the hinge of a book-style foldable.
            Row(listsModifier) {
                LazyColumn(state = topListState, modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = listPadding) { topItems() }
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = listPadding) { allAppItems() }
            }
        } else if (!editing) {
            Box(listsModifier) {
                LazyColumn(
                    state = topListState,
                    modifier = Modifier.fillMaxSize().zIndex(if (allApps) 0f else 1f).graphicsLayer { alpha = 1f - allAppsFade },
                    contentPadding = listPadding,
                    userScrollEnabled = !allApps,
                ) { topItems() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().zIndex(if (allApps) 1f else 0f).graphicsLayer { alpha = allAppsFade },
                    contentPadding = listPadding,
                    userScrollEnabled = allApps,
                ) { allAppItems() }
            }
        } else LazyColumn(state = listState, modifier = listsModifier, contentPadding = listPadding) {
            item(key = "edit-header", contentType = "edit-header") {
                EditHeader(onDone = { editing = false })
            }
            items(editOrder, key = { FAV + it.key }, contentType = { "app-edit" }) { entry ->
                ReorderableItem(reorderState, key = FAV + entry.key) { isDragging ->
                    val handleLabel = stringResource(R.string.drag_to_reorder, entry.label)
                    val removeLabel = stringResource(R.string.remove_favourite_named, entry.label)
                    AppRow(
                        entry = entry,
                        onClick = { _, _ -> },
                        onLongClick = null,
                        modifier = Modifier.graphicsLayer { alpha = if (isDragging) 0.85f else 1f },
                        trailing = {
                            IconButton(
                                onClick = { actions.toggleFavourite(entry) },
                                modifier = Modifier.semantics { contentDescription = removeLabel },
                            ) {
                                RemoveGlyph(palette.onWallpaperMuted)
                            }
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
                                            actions.reorderFavourites(editOrder.map { it.key })
                                        },
                                    )
                                    .semantics { contentDescription = handleLabel },
                            ) { DragHandleGlyph(palette.onWallpaper) }
                        },
                    )
                }
            }
            if (state.hidden.isNotEmpty()) {
                sectionLabel("hidden-label", R.string.hidden_apps)
                items(state.hidden, key = { HID + it.key }, contentType = { "app-edit" }) { entry ->
                    AppRow(
                        entry = entry,
                        onClick = actions.launch,
                        onLongClick = null,
                        trailing = {
                            TextButton(onClick = { actions.unhide(entry) }) {
                                Text(stringResource(R.string.unhide), color = palette.accent)
                            }
                        },
                    )
                }
            }
        }

        // Not under another screen: its strip claims the screen edge from the system's back gesture.
        val covered = appearanceOpen || settingsOpen || clockOpen || dashboardOpen || breakerPrompt != null
        if (!editing && !searching && !moving && !covered && state.behaviour.showScrubber) {
            val showPrivateStop = state.privateSpace != null && state.behaviour.showPrivateSpace
            AlphabetScrubber(
                sections = remember(state.letterIndex, showPrivateStop, twoPane, state.favourites.isEmpty(), state.folders.isEmpty()) {
                    AlphabetIndex.sections(
                        state.letterIndex.keys,
                        favourites = !twoPane && state.favourites.isNotEmpty(),
                        privateSpace = showPrivateStop,
                        folders = state.folders.isNotEmpty(),
                    )
                },
                onActiveChange = { scrubbing = it },
                onSection = { section ->
                    if (section == AlphabetSection.Favourites) {
                        allApps = false
                    } else {
                        val target = when (section) {
                            is AlphabetSection.Letter -> AlphabetIndex.resolve(section.char, state.letterIndex)?.plus(state.folders.size)
                            AlphabetSection.Folders -> 0
                            else -> state.folders.size + state.apps.size
                        }
                        allApps = true
                        // The chosen section lands level with the alphabet, not at the very top.
                        val offset = -alphabetJumpOffset(windowSize.height.toFloat(), density).roundToInt()
                        if (target != null) scope.launch { listState.scrollToItem(target, offset) }
                    }
                },
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }

        // Last, so the card it grows into covers the alphabet.
        FloatingButton(
            items = if (!editing && !searching && !moving && popupOwner == null) floating.items else emptyList(),
            controls = floating.controls,
            options = state.floating,
            bottomPadding = insets.calculateBottomPadding() + 12.dp,
        )
    }

    // The panel outlives popupOwner for its closing animation, so it keeps the last owner.
    val hubName = stringResource(R.string.message_hub)
    val hubEntry = remember(hubName) { AppEntry.folder(HUB, hubName) }
    val popupEntry = if (popupOwner == HUB) hubEntry else popupOwner?.let(state::find)
    val lastPopupEntry = remember { mutableStateOf<AppEntry?>(null) }
    if (popupEntry != null) lastPopupEntry.value = popupEntry
    val screenSize = windowSize.toSize()
    val popupOrigin = popupAnchor.takeIf { it.isSpecified } ?: Offset(screenSize.width / 2, screenSize.height / 2)
    val closeLabel = stringResource(R.string.done)
    AnimatedVisibility(
        visible = popupEntry != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(POPUP_CLOSE_MS)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // As in Niagara: only Message Hub veils the home screen; a pop-up floats on it as is.
                .background(if (popupEntry === hubEntry || (popupEntry == null && lastPopupEntry.value === hubEntry)) palette.panelRaised.copy(alpha = HUB_VEIL) else if (state.behaviour.popupDim) PopupDim else Color.Transparent)
                .pointerInput(Unit) { detectTapGestures { popupOwner = null } }
                .semantics { onClick(closeLabel) { popupOwner = null; true } },
        )
    }
    AnimatedVisibility(
        visible = popupEntry != null,
        enter = EnterTransition.None,
        exit = popupExit(popupOrigin, screenSize),
    ) {
        lastPopupEntry.value?.let { entry ->
            PopupPanel(
                owner = entry,
                anchor = popupOrigin,
                hub = entry === hubEntry,
                maxShortcuts = state.behaviour.popupShortcuts,
                expandFirst = state.behaviour.popupExpandNotification,
                ownerOf = { n -> appsByNotificationKey[n.appKey].takeIf { entry === hubEntry } },
                items = rememberResolvedPopup(state.popups[entry.key].orEmpty(), state::find),
                widgets = state.popupWidgets[entry.key].orEmpty(),
                weatherPlace = state.weatherPlace,
                notifications = if (entry === hubEntry) {
                    notifications.values.flatten().sortedWith(NotificationRules.order)
                } else {
                    notificationsFor(entry)
                },
                editing = popupEditing,
                onLaunchApp = { app, bounds ->
                    popupOwner = null
                    actions.launch(app, bounds)
                },
                onLaunchShortcut = { shortcut, bounds ->
                    popupOwner = null
                    actions.launchShortcut(shortcut, bounds)
                },
                onOpenNotification = { n ->
                    popupOwner = null
                    actions.openNotification(n)
                },
                onClearNotification = actions.dismissNotification,
                onNotificationAction = actions.runNotificationAction,
                onReply = actions.replyToNotification,
                onEdit = { popupEditing = true },
                onDone = { popupEditing = false },
                onAdd = { pickerOwner = entry.key },
                onRemove = { actions.removeFromPopup(entry, it) },
                onReorder = { actions.reorderPopup(entry, it) },
                onAddWidget = {
                    widgetPickerOwner = entry.key
                    widgetPicker = true
                },
                onRemoveWidget = { actions.removePopupWidget(entry.key, it) },
                onWidgetHeight = { ref, height -> actions.setPopupWidgetHeight(entry.key, ref, height) },
                onChooseWeatherPlace = { weatherDialog = true },
                onDismiss = { popupOwner = null },
            )
        }
    }

    if (moving) {
        MoveHomeOverlay(
            offset = { moveDraft },
            onOffset = { moveDraft = it },
            onReset = {
                scope.launch { animate(moveDraft, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)) { v, _ -> moveDraft = v } }
            },
            onDone = {
                actions.setBehaviour(state.behaviour.copy(homeOffset = moveDraft))
                moving = false
            },
            onCancel = {
                moving = false
                useMoveDraft = false
            },
        )
    }

    AnimatedVisibility(
        visible = searching,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { -it / 12 },
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)),
    ) {
        DisposableEffect(Unit) { onDispose { search.onClosed() } }
        SearchSurface(
            viewModel = search,
            onClose = { searching = false },
            onLaunchApp = { entry, bounds ->
                actions.launch(entry, bounds)
                searching = false
            },
            onAppLongPress = { sheetKey = it.key },
        )
    }

    if (popupOwner != null) {
        BackHandler { if (popupEditing) popupEditing = false else popupOwner = null }
    }

    pickerOwner?.let(state::find)?.let { owner ->
        PopupPickerSheet(
            owner = owner,
            apps = state.apps,
            selected = state.popups[owner.key].orEmpty().toSet(),
            onToggle = { actions.togglePopupItem(owner, it) },
            onDismiss = { pickerOwner = null },
        )
    }

    addToPopupKey?.let(state::find)?.let { entry ->
        AddToPopupDialog(
            entry = entry,
            favourites = state.favourites,
            onPick = { owner ->
                addToPopupKey = null
                actions.addToPopup(owner, PopupItem.app(entry.key))
            },
            onDismiss = { addToPopupKey = null },
        )
    }

    if (homeMenu) {
        HomeMenuSheet(
            notificationAccess = state.notificationAccess,
            onAppearance = {
                homeMenu = false
                appearanceOpen = true
            },
            onClock = {
                homeMenu = false
                clockOpen = true
            },
            onMoveHome = {
                homeMenu = false
                startMoving()
            },
            onScreenTime = {
                homeMenu = false
                dashboardOpen = true
            },
            onMessageHub = {
                homeMenu = false
                popupAnchor = Offset.Unspecified
                popupEditing = false
                popupOwner = HUB
            },
            onCreateFolder = {
                homeMenu = false
                createFolder = true
            },
            onSettings = {
                homeMenu = false
                settingsOpen = true
            },
            onAddWidget = {
                homeMenu = false
                widgetPicker = true
            },
            onDismiss = { homeMenu = false },
        )
    }

    if (widgetPicker) {
        WidgetPickerSheet(
            placedBuiltins = state.widgets.flatMap { it.items }.map { it.kind }.toSet(),
            onBuiltin = { ref ->
                widgetPicker = false
                widgetPickerOwner?.let { actions.addPopupBuiltinWidget(it, ref) } ?: actions.addBuiltinWidget(ref)
            },
            onProvider = { provider ->
                widgetPicker = false
                actions.addAppWidget(provider, widgetPickerOwner)
            },
            onDismiss = {
                widgetPicker = false
                widgetPickerOwner = null
            },
        )
    }

    val widgetStacks = state.widgets
    widgetMenuKey?.let { key ->
        val index = widgetStacks.indexOfFirst { s -> s.items.any { it.key == key } }
        if (index < 0) return@let
        val stack = widgetStacks[index]
        val ref = stack.items.first { it.key == key }
        val repository = LocalContext.current.container.widgetRepository
        val appInfo = remember(ref) { if (ref.kind == WidgetRef.Kind.App) repository.info(ref.appWidgetId) else null }
        val title = when (ref.kind) {
            WidgetRef.Kind.App -> appInfo?.let(repository::providerLabel) ?: stringResource(R.string.widget_unavailable)
            WidgetRef.Kind.Calendar -> stringResource(R.string.widget_calendar)
            WidgetRef.Kind.Weather -> stringResource(R.string.widget_weather)
        }
        val close = {
            widgetMenuKey = null
            heightPreview = null
        }
        WidgetSheet(
            menu = WidgetMenu(
                title = title,
                heightDp = stack.heightDp,
                canMoveUp = index > 0,
                canMoveDown = index < widgetStacks.lastIndex,
                canStackWithAbove = index > 0,
                isStacked = stack.items.size > 1,
                canReconfigure = appInfo != null && repository.isReconfigurable(appInfo),
                isWeather = ref.kind == WidgetRef.Kind.Weather,
            ),
            onHeightPreview = { dp -> heightPreview = dp?.let { key to it } },
            onHeight = { dp -> actions.setWidgetHeight(ref, dp) },
            onMoveUp = { actions.moveWidget(ref, -1); close() },
            onMoveDown = { actions.moveWidget(ref, 1); close() },
            onStackWithAbove = { actions.stackWidgetWithAbove(ref); close() },
            onUnstack = { actions.unstackWidget(ref); close() },
            onReconfigure = { actions.reconfigureWidget(ref.appWidgetId); close() },
            onChangePlace = { close(); weatherDialog = true },
            onRemove = { actions.removeWidget(ref); close() },
            onDismiss = close,
        )
    }

    // These screens are see-through, so one opened over another hides what's beneath it.
    AnimatedVisibility(
        visible = appearanceOpen && !clockOpen,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)),
    ) {
        AppearanceScreen(
            theme = state.theme,
            savedThemes = state.savedThemes,
            onSaveTheme = actions.saveTheme,
            onDeleteTheme = actions.deleteTheme,
            sample = state.favourites.ifEmpty { state.apps },
            onChange = actions.setTheme,
            onPreset = actions.applyPreset,
            onClock = { clockOpen = true },
            onClose = { appearanceOpen = false },
        )
    }

    AnimatedVisibility(
        visible = settingsOpen && !appearanceOpen && !clockOpen && !dashboardOpen,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)),
    ) {
        SettingsScreen(
            theme = state.theme,
            clock = state.clock,
            behaviour = state.behaviour,
            hiddenCount = state.hidden.size,
            hasPrivateSpace = state.privateSpace != null,
            floating = state.floating,
            notificationAccess = state.notificationAccess,
            onFloating = actions.setFloatingSources,
            onBehaviour = actions.setBehaviour,
            onAppearance = { appearanceOpen = true },
            onClock = { clockOpen = true },
            onScreenTime = { dashboardOpen = true },
            onMoveHome = startMoving,
            onEditHome = {
                settingsOpen = false
                editing = true
                scope.launch { listState.scrollToItem(0) }
            },
            onClose = { settingsOpen = false },
        )
    }

    AnimatedVisibility(
        visible = clockOpen,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)),
    ) {
        ClockScreen(
            clock = state.clock,
            theme = state.theme,
            place = state.weatherPlace,
            onChoosePlace = { weatherDialog = true },
            onChange = actions.setClock,
            onMoveHome = startMoving,
            onClose = { clockOpen = false },
        )
    }

    AnimatedVisibility(
        visible = dashboardOpen,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)),
    ) {
        DashboardScreen(
            apps = state.apps + state.hidden,
            breakerPackages = state.breaker.keys,
            usageText = usageText,
            onApp = { pkg, label -> breakerSheet = listOf(pkg, label) },
            onClose = { dashboardOpen = false },
        )
    }

    breakerSheet?.let { (pkg, label) ->
        BreakerSheet(
            label = label,
            packageName = pkg,
            rule = state.breaker[pkg],
            usageText = usageText,
            onRule = { actions.setBreakerRule(pkg, it) },
            onDismiss = { breakerSheet = null },
        )
    }

    breakerPrompt?.let { prompt ->
        BreakerScreen(
            label = prompt.label,
            entry = state.apps.firstOrNull { it.packageName == prompt.packageName } ?: state.hidden.firstOrNull { it.packageName == prompt.packageName },
            pause = prompt.pause,
            usageText = usageText,
            onOpenFor = { minutes ->
                searching = false
                popupOwner = null
                actions.grantBreaker(prompt, minutes)
            },
            onCancel = actions.cancelBreaker,
        )
    }

    iconPickerKey?.let(state::find)?.let { entry ->
        IconPickerSheet(
            entry = entry,
            current = state.iconOverrides[entry.key],
            preferredPack = state.theme.iconPack,
            onPick = { override ->
                iconPickerKey = null
                actions.setIconOverride(entry, override)
            },
            onDismiss = { iconPickerKey = null },
        )
    }

    if (weatherDialog) {
        WeatherPlaceDialog(
            onPlace = { place ->
                weatherDialog = false
                actions.setWeatherPlace(place)
            },
            onDismiss = { weatherDialog = false },
        )
    }

    if (createFolder) {
        CreateFolderDialog(
            onCreate = { name, inFavourites ->
                createFolder = false
                pendingFolderEdit = actions.createFolder(name, inFavourites)
            },
            onDismiss = { createFolder = false },
        )
    }
    // Opens once the new folder has reached the state.
    LaunchedEffect(pendingFolderEdit, state) {
        val key = pendingFolderEdit ?: return@LaunchedEffect
        if (state.find(key) != null) {
            pendingFolderEdit = null
            popupAnchor = Offset.Unspecified
            popupEditing = true
            popupOwner = key
            pickerOwner = key
        }
    }

    sheetKey?.let(state::find)?.takeIf { it.isFolder }?.let { entry ->
        FolderSheet(
            entry = entry,
            onEdit = {
                sheetKey = null
                popupAnchor = Offset.Unspecified
                popupEditing = true
                popupOwner = entry.key
            },
            onRename = {
                sheetKey = null
                renameKey = entry.key
            },
            onDelete = {
                sheetKey = null
                actions.deleteFolder(entry)
            },
            onDismiss = { sheetKey = null },
        )
    }

    sheetKey?.let(state::find)?.takeUnless { it.isFolder }?.let { entry ->
        AppActionsSheet(
            entry = entry,
            isFavourite = state.isFavourite(entry),
            hasPopup = state.hasPopup(entry),
            notifications = notifications[entry.notificationKey].orEmpty(),
            onOpenNotification = { n ->
                sheetKey = null
                actions.openNotification(n)
            },
            onDismissNotification = actions.dismissNotification,
            onEditPopup = {
                sheetKey = null
                searching = false
                popupAnchor = Offset.Unspecified
                popupOwner = entry.key
                popupEditing = true
            },
            onAddToPopup = {
                sheetKey = null
                addToPopupKey = entry.key
            },
            onDismiss = { sheetKey = null },
            onShortcut = { shortcut, bounds ->
                sheetKey = null
                searching = false
                actions.launchShortcut(shortcut, bounds)
            },
            onNeedHomeRole = {
                sheetKey = null
                actions.openHomeSettings()
            },
            onAppInfo = { bounds ->
                sheetKey = null
                actions.appInfo(entry, bounds)
            },
            onUninstall = {
                sheetKey = null
                actions.uninstall(entry)
            },
            onToggleFavourite = {
                actions.toggleFavourite(entry)
                sheetKey = null
            },
            onReorderFavourites = {
                sheetKey = null
                editing = true
                scope.launch { listState.scrollToItem(0) }
            },
            onRename = {
                sheetKey = null
                renameKey = entry.key
            },
            onChangeIcon = {
                sheetKey = null
                iconPickerKey = entry.key
            },
            onBreaker = {
                sheetKey = null
                breakerSheet = listOf(entry.packageName, entry.label)
            },
            onHide = {
                actions.hide(entry)
                sheetKey = null
            },
        )
    }
    renameKey?.let(state::find)?.let { entry ->
        RenameDialog(
            entry = entry,
            onDismiss = { renameKey = null },
            onRename = { label ->
                actions.rename(entry, label)
                renameKey = null
            },
        )
    }
}

private fun LazyListScope.sectionLabel(key: String, textRes: Int, modifier: Modifier = Modifier) {
    item(key = key, contentType = "section-label") {
        Text(
            text = stringResource(textRes).uppercase(),
            style = SlateText.sectionLabel,
            color = LocalSlatePalette.current.onWallpaperMuted,
            modifier = modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 8.dp).semantics { heading() },
        )
    }
}

/**
 * Lock, unlock and settings for the private space. Full rows like the apps around them, so they
 * stay clear of the alphabet strip on the right edge.
 */
@Composable
private fun PrivateSpaceRow(icon: SlateIcon, labelRes: Int, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalSlatePalette.current
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 28.dp, end = 56.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(palette.onWallpaper.copy(alpha = 0.26f), CircleShape), contentAlignment = Alignment.Center) {
            SlateIconView(icon, palette.onWallpaper, size = 24.dp)
        }
        Spacer(Modifier.width(20.dp))
        Text(stringResource(labelRes), style = SlateText.appLabel, color = palette.onWallpaper, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Rows fade out as they reach the system bars so they never collide with the status icons. The
 * fade is applied to the rows themselves, not painted over them: nothing darkens the wallpaper.
 */
private fun Modifier.fadeAtEdges(top: Dp, bottom: Dp): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val h = size.height
        val topEnd = top.toPx()
        val bottomStart = h - bottom.toPx()
        val taper = EdgeFadeTaper.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                topEnd / h to Color.Transparent,
                (topEnd + taper) / h to Color.Black,
                (bottomStart - taper) / h to Color.Black,
                bottomStart / h to Color.Transparent,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private val EdgeFadeTaper = 32.dp
private const val HUB_VEIL = 0.63f
private val PopupDim = Color(0x73000000)
private const val MOVE_STEP = 0.05f

/**
 * Move mode: any vertical drag slides the clock, widgets and favourites, seen live underneath.
 * The offset is a share of the screen height, so it survives rotating or unfolding.
 */
@Composable
private fun MoveHomeOverlay(offset: () -> Float, onOffset: (Float) -> Unit, onReset: () -> Unit, onDone: () -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val palette = LocalSlatePalette.current
    val view = LocalView.current
    val height = LocalWindowInfo.current.containerSize.height.toFloat()
    val currentOffset by rememberUpdatedState(offset)
    val currentOnOffset by rememberUpdatedState(onOffset)
    val max = BehaviourSettings.MAX_HOME_OFFSET
    val up = stringResource(R.string.move_up)
    val down = stringResource(R.string.move_down)
    fun moveTo(next: Float) {
        val clamped = next.coerceIn(0f, max)
        // One tick on reaching either end.
        if (clamped != currentOffset() && (clamped == 0f || clamped == max)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        currentOnOffset(clamped)
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(height) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    moveTo(currentOffset() + dragAmount / height)
                }
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(up) { moveTo(currentOffset() - MOVE_STEP); true },
                    CustomAccessibilityAction(down) { moveTo(currentOffset() + MOVE_STEP); true },
                )
            },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.panel)
                .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        ) {
            Text(stringResource(R.string.move_home_hint), style = SlateText.hint.copy(shadow = null), color = palette.onWallpaper)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.move_home_reset), color = palette.onWallpaperMuted) }
                TextButton(onClick = onDone) { Text(stringResource(R.string.done), color = palette.accent) }
            }
        }
    }
}

@Composable
private fun EditHeader(onDone: () -> Unit) {
    val palette = LocalSlatePalette.current
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 48.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.favourites),
            style = SlateText.date.copy(fontSize = SlateText.clock.fontSize * 0.5f),
            color = palette.onWallpaper,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDone) { Text(stringResource(R.string.done), color = palette.accent) }
    }
}

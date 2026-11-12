package dev.slate.launcher.ui.settings

import androidx.compose.runtime.produceState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import dev.slate.launcher.ui.theme.Accent
import dev.slate.launcher.floating.AudioPrompt
import dev.slate.launcher.floating.FloatingSources
import dev.slate.launcher.ui.components.backProgress
import dev.slate.launcher.ui.components.rememberBackProgress
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.slate.launcher.BuildConfig
import dev.slate.launcher.R
import dev.slate.launcher.container
import dev.slate.launcher.data.Backup
import dev.slate.launcher.data.BackupFile
import dev.slate.launcher.data.BehaviourSettings
import dev.slate.launcher.data.ClockSettings
import dev.slate.launcher.data.Presets
import dev.slate.launcher.data.RowSwipeAction
import dev.slate.launcher.data.ThemeSettings
import dev.slate.launcher.notifications.NotificationAccess
import dev.slate.launcher.ui.appearance.ChoiceRow
import dev.slate.launcher.ui.appearance.Group
import dev.slate.launcher.ui.appearance.LinkRow
import dev.slate.launcher.ui.appearance.Note
import dev.slate.launcher.ui.appearance.PanelBackdrop
import dev.slate.launcher.ui.appearance.StatusRow
import dev.slate.launcher.ui.appearance.ToggleRow
import dev.slate.launcher.ui.appearance.clockStyleLabel
import dev.slate.launcher.ui.appearance.presetLabel
import dev.slate.launcher.ui.appearance.section
import dev.slate.launcher.ui.components.SlateIcon
import dev.slate.launcher.ui.components.SlateIconView
import dev.slate.launcher.ui.onboarding.DefaultHome
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import dev.slate.launcher.ui.widgets.openAppSettings
import java.time.LocalDate

private enum class Page { Root, Home, Popups, Gestures, Pill, Permissions, Backup, About }

/**
 * A short hub of topics, each with its current state, opening a page of its own. How things look
 * lives in Appearance and Clock, which the hub links to.
 */
@Composable
fun SettingsScreen(
    theme: ThemeSettings,
    clock: ClockSettings,
    behaviour: BehaviourSettings,
    hiddenCount: Int,
    hasPrivateSpace: Boolean,
    floating: FloatingSources,
    notificationAccess: Boolean,
    onFloating: (FloatingSources) -> Unit,
    onBehaviour: (BehaviourSettings) -> Unit,
    onAppearance: () -> Unit,
    onClock: () -> Unit,
    onScreenTime: () -> Unit,
    onMoveHome: () -> Unit,
    onEditHome: () -> Unit,
    onClose: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    var page by rememberSaveable { mutableStateOf(Page.Root) }
    val back = { if (page == Page.Root) onClose() else page = Page.Root }
    val backModifier = Modifier.backProgress(rememberBackProgress(onBack = back))
    val context = LocalContext.current
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    // Re-read permission states when coming back from system settings.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val permissions = remember(refresh) { permissionRows(context) }
    val musicApps by produceState(emptyList<Pair<String, String>>(), refresh) { value = context.container.audioRoutes.musicApps() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME)) { uri ->
        uri?.let(viewModel::export)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::read)
    }

    PanelBackdrop(solid = true, modifier = backModifier) {
        AnimatedContent(
            targetState = page,
            modifier = Modifier.weight(1f),
            label = "settingsPage",
            transitionSpec = {
                // Pages come in from the side they lead to and go back the way they came.
                val forward = targetState != Page.Root
                val direction = if (forward) 1 else -1
                val spec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 500f)
                (slideInHorizontally(spec) { direction * it / 5 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(spec) { -direction * it / 5 } + fadeOut(tween(120)))
            },
        ) { shown ->
            SettingsPage(
                title = stringResource(shown.title()),
                subtitle = shown.subtitle()?.let { stringResource(it) },
                showMark = shown == Page.Root,
                backLabel = stringResource(if (shown == Page.Root) R.string.done else R.string.settings_back),
                onBack = back,
            ) {
                    when (shown) {
                        Page.Root -> item(key = "root") {
                            Column {
                                Group(stringResource(R.string.settings_group_look)) {
                                    LinkRow(stringResource(R.string.appearance), summary = presetLabelOrCustom(theme.preset), icon = SlateIcon.Droplet, tint = hubTint(0), onClick = onAppearance)
                                    LinkRow(stringResource(R.string.clock_title), summary = clockStyleLabel(clock.style), icon = SlateIcon.Clock, tint = hubTint(1), onClick = onClock)
                                }
                                Group(stringResource(R.string.settings_group_use)) {
                                    LinkRow(stringResource(R.string.settings_home_screen), summary = stringResource(R.string.settings_home_summary), icon = SlateIcon.Home, tint = hubTint(2)) { page = Page.Home }
                                    LinkRow(stringResource(R.string.settings_popups), summary = stringResource(R.string.settings_popups_summary), icon = SlateIcon.Message, tint = hubTint(3)) { page = Page.Popups }
                                    LinkRow(stringResource(R.string.floating_title), summary = stringResource(R.string.settings_pill_summary), icon = SlateIcon.Note, tint = hubTint(4)) { page = Page.Pill }
                                    LinkRow(
                                        stringResource(R.string.settings_gestures),
                                        summary = stringResource(R.string.settings_gestures_summary, swipeLabel(behaviour.rowSwipeRight)),
                                        icon = SlateIcon.SwipeRight,
                                        tint = hubTint(5),
                                    ) { page = Page.Gestures }
                                    LinkRow(stringResource(R.string.screen_time), summary = stringResource(R.string.settings_screen_time_summary), icon = SlateIcon.Hourglass, tint = hubTint(6), onClick = onScreenTime)
                                }
                                Group(stringResource(R.string.settings_group_system)) {
                                    LinkRow(
                                        stringResource(R.string.settings_permissions),
                                        summary = stringResource(R.string.settings_permissions_summary, permissions.count { it.granted }, permissions.size),
                                        icon = SlateIcon.ShieldCheck,
                                        tint = hubTint(7),
                                    ) { page = Page.Permissions }
                                    LinkRow(stringResource(R.string.settings_backup), summary = stringResource(R.string.settings_backup_summary), icon = SlateIcon.Save, tint = hubTint(8)) { page = Page.Backup }
                                    LinkRow(stringResource(R.string.settings_about), summary = BuildConfig.VERSION_NAME, icon = SlateIcon.Info, tint = hubTint(9)) { page = Page.About }
                                }
                            }
                        }
                        Page.Home -> {
                            section("layout", R.string.settings_home_layout) {
                                LinkRow(
                                    stringResource(R.string.move_home),
                                    summary = stringResource(R.string.settings_move_summary),
                                    trailing = "${Math.round(behaviour.homeOffset * 100)}%",
                                    icon = SlateIcon.Move,
                                    onClick = onMoveHome,
                                )
                                ToggleRow(stringResource(R.string.settings_scrubber), behaviour.showScrubber, stringResource(R.string.settings_scrubber_summary), SlateIcon.Alphabet) { onBehaviour(behaviour.copy(showScrubber = it)) }
                                // Without the pull gesture this button is the way into search, so it stays.
                                if (behaviour.pullDownToSearch) {
                                    ToggleRow(stringResource(R.string.settings_search_button), behaviour.showSearchButton, stringResource(R.string.settings_search_button_summary), SlateIcon.Search) { onBehaviour(behaviour.copy(showSearchButton = it)) }
                                }
                                ChoiceRow(stringResource(R.string.settings_all_apps_close), listOf(0, 3, 5, 10, 30), behaviour.allAppsCloseSeconds, { secondsLabel(it) }, icon = SlateIcon.Clock, summary = stringResource(R.string.settings_all_apps_close_summary)) { onBehaviour(behaviour.copy(allAppsCloseSeconds = it)) }
                                ToggleRow(stringResource(R.string.settings_hide_status_bar), behaviour.hideStatusBar, stringResource(R.string.settings_hide_status_bar_summary), SlateIcon.Eye) { onBehaviour(behaviour.copy(hideStatusBar = it)) }
                            }
                            section("apps", R.string.settings_home_apps) {
                                ToggleRow(stringResource(R.string.settings_search_hidden), behaviour.searchHiddenApps, stringResource(R.string.settings_search_hidden_summary), SlateIcon.Eye) { onBehaviour(behaviour.copy(searchHiddenApps = it)) }
                                if (hasPrivateSpace) {
                                    ToggleRow(stringResource(R.string.settings_show_private), behaviour.showPrivateSpace, stringResource(R.string.settings_show_private_summary), SlateIcon.Shield) { onBehaviour(behaviour.copy(showPrivateSpace = it)) }
                                }
                                LinkRow(
                                    if (hiddenCount > 0) {
                                        pluralStringResource(R.plurals.settings_edit_home_hidden, hiddenCount, hiddenCount)
                                    } else {
                                        stringResource(R.string.settings_edit_home)
                                    },
                                    icon = SlateIcon.Reorder,
                                    onClick = onEditHome,
                                )
                            }
                        }
                        Page.Gestures -> {
                            section("swipes", R.string.settings_gestures_swipes) {
                                SwipeChoice(stringResource(R.string.settings_swipe_right), SlateIcon.SwipeRight, behaviour.rowSwipeRight) { onBehaviour(behaviour.copy(rowSwipeRight = it)) }
                                SwipeChoice(stringResource(R.string.settings_swipe_left), SlateIcon.SwipeLeft, behaviour.rowSwipeLeft) { onBehaviour(behaviour.copy(rowSwipeLeft = it)) }
                            }
                            section("pull", R.string.settings_gestures_home) {
                                ToggleRow(stringResource(R.string.settings_pull_search), behaviour.pullDownToSearch, stringResource(R.string.settings_pull_search_summary), SlateIcon.PullDown) { onBehaviour(behaviour.copy(pullDownToSearch = it)) }
                                ToggleRow(stringResource(R.string.settings_haptics), behaviour.haptics, stringResource(R.string.settings_haptics_summary), SlateIcon.Diamond) { onBehaviour(behaviour.copy(haptics = it)) }
                            }
                        }
                        Page.Popups -> {
                            section("popup-content", R.string.settings_popups_content) {
                                ChoiceRow(stringResource(R.string.settings_popup_shortcuts), listOf(0, 2, 4, 6, 8), behaviour.popupShortcuts, { it.toString() }, icon = SlateIcon.Reorder, summary = stringResource(R.string.settings_popup_shortcuts_summary)) { onBehaviour(behaviour.copy(popupShortcuts = it)) }
                                ToggleRow(stringResource(R.string.settings_popup_expand), behaviour.popupExpandNotification, stringResource(R.string.settings_popup_expand_summary), SlateIcon.Bell) { onBehaviour(behaviour.copy(popupExpandNotification = it)) }
                                ToggleRow(stringResource(R.string.settings_notification_previews), behaviour.notificationPreviews, stringResource(R.string.settings_notification_previews_summary), SlateIcon.Message) { onBehaviour(behaviour.copy(notificationPreviews = it)) }
                            }
                            section("popup-look", R.string.settings_popups_look) {
                                ToggleRow(stringResource(R.string.settings_popup_dim), behaviour.popupDim, stringResource(R.string.settings_popup_dim_summary), SlateIcon.Eye) { onBehaviour(behaviour.copy(popupDim = it)) }
                                LinkRow(stringResource(R.string.appearance_panel), summary = stringResource(R.string.settings_popup_colour_summary), icon = SlateIcon.Droplet, onClick = onAppearance)
                            }
                        }
                        Page.Pill -> {
                            section("pill-shows", R.string.settings_pill_shows) {
                                ToggleRow(stringResource(R.string.floating_media), floating.media, icon = SlateIcon.Note) { onFloating(floating.copy(media = it)) }
                                ToggleRow(stringResource(R.string.floating_torch), floating.torch, stringResource(R.string.floating_torch_summary), SlateIcon.Torch) { onFloating(floating.copy(torch = it)) }
                                ToggleRow(stringResource(R.string.floating_timers), floating.timers, icon = SlateIcon.Hourglass) { onFloating(floating.copy(timers = it)) }
                                ToggleRow(stringResource(R.string.floating_alarm), floating.alarm, icon = SlateIcon.Bell) { onFloating(floating.copy(alarm = it)) }
                                if ((floating.media || floating.timers) && !notificationAccess) Note(stringResource(R.string.floating_media_needs_access))
                            }
                            section("pill-offer", R.string.settings_pill_offer) {
                                ChoiceRow(stringResource(R.string.settings_pill_offer_when), AudioPrompt.entries, floating.audioPrompt, { audioPromptLabel(it) }, icon = SlateIcon.Network, summary = stringResource(R.string.settings_pill_offer_when_summary)) { onFloating(floating.copy(audioPrompt = it)) }
                                if (floating.audioPrompt != AudioPrompt.Off) {
                                    ChoiceRow(
                                        stringResource(R.string.settings_pill_offer_style),
                                        listOf(true, false),
                                        floating.audioPromptPlayer,
                                        { stringResource(if (it) R.string.settings_pill_offer_player else R.string.settings_pill_offer_shortcut) },
                                        icon = SlateIcon.Send,
                                        summary = stringResource(R.string.settings_pill_offer_style_summary),
                                    ) { onFloating(floating.copy(audioPromptPlayer = it)) }
                                    val lastPlayed = stringResource(R.string.settings_pill_offer_last)
                                    ChoiceRow(
                                        stringResource(R.string.settings_pill_offer_app),
                                        listOf<String?>(null) + musicApps.map { it.first },
                                        floating.musicApp,
                                        { pkg -> musicApps.firstOrNull { it.first == pkg }?.second ?: pkg ?: lastPlayed },
                                        icon = SlateIcon.Note,
                                    ) { onFloating(floating.copy(musicApp = it)) }
                                }
                            }
                            section("pill-look", R.string.settings_pill_look) {
                                ToggleRow(stringResource(R.string.floating_two), floating.two, stringResource(R.string.floating_two_summary), SlateIcon.Reorder) { onFloating(floating.copy(two = it)) }
                                ToggleRow(stringResource(R.string.settings_pill_tap_opens), floating.tapOpensApp, stringResource(R.string.settings_pill_tap_opens_summary), SlateIcon.OpenExternal) { onFloating(floating.copy(tapOpensApp = it)) }
                                ToggleRow(stringResource(R.string.floating_wavy), floating.wavyProgress, stringResource(R.string.floating_wavy_summary), SlateIcon.Droplet) { onFloating(floating.copy(wavyProgress = it)) }
                                ToggleRow(stringResource(R.string.settings_pill_equalizer), floating.equalizer, stringResource(R.string.settings_pill_equalizer_summary), SlateIcon.Alphabet) { onFloating(floating.copy(equalizer = it)) }
                            }
                            section("pill-timing", R.string.settings_pill_timing) {
                                ChoiceRow(stringResource(R.string.settings_pill_torch_linger), listOf(3, 5, 10, 30), floating.torchLingerSeconds, { secondsLabel(it) }, icon = SlateIcon.Torch, summary = stringResource(R.string.settings_pill_torch_linger_summary)) { onFloating(floating.copy(torchLingerSeconds = it)) }
                                ChoiceRow(stringResource(R.string.settings_pill_alarm_window), listOf(1, 6, 12, 24), floating.alarmWindowHours, { pluralStringResource(R.plurals.hours, it, it) }, icon = SlateIcon.Bell, summary = stringResource(R.string.settings_pill_alarm_window_summary)) { onFloating(floating.copy(alarmWindowHours = it)) }
                            }
                        }
                        Page.Permissions -> item(key = "permissions") {
                            Group {
                                permissions.forEach { row ->
                                    StatusRow(
                                        stringResource(row.label),
                                        stringResource(row.summary),
                                        row.granted,
                                        stringResource(R.string.settings_perm_on),
                                        stringResource(R.string.settings_perm_off),
                                        row.icon,
                                    ) { row.open(context) }
                                }
                            }
                        }
                        Page.Backup -> item(key = "backup") {
                            Group {
                                LinkRow(stringResource(R.string.settings_backup_save), summary = stringResource(R.string.settings_backup_save_summary), icon = SlateIcon.Save) { exporter.launch("slate-backup-${LocalDate.now()}.json") }
                                LinkRow(stringResource(R.string.settings_backup_restore), summary = stringResource(R.string.settings_backup_restore_summary), icon = SlateIcon.Restore) { importer.launch(arrayOf(Backup.MIME, "text/plain", "application/octet-stream")) }
                            }
                        }
                        Page.About -> item(key = "about") {
                            Group {
                                LinkRow(stringResource(R.string.settings_about_version), trailing = BuildConfig.VERSION_NAME, icon = SlateIcon.Info) {}
                                Note(stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME))
                            }
                        }
                    }
            }
        }
    }

    BackupDialog(backup, onConfirm = viewModel::confirmRestore, onDismiss = viewModel::dismiss)
}

/** A large title that scrolls away with the page and reappears small beside the back button. */
@Composable
private fun SettingsPage(title: String, subtitle: String?, showMark: Boolean, backLabel: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val palette = LocalSlatePalette.current
    val list = rememberLazyListState()
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > TitleCollapseAt } }
    val toolbarTitle by animateFloatAsState(if (collapsed) 1f else 0f, tween(150), label = "toolbarTitle")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.groundRaised)
                    .clickable(role = Role.Button, onClickLabel = backLabel, onClick = onBack)
                    .semantics { contentDescription = backLabel },
                contentAlignment = Alignment.Center,
            ) { SlateIconView(SlateIcon.SwipeLeft, palette.onWallpaper, size = 22.dp) }
            Text(
                title,
                style = SlateText.appLabel.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, shadow = null),
                color = palette.onWallpaper,
                modifier = Modifier.padding(start = 16.dp).graphicsLayer { alpha = toolbarTitle }.clearAndSetSemantics {},
            )
        }
        LazyColumn(Modifier.weight(1f), state = list) {
            item(key = "title") {
                Column(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 4.dp)) {
                    if (showMark) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(72.dp).offset(x = (-14).dp))
                    }
                    Text(
                        title,
                        style = SlateText.clock.copy(fontSize = 40.sp, fontWeight = FontWeight.Medium, shadow = null),
                        color = palette.onWallpaper,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = SlateText.hint.copy(fontSize = 15.sp, shadow = null),
                            color = palette.onWallpaperMuted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            content()
            item(key = "bottom") { Spacer(Modifier.height(48.dp)) }
        }
    }
}

/** A colour of its own for each topic on the hub, spread round the wheel from the accent. */
@Composable
private fun hubTint(index: Int): Color {
    val accent = LocalSlatePalette.current.accent
    return remember(accent, index) { Color(Accent.custom(Accent.toHue(accent.toArgb()) + index * 47f)) }
}

@Composable
private fun secondsLabel(seconds: Int): String =
    if (seconds == 0) stringResource(R.string.settings_never) else pluralStringResource(R.plurals.seconds, seconds, seconds)

@Composable
private fun audioPromptLabel(prompt: AudioPrompt): String = stringResource(
    when (prompt) {
        AudioPrompt.Off -> R.string.torch_off
        AudioPrompt.Bluetooth -> R.string.settings_pill_offer_bluetooth
        AudioPrompt.Headset -> R.string.settings_pill_offer_headset
    },
)

private const val TitleCollapseAt = 60

private fun Page.title() = when (this) {
    Page.Root -> R.string.settings
    Page.Home -> R.string.settings_home_screen
    Page.Gestures -> R.string.settings_gestures
    Page.Popups -> R.string.settings_popups
    Page.Pill -> R.string.floating_title
    Page.Permissions -> R.string.settings_permissions
    Page.Backup -> R.string.settings_backup
    Page.About -> R.string.settings_about
}

private fun Page.subtitle() = when (this) {
    Page.Root, Page.About -> null
    Page.Home -> R.string.settings_home_subtitle
    Page.Gestures -> R.string.settings_gestures_subtitle
    Page.Popups -> R.string.settings_popups_subtitle
    Page.Pill -> R.string.floating_note
    Page.Permissions -> R.string.settings_permissions_note
    Page.Backup -> R.string.settings_backup_note
}

@Composable
private fun presetLabelOrCustom(preset: String): String =
    if (preset == Presets.CUSTOM) stringResource(R.string.settings_look_custom) else presetLabel(preset)

@Composable
private fun SwipeChoice(title: String, icon: SlateIcon, selected: RowSwipeAction, onSelect: (RowSwipeAction) -> Unit) {
    ChoiceRow(title, RowSwipeAction.entries, selected, { swipeLabel(it) }, icon = icon, onSelect = onSelect)
}

@Composable
private fun swipeLabel(action: RowSwipeAction): String = stringResource(
    when (action) {
        RowSwipeAction.Popup -> R.string.popup_open
        RowSwipeAction.AppInfo -> R.string.app_info
        RowSwipeAction.Favourite -> R.string.favourites
        RowSwipeAction.Hide -> R.string.hide
        RowSwipeAction.None -> R.string.settings_swipe_nothing
    },
)

private class PermissionRow(val label: Int, val summary: Int, val icon: SlateIcon, val granted: Boolean, val open: (Context) -> Unit)

/** Everything Slate can ask for and where to change it. All optional except the home role's perks. */
private fun permissionRows(context: Context): List<PermissionRow> {
    val c = context.container
    fun has(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    return buildList {
        add(
            PermissionRow(R.string.settings_perm_home, R.string.settings_perm_home_summary, SlateIcon.Home, DefaultHome.isHeld(context)) { ctx ->
                runCatching { ctx.startActivity(DefaultHome.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
        )
        add(PermissionRow(R.string.settings_perm_notifications, R.string.settings_perm_notifications_summary, SlateIcon.Bell, NotificationAccess.isGranted(context), NotificationAccess::open))
        add(PermissionRow(R.string.settings_perm_usage, R.string.settings_perm_usage_summary, SlateIcon.Hourglass, c.usageRepository.hasAccess()) { c.usageRepository.openSettings() })
        add(PermissionRow(R.string.settings_perm_calendar, R.string.settings_perm_calendar_summary, SlateIcon.Calendar, c.calendarSource.hasPermission(), ::openAppSettings))
        add(PermissionRow(R.string.settings_perm_contacts, R.string.settings_perm_contacts_summary, SlateIcon.Contacts, c.contactsSource.hasPermission(), ::openAppSettings))
        add(PermissionRow(R.string.settings_perm_location, R.string.settings_perm_location_summary, SlateIcon.Pin, c.weatherRepository.hasLocationPermission(), ::openAppSettings))
        add(PermissionRow(R.string.settings_perm_network, R.string.settings_perm_network_summary, SlateIcon.Network, c.weatherRepository.hasNetworkPermission(), ::openAppSettings))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(PermissionRow(R.string.settings_perm_post, R.string.settings_perm_post_summary, SlateIcon.Bell, has(Manifest.permission.POST_NOTIFICATIONS), ::openAppSettings))
        }
    }
}

@Composable
private fun BackupDialog(
    state: SettingsViewModel.BackupState,
    onConfirm: (BackupFile) -> Unit,
    onDismiss: () -> Unit,
) {
    val body = when (state) {
        SettingsViewModel.BackupState.Idle -> return
        is SettingsViewModel.BackupState.Confirm -> stringResource(R.string.settings_restore_confirm)
        SettingsViewModel.BackupState.Saved -> stringResource(R.string.settings_backup_saved)
        SettingsViewModel.BackupState.SaveFailed -> stringResource(R.string.settings_backup_failed)
        SettingsViewModel.BackupState.NotABackup -> stringResource(R.string.settings_backup_invalid)
        SettingsViewModel.BackupState.TooNew -> stringResource(R.string.settings_backup_too_new)
        is SettingsViewModel.BackupState.Restored -> if (state.appWidgetsLeftOut > 0) {
            pluralStringResource(R.plurals.settings_restored_widgets, state.appWidgetsLeftOut, state.appWidgetsLeftOut)
        } else {
            stringResource(R.string.settings_restored)
        }
    }
    val confirm = state as? SettingsViewModel.BackupState.Confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(body) },
        confirmButton = {
            if (confirm != null) {
                TextButton(onClick = { onConfirm(confirm.file) }) { Text(stringResource(R.string.settings_restore_action), color = MaterialTheme.colorScheme.primary) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done), color = MaterialTheme.colorScheme.primary) }
            }
        },
        dismissButton = {
            if (confirm != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface) }
        },
    )
}

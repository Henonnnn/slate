package dev.slate.launcher.ui.home

import dev.slate.launcher.data.SavedTheme
import android.graphics.Rect
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.slate.launcher.apps.AlphabetIndex
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.apps.AppRepository
import dev.slate.launcher.apps.AppShortcut
import dev.slate.launcher.apps.PrivateSpace
import dev.slate.launcher.apps.ShortcutRepository
import dev.slate.launcher.apps.HomeListBuilder
import dev.slate.launcher.container
import dev.slate.launcher.data.LauncherSettings
import dev.slate.launcher.data.BehaviourSettings
import dev.slate.launcher.data.ClockSettings
import dev.slate.launcher.data.Folders
import dev.slate.launcher.data.PopupFolder
import dev.slate.launcher.data.IconOverride
import dev.slate.launcher.floating.FloatingSources
import dev.slate.launcher.data.PopupItem
import dev.slate.launcher.data.Presets
import dev.slate.launcher.data.ThemeSettings
import dev.slate.launcher.data.Popups
import dev.slate.launcher.data.WeatherPlace
import dev.slate.launcher.data.WidgetRef
import dev.slate.launcher.data.WidgetStack
import dev.slate.launcher.data.Widgets
import dev.slate.launcher.notifications.AppNotification
import dev.slate.launcher.notifications.NotificationStore
import dev.slate.launcher.wellbeing.Breaker
import dev.slate.launcher.wellbeing.BreakerDecision
import dev.slate.launcher.wellbeing.BreakerReminder
import dev.slate.launcher.wellbeing.BreakerRule
import dev.slate.launcher.wellbeing.UsageRepository
import dev.slate.launcher.widgets.WidgetRepository
import dev.slate.launcher.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class HomeUiState(
    val loading: Boolean = true,
    val favourites: List<AppEntry> = emptyList(),
    /** Every visible app, alphabetical. */
    val apps: List<AppEntry> = emptyList(),
    val hidden: List<AppEntry> = emptyList(),
    /** The private space's apps; empty while it's locked. */
    val privateApps: List<AppEntry> = emptyList(),
    val privateSpace: PrivateSpace? = null,
    /** Pop-up folders at the top of the app list. */
    val folders: List<AppEntry> = emptyList(),
    val popupWidgets: Map<String, List<WidgetStack>> = emptyMap(),
    /** Bucket letter → index into [apps]. */
    val letterIndex: Map<Char, Int> = emptyMap(),
    val showDefaultHomePrompt: Boolean = false,
    val showNotificationPrompt: Boolean = false,
    val notificationAccess: Boolean = false,
    /** Owner app key → items, as stored; resolve with [find] and the shortcut repository. */
    val popups: Map<String, List<PopupItem>> = emptyMap(),
    val widgets: List<WidgetStack> = emptyList(),
    val weatherPlace: WeatherPlace? = null,
    val theme: ThemeSettings = ThemeSettings(),
    val savedThemes: List<SavedTheme> = emptyList(),
    val iconOverrides: Map<String, IconOverride> = emptyMap(),
    val floating: FloatingSources = FloatingSources(),
    val breaker: Map<String, BreakerRule> = emptyMap(),
    val behaviour: BehaviourSettings = BehaviourSettings(),
    val clock: ClockSettings = ClockSettings(),
) {
    private val favouriteKeys: Set<String> = favourites.mapTo(HashSet()) { it.key }

    fun isFavourite(entry: AppEntry) = entry.key in favouriteKeys

    private val byKey: Map<String, AppEntry> = (apps + hidden + privateApps + folders + favourites).associateBy { it.key }

    fun find(key: String): AppEntry? = byKey[key]

    fun hasPopup(entry: AppEntry) = !popups[entry.key].isNullOrEmpty()
}

/** What a breaker launch was for, so it can be re-launched after a grant. */
sealed interface BreakerTarget {
    data class App(val entry: AppEntry) : BreakerTarget
    data class Shortcut(val shortcut: AppShortcut) : BreakerTarget
}

data class BreakerPrompt(val packageName: String, val label: String, val target: BreakerTarget, val pause: BreakerDecision.Pause)

class HomeViewModel(
    private val appRepository: AppRepository,
    private val shortcutRepository: ShortcutRepository,
    private val settingsRepository: SettingsRepository,
    private val widgetRepository: WidgetRepository,
    private val notificationStore: NotificationStore,
    private val usageRepository: UsageRepository,
) : ViewModel() {

    /** Usage Breaker grants: package → time the granted minutes end. Memory only, on purpose. */
    private val grants = HashMap<String, Long>()
    private val _breakerPrompt = MutableStateFlow<BreakerPrompt?>(null)
    val breakerPrompt: StateFlow<BreakerPrompt?> = _breakerPrompt

    /** App notification key → its notifications, newest first. Empty while access is off. */
    val notifications: StateFlow<Map<String, List<AppNotification>>> = notificationStore.byApp

    private val notificationAccess = MutableStateFlow(true)

    private val isDefaultHome = MutableStateFlow(true)

    // Eager: a launcher's list should stay hot while the user is in other apps.
    val state: StateFlow<HomeUiState> = combine(
        appRepository.apps,
        settingsRepository.settings,
        isDefaultHome,
        notificationAccess,
        appRepository.privateSpace,
    ) { apps, settings, isDefault, notificationsOn, privateSpace ->
        val lists = HomeListBuilder.build(
            installed = apps,
            favourites = settings.favourites,
            hidden = settings.hidden,
            labelOverrides = settings.labelOverrides,
            collator = AlphabetIndex.collatorFor(appRepository.currentLocale()),
            folders = settings.folders.map { AppEntry.folder(Folders.key(it.id), it.name) },
        )
        HomeUiState(
            loading = false,
            favourites = lists.favourites,
            folders = lists.folders,
            apps = lists.apps,
            hidden = lists.hidden,
            privateApps = lists.privateApps,
            privateSpace = privateSpace,
            letterIndex = lists.letterIndex,
            showDefaultHomePrompt = !isDefault && !settings.defaultHomePromptDismissed,
            // One card at a time: home role first, it's what makes the rest work.
            showNotificationPrompt = (isDefault || settings.defaultHomePromptDismissed) &&
                !notificationsOn && !settings.notificationPromptDismissed,
            notificationAccess = notificationsOn,
            popups = settings.popups,
            popupWidgets = settings.popupWidgets,
            widgets = settings.widgets,
            weatherPlace = settings.weatherPlace,
            theme = settings.theme,
            savedThemes = settings.savedThemes,
            iconOverrides = settings.iconOverrides,
            floating = settings.floating,
            breaker = settings.breaker,
            behaviour = settings.behaviour,
            clock = settings.clock,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        // Widget ids the settings don't mention (a cancelled add, a crash mid-bind) are dropped once.
        viewModelScope.launch {
            val ids = settingsRepository.settings.first().usedWidgetIds()
            withContext(Dispatchers.IO) { widgetRepository.deleteOrphans(ids) }
        }
        // Keep the platform's pinned set in step with pop-ups and accepted pin requests. Re-run when
        // Slate becomes the home app, since only then is pinning allowed.
        viewModelScope.launch {
            combine(settingsRepository.settings.map { it.wantedPins() }, isDefaultHome) { pins, isDefault -> pins to isDefault }
                .distinctUntilChanged()
                .collect { (pins, isDefault) -> if (isDefault) shortcutRepository.syncPins(pins) }
        }
    }

    /** Makes a folder and returns its key, so the caller can open it. */
    fun createFolder(name: String, inFavourites: Boolean): String {
        val folder = PopupFolder(UUID.randomUUID().toString(), name)
        update { Folders.create(it, folder, inFavourites) }
        return Folders.key(folder.id)
    }

    fun deleteFolder(entry: AppEntry) {
        state.value.popupWidgets[entry.key].orEmpty().let(Widgets::appWidgetIds).forEach(widgetRepository::delete)
        update { Folders.delete(it, entry.key.removePrefix(Folders.KEY_PREFIX)) }
    }

    fun addPopupBuiltinWidget(owner: String, ref: WidgetRef) = update { s ->
        val height = if (ref.kind == WidgetRef.Kind.Calendar) 170 else 120
        s.copy(popupWidgets = Widgets.inPopup(s.popupWidgets, owner) { Widgets.add(it, ref, height) })
    }

    fun removePopupWidget(owner: String, ref: WidgetRef) {
        update { s -> s.copy(popupWidgets = Widgets.inPopup(s.popupWidgets, owner) { Widgets.remove(it, ref) }) }
        if (ref.kind == WidgetRef.Kind.App) widgetRepository.delete(ref.appWidgetId)
    }

    fun setPopupWidgetHeight(owner: String, ref: WidgetRef, heightDp: Int) = update { s ->
        val height = heightDp.coerceIn(Widgets.MIN_HEIGHT_DP, Widgets.MAX_POPUP_HEIGHT_DP)
        s.copy(popupWidgets = Widgets.inPopup(s.popupWidgets, owner) { stacks -> stacks.map { if (ref in it.items) it.copy(heightDp = height) else it } })
    }

    fun addToPopup(owner: AppEntry, item: PopupItem) = update { it.copy(popups = Popups.add(it.popups, owner.key, item)) }

    fun removeFromPopup(owner: AppEntry, item: PopupItem) = update { it.copy(popups = Popups.remove(it.popups, owner.key, item)) }

    fun togglePopupItem(owner: AppEntry, item: PopupItem) = update { s ->
        val popups = if (item in s.popups[owner.key].orEmpty()) Popups.remove(s.popups, owner.key, item) else Popups.add(s.popups, owner.key, item)
        s.copy(popups = popups)
    }

    fun reorderPopup(owner: AppEntry, visibleOrder: List<PopupItem>) =
        update { it.copy(popups = Popups.reorder(it.popups, owner.key, visibleOrder)) }

    fun addBuiltinWidget(ref: WidgetRef) = update {
        it.copy(widgets = Widgets.add(it.widgets, ref, if (ref.kind == WidgetRef.Kind.Calendar) 170 else 120))
    }

    fun removeWidget(ref: WidgetRef) {
        update { it.copy(widgets = Widgets.remove(it.widgets, ref)) }
        if (ref.kind == WidgetRef.Kind.App) widgetRepository.delete(ref.appWidgetId)
    }

    fun moveWidget(ref: WidgetRef, by: Int) = update { it.copy(widgets = Widgets.move(it.widgets, ref, by)) }

    fun stackWidgetWithAbove(ref: WidgetRef) = update { it.copy(widgets = Widgets.stackWithAbove(it.widgets, ref)) }

    fun unstackWidget(ref: WidgetRef) = update { it.copy(widgets = Widgets.unstack(it.widgets, ref)) }

    fun setWidgetHeight(ref: WidgetRef, heightDp: Int) = update { it.copy(widgets = Widgets.setHeight(it.widgets, ref, heightDp)) }

    fun setWeatherPlace(place: WeatherPlace) = update { it.copy(weatherPlace = place) }

    fun setTheme(theme: ThemeSettings) = update { it.copy(theme = theme) }

    /** Keeps the current look under [name]; the same name again replaces it. */
    fun saveTheme(name: String) = update { s -> s.copy(savedThemes = s.savedThemes.filterNot { it.name == name } + SavedTheme(name, s.theme)) }

    fun deleteTheme(name: String) = update { s -> s.copy(savedThemes = s.savedThemes.filterNot { it.name == name }) }

    /**
     * Runs [open] straight away unless [packageName] has a Usage Breaker and no live grant; then
     * the pause screen is shown instead and the caller re-launches after a grant.
     */
    fun launchGuarded(packageName: String, label: String, target: BreakerTarget, open: () -> Unit) {
        val rule = state.value.breaker[packageName]
        if (rule == null) return open()
        val now = System.currentTimeMillis()
        if (Breaker.decide(rule, now, grants[packageName], null) == BreakerDecision.Open) return open()
        viewModelScope.launch {
            val used = usageRepository.usedToday(packageName)
            when (val d = Breaker.decide(rule, System.currentTimeMillis(), grants[packageName], used)) {
                BreakerDecision.Open -> open()
                is BreakerDecision.Pause -> _breakerPrompt.value = BreakerPrompt(packageName, label, target, d)
            }
        }
    }

    /** Opens for [minutes] and schedules the time's-up reminder. The caller re-launches the target. */
    fun grant(context: android.content.Context, prompt: BreakerPrompt, minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        grants[prompt.packageName] = until
        BreakerReminder.schedule(context, prompt.packageName, prompt.label, until)
        _breakerPrompt.value = null
    }

    fun cancelBreaker() {
        _breakerPrompt.value = null
    }

    fun setBreakerRule(packageName: String, rule: BreakerRule?) = update { s ->
        s.copy(breaker = if (rule == null) s.breaker - packageName else s.breaker + (packageName to rule))
    }

    fun setPrivateSpaceLocked(locked: Boolean) {
        state.value.privateSpace?.let { appRepository.setProfileLocked(it.user, locked) }
    }

    fun openPrivateSpaceSettings() {
        appRepository.openPrivateSpaceSettings()
    }

    fun setBehaviour(behaviour: BehaviourSettings) = update { it.copy(behaviour = behaviour) }

    fun setClock(clock: ClockSettings) = update { it.copy(clock = clock) }

    fun setFloatingSources(sources: FloatingSources) = update { it.copy(floating = sources) }

    fun applyPreset(id: String) = update { it.copy(theme = Presets.apply(it.theme, id)) }

    fun setIconOverride(entry: AppEntry, override: IconOverride?) = update { s ->
        s.copy(iconOverrides = if (override == null) s.iconOverrides - entry.key else s.iconOverrides + (entry.key to override))
    }

    fun onNotificationAccessChecked(granted: Boolean) {
        notificationAccess.value = granted
    }

    fun dismissNotificationPrompt() = update { it.copy(notificationPromptDismissed = true) }

    fun dismissNotification(n: AppNotification) = notificationStore.dismiss(n)

    fun onDefaultHomeChecked(isDefault: Boolean) {
        isDefaultHome.value = isDefault
    }

    fun dismissDefaultHomePrompt() = update { it.copy(defaultHomePromptDismissed = true) }

    fun toggleFavourite(entry: AppEntry) = update { s ->
        if (entry.key in s.favourites) s.copy(favourites = s.favourites - entry.key) else s.copy(favourites = s.favourites + entry.key)
    }

    /**
     * Applies a reordering of the visible favourites. Keys of currently missing apps
     * keep their slots relative to the end so they come back if the app returns.
     */
    fun reorderFavourites(visibleOrder: List<String>) = update { s ->
        val visible = visibleOrder.toSet()
        s.copy(favourites = visibleOrder + s.favourites.filterNot { it in visible })
    }

    fun hide(entry: AppEntry) = update { it.copy(hidden = it.hidden + entry.key) }

    fun unhide(entry: AppEntry) = update { it.copy(hidden = it.hidden - entry.key) }

    /** Blank or equal to the app's own label clears the override. */
    fun rename(entry: AppEntry, label: String) = update { s ->
        val trimmed = label.trim()
        if (trimmed.isEmpty() || trimmed == entry.systemLabel) {
            s.copy(labelOverrides = s.labelOverrides - entry.key)
        } else {
            s.copy(labelOverrides = s.labelOverrides + (entry.key to trimmed))
        }
    }

    /** False if the app couldn't be opened. Paused work apps ask to resume the profile instead. */
    fun launch(entry: AppEntry, sourceBounds: Rect?, options: Bundle?): Boolean =
        if (entry.isQuiet) appRepository.requestUnpause(entry.user) else appRepository.launch(entry, sourceBounds, options)

    fun openAppInfo(entry: AppEntry, sourceBounds: Rect?) = appRepository.openAppInfo(entry, sourceBounds, null)

    fun uninstallIntent(entry: AppEntry) = appRepository.uninstallIntent(entry)

    fun launchShortcut(shortcut: AppShortcut, sourceBounds: Rect?, options: Bundle?) =
        shortcutRepository.launch(shortcut, sourceBounds, options)

    private fun update(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                HomeViewModel(
                    container.appRepository,
                    container.shortcutRepository,
                    container.settingsRepository,
                    container.widgetRepository,
                    container.notificationStore,
                    container.usageRepository,
                )
            }
        }
    }
}

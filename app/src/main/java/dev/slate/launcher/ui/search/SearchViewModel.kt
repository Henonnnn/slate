package dev.slate.launcher.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.apps.AppShortcut
import dev.slate.launcher.apps.ShortcutRepository
import dev.slate.launcher.container
import dev.slate.launcher.data.SettingsRepository
import dev.slate.launcher.search.ContactsSource
import dev.slate.launcher.search.SearchRanker
import dev.slate.launcher.search.SearchResult
import dev.slate.launcher.search.SettingsCatalog
import dev.slate.launcher.search.Torch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchUiState(
    val results: List<SearchResult> = emptyList(),
    /** Show the inline "include contacts?" explainer. */
    val askForContacts: Boolean = false,
    /** Null when the device has no torch. */
    val torchOn: Boolean? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    context: Context,
    private val shortcutRepository: ShortcutRepository,
    private val settingsRepository: SettingsRepository,
    private val contacts: ContactsSource,
    private val torch: Torch,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val apps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val shortcuts = MutableStateFlow<List<AppShortcut>>(emptyList())
    private val contactsGranted = MutableStateFlow(contacts.hasPermission())
    private val torchId = MutableStateFlow<String?>(null)

    private val settingLabels = SettingsCatalog.items.map { it to context.getString(it.label) }

    private val torchState = torchId.flatMapLatest { id -> torch.state(id).map { on -> if (id == null) null else on } }

    private val results = combine(_query, apps, shortcuts, contactsGranted, torchId) { q, a, s, _, _ -> Triple(q, a, s) }
        .mapLatest { (q, a, s) ->
            val ranked = withContext(Dispatchers.Default) {
                val labels = a.associate { it.packageName to it.label }
                SearchRanker.rank(q, a, s, labels, availableSettings())
            }
            SearchRanker.merge(ranked, contacts.search(q))
        }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<SearchUiState> = combine(results, _query, contactsGranted, settingsRepository.settings, torchState) {
            r, q, granted, settings, torchOn ->
        SearchUiState(
            results = r,
            askForContacts = q.isNotBlank() && !granted && !settings.contactsPromptDismissed,
            torchOn = torchOn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch { torchId.value = torch.cameraId() }
    }

    /** Visible and hidden apps: hiding declutters the list but keeps the app findable. */
    fun setApps(entries: List<AppEntry>) {
        apps.value = entries
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    /** Called each time the surface opens: shortcuts and permissions may have changed. */
    fun onOpened() {
        contactsGranted.value = contacts.hasPermission()
        viewModelScope.launch {
            shortcuts.value = if (shortcutRepository.hasAccess()) shortcutRepository.all() else emptyList()
        }
    }

    fun onClosed() {
        _query.value = ""
    }

    fun onContactsPermission(granted: Boolean) {
        contactsGranted.value = granted
        // Denied in the system dialog: don't keep asking on every query.
        if (!granted) dismissContactsPrompt()
    }

    fun dismissContactsPrompt() {
        viewModelScope.launch { settingsRepository.update { it.copy(contactsPromptDismissed = true) } }
    }

    fun toggleTorch(): Boolean {
        val id = torchId.value ?: return false
        return torch.set(id, state.value.torchOn != true)
    }

    fun launchShortcut(shortcut: AppShortcut) = shortcutRepository.launch(shortcut, null, null)

    private fun availableSettings() =
        if (torchId.value == null) settingLabels.filterNot { it.first.id == SettingsCatalog.TORCH } else settingLabels

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val c = app.container
                SearchViewModel(app, c.shortcutRepository, c.settingsRepository, c.contactsSource, c.torch)
            }
        }
    }
}

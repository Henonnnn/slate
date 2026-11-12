package dev.slate.launcher.search

import androidx.compose.runtime.Immutable
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.apps.AppShortcut

@Immutable
sealed interface SearchResult {
    val key: String

    data class Calculation(val expression: String, val value: String) : SearchResult {
        override val key get() = "calc"
    }
    data class Conversion(val source: String, val value: String) : SearchResult {
        override val key get() = "convert"
    }
    data class App(val entry: AppEntry) : SearchResult {
        override val key get() = "app:${entry.key}"
    }
    data class Shortcut(val shortcut: AppShortcut, val appLabel: String) : SearchResult {
        override val key get() = "shortcut:${shortcut.key}"
    }
    data class Setting(val item: SettingItem, val label: String) : SearchResult {
        override val key get() = "setting:${item.id}"
    }
    data class Contact(val hit: ContactHit) : SearchResult {
        override val key get() = "contact:${hit.id}"
    }
    data class Web(val query: String) : SearchResult {
        override val key get() = "web"
    }
}

/**
 * Turns a query into ordered results. Pure; contacts are looked up separately (IO) and
 * slotted in by [merge].
 *
 * Order: calculation or conversion, apps, settings, shortcuts, contacts, web. Settings and
 * shortcuts need a stronger match than apps (substring or better), since loose matches
 * there are mostly noise.
 */
object SearchRanker {
    const val MAX_APPS = 8
    const val MAX_SHORTCUTS = 4
    const val MAX_SETTINGS = 3
    private const val STRONG = 600

    data class Ranked(val leading: List<SearchResult>, val trailing: List<SearchResult>)

    fun rank(
        query: String,
        apps: List<AppEntry>,
        shortcuts: List<AppShortcut>,
        appLabels: Map<String, String>,
        settings: List<Pair<SettingItem, String>>,
    ): Ranked {
        val q = FuzzyMatcher.normalize(query)
        if (q.isEmpty()) return Ranked(emptyList(), emptyList())

        val leading = buildList {
            UnitConverter.convert(query)?.let { add(SearchResult.Conversion(it.source, it.text)) }
                ?: Calculator.evaluate(query)?.let { add(SearchResult.Calculation(query.trim(), it.text)) }

            apps.mapNotNull { e -> FuzzyMatcher.scoreNormalized(q, FuzzyMatcher.normalize(e.label))?.let { e to it } }
                .sortedWith(compareByDescending<Pair<AppEntry, Int>> { it.second }.thenBy { it.first.label })
                .distinctBy { it.first.key }
                .take(MAX_APPS)
                .forEach { add(SearchResult.App(it.first)) }

            settings.mapNotNull { (item, label) ->
                val best = (listOf(label) + item.keywords).maxOf { FuzzyMatcher.score(q, it) ?: 0 }
                if (best >= STRONG) Triple(item, label, best) else null
            }
                .sortedByDescending { it.third }
                .take(MAX_SETTINGS)
                .forEach { add(SearchResult.Setting(it.first, it.second)) }

            shortcuts.mapNotNull { s -> FuzzyMatcher.score(q, s.label)?.takeIf { it >= STRONG }?.let { s to it } }
                .sortedByDescending { it.second }
                .take(MAX_SHORTCUTS)
                .forEach { (s, _) -> add(SearchResult.Shortcut(s, appLabels[s.packageName].orEmpty())) }
        }
        return Ranked(leading, listOf(SearchResult.Web(query.trim())))
    }

    fun merge(ranked: Ranked, contacts: List<ContactHit>): List<SearchResult> =
        ranked.leading + contacts.map(SearchResult::Contact) + ranked.trailing
}

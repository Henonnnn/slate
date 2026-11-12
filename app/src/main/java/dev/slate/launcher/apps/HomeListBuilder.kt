package dev.slate.launcher.apps

import java.text.Collator

data class HomeLists(
    val favourites: List<AppEntry>,
    /** Every visible app, alphabetical, favourites included. */
    val apps: List<AppEntry>,
    val hidden: List<AppEntry>,
    /** Bucket letter → index into [apps]. */
    val letterIndex: Map<Char, Int>,
    /** The private space's apps; empty while it's locked. */
    val privateApps: List<AppEntry> = emptyList(),
    /** Pop-up folders kept at the top of the app list; those in favourites are in [favourites]. */
    val folders: List<AppEntry> = emptyList(),
)

/** Pure: applies favourites, hidden set and label overrides to the installed apps. */
object HomeListBuilder {
    fun build(
        installed: List<AppEntry>,
        favourites: List<String>,
        hidden: Set<String>,
        labelOverrides: Map<String, String>,
        collator: Collator,
        folders: List<AppEntry> = emptyList(),
    ): HomeLists {
        fun renamed(entry: AppEntry): AppEntry {
            val override = labelOverrides[entry.key]?.trim()?.takeIf { it.isNotEmpty() }
            return if (override == null) entry else entry.copy(label = override, bucket = AlphabetIndex.bucketOf(override))
        }
        val all = installed.map(::renamed)
        val namedFolders = folders.map(::renamed)
        val (privateEntries, labelled) = all.partition { it.isPrivate }
        val byKey = (labelled + namedFolders).associateBy { it.key }
        val visible = AlphabetIndex.sort(labelled.filter { it.key !in hidden }, collator) { it.label }
        val favouriteApps = favourites.distinct().mapNotNull { byKey[it] }.filter { it.key !in hidden }
        val favouriteKeys = favouriteApps.mapTo(HashSet()) { it.key }
        val hiddenApps = AlphabetIndex.sort(labelled.filter { it.key in hidden }, collator) { it.label }
        return HomeLists(
            favourites = favouriteApps,
            apps = visible,
            hidden = hiddenApps,
            letterIndex = AlphabetIndex.firstIndexByBucket(visible.map { it.bucket }),
            privateApps = AlphabetIndex.sort(privateEntries, collator) { it.label },
            folders = AlphabetIndex.sort(namedFolders.filter { it.key !in favouriteKeys }, collator) { it.label },
        )
    }
}

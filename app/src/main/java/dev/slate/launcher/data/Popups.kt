package dev.slate.launcher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One entry in a pop-up: an app ([dev.slate.launcher.apps.AppEntry.key]) or a shortcut ([dev.slate.launcher.apps.AppShortcut.key]). */
@Serializable
data class PopupItem(val kind: Kind, val key: String) {
    @Serializable
    enum class Kind {
        @SerialName("app") App,
        @SerialName("shortcut") Shortcut,
    }

    companion object {
        fun app(key: String) = PopupItem(Kind.App, key)
        fun shortcut(key: String) = PopupItem(Kind.Shortcut, key)
    }
}

/** A pop-up of its own, with no app behind it. It shows in favourites while its key is there, else in the app list. */
@Serializable
data class PopupFolder(val id: String, val name: String)

/** Pure edits on folders. A folder's items live in the pop-up map under [key]. */
object Folders {
    const val KEY_PREFIX = "folder:"

    fun key(id: String) = KEY_PREFIX + id

    fun create(s: LauncherSettings, folder: PopupFolder, inFavourites: Boolean): LauncherSettings = s.copy(
        folders = s.folders + folder,
        favourites = if (inFavourites) s.favourites + key(folder.id) else s.favourites,
    )

    fun delete(s: LauncherSettings, id: String): LauncherSettings = s.copy(
        folders = s.folders.filterNot { it.id == id },
        favourites = s.favourites - key(id),
        popups = s.popups - key(id),
        popupWidgets = s.popupWidgets - key(id),
        labelOverrides = s.labelOverrides - key(id),
    )
}

/**
 * Pure edits on the pop-up map (owner app key → ordered items). Like favourites, items whose
 * target is currently missing are kept, so they return with the app; an emptied pop-up is
 * removed entirely.
 */
object Popups {
    fun add(popups: Map<String, List<PopupItem>>, owner: String, item: PopupItem): Map<String, List<PopupItem>> {
        if (item.kind == PopupItem.Kind.App && item.key == owner) return popups
        val current = popups[owner].orEmpty()
        if (item in current) return popups
        return popups + (owner to current + item)
    }

    fun remove(popups: Map<String, List<PopupItem>>, owner: String, item: PopupItem): Map<String, List<PopupItem>> {
        val remaining = popups[owner].orEmpty() - item
        return if (remaining.isEmpty()) popups - owner else popups + (owner to remaining)
    }

    /** [visibleOrder] is what the user sees; items not shown (missing targets) keep their place at the end. */
    fun reorder(popups: Map<String, List<PopupItem>>, owner: String, visibleOrder: List<PopupItem>): Map<String, List<PopupItem>> {
        val current = popups[owner] ?: return popups
        val shown = visibleOrder.toSet()
        return popups + (owner to visibleOrder.filter { it in current } + current.filterNot { it in shown })
    }

    /**
     * Resolves items in order, skipping ones whose target is missing (uninstalled app,
     * removed shortcut, paused profile).
     */
    fun <A, S> resolve(items: List<PopupItem>, app: (String) -> A?, shortcut: (String) -> S?): List<Resolved<A, S>> =
        items.mapNotNull { item ->
            when (item.kind) {
                PopupItem.Kind.App -> app(item.key)?.let { Resolved.App(item, it) }
                PopupItem.Kind.Shortcut -> shortcut(item.key)?.let { Resolved.Shortcut(item, it) }
            }
        }

    sealed interface Resolved<out A, out S> {
        val item: PopupItem
        data class App<A>(override val item: PopupItem, val app: A) : Resolved<A, Nothing>
        data class Shortcut<S>(override val item: PopupItem, val shortcut: S) : Resolved<Nothing, S>
    }
}

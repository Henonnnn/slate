package dev.slate.launcher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One widget: an app widget (by its AppWidgetHost id) or one of Slate's own. */
@Serializable
data class WidgetRef(val kind: Kind, val appWidgetId: Int = 0) {
    @Serializable
    enum class Kind {
        @SerialName("app") App,
        @SerialName("calendar") Calendar,
        @SerialName("weather") Weather,
    }

    /** Stable, Bundle-saveable identity for lists and state. */
    val key: String get() = if (kind == Kind.App) "app:$appWidgetId" else kind.name.lowercase()

    companion object {
        fun app(id: Int) = WidgetRef(Kind.App, id)
        val calendar = WidgetRef(Kind.Calendar)
        val weather = WidgetRef(Kind.Weather)
    }
}

/**
 * A slot in the widget area. One item shows on its own; several form a stack you swipe
 * through sideways. All items in a stack share its height.
 */
@Serializable
data class WidgetStack(val items: List<WidgetRef>, val heightDp: Int) {
    val key: String get() = items.first().key
}

/** Pure edits on the widget area. Built-in widgets appear at most once. */
object Widgets {
    const val MIN_HEIGHT_DP = 72
    const val MAX_HEIGHT_DP = 480
    const val MAX_POPUP_HEIGHT_DP = 400

    fun clampHeight(dp: Int) = dp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)

    fun add(stacks: List<WidgetStack>, ref: WidgetRef, heightDp: Int): List<WidgetStack> {
        if (stacks.any { ref in it.items }) return stacks
        return stacks + WidgetStack(listOf(ref), clampHeight(heightDp))
    }

    /** Removes [ref] wherever it is; an emptied stack disappears. */
    fun remove(stacks: List<WidgetStack>, ref: WidgetRef): List<WidgetStack> =
        stacks.mapNotNull { s -> if (ref !in s.items) s else (s.items - ref).takeIf { it.isNotEmpty() }?.let { s.copy(items = it) } }

    fun move(stacks: List<WidgetStack>, ref: WidgetRef, by: Int): List<WidgetStack> {
        val from = stacks.indexOfFirst { ref in it.items }
        val to = from + by
        if (from < 0 || to !in stacks.indices) return stacks
        return stacks.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Merges [ref]'s whole stack into the stack above it, keeping the upper stack's height. */
    fun stackWithAbove(stacks: List<WidgetStack>, ref: WidgetRef): List<WidgetStack> {
        val i = stacks.indexOfFirst { ref in it.items }
        if (i <= 0) return stacks
        val merged = stacks[i - 1].copy(items = stacks[i - 1].items + stacks[i].items)
        return stacks.toMutableList().apply {
            set(i - 1, merged)
            removeAt(i)
        }
    }

    /** Takes [ref] out of its stack into its own slot just below it. */
    fun unstack(stacks: List<WidgetStack>, ref: WidgetRef): List<WidgetStack> {
        val i = stacks.indexOfFirst { ref in it.items }
        if (i < 0 || stacks[i].items.size < 2) return stacks
        val rest = stacks[i].copy(items = stacks[i].items - ref)
        return stacks.toMutableList().apply {
            set(i, rest)
            add(i + 1, WidgetStack(listOf(ref), rest.heightDp))
        }
    }

    fun setHeight(stacks: List<WidgetStack>, ref: WidgetRef, heightDp: Int): List<WidgetStack> =
        stacks.map { if (ref in it.items) it.copy(heightDp = clampHeight(heightDp)) else it }

    /** Replaces one owner's list, dropping the entry once it's empty. */
    fun inPopup(popups: Map<String, List<WidgetStack>>, owner: String, edit: (List<WidgetStack>) -> List<WidgetStack>): Map<String, List<WidgetStack>> {
        val next = edit(popups[owner].orEmpty())
        return if (next.isEmpty()) popups - owner else popups + (owner to next)
    }

    fun appWidgetIds(stacks: List<WidgetStack>): Set<Int> =
        stacks.flatMap { it.items }.filter { it.kind == WidgetRef.Kind.App }.mapTo(HashSet()) { it.appWidgetId }
}

package dev.slate.launcher.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app shortcut (static, dynamic or pinned) as the launcher sees it. */
@Immutable
data class AppShortcut(
    /** "pkg/id/userSerial". */
    val key: String,
    val packageName: String,
    val id: String,
    val user: UserHandle,
    val label: String,
    val pinned: Boolean,
    val info: ShortcutInfo,
)

/**
 * App shortcuts via [LauncherApps]. The platform only answers these queries for the
 * default home app ([hasAccess]); otherwise every call returns empty and the UI says why.
 */
class ShortcutRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    suspend fun hasAccess(): Boolean = withContext(io) {
        runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)
    }

    /** Static and dynamic shortcuts by rank (the app's own order), then pinned ones. */
    suspend fun forApp(entry: AppEntry): List<AppShortcut> = withContext(io) {
        val query = ShortcutQuery()
            .setPackage(entry.packageName)
            .setQueryFlags(ALL_KINDS)
        query(query, entry.user)
            .filter { it.activity == null || it.activity == entry.component }
            .sortedWith(compareBy<ShortcutInfo> { it.isPinned && !it.isDynamic && !it.isDeclaredInManifest }.thenBy { it.rank })
            .map { it.toAppShortcut(entry.user) }
    }

    /** Every shortcut on every profile, for search. */
    suspend fun all(): List<AppShortcut> = withContext(io) {
        launcherApps.profiles.flatMap { user ->
            query(ShortcutQuery().setQueryFlags(ALL_KINDS), user).map { it.toAppShortcut(user) }
        }.distinctBy { it.key }
    }

    /** Looks shortcuts up by key, one query per package and profile. Missing ones are absent from the map. */
    suspend fun resolve(keys: Collection<String>): Map<String, AppShortcut> = withContext(io) {
        keys.mapNotNull(::parseKey).groupBy { it.packageName to it.serial }.flatMap { (group, parsed) ->
            val user = userManager.getUserForSerialNumber(group.second) ?: return@flatMap emptyList()
            val query = ShortcutQuery()
                .setPackage(group.first)
                .setShortcutIds(parsed.map { it.id })
                .setQueryFlags(ALL_KINDS)
            query(query, user).map { it.toAppShortcut(user) }
        }.associateBy { it.key }
    }

    /**
     * Makes this launcher's pinned shortcuts exactly [wanted] (keys). Pinning keeps a shortcut alive
     * after the app drops it from its dynamic set, which pop-ups rely on. The platform replaces the
     * whole pinned set per package, so every package that has pins now or should have them is set.
     */
    suspend fun syncPins(wanted: Set<String>) = withContext(io) {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return@withContext
        val wantedByGroup = wanted.mapNotNull(::parseKey).groupBy({ it.packageName to it.serial }, { it.id })
        val currentByGroup = launcherApps.profiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            query(ShortcutQuery().setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED), user).map { (it.`package` to serial) to it.id }
        }.groupBy({ it.first }, { it.second })
        for (group in wantedByGroup.keys + currentByGroup.keys) {
            val ids = wantedByGroup[group].orEmpty()
            if (ids.toSet() == currentByGroup[group].orEmpty().toSet()) continue
            val user = userManager.getUserForSerialNumber(group.second) ?: continue
            try {
                launcherApps.pinShortcuts(group.first, ids, user)
            } catch (e: RuntimeException) {
                // Profile locked/paused or package gone; retried on the next change.
                Log.w(TAG, "Pin sync failed for ${group.first}", e)
            }
        }
    }

    /** Key of the app entry a shortcut belongs to, when it names its activity. */
    fun ownerAppKey(info: ShortcutInfo, user: UserHandle): String? =
        info.activity?.let { "${it.flattenToShortString()}/${userManager.getSerialNumberForUser(user)}" }

    fun keyOf(info: ShortcutInfo, user: UserHandle): String = info.toAppShortcut(user).key

    fun launch(shortcut: AppShortcut, sourceBounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startShortcut(shortcut.packageName, shortcut.id, sourceBounds, options, shortcut.user)
        true
    } catch (e: RuntimeException) {
        // ActivityNotFoundException, IllegalStateException (user locked), SecurityException (lost host role).
        Log.w(TAG, "Shortcut launch failed for ${shortcut.key}", e)
        false
    }

    private fun query(query: ShortcutQuery, user: UserHandle): List<ShortcutInfo> = try {
        launcherApps.getShortcuts(query, user).orEmpty().filter { it.isEnabled }
    } catch (e: SecurityException) {
        emptyList() // Not the default home app.
    } catch (e: IllegalStateException) {
        emptyList() // Profile locked or paused.
    }

    private data class ParsedKey(val packageName: String, val id: String, val serial: Long)

    /** "pkg/id/serial"; the id itself may contain slashes. */
    private fun parseKey(key: String): ParsedKey? {
        val first = key.indexOf('/')
        val last = key.lastIndexOf('/')
        if (first <= 0 || last <= first) return null
        val serial = key.substring(last + 1).toLongOrNull() ?: return null
        return ParsedKey(key.substring(0, first), key.substring(first + 1, last), serial)
    }

    private fun ShortcutInfo.toAppShortcut(user: UserHandle) = AppShortcut(
        key = "$`package`/$id/${userManager.getSerialNumberForUser(user)}",
        packageName = `package`,
        id = id,
        user = user,
        label = (shortLabel ?: longLabel)?.toString()?.trim().orEmpty().ifEmpty { id },
        pinned = isPinned,
        info = this,
    )

    private companion object {
        const val TAG = "ShortcutRepository"
        const val ALL_KINDS = ShortcutQuery.FLAG_MATCH_DYNAMIC or ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_PINNED
    }
}

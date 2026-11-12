package dev.slate.launcher.apps

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.slate.launcher.icons.IconCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import java.util.Locale

/** A private space profile and whether it's locked. */
data class PrivateSpace(val user: UserHandle, val locked: Boolean)

/**
 * All launchable activities across every profile the launcher can see
 * (personal + work), via [LauncherApps]. Reloads on package and profile changes.
 */
class AppRepository(
    private val context: Context,
    private val iconCache: IconCache,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _privateSpace = MutableStateFlow<PrivateSpace?>(null)

    /** The private space, if this user has one and Slate may see it (needs the home role). */
    val privateSpace: StateFlow<PrivateSpace?> = _privateSpace

    @OptIn(ExperimentalCoroutinesApi::class)
    val apps: Flow<List<AppEntry>> = changes()
        .onStart { emit(Unit) }
        .mapLatest { loadAll() }
        .flowOn(io)

    fun launch(entry: AppEntry, sourceBounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startMainActivity(entry.component, entry.user, sourceBounds, options)
        true
    } catch (e: RuntimeException) {
        // ActivityNotFoundException / SecurityException: app vanished or profile locked.
        Log.w(TAG, "Launch failed for ${entry.key}", e)
        false
    }

    fun openAppInfo(entry: AppEntry, sourceBounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startAppDetailsActivity(entry.component, entry.user, sourceBounds, options)
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "App info failed for ${entry.key}", e)
        false
    }

    /** The system uninstaller confirms; works for work-profile apps via EXTRA_USER. */
    fun uninstallIntent(entry: AppEntry): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null))
            .putExtra(Intent.EXTRA_USER, entry.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun uninstallKind(flags: Int): AppEntry.Uninstall = when {
        flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 -> AppEntry.Uninstall.Updates
        flags and ApplicationInfo.FLAG_SYSTEM != 0 -> AppEntry.Uninstall.None
        else -> AppEntry.Uninstall.App
    }

    /** Asks the system to resume a paused work profile; it may prompt for the work challenge. */
    fun requestUnpause(user: UserHandle): Boolean = setProfileLocked(user, false)

    /** Locks or unlocks a private space; unlocking shows the system's own credential prompt. */
    fun setProfileLocked(user: UserHandle, locked: Boolean): Boolean = try {
        userManager.requestQuietModeEnabled(locked, user)
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "Cannot change profile state", e)
        false
    }

    /** The system's private space settings; only the home app gets the intent. */
    fun openPrivateSpaceSettings(): Boolean {
        // The SDK lists this from API 36.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        return try {
            val sender = launcherApps.privateSpaceSettingsIntent ?: return false
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            context.startIntentSender(sender, null, 0, 0, 0, options.toBundle())
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot open private space settings", e)
            false
        } catch (e: IntentSender.SendIntentException) {
            Log.w(TAG, "Cannot open private space settings", e)
            false
        }
    }

    private fun isPrivateProfile(user: UserHandle): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE

    fun currentLocale(): Locale = context.resources.configuration.locales[0]

    /** Unsorted; ordering depends on user label overrides, see [HomeListBuilder]. */
    private fun loadAll(): List<AppEntry> {
        val myUser = Process.myUserHandle()
        val ownPackage = context.packageName
        var privateSpace: PrivateSpace? = null
        val entries = launcherApps.profiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            val quiet = userManager.isQuietModeEnabled(user)
            val private = isPrivateProfile(user)
            if (private) privateSpace = PrivateSpace(user, locked = quiet)
            // Android requires a locked private space to leave no trace: not listed, not searchable.
            if (private && quiet) return@flatMap emptyList()
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot list activities for profile $serial", e)
                emptyList()
            }
            activities
                .filterNot { it.componentName.packageName == ownPackage && user == myUser }
                .map { info ->
                    val label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName }
                    AppEntry(
                        key = "${info.componentName.flattenToShortString()}/$serial",
                        component = info.componentName,
                        user = user,
                        label = label,
                        systemLabel = label,
                        isWorkProfile = user != myUser,
                        isQuiet = quiet,
                        // The install directory is renamed on every update.
                        versionStamp = info.applicationInfo.sourceDir.orEmpty(),
                        bucket = AlphabetIndex.bucketOf(label),
                        uninstall = uninstallKind(info.applicationInfo.flags),
                        isPrivate = private,
                    )
                }
        }
        _privateSpace.value = privateSpace
        return entries
    }

    private fun changes(): Flow<Unit> = callbackFlow {
        fun changed(packageName: String?) {
            packageName?.let(iconCache::evictPackage)
            trySend(Unit)
        }

        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
            override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
            override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                packageNames.forEach(::changed)
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
                packageNames.forEach(::changed)
            override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) =
                packageNames.forEach(::changed)
            override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) =
                packageNames.forEach(::changed)
        }
        // Locale changes relabel apps; profile changes add/remove whole sets.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = changed(null)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            // Private space lock and unlock.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose {
            launcherApps.unregisterCallback(callback)
            context.unregisterReceiver(receiver)
        }
    }

    private companion object {
        const val TAG = "AppRepository"
    }
}

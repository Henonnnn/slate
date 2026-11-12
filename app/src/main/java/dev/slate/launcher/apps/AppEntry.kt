package dev.slate.launcher.apps

import android.content.ComponentName
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.Immutable

/** One launchable activity for one user profile. */
@Immutable
data class AppEntry(
    /** Stable across reboots: "pkg/.Activity/userSerial". */
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    /** Label shown in the UI (user override if set). */
    val label: String,
    /** Label the app itself declares. */
    val systemLabel: String,
    val isWorkProfile: Boolean,
    /** Work profile is paused: shown dimmed, tapping asks to resume it. */
    val isQuiet: Boolean,
    /** Changes whenever the app is updated; part of the icon disk-cache key. */
    val versionStamp: String,
    val bucket: Char,
    val uninstall: Uninstall = Uninstall.App,
    /** Lives in the private space, which gets its own section and never mixes into the main list. */
    val isPrivate: Boolean = false,
) {
    val packageName: String get() = component.packageName

    /** Not an app: a pop-up folder, or Message Hub, wearing an entry so rows and pop-ups can show it. */
    val isFolder: Boolean get() = component.packageName == FOLDER_PACKAGE

    /** "pkg/userSerial": notifications belong to a package in a profile, not to an activity. */
    val notificationKey: String get() = "$packageName/${key.substringAfterLast('/')}"

    enum class Uninstall {
        App,
        /** Preinstalled app with a newer version on top: only the update can be removed. */
        Updates,
        /** Part of the system image. */
        None,
    }

    companion object {
        private const val FOLDER_PACKAGE = "dev.slate.folder"

        fun folder(key: String, name: String) = AppEntry(
            key = key,
            component = ComponentName(FOLDER_PACKAGE, key),
            user = Process.myUserHandle(),
            label = name,
            systemLabel = name,
            isWorkProfile = false,
            isQuiet = false,
            versionStamp = "",
            bucket = AlphabetIndex.OTHER,
        )
    }
}

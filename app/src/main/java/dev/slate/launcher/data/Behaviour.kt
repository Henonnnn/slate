package dev.slate.launcher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What swiping an app row does. */
@Serializable
enum class RowSwipeAction {
    /** Opens the app's pop-up, inline under the row. */
    @SerialName("actions") Popup,
    @SerialName("app_info") AppInfo,
    @SerialName("favourite") Favourite,
    @SerialName("hide") Hide,
    @SerialName("none") None,
}

/** How the home screen behaves, as opposed to how it looks ([ThemeSettings]). */
@Serializable
data class BehaviourSettings(
    /** "Right" and "left" mean towards the end and start edge, so they mirror in RTL. */
    val rowSwipeRight: RowSwipeAction = RowSwipeAction.Popup,
    val rowSwipeLeft: RowSwipeAction = RowSwipeAction.AppInfo,
    val pullDownToSearch: Boolean = true,
    val showScrubber: Boolean = true,
    val showSearchButton: Boolean = true,
    val searchHiddenApps: Boolean = true,
    val showPrivateSpace: Boolean = true,
    /** Favourites show their newest notification's text under the app's name. */
    val notificationPreviews: Boolean = true,
    /** Empty space above the clock as a share of the screen height, 0–[MAX_HOME_OFFSET]. Moves the clock, widgets and favourites together. */
    val homeOffset: Float = 0f,
    /** Touches answer with a tick; off silences every one of Slate's own. */
    val haptics: Boolean = true,
    val hideStatusBar: Boolean = false,
    /** All apps goes back to home after this long untouched; 0 keeps it open. */
    val allAppsCloseSeconds: Int = 5,
    /** How many of an app's own shortcuts its pop-up lists by itself. */
    val popupShortcuts: Int = 4,
    /** Darken the home screen behind a pop-up (Niagara doesn't). */
    val popupDim: Boolean = false,
    /** A pop-up opens with its newest notification unfolded. */
    val popupExpandNotification: Boolean = true,
) {
    companion object {
        const val MAX_HOME_OFFSET = 0.7f
    }
}

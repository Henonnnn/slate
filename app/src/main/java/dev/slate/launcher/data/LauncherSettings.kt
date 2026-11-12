package dev.slate.launcher.data

import dev.slate.launcher.floating.FloatingSources
import dev.slate.launcher.wellbeing.BreakerRule
import kotlinx.serialization.Serializable

/**
 * Typed launcher settings, persisted as JSON. New fields must have defaults so
 * older files keep decoding; bump [schemaVersion] only for breaking changes.
 *
 * App references use [dev.slate.launcher.apps.AppEntry.key]. Keys for apps that are
 * currently missing (uninstalled, profile removed) are kept, so a reinstall or a
 * returning work profile picks its favourites, names and hidden state back up.
 */
/** A saved place for the weather widget, from device location or a city search. */
@Serializable
data class WeatherPlace(val name: String, val latitude: Double, val longitude: Double)

@Serializable
data class LauncherSettings(
    val schemaVersion: Int = LauncherSettings.CURRENT_SCHEMA,
    val defaultHomePromptDismissed: Boolean = false,
    /** Ordered favourites shown above the full list. */
    val favourites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    /** User-chosen labels; affect sorting and the alphabet index. */
    val labelOverrides: Map<String, String> = emptyMap(),
    /** "Include contacts in search?" answered with "No thanks". */
    val contactsPromptDismissed: Boolean = false,
    /** Pop-ups: owner app key → apps and shortcuts revealed by swiping that app's row. */
    val popups: Map<String, List<PopupItem>> = emptyMap(),
    val folders: List<PopupFolder> = emptyList(),
    /** Pop-up owner key → widgets shown in that pop-up. */
    val popupWidgets: Map<String, List<WidgetStack>> = emptyMap(),
    /** Shortcuts the user accepted from an app's "add to home screen" request; kept pinned. */
    val pinnedShortcuts: List<String> = emptyList(),
    /** Widget area above favourites, top to bottom. */
    val widgets: List<WidgetStack> = emptyList(),
    /** Where the weather widget looks; null until chosen. */
    val weatherPlace: WeatherPlace? = null,
    /** "Show notification dots?" card answered with "Not now". */
    val notificationPromptDismissed: Boolean = false,
    val theme: ThemeSettings = ThemeSettings(),
    val savedThemes: List<SavedTheme> = emptyList(),
    /** Icon Assistant: app key → replacement icon. */
    val iconOverrides: Map<String, IconOverride> = emptyMap(),
    val floating: FloatingSources = FloatingSources(),
    /** Usage Breaker: package → rule. Present means the app pauses before opening from Slate. */
    val breaker: Map<String, BreakerRule> = emptyMap(),
    val behaviour: BehaviourSettings = BehaviourSettings(),
    val clock: ClockSettings = ClockSettings(),
) {
    /**
     * Brings older files up to date. Schema 2 retired the default dimming layer over the wallpaper,
     * so settings saved with it (the old default was stored, not chosen) start undimmed. Schema 3
     * moved the clock onto its bundled font, so the old default look doesn't carry over.
     */
    fun migrated(): LauncherSettings {
        if (schemaVersion >= CURRENT_SCHEMA) return this
        val undimmed = if (schemaVersion < 2) theme.copy(dimming = 0f) else theme
        val updatedClock = clock.copy(
            font = clock.font.takeUnless { it == FontChoice.Sans },
            weight = if (clock.weight == ClockWeight.Light) ClockWeight.Regular else clock.weight,
        )
        return copy(schemaVersion = CURRENT_SCHEMA, theme = undimmed, clock = updatedClock)
    }

    /** Every app widget in use, on home or in a pop-up. Anything else is an orphan. */
    fun usedWidgetIds(): Set<Int> = Widgets.appWidgetIds(widgets + popupWidgets.values.flatten())

    /** Every shortcut Slate should keep pinned with the platform. */
    fun wantedPins(): Set<String> =
        pinnedShortcuts.toSet() + popups.values.flatten().filter { it.kind == PopupItem.Kind.Shortcut }.map { it.key }

    companion object {
        const val CURRENT_SCHEMA = 3
    }
}

package dev.slate.launcher.search

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import dev.slate.launcher.R

/**
 * System settings reachable from search.
 *
 * Apps can't flip Wi-Fi, Bluetooth, mobile data or airplane mode themselves on modern
 * Android, so those open the system's own quick panel or settings page instead. The
 * torch is the one real toggle a normal app may control (see [Torch]).
 */
data class SettingItem(
    val id: String,
    @param:StringRes val label: Int,
    /** English search terms besides the label. */
    val keywords: List<String>,
    /** Null for the torch, which toggles in place. */
    val intent: (() -> Intent)?,
)

object SettingsCatalog {
    const val TORCH = "torch"

    val items: List<SettingItem> = listOf(
        SettingItem(TORCH, R.string.setting_torch, listOf("flashlight", "torch", "light", "lamp"), null),
        // Quick panels slide up over the current screen instead of leaving the launcher.
        page("internet", R.string.setting_internet, listOf("wifi", "wi-fi", "wlan", "mobile data", "network", "internet"), Settings.Panel.ACTION_INTERNET_CONNECTIVITY),
        page("volume", R.string.setting_volume, listOf("volume", "sound", "ringer", "mute"), Settings.Panel.ACTION_VOLUME),
        page("nfc", R.string.setting_nfc, listOf("nfc", "tap to pay"), Settings.Panel.ACTION_NFC),
        page("bluetooth", R.string.setting_bluetooth, listOf("bluetooth", "headphones", "pair"), Settings.ACTION_BLUETOOTH_SETTINGS),
        page("airplane", R.string.setting_airplane, listOf("airplane", "aeroplane", "flight mode"), Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        page("display", R.string.setting_display, listOf("display", "brightness", "dark mode", "dark theme", "screen timeout"), Settings.ACTION_DISPLAY_SETTINGS),
        page("battery", R.string.setting_battery, listOf("battery", "battery saver", "power"), Settings.ACTION_BATTERY_SAVER_SETTINGS),
        page("location", R.string.setting_location, listOf("location", "gps"), Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        page("sound", R.string.setting_sound, listOf("sound", "ringtone", "do not disturb", "vibration"), Settings.ACTION_SOUND_SETTINGS),
        page("apps", R.string.setting_apps, listOf("apps", "applications", "app list", "default apps"), Settings.ACTION_APPLICATION_SETTINGS),
        page("home", R.string.setting_home, listOf("home app", "launcher", "default launcher"), Settings.ACTION_HOME_SETTINGS),
        page("storage", R.string.setting_storage, listOf("storage", "space", "disk"), Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        page("accessibility", R.string.setting_accessibility, listOf("accessibility", "talkback", "font size"), Settings.ACTION_ACCESSIBILITY_SETTINGS),
        page("privacy", R.string.setting_privacy, listOf("privacy", "permissions"), Settings.ACTION_PRIVACY_SETTINGS),
        page("security", R.string.setting_security, listOf("security", "lock screen", "fingerprint", "password"), Settings.ACTION_SECURITY_SETTINGS),
        page("datetime", R.string.setting_date_time, listOf("date", "time", "time zone", "clock"), Settings.ACTION_DATE_SETTINGS),
        page("language", R.string.setting_language, listOf("language", "keyboard", "input"), Settings.ACTION_LOCALE_SETTINGS),
        page("developer", R.string.setting_developer, listOf("developer options", "usb debugging", "adb"), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        page("settings", R.string.setting_all, listOf("settings", "system settings", "preferences"), Settings.ACTION_SETTINGS),
    )

    private fun page(id: String, label: Int, keywords: List<String>, action: String) =
        SettingItem(id, label, keywords) { Intent(action) }
}

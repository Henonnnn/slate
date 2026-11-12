package dev.slate.launcher

import dev.slate.launcher.floating.AudioRoutes
import android.content.Context
import dev.slate.launcher.apps.AppRepository
import dev.slate.launcher.apps.ShortcutRepository
import dev.slate.launcher.data.BackupRepository
import dev.slate.launcher.data.SettingsRepository
import dev.slate.launcher.floating.MediaSessions
import dev.slate.launcher.icons.IconCache
import dev.slate.launcher.icons.IconPackRepository
import dev.slate.launcher.notifications.NotificationStore
import dev.slate.launcher.search.ContactsSource
import dev.slate.launcher.search.Torch
import dev.slate.launcher.wellbeing.UsageRepository
import dev.slate.launcher.widgets.CalendarSource
import dev.slate.launcher.widgets.WeatherRepository
import dev.slate.launcher.widgets.WidgetRepository

/**
 * Manual constructor injection. One graph, built once in [SlateApp.onCreate];
 * no annotation processing and nothing reflective on the cold-start path.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val iconPacks = IconPackRepository(appContext)
    val iconCache = IconCache(appContext, iconPacks)
    val appRepository = AppRepository(appContext, iconCache)
    val shortcutRepository = ShortcutRepository(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val backupRepository by lazy { BackupRepository(appContext, settingsRepository) }
    val contactsSource = ContactsSource(appContext)
    val notificationStore = NotificationStore()
    // Lazy: only search needs the camera service.
    val torch by lazy { Torch(appContext) }
    val widgetRepository by lazy { WidgetRepository(appContext) }
    val mediaSessions by lazy { MediaSessions(appContext) }
    val audioRoutes by lazy { AudioRoutes(appContext) }
    val usageRepository by lazy { UsageRepository(appContext) }
    val calendarSource by lazy { CalendarSource(appContext) }
    val weatherRepository by lazy { WeatherRepository(appContext) }
}

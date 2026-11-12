package dev.slate.launcher.widgets

import android.app.Activity
import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import dev.slate.launcher.data.SettingsRepository
import dev.slate.launcher.data.WidgetRef
import dev.slate.launcher.data.Widgets
import kotlinx.coroutines.launch

/**
 * Walks a new app widget through the platform's steps: allocate an id, bind it (asking the user
 * with the system dialog if Slate isn't allowed yet), run the widget's configuration screen if
 * it has one, then place it. Any refusal or cancel deletes the id again.
 *
 * Configuration has to go through [android.appwidget.AppWidgetHost.startAppWidgetConfigureActivityForResult]
 * (the config activity is usually not exported), which reports back via [Activity.onActivityResult],
 * so the activity forwards that here.
 */
class WidgetBinder(
    private val activity: ComponentActivity,
    private val repository: WidgetRepository,
    private val settings: SettingsRepository,
) {
    private var pendingId = NONE
    /** Key of the pop-up the pending widget goes in; null for the home screen. */
    private var pendingOwner: String? = null

    private val bindLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) configureOrPlace() else cancel()
    }

    init {
        // Survive the process being killed while a system dialog or config screen is up.
        activity.savedStateRegistry.registerSavedStateProvider(STATE_KEY) { bundleOf(STATE_KEY to pendingId, OWNER_KEY to pendingOwner) }
        val restored = activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)
        pendingId = restored?.getInt(STATE_KEY, NONE) ?: NONE
        pendingOwner = restored?.getString(OWNER_KEY)
    }

    fun add(provider: WidgetProvider, popupOwner: String? = null) {
        if (pendingId != NONE) cancel()
        pendingId = repository.allocateId()
        pendingOwner = popupOwner
        if (repository.bindIfAllowed(pendingId, provider)) {
            configureOrPlace()
        } else {
            try {
                bindLauncher.launch(repository.bindPermissionIntent(pendingId, provider))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No bind dialog", e)
                cancel()
            }
        }
    }

    fun reconfigure(appWidgetId: Int) {
        runCatching { repository.host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, REQUEST_RECONFIGURE, null) }
            .onFailure { Log.w(TAG, "Reconfigure failed", it) }
    }

    /** Forwarded from the activity; true if it was ours. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode == REQUEST_RECONFIGURE) return true
        if (requestCode != REQUEST_CONFIGURE) return false
        if (resultCode == Activity.RESULT_OK) place() else cancel()
        return true
    }

    private fun configureOrPlace() {
        val info = repository.info(pendingId) ?: return cancel()
        if (!repository.needsConfigure(info)) return place()
        try {
            repository.host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQUEST_CONFIGURE, null)
        } catch (e: RuntimeException) {
            // ActivityNotFoundException / SecurityException from a broken config activity.
            Log.w(TAG, "Configure failed", e)
            cancel()
        }
    }

    private fun place() {
        val id = pendingId
        pendingId = NONE
        val info = repository.info(id) ?: return
        val height = repository.defaultHeightDp(info)
        val owner = pendingOwner
        pendingOwner = null
        activity.lifecycleScope.launch {
            settings.update { s ->
                if (owner == null) {
                    s.copy(widgets = Widgets.add(s.widgets, WidgetRef.app(id), height))
                } else {
                    val fitted = height.coerceAtMost(Widgets.MAX_POPUP_HEIGHT_DP)
                    s.copy(popupWidgets = Widgets.inPopup(s.popupWidgets, owner) { Widgets.add(it, WidgetRef.app(id), fitted) })
                }
            }
        }
    }

    private fun cancel() {
        if (pendingId != NONE) repository.delete(pendingId)
        pendingId = NONE
        pendingOwner = null
    }

    private companion object {
        const val TAG = "WidgetBinder"
        const val NONE = -1
        const val STATE_KEY = "widget-binder-pending"
        const val OWNER_KEY = "widget-binder-owner"
        const val REQUEST_CONFIGURE = 0x51A1
        const val REQUEST_RECONFIGURE = 0x51A2
    }
}

/** Null in previews and in activities without a widget host. */
val LocalWidgetBinder = staticCompositionLocalOf<WidgetBinder?> { null }

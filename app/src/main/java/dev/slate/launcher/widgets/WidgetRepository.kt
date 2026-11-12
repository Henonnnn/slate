package dev.slate.launcher.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.util.SizeF
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An installable widget, for the picker. */
@Immutable
data class WidgetProvider(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val user: UserHandle,
) {
    val key: String get() = "${info.provider.flattenToShortString()}/${user.hashCode()}"
}

/**
 * App widgets through one [SlateWidgetHost]. Widget ids live in the settings JSON; the host
 * holds the system-side binding. Ids the settings no longer mention are deleted at start-up
 * ([deleteOrphans]) so the system doesn't keep updating widgets nobody sees.
 */
class WidgetRepository(private val context: Context) {
    val host = SlateWidgetHost(context, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    suspend fun providers(): List<WidgetProvider> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        launcherApps.profiles.flatMap { user ->
            runCatching { manager.getInstalledProvidersForProfile(user) }.getOrDefault(emptyList()).map { info ->
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                }.getOrDefault(info.provider.packageName)
                WidgetProvider(info, info.loadLabel(pm)?.trim().orEmpty().ifEmpty { appLabel }, appLabel, user)
            }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, WidgetProvider::appLabel).thenBy(String.CASE_INSENSITIVE_ORDER, WidgetProvider::label))
    }

    /** Preview image, falling back to the widget's icon. */
    suspend fun preview(provider: WidgetProvider, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val density = context.resources.displayMetrics.densityDpi
        val drawable = runCatching { provider.info.loadPreviewImage(context, density) ?: provider.info.loadIcon(context, density) }.getOrNull()
            ?: return@withContext null
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxPx
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxPx
        val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h))
        drawable.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, provider: WidgetProvider): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, provider.user, provider.info.provider, null) }.getOrDefault(false)

    /** System dialog asking the user to let Slate show this provider's widgets. */
    fun bindPermissionIntent(id: Int, provider: WidgetProvider): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.user)

    /** True when the widget wants its configuration screen before it's placed. */
    fun needsConfigure(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
            if (optional) return false
        }
        return true
    }

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    /** Reconfigure, for widgets that allow it after placement. */
    fun isReconfigurable(info: AppWidgetProviderInfo): Boolean =
        info.configure != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0

    fun delete(id: Int) = runCatching { host.deleteAppWidgetId(id) }

    fun deleteOrphans(keep: Set<Int>) {
        host.appWidgetIds.filterNot { it in keep }.forEach { orphan ->
            Log.i(TAG, "Deleting orphaned widget $orphan")
            delete(orphan)
        }
    }

    /** Default height for a new widget, from what the provider asks for. */
    fun defaultHeightDp(info: AppWidgetProviderInfo): Int {
        val density = context.resources.displayMetrics.density
        return (maxOf(info.minHeight, info.minResizeHeight) / density).toInt().coerceAtLeast(110)
    }

    fun providerLabel(info: AppWidgetProviderInfo): String = info.loadLabel(context.packageManager).orEmpty()

    fun startListening() = runCatching { host.startListening() }.onFailure { Log.w(TAG, "startListening failed", it) }

    fun stopListening() = runCatching { host.stopListening() }

    companion object {
        private const val TAG = "WidgetRepository"
        const val HOST_ID = 0x51A7E

        /** Tells the widget the size it actually gets, so it can pick a matching layout. */
        fun updateSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
            }
        }
    }
}

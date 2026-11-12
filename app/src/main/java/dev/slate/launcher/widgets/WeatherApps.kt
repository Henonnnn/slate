package dev.slate.launcher.widgets

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The weather app the temperature under the clock opens: the user's pick, or the best guess. */
object WeatherApps {
    // Intent.CATEGORY_APP_WEATHER, a constant only from API 33; older versions just match nothing.
    private const val CATEGORY_APP_WEATHER = "android.intent.category.APP_WEATHER"
    private val WORDS = listOf("weather", "meteo", "météo", "wetter", "clima", "tiempo", "forecast")

    /**
     * Installed apps that look like weather apps, by package, with their names: those that answer
     * Android's weather category, and those with "weather" (or its like) in their name or package.
     */
    suspend fun candidates(context: Context): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val declared = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_APP_WEATHER), 0) }
            .getOrDefault(emptyList()).map { it.activityInfo.packageName }.toSet()
        runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0) }
            .getOrDefault(emptyList())
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { (pkg, label) -> pkg in declared || WORDS.any { label.contains(it, ignoreCase = true) || pkg.contains(it, ignoreCase = true) } }
            .sortedBy { it.second.lowercase() }
    }

    /** Opens [chosen], or with none chosen the first candidate. False when there's nothing to open. */
    suspend fun open(context: Context, chosen: String?): Boolean {
        val pm = context.packageManager
        val target = chosen?.takeIf { pm.getLaunchIntentForPackage(it) != null } ?: candidates(context).firstOrNull()?.first ?: return false
        val launch = pm.getLaunchIntentForPackage(target) ?: return false
        return runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

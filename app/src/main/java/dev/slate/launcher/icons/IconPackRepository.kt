package dev.slate.launcher.icons

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import androidx.compose.runtime.Immutable
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class IconPackInfo(val packageName: String, val label: String)

/**
 * A loaded ADW/Nova-format icon pack. Drawables are looked up by name, which is how the format
 * works (there are no resource ids to reference), hence getIdentifier.
 */
class IconPack(
    val packageName: String,
    val versionCode: Long,
    private val resources: Resources,
    val filter: AppFilter,
    private val drawableNames: () -> List<String>,
) {
    /** Every icon the pack offers, for the Icon Assistant picker. */
    val allDrawables: List<String> by lazy { drawableNames().ifEmpty { filter.components.values.distinct().sorted() } }

    @SuppressLint("DiscouragedApi")
    fun drawable(name: String, density: Int): Drawable? {
        val id = resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
            ?: resources.getIdentifier(name, "mipmap", packageName).takeIf { it != 0 }
            ?: return null
        return runCatching { resources.getDrawableForDensity(id, density, null) }.getOrNull()
    }

    fun forComponent(packageName: String, className: String, density: Int): Drawable? =
        filter.drawableFor(packageName, className)?.let { drawable(it, density) }
}

/** Finds installed icon packs and loads them (parsed once per pack version, then kept). */
class IconPackRepository(private val context: Context) {
    private val loaded = ConcurrentHashMap<String, IconPack>()

    /** Packs announce themselves with one of these launcher-theme intents. Call off the main thread. */
    fun installed(): List<IconPackInfo> {
        val pm = context.packageManager
        val intents = THEME_ACTIONS.map { Intent(it) } + Intent(Intent.ACTION_MAIN).addCategory(THEME_CATEGORY)
        return intents.flatMap { intent -> runCatching { pm.queryIntentActivities(intent, PackageManager.GET_META_DATA) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map { IconPackInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /** Null if the pack is gone or unreadable. Call off the main thread. */
    fun load(packageName: String): IconPack? {
        val pm = context.packageManager
        val version = runCatching { pm.getPackageInfo(packageName, 0).longVersionCode }.getOrNull() ?: return null
        loaded[packageName]?.takeIf { it.versionCode == version }?.let { return it }
        val resources = try {
            pm.getResourcesForApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val filter = AppFilter.parse(readXml(resources, packageName, "appfilter"))
        val pack = IconPack(packageName, version, resources, filter) {
            AppFilter.parseDrawableList(readXml(resources, packageName, "drawable"))
        }
        loaded[packageName] = pack
        return pack
    }

    fun invalidate(packageName: String) {
        loaded.remove(packageName)
    }

    /** Start tags from res/xml/<name>.xml, falling back to assets/<name>.xml. */
    @SuppressLint("DiscouragedApi")
    private fun readXml(resources: Resources, packageName: String, name: String): Sequence<AppFilter.Companion.Tag> {
        val tags = mutableListOf<AppFilter.Companion.Tag>()
        try {
            val id = resources.getIdentifier(name, "xml", packageName)
            val parser: XmlPullParser = if (id != 0) {
                resources.getXml(id)
            } else {
                Xml.newPullParser().apply { setInput(resources.assets.open("$name.xml"), null) }
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val attrs = (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
                    tags += AppFilter.Companion.Tag(parser.name, attrs)
                }
                event = parser.next()
            }
        } catch (e: IOException) {
            // No such file in this pack; fine for drawable.xml.
        } catch (e: org.xmlpull.v1.XmlPullParserException) {
            Log.w(TAG, "Malformed $name.xml in $packageName", e)
        }
        return tags.asSequence()
    }

    private companion object {
        const val TAG = "IconPacks"
        val THEME_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
        )
        const val THEME_CATEGORY = "com.fede.launcher.THEME_ICONPACK"
    }
}

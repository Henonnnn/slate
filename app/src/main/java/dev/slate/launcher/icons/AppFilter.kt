package dev.slate.launcher.icons

/**
 * The ADW/Nova icon-pack format: `appfilter.xml` maps activities to drawable names and may
 * describe a fallback treatment (back plate, mask, overlay, scale) for apps the pack doesn't
 * cover. `drawable.xml`, when present, lists every icon in the pack for manual picking.
 *
 * Parsing works on start-tag events (name + attributes) so it's independent of whether the
 * XML came from compiled resources or assets, and can be unit-tested without Android.
 */
data class AppFilter(
    /** "pkg/fully.qualified.Activity" → drawable name. */
    val components: Map<String, String>,
    val backs: List<String>,
    val mask: String?,
    val upon: String?,
    val scale: Float,
) {
    fun drawableFor(packageName: String, className: String): String? = components["$packageName/$className"]

    companion object {
        data class Tag(val name: String, val attrs: Map<String, String>)

        fun parse(tags: Sequence<Tag>): AppFilter {
            val components = LinkedHashMap<String, String>()
            val backs = mutableListOf<String>()
            var mask: String? = null
            var upon: String? = null
            var scale = 1f
            for (tag in tags) {
                when (tag.name) {
                    "item" -> {
                        val component = tag.attrs["component"]?.let(::parseComponent) ?: continue
                        val drawable = tag.attrs["drawable"]?.takeIf { it.isNotBlank() } ?: continue
                        components.putIfAbsent(component, drawable)
                    }
                    "iconback" -> tag.attrs.entries.filter { it.key.startsWith("img") }.sortedBy { it.key }.mapTo(backs) { it.value }
                    "iconmask" -> mask = tag.attrs["img1"] ?: tag.attrs["img"]
                    "iconupon" -> upon = tag.attrs["img1"] ?: tag.attrs["img"]
                    "scale" -> scale = tag.attrs["factor"]?.toFloatOrNull()?.takeIf { it in 0.1f..2f } ?: scale
                }
            }
            return AppFilter(components, backs, mask, upon, scale)
        }

        /** Names listed in `drawable.xml` (`<item drawable="…"/>`), de-duplicated, in order. */
        fun parseDrawableList(tags: Sequence<Tag>): List<String> =
            tags.filter { it.name == "item" }.mapNotNull { it.attrs["drawable"]?.takeIf(String::isNotBlank) }.distinct().toList()

        /**
         * "ComponentInfo{com.app/com.app.Main}" → "com.app/com.app.Main". Relative class names
         * (".Main") are expanded against the package.
         */
        fun parseComponent(raw: String): String? {
            val inner = raw.trim().removePrefix("ComponentInfo{").removeSuffix("}")
            val slash = inner.indexOf('/')
            if (slash <= 0 || slash == inner.lastIndex) return null
            val pkg = inner.substring(0, slash)
            val cls = inner.substring(slash + 1)
            return "$pkg/${if (cls.startsWith(".")) pkg + cls else cls}"
        }
    }
}

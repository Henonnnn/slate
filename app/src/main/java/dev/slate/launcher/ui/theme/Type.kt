package dev.slate.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import dev.slate.launcher.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.slate.launcher.data.FontChoice

/** Soft shadow so wallpaper-borne text stays legible on busy images. */
private val WallpaperShadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 1f), blurRadius = 6f)
/** Under dark text the halo is light instead. */
private val WallpaperGlow = Shadow(Color.White.copy(alpha = 0.35f), Offset(0f, 0f), blurRadius = 6f)

/** Condensed exists on every Android device under this system family name, in several weights. */
private val Condensed = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Light),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Medium),
)

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resource: Int, weights: List<FontWeight>) = FontFamily(
    weights.map { Font(resource, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight))) },
)

private val AllWeights = listOf(FontWeight.Thin, FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.Bold)
private val GoogleSans = variableFamily(R.font.google_sans_flex, AllWeights)
private val KumbhSans = variableFamily(R.font.kumbh_sans, AllWeights)

fun fontFamilyFor(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.Google -> GoogleSans
    FontChoice.Kumbh -> KumbhSans
    FontChoice.Sans -> FontFamily.Default
    FontChoice.Condensed -> Condensed
    FontChoice.Serif -> FontFamily.Serif
    FontChoice.Mono -> FontFamily.Monospace
}

/**
 * Text styles for content on the wallpaper, built from the chosen font and size. Labels and
 * the clock follow [scale]; small print (section labels, scrubber) stays put so it never gets
 * cramped or shouty.
 */
@Immutable
class SlateTextStyles(family: FontFamily = FontFamily.Default, scale: Float = 1f, darkText: Boolean = false) {
    private val halo = if (darkText) WallpaperGlow else WallpaperShadow
    val clock = TextStyle(fontFamily = family, fontWeight = FontWeight.Light, fontSize = (64 * scale).sp, letterSpacing = (-0.02).em, shadow = halo)
    val date = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 0.01.em, shadow = halo)
    val appLabel = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = (20 * scale).sp, letterSpacing = (-0.01).em, shadow = halo)
    val sectionLabel = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.14.em, shadow = halo)
    val hint = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, shadow = halo)
    val scrubberLetter = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp, shadow = halo)
    val bubbleLetter = TextStyle(fontFamily = family, fontWeight = FontWeight.Light, fontSize = 34.sp)
}

val LocalSlateText = staticCompositionLocalOf { SlateTextStyles() }

/** Current wallpaper text styles; reads like the old object so call sites stay `SlateText.appLabel`. */
val SlateText: SlateTextStyles
    @Composable @ReadOnlyComposable get() = LocalSlateText.current

fun slateTypography(family: FontFamily) = Typography(
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

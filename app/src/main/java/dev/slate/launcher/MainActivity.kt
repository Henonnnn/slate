package dev.slate.launcher

import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import dev.slate.launcher.ui.theme.wallpaperSupportsDarkText
import dev.slate.launcher.data.TextTone
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.slate.launcher.data.AccentChoice
import dev.slate.launcher.icons.IconAppearance
import dev.slate.launcher.icons.LocalIconAppearance
import dev.slate.launcher.ui.theme.Accent
import dev.slate.launcher.ui.theme.wallpaperPrimaryColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import dev.slate.launcher.ui.home.HomeRoute
import dev.slate.launcher.ui.theme.SlateTheme
import dev.slate.launcher.widgets.LocalWidgetBinder
import dev.slate.launcher.widgets.WidgetBinder
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var widgetBinder: WidgetBinder

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        FrameStats.attachIfEnabled(this)
        widgetBinder = WidgetBinder(this, container.widgetRepository, container.settingsRepository)
        val looks = container.settingsRepository.settings.map { it.theme to it.iconOverrides }.distinctUntilChanged()
        setContent {
            // Nothing until the stored theme is known (a few ms), so the default look never flashes.
            val (theme, overrides) = looks.collectAsStateWithLifecycle(null).value ?: return@setContent
            val wallpaperColor = if (theme.accent == AccentChoice.Wallpaper) {
                remember { wallpaperPrimaryColor(this@MainActivity) }.collectAsStateWithLifecycle(null).value
            } else {
                null
            }
            val icons = remember(theme, overrides, wallpaperColor) {
                IconAppearance(
                    style = theme.iconStyle,
                    pack = theme.iconPack,
                    shape = theme.iconShape,
                    themedTint = Accent.resolve(theme.accent, wallpaperColor, theme.accentHue),
                    overrides = overrides,
                ).also { container.iconCache.appearance = it } // before any icon below composes
            }
            val darkText = if (theme.wallpaperText == TextTone.Auto) {
                remember { wallpaperSupportsDarkText(this@MainActivity) }.collectAsStateWithLifecycle(false).value
            } else {
                false
            }
            val behaviour = remember { container.settingsRepository.settings.map { it.behaviour }.distinctUntilChanged() }
                .collectAsStateWithLifecycle(null).value
            val view = LocalView.current
            // One switch for every tick Slate makes: they all go through this view.
            SideEffect { view.isHapticFeedbackEnabled = behaviour?.haptics != false }
            LaunchedEffect(behaviour?.hideStatusBar) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (behaviour?.hideStatusBar == true) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
            }
            SlateTheme(theme, wallpaperColor, wallpaperDarkText = darkText) {
                CompositionLocalProvider(LocalWidgetBinder provides widgetBinder, LocalIconAppearance provides icons) {
                    HomeRoute(homePresses)
                }
            }
        }
    }

    // Widgets only get updates while the host listens; stop when Slate isn't visible.
    override fun onStart() {
        super.onStart()
        container.widgetRepository.startListening()
    }

    override fun onStop() {
        super.onStop()
        container.widgetRepository.stopListening()
    }

    @Deprecated("AppWidgetHost's configure flow still reports through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgetBinder.onActivityResult(requestCode, resultCode)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Coming back from another app keeps the scroll position; pressing home
        // while already on home (window focused, not brought to front) resets it.
        val isHome = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
        val alreadyOnTop = hasWindowFocus() && (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0
        if (isHome && alreadyOnTop) homePresses.tryEmit(Unit)
    }
}

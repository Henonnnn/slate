package dev.slate.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Line icons drawn on a 24-unit grid, one family for settings, the alphabet and the home header. */
enum class SlateIcon(private val data: String) {
    Star("M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9-5.2-2.8-5.2 2.8 1-5.9-4.3-4.1 5.9-.8z"),
    Shield("M12 3l7.5 2.8v5.4c0 4.6-3.1 8-7.5 9.8-4.4-1.8-7.5-5.2-7.5-9.8V5.8z"),
    ShieldCheck("M12 3l7.5 2.8v5.4c0 4.6-3.1 8-7.5 9.8-4.4-1.8-7.5-5.2-7.5-9.8V5.8zM8.5 12l2.5 2.5 4.5-5"),
    Droplet("M12 3.5c3.5 4 6 7 6 10.2a6 6 0 0 1-12 0C6 10.5 8.5 7.5 12 3.5z"),
    Clock("M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17zM12 7.5V12l3 2"),
    Home("M4 11l8-7 8 7M6 9.5V20h4.5v-5.5h3V20H18V9.5"),
    SwipeRight("M4 12h15M14 7l5 5-5 5"),
    SwipeLeft("M20 12H5M10 7l-5 5 5 5"),
    PullDown("M12 4v12M7 11l5 5 5-5M5 20h14"),
    Hourglass("M7 3.5h10M7 20.5h10M8 3.5c0 4 8 5.5 8 8.5s-8 4.5-8 8.5M16 3.5c0 4-8 5.5-8 8.5s8 4.5 8 8.5"),
    Save("M12 15V4M7.5 8.5L12 4l4.5 4.5M4.5 14.5V19a1 1 0 0 0 1 1h13a1 1 0 0 0 1-1v-4.5"),
    Restore("M12 4v11M7.5 10.5L12 15l4.5-4.5M4.5 14.5V19a1 1 0 0 0 1 1h13a1 1 0 0 0 1-1v-4.5"),
    Info("M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17zM12 11v5.5M12 7.8v.2"),
    Move("M12 3.5v17M8 7.5l4-4 4 4M8 16.5l4 4 4-4"),
    Alphabet("M5 19L10 5h1l5 14M7 14h7"),
    Search("M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13zM15.5 15.5L20 20"),
    Eye("M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"),
    Lock("M7 11V8a5 5 0 0 1 10 0v3M5.5 11h13v9h-13z"),
    Reorder("M4 7h16M4 12h16M4 17h16"),
    ChevronDown("M6 9.5l6 6 6-6"),
    Diamond("M12 4l8 8-8 8-8-8z"),
    Folder("M4 6.5h6l2 2.5h8v9.5H4z"),
    Send("M4.5 12L20 4.5 13.5 20l-2.5-6.5z"),
    Message("M4.5 5.5h15v11h-9l-4 3.5v-3.5h-2z"),
    Calendar("M4.5 6.5h15v14h-15zM4.5 10.5h15M8.5 3.5v5M15.5 3.5v5"),
    Bell("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15zM10 20.5h4"),
    Battery("M4 8h14v8H4zM20.5 10.5v3"),
    Charging("M4 8h14v8H4zM20.5 10.5v3M12 9.5l-2.5 3h3L10 15"),
    Contacts("M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM5 20c.8-3.8 3.5-6 7-6s6.2 2.2 7 6"),
    Pin("M12 21s6.5-6 6.5-11a6.5 6.5 0 0 0-13 0c0 5 6.5 11 6.5 11zM12 7.8a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0-4.4z"),
    Network("M3.5 9.5a12.5 12.5 0 0 1 17 0M6.5 13a8 8 0 0 1 11 0M9.5 16.5a3.6 3.6 0 0 1 5 0M12 19.8v.2"),
    Sun("M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4"),
    Cloud("M7 18.5a4 4 0 0 1-.6-8 5.5 5.5 0 0 1 10.6-1 4.5 4.5 0 0 1 .5 9z"),
    Rain("M7 15a4 4 0 0 1-.6-8 5.5 5.5 0 0 1 10.6-1 4.5 4.5 0 0 1 .5 9zM8.5 18l-1 2.5M12.5 18l-1 2.5M16.5 18l-1 2.5"),
    OpenExternal("M14 4h6v6M20 4l-9.5 9.5M18 13.5V19a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5.5"),
    Torch("M8 3.5h8v3l-2 3.5v10.5h-4V10L8 6.5zM8 6.5h8M12 13v2.5"),
    Note("M9 18V5.5l10-2V16M9 18a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0zM19 16a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0z"),
    ChevronRight("M9.5 6l6 6-6 6"),
    Snow("M7 15a4 4 0 0 1-.6-8 5.5 5.5 0 0 1 10.6-1 4.5 4.5 0 0 1 .5 9zM8 19v.2M12 18v.2M16 19v.2M10 21.5v.2M14 21.5v.2"),
    ;

    val path: Path by lazy { PathParser().parsePathString(data).toPath() }
}

/** Draws [icon] with its top-left at [at], [sizePx] wide. A null [strokePx] fills it. */
fun DrawScope.drawSlateIcon(icon: SlateIcon, color: Color, sizePx: Float, at: Offset = Offset.Zero, strokePx: Float? = null, alpha: Float = 1f) {
    val units = sizePx / 24f
    translate(at.x, at.y) {
        scale(units, pivot = Offset.Zero) {
            drawPath(
                icon.path,
                color,
                alpha = alpha,
                style = strokePx?.let { Stroke(it / units, cap = StrokeCap.Round, join = StrokeJoin.Round) } ?: Fill,
            )
        }
    }
}

@Composable
fun SlateIconView(icon: SlateIcon, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) { drawSlateIcon(icon, color, this.size.width, strokePx = (size / 14).toPx()) }
}

package dev.slate.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Small hand-drawn glyphs, so the app doesn't depend on the large icon library. */

@Composable
fun DragHandleGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = 1.75.dp.toPx()
        val inset = size.width * 0.22f
        listOf(0.36f, 0.5f, 0.64f).forEach { y ->
            drawLine(color, Offset(inset, size.height * y), Offset(size.width - inset, size.height * y), stroke, StrokeCap.Round)
        }
    }
}

@Composable
fun RemoveGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = 1.75.dp.toPx()
        drawCircle(color, radius = size.minDimension * 0.38f, style = Stroke(stroke))
        val half = size.width * 0.17f
        drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), stroke, StrokeCap.Round)
    }
}

@Composable
fun ClearGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = 1.75.dp.toPx()
        val half = size.width * 0.2f
        drawLine(color, Offset(center.x - half, center.y - half), Offset(center.x + half, center.y + half), stroke, StrokeCap.Round)
        drawLine(color, Offset(center.x - half, center.y + half), Offset(center.x + half, center.y - half), stroke, StrokeCap.Round)
    }
}

@Composable
fun SearchGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = 1.75.dp.toPx()
        val r = size.minDimension * 0.24f
        val c = Offset(size.width * 0.44f, size.height * 0.44f)
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        val d = r * 0.7071f
        drawLine(color, Offset(c.x + d, c.y + d), Offset(size.width * 0.78f, size.height * 0.78f), stroke, StrokeCap.Round)
    }
}

@Composable
fun CheckGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        drawLine(color, Offset(w * 0.24f, w * 0.52f), Offset(w * 0.42f, w * 0.7f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, w * 0.7f), Offset(w * 0.78f, w * 0.32f), stroke, StrokeCap.Round)
    }
}

/** Small chevron after a label: this row opens a pop-up when swiped. Points to the end edge. */
@Composable
fun PopupHintGlyph(color: Color, modifier: Modifier = Modifier) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Canvas(modifier.size(14.dp)) {
        val stroke = 1.5.dp.toPx()
        val w = size.width
        val (tip, back) = if (rtl) w * 0.32f to w * 0.6f else w * 0.68f to w * 0.4f
        drawLine(color, Offset(back, w * 0.22f), Offset(tip, w * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(tip, w * 0.5f), Offset(back, w * 0.78f), stroke, StrokeCap.Round)
    }
}

@Composable
fun PlayGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.34f, w * 0.24f)
            lineTo(w * 0.78f, w * 0.5f)
            lineTo(w * 0.34f, w * 0.76f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun PauseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val stroke = w * 0.12f
        drawLine(color, Offset(w * 0.37f, w * 0.27f), Offset(w * 0.37f, w * 0.73f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.63f, w * 0.27f), Offset(w * 0.63f, w * 0.73f), stroke, StrokeCap.Round)
    }
}

@Composable
fun NextGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.28f, w * 0.27f)
            lineTo(w * 0.62f, w * 0.5f)
            lineTo(w * 0.28f, w * 0.73f)
            close()
        }
        drawPath(path, color)
        drawLine(color, Offset(w * 0.7f, w * 0.27f), Offset(w * 0.7f, w * 0.73f), w * 0.09f, StrokeCap.Round)
    }
}

@Composable
fun PreviousGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.72f, w * 0.27f)
            lineTo(w * 0.38f, w * 0.5f)
            lineTo(w * 0.72f, w * 0.73f)
            close()
        }
        drawPath(path, color)
        drawLine(color, Offset(w * 0.3f, w * 0.27f), Offset(w * 0.3f, w * 0.73f), w * 0.09f, StrokeCap.Round)
    }
}

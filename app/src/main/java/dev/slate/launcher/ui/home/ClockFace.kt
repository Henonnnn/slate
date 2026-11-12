package dev.slate.launcher.ui.home

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.slate.launcher.data.ClockAlign
import dev.slate.launcher.data.ClockColour
import dev.slate.launcher.data.ClockDate
import dev.slate.launcher.data.ClockSettings
import dev.slate.launcher.data.ClockStyle
import dev.slate.launcher.data.ClockWeight
import dev.slate.launcher.data.FontChoice
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import dev.slate.launcher.ui.theme.fontFamilyFor
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

private val ClassicSize = 48.dp
private val SplitHours = 60.dp
private val SplitMinutes = 42.dp
private val SplitGap = 4.dp
private val SplitDrop = 4.dp
private val GridSize = 90.dp
private val GridGap = 10.dp
private val OverlapSize = 96.dp
private val OverlapCutout = 10.dp
private const val OVERLAP_PAIR = 0.06f
private const val OVERLAP_DIVIDER = 0.2f
private val FlipCard = 118.dp
private val FlipGap = 10.dp
private val FlipCorner = 22.dp
private val FlipDivider = 3.dp
private val FlipPadding = 10.dp
private val MinimalSize = 90.dp
private val MinimalBandText = 14.dp
private val MinimalBandPadding = 6.dp
private val AnalogSize = 128.dp
private val AnalogFace = Color(0xFF1B1A18)
private val ClockShadow = 0x55000000

fun ClockSettings.fontFamily(): FontFamily = fontFamilyFor(font ?: FontChoice.Google)

/** The real typeface behind the chosen font and weight, for drawing digits as shapes. */
@Composable
private fun rememberTypeface(clock: ClockSettings): Typeface {
    val resolver = LocalFontFamilyResolver.current
    return resolver.resolve(clock.fontFamily(), clock.weight.fontWeight()).value as Typeface
}

private fun clockPaint(typeface: Typeface, sizePx: Float, colour: Color, shadow: Boolean = true) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.typeface = typeface
    textSize = sizePx
    color = colour.toArgb()
    if (shadow) setShadowLayer(sizePx * 0.03f, 0f, sizePx * 0.012f, ClockShadow)
}

/** A clock drawn with the platform's own canvas, wrapped to [width] by [height]. */
@Composable
private fun PaintedClock(width: Dp, height: Dp, description: String, modifier: Modifier, draw: (android.graphics.Canvas) -> Unit) {
    Canvas(modifier.size(width, height).semantics { contentDescription = description }) {
        drawIntoCanvas { draw(it.nativeCanvas) }
    }
}

/** Widest digit in [paint], so tabular layouts don't jump as the time changes. */
private fun digitWidth(paint: Paint): Float = "0123456789".maxOf { paint.measureText(it.toString()) }

private fun digitHeight(paint: Paint): Float {
    val bounds = android.graphics.Rect()
    paint.getTextBounds("0", 0, 1, bounds)
    return bounds.height().toFloat()
}

/** The time, drawn in the chosen style. Null [time] keeps the size with nothing drawn. */
@Composable
fun ClockFace(clock: ClockSettings, time: ZonedDateTime?, formats: ClockFormats, modifier: Modifier = Modifier) {
    val palette = LocalSlatePalette.current
    val density = LocalDensity.current
    val typeface = rememberTypeface(clock)
    val hourColour = if (clock.colour == ClockColour.Accent) palette.accent else palette.onWallpaper
    val minuteColour = if (clock.colour == ClockColour.Text) palette.onWallpaper else palette.accent
    val textAlign = when (clock.align) {
        ClockAlign.Start -> TextAlign.Start
        ClockAlign.Center -> TextAlign.Center
        ClockAlign.End -> TextAlign.End
    }
    val digits = SlateText.clock.copy(
        fontFamily = clock.fontFamily(),
        fontWeight = clock.weight.fontWeight(),
        fontSize = with(density) { (ClassicSize * clock.size).toSp() },
        letterSpacing = 0.em,
        textAlign = textAlign,
    )
    val hours = time?.let(formats::paddedHours).orEmpty()
    val minutes = time?.let(formats::minutes).orEmpty()
    val description = time?.let(formats::plainTime).orEmpty()
    when (clock.style) {
        ClockStyle.Classic -> Text(
            text = time?.let { formats.classic(it, clock.showAmPm, palette.onWallpaperMuted, minuteColour) } ?: AnnotatedString(""),
            style = digits,
            color = hourColour,
            modifier = modifier,
        )
        ClockStyle.Split -> SplitClock(hours, minutes, typeface, clock.size, hourColour, minuteColour, description, modifier)
        ClockStyle.Stacked -> GridClock(hours, minutes, typeface, clock.size, hourColour, minuteColour, description, modifier)
        ClockStyle.Overlap -> OverlapClock(hours, minutes, typeface, clock.size, hourColour, if (clock.colour == ClockColour.Text) palette.accent else minuteColour, description, modifier)
        ClockStyle.Flip -> CutoutCards(hours, minutes, typeface, clock.size, hourColour, minuteColour, description, modifier)
        ClockStyle.Minimal -> MinimalClock(description, time?.let { t -> formats.date?.format(t) }.orEmpty(), typeface, clock.size, hourColour, description, modifier)
        ClockStyle.Day -> Column(modifier, horizontalAlignment = clock.align.horizontal()) {
            Text(time?.let(formats.weekday::format).orEmpty(), style = digits, color = hourColour)
            Text(
                text = time?.let { formats.classic(it, clock.showAmPm, palette.onWallpaperMuted, minuteColour) } ?: AnnotatedString(""),
                style = SlateText.date.copy(fontFamily = clock.fontFamily(), fontWeight = FontWeight.Medium, fontSize = SlateText.date.fontSize * clock.size),
                color = palette.onWallpaper,
            )
        }
        ClockStyle.Analog -> AnalogClock(time, clock, description, modifier)
        ClockStyle.Glance, ClockStyle.None -> Unit
    }
}

fun ClockAlign.horizontal(): Alignment.Horizontal = when (this) {
    ClockAlign.Start -> Alignment.Start
    ClockAlign.Center -> Alignment.CenterHorizontally
    ClockAlign.End -> Alignment.End
}

/** Big hours beside smaller minutes, their tops level. */
@Composable
private fun SplitClock(hours: String, minutes: String, typeface: Typeface, scale: Float, hourColour: Color, minuteColour: Color, description: String, modifier: Modifier) {
    val density = LocalDensity.current
    val hourPaint = remember(typeface, scale, hourColour) { clockPaint(typeface, with(density) { (SplitHours * scale).toPx() }, hourColour) }
    val minutePaint = remember(typeface, scale, minuteColour) { clockPaint(typeface, with(density) { (SplitMinutes * scale).toPx() }, minuteColour) }
    val gap = with(density) { (SplitGap * scale).toPx() }
    val drop = with(density) { (SplitDrop * scale).toPx() }
    val hoursWidth = hourPaint.measureText(hours)
    val hoursHeight = digitHeight(hourPaint)
    val width = hoursWidth + gap + minutePaint.measureText(minutes)
    val height = max(hoursHeight, digitHeight(minutePaint) + drop)
    PaintedClock(with(density) { width.toDp() }, with(density) { (height + 8.dp.toPx()).toDp() }, description, modifier) { canvas ->
        canvas.drawText(hours, 0f, hoursHeight, hourPaint)
        canvas.drawText(minutes, hoursWidth + gap, digitHeight(minutePaint) + drop, minutePaint)
    }
}

/** Four digits in a two-by-two grid, each centred in its cell. */
@Composable
private fun GridClock(hours: String, minutes: String, typeface: Typeface, scale: Float, hourColour: Color, minuteColour: Color, description: String, modifier: Modifier) {
    val density = LocalDensity.current
    val hourPaint = remember(typeface, scale, hourColour) { clockPaint(typeface, with(density) { (GridSize * scale).toPx() }, hourColour).apply { textAlign = Paint.Align.CENTER } }
    val minutePaint = remember(typeface, scale, minuteColour) { clockPaint(typeface, with(density) { (GridSize * scale).toPx() }, minuteColour).apply { textAlign = Paint.Align.CENTER } }
    val cell = digitWidth(hourPaint)
    val rowHeight = digitHeight(hourPaint)
    val gap = with(density) { (GridGap * scale).toPx() }
    PaintedClock(with(density) { (cell * 2).toDp() }, with(density) { (rowHeight * 2 + gap + 6.dp.toPx()).toDp() }, description, modifier) { canvas ->
        if (hours.length < 2 || minutes.length < 2) return@PaintedClock
        canvas.drawText(hours, 0, 1, cell / 2, rowHeight, hourPaint)
        canvas.drawText(hours, 1, 2, cell * 1.5f, rowHeight, hourPaint)
        canvas.drawText(minutes, 0, 1, cell / 2, rowHeight * 2 + gap, minutePaint)
        canvas.drawText(minutes, 1, 2, cell * 1.5f, rowHeight * 2 + gap, minutePaint)
    }
}

/** Digits in a row with the minutes biting into the hours: a gap follows the minutes' outline. */
@Composable
private fun OverlapClock(hours: String, minutes: String, typeface: Typeface, scale: Float, hourColour: Color, minuteColour: Color, description: String, modifier: Modifier) {
    val density = LocalDensity.current
    val sizePx = with(density) { (OverlapSize * scale).toPx() }
    val cutout = with(density) { (OverlapCutout * scale).toPx() }
    val hourPaint = remember(typeface, sizePx, hourColour) { clockPaint(typeface, sizePx, hourColour) }
    val minutePaint = remember(typeface, sizePx, minuteColour) { clockPaint(typeface, sizePx, minuteColour) }
    val height = digitHeight(hourPaint)
    val shapes = remember(hours, minutes, typeface, sizePx, height, cutout) {
        val hoursPath = Path()
        val minutesPath = Path()
        var x = 0f
        val glyph = Path()
        (hours + minutes).forEachIndexed { i, c ->
            val text = c.toString()
            hourPaint.getTextPath(text, 0, 1, x, height, glyph)
            (if (i < 2) hoursPath else minutesPath).addPath(glyph)
            val advance = hourPaint.measureText(text)
            x += advance * (1f - if (i == 1) OVERLAP_DIVIDER else OVERLAP_PAIR)
        }
        val outline = Path()
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = cutout
            strokeJoin = Paint.Join.ROUND
        }.getFillPath(minutesPath, outline)
        hoursPath.op(outline, Path.Op.DIFFERENCE)
        hoursPath.op(minutesPath, Path.Op.DIFFERENCE)
        Triple(hoursPath, minutesPath, x + hourPaint.measureText("0") * OVERLAP_PAIR)
    }
    PaintedClock(with(density) { shapes.third.toDp() }, with(density) { (height + 8.dp.toPx()).toDp() }, description, modifier) { canvas ->
        canvas.drawPath(shapes.first, hourPaint)
        canvas.drawPath(shapes.second, minutePaint)
    }
}

/** Two solid cards with the digits and a dividing line cut clean through to the wallpaper. */
@Composable
private fun CutoutCards(hours: String, minutes: String, typeface: Typeface, scale: Float, hourColour: Color, minuteColour: Color, description: String, modifier: Modifier) {
    val density = LocalDensity.current
    val card = with(density) { (FlipCard * scale).toPx() }
    val gap = with(density) { (FlipGap * scale).toPx() }
    val corner = with(density) { (FlipCorner * scale).toPx() }
    val divider = with(density) { (FlipDivider * scale).toPx() }
    val inset = with(density) { (FlipPadding * scale).toPx() }
    val paintH = remember(hourColour) { clockPaint(typeface, 1f, hourColour, shadow = false) }
    val paintM = remember(minuteColour) { clockPaint(typeface, 1f, minuteColour, shadow = false) }
    fun shape(text: String, left: Float): Path {
        val measure = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = card * 0.55f; textAlign = Paint.Align.CENTER }
        // Sized to fill most of the card's height.
        measure.textSize *= (card - inset * 2f) * 0.72f / digitHeight(measure).coerceAtLeast(1f)
        val shapeOut = Path().apply { addRoundRect(RectF(left, 0f, left + card, card), corner, corner, Path.Direction.CW) }
        val digitsPath = Path()
        val digit = Path()
        val height = digitHeight(measure)
        text.forEachIndexed { i, c ->
            // Centred in the card's left and right halves.
            val cx = left + card * (if (i == 0) 0.27f else 0.73f)
            measure.getTextPath(c.toString(), 0, 1, cx, card / 2f + height / 2f, digit)
            digitsPath.addPath(digit)
        }
        shapeOut.op(digitsPath, Path.Op.DIFFERENCE)
        shapeOut.op(Path().apply { addRect(left, card / 2f - divider / 2f, left + card, card / 2f + divider / 2f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        return shapeOut
    }
    val hoursShape = remember(hours, typeface, card) { shape(hours, 0f) }
    val minutesShape = remember(minutes, typeface, card) { shape(minutes, card + gap) }
    PaintedClock(with(density) { (card * 2 + gap).toDp() }, with(density) { (card + 6.dp.toPx()).toDp() }, description, modifier) { canvas ->
        canvas.drawPath(hoursShape, paintH)
        canvas.drawPath(minutesShape, paintM)
    }
}

/** Large digits with a band sliced through their middle, and the date in the band. */
@Composable
private fun MinimalClock(time: String, band: String, typeface: Typeface, scale: Float, colour: Color, description: String, modifier: Modifier) {
    val density = LocalDensity.current
    val sizePx = with(density) { (MinimalSize * scale).toPx() }
    val bandPad = with(density) { (MinimalBandPadding * scale).toPx() }
    val paint = remember(typeface, sizePx, colour) { clockPaint(typeface, sizePx, colour) }
    val small = remember(typeface, scale, colour) {
        clockPaint(typeface, with(density) { (MinimalBandText * scale).toPx() }, colour).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.12f }
    }
    val height = digitHeight(paint)
    val width = paint.measureText(time)
    val bandHeight = digitHeight(small) + bandPad * 2
    val shape = remember(time, typeface, sizePx, bandHeight) {
        Path().apply {
            paint.getTextPath(time, 0, time.length, 0f, height, this)
            op(Path().apply { addRect(-sizePx, height / 2f - bandHeight / 2f, width + sizePx, height / 2f + bandHeight / 2f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        }
    }
    PaintedClock(with(density) { ceil(width).toDp() }, with(density) { (height + 10.dp.toPx()).toDp() }, description, modifier) { canvas ->
        canvas.drawPath(shape, paint)
        canvas.drawText(band.uppercase(), width / 2f, height / 2f + digitHeight(small) / 2f, small)
    }
}

fun ClockWeight.fontWeight(): FontWeight = when (this) {
    ClockWeight.Thin -> FontWeight.Thin
    ClockWeight.Light -> FontWeight.Light
    ClockWeight.Regular -> FontWeight.Normal
    ClockWeight.Bold -> FontWeight.Bold
}

class ClockFormats(locale: Locale, private val is24h: Boolean, date: ClockDate, style: ClockStyle) {
    private val time = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24h) "Hm" else "hm"), locale)
    private val amPm = DateTimeFormatter.ofPattern("a", locale)
    private val hour = DateTimeFormatter.ofPattern(if (is24h) "HH" else "h", locale)
    private val paddedHour = DateTimeFormatter.ofPattern(if (is24h) "HH" else "hh", locale)
    private val minute = DateTimeFormatter.ofPattern("mm", locale)
    val weekday: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", locale)
    /** The line under the clock. Minimal carries its date inside the digits instead. */
    val showsDateLine = style != ClockStyle.Minimal && (date != ClockDate.Hidden || style == ClockStyle.Glance)
    val date: DateTimeFormatter? = when {
        // Glance has no digits, so the date is what it's made of.
        style == ClockStyle.Glance || style == ClockStyle.Minimal -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
        date == ClockDate.Hidden -> null
        // The Day style's headline already names the weekday.
        date == ClockDate.Long -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (style == ClockStyle.Day) "MMMMd" else "EEEEMMMMd"), locale)
        else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (style == ClockStyle.Day) "MMMd" else "EEEMMMd"), locale)
    }

    fun hours(now: ZonedDateTime): String = hour.format(now)

    fun paddedHours(now: ZonedDateTime?): String = now?.let(paddedHour::format).orEmpty()

    fun minutes(now: ZonedDateTime): String = minute.format(now)

    /** Digits and separator only, with any AM/PM marker removed. */
    fun plainTime(now: ZonedDateTime): String = "${hours(now)}:${minutes(now)}"

    /** Shrinks and mutes the AM/PM marker so the digits carry the clock. */
    fun classic(now: ZonedDateTime, showAmPm: Boolean, mutedColor: Color, minuteColor: Color): AnnotatedString {
        val text = time.format(now)
        val marker = if (is24h) null else amPm.format(now)
        val markerAt = marker?.let { text.indexOf(it) } ?: -1
        val digits = if (markerAt >= 0) text.substring(0, markerAt).trimEnd() else text
        val minutes = minutes(now)
        val minutesAt = digits.lastIndexOf(minutes)
        return buildAnnotatedString {
            if (minutesAt >= 0) {
                append(digits.substring(0, minutesAt))
                withStyle(SpanStyle(color = minuteColor)) { append(minutes) }
                append(digits.substring(minutesAt + minutes.length))
            } else {
                append(digits)
            }
            if (showAmPm && marker != null && markerAt >= 0) {
                append(' ')
                withStyle(SpanStyle(fontSize = 0.4.em, color = mutedColor, letterSpacing = 0.02.em)) { append(marker) }
            }
        }
    }
}

/** A solid face with hour ticks, light hands and the day of the month in a box at three o'clock. */
@Composable
private fun AnalogClock(now: ZonedDateTime?, clock: ClockSettings, description: String, modifier: Modifier = Modifier) {
    val palette = LocalSlatePalette.current
    val density = LocalDensity.current
    val typeface = rememberTypeface(clock)
    val minuteHand = if (clock.colour == ClockColour.Text) Color.White else palette.accent
    val hourHand = if (clock.colour == ClockColour.Accent) palette.accent else Color.White
    val strokeScale = when (clock.weight) {
        ClockWeight.Thin -> 0.7f
        ClockWeight.Light -> 0.85f
        ClockWeight.Regular -> 1f
        ClockWeight.Bold -> 1.5f
    }
    val datePaint = remember(typeface, clock.size) {
        clockPaint(typeface, with(density) { (AnalogSize * clock.size * 0.11f).toPx() }, Color.White, shadow = false).apply { textAlign = Paint.Align.CENTER }
    }
    Canvas(modifier.padding(bottom = 10.dp).size(AnalogSize * clock.size).semantics { contentDescription = description }) {
        val radius = size.minDimension / 2
        val stroke = radius * 0.035f * strokeScale
        drawCircle(AnalogFace, radius)
        for (hour in 0 until 12) {
            rotate(hour * 30f) {
                drawLine(
                    Color.White.copy(alpha = if (hour % 3 == 0) 0.9f else 0.45f),
                    Offset(center.x, center.y - radius * 0.9f),
                    Offset(center.x, center.y - radius * 0.8f),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
        if (now == null) return@Canvas
        val day = now.dayOfMonth.toString()
        val boxX = center.x + radius * 0.5f
        drawRoundRect(
            Color.White.copy(alpha = 0.14f),
            topLeft = Offset(boxX - radius * 0.2f, center.y - radius * 0.16f),
            size = androidx.compose.ui.geometry.Size(radius * 0.4f, radius * 0.32f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.08f),
        )
        drawIntoCanvas { it.nativeCanvas.drawText(day, boxX, center.y + digitHeight(datePaint) / 2f, datePaint) }
        rotate((now.hour % 12 + now.minute / 60f) * 30f) {
            drawLine(hourHand, center, Offset(center.x, center.y - radius * 0.5f), stroke * 2.2f, StrokeCap.Round)
        }
        rotate(now.minute * 6f) {
            drawLine(minuteHand, center, Offset(center.x, center.y - radius * 0.76f), stroke * 1.4f, StrokeCap.Round)
        }
        drawCircle(palette.accent, stroke * 1.6f)
    }
}

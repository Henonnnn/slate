package dev.slate.launcher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ClockStyle {
    /** Time on one line. */
    @SerialName("classic") Classic,
    /** No digits: the date is the headline, with the at-a-glance details around it. */
    @SerialName("glance") Glance,
    /** Big hours beside smaller minutes, tops aligned. */
    @SerialName("split") Split,
    /** A two-by-two grid of digits. */
    @SerialName("stacked") Stacked,
    /** Large digits in a row, the minutes cutting into the hours. */
    @SerialName("overlap") Overlap,
    /** Two solid cards with the digits cut out of them. */
    @SerialName("flip") Flip,
    /** Large digits sliced by a band holding the date. */
    @SerialName("outline") Minimal,
    /** The weekday as the headline, the time beneath it. */
    @SerialName("day") Day,
    @SerialName("analog") Analog,
    /** No clock; the date, if shown, stays. */
    @SerialName("none") None,
}

@Serializable
enum class ClockAlign {
    @SerialName("start") Start,
    @SerialName("center") Center,
    @SerialName("end") End,
}

@Serializable
enum class ClockWeight {
    @SerialName("thin") Thin,
    @SerialName("light") Light,
    @SerialName("regular") Regular,
    @SerialName("bold") Bold,
}

@Serializable
enum class ClockColour {
    @SerialName("text") Text,
    @SerialName("accent") Accent,
    /** Hours in the text colour, minutes in the accent. */
    @SerialName("two_tone") TwoTone,
}

@Serializable
enum class ClockDate {
    @SerialName("long") Long,
    @SerialName("short") Short,
    @SerialName("hidden") Hidden,
}

/** The home clock. Kept apart from [ThemeSettings] so applying a preset leaves it alone. */
@Serializable
data class ClockSettings(
    val style: ClockStyle = ClockStyle.Classic,
    val align: ClockAlign = ClockAlign.Start,
    val weight: ClockWeight = ClockWeight.Regular,
    val colour: ClockColour = ClockColour.Text,
    /** Null is the default, [FontChoice.Google]. */
    val font: FontChoice? = null,
    /** Multiplier on top of the theme's text size, [MIN_SIZE]–[MAX_SIZE]. */
    val size: Float = 1f,
    val date: ClockDate = ClockDate.Long,
    /** Only matters on a 12-hour phone. */
    val showAmPm: Boolean = true,
    /** At a glance: what shows with the date, each only when it has something to say. */
    val showWeather: Boolean = true,
    val showEvents: Boolean = true,
    val showAlarm: Boolean = true,
    val showBattery: Boolean = false,
) {
    fun sanitized(): ClockSettings = copy(size = if (size.isNaN()) 1f else size.coerceIn(MIN_SIZE, MAX_SIZE))

    companion object {
        const val MIN_SIZE = 0.7f
        const val MAX_SIZE = 1.6f
    }
}

package dev.slate.launcher.search

/**
 * "5 km in mi", "72f to c", "1.5 GB = MiB", "(2+3) kg -> lb".
 *
 * Offline only: there is no currency conversion, because exchange rates need the network
 * and the only network call this app may make is the optional weather fetch.
 */
object UnitConverter {

    data class Result(
        val amount: Double,
        val from: UnitDef,
        val value: Double,
        val to: UnitDef,
    ) {
        val text: String get() = "${Calculator.format(value, significant = 7)} ${to.symbol}"
        val source: String get() = "${Calculator.format(amount, significant = 7)} ${from.symbol}"
    }

    enum class Dimension { Length, Mass, Temperature, Volume, Area, Speed, Time, Data, Energy }

    /**
     * Linear units are `value * factor` in the dimension's base unit; temperature uses
     * [offset] too (base is kelvin: K = value * factor + offset).
     */
    data class UnitDef(
        val symbol: String,
        val dimension: Dimension,
        val factor: Double,
        val offset: Double = 0.0,
        /** Matched exactly first (MB vs Mb), then case-insensitively. */
        val aliases: List<String>,
    ) {
        fun toBase(v: Double) = v * factor + offset
        fun fromBase(v: Double) = (v - offset) / factor
    }

    private val connectors = setOf("to", "in", "as", "into")

    val units: List<UnitDef> = buildList {
        fun u(symbol: String, d: Dimension, factor: Double, vararg aliases: String, offset: Double = 0.0) =
            add(UnitDef(symbol, d, factor, offset, listOf(symbol, *aliases)))

        val L = Dimension.Length
        u("mm", L, 0.001, "millimeter", "millimeters", "millimetre", "millimetres")
        u("cm", L, 0.01, "centimeter", "centimeters", "centimetre", "centimetres")
        u("m", L, 1.0, "meter", "meters", "metre", "metres")
        u("km", L, 1000.0, "kilometer", "kilometers", "kilometre", "kilometres")
        u("in", L, 0.0254, "inch", "inches", "\"")
        u("ft", L, 0.3048, "foot", "feet", "'")
        u("yd", L, 0.9144, "yard", "yards")
        u("mi", L, 1609.344, "mile", "miles")
        u("nmi", L, 1852.0, "nautical mile", "nautical miles")

        val M = Dimension.Mass
        u("mg", M, 1e-6, "milligram", "milligrams")
        u("g", M, 0.001, "gram", "grams")
        u("kg", M, 1.0, "kilogram", "kilograms", "kilo", "kilos")
        u("t", M, 1000.0, "tonne", "tonnes", "ton", "tons")
        u("oz", M, 0.028349523125, "ounce", "ounces")
        u("lb", M, 0.45359237, "lbs", "pound", "pounds")
        u("st", M, 6.35029318, "stone", "stones")

        val T = Dimension.Temperature
        u("°C", T, 1.0, "c", "°c", "celsius", "centigrade", offset = 273.15)
        u("°F", T, 5.0 / 9.0, "f", "°f", "fahrenheit", offset = 273.15 - 32.0 * 5.0 / 9.0)
        u("K", T, 1.0, "kelvin")

        val V = Dimension.Volume
        u("ml", V, 0.001, "mL", "milliliter", "milliliters", "millilitre", "millilitres")
        u("cl", V, 0.01, "cL", "centiliter", "centilitre")
        u("dl", V, 0.1, "dL", "deciliter", "decilitre")
        u("l", V, 1.0, "L", "liter", "liters", "litre", "litres")
        u("tsp", V, 0.00492892159375, "teaspoon", "teaspoons")
        u("tbsp", V, 0.01478676478125, "tablespoon", "tablespoons")
        u("fl oz", V, 0.0295735295625, "floz", "fluid ounce", "fluid ounces")
        u("cup", V, 0.2365882365, "cups")
        u("pt", V, 0.473176473, "pint", "pints")
        u("qt", V, 0.946352946, "quart", "quarts")
        u("gal", V, 3.785411784, "gallon", "gallons")

        val A = Dimension.Area
        u("m²", A, 1.0, "m2", "sqm", "square meter", "square meters", "square metre", "square metres")
        u("km²", A, 1e6, "km2", "sqkm", "square kilometer", "square kilometers")
        u("ha", A, 1e4, "hectare", "hectares")
        u("ac", A, 4046.8564224, "acre", "acres")
        u("ft²", A, 0.09290304, "ft2", "sqft", "square foot", "square feet")
        u("mi²", A, 2589988.110336, "mi2", "sqmi", "square mile", "square miles")

        val S = Dimension.Speed
        u("m/s", S, 1.0, "mps")
        u("km/h", S, 1 / 3.6, "kmh", "kph", "kmph")
        u("mph", S, 0.44704, "mi/h")
        u("kn", S, 1852.0 / 3600.0, "knot", "knots", "kt")

        val Ti = Dimension.Time
        u("ms", Ti, 0.001, "millisecond", "milliseconds")
        u("s", Ti, 1.0, "sec", "secs", "second", "seconds")
        u("min", Ti, 60.0, "mins", "minute", "minutes")
        u("h", Ti, 3600.0, "hr", "hrs", "hour", "hours")
        u("d", Ti, 86400.0, "day", "days")
        u("wk", Ti, 604800.0, "week", "weeks")
        u("yr", Ti, 31556952.0, "year", "years")

        val D = Dimension.Data
        u("B", D, 1.0, "byte", "bytes")
        u("KB", D, 1e3, "kB", "kilobyte", "kilobytes")
        u("MB", D, 1e6, "megabyte", "megabytes")
        u("GB", D, 1e9, "gigabyte", "gigabytes")
        u("TB", D, 1e12, "terabyte", "terabytes")
        u("KiB", D, 1024.0, "kibibyte")
        u("MiB", D, 1024.0 * 1024, "mebibyte")
        u("GiB", D, 1024.0 * 1024 * 1024, "gibibyte")
        u("TiB", D, 1024.0 * 1024 * 1024 * 1024, "tebibyte")
        u("bit", D, 0.125, "bits")
        u("Mb", D, 1e6 / 8, "Mbit", "megabit", "megabits")
        u("Gb", D, 1e9 / 8, "Gbit", "gigabit", "gigabits")

        val E = Dimension.Energy
        u("J", E, 1.0, "joule", "joules")
        u("kJ", E, 1000.0, "kilojoule", "kilojoules")
        u("cal", E, 4.184, "calorie", "calories")
        u("kcal", E, 4184.0, "Cal", "kilocalorie", "kilocalories")
        u("kWh", E, 3.6e6, "kwh")
    }

    private val exact: Map<String, UnitDef> = units.flatMap { u -> u.aliases.map { it to u } }.toMap()
    private val folded: Map<String, UnitDef> = buildMap {
        // First alias wins on a case clash (e.g. "mb" → MB, not Mb), so list the byte forms first.
        for (u in units) for (a in u.aliases) putIfAbsent(a.lowercase(), u)
    }
    private val longestAlias = units.maxOf { u -> u.aliases.maxOf { it.length } }

    fun unitFor(text: String): UnitDef? {
        val t = text.trim()
        return exact[t] ?: folded[t.lowercase()]
    }

    fun convert(input: String): Result? {
        val normalized = input.trim()
            .replace("->", " to ").replace("→", " to ").replace("=", " to ")
        val words = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        // Rightmost connector that splits the query into "<amount> <unit>" and "<unit>".
        for (i in words.indices.reversed()) {
            if (i == 0 || i == words.lastIndex || words[i].lowercase() !in connectors) continue
            val to = unitFor(words.subList(i + 1, words.size).joinToString(" ")) ?: continue
            val (amount, from) = amountAndUnit(words.subList(0, i).joinToString(" ")) ?: continue
            if (from.dimension != to.dimension) continue
            return Result(amount, from, to.fromBase(from.toBase(amount)), to)
        }
        return null
    }

    /** Splits "12.5 km" / "12.5km" / "(1+2) kg" into amount and unit by the longest unit suffix. */
    private fun amountAndUnit(text: String): Pair<Double, UnitDef>? {
        val t = text.trim()
        for (len in minOf(longestAlias, t.length - 1) downTo 1) {
            val unit = unitFor(t.substring(t.length - len)) ?: continue
            val head = t.substring(0, t.length - len)
            // A letter right before the suffix means we cut a word in half ("10 km" ≠ "10 k" + "m").
            if (head.lastOrNull()?.isLetter() == true) continue
            val amount = Calculator.evaluateAmount(head) ?: continue
            return amount to unit
        }
        return null
    }
}

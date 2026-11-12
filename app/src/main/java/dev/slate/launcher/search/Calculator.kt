package dev.slate.launcher.search

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Inline calculator for the search surface. Recursive descent over doubles:
 *
 * ```
 * expr   := term (('+' | '-') term)*
 * term   := unary (('*' | '/') unary | implicit-multiply)*
 * unary  := '-' unary | '+' unary | power
 * power  := postfix ('^' unary)?          // right-associative, binds tighter than unary minus on the left
 * postfix:= atom ('%' | '!')*
 * atom   := number | constant | func '(' expr ')' | func atom | '(' expr ')'
 * ```
 *
 * Trig functions take degrees, matching a pocket calculator. Accepts `×`, `÷`, `√`, `π`
 * and a comma as decimal separator. Plain numbers ("42") are not "calculations".
 */
object Calculator {

    data class Result(val value: Double, val text: String)

    /** Null unless [input] is a complete expression that actually computes something. */
    fun evaluate(input: String): Result? {
        val tokens = tokenize(input) ?: return null
        if (tokens.none { it is Token.Op || it is Token.Func || it is Token.Const || it is Token.Postfix }) return null
        val value = evaluateTokens(tokens) ?: return null
        return Result(value, format(value))
    }

    /** Evaluates without the "must compute something" rule; used by unit conversion for the amount. */
    internal fun evaluateAmount(input: String): Double? = tokenize(input)?.let(::evaluateTokens)

    private fun evaluateTokens(tokens: List<Token>): Double? {
        if (tokens.isEmpty()) return null
        val parser = Parser(tokens)
        val value = try {
            parser.expr()
        } catch (_: ParseError) {
            return null
        }
        return value.takeIf { parser.atEnd() && it.isFinite() }
    }

    /** Up to 12 significant digits, plain notation in a readable range, scientific outside it. */
    fun format(value: Double, significant: Int = 12): String {
        if (value == 0.0) return "0"
        val magnitude = abs(value)
        if (magnitude >= 1e15 || magnitude < 1e-9) {
            val s = "%.${significant - 1}e".format(java.util.Locale.ROOT, value)
            val (mantissa, exponent) = s.split('e')
            val trimmed = mantissa.trimEnd('0').trimEnd('.')
            return "$trimmed×10^${exponent.toInt()}"
        }
        return BigDecimal(value).round(MathContext(significant)).stripTrailingZeros().toPlainString()
    }

    private sealed interface Token {
        data class Num(val value: Double) : Token
        data class Op(val symbol: Char) : Token
        data class Postfix(val symbol: Char) : Token
        data class Func(val name: String) : Token
        data class Const(val value: Double) : Token
        data object Open : Token
        data object Close : Token
    }

    private val functions = setOf("sqrt", "sin", "cos", "tan", "ln", "log", "abs")

    private fun tokenize(input: String): List<Token>? {
        val out = mutableListOf<Token>()
        var i = 0
        val s = input.trim()
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || ((c == '.' || c == ',') && i + 1 < s.length && s[i + 1].isDigit()) -> {
                    val start = i
                    var seenPoint = false
                    while (i < s.length && (s[i].isDigit() || ((s[i] == '.' || s[i] == ',') && !seenPoint))) {
                        if (s[i] == '.' || s[i] == ',') seenPoint = true
                        i++
                    }
                    // Scientific literal: 1e5, 2.5E-3.
                    if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                        var j = i + 1
                        if (j < s.length && (s[j] == '+' || s[j] == '-')) j++
                        if (j < s.length && s[j].isDigit()) {
                            while (j < s.length && s[j].isDigit()) j++
                            i = j
                        }
                    }
                    out += Token.Num(s.substring(start, i).replace(',', '.').toDoubleOrNull() ?: return null)
                }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' -> { out += Token.Op(c); i++ }
                c == '×' || c == 'x' && isMultiplyX(s, i) -> { out += Token.Op('*'); i++ }
                c == '÷' -> { out += Token.Op('/'); i++ }
                c == '−' -> { out += Token.Op('-'); i++ }
                c == '%' || c == '!' -> { out += Token.Postfix(c); i++ }
                c == '(' -> { out += Token.Open; i++ }
                c == ')' -> { out += Token.Close; i++ }
                c == '√' -> { out += Token.Func("sqrt"); i++ }
                c == 'π' -> { out += Token.Const(Math.PI); i++ }
                c.isLetter() -> {
                    val start = i
                    while (i < s.length && s[i].isLetter()) i++
                    when (val word = s.substring(start, i).lowercase()) {
                        in functions -> out += Token.Func(word)
                        "pi" -> out += Token.Const(Math.PI)
                        "e" -> out += Token.Const(Math.E)
                        else -> return null
                    }
                }
                else -> return null
            }
        }
        return out
    }

    /** "3x4" is multiplication; "x" elsewhere is not something we understand. */
    private fun isMultiplyX(s: String, i: Int): Boolean {
        val before = s.substring(0, i).trimEnd().lastOrNull()
        val after = s.substring(i + 1).trimStart().firstOrNull()
        return before != null && after != null && (before.isDigit() || before == ')') && (after.isDigit() || after == '(' || after == '.')
    }

    private class ParseError : Exception()

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun atEnd() = pos == tokens.size
        private fun peek(): Token? = tokens.getOrNull(pos)
        private fun next(): Token = tokens.getOrNull(pos++) ?: throw ParseError()

        fun expr(): Double {
            var value = term()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.symbol == '+' || t.symbol == '-')) {
                    pos++
                    val rhs = term()
                    value = if (t.symbol == '+') value + rhs else value - rhs
                } else {
                    return value
                }
            }
        }

        private fun term(): Double {
            var value = unary()
            while (true) {
                val t = peek()
                when {
                    t is Token.Op && (t.symbol == '*' || t.symbol == '/') -> {
                        pos++
                        val rhs = unary()
                        value = if (t.symbol == '*') value * rhs else value / rhs
                    }
                    // Implicit multiplication: 2π, 3(4+1), (2)(3), 2 sqrt 9.
                    t is Token.Open || t is Token.Const || t is Token.Func || t is Token.Num && tokens[pos - 1] is Token.Close ->
                        value *= power()
                    else -> return value
                }
            }
        }

        private fun unary(): Double {
            val t = peek()
            if (t is Token.Op && (t.symbol == '-' || t.symbol == '+')) {
                pos++
                val v = unary()
                return if (t.symbol == '-') -v else v
            }
            return power()
        }

        private fun power(): Double {
            val base = postfix()
            val t = peek()
            if (t is Token.Op && t.symbol == '^') {
                pos++
                return base.pow(unary())
            }
            return base
        }

        private fun postfix(): Double {
            var value = atom()
            while (true) {
                val t = peek() as? Token.Postfix ?: return value
                pos++
                value = when (t.symbol) {
                    '%' -> value / 100.0
                    else -> factorial(value)
                }
            }
        }

        private fun atom(): Double = when (val t = next()) {
            is Token.Num -> t.value
            is Token.Const -> t.value
            is Token.Open -> expr().also { if (next() != Token.Close) throw ParseError() }
            is Token.Func -> apply(t.name, if (peek() == Token.Open) atom() else postfix())
            else -> throw ParseError()
        }

        private fun apply(name: String, x: Double): Double = when (name) {
            "sqrt" -> sqrt(x)
            "sin" -> sin(Math.toRadians(x)).snapToZero()
            "cos" -> cos(Math.toRadians(x)).snapToZero()
            "tan" -> tan(Math.toRadians(x)).snapToZero()
            "ln" -> ln(x)
            "log" -> log10(x)
            "abs" -> abs(x)
            else -> throw ParseError()
        }

        /** sin(180) is 1.2e-16 in doubles; a calculator should say 0. */
        private fun Double.snapToZero() = if (abs(this) < 1e-12) 0.0 else this

        private fun factorial(x: Double): Double {
            if (x < 0 || x != Math.floor(x) || x > 170) throw ParseError()
            var r = 1.0
            for (k in 2..x.toInt()) r *= k
            return r
        }
    }
}

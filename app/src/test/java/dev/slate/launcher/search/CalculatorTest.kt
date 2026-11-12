package dev.slate.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun eval(s: String) = Calculator.evaluate(s)?.text

    @Test fun precedenceAndAssociativity() {
        assertEquals("14", eval("2 + 3 * 4"))
        assertEquals("20", eval("(2 + 3) * 4"))
        assertEquals("512", eval("2^3^2"))
        assertEquals("-4", eval("-2^2"))
        assertEquals("0.5", eval("2^-1"))
        assertEquals("2", eval("8 / 2 / 2"))
    }

    @Test fun symbolsAndImplicitMultiplication() {
        assertEquals("12", eval("3×4"))
        assertEquals("12", eval("3x4"))
        assertEquals("2.5", eval("5÷2"))
        assertEquals("6.28318530718", eval("2π"))
        assertEquals("15", eval("3(4+1)"))
        assertEquals("1.5", eval("1,5 * 1"))
    }

    @Test fun functionsPercentFactorial() {
        assertEquals("3", eval("sqrt 9"))
        assertEquals("4", eval("√16"))
        assertEquals("0.5", eval("sin(30)"))
        assertEquals("0", eval("sin 180"))
        assertEquals("2", eval("log(100)"))
        assertEquals("30", eval("200 * 15%"))
        assertEquals("120", eval("5!"))
    }

    @Test fun rejectsNonCalculations() {
        assertNull(eval("42"))
        assertNull(eval("maps"))
        assertNull(eval("2 +"))
        assertNull(eval("(1 + 2"))
        assertNull(eval("1 / 0"))
        assertNull(eval("x"))
        assertNull(eval(""))
    }

    @Test fun formatting() {
        assertEquals("0.3", eval("0.1 + 0.2"))
        assertEquals("1×10^20", eval("10^20"))
        assertEquals("333333.333333", eval("1000000/3"))
    }
}

package dev.slate.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConverterTest {
    private fun conv(s: String) = UnitConverter.convert(s)?.text

    @Test fun lengthAndMass() {
        assertEquals("3.106856 mi", conv("5 km in mi"))
        assertEquals("254 cm", conv("100 in in cm"))
        assertEquals("39.37008 in", conv("1 m to in"))
        assertEquals("22.04623 lb", conv("10kg to lbs"))
    }

    @Test fun temperatureHasOffsets() {
        assertEquals("22.22222 °C", conv("72f to c"))
        assertEquals("212 °F", conv("100 °C in fahrenheit"))
        assertEquals("0 °C", conv("273.15 K to C"))
    }

    @Test fun dataUnitsAreCaseSensitiveFirst() {
        assertEquals("1430.511 MiB", conv("1.5 GB = MiB"))
        assertEquals("12.5 MB", conv("100 Mb to MB"))
    }

    @Test fun amountMayBeAnExpressionAndConnectorsVary() {
        assertEquals("11.02311 lb", conv("(2+3) kg -> lb"))
        assertEquals("300 s", conv("5 minutes into seconds"))
        assertEquals("1.609344 km/h", conv("1 mph as km/h"))
    }

    @Test fun rejectsMismatchedOrUnknown() {
        assertNull(conv("5 kg to km"))
        assertNull(conv("how to cook"))
        assertNull(conv("5 km"))
        assertNull(conv("5 parsecs to km"))
    }
}

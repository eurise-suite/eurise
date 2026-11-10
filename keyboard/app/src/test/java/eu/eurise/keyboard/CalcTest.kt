package eu.eurise.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcTest {
    @Test fun italianDecimals() = assertEquals("=12*3,5" to "42", Calc.find("costa =12*3,5"))
    @Test fun trailingEquals() = assertEquals("(10+5)/4 =" to "3,75", Calc.find("totale (10+5)/4 ="))
    @Test fun percentAndPower() {
        assertEquals("20", Calc.format(Calc.eval("200*10%")!!))
        assertEquals("1024", Calc.format(Calc.eval("2^10")!!))
        assertEquals("1500,5", Calc.format(Calc.eval("1.000,5+500")!!))
    }
    @Test fun divisionIsRounded() = assertEquals("0,3333333333", Calc.format(Calc.eval("1/3")!!))
    @Test fun plainNumbersAndTextAreIgnored() {
        assertNull(Calc.find("=42"))
        assertNull(Calc.find("ci vediamo alle 18"))
        assertNull(Calc.find("=2+"))
    }
}

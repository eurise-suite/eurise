package eu.eurise.core.data

import eu.eurise.core.data.calc.Calc
import eu.eurise.core.data.calc.Invoice
import eu.eurise.core.data.calc.Finance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CalcTest {
    private fun eq(expected: Double, text: String) = assertEquals(expected, Calc.evaluate(text), 1e-9)

    @Test fun italianNumbers() {
        eq(1250.0, "1.250")
        eq(1250.5, "1.250,5")
        eq(1.5, "1,5")
        eq(1.5, "1.5")
        eq(1234567.0, "1.234.567")
    }

    @Test fun precedenceAndParentheses() {
        eq(14.0, "2 + 3 × 4")
        eq(20.0, "(2 + 3) × 4")
        eq(508.3333333333333, "1.525 ÷ 3")
        eq(-4.0, "-2²")
        eq(512.0, "2^3^2")
        eq(6.0, "2(1+2)")
    }

    @Test fun officePercentages() {
        eq(1525.0, "1.250 + 22%")
        eq(900.0, "1.000 − 10%")
        eq(20.0, "200 × 10%")
        eq(0.5, "50%")
        eq(1.5, "1 + 5 × 10%")
    }

    @Test fun functions() {
        eq(0.5, "sin 30")
        eq(5.0, "√(3² + 4²)")
        eq(2.0, "log 100")
        eq(120.0, "5!")
        eq(Math.PI * 2, "2π")
        assertEquals(0.0, Calc.evaluate("sin(π)", degrees = false), 1e-12)
    }

    @Test fun incompleteGivesNoPreview() {
        assertNull(Calc.preview("12 +"))
        assertNull(Calc.preview("5 ÷ 0"))
        assertNull(Calc.preview("√-4"))
    }

    @Test fun formatting() {
        assertEquals("1.525", Calc.format(1525.0))
        assertEquals("508,3333333333", Calc.format(1525.0 / 3))
        assertEquals("0,1", Calc.format(0.1 + 0.2 - 0.2))
        assertEquals("1.268,80", Calc.money(BigDecimal("1268.8")))
    }

    @Test fun professionalInvoice() {
        val r = Invoice.fromCompenso(BigDecimal("1000"))
        assertEquals(BigDecimal("40.00"), r.cassa)
        assertEquals(BigDecimal("1040.00"), r.imponibile)
        assertEquals(BigDecimal("228.80"), r.iva)
        assertEquals(BigDecimal("1268.80"), r.totale)
        assertEquals(BigDecimal("200.00"), r.ritenuta)
        assertEquals(BigDecimal("1068.80"), r.netto)
    }

    @Test fun flatRateAddsStampOnly() {
        val r = Invoice.fromCompenso(BigDecimal("1000"), Invoice.Rates(forfettario = true))
        assertEquals(BigDecimal("1042.00"), r.totale)
        assertEquals(BigDecimal("1042.00"), r.netto)
    }

    @Test fun backFromNet() {
        val r = Invoice.fromNetto(BigDecimal("1068.80"))
        assertEquals(BigDecimal("1000.00"), r.compenso)
        val odd = Invoice.fromNetto(BigDecimal("1500"))
        // Gli arrotondamenti possono rendere irraggiungibile il centesimo esatto: al più un centesimo di scarto.
        org.junit.Assert.assertTrue((odd.netto - BigDecimal("1500")).abs() <= BigDecimal("0.01"))
    }
}

class FinanceTest {
    @Test fun everydayMoney() {
        val (imp, iva) = Finance.scorporo(BigDecimal("1220"), BigDecimal("22"))
        assertEquals(BigDecimal("1000.00"), imp)
        assertEquals(BigDecimal("220.00"), iva)
        assertEquals(BigDecimal("80.00"), Finance.sconto(BigDecimal("100"), BigDecimal("20")))
        val (prezzo, margine) = Finance.ricarico(BigDecimal("100"), BigDecimal("25"))
        assertEquals(BigDecimal("125.00"), prezzo)
        assertEquals(BigDecimal("20.00"), margine)
        val (rata, interessi) = Finance.rata(BigDecimal("10000"), BigDecimal("0"), 10)
        assertEquals(BigDecimal("1000.00"), rata)
        assertEquals(BigDecimal("0.00"), interessi)
        assertEquals(BigDecimal("856.07"), Finance.rata(BigDecimal("10000"), BigDecimal("5"), 12).first)
    }

    @Test fun workingDays() {
        val (all, work) = Finance.giorni(java.time.LocalDate.of(2026, 11, 6), java.time.LocalDate.of(2026, 11, 13))
        assertEquals(7L, all)
        assertEquals(5L, work)
    }

    @Test fun graphVariable() {
        assertEquals(4.0, Calc.evaluate("x² − 3x + 4", x = 3.0), 1e-9)
        assertEquals(2.0, Calc.evaluate("2x", x = 1.0), 1e-9)
    }
}

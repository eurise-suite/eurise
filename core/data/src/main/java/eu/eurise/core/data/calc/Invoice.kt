package eu.eurise.core.data.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Prospetto della fattura di un professionista: compenso, cassa di previdenza, IVA, ritenuta d'acconto, netto da
 * incassare. È un aiuto al calcolo con le aliquote scelte dalla persona, non una consulenza fiscale.
 *
 * La ritenuta si calcola sul compenso (senza la cassa). Nel regime forfettario non ci sono IVA né ritenuta e, sopra
 * 77,47 €, si aggiunge la marca da bollo di 2 € a carico del cliente.
 */
object Invoice {

    data class Rates(
        val cassa: BigDecimal = BigDecimal("4"),
        val iva: BigDecimal = BigDecimal("22"),
        val ritenuta: BigDecimal = BigDecimal("20"),
        val forfettario: Boolean = false,
    )

    data class Result(
        val compenso: BigDecimal,
        val cassa: BigDecimal,
        val imponibile: BigDecimal,
        val iva: BigDecimal,
        val bollo: BigDecimal,
        val totale: BigDecimal,
        val ritenuta: BigDecimal,
        val netto: BigDecimal,
    )

    private val HUNDRED = BigDecimal("100")
    private val BOLLO = BigDecimal("2.00")
    private val BOLLO_SOGLIA = BigDecimal("77.47")

    private fun cents(v: BigDecimal) = v.setScale(2, RoundingMode.HALF_UP)
    private fun pct(base: BigDecimal, rate: BigDecimal) = cents(base.multiply(rate).divide(HUNDRED, MathContext.DECIMAL64))

    fun fromCompenso(compenso: BigDecimal, rates: Rates = Rates()): Result {
        val c = cents(compenso)
        val cassa = pct(c, rates.cassa)
        val imponibile = c + cassa
        val iva = if (rates.forfettario) BigDecimal.ZERO.setScale(2) else pct(imponibile, rates.iva)
        val bollo = if (rates.forfettario && imponibile > BOLLO_SOGLIA) BOLLO else BigDecimal.ZERO.setScale(2)
        val totale = imponibile + iva + bollo
        val ritenuta = if (rates.forfettario) BigDecimal.ZERO.setScale(2) else pct(c, rates.ritenuta)
        return Result(c, cassa, imponibile, iva, bollo, totale, ritenuta, totale - ritenuta)
    }

    /**
     * Al contrario: quale compenso chiedere per incassare [netto]. Si parte dalla formula e si corregge di un centesimo
     * alla volta, perché gli arrotondamenti di cassa, IVA e ritenuta possono spostare il netto di qualche centesimo.
     */
    fun fromNetto(netto: BigDecimal, rates: Rates = Rates()): Result {
        val c = rates.cassa.divide(HUNDRED, MathContext.DECIMAL64)
        val i = if (rates.forfettario) BigDecimal.ZERO else rates.iva.divide(HUNDRED, MathContext.DECIMAL64)
        val r = if (rates.forfettario) BigDecimal.ZERO else rates.ritenuta.divide(HUNDRED, MathContext.DECIMAL64)
        val factor = BigDecimal.ONE + c + (BigDecimal.ONE + c) * i - r
        val target = cents(netto)
        var guess = cents((if (rates.forfettario && target > BOLLO_SOGLIA) target - BOLLO else target).divide(factor, MathContext.DECIMAL64))
        repeat(20) {
            val diff = fromCompenso(guess, rates).netto.compareTo(target)
            if (diff == 0) return fromCompenso(guess, rates)
            guess += if (diff < 0) BigDecimal("0.01") else BigDecimal("-0.01")
        }
        return fromCompenso(guess, rates)
    }
}

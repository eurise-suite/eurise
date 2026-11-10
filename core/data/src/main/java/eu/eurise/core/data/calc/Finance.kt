package eu.eurise.core.data.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Calcoli di tutti i giorni per chi lavora: scorporo IVA, sconto e ricarico, rata di un prestito, giorni tra due date. */
object Finance {
    private val HUNDRED = BigDecimal("100")
    private fun cents(v: BigDecimal) = v.setScale(2, RoundingMode.HALF_UP)

    /** Da un totale con IVA: imponibile e IVA. */
    fun scorporo(totale: BigDecimal, iva: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val imponibile = cents(totale.multiply(HUNDRED).divide(HUNDRED + iva, MathContext.DECIMAL64))
        return imponibile to cents(totale) - imponibile
    }

    /** Prezzo dopo uno sconto del [percent]%. */
    fun sconto(prezzo: BigDecimal, percent: BigDecimal): BigDecimal =
        cents(prezzo - prezzo.multiply(percent).divide(HUNDRED, MathContext.DECIMAL64))

    /** Prezzo di vendita con un ricarico del [percent]% sul costo, e il margine sul prezzo che ne risulta (in %). */
    fun ricarico(costo: BigDecimal, percent: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val prezzo = cents(costo + costo.multiply(percent).divide(HUNDRED, MathContext.DECIMAL64))
        val margine = if (prezzo.signum() == 0) BigDecimal.ZERO
        else (prezzo - costo).multiply(HUNDRED).divide(prezzo, 2, RoundingMode.HALF_UP)
        return prezzo to margine
    }

    /** Rata mensile costante (ammortamento alla francese) e interessi totali. */
    fun rata(capitale: BigDecimal, tassoAnnuo: BigDecimal, mesi: Int): Pair<BigDecimal, BigDecimal> {
        require(mesi > 0)
        val c = capitale.toDouble()
        val r = tassoAnnuo.toDouble() / 100 / 12
        val rata = if (r == 0.0) c / mesi else c * r / (1 - Math.pow(1 + r, -mesi.toDouble()))
        val rataBd = cents(BigDecimal.valueOf(rata))
        return rataBd to cents(rataBd.multiply(BigDecimal(mesi)) - capitale)
    }

    /** Giorni di calendario tra due date e giorni lavorativi (lunedì–venerdì, senza contare il primo giorno). */
    fun giorni(from: LocalDate, to: LocalDate): Pair<Long, Long> {
        val total = ChronoUnit.DAYS.between(from, to)
        val (start, end) = if (total >= 0) from to to else to to from
        var working = 0L
        var d = start.plusDays(1)
        while (!d.isAfter(end)) {
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) working++
            d = d.plusDays(1)
        }
        return total to (if (total >= 0) working else -working)
    }
}

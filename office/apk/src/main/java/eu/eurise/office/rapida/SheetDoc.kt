package eu.eurise.office.rapida

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Una cella per la vista rapida: [display] è il testo come lo mostra il file, [number] il valore se è un numero,
 * [formula] la formula in sintassi A1 (null se non c'è o se la vista rapida non la sa leggere: allora [hasFormula]).
 */
data class Cell(
    val col: Int,
    val row: Int,
    val display: String,
    val number: Double? = null,
    val formula: String? = null,
    val hasFormula: Boolean = formula != null,
)

data class Sheet(val name: String, val cells: Map<Long, Cell>, val rows: Int, val cols: Int) {
    operator fun get(col: Int, row: Int): Cell? = cells[key(col, row)]

    companion object {
        fun key(col: Int, row: Int): Long = row.toLong() * 20_000L + col
    }
}

data class SheetDoc(val sheets: List<Sheet>)

/**
 * Esito di una modifica: il file nuovo, le celle ricalcolate qui ([recalculated], riferimenti A1) e quelle che si
 * aggiornano solo nella modifica completa ([pending]): formule che la vista rapida non sa calcolare o che stanno in altri
 * fogli. Il file chiede comunque il ricalcolo all'apertura, così nessun programma mostra un totale vecchio.
 */
data class CellResult(val bundle: Bundle, val recalculated: List<String>, val pending: List<String>)

interface SheetFormat {
    fun read(bundle: Bundle): SheetDoc
    /** Scrive [input] nella cella ([col], [row], da 0) del foglio [sheet]: numero, testo, «=formula» o vuoto. */
    fun setCell(bundle: Bundle, sheet: Int, col: Int, row: Int, input: String): CellResult
}

/** Riferimenti A1 e lettura dei numeri scritti all'italiana. */
object Cells {
    fun colName(col: Int): String {
        var n = col + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.append('A' + r); n = (n - 1) / 26 }
        return sb.reverse().toString()
    }

    fun ref(col: Int, row: Int) = colName(col) + (row + 1)

    /** «C5» → (2, 4); null se non è un riferimento. */
    fun parse(ref: String): Pair<Int, Int>? {
        val m = Regex("^\\$?([A-Za-z]{1,3})\\$?(\\d{1,7})$").find(ref.trim()) ?: return null
        var col = 0
        for (ch in m.groupValues[1].uppercase()) col = col * 26 + (ch - 'A' + 1)
        return (col - 1) to (m.groupValues[2].toInt() - 1)
    }

    /** Cosa ha scritto la persona: un numero (anche «1.234,50», «84,20 €», «10%»), una formula, un testo o niente. */
    sealed interface Input {
        data class Number(val value: Double, val percent: Boolean) : Input
        data class Formula(val text: String) : Input
        data class Text(val text: String) : Input
        object Empty : Input
    }

    fun input(raw: String): Input {
        val s = raw.trim()
        if (s.isEmpty()) return Input.Empty
        if (s.startsWith("=") && s.length > 1) return Input.Formula(s.substring(1))
        var t = s.replace("€", "").replace(" ", "").replace(" ", "")
        val percent = t.endsWith("%")
        if (percent) t = t.dropLast(1)
        t = when {
            t.contains(',') -> t.replace(".", "").replace(',', '.')
            Regex("^-?\\d{1,3}(\\.\\d{3})+$").matches(t) -> t.replace(".", "")
            else -> t
        }
        val n = if (Regex("^[-+]?\\d*\\.?\\d+(E[-+]?\\d+)?$", RegexOption.IGNORE_CASE).matches(t)) t.toDoubleOrNull() else null
        return if (n != null) Input.Number(if (percent) n / 100 else n, percent) else Input.Text(raw)
    }

    /** Il numero come va scritto nel file: punto decimale, senza notazioni strane. */
    fun plain(value: Double): String {
        if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) return value.toLong().toString()
        return BigDecimal(value).round(java.math.MathContext(15)).stripTrailingZeros().toPlainString()
    }
}

/**
 * Come mostrare un numero: decimali, separatore delle migliaia, percentuale e simbolo prima o dopo. Si ricava dal formato
 * del file (Excel) o da come era scritta la cella (OpenDocument), così un totale ricalcolato si presenta come prima.
 */
data class NumberLook(val decimals: Int?, val grouping: Boolean, val percent: Boolean = false, val prefix: String = "", val suffix: String = "") {
    fun format(value: Double): String {
        val v = if (percent) value * 100 else value
        val symbols = DecimalFormatSymbols(Locale.ITALY)
        val body = if (decimals == null) {
            val bd = BigDecimal(v).round(java.math.MathContext(10)).stripTrailingZeros()
            val f = DecimalFormat(if (grouping) "#,##0.##########" else "0.##########", symbols)
            f.format(bd)
        } else {
            val pattern = (if (grouping) "#,##0" else "0") + if (decimals > 0) "." + "0".repeat(decimals) else ""
            DecimalFormat(pattern, symbols).apply { roundingMode = RoundingMode.HALF_UP }.format(v)
        }
        return prefix + body + (if (percent) "%" else "") + suffix
    }

    companion object {
        val GENERAL = NumberLook(null, false)

        /** Da un testo già mostrato, per esempio «1.844,70 €». */
        fun from(display: String): NumberLook {
            val d = display.trim()
            val m = Regex("^([^0-9\\-]*?)\\s?(-?[0-9][0-9.,\\s ]*)\\s?([^0-9]*)$").find(d) ?: return GENERAL
            val number = m.groupValues[2].trim()
            val percent = m.groupValues[3].trim() == "%"
            val decimals = number.substringAfterLast(',', "").let { if (number.contains(',')) it.length else 0 }
            val grouping = number.substringBefore(',').contains('.')
            val suffix = if (percent) "" else m.groupValues[3].let { if (it.isNotBlank()) " " + it.trim() else "" }
            val prefix = m.groupValues[1].let { if (it.isNotBlank()) it.trim() + " " else "" }
            return NumberLook(decimals, grouping, percent, prefix, suffix)
        }

        /** Da un codice di formato di Excel, per esempio `#,##0.00\ "€"` o `0%`. */
        fun fromCode(code: String?): NumberLook {
            if (code.isNullOrBlank() || code.equals("General", true)) return GENERAL
            val first = code.split(';').first()
            val plain = first.replace(Regex("\"[^\"]*\"|\\[[^\\]]*\\]|\\\\."), "")
            val decimals = Regex("\\.([0#]+)").find(plain)?.groupValues?.get(1)?.length ?: 0
            val grouping = plain.contains("#,##") || plain.contains("0,0")
            val percent = plain.contains('%')
            val currency = Regex("\"([^\"]*)\"|\\[\\$([^\\]-]*)").findAll(first).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.firstOrNull { it.isNotBlank() }
            val curBefore = currency != null && first.indexOf(currency) < first.indexOfFirst { it == '0' || it == '#' }
            return NumberLook(
                decimals, grouping, percent,
                prefix = if (currency != null && curBefore) "$currency " else "",
                suffix = if (currency != null && !curBefore) " $currency" else "",
            )
        }
    }
}

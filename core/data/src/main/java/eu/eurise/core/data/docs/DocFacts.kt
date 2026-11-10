package eu.eurise.core.data.docs

import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Che documento è, con la cartella in cui di solito si tiene (percorso dentro Documenti › Eurise o nel cloud). */
enum class DocKind(val label: String, val folder: String) {
    Electricity("Bolletta luce", "Casa/Bollette"),
    Gas("Bolletta gas", "Casa/Bollette"),
    Water("Bolletta acqua", "Casa/Bollette"),
    Phone("Bolletta telefono", "Casa/Bollette"),
    WasteTax("TARI", "Casa/Tasse"),
    F24("Modello F24", "Tasse"),
    Fine("Multa", "Auto/Multe"),
    CarTax("Bollo auto", "Auto"),
    Insurance("Assicurazione", "Assicurazioni"),
    Payslip("Busta paga", "Lavoro/Buste paga"),
    MedicalReport("Referto", "Salute"),
    Prescription("Ricetta medica", "Salute"),
    BankStatement("Estratto conto", "Banca"),
    Contract("Contratto", "Contratti"),
    PaymentNotice("Avviso di pagamento", "Pagamenti"),
    Invoice("Fattura", "Fatture"),
    Receipt("Ricevuta", "Ricevute"),
}

/** Un dato trovato, con il pezzo di testo da cui viene (per farlo vedere a chi controlla). */
data class Found<T>(val value: T, val quote: String)

/**
 * Ciò che si capisce di un documento dal suo testo letto: tipo, chi lo manda, mese, nome proposto, cartella, scadenza,
 * importo, IBAN e codice avviso pagoPA. Tutto sul telefono, con regole semplici e provate dai test; ciò che non è
 * chiaro resta vuoto (meglio nessuna proposta che una sbagliata).
 */
data class DocFacts(
    val kind: DocKind?,
    val issuer: String?,
    val month: YearMonth?,
    val deadline: Found<LocalDate>?,
    val amount: Found<BigDecimal>?,
    val iban: String?,
    val notice: String?,
) {
    /** «Bolletta luce Enel · dicembre 2026», o null se non si è capito il tipo. */
    val title: String?
        get() = kind?.let { k ->
            listOfNotNull(k.label, issuer).joinToString(" ") + (month?.let { " · " + monthName(it) } ?: "")
        }

    /** Nome del file proposto, ordinabile: «2026-12 Bolletta luce Enel». */
    val fileName: String?
        get() = kind?.let { k -> listOfNotNull(month?.toString(), k.label, issuer).joinToString(" ") }

    val folder: String? get() = kind?.folder

    companion object {
        fun monthName(m: YearMonth) = "${MONTHS[m.monthValue - 1]} ${m.year}"
        fun formatAmount(v: BigDecimal): String =
            DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.ITALY)).format(v) + " €"
    }
}

object DocReader {

    fun analyze(text: String): DocFacts {
        val flat = text.replace(Regex("[ \\t]+"), " ")
        val lower = flat.lowercase(Locale.ITALIAN)
        val kind = kind(lower)
        val dates = dates(flat)
        return DocFacts(
            kind = kind,
            issuer = issuer(flat),
            month = month(flat, lower, dates),
            deadline = deadline(flat, dates),
            amount = amount(flat),
            iban = iban(flat),
            notice = notice(flat),
        )
    }

    // ---------- tipo ----------

    private val KIND_WORDS: List<Pair<DocKind, List<String>>> = listOf(
        DocKind.Electricity to listOf("energia elettrica", "kwh", "pod ", "codice pod", "fornitura di luce", "luce e gas"),
        DocKind.Gas to listOf("gas naturale", "smc", "pdr", "codice pdr", "fornitura di gas"),
        DocKind.Water to listOf("servizio idrico", "acquedotto", "fornitura idrica", "acqua potabile"),
        DocKind.Phone to listOf("fibra", "linea fissa", "telefonia", "traffico telefonico", "numero di linea", "offerta mobile"),
        DocKind.WasteTax to listOf("tari", "tassa rifiuti", "tassa sui rifiuti", "tributo rifiuti"),
        DocKind.F24 to listOf("modello f24", "f24 semplificato", "delega irrevocabile"),
        DocKind.Fine to listOf("verbale di accertamento", "codice della strada", "violazione", "polizia locale", "polizia municipale"),
        DocKind.CarTax to listOf("tassa automobilistica", "bollo auto"),
        DocKind.Insurance to listOf("polizza", "compagnia di assicurazione", "premio annuo", "rca", "assicurato"),
        DocKind.Payslip to listOf("cedolino", "busta paga", "netto in busta", "retribuzione", "imponibile previdenziale"),
        DocKind.MedicalReport to listOf("referto", "esito dell'esame", "azienda sanitaria", "valori di riferimento", "medico refertante"),
        DocKind.Prescription to listOf("ricetta elettronica", "nre", "servizio sanitario nazionale", "prescrizione"),
        DocKind.BankStatement to listOf("estratto conto", "saldo iniziale", "saldo finale", "movimenti del periodo"),
        DocKind.Contract to listOf("contratto", "le parti", "sottoscritto", "clausola"),
        DocKind.PaymentNotice to listOf("avviso di pagamento", "pagopa", "codice avviso"),
        DocKind.Invoice to listOf("fattura", "partita iva", "imponibile", "aliquota iva"),
        DocKind.Receipt to listOf("ricevuta", "scontrino", "documento commerciale", "totale complessivo"),
    )

    /** Il tipo con più parole riconosciute (a parità, il primo dell'elenco: le bollette prima delle fatture generiche). */
    internal fun kind(lower: String): DocKind? = KIND_WORDS
        .map { (k, words) -> k to words.sumOf { w -> occurrences(lower, w) } }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first

    private fun occurrences(text: String, word: String): Int {
        // Le parole corte (tari, nre, rca, pdr, smc) contano solo intere.
        val re = if (word.length <= 4 && word.trim() == word) Regex("(?<![\\p{L}])" + Regex.escape(word) + "(?![\\p{L}])") else Regex(Regex.escape(word))
        return re.findAll(text).count()
    }

    // ---------- chi lo manda ----------

    private val ISSUERS = listOf(
        "Enel", "Hera", "A2A", "Iren", "Acea", "Edison", "Plenitude", "Sorgenia", "Engie", "Illumia", "Octopus", "Iberdrola",
        "Dolomiti Energia", "Estra", "Agsm", "Tim", "Vodafone", "WindTre", "Iliad", "Fastweb", "Sky", "Poste Italiane", "INPS",
        "Agenzia delle Entrate", "Intesa Sanpaolo", "UniCredit", "Fineco", "BPER", "Banco BPM", "Generali", "Allianz",
        "UnipolSai", "Reale Mutua", "Zurich", "Amazon", "Trenitalia", "Italo",
    )

    internal fun issuer(flat: String): String? {
        ISSUERS.firstOrNull { name -> Regex("(?<![\\p{L}])" + Regex.escape(name) + "(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(flat) }
            ?.let { return it }
        return Regex("Comune di ([A-ZÀ-Ý][\\p{L}']+(?: [A-ZÀ-Ý][\\p{L}']+)?)").find(flat)?.let { "Comune di " + it.groupValues[1] }
    }

    // ---------- date ----------

    internal data class DateAt(val date: LocalDate, val start: Int, val end: Int)

    private val NUMERIC_DATE = Regex("(?<!\\d)(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4}|\\d{2})(?!\\d)")
    private val WORD_DATE = Regex("(?<!\\d)(\\d{1,2})\\s+(${MONTHS.joinToString("|")})\\s+(\\d{4})", RegexOption.IGNORE_CASE)

    internal fun dates(flat: String): List<DateAt> {
        val out = ArrayList<DateAt>()
        NUMERIC_DATE.findAll(flat).forEach { m ->
            val (d, mo, y) = m.destructured
            val year = y.toInt().let { if (y.length == 2) 2000 + it else it }
            date(year, mo.toInt(), d.toInt())?.let { out += DateAt(it, m.range.first, m.range.last + 1) }
        }
        WORD_DATE.findAll(flat).forEach { m ->
            val (d, mo, y) = m.destructured
            date(y.toInt(), MONTHS.indexOf(mo.lowercase(Locale.ITALIAN)) + 1, d.toInt())?.let { out += DateAt(it, m.range.first, m.range.last + 1) }
        }
        return out.sortedBy { it.start }
    }

    private fun date(y: Int, m: Int, d: Int): LocalDate? = if (y in 1990..2100) runCatching { LocalDate.of(y, m, d) }.getOrNull() else null

    private val DEADLINE_WORDS = Regex("scaden|entro|pagare|pagamento|termine|da versare|ultimo giorno", RegexOption.IGNORE_CASE)

    /** La data preceduta (poco prima, sulla stessa frase) da «scadenza», «entro», «da pagare»…; tra più, la prima. */
    internal fun deadline(flat: String, dates: List<DateAt>): Found<LocalDate>? = dates.firstNotNullOfOrNull { d ->
        val before = flat.substring((d.start - 45).coerceAtLeast(0), d.start)
        val sameLine = before.substringAfterLast('\n')
        if (DEADLINE_WORDS.containsMatchIn(sameLine)) Found(d.date, quote(flat, d.start - sameLine.length, d.end)) else null
    }

    private val ISSUE_WORDS = Regex("emess|emission|data (fattura|documento|bolletta)|del\\s*$", RegexOption.IGNORE_CASE)
    private val PERIOD = Regex("periodo[^\\n]{0,40}?(${MONTHS.joinToString("|")})\\s+(\\d{4})", RegexOption.IGNORE_CASE)

    /** Il mese del documento: il periodo («periodo novembre 2026»), altrimenti la data di emissione, altrimenti la prima data. */
    internal fun month(flat: String, lower: String, dates: List<DateAt>): YearMonth? {
        PERIOD.find(lower)?.let { m -> return YearMonth.of(m.groupValues[2].toInt(), MONTHS.indexOf(m.groupValues[1]) + 1) }
        dates.firstOrNull { d -> ISSUE_WORDS.containsMatchIn(flat.substring((d.start - 30).coerceAtLeast(0), d.start).substringAfterLast('\n')) }
            ?.let { return YearMonth.from(it.date) }
        return dates.firstOrNull()?.let { YearMonth.from(it.date) }
    }

    // ---------- importi ----------

    private val AMOUNT = Regex(
        "(?:(?:€|euro|EUR)\\s?(\\d{1,3}(?:[.\\s]\\d{3})*,\\d{2}|\\d+,\\d{2}))|" +
            "(?:(\\d{1,3}(?:[.\\s]\\d{3})*,\\d{2}|\\d+,\\d{2})\\s?(?:€|euro|EUR))",
        RegexOption.IGNORE_CASE,
    )
    private val MAIN_AMOUNT = Regex("totale da pagare|importo da pagare|da pagare|netto a pagare|totale fattura|totale documento|importo dovuto|totale", RegexOption.IGNORE_CASE)

    /** L'importo da pagare: quello vicino a «totale da pagare» (o simili), altrimenti il più alto con il simbolo dell'euro. */
    internal fun amount(flat: String): Found<BigDecimal>? {
        val all = AMOUNT.findAll(flat).mapNotNull { m ->
            val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }
            val value = runCatching { BigDecimal(raw.replace(".", "").replace(" ", "").replace(',', '.')) }.getOrNull() ?: return@mapNotNull null
            Triple(value, m.range.first, m.range.last + 1)
        }.toList()
        if (all.isEmpty()) return null
        val labelled = all.mapNotNull { (v, s, e) ->
            val lineStart = flat.lastIndexOf('\n', s - 1) + 1
            val before = flat.substring(maxOf(lineStart, s - 50), s)
            MAIN_AMOUNT.findAll(before).lastOrNull()?.let { label -> Triple(Found(v, quote(flat, maxOf(lineStart, s - 50) + label.range.first, e)), label.value.length, s) }
        }
        // Tra le etichette vince la più precisa (la più lunga: «totale da pagare» batte «totale»).
        labelled.maxWithOrNull(compareBy({ it.second }, { -it.third }))?.let { return it.first }
        val (v, s, e) = all.maxBy { it.first }
        return Found(v, quote(flat, s, e))
    }

    // ---------- IBAN e pagoPA ----------

    private val IBAN = Regex("(?<![A-Z0-9])IT\\s?\\d{2}\\s?[A-Z](?:\\s?[0-9A-Z]){22}(?![0-9A-Z])")

    internal fun iban(flat: String): String? = IBAN.findAll(flat.uppercase(Locale.ROOT))
        .map { it.value.replace(" ", "") }
        .firstOrNull(::ibanValid)
        ?.chunked(4)?.joinToString(" ")

    internal fun ibanValid(iban: String): Boolean {
        if (iban.length < 15) return false
        val moved = iban.substring(4) + iban.substring(0, 4)
        val digits = moved.map { if (it.isLetter()) (it - 'A' + 10).toString() else it.toString() }.joinToString("")
        return BigInteger(digits).mod(BigInteger.valueOf(97)) == BigInteger.ONE
    }

    private val NOTICE = Regex("(?<!\\d)([0-3](?:\\s?\\d){17})(?!\\d)")

    /** Codice avviso pagoPA (18 cifre), solo se il testo ne parla vicino. */
    internal fun notice(flat: String): String? = NOTICE.findAll(flat).firstOrNull { m ->
        Regex("codice avviso|cod\\. avviso|numero avviso|codice iuv|pagopa", RegexOption.IGNORE_CASE)
            .containsMatchIn(flat.substring((m.range.first - 60).coerceAtLeast(0), m.range.first))
    }?.groupValues?.get(1)?.replace(" ", "")?.chunked(4)?.joinToString(" ")

    private fun quote(flat: String, start: Int, end: Int): String =
        flat.substring(start.coerceAtLeast(0), end.coerceAtMost(flat.length)).replace('\n', ' ').trim()
}

private val MONTHS = listOf(
    "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
)

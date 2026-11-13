package eu.eurise.office.pdf

/** Un dato personale trovato nel testo di una pagina («Prima di condividere», oscura). */
data class SensitiveHit(val kind: Kind, val start: Int, val end: Int, val text: String) {
    enum class Kind { FiscalCode, Iban }
}

/**
 * Riconosce codici fiscali italiani e IBAN nel testo estratto da una pagina. Solo sul telefono: nessun servizio esterno.
 * Il codice fiscale si verifica con il carattere di controllo, l'IBAN con il modulo 97: niente falsi allarmi su sigle.
 */
object Sensitive {
    private val fiscal = Regex("""\b[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-EHLMPR-T][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]\b""", RegexOption.IGNORE_CASE)
    private val iban = Regex("""\b[A-Z]{2}[0-9]{2}(?: ?[A-Z0-9]){11,30}\b""", RegexOption.IGNORE_CASE)

    fun find(text: String): List<SensitiveHit> {
        val hits = mutableListOf<SensitiveHit>()
        fiscal.findAll(text).forEach { m ->
            if (fiscalCheck(m.value.uppercase())) hits += SensitiveHit(SensitiveHit.Kind.FiscalCode, m.range.first, m.range.last + 1, m.value)
        }
        iban.findAll(text).forEach { m ->
            // La corrispondenza può portarsi dietro la parola dopo («… 456 per»): si accorcia finché il controllo torna.
            var value = m.value
            while (value.replace(" ", "").length >= 15) {
                if (ibanCheck(value.replace(" ", "").uppercase())) break
                value = value.dropLast(1).trimEnd()
            }
            val end = m.range.first + value.length
            if (value.replace(" ", "").length >= 15 && ibanCheck(value.replace(" ", "").uppercase()) &&
                hits.none { it.start < end && m.range.first < it.end }
            ) {
                hits += SensitiveHit(SensitiveHit.Kind.Iban, m.range.first, end, value)
            }
        }
        return hits.sortedBy { it.start }
    }

    fun fiscalCheck(code: String): Boolean {
        if (code.length != 16) return false
        val odd = intArrayOf(1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23)
        var sum = 0
        for (i in 0 until 15) {
            val c = code[i]
            val n = if (c.isDigit()) c - '0' else c - 'A'
            sum += if (i % 2 == 0) odd[n] else n
        }
        return code[15] == 'A' + sum % 26
    }

    fun ibanCheck(code: String): Boolean {
        if (code.length < 15 || code.length > 34 || !code.all { it.isLetterOrDigit() }) return false
        val moved = code.substring(4) + code.substring(0, 4)
        var rest = 0
        for (c in moved) {
            val value = if (c.isDigit()) (c - '0') else (c.uppercaseChar() - 'A' + 10)
            rest = if (value >= 10) (rest * 100 + value) % 97 else (rest * 10 + value) % 97
        }
        return rest == 1
    }
}

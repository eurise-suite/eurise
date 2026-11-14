package eu.eurise.feature.assistant.online

/**
 * Una domanda ripulita: [text] è ciò che esce dal telefono, con i dati personali sostituiti da segnaposto come
 * «[PERSONA 1]»; [restore] li rimette al loro posto nella risposta, che così resta sul telefono completa.
 */
class Redaction(val text: String, val replaced: List<Pair<String, String>>) {
    val changed: Boolean get() = replaced.isNotEmpty()

    /** Rimette i dati veri al posto dei segnaposto, anche se l'AI li ha riscritti senza parentesi. */
    fun restore(answer: String): String {
        var out = answer
        // Prima i segnaposto più lunghi, così «[PERSONA 12]» non viene toccato da «[PERSONA 1]».
        for ((tag, original) in replaced.sortedByDescending { it.first.length }) {
            out = out.replace(tag, original)
            val bare = tag.removePrefix("[").removeSuffix("]")
            out = out.replace(Regex("(?<![\\p{L}\\d])" + Regex.escape(bare) + "(?![\\d])"), Regex.escapeReplacement(original))
        }
        return out
    }
}

/**
 * Toglie dalla domanda, sul telefono, ciò che dice chi sei o chi conosci prima che esca verso un'AI online: email,
 * IBAN, codici fiscali, numeri di carta e di telefono, indirizzi, i nomi della rubrica e il proprio. Gli stessi dati
 * prendono lo stesso segnaposto, così la risposta resta coerente. Non tocca il resto della frase.
 */
object Redactor {
    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
    private val IBAN = Regex("\\b[A-Z]{2}\\d{2}(?:\\s?[A-Z0-9]){11,30}\\b")
    private val FISCAL = Regex("\\b[A-Z]{6}\\d{2}[A-EHLMPRST]\\d{2}[A-Z]\\d{3}[A-Z]\\b", RegexOption.IGNORE_CASE)
    private val CARD = Regex("\\b(?:\\d[ -]?){13,19}\\b")
    private val PHONE = Regex("(?<![\\w])\\+?\\d[\\d ./-]{6,}\\d(?![\\w])")
    private val ADDRESS = Regex(
        "\\b(?:via|viale|piazza|piazzale|corso|largo|vicolo|vico|strada|contrada|borgo)\\s+" +
            "(?:[A-ZÀ-Ý][\\p{L}'’]*\\.?\\s?){1,4}(?:,?\\s*\\d+[a-zA-Z]?(?:/\\w+)?)?",
        RegexOption.IGNORE_CASE,
    )

    fun redact(input: String, names: Collection<String> = emptyList()): Redaction {
        val replaced = mutableListOf<Pair<String, String>>()
        val counters = mutableMapOf<String, Int>()
        var text = input

        fun tagFor(kind: String, original: String): String {
            replaced.firstOrNull { it.second.equals(original, ignoreCase = true) && it.first.startsWith("[$kind ") }?.let { return it.first }
            val n = (counters[kind] ?: 0) + 1
            counters[kind] = n
            return "[$kind $n]".also { replaced += it to original }
        }

        fun sweep(regex: Regex, kind: String, accept: (String) -> Boolean = { true }) {
            text = regex.replace(text) { m ->
                val v = m.value.trim()
                if (v.startsWith("[") || !accept(v)) m.value else m.value.replace(v, tagFor(kind, v))
            }
        }

        sweep(EMAIL, "EMAIL")
        sweep(IBAN, "IBAN") { it.count(Char::isDigit) >= 10 }
        sweep(FISCAL, "CODICE")
        sweep(CARD, "NUMERO") { v -> v.count(Char::isDigit) in 13..19 && luhn(v.filter(Char::isDigit)) }
        sweep(PHONE, "TELEFONO") { v -> v.count(Char::isDigit) in 7..15 && !looksLikeDate(v) && !looksLikeYearOrAmount(v) }
        sweep(ADDRESS, "INDIRIZZO") { v -> v.split(Regex("\\s+")).size >= 2 }

        // Nomi: prima i più lunghi («Anna Rossi» prima di «Anna»), solo parole intere, almeno tre lettere.
        val people = names.asSequence().map { it.trim() }.filter { it.length >= 3 }
            .flatMap { full -> sequenceOf(full) + full.split(Regex("\\s+")).filter { it.length >= 3 && it.first().isUpperCase() } }
            .distinct().sortedByDescending { it.length }.toList()
        for (name in people) {
            val re = Regex("(?<![\\p{L}\\[])" + Regex.escape(name) + "(?![\\p{L}])")
            if (re.containsMatchIn(text)) text = re.replace(text) { tagFor("PERSONA", it.value) }
        }
        return Redaction(text, replaced)
    }

    private fun luhn(digits: String): Boolean {
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return sum % 10 == 0
    }

    private fun looksLikeDate(v: String) = Regex("^\\d{1,2}[./-]\\d{1,2}([./-]\\d{2,4})?$").matches(v.trim())
    private fun looksLikeYearOrAmount(v: String) = Regex("^\\d{1,3}([.]\\d{3})+$").matches(v.trim()) || Regex("^(19|20)\\d{2}$").matches(v.trim())
}

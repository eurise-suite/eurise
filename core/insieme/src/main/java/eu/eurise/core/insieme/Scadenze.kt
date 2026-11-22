package eu.eurise.core.insieme

import java.time.LocalDate

/**
 * Le scadenze dentro un'email (tavole FamScadenze, FamScuola): bollette, avvisi, circolari. Senza Android: si prova con i test.
 * Si cerca la data vicino a «scade», «scadenza», «entro», «da pagare», «autorizzazione»; si capiscono «12 dic», «12 dicembre
 * 2026», «12/12», «12/12/2026». Tutto sul telefono.
 */
object Scadenze {
    private val months = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
    private val numeric = Regex("\\b(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{2,4}))?\\b")
    private val written = Regex("\\b(\\d{1,2})\\s+(gen|feb|mar|apr|mag|giu|lug|ago|set|ott|nov|dic)[a-z]*\\.?(?:\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE)
    private val keys = Regex("(scade|scadenza|entro|da pagare|pagare|autorizzazion|termine|ultimo giorno)", RegexOption.IGNORE_CASE)

    private fun build(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        val y = year?.let { if (it < 100) 2000 + it else it }
        val d = runCatching { LocalDate.of(y ?: today.year, month, day) }.getOrNull() ?: return null
        return if (y == null && d.isBefore(today.minusDays(7))) d.plusYears(1) else d
    }

    /** La prima data scritta in [text] («12 dic», «12/12/2026»), o null. */
    fun date(text: String, today: LocalDate): LocalDate? {
        val w = written.find(text)
        val n = numeric.find(text)
        val first = listOfNotNull(w, n).minByOrNull { it.range.first } ?: return null
        val g = first.groupValues
        return if (first === w) build(g[1].toInt(), months.indexOf(g[2].lowercase().take(3)) + 1, g[3].toIntOrNull(), today)
        else build(g[1].toInt(), g[2].toInt(), g[3].toIntOrNull(), today)
    }

    /** La scadenza dell'email: la data subito dopo una parola chiave (nella stessa frase), o null. */
    fun due(text: String, today: LocalDate): LocalDate? {
        keys.findAll(text).forEach { k ->
            val tail = text.substring(k.range.first, minOf(text.length, k.range.last + 60))
            date(tail, today)?.let { return it }
        }
        return null
    }
}

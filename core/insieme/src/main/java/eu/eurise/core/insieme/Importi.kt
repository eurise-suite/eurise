package eu.eurise.core.insieme

/**
 * Gli importi dentro un'email (tavola SpeEmail: biglietti, ricevute, prenotazioni), senza Android: si prova con i test.
 * Si cerca il totale pagato: prima le righe con «totale», «importo», «pagato», «addebitato»; se no l'importo più alto in
 * euro. Tutto sul telefono.
 */
object Importi {
    private val money = Regex("(?:€|EUR)\\s*(\\d{1,3}(?:\\.\\d{3})+(?:,\\d{2})?|\\d+[.,]\\d{2}|\\d+)|(\\d{1,3}(?:\\.\\d{3})+(?:,\\d{2})?|\\d+[.,]\\d{2}|\\d+)\\s*(?:€|EUR|euro)", RegexOption.IGNORE_CASE)
    private val keys = Regex("(totale|importo|pagato|addebitat|da pagare|prezzo finale|total)", RegexOption.IGNORE_CASE)

    /** In centesimi: «1.284,00» → 128400, «12,5»… no (servono due decimali o nessuno), «528.00» → 52800. */
    fun cents(s: String): Long? {
        val t = s.replace(" ", "")
        val norm = when {
            Regex("^\\d{1,3}(\\.\\d{3})+(,\\d{2})?$").matches(t) -> t.replace(".", "").replace(',', '.')
            Regex("^\\d+,\\d{2}$").matches(t) -> t.replace(',', '.')
            Regex("^\\d+(\\.\\d{2})?$").matches(t) -> t
            else -> return null
        }
        return norm.toBigDecimalOrNull()?.movePointRight(2)?.toLong()?.takeIf { it > 0 }
    }

    private fun all(line: String): List<Long> = money.findAll(line).mapNotNull { m -> cents(m.groupValues[1].ifEmpty { m.groupValues[2] }) }.toList()

    /** Il totale dell'email, o null. Le righe vicine alla parola chiave contano (l'importo spesso va a capo). */
    fun total(text: String): Long? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(400)
        lines.forEachIndexed { i, l ->
            if (keys.containsMatchIn(l)) {
                (all(l) + lines.getOrNull(i + 1)?.let(::all).orEmpty()).maxOrNull()?.let { return it }
            }
        }
        return lines.flatMap(::all).maxOrNull()
    }
}

package eu.eurise.feature.assistant

/**
 * L'azione utile dentro una risposta libera (tavola AlfTelefono): «cuocere 9 minuti» → «Timer 9 min», un numero di
 * telefono → «Chiama». Al massimo due proposte, solo cose che si capiscono senza dubbi; il tocco apre la scheda solita.
 */
object Suggest {
    /** Ciò che serve alla pillola: [minutes] per il timer, [number] per la chiamata. */
    data class Action(val minutes: Int? = null, val number: String? = null)

    private val MINUTES = Regex("(?iu)(?<![\\d,.])(\\d{1,3})(?:\\s*[-–]\\s*\\d{1,3})?\\s*(?:minuti|minuto|min\\b|')")
    private val HALF_HOUR = Regex("(?iu)\\bmezz'?ora\\b")
    private val PHONE = Regex("(?<![\\d])(\\+?\\d[\\d ]{7,14}\\d)(?![\\d])")

    fun actions(text: String): List<Action> = buildList {
        val t = text.replace('’', '\'')
        MINUTES.find(t)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..240 }?.let { add(Action(minutes = it)) }
            ?: if (HALF_HOUR.containsMatchIn(t)) add(Action(minutes = 30)) else Unit
        PHONE.find(t)?.groupValues?.get(1)?.filter { it.isDigit() || it == '+' }?.takeIf { it.count(Char::isDigit) in 8..14 }
            ?.let { add(Action(number = it)) }
    }.take(2)
}

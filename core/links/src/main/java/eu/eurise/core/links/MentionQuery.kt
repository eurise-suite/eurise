package eu.eurise.core.links

/**
 * Dove si sta scrivendo il nome di una persona, per proporre i contatti del telefono (tavola AgeScrivi).
 *
 * Due modi: con la chiocciola («Chiamare @Lu») ovunque; scrivendo il nome dopo «con» («Pranzo con Ma») dove la persona
 * fa parte del titolo, come negli appuntamenti. Logica pura, senza Android: si prova con i test.
 */
object MentionQuery {

    /** Il pezzo di testo da sostituire ([start] incluso, [end] escluso) e ciò che si cerca. [at]: scritto con la chiocciola. */
    data class Hit(val start: Int, val end: Int, val query: String, val at: Boolean)

    private val AT = Regex("""(?<![\p{L}\p{N}@._-])@(\p{L}[\p{L}'’-]*(?: \p{Lu}[\p{L}'’-]*)?)$""")
    private val AFTER_CON = Regex("""(?i:\b(?:con|insieme a|da|per)) (\p{Lu}[\p{L}'’-]+(?: \p{Lu}[\p{L}'’-]*)?)$""")

    /**
     * La persona che si sta scrivendo subito prima di [caret], o null. Col nome dopo «con» ([byName]) servono almeno due
     * lettere e l'iniziale maiuscola, per non disturbare mentre si scrive una frase normale.
     */
    fun find(text: String, caret: Int = text.length, byName: Boolean = false): Hit? {
        val upTo = caret.coerceIn(0, text.length)
        // Si propone solo a fine parola: se dopo il cursore continua una parola, si sta correggendo altro.
        if (upTo < text.length && text[upTo].isLetterOrDigit()) return null
        val before = text.substring(0, upTo)
        AT.find(before)?.let { m ->
            val q = m.groupValues[1]
            return Hit(m.range.first, upTo, q, at = true)
        }
        if (!byName) return null
        AFTER_CON.find(before)?.let { m ->
            val group = m.groups[1] ?: return null
            return Hit(group.range.first, upTo, group.value, at = false)
        }
        return null
    }

    /**
     * Mette il contatto scelto al posto di ciò che si stava scrivendo. Con la chiocciola ([Hit.at]) e [keepAt] resta
     * «@Nome» (come nelle attività); altrimenti il nome intero. Ritorna il testo nuovo e la posizione del cursore.
     */
    fun apply(text: String, hit: Hit, fullName: String, keepAt: Boolean): Pair<String, Int> {
        val name = fullName.trim()
        val inserted = if (hit.at && keepAt) "@" + firstName(name) else name
        val tail = text.substring(hit.end.coerceAtMost(text.length))
        val space = if (tail.startsWith(" ")) "" else " "
        val out = text.substring(0, hit.start) + inserted + space + tail
        return out to (hit.start + inserted.length + space.length)
    }

    /** Il nome da usare dopo la chiocciola: «Anna Maria Rossi» dà «Anna». */
    fun firstName(name: String): String =
        name.trim().substringBefore(' ').takeWhile { it.isLetterOrDigit() || it == '\'' || it == '’' || it == '-' }

    /** I nomi scelti che sono ancora nel testo: quelli da collegare quando si salva. */
    fun stillThere(text: String, names: Collection<String>): List<String> =
        names.filter { n -> n.isNotBlank() && (text.contains(n, ignoreCase = true) || text.contains("@" + firstName(n), ignoreCase = true)) }
}

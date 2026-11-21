package eu.eurise.keyboard

/**
 * Frase intera tra le parole (tavola TastImpostazioniFrase, approvata il 18 novembre). Ricorda le frasi che la persona
 * scrive spesso (contate, al più [MAX_ENTRIES], le meno recenti escono) e, quando l'inizio scritto coincide, propone
 * il resto. Logica pura, senza Android: si prova in `PhraseModelTest`. Resta sul telefono (file nella Tastiera).
 */
class PhraseModel {
    private class Entry(var count: Int, var text: String)
    /** Chiave = parole in minuscolo separate da uno spazio; ordine d'accesso, così escono le meno usate di recente. */
    private val entries = LinkedHashMap<String, Entry>(64, 0.75f, true)

    val size get() = entries.size

    /** Una frase scritta e lasciata nel testo. Contano solo frasi di almeno [MIN_WORDS] parole; le prime [MAX_WORDS]. */
    @Synchronized
    fun learn(sentence: String) {
        val words = words(sentence).take(MAX_WORDS)
        if (words.size < MIN_WORDS) return
        val key = words.joinToString(" ") { it.lowercase() }
        val e = entries[key]
        if (e == null) entries[key] = Entry(1, words.joinToString(" ")) else { e.count++; e.text = words.joinToString(" ") }
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }

    /**
     * Il resto della frase per il testo [typed] della frase in corso, o null. Serve un inizio di almeno [MIN_TYPED]
     * caratteri e due parole (anche l'ultima a metà), una frase vista almeno [MIN_COUNT] volte e almeno due parole da
     * aggiungere. Il risultato comincia dal resto della parola a metà, oppure dalla parola dopo.
     */
    @Synchronized
    fun propose(typed: String): String? {
        if (typed.trim().length < MIN_TYPED) return null
        val parts = words(typed)
        if (parts.isEmpty()) return null
        val endsWord = typed.last().isLetterOrDigit() || typed.last() == '\''
        val full = if (endsWord) parts.dropLast(1) else parts
        val partial = if (endsWord) parts.last() else ""
        if (full.size + (if (partial.isNotEmpty()) 1 else 0) < 2) return null
        var best: Entry? = null
        var bestKey = ""
        for ((key, e) in entries) {
            if (e.count < MIN_COUNT) continue
            val ws = key.split(' ')
            if (ws.size < full.size + 2) continue
            if ((full.indices).any { ws[it] != full[it].lowercase() }) continue
            if (partial.isNotEmpty() && !ws[full.size].startsWith(partial.lowercase())) continue
            if (partial.isNotEmpty() && ws.size < full.size + 2) continue
            val b = best
            if (b == null || e.count > b.count || (e.count == b.count && key.length > bestKey.length)) { best = e; bestKey = key }
        }
        val e = best ?: return null
        val shown = e.text.split(' ')
        val restWords = shown.drop(full.size)
        return if (partial.isNotEmpty()) {
            val head = restWords.first().drop(partial.length)
            (head + " " + restWords.drop(1).joinToString(" ")).trimStart()
        } else restWords.joinToString(" ")
    }

    /** «Togli per sempre» (pressione lunga sulla proposta): la frase che inizia con [typed] e continua con [rest]. */
    @Synchronized
    fun forget(typed: String, rest: String) {
        val joined = (typed + rest).let { words(it).joinToString(" ") { w -> w.lowercase() } }
        entries.remove(joined)
        entries.keys.filter { joined.startsWith(it) || it.startsWith(joined) }.forEach { entries.remove(it) }
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun save(): String = buildString { for ((_, e) in entries) append(e.count).append('\t').append(e.text).append('\n') }

    @Synchronized
    fun load(text: String?) {
        entries.clear()
        text?.lineSequence()?.forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val count = line.substring(0, tab).toIntOrNull() ?: return@forEach
            val phrase = line.substring(tab + 1)
            val ws = words(phrase)
            if (ws.size >= MIN_WORDS) entries[ws.joinToString(" ") { it.lowercase() }] = Entry(count, ws.joinToString(" "))
        }
    }

    /**
     * Unisce le frasi di [text] (formato di [save], per esempio da un backup) a quelle già imparate: per ogni frase vale il
     * conto più alto, nessuna frase di qui si perde; le nuove entrano tra le meno recenti, così escono per prime se si
     * supera [MAX_ENTRIES].
     */
    @Synchronized
    fun merge(text: String?) {
        val incoming = PhraseModel().apply { load(text) }
        val mine = LinkedHashMap(entries)
        entries.clear()
        for ((key, e) in incoming.entries) if (key !in mine) entries[key] = Entry(e.count, e.text)
        for ((key, e) in mine) entries[key] = Entry(maxOf(e.count, incoming.entries[key]?.count ?: 0), e.text)
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }

    companion object {
        const val MIN_WORDS = 3
        const val MAX_WORDS = 12
        const val MIN_COUNT = 3
        const val MIN_TYPED = 6
        const val MAX_ENTRIES = 400

        /** Le parole di un testo (lettere, cifre, apostrofi), senza punteggiatura. */
        fun words(text: String): List<String> = Regex("[\\p{L}\\p{N}']+").findAll(text).map { it.value }.toList()
    }
}

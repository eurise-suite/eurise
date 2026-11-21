package eu.eurise.feature.notes.voice

/**
 * Il testo di una nota vocale rimesso in ordine (tavola NoteVocaleOrdinata): un titolo per la nota e i paragrafi, con un
 * titoletto dove comincia un argomento nuovo. Si salva nella nota vocale come righe di testo: «## » apre un titoletto,
 * ogni altra riga è un paragrafo (vedi [encode] e [parse]).
 */
data class OrderedText(val title: String, val sections: List<Section>) {
    data class Section(val heading: String?, val paragraphs: List<String>)

    val isEmpty: Boolean get() = sections.all { it.paragraphs.isEmpty() }

    /** Il testo come righe; il titolo non c'è (va nel titolo della nota). */
    fun encode(): String = sections.joinToString("\n") { s ->
        (listOfNotNull(s.heading?.let { "$HEADING$it" }) + s.paragraphs).joinToString("\n")
    }

    /** Il testo semplice, per la ricerca e per Alfred. */
    fun plain(): String = sections.flatMap { listOfNotNull(it.heading) + it.paragraphs }.joinToString("\n")

    companion object {
        const val HEADING = "## "

        fun parse(text: String, title: String = ""): OrderedText {
            val sections = mutableListOf<Section>()
            var heading: String? = null
            var paragraphs = mutableListOf<String>()
            for (raw in text.lines()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith(HEADING.trim())) {
                    if (heading != null || paragraphs.isNotEmpty()) sections += Section(heading, paragraphs)
                    heading = line.removePrefix(HEADING.trim()).trim().ifEmpty { null }
                    paragraphs = mutableListOf()
                } else paragraphs += line
            }
            if (heading != null || paragraphs.isNotEmpty()) sections += Section(heading, paragraphs)
            return OrderedText(title, sections)
        }
    }
}

/**
 * Come si rimette in ordine una nota vocale. Senza il cervello di Alfred: i pezzi che Eurise Voce ha scritto tra una
 * pausa e l'altra diventano paragrafi (i più corti si uniscono al successivo), con la maiuscola e il punto finale.
 * Con il cervello: ogni paragrafo riceve punteggiatura e maiuscole giuste ([cleanPrompt]), poi titolo e titoletti
 * ([structurePrompt]). Ogni risposta si controlla e, se non torna, si tiene la versione senza cervello.
 */
object VoiceOrder {
    /** Un paragrafo più corto si unisce al pezzo che segue. */
    private const val MIN_WORDS = 14

    /** Quante parole si danno al cervello in una volta (la risposta ha al massimo circa 320 pezzi di parola). */
    const val CLEAN_WORDS = 120

    /** I pezzi della trascrizione grezza: Eurise Voce ne scrive uno per pausa, uno per riga. */
    fun pieces(transcript: String): List<String> = transcript.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** I paragrafi alle pause, senza cervello. */
    fun paragraphs(transcript: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (piece in pieces(transcript)) {
            if (current.isNotEmpty()) current.append(' ')
            current.append(piece)
            if (words(current.toString()) >= MIN_WORDS) {
                out += tidy(current.toString())
                current.clear()
            }
        }
        if (current.isNotBlank()) {
            val rest = current.toString()
            // Un ultimo pezzo cortissimo va con il paragrafo prima.
            if (out.isNotEmpty() && words(rest) < 5) out[out.lastIndex] = tidy(out.last().removeSuffix(".") + " " + rest)
            else out += tidy(rest)
        }
        return out
    }

    /** Il testo ordinato senza cervello: un solo gruppo di paragrafi, senza titolo. */
    fun local(transcript: String) = OrderedText("", listOf(OrderedText.Section(null, paragraphs(transcript)))).let {
        if (it.isEmpty) OrderedText("", emptyList()) else it
    }

    /** Maiuscola all'inizio, spazi doppi via, punto alla fine se manca. */
    fun tidy(text: String): String {
        val t = text.replace(Regex("\\s+"), " ").replace(Regex(" ([,.;:!?])"), "$1").trim()
        if (t.isEmpty()) return t
        val first = t.replaceFirstChar { it.uppercase() }
        return if (first.last() in ".!?…:;\"»)") first else "$first."
    }

    fun words(text: String) = text.split(Regex("\\s+")).count { it.isNotBlank() }

    /** I paragrafi raccolti in gruppi di circa [CLEAN_WORDS] parole, per [cleanPrompt]. */
    fun groups(paragraphs: List<String>): List<List<String>> {
        val out = mutableListOf<MutableList<String>>()
        var count = 0
        for (p in paragraphs) {
            val n = words(p)
            if (out.isEmpty() || count + n > CLEAN_WORDS && out.last().isNotEmpty()) {
                out += mutableListOf<String>()
                count = 0
            }
            out.last() += p
            count += n
        }
        return out
    }

    fun cleanPrompt(paragraphs: List<String>): String =
        "Questi sono ${if (paragraphs.size == 1) "un paragrafo" else "${paragraphs.size} paragrafi"} di una nota dettata a voce " +
            "e trascritta in automatico. Riscrivili con la punteggiatura e le maiuscole giuste. Togli solo le esitazioni " +
            "(ehm, uhm, parole ripetute) e correggi una parola solo se è chiaramente capita male. Non riassumere, non " +
            "aggiungere niente e non commentare. Tieni un paragrafo per riga, nello stesso ordine, e rispondi solo con il testo.\n\n" +
            paragraphs.joinToString("\n")

    /**
     * Il testo riscritto da [cleanPrompt], o null se non si può usare: troppo più corto o più lungo dell'originale
     * (il cervello ha riassunto o aggiunto), o con un numero di paragrafi diverso.
     */
    fun acceptClean(original: List<String>, answer: String?): List<String>? {
        if (answer.isNullOrBlank()) return null
        val lines = answer.lines()
            .map { it.trim().removePrefix("- ").trim().trim('"', '«', '»') }
            .filter { it.isNotEmpty() }
            .filterNot { PREAMBLE.containsMatchIn(it) && it.endsWith(":") }
        if (lines.isEmpty()) return null
        val before = original.sumOf { words(it) }
        val after = lines.sumOf { words(it) }
        if (before == 0 || after < before * 0.6 || after > before * 1.4 + 3) return null
        val fixed = if (lines.size == original.size) lines else if (original.size == 1) listOf(lines.joinToString(" ")) else return null
        return fixed.map { tidy(it) }
    }

    private val PREAMBLE = Regex("^(ecco|testo|ecco il testo|versione)", RegexOption.IGNORE_CASE)

    /** Quanto di ogni paragrafo si mostra al cervello per titolo e titoletti. */
    private const val PEEK_WORDS = 30
    private const val PEEK_PARAGRAPHS = 24

    fun structurePrompt(paragraphs: List<String>): String =
        "Questi sono i paragrafi di una nota dettata a voce, numerati (dei più lunghi c'è solo l'inizio).\n\n" +
            paragraphs.take(PEEK_PARAGRAPHS).mapIndexed { i, p -> "[${i + 1}] ${peek(p)}" }.joinToString("\n") +
            "\n\nScrivi un titolo breve per tutta la nota, al massimo sei parole. Poi, solo se i paragrafi parlano di " +
            "argomenti diversi, dai un titoletto di una o due parole al paragrafo dove comincia ogni argomento. " +
            "Rispondi solo così, una cosa per riga:\nTITOLO: …\n1: titoletto\n3: titoletto"

    private fun peek(p: String): String {
        val w = p.split(' ')
        return if (w.size <= PEEK_WORDS) p else w.take(PEEK_WORDS).joinToString(" ") + "…"
    }

    /**
     * Mette insieme titolo e titoletti dalla risposta a [structurePrompt]. Titoletti troppo lunghi, numeri fuori
     * dall'elenco o un solo titoletto su pochi paragrafi si scartano.
     */
    fun structure(paragraphs: List<String>, answer: String?): OrderedText {
        var title = ""
        val headings = sortedMapOf<Int, String>()
        answer.orEmpty().lines().forEach { raw ->
            val line = raw.trim().trim('*').trim()
            TITLE.find(line)?.let { m -> title = clean(m.groupValues[1]); return@forEach }
            HEAD.find(line)?.let { m ->
                val n = m.groupValues[1].toInt()
                val h = clean(m.groupValues[2])
                if (n in 1..paragraphs.size && h.isNotEmpty() && words(h) <= 5) headings[n] = h
            }
        }
        if (words(title) > 9) title = title.split(' ').take(9).joinToString(" ")
        val useful = headings.size >= 2 || headings.size == 1 && paragraphs.size >= 3 && headings.firstKey() > 1
        val sections = mutableListOf<OrderedText.Section>()
        var heading: String? = null
        var current = mutableListOf<String>()
        paragraphs.forEachIndexed { i, p ->
            val h = if (useful) headings[i + 1] else null
            if (h != null && (current.isNotEmpty() || heading != null)) {
                sections += OrderedText.Section(heading, current)
                current = mutableListOf()
            }
            if (h != null) heading = h
            current += p
        }
        if (current.isNotEmpty() || heading != null) sections += OrderedText.Section(heading, current)
        return OrderedText(title, sections)
    }

    private fun clean(s: String) = s.trim().trim('"', '«', '»', '*', '.').trim()

    private val TITLE = Regex("^titolo\\s*[:：]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val HEAD = Regex("^\\[?(\\d{1,2})\\]?\\s*[:.)\\-–]\\s*(.+)$")

    /** Quante parole per pezzo da riassumere. */
    const val SUMMARY_WORDS = 600

    fun summaryPrompt(text: String): String =
        "Questa è una nota dettata a voce. Riassumila in tre righe brevi al massimo, con i fatti e le decisioni che " +
            "contano. Una riga per punto, ognuna che comincia con «- ». Rispondi solo con le righe.\n\n$text"

    /** Le righe del riassunto, senza trattini; vuoto se la risposta non ne ha. */
    fun summaryLines(answer: String?): List<String> = answer.orEmpty().lines()
        .map { it.trim() }
        .filter { it.startsWith("-") || it.startsWith("•") || it.startsWith("*") || Regex("^\\d+[.)]").containsMatchIn(it) }
        .map { tidy(it.trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "")) }
        .filter { it.length > 1 }
        .take(5)
        .ifEmpty {
            // Risposta senza trattini: si tengono al massimo tre frasi.
            answer.orEmpty().trim().split(Regex("(?<=[.!?])\\s+")).map { tidy(it) }.filter { words(it) >= 2 }.take(3)
        }

    /** Il testo a pezzi di circa [SUMMARY_WORDS] parole, per riassumere anche una nota lunga. */
    fun summaryChunks(text: String): List<String> {
        val w = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        return w.chunked(SUMMARY_WORDS).map { it.joinToString(" ") }
    }
}

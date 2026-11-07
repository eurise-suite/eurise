package eu.eurise.feature.notes.doc

/**
 * Il contenuto di una nota come sequenza di blocchi. È il modello su cui lavora l'editor; su disco
 * diventa HTML (vedi [NoteHtml]), un formato aperto che si legge con qualsiasi browser.
 */
sealed interface Block {
    /** Identità stabile del blocco durante la modifica (non viene salvata). */
    val id: Long
}

/** I tipi di blocco di testo; si passa dall'uno all'altro senza perdere il testo. */
enum class TextKind { Paragraph, Heading, Subheading, Bullet, Numbered, Check }

/**
 * Un blocco di testo con stili in linea.
 *
 * @property checked stato della voce, solo per [TextKind.Check].
 * @property taskId attività collegata alla voce, se è stata aggiunta anche tra le Attività.
 */
data class TextBlock(
    override val id: Long,
    val kind: TextKind,
    val text: RichText,
    val checked: Boolean = false,
    val taskId: Long? = null,
) : Block

/** Tabella di testo semplice; la prima riga è l'intestazione. Tutte le righe hanno lo stesso numero di celle. */
data class TableBlock(override val id: Long, val rows: List<List<String>>) : Block {
    val columns: Int get() = rows.firstOrNull()?.size ?: 0
}

/** Collegamento a un'altra nota. [title] è quello noto al momento del salvataggio. */
data class NoteLinkBlock(override val id: Long, val noteId: Long, val title: String) : Block

/** Linea di separazione. */
data class DividerBlock(override val id: Long) : Block

/** Stili in linea. Colore ed evidenziatore portano il nome di una tinta della suite (per esempio "Salvia"). */
sealed interface Mark {
    data object Bold : Mark
    data object Italic : Mark
    data object Underline : Mark
    data object Strike : Mark
    data class Color(val tint: String) : Mark
    data class Highlight(val tint: String) : Mark

    /** Colore ed evidenziatore si escludono a vicenda dentro la loro famiglia: un testo ha un solo colore. */
    fun sameFamily(other: Mark): Boolean = when (this) {
        is Color -> other is Color
        is Highlight -> other is Highlight
        else -> this == other
    }
}

/** Uno stile applicato ai caratteri da [start] (incluso) a [end] (escluso). */
data class Span(val start: Int, val end: Int, val mark: Mark)

/** Testo con stili. Gli span sono sempre normalizzati: non vuoti, dentro il testo, senza doppioni sovrapposti. */
data class RichText(val text: String = "", val spans: List<Span> = emptyList()) {

    /** Gli stili che coprono tutti i caratteri da [start] a [end]; con intervallo vuoto, quelli del carattere prima. */
    fun marksIn(start: Int, end: Int): Set<Mark> {
        if (text.isEmpty()) return emptySet()
        if (start >= end) {
            val at = (start - 1).coerceIn(0, text.length - 1)
            return spans.filter { at >= it.start && at < it.end }.map { it.mark }.toSet()
        }
        return spans.map { it.mark }.toSet().filter { mark ->
            (start until end).all { i -> spans.any { it.mark == mark && i >= it.start && i < it.end } }
        }.toSet()
    }

    companion object {
        fun plain(text: String) = RichText(text)
    }
}

/** Mette in ordine gli span: taglia ai limiti del testo, unisce quelli uguali che si toccano, toglie i vuoti. */
internal fun normalize(length: Int, spans: List<Span>): List<Span> {
    val out = mutableListOf<Span>()
    spans
        .map { Span(it.start.coerceIn(0, length), it.end.coerceIn(0, length), it.mark) }
        .filter { it.end > it.start }
        .groupBy { it.mark }
        .forEach { (mark, list) ->
            var current: Span? = null
            for (s in list.sortedBy { it.start }) {
                val c = current
                current = when {
                    c == null -> s
                    s.start <= c.end -> Span(c.start, maxOf(c.end, s.end), mark)
                    else -> { out += c; s }
                }
            }
            current?.let { out += it }
        }
    return out.sortedWith(compareBy({ it.start }, { it.end }))
}

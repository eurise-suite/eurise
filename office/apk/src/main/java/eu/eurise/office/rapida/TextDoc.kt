package eu.eurise.office.rapida

/** Un tratto di testo con la sua formattazione essenziale, come la mostra la vista rapida. */
data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

enum class Align { Start, Center, End, Justify }

sealed interface Block

/**
 * Un paragrafo. [id] è la sua posizione tra tutti i paragrafi del file (anche quelli nelle tabelle): con quello la
 * correzione ritrova il punto esatto. [level]: 0 testo, 1…6 titolo di quel livello. [editable] è falso quando dentro
 * ci sono cose che la vista rapida non sa rimettere al loro posto (immagini, campi, revisioni, note): allora si corregge
 * nella modifica completa.
 */
data class Para(
    val id: Int,
    val spans: List<Span>,
    val level: Int = 0,
    val align: Align = Align.Start,
    val bullet: Boolean = false,
    val editable: Boolean = true,
    val comments: List<String> = emptyList(),
) : Block {
    val text: String get() = spans.joinToString("") { it.text }
    val allBold: Boolean get() = spans.any { it.text.isNotBlank() } && spans.filter { it.text.isNotBlank() }.all { it.bold }
    val allItalic: Boolean get() = spans.any { it.text.isNotBlank() } && spans.filter { it.text.isNotBlank() }.all { it.italic }
    val allUnderline: Boolean get() = spans.any { it.text.isNotBlank() } && spans.filter { it.text.isNotBlank() }.all { it.underline }
}

/** Una tabella: righe di celle, ogni cella con i suoi paragrafi. */
data class Table(val rows: List<List<List<Para>>>) : Block

/** Una pagina disegnata o un'immagine che la vista rapida non mostra: un segnaposto. */
data class Placeholder(val what: String) : Block

/**
 * Un documento di testo letto per la vista rapida. [formatting]: grassetto, corsivo e sottolineato si possono cambiare
 * (oggi solo Word). [commenting]: si possono aggiungere commenti.
 */
data class TextDoc(val blocks: List<Block>, val formatting: Boolean, val commenting: Boolean) {
    fun paragraphs(): List<Para> = blocks.flatMap { b ->
        when (b) {
            is Para -> listOf(b)
            is Table -> b.rows.flatten().flatten()
            is Placeholder -> emptyList()
        }
    }
}

/** Grassetto, corsivo, sottolineato da applicare a tutto il paragrafo; null lascia com'è. */
data class Style(val bold: Boolean? = null, val italic: Boolean? = null, val underline: Boolean? = null) {
    val none: Boolean get() = bold == null && italic == null && underline == null
}

/** Un formato di testo: Word (.docx) o OpenDocument (.odt). */
interface TextFormat {
    fun read(bundle: Bundle): TextDoc
    /** Il paragrafo [id] con il testo [text] e lo [style]; il resto del file non cambia. */
    fun edit(bundle: Bundle, id: Int, text: String, style: Style = Style()): Bundle
    /** Un paragrafo nuovo dopo [id], con lo stesso stile e il testo [text]. */
    fun insertAfter(bundle: Bundle, id: Int, text: String): Bundle
    /** Un commento di [author] sul paragrafo [id]. */
    fun comment(bundle: Bundle, id: Int, text: String, author: String): Bundle
}

class NotEditable(message: String) : Exception(message)

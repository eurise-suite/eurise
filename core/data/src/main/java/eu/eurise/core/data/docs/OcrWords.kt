package eu.eurise.core.data.docs

/** Una parola letta da Eurise Leggi, con il suo riquadro in pixel dell'immagine e la riga a cui appartiene. */
data class OcrWord(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val line: Int,
    val confidence: Float,
)

/** Le parole di una pagina dal formato TSV di Tesseract (livello, pagina, blocco, paragrafo, riga, parola, riquadro, fiducia, testo). */
object OcrWords {
    private const val WORD_LEVEL = 5

    fun fromTsv(tsv: String): List<OcrWord> = tsv.lineSequence().mapNotNull { row ->
        val p = row.split('\t')
        if (p.size < 12 || p[0].toIntOrNull() != WORD_LEVEL) return@mapNotNull null
        val text = p.subList(11, p.size).joinToString("\t").trim()
        if (text.isEmpty()) return@mapNotNull null
        val n = p.subList(2, 10).map { it.toIntOrNull() ?: return@mapNotNull null }
        val (block, par, line) = Triple(n[0], n[1], n[2])
        val (left, top, width, height) = listOf(n[4], n[5], n[6], n[7])
        if (width <= 0 || height <= 0) return@mapNotNull null
        OcrWord(text, left, top, left + width, top + height, (block * 1000 + par) * 1000 + line, p[10].toFloatOrNull() ?: 0f)
    }.toList()
}

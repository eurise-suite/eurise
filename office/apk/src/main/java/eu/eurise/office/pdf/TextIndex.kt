package eu.eurise.office.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/** Una lettera della pagina con il suo riquadro nello spazio PDF. */
data class Glyph(val text: String, val box: PdfRect, val line: Int)

/**
 * Il testo di una pagina con la posizione di ogni lettera, in ordine di lettura: serve a evidenziare e sottolineare
 * trascinando il dito, a cercare nel PDF e a trovare i dati da oscurare. Le pagine scansionate non hanno testo.
 */
class PageText(val glyphs: List<Glyph>) {
    /** Il testo della pagina, un carattere di [glyphs] per posizione (gli spazi tra parole sono glifi anch'essi). */
    val text: String = glyphs.joinToString("") { it.text }

    /** Indice in [text] di ciascun glifo. */
    private val starts: IntArray = IntArray(glyphs.size).also { arr ->
        var pos = 0
        glyphs.forEachIndexed { i, g -> arr[i] = pos; pos += g.text.length }
    }

    fun glyphAt(charIndex: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= charIndex) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Il glifo più vicino a un punto PDF (prima sulla stessa riga, poi il più vicino in assoluto). */
    fun nearest(x: Float, y: Float): Int? {
        if (glyphs.isEmpty()) return null
        var best = 0
        var bestScore = Float.MAX_VALUE
        glyphs.forEachIndexed { i, g ->
            val dy = when {
                y < g.box.bottom -> g.box.bottom - y
                y > g.box.top -> y - g.box.top
                else -> 0f
            }
            val dx = when {
                x < g.box.left -> g.box.left - x
                x > g.box.right -> x - g.box.right
                else -> 0f
            }
            val score = dy * 4f + dx
            if (score < bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }

    /** Un riquadro per ogni riga toccata dai glifi da [from] a [to] (estremi compresi, in qualsiasi ordine). */
    fun lineBoxes(from: Int, to: Int): List<PdfRect> {
        if (glyphs.isEmpty()) return emptyList()
        val a = minOf(from, to).coerceIn(0, glyphs.lastIndex)
        val b = maxOf(from, to).coerceIn(0, glyphs.lastIndex)
        return glyphs.subList(a, b + 1).filter { it.text.isNotBlank() }.groupBy { it.line }.values.map { line ->
            line.map { it.box }.reduce { acc, r -> acc.union(r) }
        }
    }

    /** I riquadri (uno per riga) del testo tra i caratteri [start] e [end] di [text]. */
    fun boxesForRange(start: Int, end: Int): List<PdfRect> =
        if (end <= start) emptyList() else lineBoxes(glyphAt(start), glyphAt(end - 1))

    companion object {
        /** Legge tutte le pagine del documento (una volta, in un lavoro in background). */
        fun read(doc: PDDocument): List<PageText> = (1..doc.numberOfPages).map { readPage(doc, it) }

        fun readPage(doc: PDDocument, pageNumber: Int): PageText {
            val page = doc.getPage(pageNumber - 1)
            val crop = page.cropBox
            val glyphs = mutableListOf<Glyph>()
            var line = 0
            var lastBaseline = Float.NaN
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                    val positions = textPositions ?: return
                    for (p in positions) {
                        val baseline = p.yDirAdj
                        if (!lastBaseline.isNaN() && kotlin.math.abs(baseline - lastBaseline) > p.heightDir * 0.6f) line++
                        lastBaseline = baseline
                        val height = maxOf(p.heightDir, p.fontSizeInPt * 0.7f, 1f)
                        val left = crop.lowerLeftX + p.xDirAdj
                        val top = crop.lowerLeftY + crop.height - (baseline - height * 1.05f)
                        val bottom = crop.lowerLeftY + crop.height - (baseline + height * 0.25f)
                        glyphs += Glyph(p.unicode ?: "", PdfRect(left, bottom, left + maxOf(p.widthDirAdj, 1f), top), line)
                    }
                }

                override fun writeWordSeparator() {
                    glyphs.lastOrNull()?.let { glyphs += Glyph(" ", it.box.copy(left = it.box.right), it.line) }
                }

                override fun writeLineSeparator() {
                    glyphs.lastOrNull()?.let { glyphs += Glyph("\n", it.box.copy(left = it.box.right), it.line) }
                }
            }
            stripper.sortByPosition = true
            stripper.startPage = pageNumber
            stripper.endPage = pageNumber
            runCatching { stripper.getText(doc) }
            return PageText(glyphs)
        }
    }
}

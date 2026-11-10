package eu.eurise.core.data.docs

import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Locale

/**
 * PDF "cercabile" senza librerie: ogni pagina è la foto della scansione (JPEG così com'è, DCTDecode) con sopra il testo letto,
 * invisibile (modo 3 del testo) e messo parola per parola dove sta nell'immagine. Così il PDF si vede come la scansione ma
 * si può cercare, selezionare e copiare; File e la ricerca di Eurise ne leggono il testo.
 * Le pagine sono larghe [PAGE_WIDTH] punti (un A4), alte in proporzione all'immagine.
 */
object SearchablePdf {
    const val PAGE_WIDTH = 595.0

    /** Una pagina: [jpeg] largo [width]×[height] pixel ([gray] se in bianco e nero), con le parole lette. */
    class Page(val jpeg: ByteArray, val width: Int, val height: Int, val words: List<OcrWord>, val gray: Boolean = false)

    /**
     * Pagina da un JPEG: misure e colori letti dall'intestazione del file; le parole, lette su un'immagine di
     * [wordsWidth] pixel di larghezza (Eurise Leggi può averla ridotta), si riportano ai pixel del JPEG. Null se il file
     * non è un JPEG leggibile.
     */
    fun pageFromJpeg(jpeg: ByteArray, words: List<OcrWord>, wordsWidth: Int): Page? {
        val (w, h, components) = jpegInfo(jpeg) ?: return null
        val k = if (wordsWidth > 0) w.toDouble() / wordsWidth else 1.0
        val scaled = if (k == 1.0) words else words.map {
            it.copy(left = (it.left * k).toInt(), top = (it.top * k).toInt(), right = (it.right * k).toInt(), bottom = (it.bottom * k).toInt())
        }
        return Page(jpeg, w, h, scaled, gray = components == 1)
    }

    /** Larghezza, altezza e numero di componenti di colore di un JPEG (dal segmento SOF), o null. */
    internal fun jpegInfo(b: ByteArray): Triple<Int, Int, Int>? {
        if (b.size < 4 || (b[0].toInt() and 0xFF) != 0xFF || (b[1].toInt() and 0xFF) != 0xD8) return null
        var i = 2
        while (i + 9 < b.size) {
            if ((b[i].toInt() and 0xFF) != 0xFF) return null
            val marker = b[i + 1].toInt() and 0xFF
            val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            // SOF0…SOF15, tranne DHT (C4), JPG (C8) e DAC (CC).
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val h = ((b[i + 5].toInt() and 0xFF) shl 8) or (b[i + 6].toInt() and 0xFF)
                val w = ((b[i + 7].toInt() and 0xFF) shl 8) or (b[i + 8].toInt() and 0xFF)
                val c = b[i + 9].toInt() and 0xFF
                return if (w > 0 && h > 0) Triple(w, h, c) else null
            }
            if (marker == 0xD8 || marker in 0xD0..0xD7 || marker == 0x01) { i += 2; continue }
            i += 2 + len
        }
        return null
    }

    private val winAnsi: Charset = Charset.forName("windows-1252")

    fun write(pages: List<Page>, out: OutputStream, title: String? = null) {
        val w = Writer(out)
        val pageCount = pages.size
        // Numeri degli oggetti: 1 catalogo, 2 albero delle pagine, 3 font, 4 informazioni, poi tre per pagina.
        fun pageObj(i: Int) = 5 + i * 3
        w.obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
        w.obj(2, "<< /Type /Pages /Count $pageCount /Kids [${(0 until pageCount).joinToString(" ") { "${pageObj(it)} 0 R" }}] >>")
        w.obj(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        w.obj(4, "<< /Producer (Eurise Leggi)" + (title?.let { " /Title ${literal(it)}" } ?: "") + " >>")
        pages.forEachIndexed { i, page ->
            val n = pageObj(i)
            val pageH = PAGE_WIDTH * page.height / page.width
            w.obj(n, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(PAGE_WIDTH)} ${num(pageH)}] " +
                "/Resources << /XObject << /Im0 ${n + 1} 0 R >> /Font << /F1 3 0 R >> >> /Contents ${n + 2} 0 R >>")
            w.stream(n + 1, "<< /Type /XObject /Subtype /Image /Width ${page.width} /Height ${page.height} " +
                "/ColorSpace /${if (page.gray) "DeviceGray" else "DeviceRGB"} /BitsPerComponent 8 /Filter /DCTDecode", page.jpeg)
            w.stream(n + 2, "<<", content(page, pageH))
        }
        w.finish(size = 5 + pageCount * 3, info = 4)
    }

    /** Il contenuto di una pagina: l'immagine a tutta pagina e il testo invisibile, riga per riga. */
    internal fun content(page: Page, pageH: Double): ByteArray {
        val scale = PAGE_WIDTH / page.width
        val sb = StringBuilder()
        sb.append("q ${num(PAGE_WIDTH)} 0 0 ${num(pageH)} 0 0 cm /Im0 Do Q\n")
        val lines = page.words.groupBy { it.line }.values
        if (lines.isNotEmpty()) sb.append("BT 3 Tr\n")
        for (line in lines) {
            // Stessa altezza e stessa base per tutta la riga: chi estrae il testo la riconosce come una riga sola.
            val top = line.minOf { it.top }
            val bottom = line.maxOf { it.bottom }
            val size = ((bottom - top) * scale).coerceAtLeast(1.0)
            val baseline = pageH - bottom * scale + size * 0.2
            sb.append("/F1 ${num(size)} Tf\n")
            line.sortedBy { it.left }.forEach { word ->
                val natural = textWidth(word.text) * size / 1000
                val wanted = (word.right - word.left) * scale
                val stretch = if (natural > 0) (wanted / natural * 100).coerceIn(10.0, 1000.0) else 100.0
                sb.append("${num(stretch)} Tz 1 0 0 1 ${num(word.left * scale)} ${num(baseline)} Tm ${literal(word.text)} Tj\n")
            }
        }
        if (lines.isNotEmpty()) sb.append("ET\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    /** Stringa letterale del PDF in WinAnsi; i caratteri che WinAnsi non ha diventano "?". */
    internal fun literal(text: String): String {
        val bytes = text.map { c -> if (c.toString().let { winAnsi.newEncoder().canEncode(it) }) c else '?' }
            .joinToString("").toByteArray(winAnsi)
        val sb = StringBuilder("(")
        bytes.forEach { b ->
            val v = b.toInt() and 0xFF
            when {
                v == '('.code || v == ')'.code || v == '\\'.code -> sb.append('\\').append(v.toChar())
                v < 32 || v > 126 -> sb.append('\\').append(Integer.toOctalString(v).padStart(3, '0'))
                else -> sb.append(v.toChar())
            }
        }
        return sb.append(')').toString()
    }

    /** Larghezza del testo in Helvetica, in millesimi della grandezza del carattere. */
    internal fun textWidth(text: String): Int = text.sumOf { c ->
        val i = c.code - 32
        if (i in HELVETICA.indices) HELVETICA[i] else 556
    }

    private fun num(v: Double): String = String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.')

    /** Larghezze di Helvetica per i caratteri da 32 a 126 (metriche standard di Adobe). */
    private val HELVETICA = intArrayOf(
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
        333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584,
    )

    /** Scrive gli oggetti tenendo il conto dei byte per la tabella finale (xref). */
    private class Writer(private val out: OutputStream) {
        private var pos = 0L
        private val offsets = HashMap<Int, Long>()

        init {
            raw("%PDF-1.4\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
        }

        fun obj(n: Int, body: String) {
            offsets[n] = pos
            raw("$n 0 obj\n$body\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }

        /** [dictStart] è il dizionario senza la chiusura: si aggiunge /Length. */
        fun stream(n: Int, dictStart: String, data: ByteArray) {
            offsets[n] = pos
            raw("$n 0 obj\n$dictStart /Length ${data.size} >>\nstream\n".toByteArray(Charsets.ISO_8859_1))
            raw(data)
            raw("\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }

        fun finish(size: Int, info: Int) {
            val xref = pos
            val sb = StringBuilder("xref\n0 $size\n0000000000 65535 f \n")
            for (n in 1 until size) sb.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[n] ?: 0L))
            sb.append("trailer\n<< /Size $size /Root 1 0 R /Info $info 0 R >>\nstartxref\n$xref\n%%EOF\n")
            raw(sb.toString().toByteArray(Charsets.ISO_8859_1))
            out.flush()
        }

        private fun raw(b: ByteArray) {
            out.write(b)
            pos += b.size
        }
    }
}

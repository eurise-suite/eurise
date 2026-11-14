package eu.eurise.feature.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import eu.eurise.core.data.docs.ReadPage
import eu.eurise.core.data.docs.SearchablePdf
import android.graphics.pdf.PdfDocument
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Aspetti della pagina (tavole ScanPagine e ScanPagina): Foto, Perfetta (Document), Grigi, Bianco e nero, Colore. */
enum class ScanMode { Document, BlackWhite, Photo, Qr, Gray, Color }

/** Raddrizza, migliora e mette le pagine in PDF, usando solo la grafica di sistema. */
object PageProcessor {
    private const val MAX_SIDE = 2000
    private const val PDF_WIDTH = 595

    /** Ritaglia [quad] e lo porta in piano; senza angoli tiene l'immagine intera. */
    fun straighten(source: Bitmap, quad: Quad?): Bitmap {
        if (quad == null) return source
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val p = quad.points.flatMap { listOf(it.x * w, it.y * h) }.toFloatArray()
        val outW = max(hypot(p[2] - p[0], p[3] - p[1]), hypot(p[4] - p[6], p[5] - p[7]))
        val outH = max(hypot(p[6] - p[0], p[7] - p[1]), hypot(p[4] - p[2], p[5] - p[3]))
        val scale = minOf(1f, MAX_SIDE / max(outW, outH))
        val width = (outW * scale).roundToInt().coerceAtLeast(1)
        val height = (outH * scale).roundToInt().coerceAtLeast(1)
        val dst = floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat())
        val matrix = Matrix().apply { setPolyToPoly(p, 0, dst, 0, 4) }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** [strength] è «Carta più bianca» (0–1) per Perfetta, Grigi e Colore. */
    fun applyMode(page: Bitmap, mode: ScanMode, strength: Float = 1f): Bitmap = when (mode) {
        ScanMode.Photo, ScanMode.Qr -> page
        // Carta bianca e neutra, ombre tolte, testo nero, colori veri tenuti (DocumentFilter, provato senza telefono).
        ScanMode.Document -> filtered(page) { p, w, h -> DocumentFilter.document(p, w, h, strength) }
        ScanMode.Gray -> filtered(page) { p, w, h -> DocumentFilter.gray(p, w, h, strength) }
        ScanMode.Color -> filtered(page) { p, w, h -> DocumentFilter.color(p, w, h, strength) }
        ScanMode.BlackWhite -> filtered(page) { p, w, h -> DocumentFilter.blackWhite(p, w, h) }
    }

    /** Gira la pagina di [turns] quarti di giro in senso orario. */
    fun rotate(page: Bitmap, turns: Int): Bitmap {
        val t = ((turns % 4) + 4) % 4
        if (t == 0) return page
        return Bitmap.createBitmap(page, 0, 0, page.width, page.height, Matrix().apply { postRotate(90f * t) }, true)
    }

    /** Luminosità ridotta a circa [columns] colonne, per [Sharpness]. */
    fun luma(page: Bitmap, columns: Int = 600): Triple<IntArray, Int, Int> {
        val w = minOf(columns, page.width)
        val h = (page.height.toLong() * w / page.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(page, w, h, true)
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        return Triple(IntArray(w * h) { i -> val c = px[i]; (299 * ((c shr 16) and 0xFF) + 587 * ((c shr 8) and 0xFF) + 114 * (c and 0xFF)) / 1000 }, w, h)
    }

    private fun filtered(page: Bitmap, filter: (IntArray, Int, Int) -> IntArray): Bitmap {
        val w = page.width
        val h = page.height
        val pixels = IntArray(w * h)
        page.getPixels(pixels, 0, w, 0, 0, w, h)
        return Bitmap.createBitmap(filter(pixels, w, h), w, h, Bitmap.Config.ARGB_8888)
    }

    /** Una pagina per immagine, larga come un A4 (595 punti). */
    fun writePdf(pages: List<File>, target: File) {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { i, file ->
                val bitmap = BitmapFactory.decodeFile(file.path) ?: return@forEachIndexed
                val height = (PDF_WIDTH * bitmap.height.toFloat() / bitmap.width).roundToInt().coerceAtLeast(1)
                val page = document.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, height, i + 1).create())
                page.canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, PDF_WIDTH, height), Paint(Paint.FILTER_BITMAP_FLAG))
                document.finishPage(page)
                bitmap.recycle()
            }
            target.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    /**
     * PDF cercabile: le pagine come sono (JPEG) con il testo letto da Eurise Leggi sopra, invisibile. False se una
     * pagina non si può usare (allora si ripiega sul PDF di sole immagini).
     */
    fun writeSearchablePdf(pages: List<File>, read: List<ReadPage>, target: File, title: String): Boolean {
        if (pages.size != read.size) return false
        val built = pages.mapIndexed { i, file ->
            SearchablePdf.pageFromJpeg(file.readBytes(), read[i].words, read[i].width) ?: return false
        }
        target.outputStream().buffered().use { SearchablePdf.write(built, it, title) }
        return true
    }

}

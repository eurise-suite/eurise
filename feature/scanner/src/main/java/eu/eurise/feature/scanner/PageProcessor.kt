package eu.eurise.feature.scanner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import eu.eurise.core.data.docs.ReadPage
import eu.eurise.core.data.docs.SearchablePdf
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

enum class ScanMode { Document, BlackWhite, Photo, Qr }

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

    fun applyMode(page: Bitmap, mode: ScanMode): Bitmap = when (mode) {
        ScanMode.Photo, ScanMode.Qr -> page
        ScanMode.Document -> enhance(page)
        ScanMode.BlackWhite -> blackWhite(page)
    }

    /** Carta bianca e neutra, ombre tolte, testo nero, colori veri tenuti (DocumentFilter, provato senza telefono). */
    private fun enhance(page: Bitmap): Bitmap = filtered(page, DocumentFilter::document)

    private fun blackWhite(page: Bitmap): Bitmap = filtered(page, DocumentFilter::blackWhite)

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

    /** Mette il PDF in Documenti/[folder] (di base Documenti/Eurise/Scansioni), dove lo trovano anche le altre app. */
    fun saveToPhone(context: Context, pdf: File, name: String, folder: String = "Eurise/Scansioni"): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + folder)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
            resolver.openOutputStream(uri)?.use { out -> pdf.inputStream().use { it.copyTo(out) } } ?: return false
            return true
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), folder).apply { mkdirs() }
        pdf.copyTo(File(dir, name), overwrite = false)
        return true
    }
}

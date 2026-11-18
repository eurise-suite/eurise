package eu.eurise.gallery.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Size
import eu.eurise.gallery.logic.DayGroup
import java.io.File

/**
 * Il diario come PDF (A4): titolo, poi per ogni giorno data e luogo, la nota e le foto due per riga. Lo fa il sistema
 * (`PdfDocument`), senza librerie e sul telefono; si condivide o si salva in Eurise File dal foglio Condividi.
 */
fun diaryPdf(context: Context, title: String, days: List<DayGroup>, heading: (DayGroup) -> String, notes: Map<String, String>): Intent {
    val doc = PdfDocument()
    val pageW = 595; val pageH = 842; val margin = 40f; val gap = 12f
    val colW = (pageW - 2 * margin - gap) / 2
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(28, 30, 34) }
    val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(28, 30, 34) }
    val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f; color = Color.rgb(60, 64, 70) }
    val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    var pageNo = 0
    var page: PdfDocument.Page? = null
    var y = 0f
    fun canvas(): Canvas = page!!.canvas
    fun newPage() {
        page?.let(doc::finishPage)
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNo).create())
        y = margin
    }
    fun ensure(h: Float) { if (page == null || y + h > pageH - margin) newPage() }
    fun text(s: String, paint: TextPaint, after: Float) {
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, (pageW - 2 * margin).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        ensure(layout.height.toFloat())
        canvas().save(); canvas().translate(margin, y); layout.draw(canvas()); canvas().restore()
        y += layout.height + after
    }
    try {
        newPage()
        text(title, titlePaint, 18f)
        for (day in days) {
            ensure(80f)
            text(heading(day), headPaint, 6f)
            notes[day.date.toString()]?.takeIf { it.isNotBlank() }?.let { text(it.trim(), notePaint, 10f) }
            day.items.chunked(2).forEach { row ->
                val pics = row.map { item ->
                    runCatching { context.contentResolver.loadThumbnail(Uri.parse(item.uri), Size(900, 900), null) }.getOrNull()
                }
                val rowH = pics.maxOf { b -> if (b == null) 0f else minOf(colW * b.height / b.width, colW * 1.25f) }
                if (rowH <= 0f) return@forEach
                ensure(rowH)
                pics.forEachIndexed { i, b ->
                    if (b == null) return@forEachIndexed
                    val h = minOf(colW * b.height / b.width, colW * 1.25f)
                    val w = h * b.width / b.height
                    val x = margin + i * (colW + gap) + (colW - w) / 2
                    canvas().drawBitmap(b, null, RectF(x, y, x + w, y + h), imagePaint)
                    b.recycle()
                }
                y += rowH + gap
            }
            y += 10f
        }
        page?.let(doc::finishPage)
        val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Diario" }
        val file = File(dir, "$safe.pdf")
        file.outputStream().use(doc::writeTo)
        val uri = context.fileUri(file)
        val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TITLE, safe)
            .setType("application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        return Intent.createChooser(send, null)
    } finally {
        doc.close()
    }
}


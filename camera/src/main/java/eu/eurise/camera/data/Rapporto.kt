package eu.eurise.camera.data

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rapporto fotografico (tavola della pagina «Fotocamera», 26 novembre 2026): le foto di una cartella di cantiere o di
 * cliente in un PDF A4, due per pagina, con data e ora sotto ciascuna. Si scrive in Documenti/Eurise/<cartella>.
 * Le immagini nel PDF sono ridisegnate: niente posizione né dati del telefono, si può mandare così com'è.
 */
object Rapporto {
    data class Photo(val uri: Uri, val taken: Long)

    /** Le foto della cartella [dest], dalla più vecchia (al massimo [MAX]). */
    fun photos(resolver: ContentResolver, dest: String): List<Photo> {
        val list = ArrayList<Photo>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(Destinations.relativePath(dest).trimEnd('/') + "/"),
            "${MediaStore.Images.Media.DATE_TAKEN} ASC",
        )?.use { c ->
            while (c.moveToNext() && list.size < MAX) {
                list += Photo(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0).toString()), c.getLong(1))
            }
        }
        return list
    }

    /**
     * Scrive il rapporto e ne restituisce l'indirizzo, o null. [title] è «Rapporto fotografico», [countLabel] dà
     * «N foto» e [periodLabel] il periodo («dal 3 al 26 novembre 2026»).
     */
    fun write(
        resolver: ContentResolver, dest: String, photos: List<Photo>,
        title: String, countLabel: (Int) -> String, periodLabel: (String, String) -> String,
    ): Uri? {
        if (photos.isEmpty()) return null
        val day = SimpleDateFormat("d MMMM yyyy", Locale.ITALY)
        val stamp = SimpleDateFormat("EEEE d MMMM yyyy · HH:mm", Locale.ITALY)
        val doc = PdfDocument()
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x14, 0x17, 0x1C) }
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x5A, 0x5E, 0x66); textSize = 9.5f }
        val image = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        try {
            var page: PdfDocument.Page? = null
            var slot = 0
            var number = 0
            var pageTop = 0f
            val perPage = 2
            for ((i, photo) in photos.withIndex()) {
                if (page == null) {
                    number++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create())
                    val c = page.canvas
                    var top = MARGIN.toFloat()
                    if (number == 1) {
                        ink.textSize = 20f; ink.typeface = Typeface.DEFAULT_BOLD
                        c.drawText("$title · $dest", MARGIN.toFloat(), top + 20f, ink)
                        ink.textSize = 11f; ink.typeface = Typeface.DEFAULT
                        val period = periodLabel(day.format(Date(photos.first().taken)), day.format(Date(photos.last().taken)))
                        c.drawText(countLabel(photos.size) + " · " + period, MARGIN.toFloat(), top + 38f, ink)
                        top += HEADER
                    }
                    c.drawText("$number", (W - MARGIN).toFloat() - soft.measureText("$number"), (H - 20).toFloat(), soft)
                    slot = 0
                    pageTop = top
                }
                val c = page.canvas
                val boxH = ((H - MARGIN - 30 - pageTop) / perPage).toInt()
                val y = (pageTop + slot * boxH).toInt()
                runCatching {
                    val bmp = PrimaDopo.decode(ImageDecoder.createSource(resolver, photo.uri), 1400)
                    val maxW = W - 2 * MARGIN; val maxH = boxH - 26
                    val k = minOf(maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height)
                    val w = (bmp.width * k).toInt(); val h = (bmp.height * k).toInt()
                    val x = MARGIN + (maxW - w) / 2
                    c.drawBitmap(bmp, null, Rect(x, y, x + w, y + h), image)
                    bmp.recycle()
                    c.drawText("${i + 1}. " + stamp.format(Date(photo.taken)), x.toFloat(), (y + h + 14).toFloat(), soft)
                }
                slot++
                if (slot == perPage) { doc.finishPage(page); page = null }
            }
            page?.let { doc.finishPage(it) }
            val name = "Rapporto_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date()) + ".pdf"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Eurise/$dest")
            }
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
            resolver.openOutputStream(uri, "w")?.use { doc.writeTo(it) } ?: return null
            return uri
        } finally {
            doc.close()
        }
    }

    /** A4 in punti. */
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 40
    private const val HEADER = 56
    const val MAX = 200
}

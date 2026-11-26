package eu.eurise.camera.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.io.OutputStream

/**
 * Prima/dopo (tavola della pagina «Fotocamera», 26 novembre 2026): la foto di prima resta in trasparenza sul mirino per
 * rifare la stessa inquadratura; dopo lo scatto si salva anche l'immagine affiancata, con le due date. Per chi lavora:
 * un muro prima e dopo l'intonaco, un giardino prima e dopo la potatura, un danno e la riparazione.
 */
object PrimaDopo {
    /** Lato lungo della copia della foto di prima tenuta per il mirino e per l'affiancata. */
    const val SIDE = 1600

    /** Copia ridotta e già dritta (orientamento applicato) della foto scelta in [out]. False se non si legge. */
    fun keep(resolver: ContentResolver, uri: Uri, out: File): Boolean = runCatching {
        val bitmap = decode(ImageDecoder.createSource(resolver, uri), SIDE)
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        true
    }.getOrDefault(false)

    /**
     * L'affiancata: [before] a sinistra, la foto nuova [after] a destra, alte uguali, con le etichette [beforeLabel] e
     * [afterLabel] (per esempio «Prima · 12 nov»). Scritta in JPEG su [sink].
     */
    fun compose(resolver: ContentResolver, before: File, after: Uri, beforeLabel: String, afterLabel: String, sink: OutputStream) {
        val a = decode(ImageDecoder.createSource(before), SIDE)
        val b = decode(ImageDecoder.createSource(resolver, after), SIDE)
        val h = minOf(a.height, b.height)
        val wa = a.width * h / a.height; val wb = b.width * h / b.height
        val gap = h / 80
        val out = Bitmap.createBitmap(wa + gap + wb, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(a, null, Rect(0, 0, wa, h), paint)
        canvas.drawBitmap(b, null, Rect(wa + gap, 0, wa + gap + wb, h), paint)
        label(canvas, beforeLabel, 0f, h)
        label(canvas, afterLabel, (wa + gap).toFloat(), h)
        out.compress(Bitmap.CompressFormat.JPEG, 92, sink)
        a.recycle(); b.recycle(); out.recycle()
    }

    private fun label(canvas: Canvas, text: String, x: Float, h: Int) {
        val size = h / 26f
        val text1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
        val pad = size * 0.5f
        val w = text1.measureText(text)
        val box = RectF(x + pad, pad, x + pad + w + 2 * pad, pad + size * 1.6f)
        canvas.drawRoundRect(box, size * 0.5f, size * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB314171C.toInt() })
        canvas.drawText(text, box.left + pad, box.bottom - size * 0.45f, text1)
    }

    /** Decodifica con il lato lungo al massimo [side], in memoria normale (si disegna su Canvas). */
    internal fun decode(source: ImageDecoder.Source, side: Int): Bitmap = ImageDecoder.decodeBitmap(source) { d, info, _ ->
        val long = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
        if (long > side) {
            val k = side.toFloat() / long
            d.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
        }
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

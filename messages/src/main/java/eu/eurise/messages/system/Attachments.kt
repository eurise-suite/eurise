package eu.eurise.messages.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Allegati degli MMS: le foto si riducono sotto il limite degli operatori (circa 300 KB), il resto passa com'è. */
object Attachments {
    private const val MAX_BYTES = 290 * 1024
    private const val MAX_SIDE = 1600

    /** I byte da mandare e il loro tipo; null se il file non si legge o è troppo grande. */
    fun prepare(context: Context, uri: Uri, mime: String?): Pair<ByteArray, String>? {
        val type = mime ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
        if (type.startsWith("image/") && type != "image/gif") return image(context, uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return if (bytes.size <= MAX_BYTES * 2) bytes to type else null
    }

    private fun image(context: Context, uri: Uri): Pair<ByteArray, String>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        var bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        var quality = 85
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= MAX_BYTES) return out.toByteArray() to "image/jpeg"
            if (quality > 45) quality -= 10
            else {
                val smaller = Bitmap.createScaledBitmap(bitmap, (bitmap.width * 0.75f).toInt().coerceAtLeast(1), (bitmap.height * 0.75f).toInt().coerceAtLeast(1), true)
                if (smaller !== bitmap) bitmap.recycle()
                bitmap = smaller
                quality = 75
            }
        }
    }
}

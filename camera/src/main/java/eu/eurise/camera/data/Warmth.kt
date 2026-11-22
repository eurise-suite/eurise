package eu.eurise.camera.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import eu.eurise.camera.TAG
import java.io.File
import kotlin.math.sqrt

/**
 * Luce calda meno arancione (22 novembre 2026). Sotto lampade a incandescenza e lucine il bilanciamento del bianco del
 * telefono lascia la foto molto gialla. Se la dominante è forte (rosso medio oltre 1,3 volte il blu sui toni medi), si
 * corregge a metà strada verso il neutro: resta un'atmosfera calda, non un'arancia. Le altre foto non si toccano
 * (nessuna ricodifica). I dati dello scatto (EXIF, anche la posizione se accesa) si ricopiano.
 */
object Warmth {
    const val THRESHOLD = 1.3
    private const val MAX_BLUE = 1.35f
    private const val MIN_RED = 0.85f

    /** Guadagni rosso e blu per i valori medi [r], [g], [b], o null se la foto va bene così. */
    fun gains(r: Double, g: Double, b: Double): Pair<Float, Float>? {
        if (r <= 0 || g <= 0 || b <= 0 || r / b < THRESHOLD) return null
        val gr = sqrt(g / r).toFloat().coerceIn(MIN_RED, 1f)
        val gb = sqrt(g / b).toFloat().coerceIn(1f, MAX_BLUE)
        return gr to gb
    }

    /** Corregge la foto a [uri] (JPEG) se serve. Restituisce true se l'ha cambiata. Mai un errore verso chi chiama. */
    fun correct(resolver: ContentResolver, uri: Uri, cache: File): Boolean = runCatching {
        val src = File(cache, "calda-in.jpg"); val out = File(cache, "calda-out.jpg")
        resolver.openInputStream(uri)?.use { input -> src.outputStream().use { input.copyTo(it) } } ?: return false
        val done = correctFile(src, out)
        if (done) resolver.openOutputStream(uri, "wt")?.use { sink -> out.inputStream().use { it.copyTo(sink) } }
        src.delete(); out.delete()
        done
    }.onFailure { Log.w(TAG, "Correzione della luce calda non riuscita", it) }.getOrDefault(false)

    /** Come [correct], da file a file: [out] si scrive solo se serve una correzione. */
    fun correctFile(src: File, out: File): Boolean {
        val bitmap = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inMutable = true }) ?: return false
        try {
            val w = bitmap.width; val h = bitmap.height
            val row = IntArray(w)
            var sr = 0.0; var sg = 0.0; var sb = 0.0; var n = 0
            var y = 0
            while (y < h) {
                bitmap.getPixels(row, 0, w, 0, y, w, 1)
                var x = 0
                while (x < w) {
                    val c = row[x]
                    val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
                    val max = maxOf(r, g, b)
                    // Toni medi: fuori le ombre profonde e le luci bruciate (lampadine, finestre).
                    if (max in 30..235) { sr += r; sg += g; sb += b; n++ }
                    x += 4
                }
                y += 4
            }
            if (n < 1000) return false
            val (gr, gb) = gains(sr / n, sg / n, sb / n) ?: return false
            val lutR = IntArray(256) { (it * gr).toInt().coerceIn(0, 255) }
            val lutB = IntArray(256) { (it * gb).toInt().coerceIn(0, 255) }
            for (yy in 0 until h) {
                bitmap.getPixels(row, 0, w, 0, yy, w, 1)
                for (x in 0 until w) {
                    val c = row[x]
                    row[x] = (c and 0xFF00FF00.toInt()) or (lutR[c shr 16 and 0xFF] shl 16) or lutB[c and 0xFF]
                }
                bitmap.setPixels(row, 0, w, 0, yy, w, 1)
            }
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally {
            bitmap.recycle()
        }
        copyExif(src, out)
        Log.i(TAG, "Luce calda corretta")
        return true
    }

    private val EXIF_TAGS = listOf(
        "Make", "Model", "DateTime", "DateTimeOriginal", "DateTimeDigitized", "SubSecTime", "SubSecTimeOriginal",
        "SubSecTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized", "Orientation", "ExposureTime",
        "FNumber", "ISOSpeedRatings", "PhotographicSensitivity", "FocalLength", "FocalLengthIn35mmFilm", "Flash",
        "WhiteBalance", "LightSource", "ExposureProgram", "ExposureMode", "MeteringMode", "ApertureValue",
        "ShutterSpeedValue", "BrightnessValue", "ExposureBiasValue", "DigitalZoomRatio", "SceneCaptureType",
        "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef", "GPSAltitude", "GPSAltitudeRef",
        "GPSTimeStamp", "GPSDateStamp", "GPSProcessingMethod",
    )

    private fun copyExif(from: File, to: File) {
        runCatching {
            val a = ExifInterface(from.path); val b = ExifInterface(to.path)
            EXIF_TAGS.forEach { tag -> a.getAttribute(tag)?.let { b.setAttribute(tag, it) } }
            b.saveAttributes()
        }.onFailure { Log.w(TAG, "Dati dello scatto non ricopiati", it) }
    }

}

package eu.eurise.camera.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import eu.eurise.camera.TAG
import java.io.File
import kotlin.math.ln
import kotlin.math.pow

/**
 * Ritocco dopo lo scatto (22 novembre 2026), sulle foto con poca luce di casa:
 * - **Luce calda**: sotto lampade a incandescenza e lucine il bilanciamento del bianco del telefono lascia la foto molto
 *   gialla. Se la dominante è forte (rosso medio oltre [THRESHOLD] volte il blu sui toni medi), si corregge per il 70%
 *   verso il neutro: resta un filo di calore.
 * - **Toni medi**: quando la Fotocamera sceglie tempo e ISO da sé il telefono non schiarisce più le ombre, e le foto di
 *   sera vengono scure. Se la mediana della luminosità è sotto [DARK], una curva la porta verso [TARGET_MEDIAN] senza
 *   toccare il bianco e il nero.
 * Le foto che non ne hanno bisogno non si ricodificano. I dati dello scatto (EXIF, anche la posizione se accesa) si ricopiano.
 */
object Ritocco {
    const val THRESHOLD = 1.2
    private const val STRENGTH = 0.7
    private const val MAX_BLUE = 1.6f
    private const val MIN_RED = 0.8f
    const val DARK = 95
    const val TARGET_MEDIAN = 110
    private const val MIN_GAMMA = 0.7

    /** Guadagni rosso e blu per i valori medi [r], [g], [b], o null se i colori vanno bene così. */
    fun gains(r: Double, g: Double, b: Double): Pair<Float, Float>? {
        if (r <= 0 || g <= 0 || b <= 0 || r / b < THRESHOLD) return null
        val gr = (g / r).pow(STRENGTH).toFloat().coerceIn(MIN_RED, 1f)
        val gb = (g / b).pow(STRENGTH).toFloat().coerceIn(1f, MAX_BLUE)
        return gr to gb
    }

    /** Esponente della curva dei toni per la mediana [median] (0-255), o null se la foto non è scura. */
    fun gamma(median: Int): Double? {
        if (median <= 0 || median >= DARK) return null
        return (ln(TARGET_MEDIAN / 255.0) / ln(median / 255.0)).coerceIn(MIN_GAMMA, 1.0)
    }

    /** Corregge la foto a [uri] (JPEG) se serve. Restituisce true se l'ha cambiata. Mai un errore verso chi chiama. */
    fun correct(resolver: ContentResolver, uri: Uri, cache: File): Boolean = runCatching {
        val src = File(cache, "ritocco-in.jpg"); val out = File(cache, "ritocco-out.jpg")
        resolver.openInputStream(uri)?.use { input -> src.outputStream().use { input.copyTo(it) } } ?: return false
        val done = correctFile(src, out)
        if (done) resolver.openOutputStream(uri, "wt")?.use { sink -> out.inputStream().use { it.copyTo(sink) } }
        src.delete(); out.delete()
        done
    }.onFailure { Log.w(TAG, "Ritocco non riuscito", it) }.getOrDefault(false)

    /** Come [correct], da file a file: [out] si scrive solo se serve una correzione. */
    fun correctFile(src: File, out: File): Boolean {
        val bitmap = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inMutable = true }) ?: return false
        try {
            val w = bitmap.width; val h = bitmap.height
            val row = IntArray(w)
            var sr = 0.0; var sg = 0.0; var sb = 0.0; var n = 0
            val hist = IntArray(256); var all = 0
            var y = 0
            while (y < h) {
                bitmap.getPixels(row, 0, w, 0, y, w, 1)
                var x = 0
                while (x < w) {
                    val c = row[x]
                    val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
                    val max = maxOf(r, g, b)
                    // Colori sui toni medi: fuori le ombre profonde e le luci bruciate (lampadine, finestre).
                    if (max in 30..235) { sr += r; sg += g; sb += b; n++ }
                    hist[(r * 77 + g * 150 + b * 29) shr 8]++; all++
                    x += 4
                }
                y += 4
            }
            if (n < 1000) return false
            val wb = gains(sr / n, sg / n, sb / n)
            var acc = 0; var median = 0
            while (median < 255 && acc + hist[median] < all / 2) { acc += hist[median]; median++ }
            val curve = gamma(median)
            if (wb == null && curve == null) return false
            val (gr, gb) = wb ?: (1f to 1f)
            val tone = IntArray(256) { v -> if (curve == null) v else (255.0 * (v / 255.0).pow(curve)).toInt().coerceIn(0, 255) }
            val lutR = IntArray(256) { tone[(it * gr).toInt().coerceIn(0, 255)] }
            val lutG = IntArray(256) { tone[it] }
            val lutB = IntArray(256) { tone[(it * gb).toInt().coerceIn(0, 255)] }
            for (yy in 0 until h) {
                bitmap.getPixels(row, 0, w, 0, yy, w, 1)
                for (x in 0 until w) {
                    val c = row[x]
                    row[x] = (c and 0xFF000000.toInt()) or (lutR[c shr 16 and 0xFF] shl 16) or (lutG[c shr 8 and 0xFF] shl 8) or lutB[c and 0xFF]
                }
                bitmap.setPixels(row, 0, w, 0, yy, w, 1)
            }
            Log.i(TAG, "Ritocco: colori ${wb != null}, mediana $median, curva $curve")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally {
            bitmap.recycle()
        }
        copyExif(src, out)
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

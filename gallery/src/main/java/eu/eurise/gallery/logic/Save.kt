package eu.eurise.gallery.logic

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Le scelte del foglio Salva (tavola GalEsporta, approvata il 17 novembre). Logica pura: si prova in `SaveTest`.
 */
enum class SaveFormat(val mime: String, val ext: String) { Jpeg("image/jpeg", "jpg"), Png("image/png", "png"), Webp("image/webp", "webp") }

/**
 * [replace]: la foto modificata prende il posto dell'originale, che resta conservato per [KEEP_DAYS] giorni.
 * [maxSide] 0 = grandezza originale. [stripLocation] è acceso di base: la posizione non esce dal telefono senza volerlo.
 */
data class SaveOptions(
    val replace: Boolean = false,
    val format: SaveFormat = SaveFormat.Jpeg,
    val quality: Int = 95,
    val maxSide: Int = 0,
    val stripLocation: Boolean = true,
    val stripCamera: Boolean = false,
) {
    /** Il formato usato davvero: sostituendo si tiene quello dell'originale, perché il file resta lo stesso. */
    fun formatFor(originalMime: String?): SaveFormat =
        if (!replace) format else SaveFormat.entries.firstOrNull { it.mime == originalMime } ?: SaveFormat.Jpeg

    fun encode() = listOf(if (replace) 1 else 0, format.ordinal, quality, maxSide, if (stripLocation) 1 else 0, if (stripCamera) 1 else 0).joinToString(",")

    companion object {
        const val KEEP_DAYS = 30
        val SIDES = listOf(0, 2048, 1080)

        fun decode(text: String?): SaveOptions {
            val v = text?.split(',')?.map { it.toIntOrNull() } ?: return SaveOptions()
            if (v.size != 6 || v.any { it == null }) return SaveOptions()
            return SaveOptions(
                // «Sostituisci» non si ricorda: ogni volta si parte da una copia nuova, la scelta più sicura.
                replace = false,
                format = SaveFormat.entries.getOrElse(v[1]!!) { SaveFormat.Jpeg },
                quality = v[2]!!.coerceIn(50, 100),
                maxSide = v[3]!!.takeIf { it in SIDES } ?: 0,
                stripLocation = v[4] == 1,
                stripCamera = v[5] == 1,
            )
        }
    }
}

/** La grandezza finale: il lato più lungo non supera [maxSide] (0 = com'è). */
fun scaledSize(w: Int, h: Int, maxSide: Int): Pair<Int, Int> {
    if (maxSide <= 0 || max(w, h) <= maxSide) return w to h
    val k = maxSide / max(w, h).toFloat()
    return (w * k).roundToInt().coerceAtLeast(1) to (h * k).roundToInt().coerceAtLeast(1)
}

/** I dati della foto (EXIF) che passano dall'originale alla copia; l'orientamento no: la copia è già dritta. */
object ExifTags {
    val TIME = listOf("DateTime", "DateTimeOriginal", "DateTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized", "SubSecTimeOriginal")
    val CAMERA = listOf(
        "Make", "Model", "LensMake", "LensModel", "FNumber", "ExposureTime", "ISOSpeedRatings", "PhotographicSensitivity",
        "FocalLength", "FocalLengthIn35mmFilm", "Flash", "WhiteBalance", "ExposureBiasValue", "MeteringMode", "ExposureProgram", "Software",
    )
    val LOCATION = listOf(
        "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef", "GPSAltitude", "GPSAltitudeRef",
        "GPSTimeStamp", "GPSDateStamp", "GPSProcessingMethod", "GPSImgDirection", "GPSImgDirectionRef",
    )

    fun toCopy(o: SaveOptions): List<String> = TIME + (if (o.stripCamera) emptyList() else CAMERA) + (if (o.stripLocation) emptyList() else LOCATION)
}

/** Un originale messo da parte con «Sostituisci»: si può ripristinare fino a [expiresAt]. */
data class KeptOriginal(val uri: String, val file: String, val mime: String, val savedAt: Long) {
    val expiresAt: Long get() = savedAt + SaveOptions.KEEP_DAYS * 86_400_000L
}

object KeptCodec {
    fun encode(list: List<KeptOriginal>) = list.joinToString("\n") { "${it.uri}\t${it.file}\t${it.mime}\t${it.savedAt}" }
    fun decode(text: String?): List<KeptOriginal> = text.orEmpty().lines().mapNotNull { l ->
        val f = l.split('\t')
        if (f.size != 4) null else KeptOriginal(f[0], f[1], f[2], f[3].toLongOrNull() ?: return@mapNotNull null)
    }
}

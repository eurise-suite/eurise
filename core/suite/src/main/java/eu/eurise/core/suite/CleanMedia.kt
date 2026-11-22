package eu.eurise.core.suite

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Copie pulite per condividere (proposta della Fotocamera, 22 novembre 2026): via la posizione e i dati che riconoscono
 * il telefono o la persona (numeri di serie, nome del proprietario, identificativo unico, note del produttore, XMP).
 * Restano data, ora, orientamento e i dati di scatto (tempo, ISO), che servono a chi riceve e non dicono chi sei.
 * Lavora su un file già copiato in cache: l'originale non si tocca mai.
 */
object CleanMedia {
    /** Le chiavi tolte da ogni foto. */
    val REMOVED_TAGS: List<String> = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.TAG_GPS_DEST_BEARING, ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_TRACK_REF, ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_VERSION_ID, ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_BODY_SERIAL_NUMBER, ExifInterface.TAG_LENS_SERIAL_NUMBER, ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_IMAGE_UNIQUE_ID, ExifInterface.TAG_MAKER_NOTE, ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
        // TAG_XMP (Android 10+): scritto per nome, così vale anche dove la costante non c'è.
        "Xmp",
    )

    /**
     * Toglie i dati dalla foto [file] e restituisce il file pulito (lo stesso, o una copia ricodificata se i metadati non
     * si possono riscrivere: in quel caso il file di partenza si cancella). Si ferma con un errore se non riesce.
     */
    fun image(file: File): File {
        val stripped = runCatching {
            val exif = ExifInterface(file.path)
            REMOVED_TAGS.forEach { exif.setAttribute(it, null) }
            exif.saveAttributes()
            val check = ExifInterface(file.path)
            !check.getLatLong(FloatArray(2)) && check.getAttribute(ExifInterface.TAG_BODY_SERIAL_NUMBER) == null
        }.getOrDefault(false)
        if (stripped) return file
        if (Build.VERSION.SDK_INT < 28) throw java.io.IOException("Metadati non riscrivibili")
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file))
        val out = File(file.path.substringBeforeLast('.') + "-pulita.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        file.delete()
        return out
    }

    /** Riscrive audio e video in un MP4 nuovo: i dati restano identici, i metadati (posizione compresa) no. */
    fun video(file: File): File {
        val out = File(file.path.substringBeforeLast('.') + "-pulito.mp4")
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(file.path)
            val retriever = MediaMetadataRetriever()
            val rotation = try {
                retriever.setDataSource(file.path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            } finally {
                retriever.release()
            }
            muxer.setOrientationHint(rotation)
            val tracks = HashMap<Int, Int>()
            var bufferSize = 1024 * 1024
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                extractor.selectTrack(i)
                tracks[i] = muxer.addTrack(format)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) bufferSize = max(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            muxer.start()
            val buffer = ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val track = tracks[extractor.sampleTrackIndex]
                if (track != null) {
                    val key = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    info.set(0, size, extractor.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(track, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
        } finally {
            extractor.release()
            runCatching { muxer.release() }
        }
        file.delete()
        return out
    }
}

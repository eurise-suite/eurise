package eu.eurise.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer

/**
 * Strumenti del lettore video senza librerie: miniature per la barra, fotogramma come foto, taglio di un tratto.
 * Il taglio copia i dati così come sono (nessuna nuova codifica: veloce e senza perdita); comincia dal fotogramma
 * chiave più vicino prima del punto scelto, come fanno i telefoni.
 */
object VideoTools {

    /** [count] miniature alte [height] px, distribuite su tutto il video. */
    fun strip(context: Context, uri: Uri, durationMs: Long, count: Int, height: Int = 96): List<Bitmap> {
        if (durationMs <= 0) return emptyList()
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            (0 until count).mapNotNull { i ->
                val at = durationMs * (2 * i + 1) / (2 * count) * 1000
                r.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, height * 16 / 9, height)
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { r.release() }
        }
    }

    /** Il fotogramma a [positionMs] salvato come foto in Immagini › Eurise; null se non riesce. */
    fun saveFrame(context: Context, uri: Uri, positionMs: Long, name: String): Uri? {
        val r = MediaMetadataRetriever()
        val frame = try {
            r.setDataSource(context, uri)
            r.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        } ?: return null
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Eurise")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val out = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
        val ok = runCatching { resolver.openOutputStream(out)?.use { frame.compress(Bitmap.CompressFormat.JPEG, 94, it) } == true }.getOrDefault(false)
        frame.recycle()
        if (!ok) { resolver.delete(out, null, null); return null }
        resolver.update(out, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return out
    }

    /** Copia il tratto tra [startMs] e [endMs] in un video nuovo in Film › Eurise. L'originale non si tocca. */
    fun cut(context: Context, uri: Uri, startMs: Long, endMs: Long, name: String): Uri? {
        val temp = File(context.cacheDir, "cut-${System.currentTimeMillis()}.mp4")
        try {
            if (!cutTo(context, uri, startMs * 1000, endMs * 1000, temp)) return null
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Eurise")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val out = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
            val ok = runCatching { resolver.openOutputStream(out)?.use { o -> temp.inputStream().use { it.copyTo(o) } } != null }.getOrDefault(false)
            if (!ok) { resolver.delete(out, null, null); return null }
            resolver.update(out, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return out
        } finally {
            temp.delete()
        }
    }

    private fun cutTo(context: Context, uri: Uri, startUs: Long, endUs: Long, target: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, uri, null)
            muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val map = HashMap<Int, Int>()
            var bufferSize = 1 shl 20
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                extractor.selectTrack(i)
                map[i] = muxer.addTrack(format)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            if (map.isEmpty()) return false
            runCatching {
                MediaMetadataRetriever().apply { setDataSource(context, uri) }.let { r ->
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.let(muxer::setOrientationHint)
                    r.release()
                }
            }
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val begin = extractor.sampleTime.coerceAtLeast(0)
            val buffer = ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()
            muxer.start()
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val time = extractor.sampleTime
                if (time > endUs) break
                if (time < begin) { extractor.advance(); continue }
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                // Il fotogramma chiave da cui si parte può stare un po' prima dell'audio: i tempi si contano da lì.
                info.presentationTimeUs = (time - begin).coerceAtLeast(0)
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                map[track]?.let { muxer.writeSampleData(it, buffer, info) }
                extractor.advance()
            }
            muxer.stop()
            return target.length() > 0
        } catch (_: Exception) {
            return false
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }
}

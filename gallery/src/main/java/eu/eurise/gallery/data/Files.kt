package eu.eurise.gallery.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.gallery.logic.EditState
import eu.eurise.gallery.logic.InkStroke
import eu.eurise.gallery.logic.TextMark
import eu.eurise.gallery.logic.straightenScale
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.adjustMatrix
import eu.eurise.gallery.logic.cropPixels
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max

private fun Context.authority() = "$packageName.files"

fun Context.fileUri(file: File): Uri = FileProvider.getUriForFile(this, authority(), file)

/** Il file di una foto che sta solo sul cloud, scaricato una volta; null se il cloud non risponde. */
suspend fun cloudFile(context: Context, item: MediaItem): File? {
    val target = File(File(context.cacheDir, "cloud-view").apply { mkdirs() }, "${-item.id}-${item.name}")
    if (target.length() > 0) return target
    val store = GalleryStore.get(context)
    val dav = CloudAccounts.get(context).dav(store.settings.value.accountId) ?: return null
    val partial = File(target.path + ".part")
    return runCatching { if (dav.download(item.remote ?: return null, partial) && partial.renameTo(target)) target else null }.getOrNull()
}

/** Prepara e apre la condivisione. Con [clean] nessun file esce con la posizione: se non si può toglierla, si ferma. */
suspend fun shareItems(context: Context, items: List<MediaItem>, clean: Boolean): Intent {
    val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
    val uris = ArrayList<Uri>()
    for ((i, item) in items.withIndex()) {
        val needsCopy = clean || item.cloudOnly
        if (!needsCopy) { uris += Uri.parse(item.uri); continue }
        val name = item.name.ifEmpty { "$i" }
        val raw = File(File(dir, "$i").apply { mkdirs() }, name)
        if (item.cloudOnly) {
            (cloudFile(context, item) ?: throw IOException("cloud")).copyTo(raw, overwrite = true)
        } else {
            context.contentResolver.openInputStream(Uri.parse(item.uri))?.use { input -> raw.outputStream().use { input.copyTo(it) } } ?: throw IOException("foto")
        }
        uris += context.fileUri(if (!clean) raw else if (item.isVideo) stripVideo(raw) else stripImage(raw))
    }
    val type = if (items.all { it.isVideo }) "video/*" else if (items.none { it.isVideo }) "image/*" else "*/*"
    val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    send.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return Intent.createChooser(send, null)
}

private val GPS_TAGS = listOf(
    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF,
    ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.TAG_GPS_DEST_BEARING, ExifInterface.TAG_GPS_DEST_BEARING_REF,
    ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_TRACK_REF, ExifInterface.TAG_GPS_AREA_INFORMATION, ExifInterface.TAG_GPS_VERSION_ID,
    ExifInterface.TAG_XMP,
)

/** Toglie la posizione dal file; dove i metadati non si possono riscrivere, ricrea l'immagine senza metadati. */
private fun stripImage(file: File): File {
    val stripped = runCatching {
        val exif = ExifInterface(file.path)
        GPS_TAGS.forEach { exif.setAttribute(it, null) }
        exif.saveAttributes()
        !ExifInterface(file.path).getLatLong(FloatArray(2))
    }.getOrDefault(false)
    if (stripped) return file
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file))
    val out = File(file.path.substringBeforeLast('.') + "-copia.jpg")
    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    file.delete()
    return out
}

/** Riscrive audio e video in un MP4 nuovo: i dati restano identici, i metadati (posizione compresa) no. */
private fun stripVideo(file: File): File {
    val out = File(file.path.substringBeforeLast('.') + "-copia.mp4")
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
                info.set(0, size, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
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

/** Disegna scritte e tratti su una tela di [w] per [h]: la stessa funzione serve l'anteprima e il salvataggio. */
fun drawMarks(canvas: Canvas, w: Float, h: Float, texts: List<TextMark>, strokes: List<InkStroke>) {
    val side = minOf(w, h)
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    for (s in strokes) {
        ink.color = s.color
        ink.strokeWidth = s.width * side
        val path = Path()
        s.points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * w, y * h) else path.lineTo(x * w, y * h) }
        if (s.points.size == 1) path.lineTo(s.points[0].first * w + 0.1f, s.points[0].second * h)
        canvas.drawPath(path, ink)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    for (t in texts) {
        paint.color = t.color
        paint.textSize = t.size * side
        paint.setShadowLayer(paint.textSize * 0.08f, 0f, paint.textSize * 0.03f, 0x66000000)
        canvas.drawText(t.text, t.x * w, t.y * h - (paint.ascent() + paint.descent()) / 2, paint)
    }
}

/** Salva una copia modificata nel telefono, con la data dell'originale. La copia non porta con sé la posizione. */
fun saveEdited(context: Context, item: MediaItem, state: EditState): Boolean {
    val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(item.uri))
    val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.setTargetSampleSize(max(1, max(info.size.width, info.size.height) / 8192))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    var bitmap = geometry(decoded, state.flip, state.turns)
    val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { if (!state.adjust.isNeutral) colorFilter = ColorMatrixColorFilter(ColorMatrix(adjustMatrix(state.adjust))) }
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    if (state.straighten != 0f) {
        val scale = straightenScale(state.straighten, w, h)
        canvas.save()
        canvas.translate(w / 2, h / 2)
        canvas.rotate(state.straighten)
        canvas.scale(scale, scale)
        canvas.translate(-w / 2, -h / 2)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        canvas.restore()
    } else {
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }
    drawMarks(canvas, w, h, state.texts, state.strokes)
    bitmap = out
    if (!state.crop.isFull) {
        val (x, y, cw, ch) = cropPixels(state.crop, bitmap.width, bitmap.height)
        bitmap = Bitmap.createBitmap(bitmap, x, y, cw, ch)
    }
    val base = item.name.substringBeforeLast('.').ifEmpty { "foto" }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$base-modificata-${System.currentTimeMillis() / 1000}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt + 1)
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Eurise")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
    val ok = runCatching { resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } == true }.getOrDefault(false)
    if (!ok) { resolver.delete(uri, null, null); return false }
    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    return true
}

/** Specchio e quarti di giro, nell'ordine usato da [EditState]. */
fun geometry(source: Bitmap, flip: Boolean, turns: Int): Bitmap {
    if (!flip && turns % 4 == 0) return source
    val matrix = Matrix().apply {
        if (flip) postScale(-1f, 1f)
        postRotate(turns * 90f)
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

/** Decodifica ridotta per l'anteprima dell'editor. */
fun editorPreview(context: Context, item: MediaItem, maxSide: Int = 1600): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(item.uri))) { decoder, info, _ ->
        decoder.setTargetSampleSize(max(1, max(info.size.width, info.size.height) / maxSide))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

fun mimeOf(name: String, isVideo: Boolean): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: if (isVideo) "video/mp4" else "image/jpeg"

/** Per confrontare davvero i possibili doppioni. */
fun contentHash(context: Context, uri: String): String? = runCatching {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
    } ?: return null
    digest.digest().joinToString("") { "%02x".format(it) }
}.getOrNull()


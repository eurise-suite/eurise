package eu.eurise.gallery.data

import eu.eurise.gallery.R
import kotlin.math.roundToInt
import android.graphics.BitmapFactory
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
import eu.eurise.gallery.logic.MarkStyle
import eu.eurise.gallery.logic.Nib
import eu.eurise.gallery.logic.toneLut
import eu.eurise.gallery.logic.straightenScale
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.adjustMatrix
import eu.eurise.gallery.logic.Adjust
import eu.eurise.gallery.logic.develop
import eu.eurise.gallery.logic.Perspective
import eu.eurise.gallery.logic.Expand
import eu.eurise.gallery.logic.expandPixels
import eu.eurise.gallery.logic.ExifTags
import eu.eurise.gallery.logic.KeptOriginal
import eu.eurise.gallery.logic.SaveFormat
import eu.eurise.gallery.logic.SaveOptions
import eu.eurise.gallery.logic.scaledSize
import eu.eurise.gallery.logic.Retouch
import eu.eurise.gallery.logic.applyLocal
import eu.eurise.gallery.logic.brushMasks
import eu.eurise.gallery.logic.heal
import eu.eurise.gallery.logic.FaceSpot
import eu.eurise.gallery.logic.MaskGrid
import eu.eurise.gallery.logic.blendMasked
import eu.eurise.gallery.logic.maskGrid
import eu.eurise.gallery.logic.portraitFace
import eu.eurise.gallery.logic.spotlight
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
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    for (s in strokes) {
        ink.color = s.color
        when (s.nib) {
            Nib.Pen -> { ink.strokeWidth = s.width * side; ink.strokeCap = Paint.Cap.ROUND; ink.alpha = 255 }
            Nib.Marker -> { ink.strokeWidth = s.width * side * 1.8f; ink.strokeCap = Paint.Cap.ROUND; ink.alpha = 235 }
            // L'evidenziatore lascia vedere sotto e ha la punta piatta.
            Nib.Highlighter -> { ink.strokeWidth = s.width * side * 3f; ink.strokeCap = Paint.Cap.SQUARE; ink.alpha = 110 }
        }
        val path = Path()
        s.points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * w, y * h) else path.lineTo(x * w, y * h) }
        if (s.points.size == 1) path.lineTo(s.points[0].first * w + 0.1f, s.points[0].second * h)
        canvas.drawPath(path, ink)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val box = Paint(Paint.ANTI_ALIAS_FLAG)
    for (t in texts) {
        paint.textSize = t.size * side
        paint.typeface = if (t.style == MarkStyle.Plain) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
        val baseline = t.y * h - (paint.ascent() + paint.descent()) / 2
        when (t.style) {
            MarkStyle.Box -> {
                // Fondo arrotondato nel colore scelto, testo bianco o nero secondo quale si legge meglio.
                val width = paint.measureText(t.text)
                val pad = paint.textSize * 0.32f
                box.color = t.color
                canvas.drawRoundRect(t.x * w - width / 2 - pad, t.y * h - paint.textSize * 0.62f, t.x * w + width / 2 + pad, t.y * h + paint.textSize * 0.62f, pad, pad, box)
                paint.clearShadowLayer()
                paint.style = Paint.Style.FILL
                paint.color = if (luma(t.color) > 0.6f) 0xFF14171C.toInt() else 0xFFFFFFFF.toInt()
                canvas.drawText(t.text, t.x * w, baseline, paint)
            }
            MarkStyle.Outline -> {
                paint.clearShadowLayer()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = paint.textSize * 0.09f
                paint.strokeJoin = Paint.Join.ROUND
                paint.color = if (luma(t.color) > 0.6f) 0xFF14171C.toInt() else 0xFFFFFFFF.toInt()
                canvas.drawText(t.text, t.x * w, baseline, paint)
                paint.style = Paint.Style.FILL
                paint.color = t.color
                canvas.drawText(t.text, t.x * w, baseline, paint)
            }
            else -> {
                paint.style = Paint.Style.FILL
                paint.color = t.color
                paint.setShadowLayer(paint.textSize * 0.08f, 0f, paint.textSize * 0.03f, 0x66000000)
                canvas.drawText(t.text, t.x * w, baseline, paint)
            }
        }
    }
}

private fun luma(c: Int) = (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f

/** L'istogramma della luminosità (un pixel ogni quattro basta) per "Automatico". */
fun histogram(bitmap: Bitmap): IntArray {
    val out = IntArray(256)
    val w = bitmap.width
    val line = IntArray(w)
    for (y in 0 until bitmap.height step 2) {
        bitmap.getPixels(line, 0, w, 0, y, w, 1)
        for (x in 0 until w step 2) {
            val c = line[x]
            out[((299 * ((c shr 16) and 0xFF) + 587 * ((c shr 8) and 0xFF) + 114 * (c and 0xFF)) / 1000).coerceIn(0, 255)]++
        }
    }
    return out
}

/** Ombre e alte luci: la curva applicata a ogni pixel, a strisce per non chiedere troppa memoria. */
fun applyCurve(bitmap: Bitmap, lut: IntArray) {
    val w = bitmap.width
    val rows = maxOf(1, 262_144 / w)
    val line = IntArray(w * rows)
    var y = 0
    while (y < bitmap.height) {
        val n = minOf(rows, bitmap.height - y)
        bitmap.getPixels(line, 0, w, 0, y, w, n)
        for (i in 0 until w * n) {
            val c = line[i]
            line[i] = (c and -0x1000000) or (lut[(c shr 16) and 0xFF] shl 16) or (lut[(c shr 8) and 0xFF] shl 8) or lut[c and 0xFF]
        }
        bitmap.setPixels(line, 0, w, 0, y, w, n)
        y += n
    }
}

/**
 * Toni, curve, vividezza, struttura, nitidezza ([develop]) e la matrice su una bitmap modificabile, a fasce: così anche
 * una foto grande non chiede la memoria di tutta l'immagine più volte. Le fasce si sovrappongono quanto il raggio della
 * struttura, perché i dettagli ai bordi delle fasce vengano uguali al resto.
 */
fun developBitmap(bitmap: Bitmap, adjust: Adjust, matrix: FloatArray?, mask: MaskGrid? = null, angle: Float = 0f, scale: Float = 1f) {
    if (!adjust.needsPixels && matrix == null) return
    val w = bitmap.width
    val h = bitmap.height
    val detail = adjust.structure != 0f || adjust.sharpen != 0f
    val pad = if (detail) maxOf(2, minOf(w, h) / 100) * 2 + 2 else 0
    val band = maxOf(64, 1_048_576 / w)
    var y = 0
    while (y < h) {
        val n = minOf(band, h - y)
        val top = maxOf(0, y - pad)
        val bottom = minOf(h, y + n + pad)
        val rows = bottom - top
        val px = IntArray(w * rows)
        bitmap.getPixels(px, 0, w, 0, top, w, rows)
        val before = if (mask != null) px.copyOf() else null
        if (detail) {
            // Il raggio della struttura dipende dal lato corto della foto intera, non della fascia.
            develop(px, w, rows, adjust, matrix, radiusBase = minOf(w, h))
        } else develop(px, w, rows, adjust, matrix)
        if (mask != null && before != null) blendMasked(px, before, w, rows, top, w, h, mask, angle, scale)
        bitmap.setPixels(px, (y - top) * w, w, 0, y, w, n)
        y += n
    }
}

/** La vignetta: bordi scuriti (valori positivi) o schiariti (negativi) attorno al riquadro tenuto. */
fun drawVignette(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, amount: Float) {
    if (amount == 0f) return
    val cx = (left + right) / 2
    val cy = (top + bottom) / 2
    val radius = kotlin.math.hypot(right - left, bottom - top) / 2
    val edge = if (amount > 0) android.graphics.Color.argb((amount * 200).toInt().coerceIn(0, 255), 0, 0, 0)
    else android.graphics.Color.argb((-amount * 170).toInt().coerceIn(0, 255), 255, 255, 255)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.RadialGradient(cx, cy, radius, intArrayOf(0, 0, edge), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
    }
    canvas.drawRect(left, top, right, bottom, paint)
}

/** La foto modificata a piena grandezza (al più 8192 px sul lato lungo), con tutti i passi della modifica. */
fun renderEdited(context: Context, item: MediaItem, state: EditState): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(item.uri))
    val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.setTargetSampleSize(max(1, max(info.size.width, info.size.height) / 8192))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    var bitmap = perspective(geometry(decoded, state.flip, state.turns), state.perspective)
    if (!state.retouch.isEmpty) bitmap = retouchBitmap(bitmap, state.retouch)
    val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val adjust = state.effective
    // La matrice si applica dopo il lavoro sui pixel (developBitmap), nello stesso ordine dell'anteprima.
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
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
    val mask = if (state.toneMask.isEmpty) null else maskGrid(state.toneMask, w / h)
    developBitmap(out, adjust, if (adjust.matrixNeutral) null else adjustMatrix(adjust), mask, state.straighten, straightenScale(state.straighten, w, h))
    drawVignette(canvas, state.crop.left * w, state.crop.top * h, state.crop.right * w, state.crop.bottom * h, adjust.vignette)
    drawMarks(canvas, w, h, state.texts, state.strokes)
    bitmap = out
    if (!state.crop.isFull) {
        val (x, y, cw, ch) = cropPixels(state.crop, bitmap.width, bitmap.height)
        bitmap = Bitmap.createBitmap(bitmap, x, y, cw, ch)
    }
    bitmap = expanded(bitmap, state.expand)
    return bitmap
}

/**
 * Salva la foto modificata (foglio Salva, tavola GalEsporta). Copia nuova in Pictures/Eurise, oppure, con
 * [SaveOptions.replace], al posto dell'originale: prima l'originale si mette da parte per 30 giorni (Torna
 * all'originale). Per sostituire serve il permesso di scrittura chiesto dall'editor (MediaStore.createWriteRequest).
 * I dati della foto passano secondo le scelte: la posizione di base no. Restituisce la foto salvata, o null.
 */
fun saveEdited(context: Context, item: MediaItem, state: EditState, opts: SaveOptions = SaveOptions()): Uri? {
    var bitmap = renderEdited(context, item, state)
    val (tw, th) = scaledSize(bitmap.width, bitmap.height, opts.maxSide)
    if (tw != bitmap.width) bitmap = Bitmap.createScaledBitmap(bitmap, tw, th, true)
    val resolver = context.contentResolver
    val original = Uri.parse(item.uri)
    val originalMime = resolver.getType(original)
    val format = opts.formatFor(originalMime)
    // Il file nuovo si scrive prima in cache: così i dati della foto si aggiungono con ExifInterface e un errore non
    // tocca mai la galleria.
    val temp = File(File(context.cacheDir, "save").apply { mkdirs() }, "out.${format.ext}")
    temp.outputStream().use { out ->
        val cf = when (format) {
            SaveFormat.Jpeg -> Bitmap.CompressFormat.JPEG
            SaveFormat.Png -> Bitmap.CompressFormat.PNG
            SaveFormat.Webp -> Bitmap.CompressFormat.WEBP_LOSSY
        }
        if (!bitmap.compress(cf, opts.quality, out)) return null
    }
    if (format == SaveFormat.Jpeg || format == SaveFormat.Webp) copyExif(context, original, temp, opts)
    if (opts.replace) {
        val kept = keepOriginal(context, item, originalMime ?: "image/jpeg") ?: return null
        val ok = runCatching { resolver.openOutputStream(original, "wt")?.use { out -> temp.inputStream().use { it.copyTo(out) } } != null }.getOrDefault(false)
        temp.delete()
        if (!ok) { File(kept.file).delete(); return null }
        GalleryStore.get(context).addKept(kept)
        return original
    }
    val base = item.name.substringBeforeLast('.').ifEmpty { "foto" }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$base-modificata-${System.currentTimeMillis() / 1000}.${format.ext}")
        put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
        put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt + 1)
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Eurise")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
    val ok = runCatching { resolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } } != null }.getOrDefault(false)
    temp.delete()
    if (!ok) { resolver.delete(uri, null, null); return null }
    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    return uri
}

/** Condivide la foto modificata senza salvarla in galleria, con le stesse scelte (formato, grandezza, dati). */
fun shareEdited(context: Context, item: MediaItem, state: EditState, opts: SaveOptions): Intent {
    var bitmap = renderEdited(context, item, state)
    val (tw, th) = scaledSize(bitmap.width, bitmap.height, opts.maxSide)
    if (tw != bitmap.width) bitmap = Bitmap.createScaledBitmap(bitmap, tw, th, true)
    val format = opts.copy(replace = false).formatFor(null)
    val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
    val base = item.name.substringBeforeLast('.').ifEmpty { "foto" }
    val file = File(dir, "$base-modificata.${format.ext}")
    file.outputStream().use { out ->
        val cf = when (format) { SaveFormat.Jpeg -> Bitmap.CompressFormat.JPEG; SaveFormat.Png -> Bitmap.CompressFormat.PNG; SaveFormat.Webp -> Bitmap.CompressFormat.WEBP_LOSSY }
        check(bitmap.compress(cf, opts.quality, out))
    }
    if (format != SaveFormat.Png) copyExif(context, Uri.parse(item.uri), file, opts)
    val uri = context.fileUri(file)
    val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri).setType(format.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    return Intent.createChooser(send, null)
}

/** Copia data, fotocamera e (se scelto) posizione dall'originale al file nuovo; l'orientamento resta «dritto». */
private fun copyExif(context: Context, original: Uri, target: File, opts: SaveOptions) {
    runCatching {
        val from = context.contentResolver.openInputStream(original)?.use { ExifInterface(it) } ?: return
        val to = ExifInterface(target.path)
        for (tag in ExifTags.toCopy(opts)) from.getAttribute(tag)?.let { to.setAttribute(tag, it) }
        to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        to.saveAttributes()
    }
}

/** Mette da parte l'originale prima di sostituirlo. */
private fun keepOriginal(context: Context, item: MediaItem, mime: String): KeptOriginal? {
    val dir = File(context.filesDir, "originali").apply { mkdirs() }
    val file = File(dir, "${item.id}-${System.currentTimeMillis()}")
    val ok = runCatching { context.contentResolver.openInputStream(Uri.parse(item.uri))?.use { input -> file.outputStream().use { input.copyTo(it) } } != null }.getOrDefault(false)
    return if (ok) KeptOriginal(item.uri, file.path, mime, System.currentTimeMillis()) else null.also { file.delete() }
}

/** Torna all'originale messo da parte con «Sostituisci» (serve di nuovo il permesso di scrittura). */
fun restoreOriginal(context: Context, uri: String): Boolean {
    val store = GalleryStore.get(context)
    val kept = store.kept().lastOrNull { it.uri == uri } ?: return false
    val file = File(kept.file)
    if (!file.exists()) { store.removeKept(kept); return false }
    val ok = runCatching { context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } } != null }.getOrDefault(false)
    if (ok) { file.delete(); store.removeKept(kept) }
    return ok
}

/** Toglie gli originali conservati da più di 30 giorni (all'avvio, fuori dal thread principale). */
fun dropExpiredOriginals(context: Context) {
    val store = GalleryStore.get(context)
    val now = System.currentTimeMillis()
    store.kept().filter { it.expiresAt < now }.forEach { File(it.file).delete(); store.removeKept(it) }
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

/**
 * La prospettiva raddrizzata: i quattro punti di [p] diventano gli angoli della foto (stessa grandezza), con la
 * trasformazione prospettica di Android (setPolyToPoly), che disegna con il filtro bilineare.
 */
fun perspective(source: Bitmap, p: Perspective): Bitmap {
    if (p.isIdentity) return source
    val w = source.width.toFloat()
    val h = source.height.toFloat()
    val q = p.quad()
    val src = FloatArray(8) { if (it % 2 == 0) q[it] * w else q[it] * h }
    val dst = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
    val matrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
    val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
}

/**
 * Correttivo, Selettivo e Pennello su una foto (tavola GalRitocca). Le macchie si curano su una zona attorno a ognuna;
 * Selettivo e Pennello a fasce, per non chiedere la memoria di tutta la foto più volte.
 */
fun retouchBitmap(source: Bitmap, r: Retouch): Bitmap {
    val bitmap = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)
    val w = bitmap.width
    val h = bitmap.height
    val short = minOf(w, h)
    for (spot in r.spots) {
        val rad = (spot.r * short).roundToInt().coerceAtLeast(2)
        val reach = rad * 6
        val cx = (spot.x * w).roundToInt(); val cy = (spot.y * h).roundToInt()
        val x0 = (cx - reach).coerceAtLeast(0); val y0 = (cy - reach).coerceAtLeast(0)
        val x1 = (cx + reach).coerceAtMost(w); val y1 = (cy + reach).coerceAtMost(h)
        val rw = x1 - x0; val rh = y1 - y0
        if (rw <= 2 || rh <= 2) continue
        val px = IntArray(rw * rh).also { bitmap.getPixels(it, 0, rw, x0, y0, rw, rh) }
        heal(px, rw, rh, cx - x0, cy - y0, rad)
        bitmap.setPixels(px, 0, rw, x0, y0, rw, rh)
    }
    // Ritratto: pelle e occhi su un ritaglio attorno a ogni volto.
    val pt = r.portrait
    if (!pt.isEmpty && (pt.smoothing != 0f || pt.eyes != 0f)) for (face in pt.faces) {
        val e = face.eyes * short
        val cx = face.x * w; val cy = face.y * h
        val x0 = (cx - 2.2f * e).toInt().coerceAtLeast(0); val x1 = (cx + 2.2f * e).toInt().coerceAtMost(w)
        val y0 = (cy - 2.4f * e).toInt().coerceAtLeast(0); val y1 = (cy + 3f * e).toInt().coerceAtMost(h)
        val rw = x1 - x0; val rh = y1 - y0
        if (rw <= 4 || rh <= 4) continue
        val px = IntArray(rw * rh).also { bitmap.getPixels(it, 0, rw, x0, y0, rw, rh) }
        portraitFace(px, rw, rh, cx - x0, cy - y0, e, pt.smoothing, pt.eyes)
        bitmap.setPixels(px, 0, rw, x0, y0, rw, rh)
    }
    if (r.needsBands) {
        val masks = if (r.strokes.isNotEmpty()) brushMasks(r.strokes, w / h.toFloat()) else null
        val band = maxOf(64, 1_048_576 / w)
        var y = 0
        while (y < h) {
            val n = minOf(band, h - y)
            val px = IntArray(w * n).also { bitmap.getPixels(it, 0, w, 0, y, w, n) }
            applyLocal(px, w, n, y, w, h, r, masks)
            if (!pt.isEmpty) spotlight(px, w, n, y, w, h, pt)
            bitmap.setPixels(px, 0, w, 0, y, w, n)
            y += n
        }
    }
    return bitmap
}

/**
 * Volti nella foto con il riconoscimento di Android (`android.media.FaceDetector`, sul telefono, senza librerie):
 * posizione tra gli occhi e distanza tra gli occhi, in quote. Lavora su una copia piccola.
 */
fun detectFaces(source: Bitmap): List<FaceSpot> {
    val k = minOf(1f, 1024f / maxOf(source.width, source.height))
    val sw = ((source.width * k).roundToInt() / 2 * 2).coerceAtLeast(2)
    val sh = (source.height * k).roundToInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(source, sw, sh, true).copy(Bitmap.Config.RGB_565, false)
    val found = arrayOfNulls<android.media.FaceDetector.Face>(8)
    val n = runCatching { android.media.FaceDetector(sw, sh, found.size).findFaces(small, found) }.getOrDefault(0)
    val short = minOf(sw, sh).toFloat()
    val mid = android.graphics.PointF()
    return (0 until n).mapNotNull { found[it] }.filter { it.confidence() >= 0.3f }.map { f ->
        f.getMidPoint(mid)
        FaceSpot(mid.x / sw, mid.y / sh, f.eyesDistance() / short)
    }
}

/** I bordi di Espandi attorno alla foto ([expandPixels]). */
fun expanded(source: Bitmap, e: Expand): Bitmap {
    val m = e.margin(source.width, source.height)
    if (m <= 0) return source
    val w = source.width
    val h = source.height
    val px = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
    val out = expandPixels(px, w, h, m, e.fill)
    return Bitmap.createBitmap(out, w + 2 * m, h + 2 * m, Bitmap.Config.ARGB_8888)
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


/** Il codice QR di un testo (una modalità tua), bianco e nero, lato [size] px. */
fun qrBitmap(text: String, size: Int): Bitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1, com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M))
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) -0x1000000 else -0x1 }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

/** Il testo del codice QR in una foto (per esempio la schermata di un amico), o null. Tutto sul telefono. */
fun readQr(context: Context, uri: Uri): String? = runCatching {
    val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
    } ?: return null
    // Le foto grandi si riducono: il codice si legge lo stesso e il calcolo resta rapido.
    val k = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
    val b = if (k < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt(), (bitmap.height * k).toInt(), true) else bitmap
    val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    val source = com.google.zxing.RGBLuminanceSource(b.width, b.height, px)
    val hints = mapOf(com.google.zxing.DecodeHintType.TRY_HARDER to true)
    com.google.zxing.qrcode.QRCodeReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source)), hints).text
}.getOrNull()

/**
 * I dati di una foto (tavola GalLookQR): quando, grandezza, peso, fotocamera, esposizione e se c'è la posizione
 * (arrotondata, mai mandata fuori). Ogni riga: etichetta e valore; solo quelle che ci sono.
 */
fun photoInfo(context: Context, item: MediaItem): List<Pair<Int, String>> {
    val out = mutableListOf<Pair<Int, String>>()
    out += R.string.info_when to java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.LONG, java.text.DateFormat.SHORT).format(java.util.Date(item.takenAt))
    out += R.string.info_name to item.name
    runCatching {
        context.contentResolver.query(Uri.parse(item.uri), arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val w = c.getInt(1); val h = c.getInt(2)
                if (w > 0 && h > 0) out += R.string.info_size to "$w × $h · %.1f MP".format(w * h / 1_000_000f)
                val bytes = c.getLong(0)
                if (bytes > 0) out += R.string.info_weight to android.text.format.Formatter.formatShortFileSize(context, bytes)
            }
        }
    }
    runCatching {
        val exif = context.contentResolver.openInputStream(Uri.parse(item.uri))?.use { ExifInterface(it) } ?: return@runCatching
        listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL)).joinToString(" ").takeIf { it.isNotBlank() }
            ?.let { out += R.string.info_camera to it }
        val shot = listOfNotNull(
            exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { "f/%.1f".format(it) },
            exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let { if (it < 1) "1/${(1 / it).roundToInt()} s" else "%.1f s".format(it) },
            exif.getAttributeInt("ISOSpeedRatings", 0).takeIf { it > 0 }?.let { "ISO $it" },
            exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { "%.1f mm".format(it) },
        ).joinToString(" · ")
        if (shot.isNotEmpty()) out += R.string.info_exposure to shot
        val ll = FloatArray(2)
        @Suppress("DEPRECATION")
        out += R.string.info_location to if (exif.getLatLong(ll)) "%.2f, %.2f".format(ll[0], ll[1]) else context.getString(R.string.info_no_location)
    }
    return out
}

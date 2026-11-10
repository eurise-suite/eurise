package eu.eurise.feature.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.data.docs.DocReader
import eu.eurise.core.data.docs.DocReading
import eu.eurise.core.data.docs.ReadClient
import eu.eurise.core.data.docs.ReadPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors

/** Da cosa è nata una pagina: serve a rifare il ritaglio senza perdere qualità. */
private class Origin(val upright: File, val mode: ScanMode, val quad: Quad?)

// Abbastanza per i bordi del foglio (rette di Hough), pochi millisecondi per fotogramma.
private const val ANALYSIS_WIDTH = 200
private const val STILL_NEEDED = 12
private const val STILL_LIMIT = 0.025f

/** Scanner: inquadra, riconosce i bordi e scatta da solo quando il foglio è fermo, poi salva un PDF. */
@Composable
fun ScannerRoute(onClose: () -> Unit, startQr: Boolean = false) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var explain by remember { mutableStateOf(!granted) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) onClose()
    }
    val pages = remember { mutableStateListOf<File>() }
    val origins = remember { HashMap<File, Origin>() }
    var reviewing by remember { mutableStateOf(false) }
    val dir = remember { File(context.cacheDir, "scan").apply { deleteRecursively(); mkdirs() } }
    DisposableEffect(Unit) { onDispose { dir.deleteRecursively() } }

    BackHandler(enabled = reviewing) { reviewing = false }
    Box(Modifier.fillMaxSize().background(if (reviewing) MaterialTheme.colorScheme.background else Color(0xFF14171C))) {
        when {
            reviewing -> Review(pages, origins, dir, onAdd = { reviewing = false }, onCancel = onClose, onSaved = onClose)
            granted -> Camera(pages, origins, dir, onClose, startQr) { if (pages.isNotEmpty()) reviewing = true }
        }
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.scan_permission_title)) },
            text = { Text(stringResource(R.string.scan_permission_body)) },
            confirmButton = { TextButton(onClick = { explain = false; request.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.scan_permission_grant)) } },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.scan_cancel)) } },
        )
    }
}

/** Luminosità ridotta a [ANALYSIS_WIDTH] colonne, già ruotata come la vede la persona. */
private fun lumaOf(image: ImageProxy): Triple<IntArray, Int, Int> {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val step = maxOf(1, image.width / ANALYSIS_WIDTH)
    val w = image.width / step
    val h = image.height / step
    val raw = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val at = y * step * plane.rowStride + x * step * plane.pixelStride
        raw[y * w + x] = if (at < buffer.limit()) buffer.get(at).toInt() and 0xFF else 0
    }
    return when (image.imageInfo.rotationDegrees) {
        90 -> Triple(IntArray(w * h) { i -> val nx = i % h; val ny = i / h; raw[(h - 1 - nx) * w + ny] }, h, w)
        180 -> Triple(IntArray(w * h) { raw[w * h - 1 - it] }, w, h)
        270 -> Triple(IntArray(w * h) { i -> val nx = i % h; val ny = i / h; raw[nx * w + (w - 1 - ny)] }, h, w)
        else -> Triple(raw, w, h)
    }
}

@Composable
private fun Camera(pages: MutableList<File>, origins: MutableMap<File, Origin>, dir: File, onClose: () -> Unit, startQr: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(if (startQr) ScanMode.Qr else ScanMode.Document) }
    var quad by remember { mutableStateOf<Quad?>(null) }
    var still by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val modeNow = remember { java.util.concurrent.atomic.AtomicReference(if (startQr) ScanMode.Qr else ScanMode.Document) }
    var qr by remember { mutableStateOf<String?>(null) }
    val qrShown = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var qrHistory by remember { mutableStateOf(false) }
    var qrCreate by remember { mutableStateOf(false) }
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.Default) { decodeFromImage(context, uri) }
            if (text != null) { qrShown.set(true); qr = text } else Toast.makeText(context, R.string.qr_none_in_photo, Toast.LENGTH_SHORT).show()
        }
    }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
            .build()
    }
    val analyzer = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analyzer.shutdown() } }
    // Per i codici un po' di zoom (si può stare più lontani, dove la messa a fuoco funziona) e fuoco al centro, ogni
    // pochi secondi: molti telefoni non mettono a fuoco da vicino e i codici piccoli restano sfocati.
    LaunchedEffect(mode, camera) {
        val cam = camera ?: return@LaunchedEffect
        val max = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
        cam.cameraControl.setZoomRatio(if (mode == ScanMode.Qr) minOf(1.8f, max) else 1f)
        while (mode == ScanMode.Qr) {
            val point = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
            runCatching { cam.cameraControl.startFocusAndMetering(androidx.camera.core.FocusMeteringAction.Builder(point).setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS).build()) }
            kotlinx.coroutines.delay(3000)
        }
    }

    fun shoot() {
        if (busy) return
        busy = true
        val corners = quad
        val chosen = mode
        capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                scope.launch {
                    val done = withContext(Dispatchers.Default) { process(image, corners, chosen, dir) }
                    if (done != null) {
                        pages += done.first
                        origins[done.first] = done.second
                    }
                    still = 0
                    // Dopo uno scatto si aspetta che il foglio cambi: niente doppioni dello stesso.
                    busy = false
                }
            }
            override fun onError(exception: ImageCaptureException) { busy = false }
        })
    }

    LaunchedEffect(mode) {
        modeNow.set(mode)
        if (mode == ScanMode.Qr) { quad = null; still = 0 }
    }
    val owed = still >= STILL_NEEDED
    LaunchedEffect(owed) { if (owed && !busy) shoot() }

    qr?.let { text -> QrSheet(text) { qr = null; qrShown.set(false) } }
    if (qrHistory) QrHistorySheet(onOpen = { qrHistory = false; qrShown.set(true); qr = it }, onClose = { qrHistory = false })
    if (qrCreate) QrCreateSheet(onClose = { qrCreate = false })
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
                        ProcessCameraProvider.getInstance(ctx).let { future ->
                            future.addListener({
                                val provider = future.get()
                                val selector = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
                                val preview = Preview.Builder().setResolutionSelector(selector).build().also { it.surfaceProvider = view.surfaceProvider }
                                // Analisi a 1280×960 circa: i codici piccoli o lontani hanno bisogno di pixel (prima erano
                                // fotogrammi da anteprima ridotta). I bordi del foglio si calcolano comunque su 200 colonne.
                                val analysisSelector = ResolutionSelector.Builder()
                                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                    .setResolutionStrategy(
                                        androidx.camera.core.resolutionselector.ResolutionStrategy(
                                            android.util.Size(1280, 960),
                                            androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                                        ),
                                    ).build()
                                val analysis = ImageAnalysis.Builder().setResolutionSelector(analysisSelector)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                                analysis.setAnalyzer(analyzer) { image ->
                                    if (modeNow.get() == ScanMode.Qr) {
                                        val text = if (qrShown.get()) null else decodeQr(image)
                                        image.close()
                                        if (text != null && qrShown.compareAndSet(false, true)) {
                                            ContextCompat.getMainExecutor(ctx).execute { qr = text }
                                        }
                                        return@setAnalyzer
                                    }
                                    val (luma, w, h) = lumaOf(image)
                                    image.close()
                                    val found = EdgeDetector.find(luma, w, h)
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        val previous = quad
                                        still = if (found != null && previous != null && found.distance(previous) < STILL_LIMIT) still + 1 else 0
                                        quad = found
                                    }
                                }
                                provider.unbindAll()
                                camera = runCatching { provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis) }.getOrNull()
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                        view
                    },
                )
                quad?.let { q ->
                    Canvas(Modifier.fillMaxSize()) {
                        val path = Path().apply {
                            q.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * size.width, p.y * size.height) else lineTo(p.x * size.width, p.y * size.height) }
                            close()
                        }
                        drawPath(path, Color(0x2EA9C1F2))
                        drawPath(path, Color(0xFFA9C1F2), style = Stroke(width = 3.dp.toPx()))
                        q.points.forEach { p ->
                            val c = Offset(p.x * size.width, p.y * size.height)
                            drawCircle(Color.White, 9.dp.toPx(), c)
                            drawCircle(Color(0xFFA9C1F2), 9.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                DarkRound(EuriseIcons.close(), stringResource(R.string.scan_close), onClose)
                val status = when {
                    mode == ScanMode.Qr -> R.string.scan_status_qr
                    busy -> R.string.scan_status_saving
                    still > 3 -> R.string.scan_status_hold
                    quad != null -> R.string.scan_status_found
                    else -> R.string.scan_status_search
                }
                Row(Modifier.height(36.dp).clip(CircleShape).background(Color(0x9E14171C)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (quad != null) Color(0xFF93BBA0) else Color(0xFFECE9E3)))
                    Text(stringResource(status), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                }
                DarkRound(EuriseIcons.flash(), stringResource(R.string.scan_flash)) {
                    torch = !torch
                    camera?.cameraControl?.enableTorch(torch)
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Color(0xC714171C)).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                listOf(ScanMode.Document to R.string.scan_mode_doc, ScanMode.BlackWhite to R.string.scan_mode_bw, ScanMode.Photo to R.string.scan_mode_photo, ScanMode.Qr to R.string.scan_mode_qr).forEach { (m, label) ->
                    val active = m == mode
                    Box(Modifier.height(34.dp).clip(CircleShape).background(if (active) Color.White else Color.Transparent).clickable { mode = m }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(label), fontSize = 13.5.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, color = if (active) Color(0xFF1E1D1B) else Color(0xFFECE9E3))
                    }
                }
            }
            if (mode == ScanMode.Qr) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(R.string.qr_from_photo, EuriseIcons.image()) { pickPhoto.launch("image/*") },
                    Triple(R.string.qr_create, EuriseIcons.plus()) { qrCreate = true },
                    Triple(R.string.qr_history, EuriseIcons.clock()) { qrHistory = true },
                ).forEach { (label, icon, action) ->
                    Column(
                        Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x33FFFFFF)).clickable(onClick = action),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(stringResource(label), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                val desc = stringResource(R.string.scan_pages, pages.size)
                Box(Modifier.size(56.dp).clickable(onClick = onDone).semantics { contentDescription = desc }) {
                    pages.lastOrNull()?.let { Thumb(it, Modifier.fillMaxSize().padding(6.dp)) }
                    if (pages.isNotEmpty()) {
                        Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Color(0xFFA9C1F2)), contentAlignment = Alignment.Center) {
                            Text("${pages.size}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF14203A))
                        }
                    }
                }
                val shutter = stringResource(R.string.scan_shutter)
                Box(Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).clickable(enabled = !busy && mode != ScanMode.Qr) { shoot() }.padding(8.dp).semantics { contentDescription = shutter }) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White))
                }
                Box(Modifier.height(44.dp).clip(CircleShape).background(if (pages.isEmpty()) Color(0x55A9C1F2) else Color(0xFFA9C1F2)).clickable(enabled = pages.isNotEmpty(), onClick = onDone).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.scan_done), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14203A))
                }
            }
        }
    }
}

/** QR, Data Matrix, i codici delle carte d'imbarco (PDF417, Aztec) e i codici a barre più comuni (EAN, Code 128). */
private val LiveFormats = listOf(
    com.google.zxing.BarcodeFormat.QR_CODE, com.google.zxing.BarcodeFormat.DATA_MATRIX,
    com.google.zxing.BarcodeFormat.PDF_417, com.google.zxing.BarcodeFormat.AZTEC,
    com.google.zxing.BarcodeFormat.EAN_13, com.google.zxing.BarcodeFormat.EAN_8, com.google.zxing.BarcodeFormat.CODE_128,
    com.google.zxing.BarcodeFormat.UPC_A, com.google.zxing.BarcodeFormat.CODE_39, com.google.zxing.BarcodeFormat.ITF,
)
private val LiveReader = com.google.zxing.MultiFormatReader().apply {
    setHints(mapOf(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to LiveFormats, com.google.zxing.DecodeHintType.TRY_HARDER to true))
}
private var liveFrame = 0

/**
 * Un fotogramma per volta: il centro (dove si punta, più veloce), poi l'immagine intera; ogni tanto anche i colori
 * invertiti (codici chiari su fondo scuro). Il lettore si azzera dopo ogni tentativo, come chiede ZXing.
 */
private fun decodeQr(image: ImageProxy): String? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val w = image.width
    val h = image.height
    val data = ByteArray(w * h)
    for (y in 0 until h) {
        buffer.position(y * plane.rowStride)
        buffer.get(data, y * w, minOf(w, buffer.remaining()))
    }
    val full = com.google.zxing.PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
    val cw = (w * 0.7).toInt()
    val ch = (h * 0.7).toInt()
    val center = com.google.zxing.PlanarYUVLuminanceSource(data, w, h, (w - cw) / 2, (h - ch) / 2, cw, ch, false)
    val turn = liveFrame++ % 3
    val attempts = buildList<com.google.zxing.LuminanceSource> {
        add(center)
        add(full)
        // Il sensore è in orizzontale: un codice a barre tenuto dritto sullo schermo arriva girato, e ZXing non gira da
        // solo queste immagini. Una volta su tre si prova il centro girato; un'altra i colori invertiti.
        if (turn == 1) add(rotated(data, w, h, cw, ch))
        if (turn == 2) add(full.invert())
    }
    for (source in attempts) {
        try {
            return LiveReader.decodeWithState(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))).text
        } catch (e: com.google.zxing.ReaderException) {
            // si prova la variante successiva
        } finally {
            LiveReader.reset()
        }
    }
    return null
}

/** Il centro del fotogramma girato di 90°, per i codici a barre a una dimensione. */
private fun rotated(data: ByteArray, w: Int, h: Int, cw: Int, ch: Int): com.google.zxing.LuminanceSource {
    val left = (w - cw) / 2
    val top = (h - ch) / 2
    val out = ByteArray(cw * ch)
    for (y in 0 until ch) for (x in 0 until cw) out[x * ch + (ch - 1 - y)] = data[(top + y) * w + left + x]
    return com.google.zxing.PlanarYUVLuminanceSource(out, ch, cw, 0, 0, ch, cw, false)
}

@Composable
private fun DarkRound(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x8C14171C)).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/**
 * I bordi si ricercano sulla foto appena scattata: quelli dell'anteprima arrivano da un fotogramma di poco prima, e
 * basta che la mano si muova per tagliare male. Si tiene quella della foto se è credibile (non molto più piccola).
 */
private fun refine(upright: Bitmap, live: Quad?): Quad? {
    val w = ANALYSIS_WIDTH
    val h = (upright.height.toLong() * w / upright.width).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(upright, w, h, true)
    val pixels = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
    val luma = IntArray(w * h) { i -> val c = pixels[i]; (299 * ((c shr 16) and 0xFF) + 587 * ((c shr 8) and 0xFF) + 114 * (c and 0xFF)) / 1000 }
    val fresh = EdgeDetector.find(luma, w, h) ?: return live
    if (live == null) return fresh
    return if (EdgeDetector.area(fresh) >= EdgeDetector.area(live) * 0.6f) fresh else live
}

private fun process(image: ImageProxy, quad: Quad?, mode: ScanMode, dir: File): Pair<File, Origin>? = try {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val rotation = image.imageInfo.rotationDegrees
    image.close()
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val upright = if (rotation != 0) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true) else decoded
    val original = File(dir, UUID.randomUUID().toString() + ".orig").also { f -> f.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 92, it) } }
    val origin = Origin(original, mode, refine(upright, quad))
    render(upright, origin, dir) to origin
} catch (e: RuntimeException) {
    null
} catch (e: OutOfMemoryError) {
    null
}

private fun render(upright: Bitmap, origin: Origin, dir: File): File {
    val page = PageProcessor.applyMode(PageProcessor.straighten(upright, origin.quad), origin.mode)
    return File(dir, UUID.randomUUID().toString() + ".jpg").also { f -> f.outputStream().use { page.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
}

/** Trascinando si sposta l'angolo più vicino al dito. */
@Composable
private fun CropEditor(origin: Origin, onCancel: () -> Unit, onDone: (Quad) -> Unit) {
    val bitmap by produceState<Bitmap?>(null, origin) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(origin.upright.path, BitmapFactory.Options().apply { inSampleSize = 2 }) }
    }
    val start = origin.quad ?: Quad(Pt(0.05f, 0.05f), Pt(0.95f, 0.05f), Pt(0.95f, 0.95f), Pt(0.05f, 0.95f))
    var corners by remember { mutableStateOf(start.points) }
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().background(Color(0xFF14171C)).statusBarsPadding().navigationBarsPadding()) {
        Text(stringResource(R.string.scan_crop_hint), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            bitmap?.let { b ->
                Box(Modifier.aspectRatio(b.width.toFloat() / b.height)) {
                    Image(b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            var active = 0
                            detectDragGestures(
                                onDragStart = { at ->
                                    active = corners.indices.minBy { i -> kotlin.math.hypot(corners[i].x * size.width - at.x, corners[i].y * size.height - at.y) }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val p = Pt((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                                    corners = corners.toMutableList().also { it[active] = p }
                                },
                            )
                        },
                    ) {
                        val path = Path().apply {
                            corners.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * size.width, p.y * size.height) else lineTo(p.x * size.width, p.y * size.height) }
                            close()
                        }
                        drawPath(path, Color(0x2EA9C1F2))
                        drawPath(path, Color(0xFFA9C1F2), style = Stroke(width = 3.dp.toPx()))
                        corners.forEach { p -> drawCircle(Color.White, 11.dp.toPx(), Offset(p.x * size.width, p.y * size.height)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.scan_cancel), color = Color.White) }
            Button(onClick = { onDone(Quad(corners[0], corners[1], corners[2], corners[3])) }) { Text(stringResource(R.string.scan_crop_done)) }
        }
    }
}

@Composable
private fun Thumb(file: File, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 8 }) }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = modifier.clip(RoundedCornerShape(4.dp))) }
}

@Composable
private fun Review(pages: MutableList<File>, origins: MutableMap<File, Origin>, dir: File, onAdd: () -> Unit, onCancel: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val accounts = remember { CloudAccounts.get(context).accounts.value }
    val fallback = stringResource(R.string.scan_default_name)
    var name by remember { mutableStateOf("${LocalDate.now()}_$fallback") }
    var nameTouched by remember { mutableStateOf(false) }
    var where by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var docRead by remember { mutableStateOf<DocRead>(if (DocReading.enabled(context)) DocRead.Idle else DocRead.Off) }
    var askReading by remember { mutableStateOf(false) }
    val pageKey = pages.toList()

    fun readText() {
        docRead = DocRead.Reading
        val snapshot = pages.toList()
        scope.launch {
            docRead = when (val r = ReadClient.read(context, snapshot)) {
                is ReadClient.Result.Pages -> DocRead.Done(r.pages, DocReader.analyze(r.pages.joinToString("\n\n") { it.text }))
                else -> DocRead.Problem(r)
            }
            (docRead as? DocRead.Done)?.facts?.fileName?.let { if (!nameTouched) name = it }
        }
    }
    // Con «Leggi ogni scansione da sola» la lettura riparte quando cambiano le pagine.
    LaunchedEffect(pageKey) {
        if (pageKey.isNotEmpty() && DocReading.enabled(context) && DocReading.auto(context)) readText()
        else if (docRead is DocRead.Done) docRead = DocRead.Idle
    }
    var menuFor by remember { mutableStateOf<File?>(null) }
    var cropping by remember { mutableStateOf<File?>(null) }

    cropping?.let { file ->
        val origin = origins[file]
        if (origin == null) { cropping = null; return@let }
        CropEditor(origin, onCancel = { cropping = null }) { quad ->
            cropping = null
            scope.launch {
                val updated = Origin(origin.upright, origin.mode, quad)
                val fresh = withContext(Dispatchers.Default) {
                    runCatching { BitmapFactory.decodeFile(origin.upright.path)?.let { render(it, updated, dir) } }.getOrNull()
                }
                val index = pages.indexOf(file)
                if (fresh != null && index >= 0) {
                    origins.remove(file)
                    origins[fresh] = updated
                    pages[index] = fresh
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.scan_cancel)) }
            Text(stringResource(R.string.scan_review_title, pages.size), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Button(enabled = !saving && pages.isNotEmpty() && name.isNotBlank(), onClick = {
                saving = true
                scope.launch {
                    val done = (docRead as? DocRead.Done)?.takeIf { it.pages.size == pages.size }
                    val message = save(context, pages, done?.pages, done?.facts?.folder, dir, name.trim(), where, accounts.firstOrNull { it.id == where }?.name)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    saving = false
                    if (message != context.getString(R.string.scan_failed)) onSaved()
                }
            }) { Text(stringResource(R.string.scan_save)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            pages.toList().forEachIndexed { i, file ->
                val label = stringResource(R.string.scan_remove, i + 1)
                Box(Modifier.size(92.dp, 124.dp).clickable { menuFor = file }.semantics { contentDescription = label }) {
                    Thumb(file, Modifier.fillMaxSize())
                }
            }
            val add = stringResource(R.string.scan_add_page)
            Box(Modifier.size(92.dp, 124.dp).clip(RoundedCornerShape(8.dp)).border(2.dp, scheme.outlineVariant, RoundedCornerShape(8.dp)).clickable(onClick = onAdd).semantics { contentDescription = add }, contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.plus(), contentDescription = null, tint = scheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.scan_review_hint), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it; nameTouched = true }, singleLine = true, label = { Text(stringResource(R.string.scan_name)) }, suffix = { Text(".pdf") }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.scan_where), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()).heightIn(min = 36.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(stringResource(R.string.scan_phone), dot = null, selected = where == null) { where = null }
                accounts.forEach { a -> FilterPill(a.name, dot = null, selected = where == a.id) { where = a.id } }
            }
        }
        DocTextPart(docRead, onRead = ::readText, onTurnOn = { askReading = true })
        Text(stringResource(R.string.scan_local_note), fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(16.dp))
    }
    if (askReading) DocReadingSheet(onClose = { askReading = false }) {
        askReading = false
        if (DocReading.auto(context)) readText() else docRead = DocRead.Idle
    }
    menuFor?.let { file ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(stringResource(R.string.scan_page_title, pages.indexOf(file) + 1)) },
            confirmButton = { TextButton(onClick = { menuFor = null; cropping = file }) { Text(stringResource(R.string.scan_crop)) } },
            dismissButton = {
                TextButton(onClick = {
                    menuFor = null
                    pages.remove(file)
                    origins.remove(file)
                    if (pages.isEmpty()) onAdd()
                }) { Text(stringResource(R.string.scan_remove_page)) }
            },
        )
    }
}

/**
 * Salva il PDF: con il testo letto è cercabile (immagini e testo invisibile, [SearchablePdf]) e va nella cartella proposta
 * (es. Documenti/Casa/Bollette), altrimenti è fatto di sole immagini e va in Documenti/Eurise/Scansioni.
 */
private suspend fun save(
    context: Context, pages: List<File>, read: List<ReadPage>?, folder: String?, dir: File, base: String, accountId: String?, accountName: String?,
): String = withContext(Dispatchers.IO) {
    val safe = base.replace('/', '-').removeSuffix(".pdf") + ".pdf"
    val pdf = File(dir, "out.pdf")
    val place = folder ?: "Eurise/Scansioni"
    try {
        val searchable = read != null && PageProcessor.writeSearchablePdf(pages, read, pdf, base)
        if (!searchable) PageProcessor.writePdf(pages, pdf)
        val phonePlace = if (searchable) place else "Eurise/Scansioni"
        val cloudPlace = if (searchable && folder != null) "Eurise/$folder" else "Eurise/Scansioni"
        if (accountId == null) {
            if (PageProcessor.saveToPhone(context, pdf, safe, phonePlace)) context.getString(R.string.scan_saved_phone_in, phonePlace) else context.getString(R.string.scan_failed)
        } else {
            val dav = CloudAccounts.get(context).dav(accountId) ?: return@withContext context.getString(R.string.scan_failed)
            dav.mkdirs(cloudPlace)
            dav.upload("$cloudPlace/$safe", pdf)
            context.getString(R.string.scan_saved_cloud_in, cloudPlace, accountName.orEmpty())
        }
    } catch (e: java.io.IOException) {
        context.getString(R.string.scan_failed)
    } catch (e: eu.eurise.core.cloud.DavException) {
        context.getString(R.string.scan_failed)
    } catch (e: OutOfMemoryError) {
        context.getString(R.string.scan_failed)
    } catch (e: IllegalStateException) {
        context.getString(R.string.scan_failed)
    }
}

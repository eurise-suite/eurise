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
import androidx.compose.animation.core.animateFloat
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

/**
 * Da cosa è nata una pagina: serve a rifarla (ritaglio, aspetto, rotazione) senza perdere qualità. [found] sono i bordi
 * trovati allo scatto (per «Bordi trovati» e l'aggancio nel ritaglio), [blurry] se la foto è mossa.
 */
internal data class Origin(
    val upright: File,
    val mode: ScanMode,
    val quad: Quad?,
    val strength: Float = 1f,
    val turns: Int = 0,
    val found: Quad? = quad,
    val blurry: Boolean = false,
)

// Abbastanza per i bordi del foglio (rette di Hough), pochi millisecondi per fotogramma.
private const val ANALYSIS_WIDTH = 200
// I lati si rifiniscono su un'immagine più grande: angoli precisi al pixel anche in anteprima.
private const val REFINE_WIDTH = 640
private const val PHOTO_REFINE_WIDTH = 1200

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
            reviewing -> ScanEditor(pages, origins, dir, onAdd = { reviewing = false }, onCancel = onClose, onSaved = onClose)
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

/** Luminosità ridotta a circa [columns] colonne, già ruotata come la vede la persona. */
private fun lumaOf(image: ImageProxy, columns: Int = ANALYSIS_WIDTH): Triple<IntArray, Int, Int> {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val step = maxOf(1, image.width / columns)
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
    // Scatto automatico come kDrive: una foto quando foglio e telefono sono fermi, poi «Va bene?».
    val auto = remember { AutoShot() }
    var confirm by remember { mutableStateOf<File?>(null) }
    val confirmNow = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val modeNow = remember { java.util.concurrent.atomic.AtomicReference(if (startQr) ScanMode.Qr else ScanMode.Document) }
    var qr by remember { mutableStateOf<String?>(null) }
    // Lettore QR: «visto» quando c'è un codice nel mirino ma non si legge ancora, «preso» quando è letto.
    var qrSeen by remember { mutableStateOf(false) }
    var qrLock by remember { mutableStateOf(false) }
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
    val frameMotion = remember { FrameMotion() }
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

    /** Scatta: prima la messa a fuoco sul foglio (al massimo un secondo e mezzo), poi la foto, poi la conferma. */
    fun shoot(byHand: Boolean = false) {
        if (busy) return
        busy = true
        if (byHand) auto.shotByHand(quad)
        val corners = quad
        val chosen = mode
        val take = {
            capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        val done = withContext(Dispatchers.Default) { process(image, corners, chosen, dir) }
                        if (done != null) {
                            pages += done.first
                            origins[done.first] = done.second
                            confirm = done.first
                            confirmNow.set(true)
                        } else auto.rearm()
                        still = 0
                        busy = false
                    }
                }
                override fun onError(exception: ImageCaptureException) { busy = false; auto.rearm() }
            })
            Unit
        }
        val cam = camera
        val center = corners?.points?.let { p -> Pt(p.map { it.x }.average().toFloat(), p.map { it.y }.average().toFloat()) }
        if (cam == null || center == null) { take(); return }
        val point = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(center.x, center.y)
        val action = androidx.camera.core.FocusMeteringAction.Builder(point, androidx.camera.core.FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
        val fired = java.util.concurrent.atomic.AtomicBoolean(false)
        val once = { if (fired.compareAndSet(false, true)) take() }
        runCatching {
            cam.cameraControl.startFocusAndMetering(action).addListener({ once() }, ContextCompat.getMainExecutor(context))
        }.onFailure { once() }
        scope.launch { kotlinx.coroutines.delay(1500); once() }
    }

    LaunchedEffect(mode) {
        modeNow.set(mode)
        if (mode == ScanMode.Qr) { quad = null; still = 0 }
    }
    var owed by remember { mutableStateOf(0) }
    LaunchedEffect(owed) { if (owed > 0 && !busy && confirm == null) shoot() }

    qr?.let { text -> QrSheet(text) { qr = null; qrShown.set(false) } }
    if (qrHistory) QrHistorySheet(onOpen = { qrHistory = false; qrShown.set(true); qr = it }, onClose = { qrHistory = false })
    if (qrCreate) QrCreateSheet(onClose = { qrCreate = false })
    confirm?.let { page ->
        ConfirmPage(
            page = page,
            count = pages.size,
            onRedo = {
                pages.remove(page); origins.remove(page); page.delete()
                confirm = null; confirmNow.set(false); auto.rearm()
            },
            onAnother = { confirm = null; confirmNow.set(false) },
            onDone = { confirm = null; confirmNow.set(false); onDone() },
        )
        return
    }
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
                                        val read = if (qrShown.get()) QrRead(null, false) else decodeQr(image)
                                        image.close()
                                        val text = read.text
                                        if (text != null && qrShown.compareAndSet(false, true)) {
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                // Un attimo di conferma nel mirino (verde, si stringe), poi i dati.
                                                qrLock = true
                                                scope.launch { kotlinx.coroutines.delay(380); qr = text; qrLock = false; qrSeen = false }
                                            }
                                        } else if (text == null) {
                                            ContextCompat.getMainExecutor(ctx).execute { qrSeen = read.seen }
                                        }
                                        return@setAnalyzer
                                    }
                                    val (luma, w, h) = lumaOf(image)
                                    val coarse = EdgeDetector.find(luma, w, h)
                                    // Lati rifiniti su 640 colonne: il riquadro aderisce al foglio, non solo ci assomiglia.
                                    val found = coarse?.let { c -> val (big, bw, bh) = lumaOf(image, REFINE_WIDTH); SideRefiner.refine(big, bw, bh, c) }
                                    image.close()
                                    val motion = frameMotion.update(luma)
                                    val shootNow = auto.onFrame(found, motion) && !confirmNow.get()
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        val previous = quad
                                        // Un po' di calma nel disegno: piccoli tremolii si smorzano, i salti veri passano subito.
                                        quad = if (found != null && previous != null && found.distance(previous) < 0.03f) previous.blend(found, 0.5f) else found
                                        still = auto.still
                                        if (shootNow) owed++
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
            if (mode == ScanMode.Qr) QrViewfinder(seen = qrSeen, locked = qrLock, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                DarkRound(EuriseIcons.close(), stringResource(R.string.scan_close), onClose)
                val status = when {
                    mode == ScanMode.Qr && qrLock -> R.string.qr_status_read
                    mode == ScanMode.Qr && qrSeen -> R.string.qr_status_seen
                    mode == ScanMode.Qr -> R.string.scan_status_qr
                    busy -> R.string.scan_status_saving
                    !auto.armed && confirm == null -> R.string.scan_status_next
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
                Box(Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).clickable(enabled = !busy && mode != ScanMode.Qr) { shoot(byHand = true) }.padding(8.dp).semantics { contentDescription = shutter }) {
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
/** Esito di un fotogramma: il testo se letto; [seen] se nel mirino c'è un QR riconoscibile ma non ancora leggibile. */
private class QrRead(val text: String?, val seen: Boolean)

private fun decodeQr(image: ImageProxy): QrRead {
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
            return QrRead(LiveReader.decodeWithState(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))).text, true)
        } catch (e: com.google.zxing.ReaderException) {
            // si prova la variante successiva
        } finally {
            LiveReader.reset()
        }
    }
    // Non si legge: si guarda almeno se nel centro ci sono i tre quadrati di un QR (per dire «tieni fermo»).
    val seen = try {
        com.google.zxing.qrcode.detector.Detector(com.google.zxing.common.HybridBinarizer(center).blackMatrix).detect()
        true
    } catch (e: com.google.zxing.ReaderException) {
        false
    } catch (e: RuntimeException) {
        false
    }
    return QrRead(null, seen)
}

/**
 * Il mirino del lettore: quattro angoli e una linea che scorre mentre cerca; angoli azzurri che pulsano quando vede un
 * codice; verdi e più stretti quando lo ha letto.
 */
@Composable
private fun QrViewfinder(seen: Boolean, locked: Boolean, modifier: Modifier) {
    val loop = androidx.compose.animation.core.rememberInfiniteTransition(label = "qr")
    val sweep by loop.animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1700), androidx.compose.animation.core.RepeatMode.Reverse), label = "sweep",
    )
    val pulse by loop.animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(520), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse",
    )
    val shrink by androidx.compose.animation.core.animateFloatAsState(if (locked) 0.86f else if (seen) 0.94f else 1f, label = "shrink")
    val color by androidx.compose.animation.animateColorAsState(
        when { locked -> Color(0xFF93BBA0); seen -> Color(0xFFA9C1F2); else -> Color.White }, label = "color",
    )
    Canvas(modifier) {
        val side = minOf(size.width, size.height) * 0.64f * shrink
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        val arm = side * 0.2f
        val width = (if (seen && !locked) 5f + 2f * pulse else 5f).dp.toPx()
        val stroke = Stroke(width = width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // Velo fuori dal mirino: l'occhio va al centro.
        drawRect(Color(0x66000000), size = androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(Color(0x66000000), topLeft = Offset(0f, top + side), size = androidx.compose.ui.geometry.Size(size.width, size.height - top - side))
        drawRect(Color(0x66000000), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, side))
        drawRect(Color(0x66000000), topLeft = Offset(left + side, top), size = androidx.compose.ui.geometry.Size(size.width - left - side, side))
        listOf(
            Triple(Offset(left, top), 1f, 1f), Triple(Offset(left + side, top), -1f, 1f),
            Triple(Offset(left, top + side), 1f, -1f), Triple(Offset(left + side, top + side), -1f, -1f),
        ).forEach { (c, dx, dy) ->
            val path = Path().apply { moveTo(c.x, c.y + dy * arm); lineTo(c.x, c.y); lineTo(c.x + dx * arm, c.y) }
            drawPath(path, color, style = stroke)
        }
        if (!seen && !locked) {
            val y = top + side * (0.08f + 0.84f * sweep)
            drawLine(
                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color(0xCCA9C1F2), Color.Transparent), left, left + side),
                Offset(left + side * 0.06f, y), Offset(left + side * 0.94f, y), strokeWidth = 2.5.dp.toPx(),
            )
        }
    }
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
    val found = refineCoarse(upright, live) ?: return null
    // I lati, rifiniti sulla foto vera a 1200 colonne.
    val w = PHOTO_REFINE_WIDTH
    val h = (upright.height.toLong() * w / upright.width).toInt().coerceAtLeast(1)
    val big = Bitmap.createScaledBitmap(upright, w, h, true)
    val pixels = IntArray(w * h).also { big.getPixels(it, 0, w, 0, 0, w, h) }
    val luma = IntArray(w * h) { i -> val c = pixels[i]; (299 * ((c shr 16) and 0xFF) + 587 * ((c shr 8) and 0xFF) + 114 * (c and 0xFF)) / 1000 }
    return SideRefiner.refine(luma, w, h, found)
}

private fun refineCoarse(upright: Bitmap, live: Quad?): Quad? {
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
    val found = refine(upright, quad)
    val flat = PageProcessor.straighten(upright, found)
    val blurry = runCatching { PageProcessor.luma(flat).let { (l, w, h) -> Sharpness.isBlurry(l, w, h) } }.getOrDefault(false)
    val origin = Origin(original, mode, found, blurry = blurry)
    render(upright, origin, dir) to origin
} catch (e: RuntimeException) {
    null
} catch (e: OutOfMemoryError) {
    null
}

internal fun render(upright: Bitmap, origin: Origin, dir: File): File {
    val flat = PageProcessor.rotate(PageProcessor.straighten(upright, origin.quad), origin.turns)
    val page = PageProcessor.applyMode(flat, origin.mode, origin.strength)
    return File(dir, UUID.randomUUID().toString() + ".jpg").also { f -> f.outputStream().use { page.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
}

/** Quanto è cambiata l'immagine dal fotogramma prima: differenza media di luminosità (telefono fermo = valori bassi). */
private class FrameMotion {
    private var previous: IntArray? = null

    fun update(luma: IntArray): Float {
        val prev = previous
        previous = luma
        if (prev == null || prev.size != luma.size) return Float.MAX_VALUE
        var sum = 0L
        var n = 0
        var i = 0
        while (i < luma.size) { sum += kotlin.math.abs(luma[i] - prev[i]); n++; i += 3 }
        return if (n == 0) 0f else sum.toFloat() / n
    }
}

/** Il quadrilatero a metà strada verso [other] (peso [t] al nuovo). */
private fun Quad.blend(other: Quad, t: Float): Quad {
    fun mix(a: Pt, b: Pt) = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    return Quad(mix(tl, other.tl), mix(tr, other.tr), mix(br, other.br), mix(bl, other.bl))
}

/** Dopo lo scatto, come kDrive: la pagina già raddrizzata e tre scelte. Il riquadro si corregge poi nella revisione. */
@Composable
private fun ConfirmPage(page: File, count: Int, onRedo: () -> Unit, onAnother: () -> Unit, onDone: () -> Unit) {
    BackHandler(onBack = onRedo)
    val bitmap by produceState<Bitmap?>(null, page) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(page.path, BitmapFactory.Options().apply { inSampleSize = 2 }) }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF14171C)).statusBarsPadding().navigationBarsPadding()) {
        Text(
            stringResource(R.string.scan_confirm_title, count), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        )
        Text(stringResource(R.string.scan_confirm_hint), color = Color(0xFFC9CED8), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            bitmap?.let { b ->
                Image(
                    b.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.aspectRatio(b.width.toFloat() / b.height).clip(RoundedCornerShape(6.dp)).background(Color.White),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(R.string.scan_confirm_redo, false, onRedo),
                Triple(R.string.scan_confirm_another, true, onAnother),
                Triple(R.string.scan_confirm_done, false, onDone),
            ).forEach { (label, strong, action) ->
                Box(
                    Modifier.weight(1f).height(52.dp).clip(CircleShape)
                        .background(if (strong) Color(0xFFA9C1F2) else Color(0x33FFFFFF)).clickable(onClick = action),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(label), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (strong) Color(0xFF14203A) else Color.White, maxLines = 2,
                    )
                }
            }
        }
    }
}

/** La miniatura dell'ultima pagina accanto al pulsante di scatto. */
@Composable
private fun Thumb(file: File, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 8 }) }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = modifier.clip(RoundedCornerShape(4.dp))) }
}

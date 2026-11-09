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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors

private const val ANALYSIS_WIDTH = 120
private const val STILL_NEEDED = 12
private const val STILL_LIMIT = 0.025f

/** Scanner: inquadra, riconosce i bordi e scatta da solo quando il foglio è fermo, poi salva un PDF. */
@Composable
fun ScannerRoute(onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var explain by remember { mutableStateOf(!granted) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) onClose()
    }
    val pages = remember { mutableStateListOf<File>() }
    var reviewing by remember { mutableStateOf(false) }
    val dir = remember { File(context.cacheDir, "scan").apply { deleteRecursively(); mkdirs() } }
    DisposableEffect(Unit) { onDispose { dir.deleteRecursively() } }

    BackHandler(enabled = reviewing) { reviewing = false }
    Box(Modifier.fillMaxSize().background(if (reviewing) MaterialTheme.colorScheme.background else Color(0xFF14171C))) {
        when {
            reviewing -> Review(pages, dir, onAdd = { reviewing = false }, onCancel = onClose, onSaved = onClose)
            granted -> Camera(pages, dir, onClose) { if (pages.isNotEmpty()) reviewing = true }
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
private fun Camera(pages: MutableList<File>, dir: File, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(ScanMode.Document) }
    var quad by remember { mutableStateOf<Quad?>(null) }
    var still by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
            .build()
    }
    val analyzer = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analyzer.shutdown() } }

    fun shoot() {
        if (busy) return
        busy = true
        val corners = quad
        val chosen = mode
        capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                scope.launch {
                    val file = withContext(Dispatchers.Default) { process(image, corners, chosen, dir) }
                    if (file != null) pages += file
                    still = 0
                    // Dopo uno scatto si aspetta che il foglio cambi: niente doppioni dello stesso.
                    busy = false
                }
            }
            override fun onError(exception: ImageCaptureException) { busy = false }
        })
    }

    val owed = still >= STILL_NEEDED
    LaunchedEffect(owed) { if (owed && !busy) shoot() }

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
                                val analysis = ImageAnalysis.Builder().setResolutionSelector(selector)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                                analysis.setAnalyzer(analyzer) { image ->
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
                listOf(ScanMode.Document to R.string.scan_mode_doc, ScanMode.BlackWhite to R.string.scan_mode_bw, ScanMode.Photo to R.string.scan_mode_photo).forEach { (m, label) ->
                    val active = m == mode
                    Box(Modifier.height(34.dp).clip(CircleShape).background(if (active) Color.White else Color.Transparent).clickable { mode = m }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(label), fontSize = 13.5.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, color = if (active) Color(0xFF1E1D1B) else Color(0xFFECE9E3))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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
                Box(Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).clickable(enabled = !busy) { shoot() }.padding(8.dp).semantics { contentDescription = shutter }) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White))
                }
                Box(Modifier.height(44.dp).clip(CircleShape).background(if (pages.isEmpty()) Color(0x55A9C1F2) else Color(0xFFA9C1F2)).clickable(enabled = pages.isNotEmpty(), onClick = onDone).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.scan_done), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14203A))
                }
            }
        }
    }
}

@Composable
private fun DarkRound(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x8C14171C)).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

private fun process(image: ImageProxy, quad: Quad?, mode: ScanMode, dir: File): File? = try {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val rotation = image.imageInfo.rotationDegrees
    image.close()
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val upright = if (rotation != 0) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true) else decoded
    val page = PageProcessor.applyMode(PageProcessor.straighten(upright, quad), mode)
    File(dir, UUID.randomUUID().toString() + ".jpg").also { f -> f.outputStream().use { page.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
} catch (e: RuntimeException) {
    null
} catch (e: OutOfMemoryError) {
    null
}

@Composable
private fun Thumb(file: File, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 8 }) }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = modifier.clip(RoundedCornerShape(4.dp))) }
}

@Composable
private fun Review(pages: MutableList<File>, dir: File, onAdd: () -> Unit, onCancel: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val accounts = remember { CloudAccounts.get(context).accounts.value }
    val fallback = stringResource(R.string.scan_default_name)
    var name by remember { mutableStateOf("${LocalDate.now()}_$fallback") }
    var where by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.scan_cancel)) }
            Text(stringResource(R.string.scan_review_title, pages.size), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Button(enabled = !saving && pages.isNotEmpty() && name.isNotBlank(), onClick = {
                saving = true
                scope.launch {
                    val message = save(context, pages, dir, name.trim(), where, accounts.firstOrNull { it.id == where }?.name)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    saving = false
                    if (message != context.getString(R.string.scan_failed)) onSaved()
                }
            }) { Text(stringResource(R.string.scan_save)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            pages.toList().forEachIndexed { i, file ->
                val label = stringResource(R.string.scan_remove, i + 1)
                Box(Modifier.size(92.dp, 124.dp).clickable { pages.remove(file); if (pages.isEmpty()) onAdd() }.semantics { contentDescription = label }) {
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
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.scan_name)) }, suffix = { Text(".pdf") }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.scan_where), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()).heightIn(min = 36.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(stringResource(R.string.scan_phone), dot = null, selected = where == null) { where = null }
                accounts.forEach { a -> FilterPill(a.name, dot = null, selected = where == a.id) { where = a.id } }
            }
        }
        Text(stringResource(R.string.scan_local_note), fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(16.dp))
    }
}

private suspend fun save(context: Context, pages: List<File>, dir: File, base: String, accountId: String?, accountName: String?): String = withContext(Dispatchers.IO) {
    val safe = base.replace('/', '-').removeSuffix(".pdf") + ".pdf"
    val pdf = File(dir, "out.pdf")
    try {
        PageProcessor.writePdf(pages, pdf)
        if (accountId == null) {
            if (PageProcessor.saveToPhone(context, pdf, safe)) context.getString(R.string.scan_saved_phone) else context.getString(R.string.scan_failed)
        } else {
            val dav = CloudAccounts.get(context).dav(accountId) ?: return@withContext context.getString(R.string.scan_failed)
            dav.mkdirs("Eurise/Scansioni")
            dav.upload("Eurise/Scansioni/$safe", pdf)
            context.getString(R.string.scan_saved_cloud, accountName.orEmpty())
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

package eu.eurise.feature.drive

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import eu.eurise.core.data.docs.DocReading
import eu.eurise.core.data.docs.ReadClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.cloud.DavException
import eu.eurise.core.cloud.DavShare
import eu.eurise.core.cloud.DavVersion
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.DockItem
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.component.EuriseDock
import eu.eurise.core.cloud.crypto.Cryptomator
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// Lettore e annotazioni dei PDF della suite, staccati da DriveRoute.kt.

// --- Lettore PDF ---
/** Il motore di sistema legge una pagina alla volta: il Mutex impedisce due letture insieme. */
private class PdfSource(file: File) : AutoCloseable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val mutex = Mutex()
    val pageCount = renderer.pageCount
    val ratios: List<Float> = (0 until pageCount).map { i -> renderer.openPage(i).use { it.width.toFloat() / it.height } }
    @Volatile private var closed = false

    suspend fun render(index: Int, width: Int): Bitmap? = mutex.withLock {
        if (closed) return@withLock null
        withContext(Dispatchers.Default) {
            renderer.openPage(index).use { page ->
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(android.graphics.Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    /** Pagine in cui compare [query] e quante volte. Il motore di sistema legge il testo solo da Android 15. */
    @androidx.annotation.RequiresApi(35)
    suspend fun search(query: String): List<Pair<Int, Int>> = mutex.withLock {
        withContext(Dispatchers.Default) {
            (0 until pageCount).mapNotNull { i ->
                val hits = renderer.openPage(i).use { it.searchText(query).size }
                if (hits > 0) i to hits else null
            }
        }
    }

    /** Ricrea il PDF pagina per pagina come immagini, disegnando sopra i tratti. */
    suspend fun exportWithInk(ink: Map<Int, List<List<Offset>>>, target: File, density: Float) = mutex.withLock {
        withContext(Dispatchers.Default) {
            val document = android.graphics.pdf.PdfDocument()
            try {
                for (i in 0 until pageCount) {
                    renderer.openPage(i).use { source ->
                        val out = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(source.width, source.height, i + 1).create())
                        val width = source.width * 2
                        val height = source.height * 2
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            source.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        }
                        out.canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, source.width, source.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                        bitmap.recycle()
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.rgb(20, 32, 90)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.6f
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        ink[i].orEmpty().forEach { stroke ->
                            val path = android.graphics.Path()
                            stroke.forEachIndexed { n, p ->
                                val x = p.x * source.width
                                val y = p.y * source.height
                                if (n == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            out.canvas.drawPath(path, paint)
                        }
                        document.finishPage(out)
                    }
                }
                target.outputStream().use { document.writeTo(it) }
            } finally {
                document.close()
            }
        }
    }

    override fun close() {
        closed = true
        renderer.close()
        descriptor.close()
    }
}

private fun savePdfCopy(context: Context, file: File, name: String): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Eurise/Firmati")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Files.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
        return true
    }
    @Suppress("DEPRECATION")
    val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Eurise/Firmati").apply { mkdirs() }
    file.copyTo(File(folder, name), overwrite = false)
    return true
}

/** Si disegna con il dito su una pagina alla volta; il salvataggio crea una copia e l'originale resta intatto. */
@Composable
private fun PdfAnnotator(source: PdfSource, start: Int, file: File, title: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    var page by remember { mutableIntStateOf(start) }
    val ink = remember { androidx.compose.runtime.mutableStateMapOf<Int, List<List<Offset>>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(scheme.surfaceVariant).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.drive_cancel)) }
            Text(stringResource(R.string.drive_pdf_page, page + 1, source.pageCount), fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { ink[page] = ink[page].orEmpty().dropLast(1) }) { Icon(EuriseIcons.undo(), contentDescription = stringResource(R.string.drive_pdf_undo)) }
            TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    val ok = runCatching {
                        val out = File(context.cacheDir, "drive/firmato.pdf").apply { parentFile?.mkdirs() }
                        source.exportWithInk(ink.toMap(), out, density)
                        withContext(Dispatchers.IO) { savePdfCopy(context, out, title.removeSuffix(".pdf") + " firmato.pdf") }
                    }.getOrDefault(false)
                    saving = false
                    Toast.makeText(context, if (ok) R.string.drive_pdf_saved else R.string.drive_error, Toast.LENGTH_LONG).show()
                    if (ok) onClose()
                }
            }) { Text(stringResource(R.string.drive_pdf_save)) }
        }
        Text(stringResource(R.string.drive_pdf_save_note), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            val pixels = with(LocalDensity.current) { maxWidth.toPx().toInt() }
            val bitmap by produceState<Bitmap?>(null, page) { value = runCatching { source.render(page, pixels) }.getOrNull() }
            Box(Modifier.aspectRatio(source.ratios[page], matchHeightConstraintsFirst = maxWidth / maxHeight > source.ratios[page]).background(Color.White)) {
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
                androidx.compose.foundation.Canvas(
                    Modifier.fillMaxSize().pointerInput(page) {
                        detectDragGestures(
                            onDragStart = { current = listOf(Offset(it.x / size.width, it.y / size.height)) },
                            onDrag = { change, _ ->
                                change.consume()
                                current = current + Offset(change.position.x / size.width, change.position.y / size.height)
                            },
                            onDragEnd = {
                                ink[page] = ink[page].orEmpty() + listOf(current)
                                current = emptyList()
                            },
                        )
                    },
                ) {
                    (ink[page].orEmpty() + listOf(current)).filter { it.size > 1 }.forEach { stroke ->
                        val path = androidx.compose.ui.graphics.Path()
                        stroke.forEachIndexed { n, p -> if (n == 0) path.moveTo(p.x * size.width, p.y * size.height) else path.lineTo(p.x * size.width, p.y * size.height) }
                        drawPath(path, Color(0xFF14205A), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = page > 0, onClick = { page-- }) { Text(stringResource(R.string.drive_pdf_prev)) }
            TextButton(enabled = page < source.pageCount - 1, onClick = { page++ }) { Text(stringResource(R.string.drive_pdf_next)) }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PdfViewer(file: File, title: String, onEdit: (() -> Unit)? = null, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val source = remember(file) { runCatching { PdfSource(file) }.getOrNull() }
    DisposableEffect(source) { onDispose { source?.close() } }
    var scale by remember { mutableFloatStateOf(1f) }
    val transform = rememberTransformableState { zoom, _, _ -> scale = (scale * zoom).coerceIn(1f, 4f) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    var annotating by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<Int, Int>>?>(null) }
    val canSearch = Build.VERSION.SDK_INT >= 35

    if (annotating && source != null) {
        PdfAnnotator(source, list.firstVisibleItemIndex.coerceAtMost(source.pageCount - 1), file, title) { annotating = false }
        return
    }

    // Tutto schermo (25 novembre): da solo in orizzontale, dal pulsante in alto in verticale; la maniglia dal lato della
    // mano richiama l'intestazione, che dopo qualche secondo si rinasconde.
    val context = androidx.compose.ui.platform.LocalContext.current
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var fullChoice by androidx.compose.runtime.saveable.rememberSaveable(landscape) { mutableStateOf<Boolean?>(null) }
    val immersive = (fullChoice ?: landscape) && source != null && !searching
    var barUp by remember(immersive) { mutableStateOf(false) }
    val leftHanded = remember { context.getSharedPreferences("eurise_launcher", Context.MODE_PRIVATE).getBoolean("left_handed", false) }
    eu.eurise.core.designsystem.component.SystemBarsHidden(immersive)
    androidx.compose.runtime.LaunchedEffect(barUp) { if (barUp) { kotlinx.coroutines.delay(5_000); barUp = false } }
    BackHandler(enabled = immersive) { if (barUp) barUp = false else fullChoice = false }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(scheme.surfaceVariant)) {
        if (!immersive || barUp) Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(EuriseIcons.chevronLeft(), contentDescription = stringResource(R.string.drive_back), tint = scheme.primary) }
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (source != null) {
                if (immersive) IconButton(onClick = { fullChoice = false }) { Icon(EuriseIcons.fullscreenExit(), contentDescription = stringResource(eu.eurise.core.designsystem.R.string.ds_fullscreen_exit)) }
                else IconButton(onClick = { fullChoice = true }) { Icon(EuriseIcons.fullscreen(), contentDescription = stringResource(eu.eurise.core.designsystem.R.string.ds_fullscreen)) }
                if (canSearch) IconButton(onClick = { searching = !searching; results = null }) { Icon(EuriseIcons.search(), contentDescription = stringResource(R.string.drive_pdf_search)) }
                if (onEdit != null) {
                    // Office c'è: annotare, firmare e lavorare sulle pagine si fa lì, sul file stesso.
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.drive_pdf_office), fontWeight = FontWeight.SemiBold) }
                } else {
                    IconButton(onClick = { annotating = true }) { Icon(EuriseIcons.pen(), contentDescription = stringResource(R.string.drive_pdf_annotate)) }
                }
            }
        }
        if (source == null) {
            Text(stringResource(R.string.drive_pdf_error), modifier = Modifier.padding(24.dp))
            return
        }
        if (searching && canSearch) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.drive_pdf_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { if (query.isNotBlank() && Build.VERSION.SDK_INT >= 35) scope.launch { results = runCatching { source.search(query.trim()) }.getOrDefault(emptyList()) } }) {
                            Icon(EuriseIcons.search(), contentDescription = stringResource(R.string.drive_pdf_search))
                        }
                    },
                )
                results?.let { found ->
                    if (found.isEmpty()) Text(stringResource(R.string.drive_pdf_no_results), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        found.forEach { (index, hits) ->
                            FilterPill(stringResource(R.string.drive_pdf_result, index + 1, hits), dot = null, selected = false) { scope.launch { list.scrollToItem(index) } }
                        }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().transformable(transform, canPan = { false }).horizontalScroll(rememberScrollState())) {
            val width = maxWidth * scale
            val pixels = with(LocalDensity.current) { maxWidth.toPx().toInt() }
            LazyColumn(Modifier.width(width).fillMaxSize(), state = list, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(source.ratios, key = { i, _ -> i }) { i, ratio ->
                    // Risoluzione fissata a 2× schermo: abbastanza per lo zoom, senza riempire la memoria.
                    val bitmap by produceState<Bitmap?>(null, source, i) { value = runCatching { source.render(i, pixels * 2) }.getOrNull() }
                    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White)) {
                        val page = stringResource(R.string.drive_pdf_page, i + 1, source.pageCount)
                        bitmap?.let { Image(it.asImageBitmap(), contentDescription = page, modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
        }
    }
    if (immersive && !barUp) eu.eurise.core.designsystem.component.FullscreenHandle(Modifier.align(if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd)) { barUp = true }
    }
}

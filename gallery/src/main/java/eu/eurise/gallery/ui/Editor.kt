package eu.eurise.gallery.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.component.ColorRow
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.gallery.R
import eu.eurise.gallery.data.applyCurve
import eu.eurise.gallery.data.drawMarks
import eu.eurise.gallery.data.drawVignette
import eu.eurise.gallery.data.editorPreview
import eu.eurise.gallery.data.geometry
import eu.eurise.gallery.data.histogram
import eu.eurise.gallery.data.saveEdited
import eu.eurise.gallery.logic.Adjust
import eu.eurise.gallery.logic.EditState
import eu.eurise.gallery.logic.InkStroke
import eu.eurise.gallery.logic.Look
import eu.eurise.gallery.logic.MarkStyle
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.Nib
import eu.eurise.gallery.logic.TextMark
import eu.eurise.gallery.logic.adjustMatrix
import eu.eurise.gallery.logic.autoAdjust
import eu.eurise.gallery.logic.dragCrop
import eu.eurise.gallery.logic.dragCropRatio
import eu.eurise.gallery.logic.erase
import eu.eurise.gallery.logic.flipped
import eu.eurise.gallery.logic.ratioCrop
import eu.eurise.gallery.logic.rotated
import eu.eurise.gallery.logic.straightenScale
import eu.eurise.gallery.logic.toneLut
import eu.eurise.gallery.logic.CurveChannel
import eu.eurise.gallery.logic.CurvePoint
import eu.eurise.gallery.logic.CurvePreset
import eu.eurise.gallery.logic.SavedLook
import eu.eurise.gallery.logic.curveLut
import eu.eurise.gallery.logic.develop
import eu.eurise.gallery.logic.Perspective
import eu.eurise.gallery.logic.ExpandFill
import eu.eurise.gallery.data.perspective
import eu.eurise.gallery.data.expanded
import eu.eurise.gallery.logic.neutralize
import eu.eurise.gallery.data.GalleryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Ingrandimento massimo con due dita nell'editor. */
private const val MAX_ZOOM = 6f

private enum class Tool(val label: Int) {
    Crop(R.string.editor_tool_crop),
    Adjust(R.string.editor_tool_adjust),
    Filters(R.string.editor_tool_filters),
    Write(R.string.editor_tool_text),
    Draw(R.string.editor_tool_draw),
}

// L'editor è sempre scuro, come nelle anteprime: i colori della foto si giudicano meglio.
private val Ground = Color(0xFF0E1014)
private val Panel = Color(0xFF1C2027)
private val Raised = Color(0xFF262B33)
private val Line = Color(0xFF323843)
private val Text1 = Color(0xFFECE9E3)
private val Muted = Color(0xFFA4A9B2)
private val Accent = Color(0xFFA9C1F2)
private val AccentVeil = Color(0xFF2C3A57)
private val OnAccent = Color(0xFF14203A)

/** Colori per scritte e disegno, scelti per leggersi sulle foto; "Il tuo" apre il selettore della suite. */
private val INK = listOf(0xFFFFFFFF, 0xFF14171C, 0xFFF2D06B, 0xFFE8505B, 0xFF8FA9D9, 0xFF93BBA0).map { Color(it) }

private val LookNames = mapOf(
    Look.None to R.string.editor_filter_none, Look.Lisbona to R.string.look_lisbona, Look.Perla to R.string.look_perla,
    Look.Notte to R.string.look_notte, Look.Vivace to R.string.editor_filter_vivid, Look.Caldo to R.string.editor_filter_warm,
    Look.Freddo to R.string.editor_filter_cold, Look.Pellicola to R.string.look_film, Look.BiancoNero to R.string.editor_filter_bw,
)

/**
 * Modifica su una copia (anteprime GalRitaglio, GalRegola, GalTesto): ritaglio con proporzioni, raddrizza con la
 * ghiera, rotazione e specchio; luce, contrasto, ombre, alte luci, saturazione, calore, vignetta e Automatico; filtri
 * dosabili; scritte in quattro stili; penna, pennarello, evidenziatore e gomma; colori della foto e "Il tuo".
 * Annulla e Ripeti; tenendo premuto l'occhio si vede l'originale.
 */
@Composable
internal fun EditorScreen(item: MediaItem, onClose: () -> Unit, onSaved: (Uri) -> Unit = {}) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source by produceState<Bitmap?>(null, item.uri) { value = withContext(Dispatchers.IO) { runCatching { editorPreview(context, item) }.getOrNull() } }
    var state by remember { mutableStateOf(EditState()) }
    var history by remember { mutableStateOf(emptyList<EditState>()) }
    var future by remember { mutableStateOf(emptyList<EditState>()) }
    var tool by remember { mutableStateOf(Tool.Crop) }
    var ratio by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var writing by remember { mutableStateOf<TextMark?>(null) }
    var color by remember { mutableStateOf(INK[0]) }
    var nib by remember { mutableStateOf<Nib?>(Nib.Pen) }
    var penWidth by remember { mutableFloatStateOf(0.012f) }
    var nextId by remember { mutableLongStateOf(1L) }
    var textStyle by remember { mutableStateOf(MarkStyle.Bold) }
    val savedText = stringResource(R.string.editor_saved)
    val failedText = stringResource(R.string.action_failed)
    val density = LocalDensity.current
    val touch = with(density) { 36.dp.toPx() }

    fun remember_() { history = (history + state).takeLast(40); future = emptyList() }
    fun commit(next: EditState) { if (next != state) { remember_(); state = next } }

    val geo = remember(source, state.turns, state.flip) { source?.let { geometry(it, state.flip, state.turns) } }
    // La prospettiva si applica subito dopo rotazione e specchio, come nella copia salvata.
    val shown = remember(geo, state.perspective) { geo?.let { perspective(it, state.perspective) } }
    var cropSection by remember { mutableStateOf(CropSection.Crop) }
    var freeCorners by remember { mutableStateOf(false) }
    val editingPerspective = tool == Tool.Crop && cropSection == CropSection.Perspective
    val adjust = state.effective
    // Ombre e alte luci non stanno in una matrice: la curva si ricalcola su una copia, poco dopo l'ultimo ritocco.
    // Ciò che la matrice non sa fare (toni, curve, vividezza, struttura, nitidezza) si calcola su una copia, poco dopo
    // l'ultimo ritocco; la matrice resta sulla GPU, così i cursori di luce e colore rispondono subito.
    val pixelPart = adjust.copy(light = 0f, contrast = 0f, saturation = 0f, warmth = 0f, tint = 0f, vignette = 0f)
    val toned by produceState<Bitmap?>(null, shown, pixelPart) {
        val base = shown ?: return@produceState
        if (!pixelPart.needsPixels) { value = null; return@produceState }
        delay(70)
        value = withContext(Dispatchers.Default) {
            val w = base.width; val h = base.height
            val px = IntArray(w * h).also { base.getPixels(it, 0, w, 0, 0, w, h) }
            develop(px, w, h, pixelPart)
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }
    }
    // Espandi: i bordi aggiunti si vedono attorno alla foto, calcolati su una copia piccola.
    val showExpand = state.expand.amount > 0f && !(tool == Tool.Crop && cropSection != CropSection.Expand)
    val expandBg by produceState<Bitmap?>(null, toned, shown, state.expand, showExpand) {
        val base = toned ?: shown
        if (!showExpand || base == null || state.expand.fill == ExpandFill.White || state.expand.fill == ExpandFill.Black) { value = null; return@produceState }
        delay(60)
        value = withContext(Dispatchers.Default) {
            val k = 400f / maxOf(base.width, base.height)
            val small = if (k < 1f) Bitmap.createScaledBitmap(base, (base.width * k).roundToInt().coerceAtLeast(1), (base.height * k).roundToInt().coerceAtLeast(1), true) else base
            expanded(small, state.expand)
        }
    }
    var section by remember { mutableStateOf(AdjustSection.Light) }
    var channel by remember { mutableStateOf(CurveChannel.Luma) }
    var pickingWhite by remember { mutableStateOf(false) }
    val store = remember { GalleryStore.get(context) }
    var myLooks by remember { mutableStateOf(emptyList<SavedLook>()) }
    LaunchedEffect(Unit) { myLooks = withContext(Dispatchers.IO) { store.myLooks() } }
    var naming by remember { mutableStateOf<SavedLook?>(null) }
    var managing by remember { mutableStateOf<SavedLook?>(null) }
    fun storeLooks(next: List<SavedLook>) { myLooks = next; scope.launch(Dispatchers.IO) { store.saveMyLooks(next) } }
    val thumb = remember(shown) { shown?.let { Bitmap.createScaledBitmap(it, 160, (160f * it.height / it.width).roundToInt().coerceAtLeast(1), true).asImageBitmap() } }

    Column(Modifier.fillMaxSize().background(Ground).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderButton(EuriseIcons.close(), stringResource(R.string.close), onClick = onClose)
            HeaderButton(EuriseIcons.undo(), stringResource(R.string.editor_undo), enabled = history.isNotEmpty()) {
                future = listOf(state) + future; state = history.last(); history = history.dropLast(1)
            }
            HeaderButton(EuriseIcons.redo(), stringResource(R.string.editor_redo), enabled = future.isNotEmpty()) {
                history = history + state; state = future.first(); future = future.drop(1)
            }
            val compare = stringResource(R.string.editor_compare)
            Box(
                Modifier.size(44.dp).clip(CircleShape).semantics { contentDescription = compare }
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); original = true; waitForUpOrCancellation(); original = false } },
                contentAlignment = Alignment.Center,
            ) { Icon(EuriseIcons.eye(), null, tint = if (original) Accent else Text1, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (busy || shown == null) Raised else Accent)
                    .clickable(enabled = !busy && shown != null) {
                        busy = true
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) { try { saveEdited(context, item, state) } catch (e: Throwable) { null } }
                            Toast.makeText(context, if (saved != null) savedText else failedText, Toast.LENGTH_LONG).show()
                            busy = false
                            if (saved != null) { onSaved(saved); onClose() }
                        }
                    }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.editor_save), color = if (busy) Muted else OnAccent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
        }

        // Zoom con due dita: ingrandisce e sposta la foto mentre la si modifica (un dito resta per ritagliare,
        // scrivere e disegnare). Le coordinate dei gesti restano quelle della foto, quindi gli strumenti non cambiano.
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        fun clampPan(p: Offset, z: Float) = Offset(
            p.x.coerceIn(boxSize.width * (1 - z), 0f),
            p.y.coerceIn(boxSize.height * (1 - z), 0f),
        )
        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().padding(horizontal = 20.dp, vertical = 12.dp).onSizeChanged { boxSize = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                val factor = event.calculateZoom()
                                val moved = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (factor != 1f || moved != Offset.Zero) {
                                    val next = (zoom * factor).coerceIn(1f, MAX_ZOOM)
                                    val anchor = (centroid - moved - pan) / zoom
                                    pan = clampPan(centroid - anchor * next, next)
                                    zoom = next
                                }
                                // Con due dita si ingrandisce soltanto: gli strumenti sotto non ricevono il gesto.
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (zoom < 1.05f) { zoom = 1f; pan = Offset.Zero }
                    }
                },
            contentAlignment = Alignment.TopStart,
        ) {
          Box(Modifier.fillMaxSize().graphicsLayer {
              scaleX = zoom
              scaleY = zoom
              translationX = pan.x
              translationY = pan.y
              transformOrigin = TransformOrigin(0f, 0f)
          }) {
            val bitmap = if (editingPerspective) geo else shown
            if (bitmap != null && boxSize != IntSize.Zero) {
                val margin = if (showExpand) state.expand.margin(bitmap.width, bitmap.height) else 0
                val fit = min(boxSize.width.toFloat() / (bitmap.width + 2 * margin), boxSize.height.toFloat() / (bitmap.height + 2 * margin))
                val w = bitmap.width * fit
                val h = bitmap.height * fit
                val left = (boxSize.width - w) / 2
                val top = (boxSize.height - h) / 2
                val plain = remember(bitmap) { bitmap.asImageBitmap() }
                val curved = remember(toned) { toned?.asImageBitmap() }
                val image = if (original) plain else curved ?: plain
                val cover = straightenScale(state.straighten, w, h)
                if (margin > 0 && !original) {
                    val mw = (bitmap.width + 2 * margin) * fit
                    val mh = (bitmap.height + 2 * margin) * fit
                    val bgMod = Modifier.offset { IntOffset((left - margin * fit).toInt(), (top - margin * fit).toInt()) }
                        .size(with(density) { mw.toDp() }, with(density) { mh.toDp() })
                    when (state.expand.fill) {
                        ExpandFill.White -> Box(bgMod.background(Color.White))
                        ExpandFill.Black -> Box(bgMod.background(Color.Black))
                        else -> expandBg?.let { bg ->
                            val img = remember(bg) { bg.asImageBitmap() }
                            Image(img, null, contentScale = ContentScale.FillBounds, modifier = bgMod,
                                colorFilter = if (adjust.matrixNeutral) null else ColorFilter.colorMatrix(ColorMatrix(adjustMatrix(adjust))))
                        }
                    }
                }
                Box(
                    Modifier.offset { IntOffset(left.toInt(), top.toInt()) }
                        .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                        .clipToBounds(),
                ) {
                    Image(
                        image, null, contentScale = ContentScale.FillBounds,
                        colorFilter = if (original || adjust.matrixNeutral) null else ColorFilter.colorMatrix(ColorMatrix(adjustMatrix(adjust))),
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            rotationZ = state.straighten
                            scaleX = cover
                            scaleY = cover
                        },
                    )
                }
                var corner by remember { mutableIntStateOf(-1) }
                var movingText by remember { mutableStateOf<Long?>(null) }
                val nearestText: (Offset) -> Long? = { p ->
                    state.texts.minByOrNull { hypot(left + it.x * w - p.x, top + it.y * h - p.y) }
                        ?.takeIf { hypot(left + it.x * w - p.x, top + it.y * h - p.y) < touch * 2.5f }?.id
                }
                Canvas(
                    Modifier.fillMaxSize()
                        .pointerInput(tool, w, h) {
                            if (tool == Tool.Write) detectTapGestures(
                                onTap = { p -> selected = nearestText(p) },
                                onDoubleTap = { p -> nearestText(p)?.let { id -> writing = state.texts.first { it.id == id } } },
                            )
                        }
                        .pointerInput(tool, w, h, ratio, nib, section, channel, pickingWhite, cropSection) {
                            when (tool) {
                                Tool.Crop -> if (cropSection == CropSection.Perspective) {
                                    // Prospettiva libera: si trascinano i quattro angoli sulla foto non ancora raddrizzata.
                                    var moving = -1
                                    detectDragGestures(
                                        onDragStart = { p ->
                                            val q = state.perspective.quad()
                                            moving = (0..3).minByOrNull { hypot(left + q[it * 2] * w - p.x, top + q[it * 2 + 1] * h - p.y) }
                                                ?.takeIf { hypot(left + q[it * 2] * w - p.x, top + q[it * 2 + 1] * h - p.y) < touch * 1.6f } ?: -1
                                            if (moving >= 0) { remember_(); freeCorners = true }
                                        },
                                        onDrag = { change, amount ->
                                            if (moving < 0) return@detectDragGestures
                                            change.consume()
                                            state = state.copy(perspective = state.perspective.movedCorner(moving, amount.x / w, amount.y / h))
                                        },
                                    )
                                } else if (cropSection == CropSection.Expand) Unit else detectDragGestures(
                                    onDragStart = { p ->
                                        val c = state.crop
                                        val corners = listOf(c.left to c.top, c.right to c.top, c.left to c.bottom, c.right to c.bottom)
                                        val near = corners.indexOfFirst { (x, y) -> abs(left + x * w - p.x) < touch && abs(top + y * h - p.y) < touch }
                                        corner = if (near >= 0) near else 4
                                        remember_()
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val r = ratio
                                        val moved = if (r != null) dragCropRatio(state.crop, corner, amount.x / w, amount.y / h, r * h / w) else dragCrop(state.crop, corner, amount.x / w, amount.y / h)
                                        state = state.copy(crop = moved)
                                    },
                                )
                                Tool.Write -> detectDragGestures(
                                    onDragStart = { p ->
                                        movingText = nearestText(p)
                                        if (movingText != null) { selected = movingText; remember_() }
                                    },
                                    onDrag = { change, amount ->
                                        val id = movingText ?: return@detectDragGestures
                                        change.consume()
                                        state = state.copy(texts = state.texts.map {
                                            if (it.id == id) it.copy(x = (it.x + amount.x / w).coerceIn(0f, 1f), y = (it.y + amount.y / h).coerceIn(0f, 1f)) else it
                                        })
                                    },
                                )
                                Tool.Draw -> {
                                    val n = nib
                                    if (n == null) detectDragGestures(
                                        onDragStart = { p -> remember_(); state = state.copy(strokes = erase(state.strokes, (p.x - left) / w, (p.y - top) / h, 0.03f)) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            state = state.copy(strokes = erase(state.strokes, (change.position.x - left) / w, (change.position.y - top) / h, 0.03f))
                                        },
                                    ) else detectDragGestures(
                                        onDragStart = { p ->
                                            remember_()
                                            state = state.copy(strokes = state.strokes + InkStroke(listOf((p.x - left) / w to (p.y - top) / h), penWidth, color.toArgb(), n))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val point = (change.position.x - left) / w to (change.position.y - top) / h
                                            val last = state.strokes.last()
                                            state = state.copy(strokes = state.strokes.dropLast(1) + last.copy(points = last.points + point))
                                        },
                                    )
                                }
                                Tool.Adjust -> if (section == AdjustSection.Curves) {
                                    // Curva sopra la foto (tavola GalCurve): x = ingresso, y = uscita (in alto = chiaro).
                                    var moving = -1
                                    detectDragGestures(
                                        onDragStart = { p ->
                                            val cx = ((p.x - left) / w).coerceIn(0f, 1f)
                                            val cy = (1f - (p.y - top) / h).coerceIn(0f, 1f)
                                            val pts = state.adjust.curves.channel(channel)
                                            remember_()
                                            moving = pts.indices.minByOrNull { abs(pts[it].x - cx) }?.takeIf { abs(pts[it].x - cx) < 0.07f } ?: -1
                                            if (moving < 0) {
                                                val y = curveLut(pts)[(cx * 255).roundToInt()] / 255f
                                                val next = (pts + CurvePoint(cx, y)).sortedBy { it.x }
                                                moving = next.indexOfFirst { it.x == cx }
                                                state = state.copy(adjust = state.adjust.copy(curves = state.adjust.curves.with(channel, next)))
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val pts = state.adjust.curves.channel(channel)
                                            if (moving !in pts.indices) return@detectDragGestures
                                            val x = (change.position.x - left) / w
                                            val y = 1f - (change.position.y - top) / h
                                            val next = pts.toMutableList().also { it[moving] = CurvePoint(x.coerceIn(-0.2f, 1.2f), y.coerceIn(-0.2f, 1.2f)) }
                                            state = state.copy(adjust = state.adjust.copy(curves = state.adjust.curves.with(channel, next)))
                                        },
                                        onDragEnd = {
                                            // Trascinato fuori dalla foto: il punto si toglie.
                                            val pts = state.adjust.curves.channel(channel)
                                            val clean = pts.filter { it.x in -0.05f..1.05f && it.y in -0.05f..1.05f }
                                                .map { CurvePoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }.sortedBy { it.x }
                                            state = state.copy(adjust = state.adjust.copy(curves = state.adjust.curves.with(channel, clean)))
                                            moving = -1
                                        },
                                    )
                                } else if (pickingWhite) detectTapGestures { p ->
                                    // Bianco giusto: media di un quadratino attorno al punto toccato, sull'immagine originale.
                                    val b = shown ?: return@detectTapGestures
                                    val bx = (((p.x - left) / w) * b.width).roundToInt().coerceIn(2, b.width - 3)
                                    val by = (((p.y - top) / h) * b.height).roundToInt().coerceIn(2, b.height - 3)
                                    var r = 0; var g = 0; var bl = 0
                                    for (dy in -2..2) for (dx in -2..2) { val c = b.getPixel(bx + dx, by + dy); r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; bl += c and 0xFF }
                                    commit(state.copy(adjust = neutralize(r / 25, g / 25, bl / 25, state.adjust.copy(warmth = 0f, tint = 0f))))
                                    pickingWhite = false
                                } else Unit
                                else -> Unit
                            }
                        },
                ) {
                    val crop = state.crop
                    val x0 = left + crop.left * w
                    val y0 = top + crop.top * h
                    val x1 = left + crop.right * w
                    val y1 = top + crop.bottom * h
                    if (!original) drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        drawVignette(native, x0, y0, x1, y1, adjust.vignette)
                        native.save()
                        native.clipRect(left, top, left + w, top + h)
                        native.translate(left, top)
                        drawMarks(native, w, h, state.texts, state.strokes)
                        native.restore()
                    }
                    if (editingPerspective) {
                        val q = state.perspective.quad()
                        val pts = (0..3).map { Offset(left + q[it * 2] * w, top + q[it * 2 + 1] * h) }
                        val thin = Color.White.copy(alpha = 0.4f)
                        for (i in 1..2) {
                            drawLine(thin, Offset(left + w * i / 3, top), Offset(left + w * i / 3, top + h), 1.dp.toPx())
                            drawLine(thin, Offset(left, top + h * i / 3), Offset(left + w, top + h * i / 3), 1.dp.toPx())
                        }
                        for (i in 0..3) drawLine(Color.White, pts[i], pts[(i + 1) % 4], 2.dp.toPx())
                        pts.forEach { drawCircle(Accent, 11.dp.toPx(), it); drawCircle(Color.White, 7.dp.toPx(), it) }
                        return@Canvas
                    }
                    val shade = Color.Black.copy(alpha = if (tool == Tool.Crop) 0.62f else 0.85f)
                    drawRect(shade, Offset(left, top), Size(w, y0 - top))
                    drawRect(shade, Offset(left, y1), Size(w, top + h - y1))
                    drawRect(shade, Offset(left, y0), Size(x0 - left, y1 - y0))
                    drawRect(shade, Offset(x1, y0), Size(left + w - x1, y1 - y0))
                    if (tool == Tool.Crop) {
                        val thin = Color.White.copy(alpha = 0.45f)
                        for (i in 1..2) {
                            drawLine(thin, Offset(x0 + (x1 - x0) * i / 3, y0), Offset(x0 + (x1 - x0) * i / 3, y1), 1.dp.toPx())
                            drawLine(thin, Offset(x0, y0 + (y1 - y0) * i / 3), Offset(x1, y0 + (y1 - y0) * i / 3), 1.dp.toPx())
                        }
                        drawRect(Color.White, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(2.dp.toPx()))
                        val arm = 22.dp.toPx()
                        val thick = 5.dp.toPx()
                        listOf(Triple(x0, y0, 1f to 1f), Triple(x1, y0, -1f to 1f), Triple(x0, y1, 1f to -1f), Triple(x1, y1, -1f to -1f)).forEach { (x, y, d) ->
                            drawLine(Color.White, Offset(x, y), Offset(x + d.first * arm, y), thick)
                            drawLine(Color.White, Offset(x, y), Offset(x, y + d.second * arm), thick)
                        }
                    }
                    if (tool == Tool.Adjust && section == AdjustSection.Curves && !original) {
                        val grid = Color.White.copy(alpha = 0.22f)
                        for (i in 1..3) {
                            drawLine(grid, Offset(left + w * i / 4, top), Offset(left + w * i / 4, top + h), 1.dp.toPx())
                            drawLine(grid, Offset(left, top + h * i / 4), Offset(left + w, top + h * i / 4), 1.dp.toPx())
                        }
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(left, top + h), Offset(left + w, top), 1.5.dp.toPx())
                        val pts = state.adjust.curves.channel(channel)
                        val lut = curveLut(pts)
                        val ink = when (channel) { CurveChannel.Luma -> Color.White; CurveChannel.Red -> Color(0xFFFF8A80); CurveChannel.Green -> Color(0xFF9BE59B); CurveChannel.Blue -> Color(0xFF8AB4FF) }
                        val path = androidx.compose.ui.graphics.Path()
                        for (i in 0..255 step 3) {
                            val x = left + w * i / 255f; val y = top + h * (1f - lut[i] / 255f)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.lineTo(left + w, top + h * (1f - lut[255] / 255f))
                        drawPath(path, ink, style = Stroke(2.5.dp.toPx()))
                        pts.forEach { pt -> drawCircle(ink, 8.dp.toPx(), Offset(left + w * pt.x, top + h * (1f - pt.y))) }
                    }
                    if (tool == Tool.Write) state.texts.firstOrNull { it.id == selected }?.let { t ->
                        drawCircle(Color.White, t.size * min(w, h) * 0.9f + 12.dp.toPx(), Offset(left + t.x * w, top + t.y * h), style = Stroke(1.5.dp.toPx()))
                    }
                }
            } else if (source == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
            }
          }
        }

        Column(Modifier.fillMaxWidth().heightIn(min = 150.dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            when (tool) {
                Tool.Crop -> {
                    ChipRow {
                        CropSection.entries.forEach { c -> Chip(stringResource(c.label), cropSection == c) { cropSection = c } }
                    }
                    when (cropSection) {
                    CropSection.Perspective -> {
                        val pr = state.perspective
                        ChipRow {
                            Chip(stringResource(R.string.editor_persp_sliders), !freeCorners) { freeCorners = false; if (pr.free != null) commit(state.copy(perspective = pr.copy(free = null))) }
                            Chip(stringResource(R.string.editor_persp_free), freeCorners) { freeCorners = true }
                            Chip(stringResource(R.string.editor_reset), false) { freeCorners = false; commit(state.copy(perspective = Perspective())) }
                        }
                        if (!freeCorners) {
                            ZeroSlider(stringResource(R.string.editor_persp_vertical), pr.vertical, ::remember_) { state = state.copy(perspective = pr.copy(vertical = it, free = null)) }
                            ZeroSlider(stringResource(R.string.editor_persp_horizontal), pr.horizontal, ::remember_) { state = state.copy(perspective = pr.copy(horizontal = it, free = null)) }
                        }
                        Hint(stringResource(R.string.editor_persp_tip))
                    }
                    CropSection.Expand -> {
                        val e = state.expand
                        ZeroSlider(stringResource(R.string.editor_expand_amount), e.amount, ::remember_, from = 0f) { state = state.copy(expand = e.copy(amount = it)) }
                        ChipRow {
                            listOf(ExpandFill.Continue to R.string.editor_expand_continue, ExpandFill.Blur to R.string.editor_expand_blur,
                                ExpandFill.White to R.string.editor_expand_white, ExpandFill.Black to R.string.editor_expand_black).forEach { (f, l) ->
                                Chip(stringResource(l), e.fill == f) { commit(state.copy(expand = e.copy(fill = f))) }
                            }
                        }
                        Hint(stringResource(R.string.editor_expand_tip))
                    }
                    CropSection.Crop -> {
                    Hint(stringResource(R.string.editor_crop_hint))
                    Dial(state.straighten) { state = state.copy(straighten = it) }
                    ChipRow {
                        RoundChip(EuriseIcons.rotate(), stringResource(R.string.editor_rotate)) { ratio = null; commit(state.rotated()) }
                        RoundChip(EuriseIcons.flip(), stringResource(R.string.editor_flip)) { ratio = null; commit(state.flipped()) }
                        val aspect = shown?.let { it.width.toFloat() / it.height } ?: 1f
                        Chip(stringResource(R.string.editor_free), ratio == null) { ratio = null }
                        Chip(stringResource(R.string.editor_original), ratio == aspect) { ratio = aspect; commit(state.copy(crop = ratioCrop(1f))) }
                        listOf("1:1" to 1f, "4:3" to 4f / 3f, "3:2" to 3f / 2f, "16:9" to 16f / 9f, "3:4" to 3f / 4f, "9:16" to 9f / 16f).forEach { (name, r) ->
                            Chip(name, ratio == r) { ratio = r; commit(state.copy(crop = ratioCrop(r / aspect))) }
                        }
                    }
                    }
                    }
                }
                Tool.Adjust -> {
                    val a = state.adjust
                    fun set(next: Adjust) { state = state.copy(adjust = next) }
                    ChipRow {
                        AdjustSection.entries.forEach { sct -> Chip(stringResource(sct.label), section == sct) { section = sct; pickingWhite = false } }
                        Chip(stringResource(R.string.editor_auto), false) {
                            shown?.let { b -> scope.launch { val hist = withContext(Dispatchers.Default) { histogram(b) }; commit(state.copy(adjust = a + autoAdjust(hist))) } }
                        }
                        Chip(stringResource(R.string.editor_reset), false) { commit(state.copy(adjust = Adjust(), look = Look.None, mine = null)) }
                    }
                    Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        when (section) {
                            AdjustSection.Light -> {
                                ZeroSlider(stringResource(R.string.editor_light), a.light, ::remember_) { set(a.copy(light = it)) }
                                ZeroSlider(stringResource(R.string.editor_contrast), a.contrast, ::remember_) { set(a.copy(contrast = it)) }
                                ZeroSlider(stringResource(R.string.editor_ambiance), a.ambiance, ::remember_) { set(a.copy(ambiance = it)) }
                                ZeroSlider(stringResource(R.string.editor_shadows), a.shadows, ::remember_) { set(a.copy(shadows = it)) }
                                ZeroSlider(stringResource(R.string.editor_highlights), a.highlights, ::remember_) { set(a.copy(highlights = it)) }
                                ZeroSlider(stringResource(R.string.editor_vignette), a.vignette, ::remember_) { set(a.copy(vignette = it)) }
                            }
                            AdjustSection.Color -> {
                                ZeroSlider(stringResource(R.string.editor_saturation), a.saturation, ::remember_) { set(a.copy(saturation = it)) }
                                ZeroSlider(stringResource(R.string.editor_vibrance), a.vibrance, ::remember_) { set(a.copy(vibrance = it)) }
                                ZeroSlider(stringResource(R.string.editor_warmth), a.warmth, ::remember_) { set(a.copy(warmth = it)) }
                                ZeroSlider(stringResource(R.string.editor_tint), a.tint, ::remember_) { set(a.copy(tint = it)) }
                                ChipRow { Chip(stringResource(if (pickingWhite) R.string.editor_white_tap else R.string.editor_white_pick), pickingWhite, strong = true) { pickingWhite = !pickingWhite } }
                            }
                            AdjustSection.Detail -> {
                                ZeroSlider(stringResource(R.string.editor_structure), a.structure, ::remember_) { set(a.copy(structure = it)) }
                                ZeroSlider(stringResource(R.string.editor_sharpen), a.sharpen, ::remember_, from = 0f) { set(a.copy(sharpen = it)) }
                            }
                            AdjustSection.Curves -> {
                                ChipRow {
                                    listOf(CurveChannel.Luma to R.string.editor_curve_luma, CurveChannel.Red to R.string.editor_curve_red,
                                        CurveChannel.Green to R.string.editor_curve_green, CurveChannel.Blue to R.string.editor_curve_blue).forEach { (c, l) ->
                                        Chip(stringResource(l), channel == c) { channel = c }
                                    }
                                }
                                ChipRow {
                                    listOf(CurvePreset.None to R.string.editor_curve_none, CurvePreset.SoftS to R.string.editor_curve_soft,
                                        CurvePreset.StrongS to R.string.editor_curve_strong, CurvePreset.Matte to R.string.editor_curve_matte,
                                        CurvePreset.Cross to R.string.editor_curve_cross).forEach { (pr, l) ->
                                        Chip(stringResource(l), false) { commit(state.copy(adjust = a.copy(curves = a.curves.with(channel, pr.points)))) }
                                    }
                                }
                                Hint(stringResource(R.string.editor_curve_tip))
                            }
                        }
                    }
                }
                Tool.Filters -> {
                    // Prima le modalità tue (salvate da una foto), poi i filtri della suite.
                    val newName = stringResource(R.string.editor_mine_default, myLooks.size + 1)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaveLookTile(enabled = !state.effective.isNeutral) {
                            naming = SavedLook(System.currentTimeMillis(), newName, state.effective)
                        }
                        myLooks.forEach { mine ->
                            LookTile(thumb, mine.adjust, mine.name, state.mine?.id == mine.id, onLongClick = { managing = mine }) {
                                // Una modalità tua prende il posto delle regolazioni fatte, come quando la si è salvata.
                                commit(state.copy(adjust = Adjust(), mine = mine, look = Look.None, lookStrength = 1f))
                            }
                        }
                        Look.entries.forEach { look ->
                            LookTile(thumb, look.adjust, stringResource(LookNames.getValue(look)), state.mine == null && state.look == look) {
                                commit(state.copy(look = look, mine = null, lookStrength = 1f))
                            }
                        }
                    }
                    if (state.look != Look.None || state.mine != null) ZeroSlider(stringResource(R.string.editor_intensity), state.lookStrength, ::remember_, from = 0f) { state = state.copy(lookStrength = it) }
                    else Hint(stringResource(R.string.editor_mine_tip))
                }
                Tool.Write -> {
                    val current = state.texts.firstOrNull { it.id == selected }
                    ChipRow {
                        Chip(stringResource(R.string.editor_add_text), false, strong = true) {
                            writing = TextMark(0, "", (state.crop.left + state.crop.right) / 2, (state.crop.top + state.crop.bottom) / 2, 0.09f, color.toArgb(), textStyle)
                        }
                        listOf(MarkStyle.Plain to R.string.editor_style_plain, MarkStyle.Bold to R.string.editor_style_bold, MarkStyle.Box to R.string.editor_style_box, MarkStyle.Outline to R.string.editor_style_outline).forEach { (st, label) ->
                            Chip(stringResource(label), (current?.style ?: textStyle) == st) {
                                textStyle = st
                                if (current != null) commit(state.copy(texts = state.texts.map { if (it.id == current.id) it.copy(style = st) else it }))
                            }
                        }
                        if (current != null) Chip(stringResource(R.string.editor_text_remove), false) {
                            commit(state.copy(texts = state.texts.filterNot { it.id == current.id })); selected = null
                        }
                    }
                    InkColors(current?.color?.let(::Color) ?: color) { c ->
                        color = c
                        if (current != null) commit(state.copy(texts = state.texts.map { if (it.id == current.id) it.copy(color = c.toArgb()) else it }))
                    }
                    if (current != null) ZeroSlider(stringResource(R.string.editor_text_size), (current.size - 0.03f) / 0.27f, ::remember_, from = 0f) { v ->
                        state = state.copy(texts = state.texts.map { if (it.id == current.id) it.copy(size = 0.03f + v * 0.27f) else it })
                    } else Hint(stringResource(R.string.editor_text_tip))
                }
                Tool.Draw -> {
                    ChipRow {
                        RoundChip(EuriseIcons.pencil(), stringResource(R.string.editor_nib_pen), nib == Nib.Pen) { nib = Nib.Pen }
                        RoundChip(EuriseIcons.marker(), stringResource(R.string.editor_nib_marker), nib == Nib.Marker) { nib = Nib.Marker }
                        RoundChip(EuriseIcons.highlighter(), stringResource(R.string.editor_nib_highlighter), nib == Nib.Highlighter) { nib = Nib.Highlighter }
                        RoundChip(EuriseIcons.eraser(), stringResource(R.string.editor_nib_eraser), nib == null) { nib = null }
                    }
                    InkColors(color) { color = it }
                    ZeroSlider(stringResource(R.string.editor_stroke), (penWidth - 0.004f) / 0.046f, {}, from = 0f) { penWidth = 0.004f + it * 0.046f }
                }
            }
        }

        Row(
            Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp).fillMaxWidth()
                .clip(RoundedCornerShape(26.dp)).background(Panel).border(1.dp, Raised, RoundedCornerShape(26.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tool.entries.forEach { t ->
                val on = tool == t
                Column(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(22.dp)).background(if (on) AccentVeil else Color.Transparent).clickable { tool = t },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon(if (on) 2.7f else 2.3f), null, tint = if (on) Accent else Muted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(t.label), color = if (on) Accent else Muted, fontSize = 10.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold)
                }
            }
        }
    }

    naming?.let { look ->
        var name by remember(look) { mutableStateOf(look.name) }
        val isNew = myLooks.none { it.id == look.id }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(stringResource(if (isNew) R.string.editor_mine_save else R.string.editor_mine_rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    naming = null
                    val saved = look.copy(name = name.trim())
                    storeLooks(if (isNew) listOf(saved) + myLooks else myLooks.map { if (it.id == saved.id) saved else it })
                    if (isNew) Toast.makeText(context, context.getString(R.string.editor_mine_saved, saved.name), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.editor_text_ok)) }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    managing?.let { look ->
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(look.name) },
            confirmButton = { TextButton(onClick = { managing = null; naming = look }) { Text(stringResource(R.string.editor_mine_rename)) } },
            dismissButton = {
                TextButton(onClick = {
                    managing = null
                    storeLooks(myLooks.filterNot { it.id == look.id })
                    if (state.mine?.id == look.id) commit(state.copy(mine = null))
                }) { Text(stringResource(R.string.editor_mine_delete)) }
            },
        )
    }

    writing?.let { mark ->
        var text by remember(mark) { mutableStateOf(mark.text) }
        AlertDialog(
            onDismissRequest = { writing = null },
            title = { Text(stringResource(if (mark.text.isEmpty()) R.string.editor_add_text else R.string.editor_edit_text)) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(stringResource(R.string.editor_text_hint)) }) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    writing = null
                    if (mark.id == 0L) {
                        val added = mark.copy(id = nextId++, text = text.trim())
                        commit(state.copy(texts = state.texts + added)); selected = added.id
                    } else commit(state.copy(texts = state.texts.map { if (it.id == mark.id) it.copy(text = text.trim()) else it }))
                }) { Text(stringResource(R.string.editor_text_ok)) }
            },
            dismissButton = { TextButton(onClick = { writing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun Tool.icon(stroke: Float): ImageVector = when (this) {
    Tool.Crop -> EuriseIcons.crop(stroke)
    Tool.Adjust -> EuriseIcons.sliders(stroke)
    Tool.Filters -> EuriseIcons.filters(stroke)
    Tool.Write -> EuriseIcons.text(stroke)
    Tool.Draw -> EuriseIcons.pencil(stroke)
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = if (enabled) Text1 else Color(0xFF6B717C), modifier = Modifier.size(21.dp)) }
}

@Composable
private fun Hint(text: String) = Text(text, color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
private fun Chip(label: String, on: Boolean, strong: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) Accent else if (strong) AccentVeil else Raised).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (on) OnAccent else if (strong) Accent else Text1, fontSize = 13.5.sp, fontWeight = if (on || strong) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun RoundChip(icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) AccentVeil else Raised).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (on) Accent else Text1, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun InkColors(selected: Color, onPick: (Color) -> Unit) {
    ColorRow(selected, onPick, Modifier.fillMaxWidth(), colors = INK, title = stringResource(R.string.editor_color_title), outline = Line, accent = Accent)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LookTile(thumb: ImageBitmap?, adjust: Adjust, name: String, on: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(
        Modifier.width(62.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Raised).then(if (on) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)) {
            thumb?.let {
                Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    colorFilter = if (adjust.matrixNeutral) null else ColorFilter.colorMatrix(ColorMatrix(adjustMatrix(adjust))))
            }
        }
        Text(name, color = if (on) Accent else Text1, fontSize = 11.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1)
    }
}

/** «Salva la tua»: la regolazione di questa foto diventa una modalità tua, prima tra i filtri. */
@Composable
private fun SaveLookTile(enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(62.dp).clickable(enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).border(1.5.dp, if (enabled) Accent else Muted, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.plus(), null, tint = if (enabled) Accent else Muted, modifier = Modifier.size(22.dp))
        }
        Text(stringResource(R.string.editor_mine_add), color = if (enabled) Accent else Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Le parti di Ritaglia (tavola GalProspettiva). */
private enum class CropSection(val label: Int) {
    Crop(R.string.editor_section_crop), Perspective(R.string.editor_section_perspective), Expand(R.string.editor_section_expand),
}

/** Le parti di Regola (tavola GalRegolaTutto). */
private enum class AdjustSection(val label: Int) {
    Light(R.string.editor_section_light), Color(R.string.editor_section_color), Detail(R.string.editor_section_detail), Curves(R.string.editor_section_curves),
}

/**
 * Cursore da -1 a 1 con lo zero al centro (o da 0 a 1 con [from] = 0): la parte colorata parte dallo zero.
 * Doppio tocco: torna allo zero. [onStart] salva il passo per Annulla.
 */
@Composable
private fun ZeroSlider(label: String, value: Float, onStart: () -> Unit, from: Float = -1f, onChange: (Float) -> Unit) {
    val latest by androidx.compose.runtime.rememberUpdatedState(value)
    val shown = if (from < 0) (value * 100).roundToInt().let { if (it > 0) "+$it" else if (it < 0) "−${-it}" else "0" } else "${(value * 100).roundToInt()}"
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(86.dp), maxLines = 1)
        Canvas(
            Modifier.weight(1f).height(36.dp).semantics { contentDescription = "$label $shown" }
                .pointerInput(from) {
                    detectTapGestures(onDoubleTap = { onStart(); onChange(if (from < 0) 0f else latest) })
                }
                .pointerInput(from) {
                    detectHorizontalDragGestures(onDragStart = { onStart() }) { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        onChange(from + f * (1f - from))
                    }
                },
        ) {
            val y = size.height / 2
            val track = 4.dp.toPx()
            fun x(v: Float) = (v - from) / (1f - from) * size.width
            drawLine(Line, Offset(0f, y), Offset(size.width, y), track, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val zero = x(if (from < 0) 0f else from)
            if (from < 0) drawLine(Color(0xFF6B717C), Offset(zero, y - 6.dp.toPx()), Offset(zero, y + 6.dp.toPx()), 2.dp.toPx())
            drawLine(Accent, Offset(zero, y), Offset(x(value), y), track)
            drawCircle(Color.White, 9.dp.toPx(), Offset(x(value), y))
        }
        Text(shown, color = Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** La ghiera di Raddrizza: si trascina di lato, ±30°; doppio tocco torna a 0. */
@Composable
private fun Dial(degrees: Float, onChange: (Float) -> Unit) {
    val label = stringResource(R.string.editor_straighten)
    val current by androidx.compose.runtime.rememberUpdatedState(degrees)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (degrees == 0f) "0°" else "%s%.1f°".format(if (degrees < 0) "−" else "+", abs(degrees)).replace('.', ','),
            color = Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
        )
        Canvas(
            Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = label }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onChange(0f) }) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, amount ->
                        change.consume()
                        onChange(((current - amount / size.width * 30f) * 10).roundToInt().div(10f).coerceIn(-30f, 30f))
                    }
                },
        ) {
            val step = size.width / 30f
            val shift = (degrees % 1f) * step
            for (i in -16..16) {
                val x = size.width / 2 + i * step + shift
                if (x < 0 || x > size.width) continue
                val tick = (i - degrees.toInt()) % 5 == 0
                drawLine(if (tick) Text1 else Color(0xFF6B717C), Offset(x, size.height), Offset(x, size.height - if (tick) 16.dp.toPx() else 9.dp.toPx()), 2.dp.toPx())
            }
            drawLine(Accent, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 3.dp.toPx())
        }
    }
}

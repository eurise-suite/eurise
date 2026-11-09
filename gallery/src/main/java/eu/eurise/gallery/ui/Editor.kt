package eu.eurise.gallery.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.gallery.R
import eu.eurise.gallery.data.drawMarks
import eu.eurise.gallery.data.editorPreview
import eu.eurise.gallery.data.geometry
import eu.eurise.gallery.data.saveEdited
import eu.eurise.gallery.logic.Adjust
import eu.eurise.gallery.logic.Crop
import eu.eurise.gallery.logic.EditState
import eu.eurise.gallery.logic.InkStroke
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.TextMark
import eu.eurise.gallery.logic.adjustMatrix
import eu.eurise.gallery.logic.dragCrop
import eu.eurise.gallery.logic.dragCropRatio
import eu.eurise.gallery.logic.flipped
import eu.eurise.gallery.logic.ratioCrop
import eu.eurise.gallery.logic.rotated
import eu.eurise.gallery.logic.straightenScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

private enum class Tool(val label: Int) {
    Crop(R.string.editor_tool_crop),
    Rotate(R.string.editor_tool_rotate),
    Adjust(R.string.editor_tool_adjust),
    Filters(R.string.editor_tool_filters),
    Write(R.string.editor_tool_text),
    Draw(R.string.editor_tool_draw),
}

private val INK = listOf(0xFFFFFFFF, 0xFF14171C, 0xFFD64545, 0xFFF2C14E, 0xFF4F9D69, 0xFF3F7DD6).map { it.toInt() }

/** Modifica su una copia: ritaglio, rotazione e specchio, regolazioni, filtri, scritte e disegno a mano. */
@Composable
internal fun EditorScreen(item: MediaItem, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source by produceState<Bitmap?>(null, item.uri) { value = withContext(Dispatchers.IO) { runCatching { editorPreview(context, item) }.getOrNull() } }
    var state by remember { mutableStateOf(EditState()) }
    var history by remember { mutableStateOf(emptyList<EditState>()) }
    var tool by remember { mutableStateOf(Tool.Crop) }
    var ratio by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var writing by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(INK[0]) }
    var penWidth by remember { mutableStateOf(0.012f) }
    var nextId by remember { mutableStateOf(1L) }
    val savedText = stringResource(R.string.editor_saved)
    val failedText = stringResource(R.string.action_failed)
    val touch = with(LocalDensity.current) { 36.dp.toPx() }
    val density = LocalDensity.current

    fun remember_() { history = (history + state).takeLast(30) }
    fun commit(next: EditState) { remember_(); state = next }

    val shown = remember(source, state.turns, state.flip) { source?.let { geometry(it, state.flip, state.turns) } }

    Column(Modifier.screen()) {
        ScreenBar(stringResource(R.string.edit), onClose) {
            IconButton(enabled = history.isNotEmpty(), onClick = { state = history.last(); history = history.dropLast(1) }) {
                Icon(EuriseIcons.undo(), stringResource(R.string.editor_undo), modifier = Modifier.size(21.dp))
            }
            TextButton(enabled = !busy && shown != null, onClick = {
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { try { saveEdited(context, item, state) } catch (e: Throwable) { false } }
                    Toast.makeText(context, if (ok) savedText else failedText, Toast.LENGTH_LONG).show()
                    busy = false
                    if (ok) onClose()
                }
            }) { Text(stringResource(R.string.editor_save), fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp).onSizeChanged { boxSize = it }, contentAlignment = Alignment.TopStart) {
            val bitmap = shown
            if (bitmap != null && boxSize != IntSize.Zero) {
                val fit = min(boxSize.width.toFloat() / bitmap.width, boxSize.height.toFloat() / bitmap.height)
                val w = bitmap.width * fit
                val h = bitmap.height * fit
                val left = (boxSize.width - w) / 2
                val top = (boxSize.height - h) / 2
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                val cover = straightenScale(state.straighten, w, h)
                Box(
                    Modifier.offset { IntOffset(left.toInt(), top.toInt()) }
                        .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                        .clipToBounds(),
                ) {
                    Image(
                        image, null, contentScale = ContentScale.FillBounds,
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix(adjustMatrix(state.adjust))),
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            rotationZ = state.straighten
                            scaleX = cover
                            scaleY = cover
                        },
                    )
                }
                var corner by remember { mutableStateOf(-1) }
                var movingText by remember { mutableStateOf<Long?>(null) }
                val nearestText: (Offset) -> Long? = { p ->
                    state.texts.minByOrNull { hypot(left + it.x * w - p.x, top + it.y * h - p.y) }
                        ?.takeIf { hypot(left + it.x * w - p.x, top + it.y * h - p.y) < touch * 2.5f }?.id
                }
                Canvas(
                    Modifier.fillMaxSize()
                        .pointerInput(tool, w, h) {
                            if (tool == Tool.Write) detectTapGestures { p -> selected = nearestText(p) }
                        }
                        .pointerInput(tool, w, h, ratio) {
                            when (tool) {
                                Tool.Crop -> detectDragGestures(
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
                                Tool.Draw -> detectDragGestures(
                                    onDragStart = { p ->
                                        remember_()
                                        state = state.copy(strokes = state.strokes + InkStroke(listOf((p.x - left) / w to (p.y - top) / h), penWidth, color))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val point = (change.position.x - left) / w to (change.position.y - top) / h
                                        val last = state.strokes.last()
                                        state = state.copy(strokes = state.strokes.dropLast(1) + last.copy(points = last.points + point))
                                    },
                                )
                                else -> Unit
                            }
                        },
                ) {
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.save()
                        native.clipRect(left, top, left + w, top + h)
                        native.translate(left, top)
                        drawMarks(native, w, h, state.texts, state.strokes)
                        native.restore()
                    }
                    val crop = state.crop
                    val x0 = left + crop.left * w
                    val y0 = top + crop.top * h
                    val x1 = left + crop.right * w
                    val y1 = top + crop.bottom * h
                    val shade = Color.Black.copy(alpha = 0.5f)
                    drawRect(shade, Offset(left, top), Size(w, y0 - top))
                    drawRect(shade, Offset(left, y1), Size(w, top + h - y1))
                    drawRect(shade, Offset(left, y0), Size(x0 - left, y1 - y0))
                    drawRect(shade, Offset(x1, y0), Size(left + w - x1, y1 - y0))
                    if (tool == Tool.Crop) {
                        drawRect(Color.White, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(2.dp.toPx()))
                        listOf(x0 to y0, x1 to y0, x0 to y1, x1 to y1).forEach { (x, y) -> drawCircle(Color.White, 7.dp.toPx(), Offset(x, y)) }
                    }
                    if (tool == Tool.Write) state.texts.firstOrNull { it.id == selected }?.let { t ->
                        drawCircle(Color.White, t.size * min(w, h) * 0.9f + 12.dp.toPx(), Offset(left + t.x * w, top + t.y * h), style = Stroke(1.5.dp.toPx()))
                    }
                }
            } else if (source == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(min = 112.dp), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                when (tool) {
                    Tool.Crop -> PillRow {
                        val aspect = shown?.let { it.width.toFloat() / it.height } ?: 1f
                        FilterPill(stringResource(R.string.editor_free), null, ratio == null) { ratio = null }
                        listOf("1:1" to 1f, "4:3" to 4f / 3f, "3:4" to 3f / 4f, "16:9" to 16f / 9f).forEach { (name, r) ->
                            FilterPill(name, null, ratio == r) { ratio = r; commit(state.copy(crop = ratioCrop(r / aspect))) }
                        }
                    }
                    Tool.Rotate -> {
                        PillRow {
                            FilterPill(stringResource(R.string.editor_rotate), null, false) { ratio = null; commit(state.rotated()) }
                            FilterPill(stringResource(R.string.editor_flip), null, false) { ratio = null; commit(state.flipped()) }
                        }
                        LabeledSlider(stringResource(R.string.editor_straighten), state.straighten, -15f..15f) { state = state.copy(straighten = it) }
                    }
                    Tool.Adjust -> {
                        val a = state.adjust
                        LabeledSlider(stringResource(R.string.editor_light), a.light, -1f..1f) { state = state.copy(adjust = a.copy(light = it)) }
                        LabeledSlider(stringResource(R.string.editor_contrast), a.contrast, -1f..1f) { state = state.copy(adjust = a.copy(contrast = it)) }
                        LabeledSlider(stringResource(R.string.editor_saturation), a.saturation, -1f..1f) { state = state.copy(adjust = a.copy(saturation = it)) }
                        LabeledSlider(stringResource(R.string.editor_warmth), a.warmth, -1f..1f) { state = state.copy(adjust = a.copy(warmth = it)) }
                    }
                    Tool.Filters -> PillRow {
                        listOf(
                            R.string.editor_filter_none to Adjust(),
                            R.string.editor_filter_bw to Adjust(saturation = -1f, contrast = 0.15f),
                            R.string.editor_filter_warm to Adjust(warmth = 0.6f, saturation = 0.1f),
                            R.string.editor_filter_cold to Adjust(warmth = -0.6f),
                            R.string.editor_filter_vivid to Adjust(saturation = 0.45f, contrast = 0.2f),
                        ).forEach { (label, preset) -> FilterPill(stringResource(label), null, state.adjust == preset) { commit(state.copy(adjust = preset)) } }
                    }
                    Tool.Write -> {
                        val current = state.texts.firstOrNull { it.id == selected }
                        PillRow {
                            FilterPill(stringResource(R.string.editor_add_text), null, false) { writing = true }
                            if (current != null) FilterPill(stringResource(R.string.editor_text_remove), null, false) {
                                commit(state.copy(texts = state.texts.filterNot { it.id == current.id }))
                                selected = null
                            }
                        }
                        Swatches(current?.color ?: color) { c ->
                            color = c
                            if (current != null) commit(state.copy(texts = state.texts.map { if (it.id == current.id) it.copy(color = c) else it }))
                        }
                        if (current != null) LabeledSlider(stringResource(R.string.editor_text_size), current.size, 0.03f..0.3f) { v ->
                            state = state.copy(texts = state.texts.map { if (it.id == current.id) it.copy(size = v) else it })
                        } else Text(stringResource(R.string.editor_text_tip), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Tool.Draw -> {
                        Swatches(color) { color = it }
                        LabeledSlider(stringResource(R.string.editor_stroke), penWidth, 0.004f..0.05f) { penWidth = it }
                    }
                }
            }
            PillRow {
                Tool.entries.forEach { t -> FilterPill(stringResource(t.label), null, tool == t) { tool = t } }
                FilterPill(stringResource(R.string.editor_reset), null, false) { commit(EditState()); ratio = null; selected = null }
            }
        }
    }
    if (writing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { writing = false },
            title = { Text(stringResource(R.string.editor_add_text)) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(stringResource(R.string.editor_text_hint)) }) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    writing = false
                    val mark = TextMark(nextId++, text.trim(), (state.crop.left + state.crop.right) / 2, (state.crop.top + state.crop.bottom) / 2, 0.09f, color)
                    commit(state.copy(texts = state.texts + mark))
                    selected = mark.id
                }) { Text(stringResource(R.string.editor_text_ok)) }
            },
            dismissButton = { TextButton(onClick = { writing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PillRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
private fun Swatches(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        INK.forEachIndexed { i, c ->
            val description = stringResource(R.string.editor_color, i + 1)
            IconButton(onClick = { onPick(c) }) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == selected) 3.dp else 1.dp, if (c == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                )
                Text(description, fontSize = 0.sp)
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.28f))
        Slider(value, onChange, valueRange = range, modifier = Modifier.weight(0.72f))
    }
}

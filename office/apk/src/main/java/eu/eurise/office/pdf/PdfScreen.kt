package eu.eurise.office.pdf

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.suite.HubBridge
import eu.eurise.office.BuildConfig
import eu.eurise.office.R
import eu.eurise.office.data.DocKind
import eu.eurise.office.data.Engine
import kotlinx.coroutines.launch

private val SuiteInk = Color(0xFF7A3440)
private val SuiteStar = Color(0xFFB8923F)

/**
 * Editor PDF (tavole OffPdf, OffPdfPagine, OffPdfCondividi). In alto nome, stato del salvataggio, cerca e Condividi; al
 * centro le pagine (due dita per ingrandire); in basso la barra Annota / Modifica / Pagine / Suite / Altro, con sopra gli
 * strumenti della scheda aperta, vicino al pollice.
 */
@Composable
fun PdfScreen(model: PdfModel, writable: Boolean, onBack: () -> Unit) {
    val state by model.session.current.collectAsState()
    val context = LocalContext.current

    if (model.showPages) {
        PdfPagesScreen(model, onClose = { model.showPages = false })
        return
    }
    if (model.showShare) {
        PdfShareScreen(model, onClose = { model.showShare = false })
        return
    }
    BackHandler(enabled = model.tool != Tool.None || model.searching) {
        if (model.searching) model.searching = false else model.tool = Tool.None
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.fillMaxSize()) {
            Header(model, state, onBack)
            if (model.searching) SearchBar(model)
            when {
                state.needsPassword -> PasswordPrompt(onPassword = { model.session.open(it) }, onCancel = onBack)
                state.error && state.pages.isEmpty() -> Message(stringResource(R.string.pdf_cannot_open))
                state.pages.isEmpty() -> Message(stringResource(R.string.pdf_loading))
                else -> Pages(model, state, Modifier.weight(1f))
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
            if (state.hasForm && model.tab == PdfTab.Annotate && model.tool == Tool.None) {
                Hint(stringResource(R.string.pdf_form_hint))
            }
            if (!writable && state.pages.isNotEmpty()) Hint(stringResource(R.string.pdf_read_only_hint))
            ToolPanel(model, state)
            BottomTabs(model)
        }
    }

    if (model.showForms) PdfFormsSheet(model, onClose = { model.showForms = false })
    if (model.showPad) {
        SignaturePadDialog(
            onDismiss = { model.showPad = false },
            onSaved = { model.showPad = false; model.tool = Tool.Sign; Toast.makeText(context, R.string.pdf_sign_place, Toast.LENGTH_LONG).show() },
        )
    }
}

@Composable
private fun Header(model: PdfModel, state: PdfState, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(EuriseIcons.chevronLeft(), contentDescription = stringResource(R.string.pdf_back)) }
        Column(Modifier.weight(1f)) {
            Text(state.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when (state.save) {
                SaveState.Saved -> stringResource(R.string.pdf_saved, state.where)
                SaveState.Saving -> stringResource(R.string.pdf_saving)
                SaveState.Pending -> stringResource(R.string.pdf_pending)
                SaveState.ReadOnly -> stringResource(R.string.pdf_read_only, state.where)
                SaveState.Protected -> stringResource(R.string.pdf_protected)
                SaveState.Failed -> stringResource(R.string.pdf_save_failed)
            }
            val ok = state.save == SaveState.Saved || state.save == SaveState.Saving || state.save == SaveState.Pending
            Text(
                status, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (ok) Color(0xFF3F5E4B) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { model.searching = !model.searching }) {
            Icon(EuriseIcons.search(), contentDescription = stringResource(R.string.pdf_search))
        }
        Box(
            Modifier.height(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                .clickable { model.showShare = true }.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.pdf_share), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun SearchBar(model: PdfModel) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (model.query.isEmpty()) Text(stringResource(R.string.pdf_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            BasicTextField(
                value = model.query,
                onValueChange = { model.query = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { model.search() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val count = model.hits.size
        Text(if (count == 0) "0" else "${model.hit + 1}/$count", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = { if (count > 0) model.hit = (model.hit - 1 + count) % count }) { Icon(EuriseIcons.chevronLeft(), stringResource(R.string.pdf_search_prev)) }
        IconButton(onClick = { if (count > 0) model.hit = (model.hit + 1) % count else model.search() }) { Icon(EuriseIcons.chevronRight(), stringResource(R.string.pdf_search_next)) }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEAF1EC)).padding(horizontal = 12.dp, vertical = 9.dp),
        fontSize = 12.sp, color = Color(0xFF2B4A37),
    )
}

@Composable
private fun PasswordPrompt(onPassword: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.pdf_password_title)) },
        text = {
            OutlinedTextField(
                value = password, onValueChange = { password = it }, singleLine = true,
                label = { Text(stringResource(R.string.pdf_password_label)) },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            )
        },
        confirmButton = { TextButton(onClick = { onPassword(password) }) { Text(stringResource(R.string.pdf_open)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.pdf_cancel)) } },
    )
}

/** Le pagine, una sotto l'altra. Due dita ingrandiscono; da ingrandite si scorre anche di lato. */
@Composable
private fun Pages(model: PdfModel, state: PdfState, modifier: Modifier) {
    var zoom by remember { mutableFloatStateOf(1f) }
    val list = rememberLazyListState()
    val sideScroll = rememberScrollState()
    LaunchedEffect(model.hit, model.hits) {
        model.hits.getOrNull(model.hit)?.let { (page, _) -> list.animateScrollToItem(page) }
    }
    BoxWithConstraints(
        modifier.fillMaxWidth().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
    ) {
        val pageWidth = maxWidth * zoom
        Box(Modifier.fillMaxSize().horizontalScroll(sideScroll, enabled = zoom > 1.01f)) {
            LazyColumn(
                state = list,
                modifier = Modifier.width(pageWidth).fillMaxHeight(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 220.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.pages.size, key = { "${state.version}-$it" }) { index ->
                    PageView(model, index, state.pages[index], state.version, zoom)
                }
            }
        }
    }
}

/** Una pagina con sopra quello che si sta facendo (selezione, tratto della penna, risultati della ricerca). */
@Composable
private fun PageView(model: PdfModel, index: Int, geometry: PageGeometry, version: Int, zoom: Float) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val step = when { zoom < 1.4f -> 1f; zoom < 2.2f -> 2f; else -> 3f }
    val bitmap by produceState<android.graphics.Bitmap?>(null, version, size.width, step) {
        if (size.width > 0) value = model.session.render(index, (size.width * step / zoom).toInt().coerceIn(200, 2600))
    }
    val text by produceState<PageText?>(null, version, model.tool) {
        if (model.tool == Tool.Highlight || model.searching) value = model.session.text(index)
    }
    val marks by produceState(emptyList<Mark>(), version) { value = model.session.marks(index) }
    val stroke = remember { mutableStateListOf<Offset>() }
    var selection by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var drag by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var askAt by remember { mutableStateOf<ViewPoint?>(null) }
    var openMark by remember { mutableStateOf<Mark?>(null) }

    fun point(o: Offset) = ViewPoint((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
    fun pdfPerPx() = geometry.pdfPerPixel(size.width.toFloat())

    val gestures = Modifier.pointerInput(model.tool, version, text) {
        when (model.tool) {
            Tool.Highlight -> detectDragGestures(
                onDragStart = { o ->
                    val (x, y) = geometry.toPdf(point(o))
                    text?.nearest(x, y)?.let { selection = it to it }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val (x, y) = geometry.toPdf(point(change.position))
                    val end = text?.nearest(x, y)
                    if (end != null) selection = (selection?.first ?: end) to end
                },
                onDragEnd = {
                    val sel = selection
                    val t = text
                    selection = null
                    if (sel != null && t != null) {
                        val lines = t.lineBoxes(sel.first, sel.second)
                        model.selectedText = t.glyphs.subList(minOf(sel.first, sel.second), maxOf(sel.first, sel.second) + 1).joinToString("") { it.text }.trim()
                        model.session.edit { d -> PdfEdits.highlight(d, d.getPage(index), lines, model.color, model.underline) }
                    }
                },
                onDragCancel = { selection = null },
            )
            Tool.Pen -> detectDragGestures(
                onDragStart = { o -> stroke.clear(); stroke.add(o) },
                onDrag = { change, _ -> change.consume(); stroke.add(change.position) },
                onDragEnd = {
                    val points = stroke.map { geometry.toPdf(point(it)) }
                    stroke.clear()
                    if (points.size > 1) model.session.edit { d -> PdfEdits.ink(d, d.getPage(index), listOf(points), model.penColor, 1.6f) }
                },
                onDragCancel = { stroke.clear() },
            )
            Tool.Cover -> detectDragGestures(
                onDragStart = { o -> drag = o to o },
                onDrag = { change, _ -> change.consume(); drag = drag?.copy(second = change.position) },
                onDragEnd = {
                    val d0 = drag
                    drag = null
                    if (d0 != null) {
                        val (x1, y1) = geometry.toPdf(point(d0.first))
                        val (x2, y2) = geometry.toPdf(point(d0.second))
                        val r = PdfRect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
                        if (r.width > 2f && r.height > 2f) model.session.edit { d -> PdfEdits.cover(d, d.getPage(index), r, white = true) }
                    }
                },
                onDragCancel = { drag = null },
            )
            Tool.Note, Tool.Text -> detectTapGestures(onTap = { o -> askAt = point(o) })
            Tool.Sign -> detectTapGestures(onTap = { o ->
                val saved = Signature.load(context)
                if (saved == null) {
                    model.showPad = true
                } else {
                    val (x, y) = geometry.toPdf(point(o))
                    val width = 150f
                    val placed = saved.place(x - width / 2f, y, width)
                    model.session.edit { d -> PdfEdits.ink(d, d.getPage(index), placed, InkColor.Notte, 1.4f, context.getString(R.string.pdf_signature_contents)) }
                    model.tool = Tool.None
                }
            })
            Tool.Image -> detectTapGestures(onTap = { o ->
                val image = model.pendingImage ?: return@detectTapGestures
                val (x, y) = geometry.toPdf(point(o))
                model.session.edit { d -> PdfTools.drawImage(d, d.getPage(index), image, x, y, 180f) }
                model.pendingImage = null
                model.tool = Tool.None
            })
            Tool.None -> detectTapGestures(onTap = { o ->
                val (x, y) = geometry.toPdf(point(o))
                val slop = 10f * pdfPerPx() * density.density
                openMark = marks.lastOrNull { m ->
                    PdfRect(m.rect.left - slop, m.rect.bottom - slop, m.rect.right + slop, m.rect.top + slop).contains(x, y)
                }
            })
        }
    }

    Box(
        Modifier.fillMaxWidth().aspectRatio(geometry.displayAspect).shadow(2.dp).background(Color.White)
            .onSizeChanged { size = it }.then(gestures)
            .semantics { contentDescription = context.getString(R.string.pdf_page_n, index + 1) },
    ) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        Canvas(Modifier.fillMaxSize()) {
            fun box(r: PdfRect): Pair<Offset, Size> {
                val v = geometry.toViewRect(r)
                return Offset(v[0] * size.width, v[1] * size.height) to Size((v[2] - v[0]) * size.width, (v[3] - v[1]) * size.height)
            }
            model.hits.forEachIndexed { i, (page, r) ->
                if (page == index) {
                    val (o, s) = box(r)
                    drawRect(if (i == model.hit) Color(0x99F2C14E) else Color(0x55F2C14E), o, s)
                }
            }
            val t = text
            val sel = selection
            if (t != null && sel != null) {
                t.lineBoxes(sel.first, sel.second).forEach { r ->
                    val (o, s) = box(r)
                    drawRect(Color(model.color.r, model.color.g, model.color.b, 0.55f), o, s)
                }
            }
            if (stroke.size > 1) {
                val path = Path().apply { moveTo(stroke[0].x, stroke[0].y); stroke.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, Color(model.penColor.r, model.penColor.g, model.penColor.b), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drag?.let { (a, b) ->
                drawRect(Color.White, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)))
                drawRect(Color(0xFF3E5C8A), Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = Stroke(1.dp.toPx()))
            }
        }
    }

    askAt?.let { at ->
        TextAsk(
            title = stringResource(if (model.tool == Tool.Note) R.string.pdf_note_title else R.string.pdf_text_title),
            onDismiss = { askAt = null },
            onDone = { value ->
                askAt = null
                val (x, y) = geometry.toPdf(at)
                if (model.tool == Tool.Note) model.session.edit { d -> PdfEdits.note(d, d.getPage(index), x, y, value, model.color) }
                else model.session.edit { d -> PdfEdits.freeText(d, d.getPage(index), x, y, value, 11f, model.penColor) }
            },
        )
    }
    openMark?.let { mark ->
        MarkDialog(
            mark = mark,
            onDismiss = { openMark = null },
            onDelete = { openMark = null; model.session.edit { d -> PdfEdits.remove(d.getPage(index), mark.index) } },
            onSave = { text -> openMark = null; model.session.edit { d -> PdfEdits.setContents(d.getPage(index), mark.index, text) } },
        )
    }
}

@Composable
private fun TextAsk(title: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, minLines = 2) },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onDone(value) }) { Text(stringResource(R.string.pdf_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pdf_cancel)) } },
    )
}

@Composable
private fun MarkDialog(mark: Mark, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (String) -> Unit) {
    val editable = mark.subtype == "Text" || mark.subtype == "FreeText"
    var value by remember { mutableStateOf(mark.contents.orEmpty()) }
    val kind = when (mark.subtype) {
        "Highlight" -> R.string.pdf_mark_highlight
        "Underline" -> R.string.pdf_mark_underline
        "Ink" -> R.string.pdf_mark_ink
        "Text" -> R.string.pdf_mark_note
        "FreeText" -> R.string.pdf_mark_text
        "Square" -> R.string.pdf_mark_cover
        else -> R.string.pdf_mark_other
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(kind)) },
        text = {
            if (editable) OutlinedTextField(value = value, onValueChange = { value = it }, minLines = 2)
            else if (!mark.contents.isNullOrBlank()) Text(mark.contents)
        },
        confirmButton = {
            if (editable) TextButton(onClick = { onSave(value) }) { Text(stringResource(R.string.pdf_save)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.pdf_close)) }
        },
        dismissButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.pdf_delete), color = Color(0xFF8C2F39)) } },
    )
}

/** Gli strumenti della scheda aperta, sopra la barra (tavola OffPdf: Annota con colori e «Sottolinea»). */
@Composable
private fun ToolPanel(model: PdfModel, state: PdfState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodeScaled(context, uri, 1600) }
            if (bitmap != null) {
                model.pendingImage = bitmap
                model.tool = Tool.Image
                Toast.makeText(context, R.string.pdf_image_place, Toast.LENGTH_LONG).show()
            }
        }
    }
    val panel = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()
        .shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)).padding(horizontal = 8.dp, vertical = 8.dp)
    when (model.tab) {
        PdfTab.Annotate -> Column(panel, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ToolKey(R.string.pdf_tool_highlight, { EuriseIcons.highlighter(it) }, model.tool == Tool.Highlight) { model.toggle(Tool.Highlight) }
                ToolKey(R.string.pdf_tool_note, { EuriseIcons.chat(it) }, model.tool == Tool.Note) { model.toggle(Tool.Note) }
                ToolKey(R.string.pdf_tool_pen, { EuriseIcons.pen(it) }, model.tool == Tool.Pen) { model.toggle(Tool.Pen) }
                ToolKey(R.string.pdf_tool_text, { EuriseIcons.text(it) }, model.tool == Tool.Text) { model.toggle(Tool.Text) }
                ToolKey(R.string.pdf_tool_sign, { EuriseIcons.pencil(it) }, model.tool == Tool.Sign) {
                    if (Signature.load(context) == null) model.showPad = true else model.toggle(Tool.Sign)
                }
                ToolKey(R.string.pdf_tool_forms, { EuriseIcons.checklist(it) }, false) {
                    if (state.hasForm) model.showForms = true else Toast.makeText(context, R.string.pdf_no_form, Toast.LENGTH_LONG).show()
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(InkColor.Sabbia to R.string.pdf_color_sand, InkColor.Azzurro to R.string.pdf_color_blue, InkColor.Salvia to R.string.pdf_color_sage, InkColor.Rosa to R.string.pdf_color_rose)
                    .forEach { (c, label) ->
                        val chosen = model.color == c
                        val name = stringResource(label)
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(Color(c.r, c.g, c.b))
                                .then(if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { model.color = c }
                                .semantics { contentDescription = name; role = Role.RadioButton; selected = chosen },
                        )
                    }
                Spacer(Modifier.weight(1f))
                if (state.canUndo) Chip(stringResource(R.string.pdf_undo), false) { model.session.undo() }
                Chip(stringResource(R.string.pdf_underline), model.underline) { model.underline = !model.underline }
            }
        }
        PdfTab.Edit -> Row(panel, horizontalArrangement = Arrangement.SpaceBetween) {
            ToolKey(R.string.pdf_tool_text, { EuriseIcons.text(it) }, model.tool == Tool.Text) { model.toggle(Tool.Text) }
            ToolKey(R.string.pdf_tool_cover, { EuriseIcons.eraser(it) }, model.tool == Tool.Cover) { model.toggle(Tool.Cover) }
            ToolKey(R.string.pdf_tool_image, { EuriseIcons.image(it) }, model.tool == Tool.Image) { pickImage.launch("image/*") }
            ToolKey(R.string.pdf_tool_engine, { EuriseIcons.office(it) }, false) {
                if (Engine.available(context)) {
                    Engine.open(context, model.uri, "application/vnd.oasis.opendocument.graphics", DocKind.Drawing, false)
                } else {
                    Toast.makeText(context, R.string.pdf_engine_needed, Toast.LENGTH_LONG).show()
                }
            }
            if (state.canUndo) ToolKey(R.string.pdf_undo, { EuriseIcons.undo(it) }, false) { model.session.undo() }
        }
        PdfTab.Suite -> Row(panel.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pdf_suite).uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = SuiteInk)
            val subject = model.selectedText.ifBlank { state.name }
            Chip(stringResource(R.string.pdf_suite_task), false) { toHub(context, HubBridge.ACTION_TASK, subject) }
            Chip(stringResource(R.string.pdf_suite_note), false) { toHub(context, HubBridge.ACTION_NOTE, subject) }
            Chip(stringResource(R.string.pdf_suite_event), false) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, subject))
                }
            }
        }
        PdfTab.More -> Row(panel, horizontalArrangement = Arrangement.SpaceBetween) {
            ToolKey(R.string.pdf_more_save_in, { EuriseIcons.folder(it) }, false) { PdfOut.saveIn(context, model) }
            ToolKey(R.string.pdf_more_print, { EuriseIcons.printer(it) }, false) { PdfOut.print(context, model) }
            ToolKey(R.string.pdf_more_open_with, { EuriseIcons.external(it) }, false) { PdfOut.openWith(context, model) }
            ToolKey(R.string.pdf_more_sign_again, { EuriseIcons.pencil(it) }, false) { model.showPad = true }
        }
        PdfTab.Pages -> Unit
    }
}

private fun PdfModel.toggle(t: Tool) {
    tool = if (tool == t) Tool.None else t
}

private fun toHub(context: android.content.Context, action: String, text: String) {
    val permission = if (BuildConfig.DEBUG) "eu.eurise.permission.SUITE_DEBUG" else "eu.eurise.permission.SUITE"
    val sent = HubBridge.send(context, BuildConfig.HUB_PACKAGE, permission, action, text)
    Toast.makeText(context, if (sent) R.string.pdf_suite_sent else R.string.office_needs_eurise, Toast.LENGTH_SHORT).show()
}

@Composable
private fun ToolKey(label: Int, icon: (Float) -> androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    val ink = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.size(width = 54.dp, height = 52.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).semantics { selected = active },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon(if (active) EuriseIcons.StrokeSelected else EuriseIcons.Stroke), contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        Text(stringResource(label), fontSize = 10.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold, color = ink, maxLines = 1)
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp).clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1)
    }
}

/** La barra in basso (tavola OffPdf): la scheda attiva è piena, «Suite» ha la stellina. */
@Composable
private fun BottomTabs(model: PdfModel) {
    Row(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp).fillMaxWidth().height(60.dp)
            .shadow(10.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(
            PdfTab.Annotate to R.string.pdf_tab_annotate, PdfTab.Edit to R.string.pdf_tab_edit, PdfTab.Pages to R.string.pdf_tab_pages,
            PdfTab.Suite to R.string.pdf_tab_suite, PdfTab.More to R.string.pdf_tab_more,
        ).forEach { (tab, label) ->
            val active = model.tab == tab
            Row(
                Modifier.weight(1f).height(46.dp).clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable {
                        model.tool = Tool.None
                        if (tab == PdfTab.Pages) model.showPages = true else model.tab = tab
                    }
                    .semantics { role = Role.Tab; selected = active },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab == PdfTab.Suite) {
                    Icon(EuriseIcons.spark(), contentDescription = null, tint = if (active) MaterialTheme.colorScheme.onPrimary else SuiteStar, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                }
                val ink = when {
                    active -> MaterialTheme.colorScheme.onPrimary
                    tab == PdfTab.Suite -> SuiteInk
                    tab == PdfTab.More -> Color(0xFF3E5C8A)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(stringResource(label), fontSize = 12.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold, color = ink, maxLines = 1)
            }
        }
    }
}

/** Una foto ridotta al lato più lungo [maxSide], senza caricarla intera in memoria. */
fun decodeScaled(context: android.content.Context, uri: Uri, maxSide: Int): android.graphics.Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val side = maxOf(decoded.width, decoded.height)
    if (side <= maxSide) decoded else {
        val k = maxSide.toFloat() / side
        android.graphics.Bitmap.createScaledBitmap(decoded, (decoded.width * k).toInt(), (decoded.height * k).toInt(), true)
    }
}.getOrNull()

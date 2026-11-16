package eu.eurise.feature.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.docs.DocReader
import eu.eurise.core.data.docs.DocReading
import eu.eurise.core.data.docs.ReadClient
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.files.EuriseFiles
import eu.eurise.core.files.SaveInSheet
import eu.eurise.core.files.SaveRequest
import eu.eurise.core.files.Spot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.hypot

/** Gli aspetti nell'ordine delle tavole. */
private val aspects = listOf(
    ScanMode.Photo to R.string.ed_photo,
    ScanMode.Document to R.string.ed_perfect,
    ScanMode.Gray to R.string.ed_gray,
    ScanMode.BlackWhite to R.string.ed_bw,
    ScanMode.Color to R.string.ed_color,
)

/**
 * L'editor dopo le foto (tavole ScanPagine, ScanPagina, ScanRitaglio): le pagine una a una con il prima e dopo, oppure
 * tutte insieme con un aspetto per tutte; «Già sistemato» con ciò che è stato fatto davvero; ritaglio preciso; «Avanti»
 * apre «Salva in…» di Eurise File con il testo letto e la cartella proposta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ScanEditor(
    pages: MutableList<File>, origins: MutableMap<File, Origin>, dir: File, onAdd: () -> Unit, onCancel: () -> Unit, onSaved: () -> Unit,
    onReturn: ((File) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf(pages.size > 1) }
    var current by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf<File>().also { it.addAll(pages) } }
    var working by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<File?>(null) }
    var menuFor by remember { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    var docRead by remember { mutableStateOf<DocRead>(if (DocReading.enabled(context)) DocRead.Idle else DocRead.Off) }
    var askReading by remember { mutableStateOf(false) }
    var withText by remember { mutableStateOf(true) }
    val pageKey = pages.toList()

    if (current > pages.lastIndex) current = pages.lastIndex.coerceAtLeast(0)
    BackHandler(enabled = cropping == null && !saving) { onAdd() }

    fun readText() {
        docRead = DocRead.Reading
        val snapshot = pages.toList()
        scope.launch {
            docRead = when (val r = ReadClient.read(context, snapshot)) {
                is ReadClient.Result.Pages -> DocRead.Done(r.pages, DocReader.analyze(r.pages.joinToString("\n\n") { it.text }))
                else -> DocRead.Problem(r)
            }
        }
    }
    LaunchedEffect(pageKey) {
        selected.retainAll(pages.toSet())
        if (pageKey.isNotEmpty() && DocReading.enabled(context) && DocReading.auto(context)) readText()
        else if (docRead is DocRead.Done) docRead = DocRead.Idle
    }

    /** Rifà le pagine [files] con il nuovo [change], dalla foto originale (niente perdita di qualità). */
    fun redo(files: List<File>, change: (Origin) -> Origin) {
        working = true
        scope.launch {
            for (file in files) {
                val origin = origins[file] ?: continue
                val updated = change(origin)
                val fresh = withContext(Dispatchers.Default) {
                    runCatching { BitmapFactory.decodeFile(origin.upright.path)?.let { render(it, updated, dir) } }.getOrNull()
                } ?: continue
                val index = pages.indexOf(file)
                if (index < 0) continue
                origins.remove(file)
                origins[fresh] = updated
                pages[index] = fresh
                val s = selected.indexOf(file)
                if (s >= 0) selected[s] = fresh
                file.delete()
            }
            working = false
        }
    }

    val targets: List<File> = if (all) selected.toList() else listOfNotNull(pages.getOrNull(current))

    cropping?.let { file ->
        val origin = origins[file]
        if (origin == null) { cropping = null; return@let }
        CropScreen(origin, pages.indexOf(file) + 1, onCancel = { cropping = null }) { quad ->
            cropping = null
            redo(listOf(file)) { it.copy(quad = quad) }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        // Testata: Indietro, titolo, Avanti; sotto «Una a una / Tutte insieme».
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onAdd) { Text(stringResource(R.string.ed_back)) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (!all && pages.size > 1) PagerArrow(EuriseIcons.chevronLeft(), enabled = current > 0) { current-- }
                Text(
                    if (all || pages.size == 1) stringResource(R.string.ed_pages, pages.size) else stringResource(R.string.ed_page_of, current + 1, pages.size),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                )
                if (!all && pages.size > 1) PagerArrow(EuriseIcons.chevronRight(), enabled = current < pages.lastIndex) { current++ }
            }
            if (onReturn != null) {
                // Chiamato da un'altra app (Office): le pagine tornano lì come PDF, senza «Salva in…».
                Button(enabled = pages.isNotEmpty() && !working, onClick = {
                    working = true
                    scope.launch {
                        val pdf = withContext(Dispatchers.IO) { File(dir, "pagine.pdf").also { PageProcessor.writePdf(pages.toList(), it) } }
                        onReturn(pdf)
                    }
                }) { Text(stringResource(R.string.ed_return)) }
            } else {
                Button(enabled = pages.isNotEmpty() && !working, onClick = { saving = true }) { Text(stringResource(R.string.ed_next)) }
            }
        }
        if (pages.size > 1) Row(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surfaceContainerHigh).padding(3.dp),
        ) {
            listOf(false to R.string.ed_one, true to R.string.ed_all).forEach { (value, label) ->
                val on = all == value
                Box(
                    Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(11.dp)).background(if (on) scheme.surface else Color.Transparent)
                        .clickable(role = Role.Tab) { all = value }.semantics { this.selected = on },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(label), fontSize = 13.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) scheme.primary else scheme.onSurfaceVariant) }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (all && pages.size > 1) {
                AllPages(pages, selected, onToggle = { f -> if (f in selected) selected.remove(f) else selected.add(f) }, onMenu = { menuFor = it })
                Text(stringResource(R.string.ed_select_hint), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                Fixed(pages, origins, onUndoEdges = { f -> redo(listOf(f)) { it.copy(quad = null) } }, onRedoShot = { f ->
                    pages.remove(f); origins.remove(f); selected.remove(f); onAdd()
                })
            } else pages.getOrNull(current)?.let { file ->
                origins[file]?.let { origin -> Compare(file, origin, working) }
            }
            AspectPicker(
                title = if (all && pages.size > 1) stringResource(R.string.ed_aspect_for, targets.size) else stringResource(R.string.ed_aspect_this),
                mode = targets.firstOrNull()?.let { origins[it]?.mode } ?: ScanMode.Document,
                strength = targets.firstOrNull()?.let { origins[it]?.strength } ?: 1f,
                enabled = targets.isNotEmpty() && !working,
                onMode = { m -> redo(targets) { it.copy(mode = m) } },
                onStrength = { v -> redo(targets) { it.copy(strength = v) } },
            )
            DocTextPart(docRead, onRead = ::readText, onTurnOn = { askReading = true })
            Text(stringResource(R.string.scan_local_note), fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
        }

        // Strumenti: si applicano alle pagine scelte (tutte insieme) o a quella aperta (una a una).
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp))
                .background(scheme.surfaceContainerLow).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tool(EuriseIcons.crop(), stringResource(R.string.ed_crop), enabled = targets.isNotEmpty() && !working) { cropping = targets.first() }
            Tool(EuriseIcons.rotate(), stringResource(R.string.ed_rotate), enabled = targets.isNotEmpty() && !working) { redo(targets) { it.copy(turns = it.turns + 1) } }
            Tool(EuriseIcons.plus(), stringResource(R.string.ed_add), enabled = !working) { onAdd() }
            Tool(EuriseIcons.trash(), stringResource(R.string.ed_delete), enabled = targets.isNotEmpty() && !working) {
                targets.forEach { f -> pages.remove(f); origins.remove(f); selected.remove(f) }
                if (pages.isEmpty()) onAdd()
            }
        }
    }

    menuFor?.let { file ->
        val index = pages.indexOf(file)
        DropdownMenu(expanded = true, onDismissRequest = { menuFor = null }) {
            if (index > 0) DropdownMenuItem(text = { Text(stringResource(R.string.ed_move_before)) }, onClick = { menuFor = null; pages.removeAt(index); pages.add(index - 1, file) })
            if (index in 0 until pages.lastIndex) DropdownMenuItem(text = { Text(stringResource(R.string.ed_move_after)) }, onClick = { menuFor = null; pages.removeAt(index); pages.add(index + 1, file) })
            DropdownMenuItem(text = { Text(stringResource(R.string.ed_crop)) }, onClick = { menuFor = null; cropping = file })
            DropdownMenuItem(text = { Text(stringResource(R.string.ed_delete)) }, onClick = {
                menuFor = null; pages.remove(file); origins.remove(file); selected.remove(file); if (pages.isEmpty()) onAdd()
            })
        }
    }

    if (askReading) DocReadingSheet(onClose = { askReading = false }) {
        askReading = false
        if (DocReading.auto(context)) readText() else docRead = DocRead.Idle
    }

    if (saving) {
        val done = (docRead as? DocRead.Done)?.takeIf { it.pages.size == pages.size }
        val fallback = stringResource(R.string.scan_default_name)
        val name = done?.facts?.fileName?.removeSuffix(".pdf") ?: "${LocalDate.now()}_$fallback"
        val folder = done?.facts?.folder
        val request = remember(pages.toList(), done) {
            SaveRequest(
                kind = "scan",
                from = context.getString(R.string.ed_from),
                name = name,
                extension = "pdf",
                mime = "application/pdf",
                produce = {
                    withContext(Dispatchers.IO) {
                        val pdf = File(dir, "out.pdf")
                        val searchable = withText && done != null && PageProcessor.writeSearchablePdf(pages.toList(), done.pages, pdf, name)
                        if (!searchable) PageProcessor.writePdf(pages.toList(), pdf)
                        pdf
                    }
                },
                hint = folder?.let { Spot(null, EuriseFiles.PHONE_HOME + "/" + it) },
                hintReason = folder?.let { context.getString(R.string.doc_folder_hint_reason) },
            )
        }
        SaveInSheet(request, onSaved = { saving = false; onSaved() }, onDismiss = { saving = false }) {
            if (done != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterPill(stringResource(R.string.ed_format_text), dot = null, selected = withText) { withText = true }
                FilterPill(stringResource(R.string.ed_format_light), dot = null, selected = !withText) { withText = false }
            }
        }
    }
}

@Composable
private fun PagerArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (enabled) scheme.onSurfaceVariant else scheme.outlineVariant, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) scheme.onSurfaceVariant else scheme.outlineVariant)
    }
}

@Composable
private fun pageBitmap(file: File, sample: Int): Bitmap? {
    val b by produceState<Bitmap?>(null, file, sample) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }
    return b
}

/** Tutte le pagine in griglia: tocco = scegli, pressione lunga = sposta, ritaglia, togli. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllPages(pages: List<File>, selected: List<File>, onToggle: (File) -> Unit, onMenu: (File) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        pages.chunked(3).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                chunk.forEachIndexed { col, file ->
                    val n = row * 3 + col + 1
                    val on = file in selected
                    val label = stringResource(R.string.ed_page_label, n) + if (on) ", " + stringResource(R.string.ed_selected) else ""
                    Box(
                        Modifier.weight(1f).aspectRatio(0.72f).clip(RoundedCornerShape(6.dp))
                            .border(if (on) 3.dp else 1.dp, if (on) scheme.primary else scheme.outlineVariant, RoundedCornerShape(6.dp))
                            .combinedClickable(onClick = { onToggle(file) }, onLongClick = { onMenu(file) })
                            .semantics { contentDescription = label },
                    ) {
                        pageBitmap(file, 8)?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize()) }
                        if (on) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                            Icon(EuriseIcons.check(3.2f), null, tint = scheme.onPrimary, modifier = Modifier.size(12.dp))
                        }
                        Text("$n", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x9E1E1D1B)).padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** «Già sistemato»: solo ciò che è stato fatto davvero, ognuno con il suo Annulla; le pagine mosse con Rifai. */
@Composable
private fun Fixed(pages: List<File>, origins: Map<File, Origin>, onUndoEdges: (File) -> Unit, onRedoShot: (File) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rows = buildList {
        pages.forEachIndexed { i, f ->
            val o = origins[f] ?: return@forEachIndexed
            if (o.blurry) add(Triple(f, false, i + 1))
            if (o.quad != null && o.quad == o.found) add(Triple(f, true, i + 1))
        }
    }
    val anyClean = pages.any { f -> origins[f]?.mode.let { it == ScanMode.Document || it == ScanMode.Gray || it == ScanMode.Color } }
    if (rows.isEmpty() && !anyClean) return
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
        Text(stringResource(R.string.ed_fixed).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        rows.forEachIndexed { i, (file, edges, n) ->
            if (i > 0) CardDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (edges) EuriseIcons.check() else EuriseIcons.flag(), null, tint = if (edges) Color(0xFF4F6B5A) else Color(0xFF85672B), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(if (edges) R.string.ed_fixed_edges else R.string.ed_blurry, n), fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { if (edges) onUndoEdges(file) else onRedoShot(file) }) { Text(stringResource(if (edges) R.string.ed_undo else R.string.ed_redo_shot)) }
            }
        }
        if (anyClean) {
            if (rows.isNotEmpty()) CardDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.check(), null, tint = Color(0xFF4F6B5A), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.ed_fixed_shadows), fontSize = 13.5.sp)
            }
        }
    }
}

/** Una pagina grande con il prima e dopo: a sinistra della maniglia la foto raddrizzata, a destra la pagina sistemata. */
@Composable
private fun Compare(file: File, origin: Origin, working: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val after = pageBitmap(file, 2)
    val before by produceState<Bitmap?>(null, origin.upright, origin.quad, origin.turns) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                BitmapFactory.decodeFile(origin.upright.path, BitmapFactory.Options().apply { inSampleSize = 2 })
                    ?.let { PageProcessor.rotate(PageProcessor.straighten(it, origin.quad), origin.turns) }
            }.getOrNull()
        }
    }
    var split by remember(file) { mutableFloatStateOf(0.42f) }
    val compare = stringResource(R.string.ed_compare)
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        val a = after ?: return@Box Text(stringResource(R.string.ed_working), color = scheme.onSurfaceVariant, modifier = Modifier.padding(40.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(a.width.toFloat() / a.height).heightIn(max = 460.dp)
            .background(Color.White).pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume(); split = (change.position.x / size.width).coerceIn(0f, 1f) }
            }.semantics { contentDescription = compare }) {
            Image(a.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
            before?.let { b ->
                Image(b.asImageBitmap(), null, modifier = Modifier.fillMaxSize().drawWithContent { clipRect(right = size.width * split) { this@drawWithContent.drawContent() } })
            }
            val x = maxWidth * split
            Box(Modifier.offset(x = x - 1.dp).width(2.dp).fillMaxHeight().background(Color.White))
            Box(Modifier.offset(x = x - 18.dp).align(Alignment.CenterStart).size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Text("‹ ›", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
            }
            Text(stringResource(R.string.ed_before), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x9E1E1D1B)).padding(horizontal = 8.dp, vertical = 3.dp))
            Text(stringResource(R.string.ed_after), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xD91F2A44)).padding(horizontal = 8.dp, vertical = 3.dp))
            if (working) Box(Modifier.fillMaxSize().background(Color(0x55FFFFFF)))
        }
    }
}

/** Gli aspetti e, per Perfetta, Grigi e Colore, quanto è bianca la carta. */
@Composable
private fun AspectPicker(title: String, mode: ScanMode, strength: Float, enabled: Boolean, onMode: (ScanMode) -> Unit, onStrength: (Float) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var value by remember(strength) { mutableFloatStateOf(strength) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            aspects.forEach { (m, label) ->
                val on = m == mode
                Row(
                    Modifier.height(38.dp).clip(CircleShape).background(if (on) scheme.primary else scheme.surfaceContainerLow)
                        .clickable(enabled = enabled && !on, role = Role.RadioButton) { onMode(m) }.semantics { this.selected = on }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (m == ScanMode.Document) Icon(EuriseIcons.spark(), null, tint = if (on) Color(0xFFE4C77E) else Color(0xFFB8923F), modifier = Modifier.size(13.dp))
                    Text(stringResource(label), fontSize = 13.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) scheme.onPrimary else scheme.onSurface)
                }
            }
        }
        if (mode == ScanMode.Document || mode == ScanMode.Gray || mode == ScanMode.Color) {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ed_whiter), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${(value * 100).toInt()} %", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                Slider(value = value, onValueChange = { value = it }, onValueChangeFinished = { if (abs(value - strength) > 0.01f) onStrength(value) }, enabled = enabled)
            }
            if (mode == ScanMode.Document) Text(stringResource(R.string.ed_perfect_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}

/**
 * Ritaglio preciso (tavola ScanRitaglio): angoli e lati, lente ×4 sul punto che si sposta, aggancio ai bordi trovati
 * allo scatto, «Bordi trovati» e «Tutta la foto».
 */
@Composable
private fun CropScreen(origin: Origin, number: Int, onCancel: () -> Unit, onDone: (Quad?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bitmap by produceState<Bitmap?>(null, origin) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(origin.upright.path, BitmapFactory.Options().apply { inSampleSize = 2 }) }
    }
    val whole = Quad(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f), Pt(0f, 1f))
    var corners by remember { mutableStateOf((origin.quad ?: origin.found ?: whole).points) }
    var snap by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf<Offset?>(null) }
    BackHandler(onBack = onCancel)

    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.scan_cancel)) }
            Text(stringResource(R.string.ed_crop_title, number), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Button(onClick = { onDone(Quad(corners[0], corners[1], corners[2], corners[3]).takeUnless { it == whole }) }) { Text(stringResource(R.string.ed_done)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF3B352E)).padding(18.dp), contentAlignment = Alignment.Center) {
            bitmap?.let { b ->
                val image = remember(b) { b.asImageBitmap() }
                Box(Modifier.aspectRatio(b.width.toFloat() / b.height)) {
                    Image(image, contentDescription = null, modifier = Modifier.fillMaxSize())
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(snap) {
                            // 0–3 angoli, 4–7 lati (tra l'angolo i e il successivo).
                            var active = 0
                            detectDragGestures(
                                onDragStart = { at ->
                                    val handles = corners.map { Offset(it.x * size.width, it.y * size.height) } +
                                        corners.indices.map { i -> mid(corners[i], corners[(i + 1) % 4]).let { Offset(it.x * size.width, it.y * size.height) } }
                                    active = handles.indices.minBy { (handles[it] - at).getDistance() }
                                    dragging = at
                                },
                                onDragEnd = { dragging = null },
                                onDragCancel = { dragging = null },
                                onDrag = { change, delta ->
                                    change.consume()
                                    dragging = change.position
                                    val d = Pt(delta.x / size.width, delta.y / size.height)
                                    val next = corners.toMutableList()
                                    if (active < 4) {
                                        var p = Pt((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                                        if (snap) origin.found?.points?.get(active)?.let { f -> if (hypot(f.x - p.x, f.y - p.y) < 0.025f) p = f }
                                        next[active] = p
                                    } else {
                                        val i = active - 4
                                        val j = (i + 1) % 4
                                        next[i] = Pt((next[i].x + d.x).coerceIn(0f, 1f), (next[i].y + d.y).coerceIn(0f, 1f))
                                        next[j] = Pt((next[j].x + d.x).coerceIn(0f, 1f), (next[j].y + d.y).coerceIn(0f, 1f))
                                    }
                                    corners = next
                                },
                            )
                        },
                    ) {
                        val pts = corners.map { Offset(it.x * size.width, it.y * size.height) }
                        val outline = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
                        // Fuori dal foglio più scuro.
                        val shade = Path().apply {
                            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                            addPath(outline)
                            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                        }
                        drawPath(shade, Color(0x73141412))
                        drawPath(outline, Color.White, style = Stroke(width = 2.5.dp.toPx()))
                        pts.forEach { p ->
                            drawCircle(Color.White, 11.dp.toPx(), p)
                            drawCircle(Color(0xFF1F2A44), 11.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
                        }
                        pts.indices.forEach { i ->
                            val m = (pts[i] + pts[(i + 1) % 4]) / 2f
                            drawCircle(Color.White, 7.dp.toPx(), m)
                            drawCircle(Color(0xFF1F2A44), 7.dp.toPx(), m, style = Stroke(2.dp.toPx()))
                        }
                        // La lente: ×4 sul punto che si sposta, nell'angolo lontano dal dito.
                        dragging?.let { at ->
                            val r = 56.dp.toPx()
                            val center = Offset(if (at.x < size.width / 2) size.width - r - 8.dp.toPx() else r + 8.dp.toPx(), r + 8.dp.toPx())
                            val zoom = 4f
                            val scaleX = b.width / size.width
                            val scaleY = b.height / size.height
                            val srcW = (2 * r / zoom * scaleX).toInt().coerceAtLeast(1)
                            val srcH = (2 * r / zoom * scaleY).toInt().coerceAtLeast(1)
                            val srcX = (at.x * scaleX - srcW / 2).toInt().coerceIn(0, (b.width - srcW).coerceAtLeast(0))
                            val srcY = (at.y * scaleY - srcH / 2).toInt().coerceIn(0, (b.height - srcH).coerceAtLeast(0))
                            val lens = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
                            clipPath(lens) {
                                drawImage(image, IntOffset(srcX, srcY), IntSize(srcW, srcH), IntOffset((center.x - r).toInt(), (center.y - r).toInt()), IntSize((2 * r).toInt(), (2 * r).toInt()))
                                drawLine(Color(0xFF1F2A44), Offset(center.x - 14.dp.toPx(), center.y), Offset(center.x + 14.dp.toPx(), center.y), 2.dp.toPx())
                                drawLine(Color(0xFF1F2A44), Offset(center.x, center.y - 14.dp.toPx()), Offset(center.x, center.y + 14.dp.toPx()), 2.dp.toPx())
                            }
                            drawCircle(Color.White, r, center, style = Stroke(3.dp.toPx()))
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                origin.found?.let { f -> FilterPill(stringResource(R.string.ed_found), dot = null, selected = corners == f.points) { corners = f.points } }
                FilterPill(stringResource(R.string.ed_whole), dot = null, selected = corners == whole.points) { corners = whole.points }
            }
            if (origin.found != null) Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ed_snap), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = snap, onCheckedChange = { snap = it })
            }
            Text(stringResource(R.string.ed_crop_hint), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = { corners = (origin.quad ?: origin.found ?: whole).points }) { Text(stringResource(R.string.ed_reset)) }
            }
        }
    }
}

private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)

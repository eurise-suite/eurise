package eu.eurise.office.rapida

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.component.DockItem
import eu.eurise.core.designsystem.component.EuriseDock
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.office.R
import eu.eurise.office.home.OfficePrefs
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

private val Paper = Color(0xFFFFFFFF)
private val Ink = Color(0xFF2A2925)
private val Mark = Color(0xFFFFF6D6)
private val Ok = Color(0xFF2B4A37)
private val OkBack = Color(0xFFEAF1EC)

/**
 * La vista rapida (tavole OffLettura, OffLetturaCorreggi, OffLetturaFoglio, OffLetturaCompleta). In alto nome e stato
 * del salvataggio, l'etichetta «Vista rapida» e «Aspetto esatto»; al centro il documento; in basso, vicino al pollice,
 * Correggi, Commenta, Completa, Condividi e la lente.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickScreen(model: QuickModel, engineReady: Boolean, onBack: () -> Unit, onFull: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    val haze = remember { HazeState() }
    var draft by remember { mutableStateOf<Draft?>(null) }
    val editing = draft?.para?.id
    var commentOn by remember { mutableStateOf<Para?>(null) }
    var firstTip by remember { mutableStateOf(!OfficePrefs.quickTipSeen(context)) }

    BackHandler(enabled = editing != null || model.selected != null || model.searching || model.commenting) {
        when {
            editing != null -> draft = null
            model.selected != null -> model.selected = null
            model.searching -> model.searching = false
            else -> model.commenting = false
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.fillMaxSize()) {
            Header(model, onBack, onExact = onFull)
            when (val s = model.state) {
                QuickState.Loading -> Message(stringResource(R.string.quick_loading))
                is QuickState.Failed -> Message(stringResource(s.reason))
                is QuickState.Text -> TextBody(
                    model, s.doc, draft, haze, Modifier.weight(1f),
                    onTap = { p ->
                        firstTip = false; OfficePrefs.setQuickTipSeen(context)
                        when {
                            model.commenting -> { commentOn = p; model.commenting = false }
                            !p.editable -> model.notice = R.string.quick_not_editable to null
                            else -> { draft?.finish(model); draft = Draft(p) }
                        }
                    },
                )
                is QuickState.Sheets -> SheetBody(model, s.doc, haze, Modifier.weight(1f))
            }
        }

        val bottom = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        // Con la tastiera aperta (cella in modifica) la scheda sale sopra la tastiera e la barra si fa da parte.
        val typing = WindowInsets.isImeVisible
        if (editing == null) Column(bottom.imePadding()) {
            val state = model.state
            if (state is QuickState.Sheets) {
                SheetTabs(model, state.doc)
                model.selected?.let { sel -> CellCard(model, state.doc, sel, onFull = { model.showFull = true }) }
            }
            val waiting = model.lastResult?.pending.orEmpty()
            when {
                model.selected == null && waiting.isNotEmpty() ->
                    Hint(stringResource(R.string.quick_pending, waiting.joinToString(", ")), stringResource(R.string.quick_full)) { model.showFull = true }
                model.unsaved -> Hint(stringResource(R.string.quick_unsaved_hint), stringResource(R.string.quick_save_in)) { model.saveIn(context) }
                model.commenting -> Hint(stringResource(R.string.quick_comment_tip))
                firstTip && state is QuickState.Text -> Hint(stringResource(R.string.quick_tip))
            }
            if (model.searching) SearchField(model)
            if (!typing || model.searching) EuriseDock(
                items = listOf(
                    DockItem(stringResource(R.string.quick_correct)) { EuriseIcons.pencil(it) },
                    DockItem(stringResource(R.string.quick_comment)) { EuriseIcons.chat(it) },
                    DockItem(stringResource(R.string.quick_full)) { EuriseIcons.office(it) },
                    DockItem(stringResource(R.string.quick_share)) { EuriseIcons.share(it) },
                ),
                selected = if (model.commenting) 1 else -1,
                onSelect = {
                    when (it) {
                        0 -> { model.commenting = false; model.showCorrect = !model.showCorrect; firstTip = true }
                        1 -> if ((state as? QuickState.Text)?.doc?.commenting == true) model.commenting = !model.commenting else model.showFull = true
                        2 -> model.showFull = true
                        3 -> onShare()
                    }
                },
                hazeState = haze,
                actionLabel = stringResource(R.string.quick_search),
                actionIcon = EuriseIcons.search(),
                onAction = { model.searching = !model.searching },
            )
        }

        draft?.let { d ->
        val doc = (model.state as? QuickState.Text)?.doc
        EditBar(d, doc?.formatting == true, Modifier.align(Alignment.BottomCenter)) {
            d.finish(model)
            draft = null
        }
    }
    }

    commentOn?.let { p -> CommentDialog(p, onDismiss = { commentOn = null }) { note, author -> commentOn = null; model.comment(p.id, note, author) } }
    if (model.showFull) FullSheet(engineReady, onDismiss = { model.showFull = false }, onOpen = { model.showFull = false; onFull() })
    model.notice?.let { (id, arg) ->
        AlertDialog(
            onDismissRequest = { model.notice = null },
            text = { Text(if (arg == null) stringResource(id) else stringResource(id, arg)) },
            confirmButton = { TextButton(onClick = { model.notice = null }) { Text(stringResource(R.string.quick_ok)) } },
            dismissButton = if (id == R.string.quick_not_editable) ({ TextButton(onClick = { model.notice = null; model.showFull = true }) { Text(stringResource(R.string.quick_full)) } }) else null,
        )
    }
}

@Composable
private fun Header(model: QuickModel, onBack: () -> Unit, onExact: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(EuriseIcons.chevronLeft(), contentDescription = stringResource(R.string.pdf_back)) }
            Column(Modifier.weight(1f)) {
                Text(model.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = when (model.save) {
                    QuickSave.Saved -> if (model.canUndo) stringResource(R.string.quick_saved, model.where) else model.where
                    QuickSave.Saving -> stringResource(R.string.pdf_saving)
                    QuickSave.ReadOnly -> stringResource(R.string.quick_read_only, model.where)
                    QuickSave.Failed -> stringResource(R.string.pdf_save_failed)
                }
                Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (model.canUndo) IconButton(onClick = { model.undo() }) { Icon(EuriseIcons.undo(), contentDescription = stringResource(R.string.quick_undo)) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.height(28.dp).clip(CircleShape).background(Color(0xFFE4E9F2)).padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(EuriseIcons.flash(), contentDescription = null, tint = Color(0xFF1F2A44), modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.quick_badge), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2A44))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onExact) { Text(stringResource(R.string.quick_exact), fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Hint(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1F2A44)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.5.sp, color = Color.White, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SearchField(model: QuickModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Box(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (model.query.isEmpty()) Text(stringResource(R.string.quick_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            BasicTextField(
                value = model.query, onValueChange = { model.query = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

// ---- Testo ----

/** Il paragrafo in correzione: il testo (a capo «morbidi» come \u2028) e grassetto, corsivo, sottolineato. */
private class Draft(val para: Para) {
    val original = para.text.replace('\n', '\u2028')
    var value by mutableStateOf(TextFieldValue(original, TextRange(original.length)))
    var bold by mutableStateOf(para.allBold)
    var italic by mutableStateOf(para.allItalic)
    var underline by mutableStateOf(para.allUnderline)

    fun reset() {
        value = TextFieldValue(original, TextRange(original.length))
        bold = para.allBold; italic = para.allItalic; underline = para.allUnderline
    }

    fun finish(model: QuickModel) {
        val style = Style(
            bold = bold.takeIf { it != para.allBold },
            italic = italic.takeIf { it != para.allItalic },
            underline = underline.takeIf { it != para.allUnderline },
        )
        if (value.text != original || !style.none) model.correct(para.id, value.text, style)
    }
}

@Composable
private fun TextBody(model: QuickModel, doc: TextDoc, draft: Draft?, haze: HazeState, modifier: Modifier, onTap: (Para) -> Unit) {
    val list = rememberLazyListState()
    val q = model.query.trim()
    LaunchedEffect(q, doc) {
        if (q.length < 2) return@LaunchedEffect
        val index = doc.blocks.indexOfFirst { b -> blockText(b).contains(q, ignoreCase = true) }
        if (index >= 0) list.animateScrollToItem(index)
    }
    Box(modifier.fillMaxWidth().imePadding()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = if (draft != null) 90.dp else 200.dp),
        ) {
            itemsIndexed(doc.blocks, key = { i, _ -> i }) { i, block ->
                val first = i == 0
                val last = i == doc.blocks.lastIndex
                val shape = RoundedCornerShape(
                    topStart = if (first) 10.dp else 0.dp, topEnd = if (first) 10.dp else 0.dp,
                    bottomStart = if (last) 10.dp else 0.dp, bottomEnd = if (last) 10.dp else 0.dp,
                )
                Box(Modifier.fillMaxWidth().clip(shape).background(Paper).padding(horizontal = 20.dp, vertical = if (first || last) 14.dp else 0.dp)) {
                    when (block) {
                        is Para -> ParaView(model, block, draft, q, onTap)
                        is Table -> TableView(model, block, draft, q, onTap)
                        is Placeholder -> Text(block.what, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun blockText(b: Block): String = when (b) {
    is Para -> b.text
    is Table -> b.rows.flatten().flatten().joinToString(" ") { it.text }
    is Placeholder -> ""
}

@Composable
private fun ParaView(model: QuickModel, p: Para, draft: Draft?, query: String, onTap: (Para) -> Unit) {
    if (draft != null && draft.para.id == p.id) { ParaField(draft); return }
    val size = when (p.level) { 1 -> 21.sp; 2 -> 18.sp; 3 -> 16.sp; else -> 14.sp }
    val outline = model.showCorrect && p.editable
    Column(
        Modifier.fillMaxWidth().padding(vertical = if (p.level > 0) 6.dp else 3.dp).clip(RoundedCornerShape(6.dp))
            .then(if (outline) Modifier.border(1.dp, Color(0xFFE8C860), RoundedCornerShape(6.dp)) else Modifier)
            .clickable { onTap(p) }.padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
        Text(
            styled(p, query),
            fontFamily = FontFamily.Serif,
            fontSize = size,
            lineHeight = size * 1.45f,
            fontWeight = if (p.level > 0) FontWeight.Bold else null,
            color = Ink,
            textAlign = when (p.align) { Align.Center -> TextAlign.Center; Align.End -> TextAlign.End; Align.Justify -> TextAlign.Justify; Align.Start -> TextAlign.Start },
            modifier = Modifier.fillMaxWidth(),
        )
        p.comments.forEach { c ->
            Row(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Mark).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.chat(), contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF6B4A12))
                Text(c, fontSize = 12.sp, color = Color(0xFF4A3A18), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

private fun styled(p: Para, query: String): AnnotatedString = buildAnnotatedString {
    if (p.bullet) append("\u2022  ")
    p.spans.forEach { s ->
        val start = length
        append(s.text)
        addStyle(
            SpanStyle(
                fontWeight = if (s.bold) FontWeight.Bold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
                textDecoration = if (s.underline) TextDecoration.Underline else null,
            ),
            start, length,
        )
    }
    if (query.length >= 2) {
        val all = toString()
        var at = all.indexOf(query, ignoreCase = true)
        while (at >= 0) {
            addStyle(SpanStyle(background = Color(0xFFFFE08A)), at, at + query.length)
            at = all.indexOf(query, at + query.length, ignoreCase = true)
        }
    }
}

/** Il paragrafo in correzione (tavola OffLetturaCorreggi). Invio fa un paragrafo nuovo. */
@Composable
private fun ParaField(draft: Draft) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(draft) { focus.requestFocus() }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        BasicTextField(
            value = draft.value, onValueChange = { draft.value = it },
            textStyle = TextStyle(
                fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 22.sp, color = Ink,
                fontWeight = if (draft.bold) FontWeight.Bold else null,
                fontStyle = if (draft.italic) FontStyle.Italic else null,
                textDecoration = if (draft.underline) TextDecoration.Underline else null,
            ),
            cursorBrush = SolidColor(Color(0xFF3E5C8A)),
            modifier = Modifier.fillMaxWidth().border(2.dp, Color(0xFF3E5C8A), RoundedCornerShape(8.dp)).padding(10.dp).focusRequester(focus),
        )
        Text(stringResource(R.string.quick_edit_note), fontSize = 11.5.sp, color = Ok, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    }
}

/** La barra della correzione: sta sopra la tastiera, a portata di pollice. */
@Composable
private fun EditBar(draft: Draft, formatting: Boolean, modifier: Modifier, onDone: () -> Unit) {
    Row(
        modifier.imePadding().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth().height(56.dp)
            .clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, Color(0x1A1E1D1B), RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (formatting) {
            Toggle("G", draft.bold, FontWeight.Bold, null, null) { draft.bold = !draft.bold }
            Toggle("C", draft.italic, null, FontStyle.Italic, null) { draft.italic = !draft.italic }
            Toggle("S", draft.underline, null, null, TextDecoration.Underline) { draft.underline = !draft.underline }
        }
        IconButton(onClick = { draft.reset() }) { Icon(EuriseIcons.undo(), contentDescription = stringResource(R.string.quick_undo)) }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.height(42.dp).clip(CircleShape).background(Color(0xFF1F2A44)).clickable(onClick = onDone).padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.quick_done), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp) }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, weight: FontWeight?, style: FontStyle?, decoration: TextDecoration?, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (on) Color(0xFFE4E9F2) else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = weight, fontStyle = style, textDecoration = decoration)
    }
}

@Composable
private fun TableView(model: QuickModel, t: Table, draft: Draft?, query: String, onTap: (Para) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, Color(0xFFE4E2DC))) {
        t.rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(if (r == 0) Color(0xFFEEF1F6) else Paper)) {
                row.forEach { cell ->
                    Column(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color(0xFFE4E2DC)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        cell.forEach { p -> ParaView(model, p, draft, query, onTap) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentDialog(p: Para, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var author by remember { mutableStateOf(OfficePrefs.author(context)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_comment_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.text.take(140), fontSize = 12.5.sp, fontFamily = FontFamily.Serif, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.quick_comment_text)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, singleLine = true, label = { Text(stringResource(R.string.quick_comment_author)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = note.isNotBlank(), onClick = { OfficePrefs.setAuthor(context, author.trim()); onSave(note.trim(), author.trim().ifEmpty { "Eurise" }) }) {
                Text(stringResource(R.string.quick_comment_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pdf_cancel)) } },
    )
}

// ---- Foglio ----

private val CellWidth = 104.dp
private val HeadWidth = 40.dp
private val RowHeight = 40.dp

@Composable
private fun SheetBody(model: QuickModel, doc: SheetDoc, haze: HazeState, modifier: Modifier) {
    val sheet = doc.sheets.getOrNull(model.sheetIndex) ?: doc.sheets.firstOrNull() ?: return
    val rows = maxOf(sheet.rows + 6, 40)
    val cols = maxOf(sheet.cols + 2, 4)
    val side = rememberScrollState()
    val list = rememberLazyListState()
    val q = model.query.trim()
    LaunchedEffect(q, sheet) {
        if (q.length < 2) return@LaunchedEffect
        sheet.cells.values.filter { it.display.contains(q, ignoreCase = true) }.minByOrNull { it.row }?.let { list.animateScrollToItem(it.row + 1) }
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(10.dp)).background(Paper).hazeSource(haze)) {
        Box(Modifier.fillMaxSize().horizontalScroll(side)) {
            LazyColumn(
                state = list,
                modifier = Modifier.width(HeadWidth + CellWidth * cols).fillMaxHeight(),
                contentPadding = PaddingValues(bottom = 260.dp),
            ) {
                item(key = "head") {
                    Row {
                        HeadCell("", HeadWidth)
                        for (c in 0 until cols) HeadCell(Cells.colName(c), CellWidth)
                    }
                }
                items(rows, key = { it }) { r ->
                    Row {
                        HeadCell((r + 1).toString(), HeadWidth)
                        for (c in 0 until cols) {
                            val cell = sheet[c, r]
                            val chosen = model.selected == (c to r)
                            val recalculated = model.lastResult?.recalculated?.contains(Cells.ref(c, r)) == true
                            val hit = q.length >= 2 && cell?.display?.contains(q, ignoreCase = true) == true
                            Box(
                                Modifier.width(CellWidth).height(RowHeight)
                                    .background(
                                        when {
                                            chosen -> Color(0xFFEEF2FA)
                                            hit -> Color(0xFFFFE08A)
                                            recalculated -> Color(0xFFDCE8DF)
                                            else -> Paper
                                        },
                                    )
                                    .border(if (chosen) 2.5.dp else 0.5.dp, if (chosen) Color(0xFF3E5C8A) else Color(0xFFE8E6E0))
                                    .clickable { model.selected = c to r; model.lastResult = null }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = if (cell?.number != null) Alignment.CenterEnd else Alignment.CenterStart,
                            ) {
                                Text(cell?.display.orEmpty(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (chosen) FontWeight.Bold else null, color = Ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.width(width).height(34.dp).background(Color(0xFFECEBE6)).border(0.5.dp, Color(0xFFDEDCD5)),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF56564F)) }
}

@Composable
private fun SheetTabs(model: QuickModel, doc: SheetDoc) {
    if (doc.sheets.size < 2) return
    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        doc.sheets.forEachIndexed { i, s ->
            val on = i == model.sheetIndex
            Box(
                Modifier.height(32.dp).clip(CircleShape).background(if (on) Color(0xFF1F2A44) else MaterialTheme.colorScheme.surface)
                    .clickable { model.sheetIndex = i; model.selected = null; model.lastResult = null }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(s.name, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface) }
        }
    }
}

/** La cella scelta (tavola OffLetturaFoglio): il campo è in basso, vicino al pollice; il totale si aggiorna qui. */
@Composable
private fun CellCard(model: QuickModel, doc: SheetDoc, at: Pair<Int, Int>, onFull: () -> Unit) {
    val sheet = doc.sheets.getOrNull(model.sheetIndex) ?: return
    val (col, row) = at
    val cell = sheet[col, row]
    val start = cell?.let { c -> c.formula?.let { "=$it" } ?: if (c.hasFormula) null else c.display } ?: ""
    var value by remember(at, model.sheetIndex, cell) { mutableStateOf(TextFieldValue(start ?: "", TextRange((start ?: "").length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(at) { if (start != null) focus.requestFocus() }
    val label = listOfNotNull(
        sheet[0, row]?.display?.takeIf { col != 0 && it.isNotBlank() },
        sheet[col, 0]?.display?.takeIf { row != 0 && it.isNotBlank() },
    ).joinToString(" · ")
    val scope = rememberCoroutineScope()

    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFE4E9F2)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(Cells.ref(col, row), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1F2A44))
            }
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (start == null) {
            // Una formula che la vista rapida non sa leggere: si cambia nella modifica completa.
            Text(stringResource(R.string.quick_formula_full, cell?.display.orEmpty()), fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = onFull) { Text(stringResource(R.string.quick_full), fontWeight = FontWeight.Bold) }
            return@Column
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = value, onValueChange = { value = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Color(0xFF3E5C8A)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.text != start) model.setCell(col, row, value.text) }),
                modifier = Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, Color(0xFF3E5C8A), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp).focusRequester(focus),
            )
            Box(
                Modifier.height(46.dp).clip(CircleShape).background(Color(0xFF1F2A44)).clickable {
                    if (value.text != start) model.setCell(col, row, value.text)
                    scope.launch { model.selected = null }
                }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.quick_done), color = Color.White, fontWeight = FontWeight.Bold) }
        }
        model.lastResult?.let { r ->
            if (r.recalculated.isNotEmpty()) Text(stringResource(R.string.quick_recalculated, r.recalculated.joinToString(", ")), fontSize = 11.5.sp, color = Ok, modifier = Modifier.padding(top = 8.dp))
            if (r.pending.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.quick_pending, r.pending.joinToString(", ")), fontSize = 11.5.sp, color = Color(0xFF6B4A12), modifier = Modifier.weight(1f))
                TextButton(onClick = onFull) { Text(stringResource(R.string.quick_full)) }
            }
        } ?: Text(stringResource(R.string.quick_cell_note), fontSize = 11.5.sp, color = Ok, modifier = Modifier.padding(top = 8.dp))
    }
}

// ---- Modifica completa ----

/** Tavola OffLetturaCompleta: cosa fa il motore, se va scaricato, e l'interruttore «Apri sempre nella modifica completa». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullSheet(engineReady: Boolean, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    var always by remember { mutableStateOf(OfficePrefs.alwaysFull(context)) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.quick_full_title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.quick_full_body), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            listOf(
                Triple(R.string.quick_full_styles, R.string.quick_full_styles_sub, EuriseIcons.heading()),
                Triple(R.string.quick_full_tables, R.string.quick_full_tables_sub, EuriseIcons.table()),
                Triple(R.string.quick_full_reviews, R.string.quick_full_reviews_sub, EuriseIcons.chat()),
            ).forEach { (t, s, icon) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEEF1F6)), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = Color(0xFF1F2A44), modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(stringResource(t), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(s), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!engineReady) Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)) {
                Text(stringResource(R.string.quick_full_missing), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.quick_full_missing_sub), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.quick_not_now), fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1.4f).height(50.dp).clip(CircleShape).background(Color(0xFF1F2A44)).clickable(onClick = onOpen),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(if (engineReady) R.string.quick_full_open else R.string.quick_full_download), color = Color.White, fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.quick_full_always), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Switch(always, { always = it; OfficePrefs.setAlwaysFull(context, it) })
            }
        }
    }
}

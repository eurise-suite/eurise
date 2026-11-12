package eu.eurise.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Gli strumenti della Lettura (tavola BrwStrumenti). */
private enum class Tool { Find, Capture, Note, Text, Font, Pdf, Images, Later }

/**
 * La vista Lettura con i suoi strumenti (tavola BrwStrumenti): tempo di lettura, Traduci e Ascolta in alto, la data
 * trovata nella pagina da mettere nel Calendario, la nota del sito e, in basso, gli strumenti della pagina.
 * [offline]: articolo salvato in «Leggi dopo» (niente Traduci né PDF, che hanno bisogno della pagina).
 */
@Composable
internal fun ReaderScreen(
    model: BrowserModel,
    tab: Tab?,
    start: PageInfo,
    offline: Boolean,
    actions: BrowserActions,
    onTranslate: (Tab) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminanceIsDark()
    var info by remember(start) { mutableStateOf(start) }
    val title = info.title.ifBlank { tab?.title.orEmpty() }
    val blocks = remember(info.text) { Reading.blocks(info.text) }
    val minutes = remember(info.text) { Reading.minutes(info.text) }
    val host = Links.host(info.url.ifBlank { tab?.url.orEmpty() })
    var note by remember(host) { mutableStateOf(model.siteNotes.get(host)) }
    var date by remember(info.text) { mutableStateOf<PageDate?>(null) }
    LaunchedEffect(info.text) {
        date = withContext(Dispatchers.Default) { Reading.firstDate(info.text, LocalDateTime.now(), Locale.getDefault().language) }
    }
    // Dopo «Traduci» il testo della pagina cambia: si rilegge quando la traduzione è arrivata.
    val translated = tab?.translated == true
    LaunchedEffect(translated) {
        if (offline || tab == null) return@LaunchedEffect
        repeat(2) { i ->
            delay(if (i == 0) 1_500 else 3_000)
            tab.page { fresh -> if (fresh != null && fresh.text.isNotBlank()) info = fresh }
        }
    }

    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hit by remember { mutableIntStateOf(0) }
    var editingNote by remember { mutableStateOf(false) }
    val hits = remember(blocks, query) {
        blocks.flatMapIndexed { i, b -> Reading.matches(b.removePrefix("# "), query).map { i to it } }
    }
    val list = rememberLazyListState()
    // Indice nella lista: titolo, (nota), (data), poi i blocchi.
    val firstBlock = 1 + (if (note.isNotBlank()) 1 else 0) + (if (date != null) 1 else 0)
    LaunchedEffect(hit, hits) {
        hits.getOrNull(hit)?.let { (block, _) -> list.animateScrollToItem(firstBlock + block) }
    }
    val atTop by remember { derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < 60 } }
    var toolsOpen by remember { mutableStateOf(true) }
    LaunchedEffect(atTop) { toolsOpen = atTop }

    val savedLabel = stringResource(R.string.brw_later_saved)
    val removedLabel = stringResource(R.string.brw_later_removed)
    val serif = model.settings.readerSerif
    val bodyFont = if (serif) FontFamily.Serif else FontFamily.Default
    val scale = model.settings.textScale
    val mark = if (dark) Color(0x664F6B5A) else Color(0xFFDCE7DF)
    val markNow = if (dark) Color(0xFF85672B) else Color(0xFFF2D58C)

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundTop(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.brw_reader_exit), onClick = onClose)
                Spacer(Modifier.width(8.dp))
                Text(
                    buildString {
                        append(stringResource(R.string.brw_reader_minutes, minutes))
                        if (offline) append(" · ").append(stringResource(R.string.brw_later_offline))
                    },
                    fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1,
                )
                if (!offline && tab != null) {
                    RoundTop(EuriseIcons.translate(2.2f), stringResource(R.string.brw_m_translate)) { onTranslate(tab) }
                    Spacer(Modifier.width(8.dp))
                }
                RoundTop(EuriseIcons.mic(2.2f), stringResource(R.string.brw_listen), strong = true) { actions.listen(info.text) }
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, bottom = 240.dp)) {
                item(key = "title") {
                    Text(title, fontSize = (22 * scale).sp, lineHeight = (29 * scale).sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                }
                if (note.isNotBlank()) item(key = "note") {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(scheme.surface)
                            .clickable { editingNote = true }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(EuriseIcons.note(2.2f), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(note, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                    }
                }
                date?.let { d ->
                    item(key = "date") {
                        val tint = CalendarTint.Salvia
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp))
                                .background((if (dark) tint.dark else tint.light).copy(alpha = 0.18f)).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(EuriseIcons.calendar(2.2f), null, tint = if (dark) tint.dark else tint.light, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                d.date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())) +
                                    (d.time?.let { " " + it.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) } ?: "") +
                                    " · " + title.take(40),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Chip(stringResource(R.string.brw_reader_remind), strong = true) { actions.addEvent(title, d, info.url) }
                        }
                    }
                }
                if (blocks.isEmpty()) item(key = "empty") {
                    Text(stringResource(R.string.brw_reader_empty), fontSize = 14.sp, color = scheme.onSurfaceVariant)
                }
                itemsIndexed(blocks, key = { i, _ -> "b$i" }) { i, block ->
                    val heading = block.startsWith("# ")
                    val plain = block.removePrefix("# ")
                    val text = highlight(plain, query, hits.filter { it.first == i }.map { it.second }, hits.getOrNull(hit)?.takeIf { it.first == i }?.second, mark, markNow)
                    if (heading) {
                        Text(text, fontSize = (19 * scale).sp, lineHeight = (25 * scale).sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                    } else {
                        Text(text, fontFamily = bodyFont, fontSize = (16.5f * scale).sp, lineHeight = (27 * scale).sp,
                            color = scheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
            }
        }

        // In basso: gli strumenti, o la ricerca nel testo.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (finding) {
                ReaderFind(
                    query = query, count = hits.size, current = if (hits.isEmpty()) 0 else hit + 1,
                    onQuery = { query = it; hit = 0 },
                    onMove = { step -> if (hits.isNotEmpty()) hit = (hit + step + hits.size) % hits.size },
                    onClose = { finding = false; query = "" },
                )
            } else if (toolsOpen) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(scheme.surfaceContainer)
                        .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.brw_tools_title), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        val hide = stringResource(R.string.brw_tools_hide)
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable { toolsOpen = false }.semantics { contentDescription = hide }, contentAlignment = Alignment.Center) {
                            Icon(EuriseIcons.close(2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                    Tool.entries.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { t ->
                                val enabled = when (t) { Tool.Pdf -> !offline && tab != null; else -> true }
                                val (icon, label) = when (t) {
                                    Tool.Find -> EuriseIcons.search(2.1f) to stringResource(R.string.brw_m_find)
                                    Tool.Capture -> EuriseIcons.camera(2.1f) to stringResource(R.string.brw_tool_capture)
                                    Tool.Note -> EuriseIcons.note(2.1f) to stringResource(R.string.brw_tool_note)
                                    Tool.Text -> EuriseIcons.zoomIn(2.1f) to stringResource(R.string.brw_tool_text, (scale * 100).toInt())
                                    Tool.Font -> EuriseIcons.text(2.1f) to stringResource(if (serif) R.string.brw_tool_font_serif else R.string.brw_tool_font_sans)
                                    Tool.Pdf -> EuriseIcons.printer(2.1f) to stringResource(R.string.brw_tool_pdf)
                                    Tool.Images -> EuriseIcons.image(2.1f) to stringResource(if (model.settings.images) R.string.brw_tool_images_on else R.string.brw_tool_images_off)
                                    Tool.Later -> EuriseIcons.history(2.1f) to stringResource(if (model.isLater(info.url)) R.string.brw_tool_later_on else R.string.brw_tool_later)
                                }
                                ToolButton(icon, label, enabled, Modifier.weight(1f)) {
                                    when (t) {
                                        Tool.Find -> finding = true
                                        Tool.Capture -> actions.captureArticle(title, info.text, info.url)
                                        Tool.Note -> editingNote = true
                                        Tool.Text -> {
                                            val scales = BrowserSettings.SCALES
                                            val i = scales.indexOfFirst { kotlin.math.abs(it - scale) < 0.01f }
                                            model.setTextScale(scales[(i + 1).mod(scales.size)])
                                        }
                                        Tool.Font -> model.settings.setReaderSerif(!serif)
                                        Tool.Pdf -> tab?.let(actions::savePdf)
                                        Tool.Images -> model.setImages(!model.settings.images)
                                        Tool.Later -> model.notice = if (model.toggleLater(info)) savedLabel else removedLabel
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainer)
                            .clickable(role = Role.Button) { toolsOpen = true }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(EuriseIcons.tools(2f), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.brw_tools_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                    }
                }
            }
        }
    }

    if (editingNote) SiteNoteSheet(host, note, onSave = { text -> model.siteNotes.set(host, text); note = model.siteNotes.get(host); editingNote = false }, onClose = { editingNote = false })
}

private fun Color.luminanceIsDark() = (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f

/** Il testo con le parole trovate evidenziate; quella in vista più forte. */
private fun highlight(text: String, query: String, starts: List<Int>, now: Int?, mark: Color, markNow: Color): AnnotatedString {
    if (starts.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        starts.forEach { s -> addStyle(SpanStyle(background = if (s == now) markNow else mark), s, (s + query.length).coerceAtMost(text.length)) }
    }
}

@Composable
private fun RoundTop(icon: ImageVector, label: String, strong: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (strong) scheme.primary else scheme.surface)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (strong) scheme.onPrimary else scheme.primary, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(scheme.surface), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** «Trova» nella Lettura: evidenzia nel testo e scorre fino alla parola. */
@Composable
private fun ReaderFind(query: String, count: Int, current: Int, onQuery: (String) -> Unit, onMove: (Int) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().background(scheme.surfaceContainer).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(scheme.surface).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(stringResource(R.string.brw_find_hint), fontSize = 14.sp, color = scheme.onSurfaceVariant)
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onMove(1) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) Text("$current/$count", fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
        }
        FindStep(EuriseIcons.chevronLeft(2f), stringResource(R.string.brw_find_previous), count > 0) { onMove(-1) }
        FindStep(EuriseIcons.chevronRight(2f), stringResource(R.string.brw_find_next), count > 0) { onMove(1) }
        FindStep(EuriseIcons.close(2f), stringResource(R.string.brw_cancel), true, onClose)
    }
}

@Composable
private fun FindStep(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(18.dp)) }
}

/** La nota del sito: resta sul telefono e la ritrovi ogni volta che leggi una pagina di quel sito. */
@Composable
internal fun SiteNoteSheet(host: String, start: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(start) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BottomSheet(onClose = onClose) {
        Text(stringResource(R.string.brw_note_title, host), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxWidth().heightIn(min = 110.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp)) {
            if (text.isEmpty()) Text(stringResource(R.string.brw_note_hint), fontSize = 14.sp, color = scheme.onSurfaceVariant)
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Text(stringResource(R.string.brw_note_where), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (start.isNotBlank()) Pill(stringResource(R.string.brw_remove), EuriseIcons.trash(2f), Modifier.weight(1f)) { onSave("") }
            Pill(stringResource(R.string.brw_note_save), EuriseIcons.check(2f), Modifier.weight(1f), strong = true) { onSave(text) }
        }
    }
}

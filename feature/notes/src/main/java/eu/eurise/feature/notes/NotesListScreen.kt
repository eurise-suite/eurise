package eu.eurise.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.NotebookRepository
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.NotebookSummary
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.component.LocalDockClearance
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.notes.doc.DrawingBlock
import eu.eurise.feature.notes.doc.NoteHtml
import eu.eurise.feature.notes.ui.DrawingThumbnail
import eu.eurise.feature.notes.ui.inkColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Elenco delle note con taccuini, ricerca e note fissate in alto.
 * [onOpenNote] riceve l'id della nota (null per una nuova) e il taccuino in cui farla nascere.
 */
@Composable
fun NotesListRoute(
    repository: NoteRepository,
    notebooks: NotebookRepository,
    filterRequest: NotesFilterRequest?,
    onOpenNote: (Long?, Long?) -> Unit,
    onOrganize: () -> Unit,
) {
    val viewModel = viewModel { NotesListViewModel(repository, notebooks) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    LaunchedEffect(filterRequest) { filterRequest?.let { viewModel.setFilter(it.filter) } }
    NotesListScreen(
        state = state,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onFilter = viewModel::setFilter,
        onOpenNote = { onOpenNote(it, (state.filter as? NotesFilter.Notebook)?.id) },
        onOrganize = onOrganize,
    )
}

@Composable
private fun NotesListScreen(
    state: NotesListState,
    query: String,
    onQueryChange: (String) -> Unit,
    onFilter: (NotesFilter) -> Unit,
    onOpenNote: (Long?) -> Unit,
    onOrganize: () -> Unit,
) {
    val haze = remember { HazeState() }
    var searching by rememberSaveable { mutableStateOf(false) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val books = state.notebooks.associateBy { it.notebook.id }
    val notes = state.notes
    val grouped = query.isBlank() && state.filter == NotesFilter.All
    val today = LocalDate.now()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = statusTop + 132.dp, bottom = LocalDockClearance.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                notes == null -> Unit
                notes.isEmpty() && query.isBlank() -> item(key = "empty") {
                    EmptyState(
                        icon = EuriseIcons.note(),
                        title = stringResource(R.string.notes_empty_title),
                        message = stringResource(R.string.notes_empty_message),
                    )
                }
                notes.isEmpty() -> item(key = "no-results") {
                    EmptyState(
                        icon = EuriseIcons.search(),
                        title = stringResource(R.string.notes_no_results_title),
                        message = stringResource(R.string.notes_no_results_message, query.trim()),
                    )
                }
                grouped -> {
                    val pinned = notes.filter { it.pinned }
                    val others = notes.filterNot { it.pinned }
                    if (pinned.isNotEmpty()) {
                        item(key = "h-pinned") { SectionLabel(stringResource(R.string.notes_section_pinned)) }
                        item(key = "pinned") {
                            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(0, 1).forEach { column ->
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        pinned.filterIndexed { i, _ -> i % 2 == column }.forEach { note ->
                                            PinnedCard(note, books[note.notebookId], onClick = { onOpenNote(note.id) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (others.isNotEmpty()) {
                        if (pinned.isNotEmpty()) item(key = "h-recent") { SectionLabel(stringResource(R.string.notes_section_recent)) }
                        items(others, key = { it.id }) { note ->
                            NoteRow(note, books[note.notebookId], today, onClick = { onOpenNote(note.id) }, modifier = Modifier.animateItem())
                        }
                    }
                }
                else -> items(notes, key = { it.id }) { note ->
                    NoteRow(note, books[note.notebookId], today, onClick = { onOpenNote(note.id) }, modifier = Modifier.animateItem())
                }
            }
        }

        // Testata satinata con titolo, ricerca e taccuini.
        Column(
            Modifier
                .fillMaxWidth()
                .glass(haze, RoundedCornerShape(0.dp), elevation = 0.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (searching) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Row(Modifier.padding(start = 16.dp, end = 4.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(stringResource(R.string.notes_search)) },
                        leadingIcon = { Icon(EuriseIcons.search(), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    IconButton(onClick = { onQueryChange(""); searching = false }) {
                        Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.notes_search_clear), modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                Row(Modifier.padding(start = 20.dp, end = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.notes_title), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        notes?.size?.toString().orEmpty(),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp).weight(1f),
                    )
                    IconButton(onClick = { searching = true }) {
                        Icon(EuriseIcons.search(), contentDescription = stringResource(R.string.notes_search), modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onOrganize) {
                        Icon(EuriseIcons.notebook(), contentDescription = stringResource(R.string.notes_organize), modifier = Modifier.size(22.dp))
                    }
                }
            }
            FilterChips(state.filter, state.notebooks, onFilter)
        }
    }
}

@Composable
private fun FilterChips(filter: NotesFilter, notebooks: List<NotebookSummary>, onFilter: (NotesFilter) -> Unit) {
    val ink = inkColors()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") { Chip(stringResource(R.string.notes_filter_all), null, filter == NotesFilter.All) { onFilter(NotesFilter.All) } }
        if (filter == NotesFilter.Pinned) {
            item(key = "pinned") { Chip(stringResource(R.string.notes_section_pinned), null, true, closable = true) { onFilter(NotesFilter.All) } }
        }
        if (filter is NotesFilter.Tag) {
            item(key = "tag") { Chip("#${filter.tag}", null, true, closable = true) { onFilter(NotesFilter.All) } }
        }
        items(notebooks, key = { it.notebook.id }) { s ->
            val selected = filter == NotesFilter.Notebook(s.notebook.id)
            Chip(s.notebook.name, ink.dot(s.notebook.tint), selected) {
                onFilter(if (selected) NotesFilter.All else NotesFilter.Notebook(s.notebook.id))
            }
        }
    }
}

@Composable
private fun Chip(label: String, dot: Color?, selected: Boolean, closable: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else scheme.surfaceContainerLow)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) scheme.onPrimary else dot))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            maxLines = 1,
        )
        if (closable) Icon(EuriseIcons.close(), contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 4.dp),
    )
}

/** Avanzamento degli elenchi di controllo di una nota, letto dall'HTML: (fatte, totali). */
private fun checklistProgress(note: Note): Pair<Int, Int>? {
    if (note.content.isBlank()) return null
    val all = CHECK.findAll(note.content).map { it.groupValues[1] == "true" }.toList()
    return if (all.isEmpty()) null else all.count { it } to all.size
}

private val CHECK = Regex("""data-checked="(true|false)"""")

private fun Note.heading(): String = title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }

private fun Note.snippet(): String {
    val text = if (title.isBlank()) body.substringAfter(heading(), "") else body
    return text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
private fun PinnedCard(note: Note, book: NotebookSummary?, onClick: () -> Unit) {
    val ink = inkColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        book?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(ink.dot(it.notebook.tint)))
                Text(it.notebook.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Text(
            note.heading().ifBlank { stringResource(R.string.notes_untitled) },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val snippet = note.snippet()
        if (snippet.isNotEmpty()) {
            Text(snippet, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        val drawing = remember(note.content) {
            if ("data-eurise=\"drawing\"" in note.content) {
                var n = 0L
                NoteHtml.read(note.content) { ++n }.firstOrNull { it is DrawingBlock } as? DrawingBlock
            } else null
        }
        drawing?.let { DrawingThumbnail(it) }
        if (note.hasConflict) ConflictBadge()
        checklistProgress(note)?.let { (done, total) -> Progress(done, total) }
    }
}

@Composable
private fun Progress(done: Int, total: Int) {
    val color = if (done == total) MaterialTheme.colorScheme.onSurfaceVariant else inkColors().text("Salvia")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(EuriseIcons.check(), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.notes_progress, done, total), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun NoteRow(note: Note, book: NotebookSummary?, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = inkColors()
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(book?.let { ink.dot(it.notebook.tint) } ?: MaterialTheme.colorScheme.outlineVariant),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.pinned) Icon(EuriseIcons.pin(), contentDescription = stringResource(R.string.notes_pinned), modifier = Modifier.size(14.dp).padding(end = 2.dp))
                Text(
                    note.heading().ifBlank { stringResource(R.string.notes_untitled) },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(shortTime(note.updatedAt, today), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val snippet = note.snippet()
            if (snippet.isNotEmpty()) {
                Text(snippet, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val progress = checklistProgress(note)
            if (note.hasConflict) ConflictBadge()
            when {
                progress != null -> Progress(progress.first, progress.second)
                book != null -> Text(book.notebook.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun shortTime(millis: Long, today: LocalDate): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val day = time.toLocalDate()
    return when (day) {
        today -> time.format(DateTimeFormatter.ofPattern("H:mm"))
        today.minusDays(1) -> stringResource(R.string.notes_yesterday)
        else -> time.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}

/** Segno delle note cambiate in due posti, in attesa di una scelta. */
@Composable
private fun ConflictBadge() {
    Text(
        stringResource(R.string.notes_conflict_badge),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = inkColors().text("Borgogna"),
    )
}

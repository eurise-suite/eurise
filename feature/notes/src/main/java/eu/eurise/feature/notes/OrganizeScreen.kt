package eu.eurise.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.NotebookRepository
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.NoteCollections
import eu.eurise.core.data.model.Notebook
import eu.eurise.core.data.model.NotebookSummary
import eu.eurise.core.data.model.TagCount
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.feature.notes.ui.NotebookDialog
import eu.eurise.feature.notes.ui.inkColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class OrganizeState(
    val collections: NoteCollections = NoteCollections(0, 0, 0),
    val notebooks: List<NotebookSummary> = emptyList(),
    val tags: List<TagCount> = emptyList(),
)

class OrganizeViewModel(notes: NoteRepository, private val notebooks: NotebookRepository) : ViewModel() {

    val state: StateFlow<OrganizeState> =
        combine(notes.observeCollections(), notebooks.observe(), notes.observeTags()) { c, b, t -> OrganizeState(c, b, t) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrganizeState())

    fun create(name: String, tint: String) {
        if (name.isBlank()) return
        viewModelScope.launch { notebooks.add(name, tint) }
    }

    fun update(notebook: Notebook) {
        if (notebook.name.isBlank()) return
        viewModelScope.launch { notebooks.update(notebook) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { notebooks.delete(id) }
    }
}

/** Raccolte, taccuini e tag. Scegliere una voce torna all'elenco con quel filtro. */
@Composable
fun OrganizeRoute(
    notes: NoteRepository,
    notebooks: NotebookRepository,
    onBack: () -> Unit,
    onFilter: (NotesFilter) -> Unit,
    onTrash: () -> Unit,
    onCloud: () -> Unit,
) {
    val viewModel = viewModel { OrganizeViewModel(notes, notebooks) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Notebook?>(null) }
    var deleting by remember { mutableStateOf<NotebookSummary?>(null) }

    SubScreen(title = stringResource(R.string.notes_organize), backLabel = stringResource(R.string.notes_title), onBack = onBack) {
        item(key = "collections") {
            Group {
                ListRow(EuriseIcons.note(), stringResource(R.string.notes_filter_all), state.collections.all.toString()) { onFilter(NotesFilter.All) }
                RowDivider()
                ListRow(EuriseIcons.pin(), stringResource(R.string.notes_section_pinned), state.collections.pinned.toString()) { onFilter(NotesFilter.Pinned) }
                RowDivider()
                ListRow(
                    EuriseIcons.trash(),
                    stringResource(R.string.notes_trash_title),
                    state.collections.trashed.toString(),
                    subtitle = stringResource(R.string.notes_trash_hint, NoteRepository.TRASH_DAYS),
                    onClick = onTrash,
                )
            }
        }
        item(key = "cloud") {
            Group {
                ListRow(EuriseIcons.cloud(), stringResource(R.string.notes_cloud), "", subtitle = stringResource(R.string.notes_cloud_hint), onClick = onCloud)
            }
        }
        item(key = "h-books") {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.notes_notebooks), Modifier.weight(1f))
                TextButton(onClick = { creating = true }) { Text(stringResource(R.string.notes_notebook_new_short)) }
            }
        }
        item(key = "books") {
            if (state.notebooks.isEmpty()) {
                Text(
                    stringResource(R.string.notes_notebooks_empty),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                Group {
                    state.notebooks.forEachIndexed { i, s ->
                        if (i > 0) RowDivider()
                        NotebookRow(
                            summary = s,
                            onClick = { onFilter(NotesFilter.Notebook(s.notebook.id)) },
                            onEdit = { editing = s.notebook },
                            onDelete = { deleting = s },
                        )
                    }
                }
            }
        }
        if (state.tags.isNotEmpty()) {
            item(key = "h-tags") { Label(stringResource(R.string.notes_tags), Modifier.padding(start = 20.dp, top = 10.dp)) }
            item(key = "tags") { TagCloud(state.tags) { onFilter(NotesFilter.Tag(it)) } }
        } else {
            item(key = "tags-hint") {
                Text(
                    stringResource(R.string.notes_tags_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }

    if (creating) {
        NotebookDialog(
            initialName = "",
            initialTint = CalendarTint.entries[state.notebooks.size % CalendarTint.entries.size].name,
            onConfirm = { name, tint -> creating = false; viewModel.create(name, tint) },
            onDismiss = { creating = false },
        )
    }
    editing?.let { book ->
        NotebookDialog(
            initialName = book.name,
            initialTint = book.tint,
            title = R.string.notes_notebook_edit,
            onConfirm = { name, tint -> editing = null; viewModel.update(book.copy(name = name, tint = tint)) },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.notes_notebook_delete_title, s.notebook.name)) },
            text = { Text(stringResource(R.string.notes_notebook_delete_message)) },
            confirmButton = {
                TextButton(onClick = { deleting = null; viewModel.delete(s.notebook.id) }) {
                    Text(stringResource(R.string.notes_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.notes_cancel)) } },
        )
    }
}

@Composable
private fun NotebookRow(summary: NotebookSummary, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val ink = inkColors()
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(ink.dot(summary.notebook.tint)))
        Text(summary.notebook.name, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(summary.count.toString(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(EuriseIcons.more(), contentDescription = stringResource(R.string.notes_notebook_actions, summary.notebook.name), modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.notes_notebook_edit)) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notes_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagCloud(tags: List<TagCount>, onClick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { t ->
            Row(
                Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { onClick(t.tag) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("#${t.tag}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(t.count.toString(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Cestino

class TrashViewModel(private val notes: NoteRepository) : ViewModel() {
    val trash: StateFlow<List<Note>?> = notes.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(id: Long) {
        viewModelScope.launch { notes.restore(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { notes.delete(id) }
    }

    fun empty() {
        viewModelScope.launch { notes.emptyTrash() }
    }
}

/** Note nel cestino: si ripristinano o si eliminano per sempre. */
@Composable
fun TrashRoute(notes: NoteRepository, onBack: () -> Unit) {
    val viewModel = viewModel { TrashViewModel(notes) }
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    SubScreen(
        title = stringResource(R.string.notes_trash_title),
        backLabel = stringResource(R.string.notes_organize),
        onBack = onBack,
        actionLabel = if (!trash.isNullOrEmpty()) stringResource(R.string.notes_trash_empty) else null,
        onAction = { confirmEmpty = true },
    ) {
        val list = trash
        when {
            list == null -> Unit
            list.isEmpty() -> item(key = "empty") {
                Text(
                    stringResource(R.string.notes_trash_none),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
            else -> items(list, key = { it.id }) { note ->
                val trashedAt = note.trashedAt ?: now
                val daysLeft = (NoteRepository.TRASH_DAYS - ((now - trashedAt) / 86_400_000L).toInt()).coerceAtLeast(0)
                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                ) {
                    Text(
                        note.title.ifBlank { note.body.lineSequence().firstOrNull().orEmpty() }.ifBlank { stringResource(R.string.notes_untitled) },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(R.plurals.notes_trash_days_left, daysLeft, daysLeft),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = { viewModel.restore(note.id) }) { Text(stringResource(R.string.notes_restore)) }
                        TextButton(onClick = { viewModel.delete(note.id) }) {
                            Text(stringResource(R.string.notes_delete_forever), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.notes_trash_empty_title)) },
            text = { Text(stringResource(R.string.notes_trash_empty_message)) },
            confirmButton = {
                TextButton(onClick = { confirmEmpty = false; viewModel.empty() }) {
                    Text(stringResource(R.string.notes_trash_empty), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.notes_cancel)) } },
        )
    }
}

// Pezzi comuni

/** Schermata secondaria: ritorno, titolo grande e contenuto a scorrimento. */
@Composable
private fun SubScreen(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) {
                        Icon(EuriseIcons.chevronLeft(), contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(backLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                    if (actionLabel != null) {
                        TextButton(onClick = onAction) { Text(actionLabel, color = MaterialTheme.colorScheme.error) }
                    }
                }
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
        content()
        item(key = "nav") { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) { content() }
}

@Composable
private fun RowDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.background)

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.getDefault()),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ListRow(icon: ImageVector, label: String, count: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            subtitle?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(count, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
    }
}

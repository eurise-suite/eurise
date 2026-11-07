package eu.eurise.feature.notes

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.NotebookRepository
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.model.Note
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.notes.doc.DividerBlock
import eu.eurise.feature.notes.doc.DrawingBlock
import eu.eurise.feature.notes.doc.NoteLinkBlock
import eu.eurise.feature.notes.doc.TableBlock
import eu.eurise.feature.notes.doc.TextBlock
import eu.eurise.feature.notes.doc.TextKind
import eu.eurise.feature.notes.ui.DividerView
import eu.eurise.feature.notes.ui.DrawingEditor
import eu.eurise.feature.notes.ui.DrawingPreview
import eu.eurise.feature.notes.ui.FormatToolbar
import eu.eurise.feature.notes.ui.InsertGrid
import eu.eurise.feature.notes.ui.InsertOption
import eu.eurise.feature.notes.ui.NoteLinkView
import eu.eurise.feature.notes.ui.NotebookPickerSheet
import eu.eurise.feature.notes.ui.TableBlockView
import eu.eurise.feature.notes.ui.TextBlockActions
import eu.eurise.feature.notes.ui.TextBlockField
import eu.eurise.feature.notes.ui.ToolbarActions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Editor di una nota; [noteId] nullo crea una nota nuova (nel taccuino [notebookId], se c'è).
 * [onClose] torna alla schermata precedente, [onOpenNote] apre una nota collegata.
 */
@Composable
fun NoteEditorRoute(
    notes: NoteRepository,
    notebooks: NotebookRepository,
    tasks: TaskRepository,
    noteId: Long?,
    notebookId: Long?,
    onClose: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val viewModel = viewModel { NoteEditorViewModel(notes, notebooks, tasks, noteId, notebookId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val books by viewModel.notebooks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler { viewModel.close(onClose) }
    NoteEditorScreen(
        state = state,
        notebookName = books.firstOrNull { it.notebook.id == state.notebookId }?.notebook?.name,
        isNew = noteId == null,
        viewModel = viewModel,
        onDone = { viewModel.close(onClose) },
        onTrash = { viewModel.trash(onClose) },
        onShare = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
            context.startActivity(Intent.createChooser(send, null))
        },
        onOpenNote = { target -> viewModel.close { onOpenNote(target) } },
        notebookPicker = { onDismiss ->
            NotebookPickerSheet(
                notebooks = books,
                selected = state.notebookId,
                onPick = { viewModel.setNotebook(it); onDismiss() },
                onCreate = { name, tint -> viewModel.createNotebook(name, tint); onDismiss() },
                onDismiss = onDismiss,
            )
        },
        linkPicker = { onDismiss ->
            NoteLinkPicker(
                search = { notes.observe(it) },
                excludeId = noteId,
                onPick = { note -> viewModel.insertNoteLink(note.id, note.title.ifBlank { note.body.lineSequence().firstOrNull().orEmpty() }); onDismiss() },
                onDismiss = onDismiss,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NoteEditorScreen(
    state: NoteEditorState,
    notebookName: String?,
    isNew: Boolean,
    viewModel: NoteEditorViewModel,
    onDone: () -> Unit,
    onTrash: () -> Unit,
    onShare: () -> Unit,
    onOpenNote: (Long) -> Unit,
    notebookPicker: @Composable (onDismiss: () -> Unit) -> Unit,
    linkPicker: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    val haze = remember { HazeState() }
    var menu by remember { mutableStateOf(false) }
    var insertSheet by rememberSaveable { mutableStateOf(false) }
    var pickNotebook by rememberSaveable { mutableStateOf(false) }
    var pickLink by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!state.loaded) return@Box
        val titleFocus = remember { FocusRequester() }
        val actions = remember(viewModel) {
            TextBlockActions(
                onChange = viewModel::onTextChange,
                onBackspaceAtStart = viewModel::onBackspaceAtStart,
                onFocus = viewModel::onFocus,
                onToggleChecked = viewModel::toggleChecked,
                onAddToTasks = viewModel::addToTasks,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = statusTop + 64.dp, bottom = 120.dp + navBottom),
        ) {
            BasicTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                textStyle = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .onFocusChanged { if (it.isFocused) viewModel.onFocus(null) },
                decorationBox = { inner ->
                    Box {
                        if (state.title.isEmpty()) {
                            Text(stringResource(R.string.notes_title_placeholder), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
            Text(
                metaLine(state.updatedAt, notebookName),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )

            var number = 0
            state.blocks.forEachIndexed { index, block ->
                when (block) {
                    is TextBlock -> {
                        number = if (block.kind == TextKind.Numbered) number + 1 else 0
                        TextBlockField(
                            block = block,
                            selection = state.selections[block.id],
                            focused = state.focusedId == block.id,
                            number = number,
                            focusRequest = state.focusRequest,
                            actions = actions,
                            placeholder = if (index == 0 && state.blocks.size == 1) stringResource(R.string.notes_body_placeholder) else null,
                        )
                    }
                    is TableBlock -> TableBlockView(
                        block = block,
                        onCell = { r, c, t -> viewModel.updateCell(block.id, r, c, t) },
                        onAddRow = { viewModel.addRow(block.id) },
                        onAddColumn = { viewModel.addColumn(block.id) },
                        onRemove = { viewModel.removeBlock(block.id) },
                        onFocus = viewModel::onFocus,
                    )
                    is NoteLinkBlock -> NoteLinkView(block, onOpen = { onOpenNote(block.noteId) }, onRemove = { viewModel.removeBlock(block.id) })
                    is DividerBlock -> DividerView()
                    is DrawingBlock -> DrawingPreview(block, onOpen = { viewModel.openDrawing(block.id) }, modifier = Modifier.padding(vertical = 8.dp))
                }
                if (block !is TextBlock) number = 0
            }

            val tags = state.tags
            if (tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text("#$tag", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // Toccando sotto l'ultimo blocco si torna a scrivere in fondo.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .clickable(interactionSource = null, indication = null) { viewModel.focusEnd() },
            )
        }

        // Testata satinata.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(haze, RoundedCornerShape(0.dp), elevation = 0.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDone) {
                Icon(EuriseIcons.chevronLeft(), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(notebookName ?: stringResource(R.string.notes_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                Icon(EuriseIcons.undo(), contentDescription = stringResource(R.string.notes_undo), modifier = Modifier.size(20.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(EuriseIcons.more(), contentDescription = stringResource(R.string.notes_more), modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (state.pinned) R.string.notes_unpin else R.string.notes_pin)) },
                        leadingIcon = { Icon(EuriseIcons.pin(), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menu = false; viewModel.togglePinned() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_notebook_choose)) },
                        leadingIcon = { Icon(EuriseIcons.notebook(), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menu = false; pickNotebook = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_share)) },
                        leadingIcon = { Icon(EuriseIcons.share(), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menu = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_trash), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(EuriseIcons.trash(), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                        onClick = { menu = false; onTrash() },
                    )
                }
            }
            TextButton(
                onClick = { focusManager.clearFocus(); onDone() },
                modifier = Modifier.padding(start = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).height(36.dp),
            ) { Text(stringResource(R.string.notes_done), fontWeight = FontWeight.SemiBold) }
        }

        // Barra di formattazione: segue la tastiera.
        val focused = state.focusedText
        FormatToolbar(
            hazeState = haze,
            active = state.activeMarks,
            kind = focused?.kind,
            enabled = focused != null,
            actions = remember(viewModel) {
                ToolbarActions(
                    onToggle = viewModel::toggleMark,
                    onColor = viewModel::setColor,
                    onHighlight = viewModel::setHighlight,
                    onKind = viewModel::setKind,
                    onInsert = { insertSheet = true },
                    onDraw = viewModel::insertDrawing,
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )

        if (isNew) {
            LaunchedEffect(Unit) { runCatching { titleFocus.requestFocus() } }
        }

        // Disegno a schermo intero, sopra tutto.
        val drawing = state.blocks.firstOrNull { it.id == state.drawingId } as? DrawingBlock
        LaunchedEffect(drawing?.id) { if (drawing != null) focusManager.clearFocus() }
        if (drawing != null) {
            androidx.compose.runtime.key(drawing.id) {
                DrawingEditor(
                    initial = drawing,
                    onDone = viewModel::finishDrawing,
                    onDelete = { viewModel.deleteDrawing(drawing.id) },
                )
            }
        }
    }

    if (insertSheet) {
        ModalBottomSheet(onDismissRequest = { insertSheet = false }, containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 20.dp)) {
                Text(
                    stringResource(R.string.notes_insert),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(6.dp))
                InsertGrid(
                    listOf(
                        InsertOption(R.string.notes_kind_check, { EuriseIcons.checklist() }) { insertSheet = false; viewModel.insertKind(TextKind.Check) },
                        InsertOption(R.string.notes_kind_bullet, { EuriseIcons.bullets() }) { insertSheet = false; viewModel.insertKind(TextKind.Bullet) },
                        InsertOption(R.string.notes_kind_numbered, { EuriseIcons.numbers() }) { insertSheet = false; viewModel.insertKind(TextKind.Numbered) },
                        InsertOption(R.string.notes_insert_drawing, { EuriseIcons.pen() }) { insertSheet = false; viewModel.insertDrawing() },
                        InsertOption(R.string.notes_insert_table, { EuriseIcons.table() }) { insertSheet = false; viewModel.insertTable() },
                        InsertOption(R.string.notes_insert_link, { EuriseIcons.link() }) { insertSheet = false; pickLink = true },
                        InsertOption(R.string.notes_insert_divider, { EuriseIcons.divider() }) { insertSheet = false; viewModel.insertDivider() },
                    )
                )
                Text(
                    stringResource(R.string.notes_insert_soon),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
    if (pickNotebook) notebookPicker { pickNotebook = false }
    if (pickLink) linkPicker { pickLink = false }
}

/** "Modificata alle 8:31 · Casa", oppure "Modificata il 4 nov" se non è di oggi. */
@Composable
private fun metaLine(updatedAt: Long?, notebook: String?): String {
    val parts = mutableListOf<String>()
    if (updatedAt != null) {
        val time = Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault())
        parts += if (time.toLocalDate() == LocalDate.now()) {
            stringResource(R.string.notes_edited_at, time.format(DateTimeFormatter.ofPattern("H:mm")))
        } else {
            stringResource(R.string.notes_edited_on, time.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())))
        }
    } else {
        parts += stringResource(R.string.notes_new)
    }
    notebook?.let { parts += it }
    return parts.joinToString(" · ")
}

/** Foglio per scegliere la nota da collegare. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteLinkPicker(
    search: (String) -> kotlinx.coroutines.flow.Flow<List<Note>>,
    excludeId: Long?,
    onPick: (Note) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val flow = remember(query) { search(query) }
    val results by flow.collectAsState(initial = emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.notes_insert_link),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.notes_search)) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                items(results.filter { it.id != excludeId }, key = { it.id }) { note ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(note) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(
                            note.title.ifBlank { note.body.lineSequence().firstOrNull().orEmpty() }.ifBlank { stringResource(R.string.notes_untitled) },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (note.title.isNotBlank() && note.body.isNotBlank()) {
                            Text(note.body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

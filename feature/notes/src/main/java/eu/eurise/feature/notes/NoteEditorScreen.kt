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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.cloud.CloudSync
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.NotebookRepository
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.model.Note
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.notes.doc.DividerBlock
import eu.eurise.feature.notes.doc.LockedBlock
import eu.eurise.feature.notes.doc.AudioBlock
import eu.eurise.feature.notes.doc.DrawingBlock
import eu.eurise.feature.notes.doc.NoteLinkBlock
import eu.eurise.feature.notes.doc.TableBlock
import eu.eurise.feature.notes.doc.TextBlock
import eu.eurise.feature.notes.doc.TextKind
import eu.eurise.feature.notes.ui.ConflictSheet
import eu.eurise.feature.notes.ui.DividerView
import eu.eurise.feature.notes.ui.DrawingEditor
import eu.eurise.feature.notes.ui.DrawingPreview
import eu.eurise.feature.notes.ui.FormatToolbar
import eu.eurise.feature.notes.ui.InsertGrid
import eu.eurise.feature.notes.ui.InsertOption
import eu.eurise.feature.notes.ui.NoteLinkView
import eu.eurise.feature.notes.ui.NotebookPickerSheet
import eu.eurise.feature.notes.ui.SelectableBlocks
import eu.eurise.feature.notes.ui.TableBlockView
import eu.eurise.feature.notes.ui.TableActions
import eu.eurise.feature.notes.ui.BlockFrame
import eu.eurise.feature.notes.ui.BlockMenu
import eu.eurise.feature.notes.ui.TextBlockActions
import eu.eurise.feature.notes.ui.TextBlockField
import eu.eurise.feature.notes.ui.ToolbarActions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import eu.eurise.core.designsystem.component.bleed
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import eu.eurise.feature.notes.lock.rememberDeviceAuth
import eu.eurise.feature.notes.lock.LockedNoteView
import eu.eurise.feature.notes.lock.ReopenNotesView
import eu.eurise.feature.notes.lock.LockSheet
import eu.eurise.feature.notes.voice.AudioActions
import eu.eurise.feature.notes.voice.AudioBlockView
import eu.eurise.feature.notes.voice.VoiceRecordPanel
import android.widget.Toast

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
    linked: @Composable (noteId: Long, title: String) -> Unit = { _, _ -> },
    /** «Attiva il backup» dal foglio «Blocca» senza backup (tavola NoteBloccaSenzaBackup). */
    onBackup: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel = viewModel { NoteEditorViewModel(notes, notebooks, tasks, noteId, notebookId, context.applicationContext) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val books by viewModel.notebooks.collectAsStateWithLifecycle()
    // Chiusa la nota, il cloud la riceve dopo pochi secondi (se è collegato).
    val closeAndSync = { CloudSync.get(context).requestSoon(); onClose() }
    BackHandler { viewModel.close(closeAndSync) }
    NoteEditorScreen(
        state = state,
        notebookName = books.firstOrNull { it.notebook.id == state.notebookId }?.notebook?.name,
        isNew = noteId == null,
        viewModel = viewModel,
        onDone = { viewModel.close(closeAndSync) },
        onTrash = { viewModel.trash(closeAndSync) },
        onShare = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
            context.startActivity(Intent.createChooser(send, null))
        },
        onOpenNote = { target -> viewModel.close { CloudSync.get(context).requestSoon(); onOpenNote(target) } },
        onConflictResolved = { CloudSync.get(context).requestSoon() },
        onBackup = { viewModel.close { CloudSync.get(context).requestSoon(); onBackup() } },
        linked = { viewModel.currentId?.let { id -> linked(id, state.title) } },
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
    onConflictResolved: () -> Unit,
    onBackup: () -> Unit,
    linked: @Composable () -> Unit,
    notebookPicker: @Composable (onDismiss: () -> Unit) -> Unit,
    linkPicker: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var insertSheet by rememberSaveable { mutableStateOf(false) }
    var pickNotebook by rememberSaveable { mutableStateOf(false) }
    var pickLink by rememberSaveable { mutableStateOf(false) }
    var lockSheet by rememberSaveable { mutableStateOf(false) }
    var recordingVoice by rememberSaveable { mutableStateOf(false) }
    val screenContext = LocalContext.current
    val taskAdded = stringResource(R.string.notes_voice_task_done)
    val askUnlock = rememberDeviceAuth(
        stringResource(R.string.notes_unlock_prompt),
        stringResource(R.string.notes_unlock_prompt_sub),
        onSuccess = viewModel::unlock,
    )
    // Bloccare chiede l'impronta o il PIN: la chiave delle note si apre solo subito dopo (LockKeys).
    var pendingHide by remember { mutableStateOf(false) }
    val askLock = rememberDeviceAuth(
        stringResource(R.string.notes_lock_prompt),
        stringResource(R.string.notes_lock_prompt_sub),
        onSuccess = { viewModel.lock(pendingHide) },
    )
    val backupOn = remember { eu.eurise.core.cloud.backup.BackupManager.get(screenContext).state.value.configured }
    // Uscendo dall'app la nota bloccata si richiude subito.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.relock() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Aprendo una nota bloccata, la richiesta dell'impronta parte da sola.
    LaunchedEffect(state.loaded, state.hidden, state.lockReopen) {
        if (state.loaded && state.hidden && !state.lockMissingKey && !state.lockReopen) askUnlock()
    }
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    BackHandler(enabled = selecting) { selecting = false }
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = statusTop + 64.dp, bottom = 120.dp + navBottom),
        ) {
            if (state.hidden) {
                Text(
                    state.title.ifBlank { stringResource(R.string.notes_locked) },
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    metaLine(state.updatedAt, notebookName) + " · " + stringResource(R.string.notes_locked_status),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                )
                LockedNoteView(missingKey = state.lockMissingKey, onUnlock = askUnlock)
            } else if (selecting) {
                SelectableBlocks(state.title, state.blocks)
            } else {
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
                        menu = blockMenu(viewModel, block.id, index, state.blocks.lastIndex, R.string.notes_table_remove),
                        actions = TableActions(
                            onCell = { r, c, t -> viewModel.updateCell(block.id, r, c, t) },
                            onInsertRow = { viewModel.insertRow(block.id, it) },
                            onInsertColumn = { viewModel.insertColumn(block.id, it) },
                            onRemoveRow = { viewModel.removeRow(block.id, it) },
                            onRemoveColumn = { viewModel.removeColumn(block.id, it) },
                            onFocus = viewModel::onFocus,
                        ),
                    )
                    is NoteLinkBlock -> NoteLinkView(block, onOpen = { onOpenNote(block.noteId) }, onRemove = { viewModel.removeBlock(block.id) })
                    is LockedBlock -> Unit
                    is AudioBlock -> BlockFrame(
                        menu = blockMenu(viewModel, block.id, index, state.blocks.lastIndex, R.string.notes_voice_delete),
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        AudioBlockView(
                            block,
                            AudioActions(
                                onToText = { viewModel.audioToText(block.id) },
                                onTask = { task -> viewModel.audioToTask(task) { Toast.makeText(screenContext, taskAdded, Toast.LENGTH_SHORT).show() } },
                            ),
                        )
                    }
                    is DividerBlock -> BlockFrame(
                        menu = blockMenu(viewModel, block.id, index, state.blocks.lastIndex, R.string.notes_divider_delete),
                        minHeight = 38.dp,
                    ) { Box(Modifier.padding(end = 40.dp)) { DividerView() } }
                    is DrawingBlock -> BlockFrame(
                        menu = blockMenu(viewModel, block.id, index, state.blocks.lastIndex, R.string.notes_drawing_delete),
                        modifier = Modifier.padding(vertical = 8.dp),
                        extra = { close ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_drawing_edit)) },
                                leadingIcon = { Icon(EuriseIcons.pen(), contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { close(); viewModel.openDrawing(block.id) },
                            )
                        },
                    ) { DrawingPreview(block, onOpen = { viewModel.openDrawing(block.id) }) }
                }
                if (block !is TextBlock) number = 0
            }
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
            // Collegati: solo per le note già salvate (una nota nuova nasce al primo testo).
            if (state.saved) {
                Box(Modifier.bleed(22.dp).padding(top = 22.dp)) { linked() }
            }
            // Toccando sotto l'ultimo blocco si torna a scrivere in fondo.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .clickable(interactionSource = null, indication = null) { viewModel.focusEnd() },
            )
        }

        // Testata opaca: il testo che scorre non deve vedersi sotto i comandi.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDone) {
                Icon(EuriseIcons.chevronLeft(), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(notebookName ?: stringResource(R.string.notes_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            if (selecting) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(viewModel.shareText())) }) {
                    Text(stringResource(R.string.notes_copy_all), fontWeight = FontWeight.SemiBold)
                }
            } else if (!state.hidden) {
            IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                Icon(EuriseIcons.undo(), contentDescription = stringResource(R.string.notes_undo), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { if (state.locked) viewModel.relock() else { focusManager.clearFocus(); lockSheet = true } }) {
                Icon(
                    EuriseIcons.lock(),
                    contentDescription = stringResource(if (state.locked) R.string.notes_lock_close_now else R.string.notes_lock_menu),
                    tint = if (state.locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
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
                        text = { Text(stringResource(R.string.notes_select_text)) },
                        leadingIcon = { Icon(EuriseIcons.note(), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menu = false; focusManager.clearFocus(); selecting = true },
                    )
                    if (state.locked) DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_lock_remove)) },
                        leadingIcon = { Icon(EuriseIcons.lock(), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menu = false; viewModel.unlockForever() },
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
            }
            TextButton(
                onClick = { if (selecting) selecting = false else { focusManager.clearFocus(); onDone() } },
                modifier = Modifier.padding(start = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).height(36.dp),
            ) { Text(stringResource(R.string.notes_done), fontWeight = FontWeight.SemiBold) }
        }

        // Barra di formattazione: segue la tastiera.
        val focused = state.focusedText
        if (recordingVoice) VoiceRecordPanel(
            onDone = { audio -> recordingVoice = false; viewModel.insertAudio(audio) },
            onCancel = { recordingVoice = false },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) else if (!selecting && !state.hidden) FormatToolbar(
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
                        InsertOption(R.string.notes_insert_voice, { EuriseIcons.mic() }) { insertSheet = false; focusManager.clearFocus(); recordingVoice = true },
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
    state.conflict?.let { conflict ->
        ConflictSheet(conflict) { choice -> viewModel.chooseConflict(choice, onConflictResolved) }
    }
    if (pickNotebook) notebookPicker { pickNotebook = false }
    if (pickLink) linkPicker { pickLink = false }
    if (lockSheet) LockSheet(
        backupOn = backupOn,
        onLock = { hide ->
            lockSheet = false
            if (!backupOn) eu.eurise.core.cloud.lock.LockKeys.acceptNoBackup(screenContext)
            pendingHide = hide
            askLock()
        },
        onBackup = { lockSheet = false; onBackup() },
        onDismiss = { lockSheet = false },
    )
    if (state.lockReopen) ReopenNotesView(busy = state.reopenBusy, wrong = state.reopenWrong, onReopen = viewModel::reopen, onNotNow = onDone)
}

/** Menu di un blocco alla posizione [index] di [last]+1 blocchi: niente «Sposta su» in cima, niente «Sposta giù» in fondo. */
private fun blockMenu(viewModel: NoteEditorViewModel, id: Long, index: Int, last: Int, deleteLabel: Int) = BlockMenu(
    onUp = if (index > 0) ({ viewModel.moveBlock(id, -1) }) else null,
    onDown = if (index < last) ({ viewModel.moveBlock(id, 1) }) else null,
    onDuplicate = { viewModel.duplicateBlock(id) },
    onDelete = { viewModel.removeBlock(id) },
    deleteLabel = deleteLabel,
)

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
    val results by flow.collectAsStateWithLifecycle(initialValue = emptyList())
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

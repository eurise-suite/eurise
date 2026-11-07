package eu.eurise.feature.notes

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.NotebookRepository
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.extractTags
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.NotebookSummary
import eu.eurise.feature.notes.doc.Block
import eu.eurise.feature.notes.doc.DividerBlock
import eu.eurise.feature.notes.doc.DrawingBlock
import eu.eurise.feature.notes.doc.Mark
import eu.eurise.feature.notes.doc.MergeChoice
import eu.eurise.feature.notes.doc.MergePart
import eu.eurise.feature.notes.doc.MergeResult
import eu.eurise.feature.notes.doc.NoteFile
import eu.eurise.feature.notes.doc.NoteMerge
import eu.eurise.feature.notes.doc.NoteHtml
import eu.eurise.feature.notes.doc.NoteLinkBlock
import eu.eurise.feature.notes.doc.RichText
import eu.eurise.feature.notes.doc.TableBlock
import eu.eurise.feature.notes.doc.TextBlock
import eu.eurise.feature.notes.doc.TextKind
import eu.eurise.feature.notes.doc.edit
import eu.eurise.feature.notes.doc.plus
import eu.eurise.feature.notes.doc.removeMark
import eu.eurise.feature.notes.doc.substringFrom
import eu.eurise.feature.notes.doc.substringTo
import eu.eurise.feature.notes.doc.toggleMark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Richiesta di portare il cursore in un blocco; [serial] distingue due richieste uguali. */
data class FocusRequest(val blockId: Long, val cursor: Int, val serial: Long)

data class NoteEditorState(
    val loaded: Boolean = false,
    val title: String = "",
    val blocks: List<Block> = emptyList(),
    /** Selezione di ogni blocco di testo, per id. */
    val selections: Map<Long, TextRange> = emptyMap(),
    val focusedId: Long? = null,
    /** Stili da dare al prossimo testo scritto, scelti con il cursore fermo; nullo se si continua lo stile attuale. */
    val typing: Set<Mark>? = null,
    val focusRequest: FocusRequest? = null,
    val pinned: Boolean = false,
    /** Vero se la nota esiste già nel database. */
    val saved: Boolean = false,
    val notebookId: Long? = null,
    val updatedAt: Long? = null,
    val canUndo: Boolean = false,
    /** Disegno aperto a schermo intero, se c'è. */
    val drawingId: Long? = null,
    /** Nota cambiata in due posti: le scelte da fare prima di continuare. */
    val conflict: ConflictUi? = null,
) {
    val focusedText: TextBlock? get() = blocks.firstOrNull { it.id == focusedId } as? TextBlock

    /** Stili attivi dove si trova il cursore: accendono i pulsanti della barra. */
    val activeMarks: Set<Mark>
        get() {
            typing?.let { return it }
            val block = focusedText ?: return emptySet()
            val sel = selections[block.id] ?: TextRange(block.text.text.length)
            return block.text.marksIn(sel.min, sel.max)
        }

    val tags: List<String> get() = extractTags(title + "\n" + NoteHtml.plainText(blocks)).toList()
}

/**
 * Le scelte da fare su una nota cambiata sul telefono e sul cloud. Si sceglie un punto per volta:
 * prima il titolo (se serve), poi le parti del testo.
 */
data class ConflictUi(
    val result: MergeResult,
    val titleChoice: MergeChoice? = null,
    val choices: List<MergeChoice> = emptyList(),
    /** Quando è stata cambiata la versione del cloud. */
    val remoteUpdated: Long = 0,
) {
    private val titleSteps get() = if (result.titleConflict != null) 1 else 0
    val total: Int get() = titleSteps + result.conflicts.size
    val step: Int get() = (if (titleChoice != null) titleSteps else 0) + choices.size
    val choosingTitle: Boolean get() = result.titleConflict != null && titleChoice == null
    val current: MergePart.Conflict? get() = if (choosingTitle) null else result.conflicts.getOrNull(choices.size)
    val done: Boolean get() = step >= total
}

/**
 * Editor di una nota a blocchi, con salvataggio automatico: ogni modifica viene scritta in HTML dopo una
 * breve pausa, e alla chiusura in ogni caso. Una nota rimasta vuota alla chiusura viene eliminata.
 *
 * @param noteId nota da aprire; nullo per una nota nuova, che nasce nel database al primo testo.
 * @param newNotebookId taccuino in cui far nascere una nota nuova.
 */
class NoteEditorViewModel(
    private val notes: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val tasks: TaskRepository,
    noteId: Long?,
    newNotebookId: Long?,
) : ViewModel() {

    private var id: Long? = noteId
    private var nextId = 0L
    private val newId = { ++nextId }
    private var serial = 0L
    private var pendingSave: Job? = null
    /** Vero se c'è qualcosa di non ancora salvato: aprire e chiudere una nota non la cambia. */
    private var dirty = false
    private val writeLock = Mutex()

    /** Titoli delle attività collegate, per aggiornarle quando cambia il testo della voce. */
    private val taskTitles = mutableMapOf<Long, String>()

    private data class Snapshot(val title: String, val blocks: List<Block>)
    private val history = ArrayDeque<Snapshot>()
    private var lastSnapshotAt = 0L

    private val _state = MutableStateFlow(NoteEditorState())
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    val notebooks: StateFlow<List<NotebookSummary>> =
        notebookRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val note = noteId?.let { notes.get(it) }
            if (note == null) {
                id = null
                val first = TextBlock(newId(), TextKind.Paragraph, RichText())
                _state.value = NoteEditorState(loaded = true, blocks = listOf(first), notebookId = newNotebookId)
            } else {
                val blocks = load(note)
                _state.value = NoteEditorState(
                    loaded = true,
                    title = note.title,
                    blocks = blocks,
                    pinned = note.pinned,
                    saved = true,
                    notebookId = note.notebookId,
                    updatedAt = note.updatedAt,
                    conflict = if (note.hasConflict) conflictFor(note.id, note.title, blocks) else null,
                )
            }
        }
    }

    /** Prepara le scelte del conflitto confrontando telefono, cloud e ultima versione comune. */
    private suspend fun conflictFor(noteId: Long, title: String, blocks: List<Block>): ConflictUi? {
        val sync = notes.syncState(noteId) ?: return null
        val remoteText = sync.conflict ?: return null
        val uid = sync.uid.orEmpty()
        val remote = NoteFile.read(remoteText, uid, newId)
        val base = sync.base?.let { NoteFile.read(it, uid, newId) }
        val result = NoteMerge.merge(base?.title.orEmpty(), base?.blocks.orEmpty(), title, blocks, remote.title, remote.blocks)
        return ConflictUi(result, remoteUpdated = remote.updated)
    }

    /** Una scelta sul punto in conflitto mostrato; all'ultima la nota si salva con le scelte fatte. */
    fun chooseConflict(choice: MergeChoice, onResolved: () -> Unit) {
        val c = _state.value.conflict ?: return
        val next = if (c.choosingTitle) c.copy(titleChoice = choice) else c.copy(choices = c.choices + choice)
        if (!next.done) {
            _state.update { it.copy(conflict = next) }
            return
        }
        val title = next.result.title(next.titleChoice)
        val blocks = next.result.blocks(next.choices).ifEmpty { listOf(TextBlock(newId(), TextKind.Paragraph, RichText())) }
        viewModelScope.launch {
            val current = id ?: return@launch
            writeLock.withLock {
                notes.resolveConflict(current, title.trim(), NoteHtml.plainText(blocks), NoteHtml.write(blocks))
            }
            history.clear()
            _state.update {
                it.copy(title = title, blocks = blocks, conflict = null, canUndo = false, updatedAt = System.currentTimeMillis())
            }
            onResolved()
        }
    }

    /** Legge i blocchi della nota e li allinea con le attività e le note collegate. */
    private suspend fun load(note: Note): List<Block> {
        val raw = if (note.content.isNotBlank()) NoteHtml.read(note.content, newId) else NoteHtml.fromPlain(note.body, newId)
        val linked = tasks.getAll(raw.mapNotNull { (it as? TextBlock)?.taskId }).associateBy { it.id }
        linked.values.forEach { taskTitles[it.id] = it.title }
        val blocks = raw.map { block ->
            when {
                block is TextBlock && block.taskId != null -> {
                    val task = linked[block.taskId]
                    if (task == null) block.copy(taskId = null) else block.copy(checked = task.done)
                }
                block is NoteLinkBlock -> notes.get(block.noteId)?.let { target ->
                    block.copy(title = target.title.ifBlank { target.body.lineSequence().firstOrNull().orEmpty() })
                } ?: block
                else -> block
            }
        }
        return blocks.ifEmpty { listOf(TextBlock(newId(), TextKind.Paragraph, RichText())) }
    }

    // Testo

    fun onTitleChange(text: String) {
        remember(coalesce = true)
        _state.update { it.copy(title = text.replace('\n', ' ')) }
        scheduleSave()
    }

    fun onFocus(blockId: Long?) {
        _state.update { if (it.focusedId == blockId) it else it.copy(focusedId = blockId, typing = null) }
    }

    /**
     * Il campo di testo del blocco [blockId] è cambiato: nuovo testo e nuova selezione. Un a capo divide il
     * blocco (o, su una voce d'elenco vuota, chiude l'elenco).
     */
    fun onTextChange(blockId: Long, newText: String, selection: TextRange) {
        val s = _state.value
        val index = s.blocks.indexOfFirst { it.id == blockId }
        val block = s.blocks.getOrNull(index) as? TextBlock ?: return
        if (newText == block.text.text) {
            // Solo il cursore si è spostato: gli stili scelti in attesa decadono.
            _state.update { it.copy(selections = it.selections + (blockId to selection), typing = null) }
            return
        }
        remember(coalesce = true)
        val edited = block.text.edit(newText, s.typing)
        if ('\n' !in edited.text) {
            _state.update {
                it.copy(
                    blocks = it.blocks.replaceAt(index, block.copy(text = edited)),
                    selections = it.selections + (blockId to selection),
                )
            }
            scheduleSave()
            return
        }
        if (block.text.text.isEmpty() && edited.text == "\n" && block.kind != TextKind.Paragraph) {
            // Invio su una voce vuota: si esce dall'elenco.
            val plain = block.copy(kind = TextKind.Paragraph, text = RichText(), checked = false, taskId = null)
            _state.update {
                it.copy(
                    blocks = it.blocks.replaceAt(index, plain),
                    selections = it.selections + (blockId to TextRange(0)),
                    typing = null,
                )
            }
            scheduleSave()
            return
        }
        // Divide a ogni a capo: il primo pezzo resta nel blocco, gli altri diventano blocchi nuovi.
        val pieces = mutableListOf<RichText>()
        var rest = edited
        while (true) {
            val nl = rest.text.indexOf('\n')
            if (nl < 0) break
            pieces += rest.substringTo(nl)
            rest = rest.substringFrom(nl + 1)
        }
        pieces += rest
        val nextKind = when (block.kind) {
            TextKind.Heading, TextKind.Subheading -> TextKind.Paragraph
            else -> block.kind
        }
        val created = pieces.drop(1).map { TextBlock(newId(), nextKind, it) }
        val updated = listOf(block.copy(text = pieces.first())) + created
        val last = created.last()
        _state.update {
            it.copy(
                blocks = it.blocks.take(index) + updated + it.blocks.drop(index + 1),
                selections = it.selections + (blockId to TextRange(pieces.first().text.length)) + (last.id to TextRange(0)),
                focusedId = last.id,
                focusRequest = FocusRequest(last.id, 0, ++serial),
            )
        }
        scheduleSave()
    }

    /** Cancellazione con il cursore all'inizio del blocco: toglie l'elenco, oppure unisce al blocco prima. */
    fun onBackspaceAtStart(blockId: Long) {
        val s = _state.value
        val index = s.blocks.indexOfFirst { it.id == blockId }
        val block = s.blocks.getOrNull(index) as? TextBlock ?: return
        remember(coalesce = false)
        if (block.kind != TextKind.Paragraph) {
            _state.update { it.copy(blocks = it.blocks.replaceAt(index, block.copy(kind = TextKind.Paragraph, checked = false, taskId = null))) }
            scheduleSave()
            return
        }
        val previous = s.blocks.getOrNull(index - 1) ?: return
        if (previous is TextBlock) {
            val joined = previous.copy(text = previous.text + block.text)
            val cursor = previous.text.text.length
            _state.update {
                it.copy(
                    blocks = it.blocks.replaceAt(index - 1, joined).filterNot { b -> b.id == blockId },
                    selections = it.selections + (previous.id to TextRange(cursor)),
                    focusedId = previous.id,
                    focusRequest = FocusRequest(previous.id, cursor, ++serial),
                )
            }
        } else {
            _state.update { it.copy(blocks = it.blocks.filterNot { b -> b.id == previous.id }) }
        }
        scheduleSave()
    }

    /** Porta il cursore alla fine dell'ultimo blocco di testo; se in fondo c'è altro, aggiunge un paragrafo. */
    fun focusEnd() {
        val s = _state.value
        val last = s.blocks.lastOrNull()
        if (last is TextBlock) {
            val end = last.text.text.length
            _state.update {
                it.copy(
                    focusedId = last.id,
                    selections = it.selections + (last.id to TextRange(end)),
                    focusRequest = FocusRequest(last.id, end, ++serial),
                )
            }
        } else {
            val paragraph = TextBlock(newId(), TextKind.Paragraph, RichText())
            _state.update {
                it.copy(
                    blocks = it.blocks + paragraph,
                    focusedId = paragraph.id,
                    focusRequest = FocusRequest(paragraph.id, 0, ++serial),
                )
            }
        }
    }

    // Formattazione

    /** Grassetto, corsivo, sottolineato, barrato: sulla selezione, oppure per il testo che si scriverà. */
    fun toggleMark(mark: Mark) {
        val s = _state.value
        val block = s.focusedText ?: return
        val sel = s.selections[block.id] ?: return
        if (sel.collapsed) {
            val current = s.activeMarks
            _state.update { it.copy(typing = if (mark in current) current - mark else current + mark) }
            return
        }
        remember(coalesce = false)
        replaceBlock(block.copy(text = block.text.toggleMark(sel.min, sel.max, mark)))
    }

    /** Colore del testo ([tint] nullo lo toglie). */
    fun setColor(tint: String?) = setFamily(tint?.let { Mark.Color(it) }) { it is Mark.Color }

    /** Evidenziatore ([tint] nullo lo toglie). */
    fun setHighlight(tint: String?) = setFamily(tint?.let { Mark.Highlight(it) }) { it is Mark.Highlight }

    private fun setFamily(mark: Mark?, family: (Mark) -> Boolean) {
        val s = _state.value
        val block = s.focusedText ?: return
        val sel = s.selections[block.id] ?: return
        if (sel.collapsed) {
            val base = s.activeMarks.filterNot(family).toSet()
            _state.update { it.copy(typing = if (mark == null) base else base + mark) }
            return
        }
        remember(coalesce = false)
        val cleared = block.text.removeMark(sel.min, sel.max, family)
        replaceBlock(block.copy(text = if (mark == null) cleared else cleared.toggleMark(sel.min, sel.max, mark)))
    }

    /** Cambia il tipo del blocco col cursore; scegliere di nuovo lo stesso tipo torna a testo normale. */
    fun setKind(kind: TextKind) {
        val block = _state.value.focusedText ?: return
        remember(coalesce = false)
        val target = if (block.kind == kind) TextKind.Paragraph else kind
        replaceBlock(block.copy(kind = target, checked = false, taskId = if (target == TextKind.Check) block.taskId else null))
    }

    fun toggleChecked(blockId: Long) {
        val block = _state.value.blocks.firstOrNull { it.id == blockId } as? TextBlock ?: return
        remember(coalesce = false)
        val checked = !block.checked
        replaceBlock(block.copy(checked = checked))
        block.taskId?.let { taskId -> viewModelScope.launch { tasks.setDone(taskId, checked) } }
    }

    /** Aggiunge la voce anche tra le Attività, legata a questa nota. */
    fun addToTasks(blockId: Long) {
        viewModelScope.launch {
            saveNow(force = true)
            val noteId = id ?: return@launch
            val block = _state.value.blocks.firstOrNull { it.id == blockId } as? TextBlock ?: return@launch
            if (block.taskId != null || block.text.text.isBlank()) return@launch
            val taskId = tasks.add(block.text.text, noteId = noteId)
            if (block.checked) tasks.setDone(taskId, true)
            taskTitles[taskId] = block.text.text.trim()
            val current = _state.value.blocks.firstOrNull { it.id == blockId } as? TextBlock ?: return@launch
            replaceBlock(current.copy(taskId = taskId))
        }
    }

    // Inserimento

    fun insertTable() = insert(TableBlock(newId(), listOf(listOf("", ""), listOf("", ""))))

    fun insertDivider() = insert(DividerBlock(newId()))

    fun insertNoteLink(noteId: Long, title: String) = insert(NoteLinkBlock(newId(), noteId, title))

    fun insertKind(kind: TextKind) {
        val focused = _state.value.focusedText
        if (focused != null && focused.text.text.isEmpty()) setKind(kind)
        else insert(TextBlock(newId(), kind, RichText()))
    }

    /** Aggiunge un disegno dopo il blocco col cursore e lo apre a schermo intero. */
    fun insertDrawing() {
        val drawing = DrawingBlock(newId())
        insert(drawing, focus = false)
        _state.update { it.copy(drawingId = drawing.id, focusedId = null) }
    }

    fun openDrawing(blockId: Long) = _state.update { it.copy(drawingId = blockId, focusedId = null) }

    /** Chiude l'editor del disegno e salva; un disegno rimasto vuoto sparisce. */
    fun finishDrawing(drawing: DrawingBlock) {
        val existing = _state.value.blocks.firstOrNull { it.id == drawing.id } as? DrawingBlock
        _state.update { it.copy(drawingId = null) }
        when {
            existing == null -> Unit
            drawing.strokes.isEmpty() -> _state.update { s -> s.copy(blocks = s.blocks.filterNot { it.id == drawing.id }) }.also { scheduleSave() }
            drawing != existing -> {
                remember(coalesce = false)
                replaceBlock(drawing)
            }
        }
    }

    fun deleteDrawing(blockId: Long) {
        _state.update { it.copy(drawingId = null) }
        removeBlock(blockId)
    }

    /** Inserisce [block] dopo quello col cursore (o in fondo), con un paragrafo dopo se serve per continuare a scrivere. */
    private fun insert(block: Block, focus: Boolean = true) {
        remember(coalesce = false)
        val s = _state.value
        val at = s.blocks.indexOfFirst { it.id == s.focusedId }.let { if (it < 0) s.blocks.lastIndex else it }
        val focused = s.blocks.getOrNull(at)
        val replaceEmpty = focused is TextBlock && focused.text.text.isEmpty() && focused.kind == TextKind.Paragraph && block !is TextBlock
        val before = if (replaceEmpty) s.blocks.take(at) else s.blocks.take(at + 1)
        val after = s.blocks.drop(at + 1)
        val needsParagraph = block !is TextBlock && (after.firstOrNull() !is TextBlock)
        val trailing = if (needsParagraph) listOf(TextBlock(newId(), TextKind.Paragraph, RichText())) else emptyList()
        val focusTarget = (if (block is TextBlock) block else trailing.firstOrNull() ?: after.firstOrNull { it is TextBlock }) as? TextBlock
        _state.update {
            it.copy(
                blocks = before + block + trailing + after,
                focusedId = if (focus) focusTarget?.id ?: it.focusedId else it.focusedId,
                focusRequest = if (focus) focusTarget?.let { t -> FocusRequest(t.id, 0, ++serial) } ?: it.focusRequest else it.focusRequest,
            )
        }
        scheduleSave()
    }

    fun removeBlock(blockId: Long) {
        remember(coalesce = false)
        _state.update { s ->
            val left = s.blocks.filterNot { it.id == blockId }
            s.copy(blocks = left.ifEmpty { listOf(TextBlock(newId(), TextKind.Paragraph, RichText())) })
        }
        scheduleSave()
    }

    fun updateCell(blockId: Long, row: Int, column: Int, text: String) {
        val table = _state.value.blocks.firstOrNull { it.id == blockId } as? TableBlock ?: return
        remember(coalesce = true)
        val rows = table.rows.mapIndexed { r, cells -> if (r == row) cells.mapIndexed { c, v -> if (c == column) text.replace('\n', ' ') else v } else cells }
        replaceBlock(table.copy(rows = rows))
    }

    fun addRow(blockId: Long) {
        val table = _state.value.blocks.firstOrNull { it.id == blockId } as? TableBlock ?: return
        remember(coalesce = false)
        replaceBlock(table.copy(rows = table.rows + listOf(List(table.columns) { "" })))
    }

    fun addColumn(blockId: Long) {
        val table = _state.value.blocks.firstOrNull { it.id == blockId } as? TableBlock ?: return
        if (table.columns >= MAX_COLUMNS) return
        remember(coalesce = false)
        replaceBlock(table.copy(rows = table.rows.map { it + "" }))
    }

    private fun replaceBlock(block: Block) {
        _state.update { s -> s.copy(blocks = s.blocks.map { if (it.id == block.id) block else it }) }
        scheduleSave()
    }

    // Annulla

    /**
     * Ricorda lo stato prima di una modifica. Le modifiche di scrittura ravvicinate ([coalesce]) finiscono
     * in un solo passo, così "Annulla" toglie una frase e non una lettera.
     */
    private fun remember(coalesce: Boolean) {
        val now = System.currentTimeMillis()
        if (coalesce && now - lastSnapshotAt < UNDO_GROUP_MS && history.isNotEmpty()) {
            lastSnapshotAt = now
            return
        }
        lastSnapshotAt = now
        val s = _state.value
        history.addLast(Snapshot(s.title, s.blocks))
        while (history.size > MAX_UNDO) history.removeFirst()
        _state.update { it.copy(canUndo = true) }
    }

    fun undo() {
        val snap = history.removeLastOrNull() ?: return
        lastSnapshotAt = 0L
        _state.update {
            it.copy(title = snap.title, blocks = snap.blocks, typing = null, focusedId = null, canUndo = history.isNotEmpty())
        }
        scheduleSave()
    }

    // Nota

    fun togglePinned() {
        viewModelScope.launch {
            saveNow(force = true)
            val current = id ?: return@launch
            val pinned = !_state.value.pinned
            notes.setPinned(current, pinned)
            _state.update { it.copy(pinned = pinned) }
        }
    }

    fun setNotebook(notebookId: Long?) {
        _state.update { it.copy(notebookId = notebookId) }
        viewModelScope.launch {
            val current = id ?: return@launch
            notes.setNotebook(current, notebookId)
        }
    }

    /** Crea un taccuino e ci mette la nota. */
    fun createNotebook(name: String, tint: String) {
        viewModelScope.launch { setNotebook(notebookRepository.add(name, tint)) }
    }

    /** Il testo semplice della nota, per condividerla con un'altra app. */
    fun shareText(): String {
        val s = _state.value
        return listOf(s.title, NoteHtml.plainText(s.blocks)).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    /** Salva (o elimina se vuota) e poi chiama [onClosed]. */
    fun close(onClosed: () -> Unit) {
        pendingSave?.cancel()
        viewModelScope.launch {
            if (isEmpty()) {
                writeLock.withLock {
                    id?.let { notes.delete(it) }
                    id = null
                }
            } else if (dirty || id == null) {
                saveNow(force = true)
            }
            onClosed()
        }
    }

    /** Sposta la nota nel cestino e chiama [onDone]. */
    fun trash(onDone: () -> Unit) {
        pendingSave?.cancel()
        viewModelScope.launch {
            if (isEmpty()) {
                writeLock.withLock { id?.let { notes.delete(it) }; id = null }
            } else {
                saveNow(force = true)
                id?.let { notes.trash(it) }
            }
            onDone()
        }
    }

    private fun isEmpty(): Boolean {
        val s = _state.value
        return s.title.isBlank() && NoteHtml.plainText(s.blocks).isBlank() && s.blocks.none { it !is TextBlock }
    }

    private fun scheduleSave() {
        dirty = true
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            saveNow()
        }
    }

    /** Scrive il contenuto attuale; una nota vuota non viene creata, a meno di [force] (serve un id). */
    private suspend fun saveNow(force: Boolean = false) = writeLock.withLock {
        if (isEmpty() && !force) return@withLock
        val s = _state.value
        if (isEmpty() && id == null) return@withLock
        dirty = false
        id = notes.saveRich(id, s.title.trim(), NoteHtml.plainText(s.blocks), NoteHtml.write(s.blocks), s.notebookId)
        _state.update { it.copy(saved = true, updatedAt = System.currentTimeMillis()) }
        syncTaskTitles(s.blocks)
    }

    /** Il testo di una voce collegata è cambiato: lo stesso titolo va all'attività. */
    private suspend fun syncTaskTitles(blocks: List<Block>) {
        for (block in blocks) {
            if (block !is TextBlock) continue
            val taskId = block.taskId ?: continue
            val title = block.text.text.trim()
            if (title.isEmpty() || taskTitles[taskId] == title) continue
            tasks.get(taskId)?.let { tasks.update(it.copy(title = title)) }
            taskTitles[taskId] = title
        }
    }

    private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = mapIndexed { i, v -> if (i == index) value else v }

    private companion object {
        const val SAVE_DELAY_MS = 600L
        const val UNDO_GROUP_MS = 1_200L
        const val MAX_UNDO = 100
        const val MAX_COLUMNS = 5
    }
}

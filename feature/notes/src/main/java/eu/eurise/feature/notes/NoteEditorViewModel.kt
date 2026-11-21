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
import eu.eurise.feature.notes.doc.LockedBlock
import eu.eurise.feature.notes.doc.AudioBlock
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
import eu.eurise.feature.notes.lock.NoteLock
import eu.eurise.feature.notes.voice.NotesBrain
import eu.eurise.feature.notes.voice.OrderedText
import eu.eurise.feature.notes.voice.VoiceOrder
import eu.eurise.feature.notes.voice.VoicePlace
import eu.eurise.feature.notes.voice.VoicePlaces
import eu.eurise.core.cloud.lock.LockKeys
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.Context

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
    /** Nota bloccata: sul telefono e nel cloud si salva cifrata. */
    val locked: Boolean = false,
    /** Nota bloccata ma aperta con l'impronta o il PIN (si richiude dopo [NoteLock.OPEN_MS] senza tocchi). */
    val unlocked: Boolean = true,
    /** Nota bloccata con il titolo nascosto anche nell'elenco. */
    val hideTitle: Boolean = false,
    /** La chiave delle note bloccate non c'è (telefono nuovo senza backup): la nota non si apre. */
    val lockMissingKey: Boolean = false,
    /** Il blocco schermo è stato tolto: le note si riaprono con la frase del backup (tavola NoteRiapri). */
    val lockReopen: Boolean = false,
    /** «Riapri» in corso (la frase si prova con calma, qualche secondo). */
    val reopenBusy: Boolean = false,
    /** La frase o il codice scritti non aprono la copia. */
    val reopenWrong: Boolean = false,
    /** Note vocali su cui Alfred sta lavorando, per id del blocco. */
    val voiceWork: Map<Long, VoiceWork> = emptyMap(),
    /** C'è il cervello di Alfred con un modello pronto: «Riassunto» compare solo allora. */
    val brainReady: Boolean = false,
) {
    /** Vero se il contenuto non si deve vedere: nota bloccata e non ancora aperta. */
    val hidden: Boolean get() = locked && !unlocked

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

/** Che cosa sta facendo Alfred su una nota vocale. */
enum class VoiceWork { Ordering, Summarizing }

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
    private val appContext: Context,
) : ViewModel() {

    private var id: Long? = noteId

    /** Id della nota nel database; nullo finché una nota nuova non è stata salvata. */
    val currentId: Long? get() = id
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
            } else if (NoteLock.isLocked(note)) {
                val sealed = load(note).filterIsInstance<LockedBlock>().firstOrNull()
                _state.value = NoteEditorState(
                    loaded = true,
                    title = note.title,
                    blocks = listOfNotNull(sealed),
                    pinned = note.pinned,
                    saved = true,
                    notebookId = note.notebookId,
                    updatedAt = note.updatedAt,
                    locked = true,
                    unlocked = false,
                    hideTitle = note.title.isBlank(),
                )
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
        if (_state.value.hidden) return
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

    /** Mette nella nota una nota vocale appena registrata. */
    fun insertAudio(block: AudioBlock) = insert(block.copy(id = newId()))

    // Note vocali complete (tavole NoteVocaleRegistra, NoteVocaleOrdinata, NoteVocaleDove)

    private var brain: NotesBrain? = null
    private fun brain() = brain ?: NotesBrain(appContext).also { brain = it }

    /** Chiede (una volta) se il cervello di Alfred è pronto, per mostrare «Riassunto». */
    fun checkBrain() {
        if (brainChecked) return
        brainChecked = true
        viewModelScope.launch {
            val b = brain()
            val ready = b.installed && b.ready()
            _state.update { it.copy(brainReady = ready) }
        }
    }
    private var brainChecked = false

    /**
     * Una nota vocale finita: l'audio va nel posto scelto, il blocco entra nella nota e sotto di lui il testo a
     * paragrafi; poi Alfred, se c'è, lo rimette in ordine (punteggiatura, titolo, titoletti).
     */
    fun finishVoice(recorded: AudioBlock, place: VoicePlace) {
        viewModelScope.launch {
            val block = VoicePlaces.settle(appContext, recorded, place).copy(id = newId())
            insert(block, focus = false)
            reorder(block.id)
        }
    }

    /** «Riordina di nuovo»: rifà il testo sotto la registrazione, senza toccarlo se nel frattempo è stato cambiato. */
    fun reorder(blockId: Long) {
        val audio = audioBlock(blockId) ?: return
        if (audio.transcript.isBlank() || blockId in _state.value.voiceWork) return
        viewModelScope.launch {
            val local = VoiceOrder.local(audio.transcript)
            // Il testo alle pause entra subito: se si chiude la nota prima di Alfred, resta questo.
            if (audio.ordered.isBlank()) applyOrdered(blockId, local)
            val b = brain()
            val ready = b.installed && b.ready()
            _state.update { it.copy(brainReady = ready) }
            if (!ready) {
                if (audio.ordered.isNotBlank()) applyOrdered(blockId, local)
                fallbackTitle()
                return@launch
            }
            setWork(blockId, VoiceWork.Ordering)
            try {
                val paragraphs = local.sections.flatMap { it.paragraphs }
                val cleaned = VoiceOrder.groups(paragraphs).flatMap { group ->
                    VoiceOrder.acceptClean(group, b.ask(VoiceOrder.cleanPrompt(group))) ?: group
                }
                val ordered = VoiceOrder.structure(cleaned, b.ask(VoiceOrder.structurePrompt(cleaned), timeoutMs = 60_000))
                applyOrdered(blockId, ordered)
                if (_state.value.title.isBlank() && ordered.title.isNotBlank()) onTitleChange(ordered.title)
                fallbackTitle()
            } finally {
                setWork(blockId, null)
            }
        }
    }

    /** «Riassunto»: tre righe da Alfred, sopra il testo. Le note lunghe si riassumono a pezzi e poi tutte insieme. */
    fun summarize(blockId: Long) {
        val audio = audioBlock(blockId) ?: return
        if (blockId in _state.value.voiceWork) return
        val text = audio.ordered.lines().joinToString("\n") { it.removePrefix(OrderedText.HEADING) }.ifBlank { audio.transcript }
        if (text.isBlank()) return
        viewModelScope.launch {
            val b = brain()
            if (!(b.installed && b.ready())) { _state.update { it.copy(brainReady = false) }; return@launch }
            setWork(blockId, VoiceWork.Summarizing)
            try {
                val chunks = VoiceOrder.summaryChunks(text)
                var lines = chunks.flatMap { VoiceOrder.summaryLines(b.ask(VoiceOrder.summaryPrompt(it))) }
                if (chunks.size > 1 && lines.size > 4) {
                    lines = VoiceOrder.summaryLines(b.ask(VoiceOrder.summaryPrompt(lines.joinToString("\n")))).ifEmpty { lines }
                }
                if (lines.isEmpty()) return@launch
                val current = audioBlock(blockId) ?: return@launch
                remember(coalesce = false)
                replaceBlock(current.copy(summary = lines.joinToString("\n")))
            } finally {
                setWork(blockId, null)
            }
        }
    }

    /** Toglie il riassunto (la ✕ sul riquadro). */
    fun clearSummary(blockId: Long) {
        val audio = audioBlock(blockId) ?: return
        remember(coalesce = false)
        replaceBlock(audio.copy(summary = ""))
    }

    private fun audioBlock(id: Long) = _state.value.blocks.firstOrNull { it.id == id } as? AudioBlock

    private fun setWork(blockId: Long, work: VoiceWork?) = _state.update {
        it.copy(voiceWork = if (work == null) it.voiceWork - blockId else it.voiceWork + (blockId to work))
    }

    /** Una nota vocale nuova senza titolo prende «Nota vocale del 21 novembre». */
    private fun fallbackTitle() {
        if (_state.value.title.isNotBlank()) return
        val day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.getDefault()))
        onTitleChange(appContext.getString(R.string.notes_voice_title_fallback, day))
    }

    /** I blocchi di testo di un testo ordinato: titoletti e paragrafi. */
    private fun blocksOf(ordered: OrderedText): List<TextBlock> = ordered.sections.flatMap { s ->
        listOfNotNull(s.heading?.let { TextBlock(newId(), TextKind.Heading, RichText(it)) }) +
            s.paragraphs.map { TextBlock(newId(), TextKind.Paragraph, RichText(it)) }
    }

    /**
     * Mette [ordered] sotto la nota vocale. Se lì c'è ancora, intatto, il testo messo l'altra volta, lo sostituisce;
     * se è stato cambiato a mano, il testo nuovo va subito sotto la registrazione e quello vecchio resta.
     */
    private fun applyOrdered(blockId: Long, ordered: OrderedText) {
        if (ordered.isEmpty) return
        val blocks = _state.value.blocks
        val index = blocks.indexOfFirst { it.id == blockId }
        val audio = blocks.getOrNull(index) as? AudioBlock ?: return
        val previous = OrderedText.parse(audio.ordered).sections.flatMap { s ->
            listOfNotNull(s.heading?.let { TextKind.Heading to it }) + s.paragraphs.map { TextKind.Paragraph to it }
        }
        val following = blocks.drop(index + 1).take(previous.size)
        val untouched = previous.isNotEmpty() && following.size == previous.size && following.zip(previous).all { (b, p) ->
            b is TextBlock && b.kind == p.first && b.text.text.trim() == p.second.trim()
        }
        remember(coalesce = false)
        val fresh = blocksOf(ordered)
        val rest = blocks.drop(index + 1 + if (untouched) previous.size else 0)
        val updated = audio.copy(ordered = ordered.encode())
        _state.update { it.copy(blocks = blocks.take(index) + updated + fresh + rest) }
        scheduleSave()
    }

    /** Copia la trascrizione di una nota vocale in un paragrafo subito sotto. */
    fun audioToText(blockId: Long) {
        val blocks = _state.value.blocks
        val index = blocks.indexOfFirst { it.id == blockId }
        val audio = blocks.getOrNull(index) as? AudioBlock ?: return
        if (audio.transcript.isBlank()) return
        remember(coalesce = false)
        val paragraph = TextBlock(newId(), TextKind.Paragraph, RichText(VoiceOrder.pieces(audio.transcript).joinToString(" ")))
        _state.update { it.copy(blocks = blocks.take(index + 1) + paragraph + blocks.drop(index + 1)) }
        scheduleSave()
    }

    /** Crea un'attività dalla cosa da fare trovata nella trascrizione, legata a questa nota. */
    fun audioToTask(text: String, onCreated: () -> Unit) {
        viewModelScope.launch {
            saveNow(force = true)
            val noteId = id ?: return@launch
            tasks.add(text, noteId = noteId)
            onCreated()
        }
    }

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

    /** Inserisce una riga vuota alla posizione [at] (0 = in cima, sotto l'intestazione resta la riga 1). */
    fun insertRow(blockId: Long, at: Int) {
        val table = _state.value.blocks.firstOrNull { it.id == blockId } as? TableBlock ?: return
        remember(coalesce = false)
        val i = at.coerceIn(0, table.rows.size)
        replaceBlock(table.copy(rows = table.rows.take(i) + listOf(List(table.columns) { "" }) + table.rows.drop(i)))
    }

    /** Inserisce una colonna vuota alla posizione [at]. */
    fun insertColumn(blockId: Long, at: Int) {
        val table = _state.value.blocks.firstOrNull { it.id == blockId } as? TableBlock ?: return
        if (table.columns >= MAX_COLUMNS) return
        remember(coalesce = false)
        val i = at.coerceIn(0, table.columns)
        replaceBlock(table.copy(rows = table.rows.map { it.take(i) + "" + it.drop(i) }))
    }

    /** Toglie la riga [row]; l'ultima riga rimasta toglie la tabella. */
    fun removeRow(blockId: Long, row: Int) {
        val table = _state.value.blocks.firstOrNull { it.id == blockId } as? TableBlock ?: return
        if (table.rows.size <= 1) return removeBlock(blockId)
        remember(coalesce = false)
        replaceBlock(table.copy(rows = table.rows.filterIndexed { r, _ -> r != row }))
    }

    /** Toglie la colonna [column]; l'ultima colonna rimasta toglie la tabella. */
    fun removeColumn(blockId: Long, column: Int) {
        val table = _state.value.blocks.firstOrNull { it.id == blockId } as? TableBlock ?: return
        if (table.columns <= 1) return removeBlock(blockId)
        remember(coalesce = false)
        replaceBlock(table.copy(rows = table.rows.map { cells -> cells.filterIndexed { c, _ -> c != column } }))
    }

    /** Sposta un blocco di un posto in su ([delta] = -1) o in giù (+1). */
    fun moveBlock(blockId: Long, delta: Int) {
        val blocks = _state.value.blocks
        val from = blocks.indexOfFirst { it.id == blockId }
        val to = from + delta
        if (from < 0 || to !in blocks.indices) return
        remember(coalesce = false)
        val list = blocks.toMutableList()
        list.add(to, list.removeAt(from))
        _state.update { it.copy(blocks = list) }
        scheduleSave()
    }

    /** Duplica un blocco subito sotto di sé. */
    fun duplicateBlock(blockId: Long) {
        val blocks = _state.value.blocks
        val index = blocks.indexOfFirst { it.id == blockId }
        val copy = when (val b = blocks.getOrNull(index)) {
            is TableBlock -> b.copy(id = newId())
            is DrawingBlock -> b.copy(id = newId())
            is NoteLinkBlock -> b.copy(id = newId())
            is DividerBlock -> b.copy(id = newId())
            is LockedBlock -> return
            is AudioBlock -> b.copy(id = newId())
            is TextBlock -> b.copy(id = newId(), taskId = null)
            null -> return
        }
        remember(coalesce = false)
        _state.update { it.copy(blocks = blocks.take(index + 1) + copy + blocks.drop(index + 1)) }
        scheduleSave()
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

    // Nota bloccata

    private var relockJob: Job? = null

    /** Rimette il conto alla rovescia dei 5 minuti dopo i quali la nota si richiude. */
    private fun touchLock() {
        if (!_state.value.locked || !_state.value.unlocked) return
        relockJob?.cancel()
        relockJob = viewModelScope.launch {
            delay(NoteLock.OPEN_MS)
            relock()
        }
    }

    /**
     * Blocca la nota (subito dopo l'impronta o il PIN): da ora si salva cifrata. [hideTitle] nasconde anche il titolo
     * nell'elenco. Se la chiave non si apre, la nota resta com'era.
     */
    fun lock(hideTitle: Boolean) {
        viewModelScope.launch {
            when (val opened = withContext(Dispatchers.Default) { LockKeys.unlock(appContext, create = true) }) {
                LockKeys.Opened.Ok -> Unit
                is LockKeys.Opened.Invalidated -> { _state.update { it.copy(lockReopen = opened.canReopen) }; return@launch }
                else -> return@launch
            }
            _state.update { it.copy(locked = true, unlocked = true, hideTitle = hideTitle) }
            dirty = true
            saveNow(force = true)
            touchLock()
        }
    }

    /** Riapre le note con la frase o il codice del backup (tavola NoteRiapri); poi serve di nuovo l'impronta o il PIN. */
    fun reopen(secret: String) {
        if (_state.value.reopenBusy) return
        _state.update { it.copy(reopenBusy = true, reopenWrong = false) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { LockKeys.reopen(appContext, secret) }
            _state.update { it.copy(reopenBusy = false, reopenWrong = !ok, lockReopen = !ok, lockMissingKey = false) }
        }
    }

    /** Apre la nota dopo l'impronta o il PIN. */
    fun unlock() {
        val s = _state.value
        val sealed = s.blocks.firstOrNull() as? LockedBlock ?: return
        viewModelScope.launch {
            when (val key = withContext(Dispatchers.Default) { LockKeys.unlock(appContext) }) {
                LockKeys.Opened.Ok -> Unit
                LockKeys.Opened.NeedsAuth -> return@launch
                is LockKeys.Opened.Invalidated -> {
                    _state.update { it.copy(lockReopen = key.canReopen, lockMissingKey = !key.canReopen) }
                    return@launch
                }
                LockKeys.Opened.Missing -> { _state.update { it.copy(lockMissingKey = true) }; return@launch }
            }
            val opened = withContext(Dispatchers.Default) { NoteLock.open(sealed, newId) }
            if (opened == null) {
                _state.update { it.copy(lockMissingKey = true) }
                return@launch
            }
            val (title, blocks) = opened
            _state.update {
                it.copy(
                    title = title.ifBlank { it.title },
                    blocks = blocks.ifEmpty { listOf(TextBlock(newId(), TextKind.Paragraph, RichText())) },
                    unlocked = true,
                    lockMissingKey = false,
                )
            }
            touchLock()
        }
    }

    /** Richiude la nota (dopo 5 minuti, o uscendo dall'app): salva cifrata e nasconde il testo. */
    fun relock() {
        val s = _state.value
        if (!s.locked || !s.unlocked) return
        relockJob?.cancel()
        viewModelScope.launch {
            pendingSave?.cancel()
            dirty = true
            saveNow(force = true)
            val sealed = withContext(Dispatchers.Default) { NoteLock.seal(_state.value.title.trim(), _state.value.blocks) }
                ?: id?.let { notes.get(it) }?.let { load(it).filterIsInstance<LockedBlock>().firstOrNull() }
            // La chiave esce dalla memoria insieme al testo.
            LockKeys.endSession()
            history.clear()
            _state.update {
                it.copy(blocks = listOfNotNull(sealed?.copy(id = newId())), unlocked = false, focusedId = null, canUndo = false, drawingId = null)
            }
        }
    }

    /** Toglie il blocco: la nota torna normale, leggibile anche da ricerca e cloud. */
    fun unlockForever() {
        val s = _state.value
        if (!s.locked || !s.unlocked) return
        relockJob?.cancel()
        _state.update { it.copy(locked = false, hideTitle = false) }
        dirty = true
        viewModelScope.launch { saveNow(force = true) }
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
        if (s.locked) return false
        return s.title.isBlank() && NoteHtml.plainText(s.blocks).isBlank() && s.blocks.none { it !is TextBlock }
    }

    private fun scheduleSave() {
        dirty = true
        touchLock()
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
        if (s.hidden) return@withLock // ancora cifrata: niente da riscrivere
        dirty = false
        id = if (s.locked) {
            // Senza la chiave aperta non si scrive nulla: mai il testo in chiaro di una nota bloccata.
            val sealed = withContext(Dispatchers.Default) { NoteLock.seal(s.title.trim(), s.blocks) } ?: run { dirty = true; return@withLock }
            notes.saveRich(id, if (s.hideTitle) "" else s.title.trim(), "", NoteHtml.write(listOf(sealed)), s.notebookId)
        } else {
            notes.saveRich(id, s.title.trim(), NoteHtml.plainText(s.blocks), NoteHtml.write(s.blocks), s.notebookId)
        }
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

    override fun onCleared() {
        brain?.close()
        super.onCleared()
    }

    private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = mapIndexed { i, v -> if (i == index) value else v }

    private companion object {
        const val SAVE_DELAY_MS = 600L
        const val UNDO_GROUP_MS = 1_200L
        const val MAX_UNDO = 100
        const val MAX_COLUMNS = 5
    }
}

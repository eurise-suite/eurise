package eu.eurise.feature.notes.meeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.meetings.MeetingActions
import eu.eurise.core.data.meetings.MeetingStore
import eu.eurise.core.data.model.LinkRef
import eu.eurise.feature.notes.doc.NoteHtml
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MeetingState(
    val loaded: Boolean = false,
    /** L'evento non esiste più o manca il permesso del calendario. */
    val missing: Boolean = false,
    val title: String = "",
    val event: CalendarEvent? = null,
    val content: MeetingContent = MeetingContent(),
    val noteId: Long? = null,
    val closed: Boolean = false,
    /** Azioni trovate negli appunti e non ancora diventate attività. */
    val pending: List<MeetingActions.Action> = emptyList(),
)

/**
 * La riunione di un'occorrenza di un evento. La nota nasce alla prima modifica (aprire e chiudere non lascia
 * note vuote), è collegata all'evento e si salva da sola mentre si scrive.
 */
class MeetingViewModel(
    private val data: EuriseData,
    private val store: MeetingStore,
    private val labels: MeetingLabels,
    private val eventId: Long,
    private val beginMillis: Long,
    private val taskNote: (String) -> String,
) : ViewModel() {

    private val _state = MutableStateFlow(MeetingState())
    val state: StateFlow<MeetingState> = _state.asStateFlow()
    private var nextId = 0L
    private val ids = { ++nextId }
    private val saving = Mutex()
    private var pendingSave: Job? = null
    private var dirty = false

    init {
        viewModelScope.launch {
            val event = data.calendar.getOccurrence(eventId, beginMillis)
            if (event == null) {
                _state.value = MeetingState(loaded = true, missing = true)
                return@launch
            }
            val noteId = store.noteFor(eventId, beginMillis)?.takeIf { data.notes.get(it)?.trashedAt == null }
            val content = noteId?.let { id -> data.notes.get(id)?.let { readNote(it.content, it.body) } } ?: fresh(event)
            _state.value = MeetingState(
                loaded = true, title = event.title, event = event, content = content, noteId = noteId,
                closed = store.isClosed(eventId, beginMillis),
            ).withPending()
        }
    }

    private fun readNote(html: String, plain: String): MeetingContent {
        val blocks = if (html.isNotBlank()) NoteHtml.read(html, ids) else NoteHtml.fromPlain(plain, ids)
        return MeetingDoc.read(blocks, labels)
    }

    private suspend fun fresh(event: CalendarEvent): MeetingContent {
        val people = data.calendar.attendees(eventId).filterNot { it.self }.map { MeetingPerson(it.name, present = false) }
        // Un ordine del giorno scritto come elenco nella descrizione dell'evento diventa la lista da spuntare.
        val agenda = event.description.lines().map { it.trim() }
            .filter { BULLET.containsMatchIn(it) }
            .map { AgendaItem(it.replace(BULLET, "").trim(), false) }
            .filter { it.text.isNotEmpty() }
        return MeetingContent(intro = intro(event), people = people, agenda = agenda)
    }

    private fun intro(e: CalendarEvent): String =
        listOf(e.start.format(DAY).replaceFirstChar { it.uppercase() }, "${e.start.format(TIME)}–${e.end.format(TIME)}", e.location.trim())
            .filter { it.isNotEmpty() }.joinToString(" · ")

    private fun MeetingState.withPending(): MeetingState {
        val made = content.actions.map { it.title.lowercase() }.toSet()
        return copy(pending = MeetingActions.extract(content.notes, LocalDateTime.now()).filter { it.title.lowercase() !in made })
    }

    private fun change(transform: (MeetingContent) -> MeetingContent) {
        _state.update { it.copy(content = transform(it.content)).withPending() }
        dirty = true
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch { delay(SAVE_DELAY); save() }
    }

    fun togglePerson(name: String) = change { c ->
        c.copy(people = c.people.map { if (it.name == name) it.copy(present = !it.present) else it })
    }

    fun addPerson(name: String) {
        val clean = name.trim().removePrefix("@").trim()
        if (clean.isEmpty() || _state.value.content.people.any { it.name.equals(clean, ignoreCase = true) }) return
        change { it.copy(people = it.people + MeetingPerson(clean, present = true)) }
    }

    fun removePerson(name: String) = change { c -> c.copy(people = c.people.filterNot { it.name == name }) }

    fun toggleAgenda(index: Int) = change { c ->
        c.copy(agenda = c.agenda.mapIndexed { i, a -> if (i == index) a.copy(done = !a.done) else a })
    }

    fun addAgenda(text: String) {
        if (text.isBlank()) return
        change { it.copy(agenda = it.agenda + AgendaItem(text.trim(), false)) }
    }

    fun removeAgenda(index: Int) = change { c -> c.copy(agenda = c.agenda.filterIndexed { i, _ -> i != index }) }

    fun onNotes(text: String) = change { it.copy(notes = text) }

    /**
     * Chiude la riunione: le azioni scelte diventano attività legate alla nota e all'evento, e compaiono nella
     * sezione Da fare. Si può richiamare: aggiunge solo le azioni nuove.
     */
    fun close(chosen: List<MeetingActions.Action>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val noteId = ensureNote()
            val event = LinkRef.event(eventId)
            val made = chosen.map { action ->
                val id = data.tasks.add(
                    title = action.title, due = action.due, time = action.time,
                    notes = taskNote(_state.value.title), noteId = noteId,
                )
                data.links.link(LinkRef.task(id), event)
                MeetingAction(action.title, id, false)
            }
            store.setClosed(eventId, beginMillis, true)
            _state.update { it.copy(content = it.content.copy(actions = it.content.actions + made), closed = true).withPending() }
            dirty = true
            save()
            onDone()
        }
    }

    fun summary(): String = MeetingDoc.summary(_state.value.title, _state.value.content, labels)

    /** Salva subito (all'uscita dalla schermata). */
    fun flush(then: () -> Unit) {
        pendingSave?.cancel()
        viewModelScope.launch {
            withContext(NonCancellable) { save() }
            then()
        }
    }

    /** L'id della nota, creandola se serve; da usare per aprirla nell'editor delle Note. */
    fun noteId(then: (Long) -> Unit) {
        pendingSave?.cancel()
        viewModelScope.launch { then(withContext(NonCancellable) { ensureNote() }) }
    }

    private suspend fun ensureNote(): Long = saving.withLock {
        _state.value.noteId ?: run {
            val id = write(null)
            store.setNote(eventId, beginMillis, id)
            data.links.link(LinkRef.note(id), LinkRef.event(eventId))
            _state.update { it.copy(noteId = id) }
            dirty = false
            id
        }
    }

    private suspend fun save() {
        if (!dirty || !_state.value.loaded || _state.value.missing) return
        if (_state.value.noteId == null) { ensureNote(); return }
        saving.withLock {
            write(_state.value.noteId)
            dirty = false
        }
    }

    private suspend fun write(id: Long?): Long {
        val s = _state.value
        val blocks = MeetingDoc.write(s.content, labels, ids)
        return data.notes.saveRich(id, s.title.ifBlank { labels.notes }, NoteHtml.plainText(blocks), NoteHtml.write(blocks))
    }

    override fun onCleared() {
        // Ultimo salvataggio se si esce senza passare da flush (per esempio con il gesto indietro di sistema).
        // Fuori dal thread principale e fuori dal ViewModel, che sta per sparire.
        if (dirty) kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { runCatching { save() } }
    }

    private companion object {
        const val SAVE_DELAY = 700L
        val BULLET = Regex("""^(?:[-*•–]|\d+[.)])\s+""")
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

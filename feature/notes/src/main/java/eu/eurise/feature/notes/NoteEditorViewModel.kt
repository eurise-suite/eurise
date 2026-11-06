package eu.eurise.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.NoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NoteEditorState(
    val loaded: Boolean = false,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    /** Vero se la nota esiste già nel database (serve per mostrare "Fissa" ed "Elimina"). */
    val saved: Boolean = false,
)

/**
 * Editor di una nota con salvataggio automatico: ogni modifica viene scritta dopo una breve pausa,
 * e alla chiusura in ogni caso. Una nota rimasta vuota alla chiusura viene eliminata.
 *
 * @param noteId nota da aprire; nullo per una nota nuova, che nasce nel database al primo testo.
 */
class NoteEditorViewModel(
    private val repository: NoteRepository,
    noteId: Long?,
) : ViewModel() {

    private var id: Long? = noteId
    private var pendingSave: Job? = null
    private val writeLock = Mutex()

    private val _state = MutableStateFlow(NoteEditorState(loaded = noteId == null))
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    init {
        if (noteId != null) {
            viewModelScope.launch {
                val note = repository.get(noteId)
                _state.value = if (note == null) {
                    id = null
                    NoteEditorState(loaded = true)
                } else {
                    NoteEditorState(true, note.title, note.body, note.pinned, saved = true)
                }
            }
        }
    }

    fun onTitleChange(text: String) {
        _state.update { it.copy(title = text) }
        scheduleSave()
    }

    fun onBodyChange(text: String) {
        _state.update { it.copy(body = text) }
        scheduleSave()
    }

    fun togglePinned() {
        viewModelScope.launch {
            saveNow()
            val current = id ?: return@launch
            val pinned = !_state.value.pinned
            repository.setPinned(current, pinned)
            _state.update { it.copy(pinned = pinned) }
        }
    }

    /** Salva (o elimina se vuota) e poi chiama [onClosed]. */
    fun close(onClosed: () -> Unit) {
        pendingSave?.cancel()
        viewModelScope.launch {
            writeLock.withLock {
                val s = _state.value
                val current = id
                if (s.title.isBlank() && s.body.isBlank()) {
                    if (current != null) repository.delete(current)
                    id = null
                } else {
                    id = repository.save(current, s.title, s.body)
                }
            }
            onClosed()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        pendingSave?.cancel()
        viewModelScope.launch {
            writeLock.withLock {
                id?.let { repository.delete(it) }
                id = null
            }
            onDeleted()
        }
    }

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            saveNow()
        }
    }

    /** Scrive il contenuto attuale; una nota vuota non viene creata. */
    private suspend fun saveNow() = writeLock.withLock {
        val s = _state.value
        if (s.title.isBlank() && s.body.isBlank()) return@withLock
        id = repository.save(id, s.title, s.body)
        _state.update { it.copy(saved = true) }
    }

    private companion object {
        const val SAVE_DELAY_MS = 600L
    }
}

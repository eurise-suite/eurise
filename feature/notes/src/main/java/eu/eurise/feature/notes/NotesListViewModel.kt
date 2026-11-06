package eu.eurise.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.model.Note
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Risultato della lista: [notes] è nullo finché il database non ha risposto la prima volta. */
data class NotesListState(val query: String = "", val notes: List<Note>? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesListViewModel(repository: NoteRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    /** Testo digitato nella ricerca: aggiornato subito, mentre i risultati arrivano un attimo dopo. */
    val queryText: StateFlow<String> = query.asStateFlow()

    val state: StateFlow<NotesListState> = query
        .flatMapLatest { q -> repository.observe(q).map { NotesListState(q, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesListState())

    fun onQueryChange(text: String) {
        query.value = text
    }
}

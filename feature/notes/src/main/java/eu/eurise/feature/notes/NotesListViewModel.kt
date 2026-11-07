package eu.eurise.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.NotebookRepository
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.NotebookSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Quali note mostra l'elenco. */
sealed interface NotesFilter {
    data object All : NotesFilter
    data object Pinned : NotesFilter
    data class Notebook(val id: Long) : NotesFilter
    data class Tag(val tag: String) : NotesFilter
}

/** Richiesta di filtro arrivata da fuori (Organizza); [serial] distingue due richieste uguali. */
data class NotesFilterRequest(val filter: NotesFilter, val serial: Int)

/** Risultato dell'elenco: [notes] è nullo finché il database non ha risposto la prima volta. */
data class NotesListState(
    val query: String = "",
    val filter: NotesFilter = NotesFilter.All,
    val notes: List<Note>? = null,
    val notebooks: List<NotebookSummary> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesListViewModel(
    private val repository: NoteRepository,
    notebooks: NotebookRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<NotesFilter>(NotesFilter.All)

    /** Testo digitato nella ricerca: aggiornato subito, mentre i risultati arrivano un attimo dopo. */
    val queryText: StateFlow<String> = query.asStateFlow()

    val state: StateFlow<NotesListState> =
        combine(query, filter) { q, f -> q to f }
            .flatMapLatest { (q, f) ->
                val source = when (f) {
                    is NotesFilter.Notebook -> repository.observe(q, notebookId = f.id)
                    is NotesFilter.Tag -> repository.observe(q, tag = f.tag)
                    NotesFilter.Pinned -> repository.observe(q).map { list -> list.filter { it.pinned } }
                    NotesFilter.All -> repository.observe(q)
                }
                combine(source, notebooks.observe()) { list, books -> NotesListState(q, f, list, books) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesListState())

    init {
        // Le note nel cestino da più di 30 giorni se ne vanno da sole.
        viewModelScope.launch { repository.purgeTrash() }
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun setFilter(value: NotesFilter) {
        filter.value = value
    }
}

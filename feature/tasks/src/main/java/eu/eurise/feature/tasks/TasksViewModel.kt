package eu.eurise.feature.tasks

import eu.eurise.core.data.model.QuickAdd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.modes.Modes
import eu.eurise.core.data.model.StepProgress
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.data.model.TaskListSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Che cosa mostra l'elenco: una delle viste o un elenco preciso. */
sealed interface TasksFilter {
    data object Today : TasksFilter
    data object Scheduled : TasksFilter
    data object Important : TasksFilter
    data object All : TasksFilter
    data class OfList(val id: Long) : TasksFilter
}

/** [tasks] è nullo finché il database non ha risposto la prima volta. */
data class TasksState(
    val filter: TasksFilter = TasksFilter.Today,
    val query: String = "",
    val tasks: List<Task>? = null,
    val lists: List<TaskListSummary> = emptyList(),
    val steps: Map<Long, StepProgress> = emptyMap(),
    val showDone: Boolean = true,
    val inCalendar: Boolean = true,
)

private data class Inputs(val filter: TasksFilter, val query: String, val tasks: List<Task>, val hidden: Set<Long> = emptySet())

class TasksViewModel(
    private val repository: TaskRepository,
    private val trash: eu.eurise.core.data.trash.TrashRepository? = null,
    private val links: eu.eurise.core.data.LinkRepository? = null,
    /** Il nome dell'account sul server, per «preso da» negli elenchi condivisi. */
    private val myName: () -> String? = { null },
) : ViewModel() {

    private val filter = MutableStateFlow<TasksFilter>(TasksFilter.Today)
    private val query = MutableStateFlow("")
    private val settings = repository.settings

    val state: StateFlow<TasksState> =
        combine(
            combine(filter, query, repository.observeAll(), Modes.state) { f, q, t, m ->
                // Gli elenchi nascosti dalla modalità accesa spariscono, tranne quello aperto di proposito.
                val hidden = m.hiddenLists - ((f as? TasksFilter.OfList)?.id ?: -1L)
                Inputs(f, q, if (hidden.isEmpty()) t else t.filter { it.listId !in hidden }, hidden)
            },
            repository.observeLists(),
            repository.observeStepProgress(),
            settings.showDone,
            settings.showInCalendar,
        ) { i, lists, steps, showDone, inCalendar ->
            TasksState(i.filter, i.query, i.tasks, lists.filter { it.list.id !in i.hidden }, steps, showDone, inCalendar)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksState())

    fun setFilter(value: TasksFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    /**
     * Spunta. Negli elenchi condivisi (tavola CollElenchi) il primo tocco prende la cosa da fare, il secondo la segna
     * fatta, con il nome di chi l'ha fatto.
     */
    fun setDone(task: Task, done: Boolean) {
        val shared = task.parentId == null && state.value.lists.firstOrNull { it.list.id == task.listId }?.list?.shared == true
        val me = myName()
        viewModelScope.launch {
            if (shared && me != null) repository.takeOrDone(task.id, me) else repository.setDone(task.id, done)
        }
    }

    /**
     * L'elenco «Con [person]» in cui stanno le cose affidate a quella persona (creato la prima volta): si condivide solo
     * quello, così vede solo ciò che le affidi. [then] riceve l'id.
     */
    internal fun personList(listName: String, then: (Long) -> Unit) {
        viewModelScope.launch {
            then(repository.personList(listName))
        }
    }

    /** Aggiunge l'attività letta dall'aggiunta rapida; [then] riceve l'id (per aprirne la scheda). */
    internal fun add(
        parsed: QuickAdd.Result,
        fallbackList: Long,
        people: List<eu.eurise.core.data.model.LinkRef> = emptyList(),
        then: (Long) -> Unit = {},
    ) {
        if (parsed.title.isBlank()) return
        val listId = state.value.lists.firstOrNull { it.list.name == parsed.listName }?.list?.id ?: fallbackList
        viewModelScope.launch {
            val id = repository.add(
                parsed.title, due = parsed.due, listId = listId, time = parsed.time,
                repeat = parsed.repeat, priority = parsed.priority,
                remindBefore = if (parsed.time != null) 0 else null,
            )
            // Le persone scelte con «@» si ritrovano nella loro scheda.
            links?.let { l -> people.forEach { runCatching { l.link(eu.eurise.core.data.model.LinkRef.task(id), it) } } }
            then(id)
        }
    }

    /** Dalla scheda: prima salva le modifiche in corso, poi spunta (o toglie la spunta). */
    fun saveAndSetDone(task: Task, done: Boolean) {
        viewModelScope.launch {
            if (task.title.isNotBlank()) repository.update(task)
            repository.setDone(task.id, done)
        }
    }

    fun update(task: Task) {
        if (task.title.isBlank()) return
        viewModelScope.launch { repository.update(task) }
    }

    /** Un'attività va nel cestino unico (30 giorni, torna con i collegamenti); un passo si toglie e basta. */
    fun delete(task: Task) {
        viewModelScope.launch {
            val bin = trash
            if (task.parentId == null && bin != null) bin.trashTask(task.id) else repository.delete(task.id)
        }
    }

    fun addStep(parentId: Long, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.addStep(parentId, title) }
    }

    fun clearCompleted() {
        viewModelScope.launch { repository.deleteCompleted() }
    }

    fun addList(name: String, tint: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addList(name, tint) }
    }

    fun updateList(list: TaskList) {
        if (list.name.isBlank()) return
        viewModelScope.launch { repository.updateList(list) }
    }

    fun deleteList(list: TaskList) {
        if (filter.value == TasksFilter.OfList(list.id)) filter.value = TasksFilter.All
        viewModelScope.launch { repository.deleteList(list.id) }
    }

    fun setListSync(list: TaskList, sync: Boolean) {
        viewModelScope.launch { repository.setListSync(list.id, sync) }
    }

    fun setShowDone(value: Boolean) = settings.setShowDone(value)

    fun setShowInCalendar(value: Boolean) = settings.setShowInCalendar(value)

    /** L'elenco in cui nasce un'attività nuova: quello aperto, altrimenti il principale. */
    fun currentList(): Long = (filter.value as? TasksFilter.OfList)?.id ?: TaskList.MAIN

    fun now(): LocalDateTime = LocalDateTime.now()
}

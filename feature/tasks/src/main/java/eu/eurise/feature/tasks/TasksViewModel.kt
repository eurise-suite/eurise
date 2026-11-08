package eu.eurise.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.TaskRepository
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

private data class Inputs(val filter: TasksFilter, val query: String, val tasks: List<Task>)

class TasksViewModel(private val repository: TaskRepository) : ViewModel() {

    private val filter = MutableStateFlow<TasksFilter>(TasksFilter.Today)
    private val query = MutableStateFlow("")
    private val settings = repository.settings

    val state: StateFlow<TasksState> =
        combine(
            combine(filter, query, repository.observeAll()) { f, q, t -> Inputs(f, q, t) },
            repository.observeLists(),
            repository.observeStepProgress(),
            settings.showDone,
            settings.showInCalendar,
        ) { i, lists, steps, showDone, inCalendar ->
            TasksState(i.filter, i.query, i.tasks, lists, steps, showDone, inCalendar)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksState())

    fun setFilter(value: TasksFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setDone(task: Task, done: Boolean) {
        viewModelScope.launch { repository.setDone(task.id, done) }
    }

    /** Aggiunge l'attività letta dall'aggiunta rapida; [then] riceve l'id (per aprirne la scheda). */
    fun add(parsed: QuickAdd.Result, fallbackList: Long, then: (Long) -> Unit = {}) {
        if (parsed.title.isBlank()) return
        val listId = state.value.lists.firstOrNull { it.list.name == parsed.listName }?.list?.id ?: fallbackList
        viewModelScope.launch {
            val id = repository.add(
                parsed.title, due = parsed.due, listId = listId, time = parsed.time,
                repeat = parsed.repeat, priority = parsed.priority,
                remindBefore = if (parsed.time != null) 0 else null,
            )
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

    fun delete(task: Task) {
        viewModelScope.launch { repository.delete(task.id) }
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

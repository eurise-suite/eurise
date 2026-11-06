package eu.eurise.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class TaskFilter { Open, Done }

/** Le liste sono nulle finché il database non ha risposto la prima volta. */
data class TasksState(
    val filter: TaskFilter = TaskFilter.Open,
    val open: List<Task>? = null,
    val done: List<Task>? = null,
)

class TasksViewModel(private val repository: TaskRepository) : ViewModel() {

    private val filter = MutableStateFlow(TaskFilter.Open)

    val state: StateFlow<TasksState> =
        combine(filter, repository.observeOpen(), repository.observeDone()) { f, open, done ->
            TasksState(f, open, done)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksState())

    fun setFilter(value: TaskFilter) {
        filter.value = value
    }

    fun setDone(task: Task, done: Boolean) {
        viewModelScope.launch { repository.setDone(task.id, done) }
    }

    fun add(title: String, due: LocalDate?, notes: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.add(title, due, notes) }
    }

    fun update(task: Task) {
        if (task.title.isBlank()) return
        viewModelScope.launch { repository.update(task) }
    }

    fun delete(task: Task) {
        viewModelScope.launch { repository.delete(task.id) }
    }

    fun clearCompleted() {
        viewModelScope.launch { repository.deleteCompleted() }
    }
}

package eu.eurise.feature.tasks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.model.Task
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.component.LocalDockClearance
import eu.eurise.core.designsystem.component.TaskRow
import java.time.LocalDate

/** Che cosa mostra il foglio di modifica: un'attività nuova o una esistente. */
private sealed interface EditorTarget {
    data object New : EditorTarget
    data class Existing(val task: Task) : EditorTarget
}

/**
 * @param newTaskRequests contatore che il guscio incrementa quando si tocca il + della barra:
 * ogni aumento apre il foglio per una nuova attività.
 */
@Composable
fun TasksRoute(repository: TaskRepository, newTaskRequests: Int = 0) {
    val viewModel = viewModel { TasksViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TasksScreen(
        state = state,
        today = LocalDate.now(),
        onFilterChange = viewModel::setFilter,
        onSetDone = viewModel::setDone,
        onAdd = viewModel::add,
        onUpdate = viewModel::update,
        onDelete = viewModel::delete,
        onClearCompleted = viewModel::clearCompleted,
        newTaskRequests = newTaskRequests,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasksScreen(
    state: TasksState,
    today: LocalDate,
    onFilterChange: (TaskFilter) -> Unit,
    onSetDone: (Task, Boolean) -> Unit,
    onAdd: (String, LocalDate?, String) -> Unit,
    onUpdate: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onClearCompleted: () -> Unit,
    newTaskRequests: Int = 0,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    // Il valore presente all'apertura è già "servito": si reagisce solo agli aumenti successivi.
    var handledRequests by remember { mutableIntStateOf(newTaskRequests) }
    LaunchedEffect(newTaskRequests) {
        if (newTaskRequests > handledRequests) {
            handledRequests = newTaskRequests
            editor = EditorTarget.New
        }
    }
    val visible = if (state.filter == TaskFilter.Open) state.open else state.done

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tasks_title)) },
                actions = {
                    if (state.filter == TaskFilter.Done && !state.done.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.tasks_clear_completed))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = LocalDockClearance.current + 16.dp,
            ),
        ) {
            item(key = "filter") {
                FilterRow(
                    filter = state.filter,
                    openCount = state.open?.size ?: 0,
                    onFilterChange = onFilterChange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                visible == null -> Unit
                visible.isEmpty() -> item(key = "empty") {
                    if (state.filter == TaskFilter.Open) {
                        EmptyState(
                            icon = Icons.Outlined.TaskAlt,
                            title = stringResource(R.string.tasks_empty_title),
                            message = stringResource(R.string.tasks_empty_message),
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.TaskAlt,
                            title = stringResource(R.string.tasks_done_empty_title),
                            message = stringResource(R.string.tasks_done_empty_message),
                        )
                    }
                }
                else -> items(visible, key = { it.id }) { task ->
                    TaskRow(
                        title = task.title,
                        done = task.done,
                        due = task.due,
                        today = today,
                        onCheckedChange = { onSetDone(task, it) },
                        onClick = { editor = EditorTarget.Existing(task) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    editor?.let { target ->
        val existing = (target as? EditorTarget.Existing)?.task
        TaskEditorSheet(
            task = existing,
            today = today,
            onSave = { title, due, notes ->
                if (existing == null) onAdd(title, due, notes)
                else onUpdate(existing.copy(title = title, due = due, notes = notes))
            },
            onDelete = existing?.let { task -> { onDelete(task) } },
            onDismiss = { editor = null },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.tasks_clear_title)) },
            text = { Text(stringResource(R.string.tasks_clear_message)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearCompleted() }) {
                    Text(stringResource(R.string.tasks_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.tasks_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    filter: TaskFilter,
    openCount: Int,
    onFilterChange: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = filter == TaskFilter.Open,
            onClick = { onFilterChange(TaskFilter.Open) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text(
                if (openCount > 0) stringResource(R.string.tasks_filter_open_count, openCount)
                else stringResource(R.string.tasks_filter_open)
            )
        }
        SegmentedButton(
            selected = filter == TaskFilter.Done,
            onClick = { onFilterChange(TaskFilter.Done) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text(stringResource(R.string.tasks_filter_done))
        }
    }
}

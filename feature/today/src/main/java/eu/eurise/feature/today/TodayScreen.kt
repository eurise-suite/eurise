package eu.eurise.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.model.Task
import eu.eurise.core.designsystem.component.NoteCard
import eu.eurise.core.designsystem.component.SectionHeader
import eu.eurise.core.designsystem.component.TaskRow
import eu.eurise.core.designsystem.format.longDate
import java.time.LocalTime

/**
 * Schermata Oggi.
 *
 * @param onOpenNote apre una nota nell'editor.
 * @param onShowNotes / [onShowTasks] passano alle sezioni complete.
 */
@Composable
fun TodayRoute(
    notes: NoteRepository,
    tasks: TaskRepository,
    onOpenNote: (Long) -> Unit,
    onShowNotes: () -> Unit,
    onShowTasks: () -> Unit,
) {
    val viewModel = viewModel { TodayViewModel(notes, tasks) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayScreen(
        state = state,
        greeting = greetingFor(LocalTime.now()),
        onSetDone = viewModel::setDone,
        onCapture = viewModel::capture,
        onOpenNote = onOpenNote,
        onShowNotes = onShowNotes,
        onShowTasks = onShowTasks,
    )
}

@Composable
private fun greetingFor(time: LocalTime): String = stringResource(
    when (time.hour) {
        in 5..12 -> R.string.today_good_morning
        in 13..17 -> R.string.today_good_afternoon
        else -> R.string.today_good_evening
    }
)

@Composable
internal fun TodayScreen(
    state: TodayState,
    greeting: String,
    onSetDone: (Task, Boolean) -> Unit,
    onCapture: (String, CaptureKind) -> Unit,
    onOpenNote: (Long) -> Unit,
    onShowNotes: () -> Unit,
    onShowTasks: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)) {
                Text(
                    longDate(state.date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    greeting,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        item(key = "capture") {
            QuickCapture(onCapture, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        item(key = "tasks-header") {
            SectionRow(
                title = stringResource(R.string.today_section_tasks),
                action = stringResource(R.string.today_all_tasks),
                onAction = onShowTasks,
            )
        }
        if (state.loaded && state.due.isEmpty() && state.completedToday.isEmpty()) {
            item(key = "tasks-empty") {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.TaskAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        stringResource(R.string.today_tasks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(state.due, key = { "due-${it.id}" }) { task ->
            TaskRow(
                title = task.title,
                done = false,
                due = task.due,
                today = state.date,
                onCheckedChange = { onSetDone(task, it) },
                modifier = Modifier.animateItem(),
            )
        }
        items(state.completedToday, key = { "done-${it.id}" }) { task ->
            TaskRow(
                title = task.title,
                done = true,
                due = task.due,
                today = state.date,
                onCheckedChange = { onSetDone(task, it) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "notes-header") {
            SectionRow(
                title = stringResource(R.string.today_section_notes),
                action = stringResource(R.string.today_all_notes),
                onAction = onShowNotes,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        if (state.loaded && state.recentNotes.isEmpty()) {
            item(key = "notes-empty") {
                Text(
                    stringResource(R.string.today_notes_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        items(state.recentNotes, key = { "note-${it.id}" }) { note ->
            NoteCard(
                title = note.title,
                body = note.body,
                pinned = note.pinned,
                updatedAt = note.updatedAt,
                today = state.date,
                onClick = { onOpenNote(note.id) },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .animateItem(),
            )
        }
        item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionRow(
    title: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(title, Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

/**
 * Cattura rapida: si scrive una riga e si sceglie se diventa un'attività per oggi o una nota.
 * Il campo si svuota dopo il salvataggio.
 */
@Composable
private fun QuickCapture(onCapture: (String, CaptureKind) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val submit = { kind: CaptureKind ->
        onCapture(text, kind)
        text = ""
        focusManager.clearFocus()
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.today_capture_placeholder)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val enabled = text.isNotBlank()
                OutlinedButton(onClick = { submit(CaptureKind.Note) }, enabled = enabled) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, Modifier.size(18.dp))
                    Text(stringResource(R.string.today_capture_note), Modifier.padding(start = 6.dp))
                }
                FilledTonalButton(onClick = { submit(CaptureKind.Task) }, enabled = enabled) {
                    Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp))
                    Text(stringResource(R.string.today_capture_task), Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

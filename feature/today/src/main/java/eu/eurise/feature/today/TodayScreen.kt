package eu.eurise.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.data.model.Task
import eu.eurise.core.designsystem.component.LocalDockClearance
import eu.eurise.core.designsystem.component.NoteCard
import eu.eurise.core.designsystem.component.SectionHeader
import eu.eurise.core.designsystem.component.TaskRow
import eu.eurise.core.designsystem.format.longDate
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.colors
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val Hm = DateTimeFormatter.ofPattern("H:mm")

/**
 * Schermata Oggi.
 *
 * @param onOpenEvent apre un appuntamento (id dell'evento, inizio dell'occorrenza in millisecondi).
 */
@Composable
fun TodayRoute(
    notes: NoteRepository,
    tasks: TaskRepository,
    calendar: CalendarRepository,
    onOpenNote: (Long) -> Unit,
    onOpenEvent: (Long, Long) -> Unit,
    onShowCalendar: () -> Unit,
    onShowNotes: () -> Unit,
    onShowTasks: () -> Unit,
) {
    val viewModel = viewModel { TodayViewModel(notes, tasks, calendar) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        onPauseOrDispose { }
    }
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }
    TodayScreen(
        state = state,
        now = now,
        greeting = greetingFor(now.toLocalTime()),
        onSetDone = viewModel::setDone,
        onCapture = viewModel::capture,
        onOpenNote = onOpenNote,
        onOpenEvent = { onOpenEvent(it.eventId, it.beginMillis) },
        onShowCalendar = onShowCalendar,
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
    now: LocalDateTime,
    greeting: String,
    onSetDone: (Task, Boolean) -> Unit,
    onCapture: (String, CaptureKind) -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onShowCalendar: () -> Unit,
    onShowNotes: () -> Unit,
    onShowTasks: () -> Unit,
) {
    val timed = state.events.filter { !it.allDay }.sortedBy { it.start }
    val featured = timed.firstOrNull { !it.start.isAfter(now) && it.end.isAfter(now) } ?: timed.firstOrNull { it.start.isAfter(now) }
    val later = state.events
        .filter { it != featured && (it.allDay || it.end.isAfter(now)) }
        .sortedWith(compareBy({ !it.allDay }, { it.start }))

    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(bottom = LocalDockClearance.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
                Text(longDate(state.date), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(greeting, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
        }
        item(key = "featured") {
            when {
                !state.loaded -> Unit
                !state.calendarConnected -> ConnectCalendar(onShowCalendar)
                featured != null -> NextCard(featured, now, onOpenEvent)
                else -> Unit
            }
        }
        item(key = "capture") { QuickCapture(onCapture, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

        if (state.calendarConnected && later.isNotEmpty()) {
            item(key = "later-header") {
                SectionRow(stringResource(if (featured != null) R.string.today_section_later else R.string.today_section_events), stringResource(R.string.today_show_calendar), onShowCalendar)
            }
            items(later, key = { "ev-${it.eventId}-${it.beginMillis}" }) { e -> EventLine(e, onOpenEvent) }
        }

        item(key = "tasks-header") {
            SectionRow(stringResource(R.string.today_section_tasks), stringResource(R.string.today_all_tasks), onShowTasks, Modifier.padding(top = 8.dp))
        }
        if (state.loaded && state.due.isEmpty() && state.completedToday.isEmpty()) {
            item(key = "tasks-empty") {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(EuriseIcons.task(), contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.today_tasks_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(state.due, key = { "due-${it.id}" }) { task ->
            TaskRow(task.title, false, task.due, state.date, { onSetDone(task, it) }, Modifier.animateItem())
        }
        items(state.completedToday, key = { "done-${it.id}" }) { task ->
            TaskRow(task.title, true, task.due, state.date, { onSetDone(task, it) }, Modifier.animateItem())
        }

        item(key = "notes-header") {
            SectionRow(stringResource(R.string.today_section_notes), stringResource(R.string.today_all_notes), onShowNotes, Modifier.padding(top = 8.dp))
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
            )
        }
    }
}

@Composable
private fun SectionRow(title: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(title, Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

/** Il prossimo appuntamento (o quello in corso) in evidenza, con il tempo che manca. */
@Composable
private fun NextCard(event: CalendarEvent, now: LocalDateTime, onOpen: (CalendarEvent) -> Unit) {
    val tint = CalendarTint.nearest(event.color).colors()
    val running = !event.start.isAfter(now)
    val minutes = Duration.between(now, event.start).toMinutes().coerceAtLeast(0)
    val badge = when {
        running -> stringResource(R.string.today_running_until, event.end.toLocalTime().format(Hm))
        minutes < 60 -> stringResource(R.string.today_in_minutes, minutes.coerceAtLeast(1))
        minutes < 180 -> stringResource(R.string.today_in_hours, minutes / 60, minutes % 60)
        else -> stringResource(R.string.today_at, event.start.toLocalTime().format(Hm))
    }
    Surface(
        onClick = { onOpen(event) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (running) R.string.today_now_label else R.string.today_next_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(4.dp).heightIn(min = 44.dp).background(tint.dot, RoundedCornerShape(2.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(
                        "${event.start.toLocalTime().format(Hm)}–${event.end.toLocalTime().format(Hm)}",
                        event.location.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EventLine(event: CalendarEvent, onOpen: (CalendarEvent) -> Unit) {
    val tint = CalendarTint.nearest(event.color).colors()
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(event) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (event.allDay) stringResource(R.string.today_all_day) else event.start.toLocalTime().format(Hm),
            fontSize = if (event.allDay) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp),
        )
        Box(Modifier.width(4.dp).heightIn(min = 34.dp).background(tint.dot, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f)) {
            Text(event.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = event.location.ifBlank {
                if (event.allDay) "" else stringResource(R.string.today_until, event.end.toLocalTime().format(Hm))
            }
            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ConnectCalendar(onShowCalendar: () -> Unit) {
    Surface(
        onClick = onShowCalendar,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(EuriseIcons.calendar(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_connect_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.today_connect_message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Cattura rapida su una riga: il testo diventa una nota (matita) o un'attività per oggi (+).
 * Il campo si svuota dopo il salvataggio.
 */
@Composable
private fun QuickCapture(onCapture: (String, CaptureKind) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val enabled = text.isNotBlank()
    val submit = { kind: CaptureKind ->
        if (text.isNotBlank()) {
            onCapture(text, kind)
            text = ""
            focusManager.clearFocus()
        }
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.today_capture_placeholder)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit(CaptureKind.Task) }),
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { submit(CaptureKind.Note) }, enabled = enabled, modifier = Modifier.alpha(if (enabled) 1f else 0.5f)) {
                Icon(EuriseIcons.pen(), contentDescription = stringResource(R.string.today_capture_note), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            IconButton(
                onClick = { submit(CaptureKind.Task) },
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ),
            ) {
                Icon(EuriseIcons.plus(), contentDescription = stringResource(R.string.today_capture_task), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

package eu.eurise.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.Task
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class TodayState(
    val date: LocalDate,
    /** Appuntamenti di oggi, compresi quelli di tutto il giorno. */
    val events: List<CalendarEvent> = emptyList(),
    val calendarConnected: Boolean = false,
    /** Da fare con scadenza oggi o già passata. */
    val due: List<Task> = emptyList(),
    /** Spuntate oggi: restano visibili (barrate) per poterle riaprire. */
    val completedToday: List<Task> = emptyList(),
    val recentNotes: List<Note> = emptyList(),
    val loaded: Boolean = false,
)

/** Cosa diventa il testo scritto nella cattura rapida. */
enum class CaptureKind { Task, Note }

private data class TaskPart(val due: List<Task>, val done: List<Task>, val notes: List<Note>)

/**
 * Schermata Oggi: appuntamenti, attività in scadenza e note recenti, più la cattura rapida.
 * La data viene fissata all'apertura della schermata.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    private val calendar: CalendarRepository,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val permission = MutableStateFlow(calendar.hasPermission())

    private val local = combine(
        tasks.observeDueBy(today),
        tasks.observeCompletedSince(startOfDay),
        notes.observeRecent(RECENT_NOTES),
    ) { due, done, recent -> TaskPart(due, done, recent) }

    private val events = permission.flatMapLatest { granted ->
        if (granted) calendar.observeEvents(today, today) else flowOf(emptyList())
    }

    val state: StateFlow<TodayState> = combine(local, events, permission) { part, ev, granted ->
        TodayState(today, ev, granted, part.due, part.done, part.notes, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState(today))

    /** Da richiamare al ritorno sulla schermata: il permesso del calendario può essere cambiato. */
    fun refreshPermission() {
        permission.value = calendar.hasPermission()
    }

    fun setDone(task: Task, done: Boolean) {
        viewModelScope.launch { tasks.setDone(task.id, done) }
    }

    /**
     * Salva il testo come attività in scadenza oggi o come nota. Per una nota la prima riga
     * diventa il titolo e il resto il testo.
     */
    fun capture(text: String, kind: CaptureKind) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            when (kind) {
                CaptureKind.Task -> tasks.add(clean, due = state.value.date)
                CaptureKind.Note -> {
                    val title = clean.substringBefore('\n').trim()
                    val body = clean.substringAfter('\n', "").trim()
                    notes.save(id = null, title = title, body = body)
                }
            }
        }
    }

    private companion object {
        const val RECENT_NOTES = 3
    }
}

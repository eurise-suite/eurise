package eu.eurise.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.modes.Modes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class CalendarView { Agenda, Day, ThreeDays, Week, Month }

data class CalendarState(
    val view: CalendarView = CalendarView.ThreeDays,
    /** Giorno di riferimento: il primo mostrato, o quello selezionato nella vista Mese. */
    val anchor: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val hasPermission: Boolean = false,
    val from: LocalDate = anchor,
    val to: LocalDate = anchor,
    val events: List<CalendarEvent> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val loaded: Boolean = false,
) {
    /** I giorni mostrati, nell'ordine. */
    val days: List<LocalDate>
        get() = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
}

private data class Query(val view: CalendarView, val anchor: LocalDate, val permission: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val calendar: CalendarRepository,
    private val tasks: TaskRepository,
) : ViewModel() {

    private val today = LocalDate.now()
    private val view = MutableStateFlow(CalendarView.ThreeDays)
    private val anchor = MutableStateFlow(today)
    private val permission = MutableStateFlow(calendar.hasPermission())

    val state: StateFlow<CalendarState> =
        combine(view, anchor, permission) { v, a, p -> Query(v, a, p) }
            .flatMapLatest { q ->
                val (from, to) = range(q.view, q.anchor)
                val events = if (q.permission) calendar.observeEvents(from, to) else flowOf(emptyList())
                combine(events, tasks.observeForCalendar(from, to), Modes.state) { e, t, m ->
                    // La modalità accesa può nascondere calendari ed elenchi.
                    val shown = if (m.hiddenCalendars.isEmpty()) e else e.filter { it.calendarId !in m.hiddenCalendars }
                    val shownTasks = if (m.hiddenLists.isEmpty()) t else t.filter { it.listId !in m.hiddenLists }
                    CalendarState(q.view, q.anchor, today, q.permission, from, to, shown, shownTasks, loaded = true)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState(today = today, anchor = today))

    fun setView(value: CalendarView) {
        view.value = value
        // Nella settimana e nel mese il giorno di riferimento resta; negli altri si riparte da quello scelto.
    }

    fun goToToday() {
        anchor.value = today
    }

    /** Sposta il periodo mostrato avanti ([forward]) o indietro, del passo naturale della vista. */
    fun step(forward: Boolean) {
        val sign = if (forward) 1L else -1L
        val a = anchor.value
        anchor.value = when (view.value) {
            CalendarView.Day -> a.plusDays(sign)
            CalendarView.ThreeDays -> a.plusDays(3 * sign)
            CalendarView.Week -> a.plusWeeks(sign)
            CalendarView.Agenda -> a.plusDays(30 * sign)
            CalendarView.Month -> {
                val first = a.withDayOfMonth(1).plusMonths(sign)
                if (first.year == today.year && first.month == today.month) today else first
            }
        }
    }

    /** Apre un giorno: nella vista Mese lo seleziona, altrimenti passa alla vista Giorno. */
    fun openDay(day: LocalDate, switchToDay: Boolean) {
        anchor.value = day
        if (switchToDay) view.value = CalendarView.Day
    }

    fun onPermissionResult(granted: Boolean) {
        permission.value = granted || calendar.hasPermission()
    }

    fun setTaskDone(task: Task, done: Boolean) {
        viewModelScope.launch { tasks.setDone(task.id, done) }
    }

    companion object {
        /** Giorni da caricare per una vista; le settimane iniziano di lunedì. */
        fun range(view: CalendarView, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (view) {
            CalendarView.Agenda -> anchor to anchor.plusDays(29)
            CalendarView.Day -> anchor to anchor
            CalendarView.ThreeDays -> anchor to anchor.plusDays(2)
            CalendarView.Week -> {
                val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                monday to monday.plusDays(6)
            }
            CalendarView.Month -> {
                val start = anchor.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                start to start.plusDays(41)
            }
        }
    }
}

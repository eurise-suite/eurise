package eu.eurise.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.data.calendar.DeviceCalendar
import eu.eurise.core.data.calendar.EventDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class EditorError { Title, End, Save }

data class EventEditorState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    /** Ricorrente o in un calendario in sola lettura: si consulta soltanto. */
    val readOnly: Boolean = false,
    val recurring: Boolean = false,
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val allDay: Boolean = false,
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val endDate: LocalDate = LocalDate.now(),
    val endTime: LocalTime = LocalTime.of(10, 0),
    val calendarId: Long? = null,
    val calendars: List<DeviceCalendar> = emptyList(),
    val error: EditorError? = null,
    val busy: Boolean = false,
) {
    val selectedCalendar: DeviceCalendar? get() = calendars.firstOrNull { it.id == calendarId }
}

/**
 * Crea o modifica un appuntamento nel calendario del telefono.
 *
 * @param eventId evento da modificare, con [beginMillis] che indica quale occorrenza; null per uno nuovo.
 * @param suggestedStart inizio proposto per un appuntamento nuovo (per esempio l'ora toccata nella griglia).
 */
class EventEditorViewModel(
    private val repository: CalendarRepository,
    private val eventId: Long?,
    beginMillis: Long?,
    suggestedStart: Long?,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(EventEditorState(isNew = eventId == null))
    val state: StateFlow<EventEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val calendars = repository.observeCalendars().first()
            if (eventId != null && beginMillis != null) {
                val e = repository.getOccurrence(eventId, beginMillis, zone)
                if (e != null) {
                    val lastDay = e.lastDay
                    _state.value = EventEditorState(
                        loaded = true,
                        isNew = false,
                        readOnly = e.recurring || !e.writable,
                        recurring = e.recurring,
                        title = e.title,
                        location = e.location,
                        description = e.description,
                        allDay = e.allDay,
                        startDate = e.start.toLocalDate(),
                        startTime = e.start.toLocalTime(),
                        endDate = if (e.allDay) lastDay else e.end.toLocalDate(),
                        endTime = e.end.toLocalTime(),
                        calendarId = e.calendarId,
                        calendars = calendars,
                    )
                    return@launch
                }
            }
            val start = suggestedStart?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
                ?: LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
            val end = start.plusHours(1)
            _state.value = EventEditorState(
                loaded = true,
                startDate = start.toLocalDate(),
                startTime = start.toLocalTime(),
                endDate = end.toLocalDate(),
                endTime = end.toLocalTime(),
                calendarId = calendars.firstOrNull { it.writable }?.id,
                calendars = calendars,
            )
        }
    }

    fun onTitle(value: String) = _state.update { it.copy(title = value, error = null) }
    fun onLocation(value: String) = _state.update { it.copy(location = value) }
    fun onDescription(value: String) = _state.update { it.copy(description = value) }
    fun onAllDay(value: Boolean) = _state.update { it.copy(allDay = value, error = null) }
    fun onCalendar(id: Long) = _state.update { it.copy(calendarId = id) }

    /** Cambiando l'inizio, la fine si sposta della stessa quantità: la durata resta. */
    fun onStartDate(date: LocalDate) = _state.update {
        val shift = date.toEpochDay() - it.startDate.toEpochDay()
        it.copy(startDate = date, endDate = it.endDate.plusDays(shift), error = null)
    }

    fun onStartTime(time: LocalTime) = _state.update {
        val oldStart = it.startDate.atTime(it.startTime)
        val oldEnd = it.endDate.atTime(it.endTime)
        val newEnd = it.startDate.atTime(time).plus(java.time.Duration.between(oldStart, oldEnd))
        it.copy(startTime = time, endDate = newEnd.toLocalDate(), endTime = newEnd.toLocalTime(), error = null)
    }

    fun onEndDate(date: LocalDate) = _state.update { it.copy(endDate = date, error = null) }
    fun onEndTime(time: LocalTime) = _state.update { it.copy(endTime = time, error = null) }

    fun createLocalCalendar(name: String, color: Int) {
        viewModelScope.launch {
            val id = repository.createLocalCalendar(name, color) ?: return@launch
            val calendars = repository.observeCalendars().first()
            _state.update { it.copy(calendars = calendars, calendarId = id) }
        }
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.readOnly || s.busy) return
        val calendarId = s.calendarId ?: return
        if (s.title.isBlank()) return _state.update { it.copy(error = EditorError.Title) }
        val start = if (s.allDay) s.startDate.atStartOfDay() else s.startDate.atTime(s.startTime)
        val end = if (s.allDay) s.endDate.plusDays(1).atStartOfDay() else s.endDate.atTime(s.endTime)
        if (!end.isAfter(start)) return _state.update { it.copy(error = EditorError.End) }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val id = repository.save(EventDraft(eventId, calendarId, s.title, s.location, s.description, s.allDay, start, end), zone)
            if (id != null) onSaved() else _state.update { it.copy(busy = false, error = EditorError.Save) }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = eventId ?: return
        if (_state.value.readOnly) return
        viewModelScope.launch {
            if (repository.delete(id)) onDeleted() else _state.update { it.copy(error = EditorError.Save) }
        }
    }
}

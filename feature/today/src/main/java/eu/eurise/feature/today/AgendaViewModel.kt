package eu.eurise.feature.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.model.Capture
import eu.eurise.core.data.model.LinkRef
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.route.RouteRepository
import eu.eurise.core.data.weather.Forecast
import eu.eurise.core.data.weather.MeteoRepository
import eu.eurise.core.links.LinkResolver
import eu.eurise.core.links.Resolved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Una cosa da fare con le persone collegate (nomi brevi, per le pillole). */
data class AgendaTask(val task: Task, val people: List<String>)

/** Un giorno dell'Agenda. */
data class AgendaDay(
    val date: LocalDate,
    val loaded: Boolean = false,
    val calendarConnected: Boolean = false,
    val events: List<CalendarEvent> = emptyList(),
    val open: List<AgendaTask> = emptyList(),
    val done: List<AgendaTask> = emptyList(),
    val people: List<DayPerson> = emptyList(),
)

private val Hm = DateTimeFormatter.ofPattern("H:mm")

/**
 * L'Agenda (prima «Oggi», tavola AgeGiorno): un giorno alla volta con appuntamenti, cose da fare, persone del giorno,
 * meteo e massima; si scorre ai giorni prima e dopo. Meteo e strada passano dal centro privacy (spenti di base).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModel(
    context: Context,
    private val data: EuriseData,
    val today: LocalDate = LocalDate.now(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val app = context.applicationContext
    private val resolver = LinkResolver(app, data)
    private val dayPeople = DayPeople(app)
    private val routes = RouteRepository(app)
    private val permission = MutableStateFlow(data.calendar.hasPermission())

    private val _forecast = MutableStateFlow<Forecast?>(null)
    val forecast: StateFlow<Forecast?> = _forecast.asStateFlow()

    /** Minuti di strada in auto per indirizzo (solo per gli impegni di oggi, se «Strada» è accesa). */
    private val _travels = MutableStateFlow<Map<String, Int>>(emptyMap())
    val travels: StateFlow<Map<String, Int>> = _travels.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val meteo = MeteoRepository(app)
            meteo.cached()?.forecast?.let { _forecast.value = it }
            runCatching { meteo.load(withPollen = false) }.getOrNull()?.forecast?.let { _forecast.value = it }
        }
    }

    fun refreshPermission() {
        permission.value = data.calendar.hasPermission()
    }

    /** Il giorno [date], che si aggiorna da solo quando cambiano impegni e cose da fare. */
    fun day(date: LocalDate): Flow<AgendaDay> {
        val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val tasks: Flow<Pair<List<Task>, List<Task>>> = if (date == today) {
            combine(data.tasks.observeDueBy(date), data.tasks.observeCompletedSince(startMillis)) { open, done -> open to done }
        } else {
            data.tasks.observeDueBetween(date, date).map { all -> all.filter { !it.done } to all.filter { it.done } }
        }
        val events = permission.flatMapLatest { granted ->
            if (granted) data.calendar.observeEvents(date, date, zone) else flowOf(emptyList())
        }
        return combine(tasks, events, permission) { t, e, granted -> Triple(t, e, granted) }
            .mapLatest { (t, e, granted) ->
                val sorted = e.sortedWith(compareBy({ !it.allDay }, { it.start }))
                val open = t.first.map { AgendaTask(it, linkedNames(LinkRef.task(it.id))) }
                val done = t.second.map { AgendaTask(it, linkedNames(LinkRef.task(it.id))) }
                AgendaDay(date, loaded = true, calendarConnected = granted, events = sorted, open = open, done = done, people = peopleOf(sorted, t.first))
            }
            .flowOn(Dispatchers.IO)
    }

    private suspend fun linkedContacts(ref: LinkRef): List<LinkRef> =
        runCatching { data.links.observe(ref).first() }.getOrDefault(emptyList())
            .map { it.other }.filter { it.kind == LinkRef.Kind.Contact }

    private suspend fun linkedNames(ref: LinkRef): List<String> = linkedContacts(ref).mapNotNull { c ->
        (resolver.resolve(c) as? Resolved.Found)?.item?.title?.substringBefore(' ')
    }

    /** Chi si vede oggi: collegati agli impegni e alle cose da fare, poi gli invitati in rubrica. Al massimo otto. */
    private suspend fun peopleOf(events: List<CalendarEvent>, tasks: List<Task>): List<DayPerson> {
        if (!dayPeople.canRead()) return emptyList()
        val why = LinkedHashMap<String, String>()
        for (e in events) {
            val at = if (e.allDay) e.title else e.start.toLocalTime().format(Hm)
            linkedContacts(LinkRef.event(e.eventId)).forEach { why.putIfAbsent(it.key, at) }
            dayPeople.attendees(e.eventId).forEach { why.putIfAbsent(it, at) }
            if (why.size >= DayPeople.MAX) break
        }
        for (task in tasks) {
            if (why.size >= DayPeople.MAX) break
            linkedContacts(LinkRef.task(task.id)).forEach { why.putIfAbsent(it.key, AgendaText.splitPeople(task.title).first) }
        }
        return why.entries.take(DayPeople.MAX).mapNotNull { (key, reason) -> dayPeople.person(key, reason) }
    }

    /** Calcola la strada per gli impegni di oggi che hanno un luogo e devono ancora cominciare. */
    fun routesFor(events: List<CalendarEvent>) {
        val now = LocalDateTime.now()
        val places = events.filter { !it.allDay && it.location.isNotBlank() && it.start.isAfter(now) && it.start.toLocalDate() == today }
            .map { it.location }.distinct().filter { it !in _travels.value }
        if (places.isEmpty()) return
        viewModelScope.launch {
            for (p in places) {
                val t = routes.travelTo(p) ?: continue
                _travels.value = _travels.value + (p to t.minutes)
            }
        }
    }

    fun setDone(task: Task, done: Boolean) {
        viewModelScope.launch { data.tasks.setDone(task.id, done) }
    }

    /**
     * Il testo scritto in basso diventa una cosa da fare per il giorno mostrato (o per quello scritto nella frase),
     * con le persone collegate. Le persone restano nel titolo con la chiocciola, come nelle Attività.
     */
    fun addTask(text: String, date: LocalDate, people: List<LinkRef>) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val q = Capture.parse(clean, LocalDateTime.now()).quick
        viewModelScope.launch {
            val id = data.tasks.add(
                q.title.ifBlank { clean }, due = q.due ?: date, time = q.time, repeat = q.repeat, priority = q.priority,
                remindBefore = if (q.time != null) 0 else null,
            )
            people.forEach { runCatching { data.links.link(LinkRef.task(id), it) } }
        }
    }

    /** Il testo diventa una nota: la prima riga è il titolo. */
    fun addNote(text: String, people: List<LinkRef>) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val id = data.notes.save(null, clean.substringBefore('\n').trim(), clean.substringAfter('\n', "").trim())
            people.forEach { runCatching { data.links.link(LinkRef.note(id), it) } }
        }
    }

    /** Il testo parla di un appuntamento («domani alle 10 dentista»)? Allora si conferma nel + unico. */
    fun isEvent(text: String): Boolean =
        Capture.parse(text.trim(), LocalDateTime.now()).suggested == Capture.Target.Event
}

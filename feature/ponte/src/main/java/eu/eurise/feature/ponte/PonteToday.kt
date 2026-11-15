package eu.eurise.feature.ponte

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * «Oggi» per il pannello Eurise di Firefox sul computer (tavola PcFirefox). Il modulo del Ponte non vede i dati della
 * suite: li dà l'applicazione Eurise, che implementa [Source]. Si manda solo se l'utente ha acceso «Oggi sul computer»
 * (spento di base) e solo al computer abbinato che lo chiede, sul collegamento cifrato di casa.
 */
object PonteToday {
    /** Un appuntamento di oggi o di domani mattina. */
    data class Item(val start: LocalDateTime, val end: LocalDateTime, val allDay: Boolean, val title: String, val place: String)
    data class Todo(val title: String, val due: LocalDate?)
    data class Summary(val events: List<Item>, val tasks: List<Todo>, val tasksOpen: Int)

    /** Implementata dall'applicazione Eurise (`EuriseApplication`). */
    interface Source { suspend fun ponteToday(): Summary }

    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    /** I campi del messaggio [eu.eurise.core.ponte.Kind.Today]; i testi senza tabulazioni né a capo. */
    fun fields(s: Summary, now: LocalDateTime = LocalDateTime.now()): Map<String, String> {
        fun clean(t: String) = t.replace('\t', ' ').replace('\n', ' ').trim().take(120)
        val events = s.events.filter { it.end.isAfter(now) || it.allDay }.sortedBy { it.start }.take(5).joinToString("\n") { e ->
            listOf(
                if (e.allDay) "" else e.start.format(hm),
                if (e.allDay) "" else e.end.format(hm),
                clean(e.title), clean(e.place),
                e.start.toLocalDate().toString(),
            ).joinToString("\t")
        }
        val tasks = s.tasks.take(5).joinToString("\n") { t -> listOf(clean(t.title), t.due?.toString().orEmpty()).joinToString("\t") }
        return mapOf("events" to events, "tasks" to tasks, "tasksOpen" to s.tasksOpen.toString(), "day" to now.toLocalDate().toString())
    }

    /** Le attività da mostrare: scadute e di oggi per prime, poi le altre in ordine di scadenza. */
    fun pickTasks(all: List<Todo>, today: LocalDate = LocalDate.now()): List<Todo> =
        all.sortedWith(compareBy<Todo> { it.due == null }.thenBy { it.due ?: LocalDate.MAX }).let { sorted ->
            sorted.filter { it.due != null && !it.due.isAfter(today) } + sorted.filter { it.due == null || it.due.isAfter(today) }
        }
}

package eu.eurise.feature.assistant

import eu.eurise.core.data.calendar.EventDraft
import java.time.LocalDate
import java.time.LocalTime

/** L'ultima cosa che Alfred ha fatto, con ciò che serve per spostarla, toglierla o mandarla ad altri. */
sealed interface Done {
    /** [who]: con chi è l'impegno, se detto (per «avvisa anche…»). */
    data class Event(val id: Long, val draft: EventDraft, val who: String?) : Done
    data class Task(val id: Long, val title: String, val due: LocalDate?, val time: LocalTime?) : Done
    data class Note(val id: Long, val text: String) : Done
    data class Message(val who: String, val text: String, val channel: Channel?) : Done
    data class Call(val who: String) : Done
    data class Alarm(val time: LocalTime) : Done
}

/**
 * Ciò che Alfred tiene a mente per due minuti dopo un'azione (tavola AlfSeguito): «spostalo alle 10», «cancellalo»,
 * «mandalo anche a Luca», «annulla». Solo in memoria, mai su disco: chiuso il telefono o passati due minuti, si dimentica.
 */
object FollowUp {
    const val WINDOW_MS = 2 * 60 * 1000L

    @Volatile private var last: Done? = null
    @Volatile private var at = 0L

    fun remember(done: Done, now: Long = System.currentTimeMillis()) {
        last = done
        at = now
    }

    /** L'ultima cosa fatta, se non sono passati due minuti. */
    fun current(now: Long = System.currentTimeMillis()): Done? = last?.takeIf { now - at <= WINDOW_MS }

    fun clear() {
        last = null
    }
}

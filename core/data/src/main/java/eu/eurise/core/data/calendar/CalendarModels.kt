package eu.eurise.core.data.calendar

import java.time.LocalDate
import java.time.LocalDateTime

/** Un calendario presente sul telefono (per esempio quello di Murena sincronizzato da /e/OS). */
data class DeviceCalendar(
    val id: Long,
    val name: String,
    val accountName: String,
    /** Colore ARGB del calendario. */
    val color: Int,
    /** Vero se l'utente può aggiungere e modificare eventi in questo calendario. */
    val writable: Boolean,
    /** Calendario creato da Eurise solo sul telefono, senza sincronizzazione. */
    val local: Boolean,
)

/**
 * Un'occorrenza di un evento nell'intervallo richiesto. Gli eventi ricorrenti compaiono una volta
 * per ogni ripetizione, con lo stesso [eventId].
 *
 * Gli orari sono nel fuso del telefono. Per gli eventi di tutto il giorno [start] è la mezzanotte
 * del primo giorno ed [end] la mezzanotte dopo l'ultimo (escluso).
 */
data class CalendarEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val location: String,
    val description: String,
    val allDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** Colore ARGB dell'evento, o del suo calendario se l'evento non ne ha uno proprio. */
    val color: Int,
    val recurring: Boolean,
    val calendarName: String,
    val writable: Boolean,
    /** Inizio dell'occorrenza come lo salva il sistema: identifica la ripetizione di un evento ricorrente. */
    val beginMillis: Long,
) {
    /** Primo giorno in cui l'evento compare. */
    val firstDay: LocalDate get() = start.toLocalDate()

    /** Ultimo giorno in cui l'evento compare (incluso). */
    val lastDay: LocalDate
        get() = if (end.toLocalTime() == java.time.LocalTime.MIDNIGHT && end.isAfter(start)) {
            end.toLocalDate().minusDays(1)
        } else {
            end.toLocalDate()
        }

    fun occursOn(day: LocalDate): Boolean = !day.isBefore(firstDay) && !day.isAfter(lastDay)
}

/** Dati di un evento da creare ([eventId] nullo) o da modificare. */
data class EventDraft(
    val eventId: Long?,
    val calendarId: Long,
    val title: String,
    val location: String,
    val description: String,
    val allDay: Boolean,
    val start: LocalDateTime,
    /** Per gli eventi di tutto il giorno: la mezzanotte dopo l'ultimo giorno. */
    val end: LocalDateTime,
)

/** Un partecipante di un evento. [self] è chi usa il telefono. */
data class Attendee(val name: String, val email: String, val organizer: Boolean, val self: Boolean)

/** Una riunione: un evento con almeno un'altra persona invitata oltre a chi usa il telefono. */
fun List<Attendee>.isMeeting(): Boolean {
    val others = filterNot { it.self }
    // Un solo organizzatore senza invitati è un evento personale anche se il telefono non lo riconosce come "sé".
    return others.any { !it.organizer } || (size >= 2 && others.isNotEmpty())
}

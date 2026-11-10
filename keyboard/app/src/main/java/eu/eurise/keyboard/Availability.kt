package eu.eurise.keyboard

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Un intervallo di tempo nello stesso giorno o a cavallo di più giorni: [start] incluso, [end] escluso. */
data class Span(val start: LocalDateTime, val end: LocalDateTime)

/** Un orario libero proposto: un giorno, dalle [from] alle [to]. */
data class Slot(val day: LocalDate, val from: LocalTime, val to: LocalTime)

/**
 * Orari liberi per "Disponibilità": tutto si calcola sul telefono a partire dagli impegni del calendario.
 * Le proposte cominciano all'ora o alla mezz'ora, durano [minutes] minuti e stanno dentro la giornata di lavoro.
 */
object Availability {

    val DAY_START: LocalTime = LocalTime.of(9, 0)
    val DAY_END: LocalTime = LocalTime.of(20, 0)

    /** Orari liberi di [day], al massimo [limit]; mai nel passato rispetto a [now]. */
    fun slots(
        day: LocalDate,
        busy: List<Span>,
        minutes: Int,
        now: LocalDateTime,
        dayStart: LocalTime = DAY_START,
        dayEnd: LocalTime = DAY_END,
        limit: Int = 12,
    ): List<Slot> {
        if (minutes <= 0) return emptyList()
        var cursor = day.atTime(dayStart)
        if (now.toLocalDate() == day && now.isAfter(cursor)) cursor = roundUp(now)
        if (now.toLocalDate().isAfter(day)) return emptyList()
        val end = day.atTime(dayEnd)
        val blocks = busy.filter { it.end.isAfter(day.atStartOfDay()) && it.start.isBefore(end) }.sortedBy { it.start }
        val out = mutableListOf<Slot>()
        while (!cursor.plusMinutes(minutes.toLong()).isAfter(end) && out.size < limit) {
            val slotEnd = cursor.plusMinutes(minutes.toLong())
            val clash = blocks.firstOrNull { it.start.isBefore(slotEnd) && it.end.isAfter(cursor) }
            if (clash == null) {
                out += Slot(day, cursor.toLocalTime(), slotEnd.toLocalTime())
                cursor = slotEnd
            } else {
                cursor = roundUp(clash.end)
            }
        }
        return out
    }

    /** Vero se il giorno ha almeno un orario libero di [minutes] minuti (il puntino nel calendario del mese). */
    fun hasFree(day: LocalDate, busy: List<Span>, minutes: Int, now: LocalDateTime): Boolean =
        slots(day, busy, minutes, now, limit = 1).isNotEmpty()

    /** I fine settimana restano liberi ma non si segnano, salvo impegni già presi. */
    fun isWeekend(day: LocalDate) = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY

    private fun roundUp(t: LocalDateTime): LocalDateTime {
        val base = t.withSecond(0).withNano(0)
        val m = base.minute
        val add = when {
            m == 0 && t.second == 0 && t.nano == 0 -> 0
            m <= 30 && !(m == 30 && t.second == 0 && t.nano == 0) -> 30 - m
            m == 30 -> 0
            else -> 60 - m
        }
        return base.plusMinutes(add.toLong())
    }

    private val DAYS = mapOf(
        DayOfWeek.MONDAY to "lunedì", DayOfWeek.TUESDAY to "martedì", DayOfWeek.WEDNESDAY to "mercoledì",
        DayOfWeek.THURSDAY to "giovedì", DayOfWeek.FRIDAY to "venerdì", DayOfWeek.SATURDAY to "sabato", DayOfWeek.SUNDAY to "domenica",
    )

    private fun time(t: LocalTime) = if (t.minute == 0) "${t.hour}" else "${t.hour}:%02d".format(t.minute)

    /** "giovedì 12 dalle 15 alle 17"; con [today] diventa "oggi dalle…" o "domani dalle…". */
    fun describe(slot: Slot, today: LocalDate): String {
        val day = when (slot.day) {
            today -> "oggi"
            today.plusDays(1) -> "domani"
            else -> "${DAYS.getValue(slot.day.dayOfWeek)} ${slot.day.dayOfMonth}"
        }
        val from = time(slot.from)
        val to = time(slot.to)
        val dalle = if (slot.from.hour == 1) "dall'" else "dalle "
        val alle = if (slot.to.hour == 1) "all'" else "alle "
        return "$day $dalle$from $alle$to"
    }

    /** La frase pronta da inserire: "giovedì 12 dalle 15 alle 17 o venerdì 13 dalle 10 alle 12. Ti va bene uno dei due?" */
    fun sentence(chosen: List<Slot>, today: LocalDate): String {
        val parts = chosen.sortedWith(compareBy({ it.day }, { it.from })).map { describe(it, today) }
        return when (parts.size) {
            0 -> ""
            1 -> "${parts[0]}. Ti va bene?"
            2 -> "${parts[0]} o ${parts[1]}. Ti va bene uno dei due?"
            else -> parts.dropLast(1).joinToString(", ") + " o " + parts.last() + ". Ti va bene uno di questi?"
        }
    }
}

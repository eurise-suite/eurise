package eu.eurise.feature.clock.logic

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Perché una sveglia tace in un giorno in cui suonerebbe. */
enum class Silence { Skipped, Holiday, Vacation }

object Schedule {

    /**
     * La prossima volta in cui [alarm] suona dopo [now]. [vacation] dice se un giorno è di ferie (dal calendario).
     * Nullo se la sveglia è spenta o non suonerà più entro un anno.
     */
    fun next(alarm: Alarm, now: LocalDateTime, vacation: (LocalDate) -> Boolean = { false }): LocalDateTime? {
        if (!alarm.enabled) return null
        var day = now.toLocalDate()
        repeat(370) {
            val at = day.atTime(alarm.time)
            if (at.isAfter(now) && (alarm.days.isEmpty() || day.dayOfWeek in alarm.days) && silence(alarm, day, vacation) == null) {
                return at
            }
            day = day.plusDays(1)
        }
        return null
    }

    /** Se e perché [alarm] tace il giorno [day]. */
    fun silence(alarm: Alarm, day: LocalDate, vacation: (LocalDate) -> Boolean = { false }): Silence? = when {
        alarm.skipped == day -> Silence.Skipped
        alarm.skipHolidays && ItalianHolidays.isHoliday(day) -> Silence.Holiday
        alarm.skipVacation && vacation(day) -> Silence.Vacation
        else -> null
    }

    /** La prossima volta, tra le sveglie del giorno ripetuto, in cui [alarm] tacerà (per la riga "Tace l'8 dicembre"). */
    fun nextSilence(alarm: Alarm, from: LocalDate, vacation: (LocalDate) -> Boolean = { false }): Pair<LocalDate, Silence>? {
        if (!alarm.repeating || !alarm.enabled) return null
        var day = from
        repeat(120) {
            if (day.dayOfWeek in alarm.days) silence(alarm, day, vacation)?.let { return day to it }
            day = day.plusDays(1)
        }
        return null
    }

    /** La sveglia che suona per prima, con il suo momento. */
    fun first(alarms: List<Alarm>, now: LocalDateTime, vacation: (LocalDate) -> Boolean = { false }): Pair<Alarm, LocalDateTime>? =
        alarms.mapNotNull { a -> next(a, now, vacation)?.let { a to it } }.minByOrNull { it.second }

    /** Salta solo la prossima volta: segna il giorno in cui avrebbe suonato. */
    fun skipNext(alarm: Alarm, now: LocalDateTime, vacation: (LocalDate) -> Boolean = { false }): Alarm {
        val at = next(alarm, now, vacation) ?: return alarm
        return alarm.copy(skipped = at.toLocalDate())
    }

    /** Ora di andare a dormire per avere [sleep] di sonno prima di [wake] (più 15 minuti per addormentarsi). */
    fun bedtime(wake: LocalDateTime, sleep: Duration): LocalDateTime = wake.minus(sleep).minusMinutes(15)

    /**
     * Proposta di anticipo: se il primo impegno del giorno inizia meno di [margin] dopo la sveglia, propone di
     * suonare [margin] prima dell'impegno. Nullo se non serve.
     */
    fun earlierFor(wake: LocalDateTime, firstEvent: LocalDateTime?, margin: Duration): LocalDateTime? {
        firstEvent ?: return null
        if (firstEvent.toLocalDate() != wake.toLocalDate()) return null
        val wanted = firstEvent.minus(margin)
        return wanted.takeIf { it.isBefore(wake) }
    }
}

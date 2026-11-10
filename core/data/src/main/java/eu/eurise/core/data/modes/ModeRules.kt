package eu.eurise.core.data.modes

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay

/** Le quattro modalità, in ordine di precedenza quando più d'una vorrebbe accendersi insieme. */
enum class Mode { Travel, Night, Work, Home }

/** Quali notifiche passano: "Non cambia" lascia l'impostazione del telefono com'era. */
enum class Interruptions { Unchanged, All, Priority, Alarms, None }

enum class Ringer { Unchanged, Sound, Vibrate, Silent }

/**
 * Accensione per orario. [end] prima di [start] vuol dire che si passa la mezzanotte (per esempio 23–7): il giorno
 * che conta è quello in cui si comincia. Con [skipHolidays] i festivi e le ferie restano fuori.
 */
data class Schedule(
    val enabled: Boolean = false,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val start: LocalTime = LocalTime.of(9, 0),
    val end: LocalTime = LocalTime.of(18, 0),
    val skipHolidays: Boolean = false,
)

/** Che cosa cambia una modalità e quando si accende da sola. */
data class ModeConfig(
    val mode: Mode,
    /** Categoria della Scrivania da mostrare; nulla per lasciare la home. */
    val homeCategory: String? = null,
    val interruptions: Interruptions = Interruptions.Unchanged,
    val ringer: Ringer = Ringer.Unchanged,
    val hiddenCalendars: Set<Long> = emptySet(),
    val hiddenLists: Set<Long> = emptySet(),
    val schedule: Schedule = Schedule(),
    /** Si accende durante gli eventi di questo calendario. */
    val calendarTrigger: Long? = null,
) {
    companion object {
        /** Partenza proposta: orari già scritti ma spenti, nulla cambia finché la persona non decide. */
        fun defaults(mode: Mode): ModeConfig = when (mode) {
            Mode.Work -> ModeConfig(mode, schedule = Schedule(days = DayOfWeek.entries.take(5).toSet(), skipHolidays = true))
            Mode.Home -> ModeConfig(mode, schedule = Schedule(start = LocalTime.of(18, 30), end = LocalTime.of(22, 30)))
            Mode.Night -> ModeConfig(mode, interruptions = Interruptions.Alarms, schedule = Schedule(start = LocalTime.of(23, 0), end = LocalTime.of(7, 0)))
            Mode.Travel -> ModeConfig(mode)
        }
    }
}

/** Una scelta fatta a mano nel Pannello: vale fino a [until]; [mode] nullo vuol dire "nessuna modalità". */
data class ManualChoice(val mode: Mode?, val until: LocalDateTime)

/** Un intervallo in cui un calendario ha un evento in corso ([TRIP]: un viaggio preparato da un biglietto). */
data class Busy(val calendarId: Long, val start: LocalDateTime, val end: LocalDateTime) {
    companion object {
        /** Finto calendario dei viaggi: accende sempre la modalità Viaggio. */
        const val TRIP = -2L
    }
}

/** Regole pure, senza Android: si provano senza telefono. */
object ModeRules {

    /** La modalità che deve essere accesa in [now]. */
    fun resolve(
        now: LocalDateTime,
        configs: List<ModeConfig>,
        manual: ManualChoice?,
        busy: List<Busy>,
        isDayOff: (LocalDate) -> Boolean,
    ): Mode? {
        if (manual != null && now.isBefore(manual.until)) return manual.mode
        return automatic(now, configs, busy, isDayOff)
    }

    fun automatic(now: LocalDateTime, configs: List<ModeConfig>, busy: List<Busy>, isDayOff: (LocalDate) -> Boolean): Mode? =
        configs.sortedBy { it.mode.ordinal }.firstOrNull { c ->
            (c.mode == Mode.Travel && busy.any { it.calendarId == Busy.TRIP && !now.isBefore(it.start) && now.isBefore(it.end) }) ||
                (c.calendarTrigger != null && busy.any { it.calendarId == c.calendarTrigger && !now.isBefore(it.start) && now.isBefore(it.end) }) ||
                inSchedule(now, c.schedule, isDayOff)
        }?.mode

    fun inSchedule(now: LocalDateTime, s: Schedule, isDayOff: (LocalDate) -> Boolean): Boolean {
        if (!s.enabled || s.start == s.end) return false
        val time = now.toLocalTime()
        val overnight = s.end.isBefore(s.start)
        // Il giorno in cui l'intervallo è cominciato: ieri, se siamo nella parte dopo mezzanotte.
        val day = when {
            !overnight -> if (!time.isBefore(s.start) && time.isBefore(s.end)) now.toLocalDate() else return false
            !time.isBefore(s.start) -> now.toLocalDate()
            time.isBefore(s.end) -> now.toLocalDate().minusDays(1)
            else -> return false
        }
        return day.dayOfWeek in s.days && !(s.skipHolidays && isDayOff(day))
    }

    /**
     * I prossimi momenti in cui la modalità potrebbe cambiare, entro [hours] ore: inizio e fine degli orari, degli
     * eventi dei calendari scelti, della scelta a mano, e le mezzanotti (festivi e ferie cambiano col giorno).
     */
    fun nextChange(now: LocalDateTime, configs: List<ModeConfig>, manual: ManualChoice?, busy: List<Busy>, hours: Long = 48): LocalDateTime? {
        val limit = now.plusHours(hours)
        val out = mutableListOf<LocalDateTime>()
        manual?.until?.let { out += it }
        var day = now.toLocalDate().minusDays(1)
        while (!day.isAfter(limit.toLocalDate())) {
            out += day.atStartOfDay()
            configs.filter { it.schedule.enabled }.forEach { c ->
                out += day.atTime(c.schedule.start)
                out += (if (c.schedule.end.isBefore(c.schedule.start)) day.plusDays(1) else day).atTime(c.schedule.end)
            }
            day = day.plusDays(1)
        }
        val triggers = configs.mapNotNull { it.calendarTrigger }.toSet() + Busy.TRIP
        busy.filter { it.calendarId in triggers }.forEach { out += it.start; out += it.end }
        return out.filter { it.isAfter(now) && !it.isAfter(limit) }.minOrNull()
    }
}

/** Festività nazionali italiane (con Pasqua e Pasquetta). */
object ItalianHolidays {
    private val fixed = listOf(
        MonthDay.of(1, 1), MonthDay.of(1, 6), MonthDay.of(4, 25), MonthDay.of(5, 1), MonthDay.of(6, 2),
        MonthDay.of(8, 15), MonthDay.of(11, 1), MonthDay.of(12, 8), MonthDay.of(12, 25), MonthDay.of(12, 26),
    )

    fun isHoliday(day: LocalDate): Boolean {
        if (MonthDay.from(day) in fixed) return true
        val easter = easter(day.year)
        return day == easter || day == easter.plusDays(1)
    }

    /** Pasqua secondo il calendario gregoriano (algoritmo anonimo, Meeus/Jones/Butcher). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val dayOfMonth = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, dayOfMonth)
    }

    /** Un evento di tutto il giorno che segna le ferie: "Ferie", "In vacanza", "Vacanze". */
    fun isTimeOffTitle(title: String): Boolean {
        val t = title.lowercase()
        return "ferie" in t || "vacanz" in t
    }
}

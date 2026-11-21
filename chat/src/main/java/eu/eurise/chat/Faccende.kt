package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Faccende a turno (tavola FamFaccende), senza Android: si prova con i test.
 *
 * Le faccende del gruppo sono uno stato della stanza ([TYPE]), lo stesso per tutti: titolo, quando (giorni della settimana
 * o ogni N giorni), l'ora, le persone in giro e a chi tocca. «Fatto» segna chi e quando, e il turno passa da solo al
 * prossimo. Se l'ultima volta dovuta non è stata fatta, la faccenda torna in cima e lo vede anche chi l'ha messa.
 */
object Faccende {
    const val TYPE = "eu.eurise.chores"
    const val REMIND_BEFORE_MIN = 30L

    /** [days]: giorni della settimana (1 = lunedì … 7 = domenica); se vuoto, ogni [every] giorni da [start]. */
    data class Chore(
        val id: String, val title: String, val days: Set<Int>, val every: Int, val start: Long, val time: String,
        val people: List<String>, val turn: Int = 0, val doneAt: Long = 0, val doneBy: String = "", val by: String = "",
    )

    enum class Status { Todo, Done, Late, Free }

    fun whose(c: Chore): String? = c.people.getOrNull(c.turn.mod(c.people.size.coerceAtLeast(1)))
    fun next(c: Chore): String? = if (c.people.size < 2) whose(c) else c.people[(c.turn + 1).mod(c.people.size)]

    fun dueOn(c: Chore, day: LocalDate): Boolean =
        if (c.days.isNotEmpty()) day.dayOfWeek.value in c.days
        else c.every > 0 && (day.toEpochDay() - c.start).let { it >= 0 && it % c.every == 0L }

    /** L'ultimo giorno dovuto fino a [today] compreso (al massimo una settimana, o [every] giorni, indietro). */
    fun lastDue(c: Chore, today: LocalDate): LocalDate? =
        (0..maxOf(7, c.every).toLong()).map { today.minusDays(it) }.firstOrNull { dueOn(c, it) }

    fun nextDue(c: Chore, after: LocalDate): LocalDate? = (0..maxOf(7, c.every).toLong()).map { after.plusDays(it) }.firstOrNull { dueOn(c, it) }

    fun time(c: Chore): LocalTime = runCatching { LocalTime.parse(c.time) }.getOrDefault(LocalTime.of(20, 0))

    /** Fatta se dopo l'inizio dell'ultimo giorno dovuto; in ritardo se quel giorno è passato; libera se oggi non c'è. */
    fun status(c: Chore, now: LocalDateTime, toMs: (LocalDateTime) -> Long): Status {
        val today = now.toLocalDate()
        val last = lastDue(c, today) ?: return Status.Free
        if (c.doneAt >= toMs(last.atStartOfDay())) return Status.Done
        if (last < today) {
            // Se nel frattempo c'è un giorno libero non ancora dovuto, resta in ritardo finché non la fa qualcuno.
            return Status.Late
        }
        return Status.Todo
    }

    /** «Fatto»: chi e quando, e il turno passa al prossimo. */
    fun done(c: Chore, by: String, at: Long): Chore = c.copy(turn = if (c.people.isEmpty()) 0 else (c.turn + 1).mod(c.people.size), doneAt = at, doneBy = by)

    /** In cima le faccende in ritardo, poi da fare, poi fatte, poi quelle libere oggi. */
    fun sorted(list: List<Chore>, now: LocalDateTime, toMs: (LocalDateTime) -> Long): List<Chore> =
        list.sortedWith(compareBy({ listOf(Status.Late, Status.Todo, Status.Done, Status.Free).indexOf(status(it, now, toMs)) }, { it.time }))

    /** Il prossimo promemoria per [me]: 30 minuti prima della prossima volta in cui tocca a lui (e non è già fatta). */
    fun nextReminder(list: List<Chore>, me: String, now: LocalDateTime, toMs: (LocalDateTime) -> Long): Pair<Chore, LocalDateTime>? =
        list.filter { whose(it) == me }.mapNotNull { c ->
            val today = now.toLocalDate()
            val todayAt = today.atTime(time(c)).minusMinutes(REMIND_BEFORE_MIN)
            val doneToday = c.doneAt >= toMs(today.atStartOfDay())
            val day = if (dueOn(c, today) && !doneToday && todayAt.isAfter(now)) today else nextDue(c, today.plusDays(1)) ?: return@mapNotNull null
            c to day.atTime(time(c)).minusMinutes(REMIND_BEFORE_MIN)
        }.minByOrNull { it.second }

    fun daysLabel(c: Chore, names: Map<DayOfWeek, String>, everyDay: String, alternate: String, everyN: (Int) -> String): String = when {
        c.days.size == 7 -> everyDay
        c.days.isNotEmpty() -> c.days.sorted().joinToString(", ") { names[DayOfWeek.of(it)].orEmpty() }
        c.every == 1 -> everyDay
        c.every == 2 -> alternate
        else -> everyN(c.every)
    }

    fun encode(list: List<Chore>): JSONObject = JSONObject().put("chores", JSONArray().apply {
        list.forEach { c ->
            put(JSONObject().put("id", c.id).put("title", c.title).put("days", JSONArray(c.days.sorted())).put("every", c.every).put("start", c.start)
                .put("time", c.time).put("people", JSONArray(c.people)).put("turn", c.turn).put("doneAt", c.doneAt).put("doneBy", c.doneBy).put("by", c.by))
        }
    })

    fun decode(o: JSONObject?): List<Chore> = runCatching {
        val a = o?.optJSONArray("chores") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            val c = a.optJSONObject(i) ?: return@mapNotNull null
            val days = c.optJSONArray("days")?.let { d -> (0 until d.length()).map { d.optInt(it) }.filter { it in 1..7 }.toSet() }.orEmpty()
            val people = c.optJSONArray("people")?.let { p -> (0 until p.length()).map { p.optString(it) }.filter { it.isNotEmpty() } }.orEmpty()
            Chore(c.optString("id"), c.optString("title"), days, c.optInt("every"), c.optLong("start"), c.optString("time", "20:00"), people,
                c.optInt("turn"), c.optLong("doneAt"), c.optString("doneBy"), c.optString("by")).takeIf { it.id.isNotEmpty() && it.title.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}

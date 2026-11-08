package eu.eurise.clock.logic

import org.json.JSONObject

/**
 * Un timer. In corso ha [endsAt] (istante in millisecondi); in pausa ha solo [remainingMs].
 * Un Pomodoro ha [pomodoro] e [phase]: le fasi pari sono di lavoro, le dispari di pausa.
 */
data class ClockTimer(
    val id: Int,
    val label: String,
    val totalMs: Long,
    val endsAt: Long? = null,
    val remainingMs: Long = totalMs,
    val pomodoro: Pomodoro? = null,
    val phase: Int = 0,
) {
    val running: Boolean get() = endsAt != null

    fun remaining(now: Long): Long = endsAt?.let { (it - now).coerceAtLeast(0) } ?: remainingMs

    fun start(now: Long): ClockTimer = if (running) this else copy(endsAt = now + remainingMs)

    fun pause(now: Long): ClockTimer = if (!running) this else copy(endsAt = null, remainingMs = remaining(now))

    /** Aggiunge un minuto (o quanto si vuole), anche mentre corre. */
    fun plus(ms: Long, now: Long): ClockTimer =
        if (running) copy(endsAt = endsAt!! + ms, totalMs = totalMs + ms) else copy(remainingMs = remainingMs + ms, totalMs = totalMs + ms)

    val isWork: Boolean get() = pomodoro != null && phase % 2 == 0

    /** La fase seguente del Pomodoro, già avviata; nullo alla fine dell'ultimo giro. */
    fun nextPhase(now: Long): ClockTimer? {
        val p = pomodoro ?: return null
        val next = phase + 1
        if (next >= p.rounds * 2 - 1) return null
        val ms = (if (next % 2 == 0) p.workMinutes else p.restMinutes) * 60_000L
        return copy(phase = next, totalMs = ms, remainingMs = ms, endsAt = now + ms)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("label", label).put("total", totalMs).put("ends", endsAt ?: JSONObject.NULL).put("remaining", remainingMs)
        .put("pomodoro", pomodoro?.let { JSONObject().put("work", it.workMinutes).put("rest", it.restMinutes).put("rounds", it.rounds).put("dnd", it.quietDuringWork) } ?: JSONObject.NULL)
        .put("phase", phase)

    companion object {
        fun fromJson(o: JSONObject) = ClockTimer(
            id = o.getInt("id"),
            label = o.optString("label"),
            totalMs = o.getLong("total"),
            endsAt = if (o.isNull("ends")) null else o.getLong("ends"),
            remainingMs = o.getLong("remaining"),
            pomodoro = o.optJSONObject("pomodoro")?.let { Pomodoro(it.getInt("work"), it.getInt("rest"), it.getInt("rounds"), it.optBoolean("dnd")) },
            phase = o.optInt("phase"),
        )
    }
}

/** Pomodoro: [rounds] giri di lavoro da [workMinutes] con pause da [restMinutes]; silenzio durante il lavoro se scelto. */
data class Pomodoro(val workMinutes: Int = 25, val restMinutes: Int = 5, val rounds: Int = 4, val quietDuringWork: Boolean = false)

/** Durata leggibile di un timer: "4:12", "1:05:00". */
fun formatDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

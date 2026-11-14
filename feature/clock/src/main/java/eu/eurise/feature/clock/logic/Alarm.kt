package eu.eurise.feature.clock.logic

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Una sveglia.
 *
 * @property days giorni in cui suona; vuoto = una volta sola (la prossima volta che arriva quell'ora).
 * @property skipHolidays tace nei giorni festivi italiani.
 * @property skipVacation tace nei giorni con un evento "Ferie" (o "Vacanza") nel calendario.
 * @property earlier se c'è un impegno presto, la sera prima propone di anticipare (non lo fa da sola).
 * @property skipped data in cui la sveglia non suonerà, una volta sola (pulsante "Salta").
 * @property routine dopo lo spegnimento legge l'ora e gli impegni del giorno, poi fa partire la musica.
 * @property date giorno preciso di una sveglia che suona una volta sola (per esempio la partenza di un viaggio).
 * @property alfred sveglia di Alfred (tavola AlfSveglia): saluto con l'ora e il meteo, una massima, un brano del suo
 * catalogo nell'app di musica scelta; se la musica non parte, la suoneria.
 */
data class Alarm(
    val id: Int,
    val time: LocalTime,
    val label: String = "",
    val days: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val skipHolidays: Boolean = false,
    val skipVacation: Boolean = false,
    val earlier: Boolean = false,
    val sound: String? = null,
    val rampSeconds: Int = 60,
    val snoozeMinutes: Int = 10,
    val maxSnoozes: Int = 3,
    val challenge: Boolean = false,
    val vibrate: Boolean = true,
    val skipped: LocalDate? = null,
    val routine: Boolean = false,
    val date: LocalDate? = null,
    val alfred: Boolean = false,
) {
    val repeating: Boolean get() = days.isNotEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("time", time.toString()).put("label", label)
        .put("days", JSONArray(days.map { it.value }))
        .put("enabled", enabled).put("holidays", skipHolidays).put("vacation", skipVacation).put("earlier", earlier)
        .put("sound", sound ?: JSONObject.NULL).put("ramp", rampSeconds).put("snooze", snoozeMinutes).put("maxSnoozes", maxSnoozes)
        .put("challenge", challenge).put("vibrate", vibrate).put("skipped", skipped?.toString() ?: JSONObject.NULL)
        .put("routine", routine)
        .put("date", date?.toString() ?: JSONObject.NULL)
        .put("alfred", alfred)

    companion object {
        fun fromJson(o: JSONObject): Alarm = Alarm(
            id = o.getInt("id"),
            time = LocalTime.parse(o.getString("time")),
            label = o.optString("label"),
            days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { DayOfWeek.of(a.getInt(it)) }.toSet() }.orEmpty(),
            enabled = o.optBoolean("enabled", true),
            skipHolidays = o.optBoolean("holidays"),
            skipVacation = o.optBoolean("vacation"),
            earlier = o.optBoolean("earlier"),
            sound = o.optString("sound").takeIf { !o.isNull("sound") && it.isNotBlank() },
            rampSeconds = o.optInt("ramp", 60),
            snoozeMinutes = o.optInt("snooze", 10),
            maxSnoozes = o.optInt("maxSnoozes", 3),
            challenge = o.optBoolean("challenge"),
            vibrate = o.optBoolean("vibrate", true),
            skipped = o.optString("skipped").takeIf { !o.isNull("skipped") && it.isNotBlank() }?.let(LocalDate::parse),
            routine = o.optBoolean("routine"),
            date = o.optString("date").takeIf { !o.isNull("date") && it.isNotBlank() }?.let(LocalDate::parse),
            alfred = o.optBoolean("alfred"),
        )

        val Workdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
}

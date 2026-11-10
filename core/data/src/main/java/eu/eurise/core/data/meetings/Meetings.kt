package eu.eurise.core.data.meetings

import android.content.Context
import eu.eurise.core.data.model.Capture
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Quale nota appartiene a quale riunione. La chiave è l'occorrenza (evento più inizio), così una riunione
 * settimanale ha una nota per ogni settimana. Tutto resta sul telefono.
 */
class MeetingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("eurise_meetings", Context.MODE_PRIVATE)

    fun noteFor(eventId: Long, beginMillis: Long): Long? =
        prefs.getLong(key(eventId, beginMillis), -1L).takeIf { it > 0 }

    fun setNote(eventId: Long, beginMillis: Long, noteId: Long) =
        prefs.edit().putLong(key(eventId, beginMillis), noteId).apply()

    /** Vero dopo "Chiudi la riunione": le azioni sono già diventate attività. */
    fun isClosed(eventId: Long, beginMillis: Long): Boolean = prefs.getBoolean(key(eventId, beginMillis) + ":closed", false)

    fun setClosed(eventId: Long, beginMillis: Long, closed: Boolean) =
        prefs.edit().putBoolean(key(eventId, beginMillis) + ":closed", closed).apply()

    private fun key(eventId: Long, beginMillis: Long) = "$eventId:$beginMillis"
}

/**
 * Le azioni scritte negli appunti di una riunione: una riga con almeno una persona citata ("@Anna") e una data
 * ("entro venerdì", "il 20") diventa un'attività. Il resto della riga resta il titolo, con il nome senza chiocciola.
 */
object MeetingActions {

    data class Action(
        /** Posizione della riga negli appunti (da zero), per ritrovarla. */
        val line: Int,
        val title: String,
        val people: List<String>,
        val due: LocalDate,
        val time: LocalTime?,
    )

    private val BULLET = Regex("""^\s*(?:[-*•–]|\d+[.)]|\[\s?[xX]?\s?])\s*""")

    fun extract(notes: String, now: LocalDateTime): List<Action> =
        notes.lines().mapIndexedNotNull { index, raw ->
            val line = raw.replace(BULLET, "").trim()
            if (line.isEmpty() || '@' !in line) return@mapIndexedNotNull null
            val parsed = Capture.parse(line, now)
            val due = parsed.quick.due ?: return@mapIndexedNotNull null
            if (parsed.mentions.isEmpty()) return@mapIndexedNotNull null
            val title = parsed.title.trim().trimEnd(',', ';', '.', ':').trim()
            if (title.isEmpty()) return@mapIndexedNotNull null
            Action(index, title.replaceFirstChar { it.uppercase() }, parsed.mentions.map { it.name }.distinct(), due, parsed.quick.time)
        }
}

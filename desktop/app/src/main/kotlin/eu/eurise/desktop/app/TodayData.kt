package eu.eurise.desktop.app

import eu.eurise.core.data.model.QuickAdd
import eu.eurise.core.ponte.Json
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * I dati di «Oggi» sul computer (tavole PcOggi e PcOggiNotte). Finché Calendario e Attività non sincronizzano da soli
 * (CalDAV in `core/sync`), arrivano dal telefono con il Ponte: il riassunto [eu.eurise.core.ponte.Kind.Today] che
 * il telefono manda solo se l'utente ha acceso «Oggi sul computer», e le ultime cose passate dal telefono (`recent`).
 * Qui solo la lettura e le frasi, senza interfaccia, così si prova senza finestra (`TodayDataTest`).
 */
object TodayData {
    private val IT = Locale.ITALIAN
    private val HM = DateTimeFormatter.ofPattern("H:mm")

    data class Event(val start: LocalTime?, val end: LocalTime?, val title: String, val place: String, val day: LocalDate)
    data class Todo(val title: String, val due: LocalDate?)

    /** Il riassunto del telefono; [off] se sul telefono «Oggi sul computer» è spento. */
    data class Summary(val events: List<Event>, val tasks: List<Todo>, val tasksOpen: Int, val off: Boolean = false)

    /** Una cosa arrivata dal telefono (link, avviso, appunti, dettatura). */
    data class Recent(val kind: String, val title: String, val sub: String, val at: Long, val url: String?)

    /** Una riga `today` del Ponte; null se vuota o illeggibile. */
    fun parseSummary(line: String?): Summary? {
        val f = line?.takeIf { it.isNotBlank() }?.let(Json::read) ?: return null
        if (f["off"] == "1") return Summary(emptyList(), emptyList(), 0, off = true)
        val today = f["day"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val events = f["events"].orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull { l ->
            val p = l.split('\t')
            val title = p.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Event(time(p.getOrNull(0)), time(p.getOrNull(1)), title, p.getOrNull(3).orEmpty(),
                p.getOrNull(4)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today)
        }
        val tasks = f["tasks"].orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull { l ->
            val p = l.split('\t')
            val title = p.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Todo(title, p.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
        }
        return Summary(events, tasks, f["tasksOpen"]?.toIntOrNull() ?: tasks.size)
    }

    /** Le righe `recent` del Ponte (già dalla più nuova), al massimo [max]. */
    fun parseRecent(lines: List<String>, max: Int = 4): List<Recent> = lines.mapNotNull { l ->
        val f = Json.read(l) ?: return@mapNotNull null
        Recent(f["k"].orEmpty(), f["title"].orEmpty(), f["sub"].orEmpty(), f["at"]?.toLongOrNull() ?: 0, f["url"])
    }.take(max)

    private fun time(s: String?) = s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    /** «Buongiorno, Marco.» (senza nome se il sistema non lo conosce). */
    fun greeting(now: LocalDateTime, name: String?): String {
        val word = when (now.hour) { in 5..12 -> "Buongiorno"; in 13..17 -> "Buon pomeriggio"; else -> "Buonasera" }
        return if (name.isNullOrBlank()) "$word." else "$word, $name."
    }

    /** «Oggi hai 4 impegni, il primo alle 11.» Contano solo gli impegni di oggi non ancora finiti. */
    fun headline(events: List<Event>, now: LocalDateTime): String {
        val today = events.filter { it.day == now.toLocalDate() }
        if (today.isEmpty()) return "Oggi non hai altri impegni."
        val timed = today.filter { it.start != null }.sortedBy { it.start }
        val first = timed.firstOrNull()
        val started = first != null && !first.start!!.isAfter(now.toLocalTime())
        val next = timed.firstOrNull { it.start!!.isAfter(now.toLocalTime()) }
        val count = if (today.size == 1) "un impegno" else "${today.size} impegni"
        return when {
            first == null -> "Oggi hai $count, per tutto il giorno."
            today.size == 1 -> if (started) "Oggi hai un impegno, adesso." else "Oggi hai un impegno, alle ${hour(first!!.start!!)}."
            !started -> "Oggi hai $count, il primo alle ${hour(first!!.start!!)}."
            next != null -> "Oggi hai ancora $count, il prossimo alle ${hour(next.start!!)}."
            else -> "Oggi hai ancora $count."
        }
    }

    /** «11» per le 11:00, «15:30» altrimenti. */
    fun hour(t: LocalTime): String = if (t.minute == 0) t.hour.toString() else t.format(HM)

    /** «Domenica 15 novembre». */
    fun dateLine(d: LocalDate): String =
        d.dayOfWeek.getDisplayName(TextStyle.FULL, IT).replaceFirstChar { it.uppercase() } + " ${d.dayOfMonth} " +
            d.month.getDisplayName(TextStyle.FULL, IT)

    /** «11:00 Riunione» per oggi, «domani 8:30 Treno» per domani, solo il titolo se dura tutto il giorno. */
    fun eventTitle(e: Event, today: LocalDate): String {
        val time = e.start?.format(DateTimeFormatter.ofPattern("HH:mm"))
        val day = if (e.day == today) null else if (e.day == today.plusDays(1)) "domani" else dayShort(e.day)
        return listOfNotNull(day, time, e.title).joinToString(" ")
    }

    fun eventSub(e: Event): String = listOfNotNull(
        e.place.takeIf { it.isNotBlank() },
        if (e.start == null) "tutto il giorno" else e.end?.let { "fino alle ${it.format(HM)}" },
    ).joinToString(" · ")

    /** «tra 1 h 48» per il prossimo impegno entro tre ore, «adesso» se è in corso; altrimenti null. */
    fun countdown(e: Event, now: LocalDateTime): String? {
        val start = e.start ?: return null
        val at = e.day.atTime(start)
        val end = e.end?.let { e.day.atTime(it) }
        if (!at.isAfter(now)) return if (end == null || end.isAfter(now)) "adesso" else null
        val m = Duration.between(now, at).toMinutes()
        return when {
            m > 180 -> null
            m < 60 -> "tra ${m.coerceAtLeast(1)} min"
            else -> "tra ${m / 60} h" + if (m % 60 != 0L) " ${m % 60}" else ""
        }
    }

    /** Le attività da fare oggi: scadute e di oggi. */
    fun dueToday(s: Summary, today: LocalDate) = s.tasks.filter { it.due != null && !it.due.isAfter(today) }

    /** «scaduta ieri», «scaduta il 12 nov», «oggi», «domani», «ven 20 nov», «senza data». */
    fun dueLine(t: Todo, today: LocalDate): String {
        val d = t.due ?: return "senza data"
        return when {
            d == today -> "oggi"
            d == today.minusDays(1) -> "scaduta ieri"
            d.isBefore(today) -> "scaduta il ${d.dayOfMonth} ${month(d)}"
            d == today.plusDays(1) -> "domani"
            else -> dayShort(d)
        }
    }

    fun overdue(t: Todo, today: LocalDate) = t.due != null && t.due.isBefore(today)

    private fun month(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, IT).trimEnd('.')
    private fun dayShort(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, IT).trimEnd('.') + " ${d.dayOfMonth} ${month(d)}"

    /** «2 minuti fa», «alle 8:50», «ieri». */
    fun ago(at: Long, now: Long = System.currentTimeMillis()): String {
        val m = (now - at) / 60_000
        val then = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        val nowDate = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return when {
            m < 1 -> "adesso"
            m == 1L -> "un minuto fa"
            m < 60 -> "$m minuti fa"
            then.toLocalDate() == nowDate -> "alle ${then.toLocalTime().format(HM)}"
            then.toLocalDate() == nowDate.minusDays(1) -> "ieri"
            else -> "${then.dayOfMonth} ${month(then.toLocalDate())}"
        }
    }

    /**
     * «Scrivi come parli» come appuntamento: giorno e ora letti con la stessa [QuickAdd] del telefono. Senza giorno è
     * oggi; senza ora è un appuntamento di tutto il giorno. Restituisce i campi di [eu.eurise.core.ponte.Kind.Event].
     */
    fun eventFields(text: String, now: LocalDateTime): Map<String, String>? {
        val r = QuickAdd.parse(text, now)
        val title = r.title.trim().ifBlank { return null }
        val day = r.due ?: now.toLocalDate()
        val start = r.time?.let { day.atTime(it).toString().take(16) } ?: day.toString()
        val end = r.time?.let { day.atTime(it).plusHours(1).toString().take(16) }
        return mapOf("title" to title, "start" to start) + (end?.let { mapOf("end" to it) } ?: emptyMap())
    }

    /** Come la frase sarà capita, per l'anteprima sotto il campo: «giovedì 19 novembre alle 15 · Dentista». */
    fun eventPreview(text: String, now: LocalDateTime): String? {
        val r = QuickAdd.parse(text, now)
        if (r.title.isBlank()) return null
        val day = r.due ?: now.toLocalDate()
        val whenText = when (day) {
            now.toLocalDate() -> "oggi"
            now.toLocalDate().plusDays(1) -> "domani"
            else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, IT) + " ${day.dayOfMonth} " + day.month.getDisplayName(TextStyle.FULL, IT)
        } + (r.time?.let { " alle ${hour(it)}" } ?: ", tutto il giorno")
        return "$whenText · ${r.title.trim()}"
    }
}

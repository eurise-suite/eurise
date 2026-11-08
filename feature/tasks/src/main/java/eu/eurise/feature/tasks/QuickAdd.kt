package eu.eurise.feature.tasks

import eu.eurise.core.data.model.Recurrence
import eu.eurise.core.data.model.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Legge un'attività scritta "come si parla": "Pagare il bollo il 30 novembre alle 18 ogni anno !importante #casa".
 * Riconosce giorno, ora, ripetizione, importanza ed elenco; il resto diventa il titolo. Ogni pezzo riconosciuto
 * ricorda dove stava nel testo, così l'aggiunta rapida lo può evidenziare e togliere.
 */
internal object QuickAdd {

    enum class Kind { Date, Time, Repeat, Priority, List }

    /** Un pezzo riconosciuto: [start] e [end] sono le posizioni nel testo scritto. */
    data class Piece(val kind: Kind, val start: Int, val end: Int)

    data class Result(
        val title: String,
        val due: LocalDate? = null,
        val time: LocalTime? = null,
        val repeat: String? = null,
        val priority: Int = Task.PRIORITY_NONE,
        /** Nome dell'elenco così come esiste (non come è stato scritto). */
        val listName: String? = null,
        val pieces: List<Piece> = emptyList(),
    )

    private const val B = """(?<![\p{L}\p{N}])"""
    private const val E = """(?![\p{L}\p{N}])"""
    private const val OPTS = "(?iu)"

    private val WEEKDAYS = linkedMapOf(
        "lunedì" to DayOfWeek.MONDAY, "lunedi" to DayOfWeek.MONDAY,
        "martedì" to DayOfWeek.TUESDAY, "martedi" to DayOfWeek.TUESDAY,
        "mercoledì" to DayOfWeek.WEDNESDAY, "mercoledi" to DayOfWeek.WEDNESDAY,
        "giovedì" to DayOfWeek.THURSDAY, "giovedi" to DayOfWeek.THURSDAY,
        "venerdì" to DayOfWeek.FRIDAY, "venerdi" to DayOfWeek.FRIDAY,
        "sabato" to DayOfWeek.SATURDAY, "domenica" to DayOfWeek.SUNDAY,
    )
    private val MONTHS = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )
    private val DAY_NAMES = WEEKDAYS.keys.joinToString("|")
    private val MONTH_NAMES = MONTHS.joinToString("|")

    private val REPEAT = Regex(
        "$OPTS$B(?:ogni\\s+(?:(\\d+)\\s+)?(giorni?|settimane?|mesi?|anni?|$DAY_NAMES)|tutti\\s+i\\s+giorni|" +
            "nei\\s+giorni\\s+feriali|dal\\s+luned[iì]\\s+al\\s+venerd[iì])$E"
    )
    private val PRIORITY = Regex("$OPTS(?<![\\p{L}\\p{N}])!(importante|alta|urgente|media)$E")
    private val LIST = Regex("""(?<![\p{L}\p{N}&/])#([\p{L}\p{N}][\p{L}\p{N}_-]*)""")
    private val TIME = Regex("$OPTS$B(?:alle|all'|per\\s+le|verso\\s+le)\\s*(\\d{1,2})(?:[:.](\\d{2}))?$E|$B(a\\s+)?mezzogiorno$E")
    private val DATE = Regex(
        "$OPTS$B(?:entro\\s+|per\\s+)?(?:" +
            "(oggi|stasera|domani|dopodomani)" +
            "|(?:(?:il|lo|la)\\s+)?($DAY_NAMES)(?:\\s+prossim[oa])?" +
            "|(?:il\\s+|l')?(\\d{1,2})\\s+($MONTH_NAMES)(?:\\s+(\\d{4}))?" +
            "|(?:il\\s+|l')?(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?" +
            "|tra\\s+(\\d+|una?)\\s+(giorni?|settimane?)" +
            ")$E"
    )

    /**
     * @param lists nomi degli elenchi esistenti: "#casa" vale solo se c'è un elenco "Casa".
     */
    fun parse(text: String, now: LocalDateTime, lists: List<String> = emptyList()): Result {
        val today = now.toLocalDate()
        val pieces = mutableListOf<Piece>()
        fun free(range: IntRange) = pieces.none { range.first < it.end && it.start <= range.last }

        var repeat: Recurrence? = null
        var repeatDay: DayOfWeek? = null
        REPEAT.find(text)?.let { m ->
            val unit = m.groupValues[2].lowercase()
            val n = m.groupValues[1].toIntOrNull() ?: 1
            val whole = m.value.lowercase()
            repeat = when {
                whole.startsWith("tutti") -> Recurrence(Recurrence.Frequency.Daily)
                whole.startsWith("nei") || whole.startsWith("dal") ->
                    Recurrence(Recurrence.Frequency.Weekly, days = Recurrence.WORKDAYS)
                unit.startsWith("giorn") -> Recurrence(Recurrence.Frequency.Daily, n)
                unit.startsWith("settiman") -> Recurrence(Recurrence.Frequency.Weekly, n)
                unit.startsWith("mes") -> Recurrence(Recurrence.Frequency.Monthly, n)
                unit.startsWith("ann") -> Recurrence(Recurrence.Frequency.Yearly, n)
                else -> WEEKDAYS[unit]?.let { day ->
                    repeatDay = day
                    Recurrence(Recurrence.Frequency.Weekly, n, setOf(day))
                }
            }
            if (repeat != null) pieces += Piece(Kind.Repeat, m.range.first, m.range.last + 1)
        }

        var priority = Task.PRIORITY_NONE
        PRIORITY.find(text)?.let { m ->
            priority = if (m.groupValues[1].lowercase() == "media") Task.PRIORITY_MEDIUM else Task.PRIORITY_HIGH
            pieces += Piece(Kind.Priority, m.range.first, m.range.last + 1)
        }

        var listName: String? = null
        LIST.findAll(text).firstNotNullOfOrNull { m ->
            val wanted = normalize(m.groupValues[1])
            lists.firstOrNull { normalize(it) == wanted }?.let { m to it }
        }?.let { (m, name) ->
            listName = name
            pieces += Piece(Kind.List, m.range.first, m.range.last + 1)
        }

        var time: LocalTime? = null
        TIME.findAll(text).firstOrNull { free(it.range) }?.let { m ->
            val hour = m.groupValues[1].toIntOrNull()
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            time = when {
                hour == null -> LocalTime.NOON
                hour in 0..23 && minute in 0..59 -> LocalTime.of(hour, minute)
                else -> null
            }
            if (time != null) pieces += Piece(Kind.Time, m.range.first, m.range.last + 1)
        }

        var due: LocalDate? = null
        DATE.findAll(text).firstOrNull { free(it.range) }?.let { m ->
            due = dateOf(m, today)
            if (due != null) {
                pieces += Piece(Kind.Date, m.range.first, m.range.last + 1)
                if (m.groupValues[1].lowercase() == "stasera" && time == null) time = LocalTime.of(20, 0)
            }
        }

        // Senza giorno: un'ora già passata vale per domani; una ripetizione parte dal suo primo giorno utile.
        if (due == null) {
            due = when {
                repeatDay != null -> today.with(TemporalAdjusters.next(repeatDay!!))
                repeat != null -> today
                time != null -> if (time!!.isAfter(now.toLocalTime())) today else today.plusDays(1)
                else -> null
            }
        }

        return Result(
            title = titleWithout(text, pieces),
            due = due,
            time = time,
            repeat = repeat?.toRule(),
            priority = priority,
            listName = listName,
            pieces = pieces.sortedBy { it.start },
        )
    }

    private fun dateOf(m: MatchResult, today: LocalDate): LocalDate? {
        val g = m.groupValues
        g[1].lowercase().takeIf { it.isNotEmpty() }?.let {
            return when (it) {
                "oggi", "stasera" -> today
                "domani" -> today.plusDays(1)
                else -> today.plusDays(2)
            }
        }
        g[2].lowercase().takeIf { it.isNotEmpty() }?.let { name ->
            return WEEKDAYS[name]?.let { today.with(TemporalAdjusters.next(it)) }
        }
        if (g[3].isNotEmpty()) {
            val month = MONTHS.indexOf(g[4].lowercase()) + 1
            return dayOf(g[3].toInt(), month, g[5].toIntOrNull(), today)
        }
        if (g[6].isNotEmpty()) {
            val year = g[8].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            return dayOf(g[6].toInt(), g[7].toInt(), year, today)
        }
        if (g[9].isNotEmpty()) {
            val n = g[9].toIntOrNull() ?: 1
            return if (g[10].lowercase().startsWith("settiman")) today.plusWeeks(n.toLong()) else today.plusDays(n.toLong())
        }
        return null
    }

    /** Un giorno senza anno è il prossimo che deve ancora arrivare (oggi compreso). */
    private fun dayOf(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? = runCatching {
        if (year != null) return@runCatching LocalDate.of(year, month, day)
        val thisYear = LocalDate.of(today.year, month, day)
        if (thisYear.isBefore(today)) LocalDate.of(today.year + 1, month, day) else thisYear
    }.getOrNull()

    private fun normalize(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

    private fun titleWithout(text: String, pieces: List<Piece>): String {
        val sb = StringBuilder()
        var last = 0
        pieces.sortedBy { it.start }.forEach { p ->
            if (p.start >= last) {
                sb.append(text, last, p.start)
                last = p.end
            }
        }
        sb.append(text.substring(last.coerceAtMost(text.length)))
        return sb.toString().replace(Regex("\\s+"), " ").trim().trimEnd(',', ';', '-').trim()
    }
}

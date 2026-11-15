package eu.eurise.feature.assistant

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un impegno del calendario come lo legge Alfred. */
data class DataEvent(
    val title: String,
    val start: LocalDateTime,
    val allDay: Boolean,
    val place: String,
    val calendar: String,
    val color: Int,
    val eventId: Long = 0,
    val beginMillis: Long = 0,
)

/** Una cosa da fare con il nome del suo elenco. */
data class DataTask(val title: String, val due: LocalDate?, val list: String)

/** Una chiamata del registro: [name] vuoto se il numero non è in rubrica. */
data class DataCall(val name: String, val number: String, val at: LocalDateTime, val missed: Boolean)

/** Una nota trovata: [snippet] è il pezzo con la parola cercata. */
data class DataNote(val id: Long, val title: String, val snippet: String)

/** Una riga della risposta; [open] è la sezione di Eurise che il tocco apre (es. «note:12»). */
data class DataRow(val title: String, val detail: String, val tint: Long, val open: String? = null)

/**
 * La risposta di Alfred a una domanda sui dati (tavola AlfDati): la frase in testa, le righe, le proposte, ciò che si
 * legge a voce e la sezione che apre il pulsante. [sources] elenca dove si è cercato (sempre e solo sul telefono).
 */
data class DataAnswer(
    val ask: Command.Ask,
    val lead: String,
    val spoken: String,
    val rows: List<DataRow>,
    val chips: List<Chip>,
    val open: String,
    val sources: String,
    /** Niente trovato: si propone di chiedere altrove. */
    val empty: Boolean = false,
    /** Il permesso che manca per leggere (calendario o registro chiamate), se manca. */
    val needs: String? = null,
)

/**
 * Le frasi delle risposte sui dati: cortesi e asciutte, nella voce di Alfred (del «lei»). Solo testo, per i test;
 * i dati li raccoglie [Records].
 */
object DataWriter {
    private val IT = Locale.ITALIAN
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE d", IT)
    private val SHORT = DateTimeFormatter.ofPattern("EEE d", IT)
    private val LONG = DateTimeFormatter.ofPattern("EEEE d MMMM", IT)

    const val CAL_TINT = 0xFF3E5C8A
    const val TASK_TINT = 0xFFC9A04B
    const val NOTE_TINT = 0xFF7A4A2A
    const val CALL_TINT = 0xFF2F6B45
    const val MISSED_TINT = 0xFFC2453F

    private val NUMBERS = listOf("zero", "un", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci")

    /** «un impegno», «tre impegni», «una cosa da fare», «due cose da fare». */
    internal fun count(n: Int, one: String, many: String, feminine: Boolean = false): String {
        val word = when {
            n == 1 && feminine -> "una"
            n in 0..10 -> NUMBERS[n]
            else -> n.toString()
        }
        return "$word ${if (n == 1) one else many}"
    }

    /** «Oggi», «Domani», «Dopodomani», «Martedì 17». */
    internal fun dayName(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Oggi"
        today.plusDays(1) -> "Domani"
        today.plusDays(2) -> "Dopodomani"
        today.minusDays(1) -> "Ieri"
        else -> day.format(WEEKDAY).replaceFirstChar { it.titlecase(IT) }
    }

    internal fun time(t: LocalDateTime): String = if (t.minute == 0) "${t.hour}" else "%d:%02d".format(t.hour, t.minute)
    private fun clock(t: LocalDateTime) = "%d:%02d".format(t.hour, t.minute)

    // --- «Cosa ho domani?» ---

    fun agenda(ask: Command.Ask, now: LocalDateTime, events: List<DataEvent>, tasks: List<DataTask>): DataAnswer {
        val today = now.toLocalDate()
        val from = ask.day ?: today
        val week = ask.days > 1
        val label = when {
            !week -> dayName(from, today)
            from == today -> "Questa settimana"
            else -> "La settimana prossima"
        }
        // Oggi contano gli impegni non ancora finiti: quelli passati si dicono a parte.
        val passed = if (!week && from == today) events.count { !it.allDay && it.start.isBefore(now.minusMinutes(30)) } else 0
        val shown = events.filter { week || from != today || it.allDay || !it.start.isBefore(now.minusMinutes(30)) }
            .sortedWith(compareBy({ it.start.toLocalDate() }, { !it.allDay }, { it.start }))
        val rows = buildList {
            shown.forEach { e ->
                val day = if (week) e.start.format(SHORT).replaceFirstChar { it.titlecase(IT) } + " · " else ""
                val hour = if (e.allDay) "Tutto il giorno" else clock(e.start)
                val detail = e.place.takeIf { it.isNotBlank() }?.replace('\n', ' ') ?: "in calendario ${e.calendar}".takeIf { e.calendar.isNotBlank() }.orEmpty()
                add(DataRow("$day$hour · ${e.title}", detail, e.color.toLong() and 0xFFFFFFFFL, "calendar"))
            }
            tasks.forEach { t ->
                val late = t.due != null && t.due.isBefore(from)
                val detail = listOfNotNull(if (late) "in ritardo" else null, t.list.takeIf { it.isNotBlank() }?.let { "nell'elenco $it" }).joinToString(" · ")
                add(DataRow("Da fare · ${t.title}", detail, TASK_TINT, "tasks"))
            }
        }
        val parts = listOfNotNull(
            shown.size.takeIf { it > 0 }?.let { count(it, "impegno", "impegni") },
            tasks.size.takeIf { it > 0 }?.let { count(it, "cosa da fare", "cose da fare", feminine = true) },
        )
        val lead = when {
            parts.isEmpty() && passed > 0 -> "$label non le resta altro: gli impegni sono passati."
            parts.isEmpty() -> "$label l'agenda è libera."
            passed > 0 -> "$label le ${if (shown.size + tasks.size == 1) "resta" else "restano"} ${parts.joinToString(" e ")}."
            else -> "$label ha ${parts.joinToString(" e ")}."
        }
        val spoken = buildString {
            append(lead)
            val timed = shown.filter { !it.allDay }
            if (timed.isNotEmpty()) {
                append(' ')
                append(timed.joinToString(", ") { e ->
                    val d = if (week) e.start.format(WEEKDAY) + " " else ""
                    "${d}alle ${time(e.start)} ${e.title}"
                }.replaceFirstChar { it.titlecase(IT) }).append('.')
            }
            shown.filter { it.allDay }.takeIf { it.isNotEmpty() }?.let { append(" Per tutto il giorno: ").append(it.joinToString(", ") { e -> e.title }).append('.') }
            if (tasks.isNotEmpty()) append(" Da fare: ").append(tasks.take(5).joinToString(", ") { it.title }).append('.')
        }
        return DataAnswer(ask, lead, spoken, rows, proposals(shown), "calendar", "Calendario e Attività", empty = rows.isEmpty())
    }

    /** Le proposte sotto la risposta: la strada per il primo impegno con un luogo, l'avviso a chi si vede. */
    internal fun proposals(events: List<DataEvent>): List<Chip> = buildList {
        events.firstOrNull { it.place.isNotBlank() }?.let { e ->
            add(Chip("Strada per ${e.place.substringBefore(',').substringBefore('\n').trim()}", Command.Navigate(e.place.replace('\n', ' '))))
        }
        events.firstNotNullOfOrNull { e -> WITH_PERSON.find(e.title)?.groupValues?.get(1) }?.let { who ->
            add(Chip("Avvisa $who", Command.Message(who, "", null)))
        }
    }

    private val WITH_PERSON = Regex("(?u)(?<![\\p{L}])(?:con|da)\\s+(\\p{Lu}[\\p{L}'’]+)")

    // --- «Quando ho il dentista?» ---

    fun whenIs(ask: Command.Ask, now: LocalDateTime, events: List<DataEvent>, tasks: List<DataTask>): DataAnswer {
        val today = now.toLocalDate()
        val what = ask.about.replaceFirstChar { it.lowercase(IT) }
        val next = events.filter { (it.allDay && !it.start.toLocalDate().isBefore(today)) || !it.start.isBefore(now) }.sortedBy { it.start }
        val past = events.filter { it !in next }.sortedByDescending { it.start }
        val rows = (next + past).take(4).map { e ->
            val d = e.start.toLocalDate()
            val title = (if (e.allDay) dayName(d, today) else "${dayName(d, today)} · ${clock(e.start)}") + " · " + e.title
            DataRow(title, e.place.ifBlank { d.format(LONG) }, e.color.toLong() and 0xFFFFFFFFL, "calendar")
        } + tasks.take(3).map { t ->
            DataRow("Da fare · ${t.title}", t.due?.let { "entro " + dayName(it, today).lowercase(IT) } ?: "senza scadenza", TASK_TINT, "tasks")
        }
        val first = next.firstOrNull()
        val lead = when {
            first != null -> {
                val d = dayName(first.start.toLocalDate(), today)
                val at = if (first.allDay) "" else " alle ${time(first.start)}"
                "${capitalFirst(article(what))} è ${d.lowercase(IT)}$at."
            }
            past.isNotEmpty() -> "In agenda non c'è un prossimo «${ask.about}»: l'ultimo è stato ${dayName(past.first().start.toLocalDate(), today).lowercase(IT)}."
            tasks.isNotEmpty() -> "Non è in calendario, ma è tra le cose da fare${tasks.first().due?.let { ", entro " + dayName(it, today).lowercase(IT) } ?: ""}."
            else -> "Non trovo «${ask.about}» né in calendario né tra le cose da fare."
        }
        val spoken = if (first != null && first.place.isNotBlank()) "$lead A ${first.place.substringBefore(',').trim()}." else lead
        return DataAnswer(ask, lead, spoken, rows, proposals(listOfNotNull(first)), "calendar", "Calendario e Attività", empty = rows.isEmpty())
    }

    /** «il dentista», «la riunione», «l'aperitivo»: un articolo per la frase, quando la parola non ne ha. */
    internal fun article(what: String): String {
        val w = what.trim()
        if (w.isEmpty() || Regex("^(il|lo|la|l'|i|gli|le|un|una|uno)\\s", RegexOption.IGNORE_CASE).containsMatchIn("$w ")) return w
        val first = w.substringBefore(' ').lowercase(IT)
        if (first.endsWith("i") || first.endsWith("e") && !first.endsWith("ione")) return w
        return when {
            first.first() in "aeiouàèéìòù" -> "l'$w"
            first.endsWith("ista") || first.endsWith("ma") -> if (first.startsWith("s") && first.getOrNull(1)?.let { it !in "aeiou" } == true) "lo $w" else "il $w"
            (first.startsWith("s") && first.length > 1 && first[1] !in "aeiou") || first.startsWith("z") || first.startsWith("gn") || first.startsWith("ps") -> "lo $w"
            first.endsWith("a") || first.endsWith("ione") || first.endsWith("tà") -> "la $w"
            else -> "il $w"
        }
    }

    private fun capitalFirst(s: String) = s.replaceFirstChar { it.titlecase(IT) }

    // --- «Chi mi ha chiamato?» ---

    fun calls(ask: Command.Ask, now: LocalDateTime, calls: List<DataCall>): DataAnswer {
        val today = now.toLocalDate()
        val who = { c: DataCall -> c.name.ifBlank { c.number.ifBlank { "Numero sconosciuto" } } }
        val rows = calls.take(6).map { c ->
            val d = c.at.toLocalDate()
            val whenText = if (d == today) clock(c.at) else "${dayName(d, today)} · ${clock(c.at)}"
            DataRow(who(c), (if (c.missed) "persa · " else "ricevuta · ") + whenText, if (c.missed) MISSED_TINT else CALL_TINT, "calls")
        }
        val missed = calls.filter { it.missed }
        val names = calls.map(who).distinct()
        val span = ask.day?.let { dayName(it, today) } ?: "Nelle ultime ventiquattro ore"
        val lead = when {
            calls.isEmpty() -> "$span non l'ha chiamata nessuno."
            names.size == 1 -> "$span l'ha chiamata ${names.first()}" + (if (missed.isNotEmpty()) ", senza trovarla." else ".")
            else -> "$span l'hanno chiamata in ${NUMBERS.getOrElse(names.size) { names.size.toString() }}" +
                (if (missed.isNotEmpty()) ", ${count(missed.size, "chiamata persa", "chiamate perse", feminine = true)}." else ".")
        }
        val spoken = if (calls.isEmpty() || names.size == 1) lead else lead + " " + names.take(4).joinToString(", ") + "."
        val chips = calls.filter { it.missed }.ifEmpty { calls }.map { c -> c.name.ifBlank { c.number } }.filter { it.isNotBlank() }.distinct().take(2)
            .map { Chip("Richiama ${it.split(' ').first()}", Command.Call(it)) }
        return DataAnswer(ask, lead, spoken, rows, chips, "calls", "Telefono", empty = calls.isEmpty())
    }

    // --- «Cosa avevo scritto sul cancello?» ---

    fun notes(ask: Command.Ask, notes: List<DataNote>, tasks: List<DataTask>): DataAnswer {
        val rows = notes.take(4).map { n -> DataRow(n.title.ifBlank { "Nota" }, n.snippet, NOTE_TINT, "note:${n.id}") } +
            tasks.take(2).map { t -> DataRow("Da fare · ${t.title}", t.list.takeIf { it.isNotBlank() }?.let { "nell'elenco $it" }.orEmpty(), TASK_TINT, "tasks") }
        val lead = when {
            notes.isEmpty() && tasks.isEmpty() -> "Su «${ask.about}» non trovo niente nelle note."
            notes.size == 1 -> "Ho trovato una nota."
            notes.isEmpty() -> "Nelle note niente, ma c'è tra le cose da fare."
            else -> "Ho trovato ${count(notes.size, "nota", "note", feminine = true)}."
        }
        val spoken = notes.firstOrNull()?.let { "$lead ${it.snippet}" } ?: lead
        return DataAnswer(ask, lead, spoken, rows, emptyList(), notes.singleOrNull()?.let { "note:${it.id}" } ?: "notes", "Note e Attività",
            empty = rows.isEmpty())
    }

    /** Il pezzo di testo intorno alla prima parola cercata (una frase, al massimo 140 caratteri). */
    internal fun snippet(body: String, words: List<String>): String {
        val text = body.replace(Regex("\\s+"), " ").trim()
        val low = text.lowercase(IT)
        val at = words.map { low.indexOf(it.lowercase(IT)) }.filter { it >= 0 }.minOrNull() ?: return text.take(140)
        val boundary = low.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), at).let { if (it < 0) 0 else it + 1 }
        val start = boundary.coerceAtLeast(at - 80)
        val end = low.indexOfAny(charArrayOf('.', '!', '?'), at).let { if (it < 0) text.length else it + 1 }.coerceAtMost(start + 140)
        return (if (start > boundary) "…" else "") + text.substring(start, end).trim() + (if (end < text.length && text[end - 1] !in ".!?") "…" else "")
    }

    /** Le parole che contano in «codice del cancello»: «codice», «cancello». */
    internal fun keywords(about: String): List<String> =
        about.lowercase(IT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 && it !in STOP }

    private val STOP = setOf("del", "della", "dello", "dei", "degli", "delle", "con", "per", "che", "una", "uno", "nel", "nella", "sul", "sulla", "mio", "mia", "miei", "dal", "dalla",
        "appuntamento", "impegno", "evento")
}

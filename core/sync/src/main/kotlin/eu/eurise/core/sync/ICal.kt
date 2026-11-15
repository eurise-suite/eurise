package eu.eurise.core.sync

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Una proprietà iCalendar: `NOME;PARAM=valore:testo` (il testo è già senza escape). */
data class ICalProp(val name: String, val params: Map<String, String>, val value: String)

/** Un componente (VCALENDAR, VEVENT, VTODO, VALARM, VTIMEZONE…) con le sue proprietà e i figli, nell'ordine del file. */
class ICalComponent(val name: String) {
    val props = ArrayList<ICalProp>()
    val children = ArrayList<ICalComponent>()

    fun first(name: String): ICalProp? = props.firstOrNull { it.name == name }
    fun value(name: String): String? = first(name)?.value

    /** Mette [value] al posto della proprietà [name] (o la toglie se null), lasciando com'erano le altre. */
    fun set(name: String, value: String?, params: Map<String, String> = emptyMap()) {
        val i = props.indexOfFirst { it.name == name }
        props.removeAll { it.name == name }
        if (value == null) return
        val p = ICalProp(name, params, value)
        if (i >= 0) props.add(minOf(i, props.size), p) else props += p
    }
}

/** Quando: un giorno intero oppure un momento preciso. */
sealed interface CalTime {
    data class Day(val date: LocalDate) : CalTime
    data class At(val time: ZonedDateTime) : CalTime
}

/** Un appuntamento (VEVENT) o un'attività (VTODO) letto da un file iCalendar. */
data class CalItem(
    val uid: String,
    val todo: Boolean,
    val summary: String,
    val start: CalTime?,
    /** Fine per gli appuntamenti, scadenza per le attività. */
    val end: CalTime?,
    val location: String,
    val description: String,
    val status: String?,
    val completed: Boolean,
    val rrule: String?,
    val lastModified: Instant?,
)

/**
 * Lettore e scrittore iCalendar (RFC 5545) per CalDAV: tiene tutto il file (anche le proprietà che non conosce) così
 * che una modifica fatta sul computer non tolga nulla di ciò che ha scritto un'altra app. Righe ripiegate a 75 byte.
 */
object ICal {
    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val basic = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateOnly = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** Il componente principale (di solito VCALENDAR) di [text]. */
    fun parse(text: String): ICalComponent {
        val root = ICalComponent("ROOT")
        val stack = ArrayDeque<ICalComponent>().apply { addLast(root) }
        for (line in unfold(text)) {
            if (line.isBlank()) continue
            val p = parseLine(line) ?: continue
            when (p.name) {
                "BEGIN" -> ICalComponent(p.value.uppercase()).also { stack.last().children += it; stack.addLast(it) }
                "END" -> if (stack.size > 1) stack.removeLast()
                else -> stack.last().props += p
            }
        }
        return root.children.firstOrNull() ?: root
    }

    /** Gli appuntamenti e le attività di un file (più di uno se ci sono eccezioni a una ripetizione). */
    fun items(text: String, zone: ZoneId = ZoneId.systemDefault()): List<CalItem> =
        parse(text).children.filter { it.name == "VEVENT" || it.name == "VTODO" }.mapNotNull { item(it, zone) }

    fun item(c: ICalComponent, zone: ZoneId = ZoneId.systemDefault()): CalItem? {
        val uid = c.value("UID") ?: return null
        val todo = c.name == "VTODO"
        val status = c.value("STATUS")?.uppercase()
        return CalItem(
            uid = uid,
            todo = todo,
            summary = c.value("SUMMARY").orEmpty(),
            start = c.first("DTSTART")?.let { time(it, zone) },
            end = (if (todo) c.first("DUE") else c.first("DTEND"))?.let { time(it, zone) },
            location = c.value("LOCATION").orEmpty(),
            description = c.value("DESCRIPTION").orEmpty(),
            status = status,
            completed = todo && (status == "COMPLETED" || c.first("COMPLETED") != null),
            rrule = c.value("RRULE"),
            lastModified = (c.first("LAST-MODIFIED") ?: c.first("DTSTAMP"))?.let { (time(it, ZoneOffset.UTC) as? CalTime.At)?.time?.toInstant() },
        )
    }

    /** Legge una data o data e ora: `VALUE=DATE`, UTC con `Z`, con `TZID`, oppure locale (nel fuso [zone]). */
    fun time(p: ICalProp, zone: ZoneId): CalTime? = runCatching {
        val v = p.value.trim()
        when {
            p.params["VALUE"]?.uppercase() == "DATE" || v.length == 8 -> CalTime.Day(LocalDate.parse(v, dateOnly))
            v.endsWith("Z") -> CalTime.At(LocalDateTime.parse(v.dropLast(1), basic).atZone(ZoneOffset.UTC).withZoneSameInstant(zone))
            else -> {
                val tz = p.params["TZID"]?.let { runCatching { ZoneId.of(it.trim('"')) }.getOrNull() } ?: zone
                CalTime.At(LocalDateTime.parse(v, basic).atZone(tz).withZoneSameInstant(zone))
            }
        }
    }.getOrNull()

    /** Scrive una data per DTSTART, DTEND o DUE: i momenti sempre in UTC (nessun VTIMEZONE da mantenere). */
    fun format(t: CalTime): Pair<String, Map<String, String>> = when (t) {
        is CalTime.Day -> t.date.format(dateOnly) to mapOf("VALUE" to "DATE")
        is CalTime.At -> utc.format(t.time.toInstant()) to emptyMap()
    }

    /**
     * Un file nuovo con un appuntamento o un'attività. [uid] lo sceglie chi chiama (lo stesso del nome del file sul
     * server, come fanno il telefono e le altre app).
     */
    fun create(uid: String, todo: Boolean, summary: String, start: CalTime?, end: CalTime?, location: String = "",
               description: String = "", now: Instant = Instant.now()): String {
        val cal = ICalComponent("VCALENDAR")
        cal.props += ICalProp("VERSION", emptyMap(), "2.0")
        cal.props += ICalProp("PRODID", emptyMap(), "-//Eurise//Eurise per il computer//IT")
        val c = ICalComponent(if (todo) "VTODO" else "VEVENT")
        c.props += ICalProp("UID", emptyMap(), uid)
        c.props += ICalProp("DTSTAMP", emptyMap(), utc.format(now))
        cal.children += c
        update(c, summary, start, end, location, description, null, now)
        return write(cal)
    }

    /** Cambia i campi principali di [c] tenendo tutto il resto; aggiorna LAST-MODIFIED e SEQUENCE. */
    fun update(c: ICalComponent, summary: String, start: CalTime?, end: CalTime?, location: String, description: String,
               completed: Boolean?, now: Instant = Instant.now()) {
        c.set("SUMMARY", summary)
        if (start == null) c.set("DTSTART", null) else format(start).let { (v, p) -> c.set("DTSTART", v, p) }
        val endName = if (c.name == "VTODO") "DUE" else "DTEND"
        if (end == null) c.set(endName, null) else format(end).let { (v, p) -> c.set(endName, v, p) }
        c.set("LOCATION", location.ifEmpty { null })
        c.set("DESCRIPTION", description.ifEmpty { null })
        if (c.name == "VTODO" && completed != null) {
            c.set("STATUS", if (completed) "COMPLETED" else "NEEDS-ACTION")
            c.set("COMPLETED", if (completed) utc.format(now) else null)
            c.set("PERCENT-COMPLETE", if (completed) "100" else null)
        }
        c.set("LAST-MODIFIED", utc.format(now))
        val seq = c.value("SEQUENCE")?.toIntOrNull() ?: -1
        c.set("SEQUENCE", (seq + 1).toString())
    }

    fun write(c: ICalComponent): String = buildString {
        fun emit(comp: ICalComponent) {
            fold("BEGIN:${comp.name}")
            for (p in comp.props) {
                val params = p.params.entries.joinToString("") { (k, v) -> ";$k=${quoteParam(v)}" }
                fold("${p.name}$params:${escapeValue(p.name, p.value)}")
            }
            comp.children.forEach(::emit)
            fold("END:${comp.name}")
        }
        emit(c)
    }

    private fun StringBuilder.fold(line: String) {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 75) { append(line).append("\r\n"); return }
        // La prima riga tiene 75 byte, le seguenti 74 più lo spazio iniziale; mai spezzare un carattere.
        var first = true
        var i = 0
        var count = 0
        val sb = StringBuilder()
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val len = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            val limit = if (first) 75 else 74
            if (count + len > limit) {
                append(sb).append("\r\n ")
                sb.setLength(0); count = 0; first = false
            }
            sb.appendCodePoint(cp); count += len
            i += Character.charCount(cp)
        }
        append(sb).append("\r\n")
    }

    private val textProps = setOf("SUMMARY", "DESCRIPTION", "LOCATION", "COMMENT", "CATEGORIES", "CONTACT", "RESOURCES")

    private fun escapeValue(name: String, v: String): String =
        if (name in textProps) v.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;") else v

    private fun unescapeValue(name: String, v: String): String {
        if (name !in textProps) return v
        val sb = StringBuilder()
        var i = 0
        while (i < v.length) {
            val ch = v[i]
            if (ch == '\\' && i + 1 < v.length) {
                when (v[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(v[i + 1])
                }
                i += 2
            } else { sb.append(ch); i++ }
        }
        return sb.toString()
    }

    private fun quoteParam(v: String) = if (v.any { it == ':' || it == ';' || it == ',' }) "\"$v\"" else v

    /** Riunisce le righe ripiegate (quelle che iniziano con uno spazio o una tabulazione). */
    fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.split("\r\n", "\n")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) out[out.lastIndex] = out.last() + raw.substring(1)
            else out += raw
        }
        return out
    }

    /** `NOME;P1=a;P2="b:c":valore`: i due punti dentro le virgolette non contano. */
    private fun parseLine(line: String): ICalProp? {
        var quoted = false
        var colon = -1
        for (i in line.indices) {
            val ch = line[i]
            if (ch == '"') quoted = !quoted
            else if (ch == ':' && !quoted) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val parts = splitParams(head)
        val name = parts.first().uppercase()
        val params = LinkedHashMap<String, String>()
        for (p in parts.drop(1)) {
            val eq = p.indexOf('=')
            if (eq > 0) params[p.substring(0, eq).uppercase()] = p.substring(eq + 1).trim('"')
        }
        return ICalProp(name, params, unescapeValue(name, line.substring(colon + 1)))
    }

    private fun splitParams(head: String): List<String> {
        val out = ArrayList<String>()
        var quoted = false
        val sb = StringBuilder()
        for (ch in head) {
            when {
                ch == '"' -> { quoted = !quoted; sb.append(ch) }
                ch == ';' && !quoted -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(ch)
            }
        }
        out += sb.toString()
        return out
    }
}

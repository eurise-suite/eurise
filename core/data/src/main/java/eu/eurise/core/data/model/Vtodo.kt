package eu.eurise.core.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** I campi di un'attività che Eurise e CalDAV hanno in comune. [completedAt] è arrotondato al secondo. */
data class VtodoFields(
    val summary: String,
    val description: String = "",
    val due: LocalDate? = null,
    val time: LocalTime? = null,
    val done: Boolean = false,
    val completedAt: Long? = null,
    val priority: Int = 0,
    val rrule: String? = null,
    val parentUid: String? = null,
)

data class ParsedVtodo(val uid: String, val fields: VtodoFields)

/**
 * Lettura e scrittura di un'attività iCalendar (VTODO). Riscrivendo si parte dal testo arrivato dal server: tutto ciò che
 * Eurise non capisce (promemoria, categorie, altri campi di altre app) resta com'era.
 */
object Vtodo {
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val OWNED = setOf("SUMMARY", "DESCRIPTION", "DUE", "STATUS", "COMPLETED", "PRIORITY", "RRULE", "PERCENT-COMPLETE", "LAST-MODIFIED", "DTSTAMP")

    private class Prop(val name: String, val params: String, val value: String)

    private fun unfold(text: String): List<String> =
        text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split('\n').filter { it.isNotEmpty() }

    private fun prop(line: String): Prop {
        var quoted = false
        var colon = -1
        for (i in line.indices) {
            when {
                line[i] == '"' -> quoted = !quoted
                line[i] == ':' && !quoted -> { colon = i; break }
            }
        }
        if (colon < 0) return Prop(line.uppercase(), "", "")
        val head = line.substring(0, colon)
        return Prop(head.substringBefore(';').uppercase(), head.substringAfter(';', ""), line.substring(colon + 1))
    }

    private fun unescape(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                val n = text[++i]
                out.append(if (n == 'n' || n == 'N') '\n' else n)
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }

    private fun escape(text: String) = text.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    /** Le righe proprie del VTODO, senza quelle dei promemoria annidati. */
    private fun ownLines(lines: List<String>): List<String> {
        val start = lines.indexOfFirst { it.equals("BEGIN:VTODO", true) }
        if (start < 0) return emptyList()
        val out = ArrayList<String>()
        var depth = 0
        for (line in lines.drop(start + 1)) {
            if (line.equals("END:VTODO", true)) break
            if (line.startsWith("BEGIN:", true)) depth++
            else if (line.startsWith("END:", true)) depth--
            else if (depth == 0) out += line
        }
        return out
    }

    private fun isParent(p: Prop) = p.name == "RELATED-TO" && (!p.params.contains("RELTYPE", true) || p.params.contains("RELTYPE=PARENT", true))

    private fun moment(p: Prop, zone: ZoneId): LocalDateTime? = runCatching {
        val value = p.value.trim()
        when {
            value.endsWith("Z") -> Instant.from(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX").parse(value)).atZone(zone).toLocalDateTime()
            p.params.contains("TZID=", true) -> {
                val id = p.params.substringAfter("TZID=").substringBefore(';').trim('"')
                LocalDateTime.parse(value, LOCAL).atZone(ZoneId.of(id)).withZoneSameInstant(zone).toLocalDateTime()
            }
            else -> LocalDateTime.parse(value, LOCAL)
        }
    }.getOrNull()

    /** Null se il testo non contiene un'attività. */
    fun parse(ics: String, zone: ZoneId = ZoneId.systemDefault()): ParsedVtodo? {
        val props = ownLines(unfold(ics)).map(::prop)
        if (props.isEmpty()) return null
        val uid = props.firstOrNull { it.name == "UID" }?.value ?: return null
        fun text(name: String) = props.firstOrNull { it.name == name }?.value?.let(::unescape)
        val dueProp = props.firstOrNull { it.name == "DUE" }
        var due: LocalDate? = null
        var time: LocalTime? = null
        if (dueProp != null) {
            if (dueProp.value.length == 8) {
                due = runCatching { LocalDate.parse(dueProp.value, DATE) }.getOrNull()
            } else {
                moment(dueProp, zone)?.let { due = it.toLocalDate(); time = it.toLocalTime().withSecond(0).withNano(0) }
            }
        }
        val status = props.firstOrNull { it.name == "STATUS" }?.value?.uppercase()
        val completed = props.firstOrNull { it.name == "COMPLETED" }
        val done = status == "COMPLETED" || (status == null && completed != null)
        return ParsedVtodo(
            uid,
            VtodoFields(
                summary = text("SUMMARY").orEmpty(),
                description = text("DESCRIPTION").orEmpty(),
                due = due,
                time = time,
                done = done,
                completedAt = if (done) completed?.let { moment(it, ZoneOffset.UTC) }?.toEpochSecond(ZoneOffset.UTC)?.times(1000) else null,
                priority = props.firstOrNull { it.name == "PRIORITY" }?.value?.trim()?.toIntOrNull() ?: 0,
                rrule = props.firstOrNull { it.name == "RRULE" }?.value,
                parentUid = props.firstOrNull(::isParent)?.value,
            ),
        )
    }

    private fun fold(line: String): String {
        if (line.toByteArray().size <= 74) return line
        val out = StringBuilder()
        var size = 0
        for (c in line) {
            val bytes = c.toString().toByteArray().size
            if (size + bytes > 73 && !c.isLowSurrogate()) { out.append("\r\n "); size = 1 }
            out.append(c)
            size += bytes
        }
        return out.toString()
    }

    private fun ownedLines(f: VtodoFields, now: Long): List<String> = buildList {
        add("DTSTAMP:" + LOCAL.format(LocalDateTime.ofEpochSecond(now / 1000, 0, ZoneOffset.UTC)) + "Z")
        add("LAST-MODIFIED:" + LOCAL.format(LocalDateTime.ofEpochSecond(now / 1000, 0, ZoneOffset.UTC)) + "Z")
        add("SUMMARY:" + escape(f.summary))
        if (f.description.isNotEmpty()) add("DESCRIPTION:" + escape(f.description))
        f.due?.let { day ->
            val t = f.time
            add(if (t == null) "DUE;VALUE=DATE:" + DATE.format(day) else "DUE:" + LOCAL.format(day.atTime(t)))
        }
        add("STATUS:" + if (f.done) "COMPLETED" else "NEEDS-ACTION")
        if (f.done) {
            add("PERCENT-COMPLETE:100")
            f.completedAt?.let { add("COMPLETED:" + LOCAL.format(LocalDateTime.ofEpochSecond(it / 1000, 0, ZoneOffset.UTC)) + "Z") }
        }
        if (f.priority != 0) add("PRIORITY:${f.priority}")
        f.rrule?.let { add("RRULE:$it") }
        f.parentUid?.let { add("RELATED-TO:$it") }
    }

    /** Il testo da mandare al server. Con [base] si riparte da lì, così i campi sconosciuti non vanno persi. */
    fun render(base: String?, uid: String, fields: VtodoFields, now: Long): String {
        val owned = ownedLines(fields, now)
        val lines = if (base != null && parse(base) != null) {
            val source = unfold(base)
            val out = ArrayList<String>()
            var inTodo = false
            var depth = 0
            var placed = false
            for (line in source) {
                when {
                    line.equals("BEGIN:VTODO", true) -> { inTodo = true; out += line }
                    inTodo && line.equals("END:VTODO", true) -> {
                        if (!placed) out += owned
                        placed = true
                        inTodo = false
                        out += line
                    }
                    inTodo && line.startsWith("BEGIN:", true) -> {
                        if (!placed) out += owned
                        placed = true
                        depth++
                        out += line
                    }
                    inTodo && line.startsWith("END:", true) -> { depth--; out += line }
                    inTodo && depth == 0 -> prop(line).let { p -> if (p.name !in OWNED && !isParent(p)) out += line }
                    else -> out += line
                }
            }
            out
        } else {
            listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Eurise//Attivita//IT", "BEGIN:VTODO", "UID:$uid") + owned + listOf("END:VTODO", "END:VCALENDAR")
        }
        return lines.joinToString("\r\n", postfix = "\r\n") { fold(it) }
    }

    /** Unisce campo per campo: ciò che è cambiato da una parte sola passa; se è cambiato da tutte e due, vale il telefono. */
    fun merge(base: VtodoFields?, local: VtodoFields, remote: VtodoFields): VtodoFields {
        if (base == null) return local
        fun <T> pick(b: T, l: T, r: T) = if (l == b) r else l
        val due = pick(base.due to base.time, local.due to local.time, remote.due to remote.time)
        val done = pick(base.done to base.completedAt, local.done to local.completedAt, remote.done to remote.completedAt)
        return VtodoFields(
            pick(base.summary, local.summary, remote.summary),
            pick(base.description, local.description, remote.description),
            due.first, due.second, done.first, done.second,
            pick(base.priority, local.priority, remote.priority),
            pick(base.rrule, local.rrule, remote.rrule),
            pick(base.parentUid, local.parentUid, remote.parentUid),
        )
    }
}

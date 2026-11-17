package eu.eurise.core.data.calendar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Lettore dei file .ics (iCalendar, RFC 5545) per «Apri con › Calendario» (15 novembre): biglietti, inviti, calendari
 * scaricati. Legge i VEVENT con titolo, inizio, fine, luogo, descrizione e indirizzo; orari in UTC (`Z`), con `TZID` o
 * locali, convertiti nel fuso del telefono; `VALUE=DATE` per il giorno intero. Le ripetizioni non si espandono: si
 * importa la prima volta, e la regola finisce nella descrizione.
 */
object Ics {
    data class Event(
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val allDay: Boolean,
        val location: String,
        val description: String,
        val url: String,
        val repeats: Boolean,
        /** UID dell'appuntamento, vuoto se manca. */
        val uid: String = "",
        /** STATUS in maiuscolo (CONFIRMED, TENTATIVE, CANCELLED), vuoto se manca. */
        val status: String = "",
        /** Il nome di chi l'ha scritto (ORGANIZER con CN, o l'indirizzo), vuoto se manca. */
        val organizer: String = "",
    )

    private val basic = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateOnly = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<Event> {
        val out = ArrayList<Event>()
        var inside = false
        var props = ArrayList<Pair<String, String>>()
        var nested = 0
        for (line in unfold(text)) {
            val upper = line.uppercase()
            when {
                upper == "BEGIN:VEVENT" -> { inside = true; props = ArrayList(); nested = 0 }
                inside && upper.startsWith("BEGIN:") -> nested++
                inside && upper.startsWith("END:") && upper != "END:VEVENT" -> nested--
                upper == "END:VEVENT" -> { if (inside) event(props, zone)?.let(out::add); inside = false }
                inside && nested == 0 -> {
                    val colon = colonIndex(line).takeIf { it > 0 } ?: continue
                    props += line.substring(0, colon) to line.substring(colon + 1)
                }
            }
        }
        return out
    }

    private fun event(props: List<Pair<String, String>>, zone: ZoneId): Event? {
        fun prop(name: String) = props.firstOrNull { it.first.substringBefore(';').equals(name, true) }
        val (startKey, startValue) = prop("DTSTART") ?: return null
        val allDay = startKey.contains("VALUE=DATE", true) && !startKey.contains("VALUE=DATE-TIME", true) || startValue.trim().length == 8
        val start = time(startKey, startValue, zone) ?: return null
        val end = prop("DTEND")?.let { (k, v) -> time(k, v, zone) }
            ?: prop("DURATION")?.second?.let { d -> duration(d)?.let { start.plusSeconds(it) } }
            ?: if (allDay) start.plusDays(1) else start.plusHours(1)
        val rule = prop("RRULE")?.second
        val description = listOfNotNull(prop("DESCRIPTION")?.second?.let(::unescape), rule?.let { "Si ripete: $it" })
            .filter { it.isNotBlank() }.joinToString("\n\n")
        return Event(
            title = prop("SUMMARY")?.second?.let(::unescape)?.trim().orEmpty().ifBlank { "Appuntamento" },
            start = start,
            end = if (end.isAfter(start)) end else if (allDay) start.plusDays(1) else start.plusHours(1),
            allDay = allDay,
            location = prop("LOCATION")?.second?.let(::unescape)?.trim().orEmpty(),
            description = description,
            url = prop("URL")?.second?.trim().orEmpty(),
            repeats = rule != null,
            uid = prop("UID")?.second?.trim().orEmpty(),
            status = prop("STATUS")?.second?.trim()?.uppercase().orEmpty(),
            organizer = prop("ORGANIZER")?.let { (k, v) ->
                Regex("CN=(\"[^\"]*\"|[^;:]+)", RegexOption.IGNORE_CASE).find(k)?.groupValues?.get(1)?.trim('"')
                    ?: v.trim().removePrefix("mailto:").removePrefix("MAILTO:")
            }.orEmpty(),
        )
    }

    private fun time(key: String, raw: String, zone: ZoneId): LocalDateTime? = runCatching {
        val v = raw.trim()
        if (v.length == 8) return@runCatching LocalDate.parse(v, dateOnly).atStartOfDay()
        val utc = v.endsWith("Z")
        val local = LocalDateTime.parse(v.removeSuffix("Z").take(15), basic)
        val tzid = Regex("TZID=([^;:]+)", RegexOption.IGNORE_CASE).find(key)?.groupValues?.get(1)?.trim('"')
        val from: ZoneId? = when {
            utc -> ZoneOffset.UTC
            tzid != null -> runCatching { ZoneId.of(tzid) }.getOrNull()
            else -> null
        }
        if (from == null) local else local.atZone(from).withZoneSameInstant(zone).toLocalDateTime()
    }.getOrNull()

    /**
     * Lo stesso testo con lo STATUS del primo VEVENT cambiato in [status] (per esempio CANCELLED per una proposta
     * rifiutata): la riga c'è già e si sostituisce, altrimenti si aggiunge prima della fine dell'appuntamento. Il resto,
     * comprese le righe piegate, resta com'è.
     */
    fun withStatus(text: String, status: String): String {
        val lines = text.replace("\r\n", "\n").split('\n')
        val out = ArrayList<String>(lines.size + 1)
        var inside = false
        var nested = 0
        var done = false
        var skipping = false
        for (line in lines) {
            val upper = line.trimEnd().uppercase()
            if (skipping && (line.startsWith(" ") || line.startsWith("\t"))) continue
            skipping = false
            when {
                done -> out += line
                upper == "BEGIN:VEVENT" -> { inside = true; nested = 0; out += line }
                inside && upper.startsWith("BEGIN:") -> { nested++; out += line }
                inside && upper == "END:VEVENT" -> { out += "STATUS:$status"; out += line; done = true; inside = false }
                inside && upper.startsWith("END:") -> { nested--; out += line }
                inside && nested == 0 && (upper.startsWith("STATUS:") || upper.startsWith("STATUS;")) -> skipping = true
                else -> out += line
            }
        }
        return out.joinToString("\r\n")
    }

    /** `PT1H30M`, `P1D`, `-PT15M` → secondi. */
    private fun duration(d: String): Long? {
        val m = Regex("""([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""").matchEntire(d.trim()) ?: return null
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0
        val s = g(2) * 604_800 + g(3) * 86_400 + g(4) * 3_600 + g(5) * 60 + g(6)
        return if (m.groupValues[1] == "-") -s else s
    }

    /** I due punti fuori dalle virgolette dei parametri (`TZID="Europe/Rome":…`). */
    private fun colonIndex(line: String): Int {
        var quoted = false
        line.forEachIndexed { i, c -> if (c == '"') quoted = !quoted else if (c == ':' && !quoted) return i }
        return -1
    }

    internal fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { l ->
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) out[out.size - 1] = out.last() + l.substring(1)
            else if (l.isNotEmpty()) out += l
        }
        return out
    }

    internal fun unescape(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}

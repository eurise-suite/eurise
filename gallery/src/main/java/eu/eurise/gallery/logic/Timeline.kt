package eu.eurise.gallery.logic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Una foto o un video del telefono. [uri] è il testo dell'indirizzo in MediaStore. */
data class MediaItem(
    val id: Long,
    val uri: String,
    val isVideo: Boolean,
    val takenAt: Long,
    val durationMs: Long,
    val bucket: String,
    val favorite: Boolean,
)

/** Le foto di un giorno, dalla più recente. */
data class DayGroup(val date: LocalDate, val items: List<MediaItem>)

/** Un punto della barra di anni e mesi: [label] è l'anno se [isYear], altrimenti il numero del mese. */
data class RailMark(val label: Int, val isYear: Boolean, val groupIndex: Int)

/** I ricordi di una data: le foto di un giorno di [yearsAgo] anni fa. */
data class Memory(val yearsAgo: Int, val date: LocalDate, val items: List<MediaItem>)

fun groupByDay(items: List<MediaItem>, zone: ZoneId = ZoneId.systemDefault()): List<DayGroup> =
    items.sortedByDescending { it.takenAt }
        .groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
        .map { (date, list) -> DayGroup(date, list) }

/**
 * La barra a lato: un punto per mese, con l'anno al posto del primo mese di ogni anno precedente. Se i mesi sono
 * più di [max] si diradano, ma gli anni restano sempre.
 */
fun railMarks(groups: List<DayGroup>, max: Int = 10): List<RailMark> {
    val months = groups.withIndex().distinctBy { (_, g) -> g.date.year * 12 + g.date.monthValue }
    val marks = months.mapIndexed { i, (index, g) ->
        val newYear = i > 0 && months[i - 1].value.date.year != g.date.year
        if (newYear) RailMark(g.date.year, true, index) else RailMark(g.date.monthValue, false, index)
    }
    if (marks.size <= max) return marks
    val years = marks.filter { it.isYear }
    if (years.size >= max) return years.filterIndexed { i, _ -> i % (years.size / max + 1) == 0 }
    val room = max - years.size
    val others = marks.filterNot { it.isYear }
    val step = others.size.toFloat() / room
    val kept = (0 until room).map { others[(it * step).toInt()] }
    return (years + kept).distinct().sortedBy { it.groupIndex }
}

/** Le foto scattate in questo giorno e mese negli anni passati, dalle più vicine. */
fun onThisDay(groups: List<DayGroup>, today: LocalDate): List<Memory> =
    groups.filter { it.date.year < today.year && it.date.monthValue == today.monthValue && it.date.dayOfMonth == today.dayOfMonth }
        .map { Memory(today.year - it.date.year, it.date, it.items) }

/** Le cartelle del telefono, dalla più ricca: nome e foto. */
fun albums(items: List<MediaItem>): List<Pair<String, List<MediaItem>>> =
    items.groupBy { it.bucket }.toList().sortedByDescending { it.second.size }

/** Vero per ciò che di solito si riordina: gli screenshot. */
fun isToTidy(item: MediaItem): Boolean = item.bucket.contains("screenshot", ignoreCase = true)

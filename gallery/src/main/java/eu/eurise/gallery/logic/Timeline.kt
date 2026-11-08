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

/** Un punto della barra laterale: un mese di un anno, e il primo giorno di foto che gli appartiene. */
data class RailMark(val year: Int, val month: Int, val groupIndex: Int)

/** I ricordi di una data: le foto di un giorno di [yearsAgo] anni fa. */
data class Memory(val yearsAgo: Int, val date: LocalDate, val items: List<MediaItem>)

fun groupByDay(items: List<MediaItem>, zone: ZoneId = ZoneId.systemDefault()): List<DayGroup> =
    items.sortedByDescending { it.takenAt }
        .groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
        .map { (date, list) -> DayGroup(date, list) }

/** La barra a lato: un punto per mese, diradati in modo uniforme se sono più di [max]. */
fun railMarks(groups: List<DayGroup>, max: Int = 10): List<RailMark> {
    val months = groups.withIndex()
        .distinctBy { (_, g) -> g.date.year * 12 + g.date.monthValue }
        .map { (index, g) -> RailMark(g.date.year, g.date.monthValue, index) }
    if (months.size <= max) return months
    val step = (months.size - 1).toFloat() / (max - 1)
    return (0 until max).map { months[(it * step).toInt()] }
}

/**
 * Il giorno sotto il dito, con [fraction] da 0 (in alto) a 1 (in basso): su ogni etichetta si è esattamente in
 * quel mese, tra due etichette si procede in proporzione.
 */
fun groupAt(marks: List<RailMark>, groupCount: Int, fraction: Float): Int {
    if (marks.isEmpty() || groupCount == 0) return 0
    val position = (fraction.coerceIn(0f, 1f) * marks.size - 0.5f).coerceIn(0f, marks.size - 0.5f)
    val i = position.toInt()
    val from = marks[i].groupIndex
    val to = marks.getOrNull(i + 1)?.groupIndex ?: (groupCount - 1)
    // Dopo l'ultima etichetta resta mezzo passo per arrivare fino alle foto più vecchie.
    val progress = if (i == marks.lastIndex) (position - i) * 2 else position - i
    return (from + (to - from) * progress).toInt().coerceIn(0, groupCount - 1)
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

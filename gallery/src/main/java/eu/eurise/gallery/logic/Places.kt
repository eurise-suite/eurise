package eu.eurise.gallery.logic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.floor

/**
 * Un luogo è una casella di 0,1 gradi (circa 10 km). La chiave è anche ciò che esce dal telefono se si accendono i
 * nomi dei luoghi: mai la posizione esatta di una foto.
 */
fun placeCell(lat: Double, lon: Double): String =
    String.format(Locale.ROOT, "%.2f,%.2f", floor(lat * 10) / 10 + 0.05, floor(lon * 10) / 10 + 0.05)

fun cellLat(cell: String): Double = cell.substringBefore(',').toDouble()
fun cellLon(cell: String): Double = cell.substringAfter(',').toDouble()

/** "+45.4642+009.1900/" o "+45.4642+009.1900+120.5/", il formato ISO 6709 dei video. */
fun parseVideoLocation(text: String?): Pair<Double, Double>? {
    val match = Regex("""^([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(text ?: return null) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lon = match.groupValues[2].toDoubleOrNull() ?: return null
    return if (lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)) lat to lon else null
}

/** Un luogo con le sue foto, dalla più recente. [key] è il nome se c'è, altrimenti la casella. */
data class Place(val key: String, val name: String?, val cell: String, val items: List<MediaItem>)

/** I luoghi dal più ricco: caselle diverse con lo stesso nome diventano un luogo solo. */
fun places(media: List<MediaItem>, cells: Map<String, String>, names: Map<String, String>): List<Place> =
    media.mapNotNull { item -> cells[item.uri]?.let { it to item } }
        .groupBy { (cell, _) -> names[cell] ?: cell }
        .map { (key, list) ->
            val cell = list.first().first
            Place(key, names[cell], cell, list.map { it.second }.sortedByDescending { it.takenAt })
        }
        .sortedByDescending { it.items.size }

/** Il luogo più presente tra le foto di un giorno, se ha un nome. */
fun dayPlace(items: List<MediaItem>, cells: Map<String, String>, names: Map<String, String>): String? =
    items.mapNotNull { cells[it.uri]?.let(names::get) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

/** La ricerca, tutta sul telefono: ogni parola deve trovare riscontro in data, tipo, cartella, nome o luogo. */
fun matchesSearch(
    item: MediaItem,
    query: String,
    place: String?,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    val date = Instant.ofEpochMilli(item.takenAt).atZone(zone).toLocalDate()
    return searchWords(query).all { word -> wordMatches(word, item, date, place, today) }
}

fun searchWords(query: String): List<String> = query.lowercase().split(Regex("\\s+")).filter { it.length > 1 && it !in STOP_WORDS }

private fun wordMatches(word: String, item: MediaItem, date: LocalDate, place: String?, today: LocalDate): Boolean {
    MONTHS[word]?.let { return date.monthValue == it }
    if (word.length == 4 && word.all(Char::isDigit)) return date.year == word.toInt()
    when (word) {
        "video", "filmato", "filmati" -> return item.isVideo
        "foto", "immagine", "immagini" -> return !item.isVideo
        "preferita", "preferite", "preferito", "preferiti" -> return item.favorite
        "oggi" -> return date == today
        "ieri" -> return date == today.minusDays(1)
    }
    return item.bucket.contains(word, true) || item.name.contains(word, true) || place?.contains(word, true) == true
}

private val STOP_WORDS = setOf("di", "del", "dei", "in", "a", "al", "da", "le", "la", "il")

private val MONTHS = listOf(
    "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
).withIndex().associate { (i, m) -> m to i + 1 }

package eu.eurise.desktop.app

import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * I calcoli della sezione Mappe sul computer (tavola PcMappe), senza interfaccia: gli impegni con un luogo presi dal
 * riassunto del telefono e il link `geo:` che apre il luogo in CoMaps (sul computer) o in Mappe (sul telefono, con il
 * Ponte). Il luogo è il testo dell'impegno così com'è: lo cerca la mappa, offline.
 */
object MapsData {
    private val IT = Locale.ITALIAN
    private val HM = DateTimeFormatter.ofPattern("H:mm")

    /** `geo:0,0?q=Via%20Lecco%2012%2C%20Monza`: lo standard che CoMaps e Mappe leggono per cercare un luogo. */
    fun geo(place: String): String? {
        val p = place.trim().replace(Regex("\\s+"), " ").take(200)
        if (p.isEmpty()) return null
        return "geo:0,0?q=" + URLEncoder.encode(p, Charsets.UTF_8).replace("+", "%20")
    }

    /** Vero solo per i link che costruiamo noi: niente altro passa a xdg-open. */
    fun isGeo(uri: String): Boolean = uri.startsWith("geo:0,0?q=") && uri.length <= 700 && uri.none { it.isWhitespace() }

    /** Gli impegni con un luogo non ancora finiti, il più vicino per primo. */
    fun withPlace(s: TodayData.Summary?, now: LocalDateTime): List<TodayData.Event> =
        s?.events.orEmpty()
            .filter { it.place.isNotBlank() && !ended(it, now) }
            .sortedWith(compareBy({ it.day }, { it.start ?: LocalTime.MIN }))

    private fun ended(e: TodayData.Event, now: LocalDateTime): Boolean {
        if (e.day.isBefore(now.toLocalDate())) return true
        if (e.day.isAfter(now.toLocalDate())) return false
        val end = e.end ?: e.start?.plusHours(1) ?: return false
        return end.isBefore(now.toLocalTime())
    }

    /** «Oggi 13:00», «Domani 9:00», «Lun 16», «Oggi, tutto il giorno». */
    fun whenText(e: TodayData.Event, today: LocalDate): String {
        val day = when (e.day) {
            today -> "Oggi"
            today.plusDays(1) -> "Domani"
            else -> e.day.format(DateTimeFormatter.ofPattern("EEE d", IT)).replaceFirstChar { it.titlecase(IT) }
        }
        return e.start?.let { "$day ${it.format(HM)}" } ?: "$day, tutto il giorno"
    }

    /** Quando partire, detto senza stime inventate: il tempo di strada lo calcola la mappa. */
    fun startsIn(e: TodayData.Event, now: LocalDateTime): String? {
        val start = e.start ?: return null
        val m = java.time.Duration.between(now, e.day.atTime(start)).toMinutes()
        return when {
            m < 0 -> "in corso"
            m < 60 -> "tra $m min"
            m < 24 * 60 -> "tra ${m / 60} h ${m % 60} min".replace(" 0 min", "")
            else -> null
        }
    }
}

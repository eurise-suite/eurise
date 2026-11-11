package eu.eurise.core.data.weather

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Una frase breve, con il tono che deve avere: tranquilla, da tenere d'occhio o di allerta. */
data class MeteoLine(val text: String, val tone: Tone) {
    enum class Tone { Calm, Watch, Alert }
}

/** Un breve aggiornamento scritto dai dati: titolo e due righe. */
data class MeteoStory(val title: String, val body: String)

/** Le parole del meteo, scritte sul telefono dai dati: logica pura, provata dai test. */
object MeteoText {
    private val IT = Locale.ITALIAN

    fun sky(s: Sky): String = when (s) {
        Sky.Clear -> "Sereno"
        Sky.PartlyCloudy -> "Poco nuvoloso"
        Sky.Cloudy -> "Nuvoloso"
        Sky.Fog -> "Nebbia"
        Sky.Drizzle -> "Pioviggine"
        Sky.Rain -> "Pioggia"
        Sky.Showers -> "Rovesci"
        Sky.Snow -> "Neve"
        Sky.Storm -> "Temporali"
    }

    fun level(level: Int): String = when (level) { 4 -> "rossa"; 3 -> "arancione"; else -> "gialla" }

    /** Da dove viene il vento, in otto direzioni (N, NE, E…; O per ovest). */
    fun windFrom(deg: Int): String = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[Math.floorMod(((deg + 22.5) / 45).toInt(), 8)]

    fun pollen(level: Int): String = when (level) { 0 -> "Assenti"; 1 -> "Bassi"; 2 -> "Moderati"; else -> "Alti" }

    fun dayName(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Oggi"
        today.plusDays(1) -> "Domani"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, IT).replaceFirstChar { c -> c.titlecase(IT) }
    }

    private fun wet(s: Sky) = s == Sky.Rain || s == Sky.Showers || s == Sky.Storm || s == Sky.Snow

    private fun part(h: Int): String = when (h) { in 5..11 -> "mattina"; in 12..17 -> "pomeriggio"; in 18..22 -> "sera"; else -> "notte" }

    /**
     * L'aggiornamento della giornata: il fatto più importante delle prossime 36 ore (allerta, temporali, pioggia, neve,
     * vento forte, sbalzo di temperatura), poi come va dopo.
     */
    fun story(f: Forecast, alerts: List<Alert>, now: LocalDateTime): MeteoStory {
        val today = now.toLocalDate()
        alerts.firstOrNull()?.let { a ->
            val whenText = a.onset?.let { o -> "${dayName(o.toLocalDate(), today).lowercase(IT)} dalle ${o.hour}" } ?: "in corso"
            val title = "Allerta ${level(a.level)}: ${a.event.ifBlank { "maltempo" }.lowercase(IT)}"
            return MeteoStory(title, "Vale $whenText${a.expires?.let { e -> " alle ${e.hour}" } ?: ""}. ${after(f, today)}")
        }
        val next = f.hours.filter { it.time.isBefore(now.plusHours(36)) }
        val wetHours = next.filter { wet(it.sky) && it.rainChance >= 50 }
        if (wetHours.isNotEmpty()) {
            val first = wetHours.first()
            val last = wetHours.last()
            val kind = when {
                wetHours.any { it.sky == Sky.Storm } -> "temporali"
                wetHours.any { it.sky == Sky.Snow } -> "neve"
                wetHours.any { it.sky == Sky.Showers } -> "rovesci"
                else -> "pioggia"
            }
            val day = dayName(first.time.toLocalDate(), today).lowercase(IT)
            val title = "${day.replaceFirstChar { c -> c.titlecase(IT) }} ${part(first.time.hour)}: ${kind}"
            val span = if (last.time.toLocalDate() == first.time.toLocalDate() && last.time.hour > first.time.hour) "tra le ${first.time.hour} e le ${last.time.hour + 1}" else "dalle ${first.time.hour}"
            return MeteoStory(title, "${kind.replaceFirstChar { c -> c.titlecase(IT) }} $span, probabilità fino al ${wetHours.maxOf { it.rainChance }}%. ${after(f, first.time.toLocalDate())}")
        }
        val windy = f.days.take(2).firstOrNull { it.gustKmh >= 60 }
        if (windy != null) {
            return MeteoStory("${dayName(windy.date, today)} vento forte", "Raffiche fino a ${windy.gustKmh} km/h: attenzione a oggetti sui balconi e mezzi leggeri. ${after(f, windy.date)}")
        }
        val t0 = f.days.firstOrNull()
        val t1 = f.days.getOrNull(1)
        if (t0 != null && t1 != null && kotlin.math.abs(t1.max - t0.max) >= 5) {
            val up = t1.max > t0.max
            return MeteoStory(if (up) "Domani più caldo" else "Domani più fresco", "Massima di ${t1.max}° contro i ${t0.max}° di oggi. ${after(f, t1.date)}")
        }
        return MeteoStory("Tempo tranquillo", "${sky(f.now.sky)} con ${f.now.temp}°. ${after(f, today)}")
    }

    /** Come va dopo [day]: i due giorni seguenti in breve. */
    private fun after(f: Forecast, day: LocalDate): String {
        val rest = f.days.filter { it.date.isAfter(day) }.take(2)
        if (rest.isEmpty()) return ""
        val dry = rest.none { wet(it.sky) && it.rainChance >= 50 }
        val today = f.days.first().date
        val names = rest.joinToString(" e ") { dayName(it.date, today).lowercase(IT) }
        return if (dry) "Poi $names asciutti, massime tra ${rest.minOf { it.max }}° e ${rest.maxOf { it.max }}°."
        else "Poi $names ancora instabili."
    }

    /**
     * La frase per un impegno nella bolla «I tuoi impegni». [travelMinutes] è il tempo di viaggio se si conosce. Del
     * traffico non si sa nulla finché non c'è una fonte: la frase parla solo del tempo.
     */
    fun forEvent(start: LocalDateTime, travelMinutes: Int?, f: Forecast?, alerts: List<Alert>, now: LocalDateTime): MeteoLine? {
        val leave = travelMinutes?.let { start.minusMinutes(it.toLong()) }
        val around = (leave ?: start.minusMinutes(30))
        val alert = alerts.firstOrNull { a -> (a.onset == null || !a.onset.isAfter(start)) && (a.expires == null || a.expires.isAfter(around)) }
        if (alert != null) {
            val from = alert.onset?.takeIf { it.isAfter(now) }?.let { " dalle ${it.hour}" } ?: ""
            val earlier = travelMinutes?.let { t -> start.minusMinutes((t * 1.3 + 10).toLong()) }
            val advice = earlier?.let { " e anticipa la partenza alle ${it.hour}:${"%02d".format(it.minute)}" } ?: " e parti con anticipo"
            return MeteoLine("Allerta meteo ${level(alert.level)}$from: presta attenzione$advice.", MeteoLine.Tone.Alert)
        }
        if (f == null) return null
        val hour = f.hours.firstOrNull { !it.time.isBefore(around.withMinute(0)) && it.time.isBefore(start.plusHours(1)) && wet(it.sky) && it.rainChance >= 50 }
        val day = if (start.toLocalDate() == now.toLocalDate()) "Oggi" else dayName(start.toLocalDate(), now.toLocalDate())
        if (hour != null) {
            val what = when (hour.sky) { Sky.Storm -> "temporali"; Sky.Snow -> "neve"; else -> "pioggia" }
            return MeteoLine("Alle ${hour.time.hour} è prevista $what: porta l'ombrello e calcola qualche minuto in più.", MeteoLine.Tone.Watch)
        }
        return MeteoLine("$day il tempo regge: dovresti arrivare in tempo.", MeteoLine.Tone.Calm)
    }
}

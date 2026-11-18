package eu.eurise.feature.today

import eu.eurise.core.data.weather.Forecast
import eu.eurise.core.data.weather.MeteoText
import eu.eurise.core.data.weather.Sky
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.IsoFields

/** La riga del meteo sotto la data: [head] in grassetto («12° · sereno»), [tail] il resto della giornata. */
data class AgendaMeteo(val head: String, val tail: String, val sky: Sky)

/** Le parole dell'Agenda scritte dai dati: logica pura, provata dai test. */
object AgendaText {

    fun week(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** Il giorno dell'anno (1 = primo gennaio), come lo stampavano le vecchie agende in testa alla pagina. */
    fun dayOfYear(date: LocalDate): Int = date.dayOfYear

    /** Feriale, prefestivo o festivo: decide il colore del numero nella pagina d'agenda (tavola AgePagina). */
    enum class DayKind { Weekday, PreHoliday, Holiday }

    /**
     * Festivi le domeniche e le feste nazionali; prefestivi i sabati e le vigilie delle feste (24 dicembre, 31 ottobre…);
     * gli altri feriali.
     */
    fun dayKind(date: LocalDate, isHoliday: (LocalDate) -> Boolean = eu.eurise.core.data.modes.ItalianHolidays::isHoliday): DayKind = when {
        date.dayOfWeek == java.time.DayOfWeek.SUNDAY || isHoliday(date) -> DayKind.Holiday
        date.dayOfWeek == java.time.DayOfWeek.SATURDAY || isHoliday(date.plusDays(1)) -> DayKind.PreHoliday
        else -> DayKind.Weekday
    }

    /**
     * Il meteo del giorno [date]: oggi la temperatura di adesso e da quando piove; gli altri giorni minima e massima.
     * Null se le previsioni non arrivano a quel giorno.
     */
    fun meteo(f: Forecast?, date: LocalDate, now: LocalDateTime): AgendaMeteo? {
        f ?: return null
        val day = f.days.firstOrNull { it.date == date } ?: return null
        val today = date == now.toLocalDate()
        val sky = if (today) f.now.sky else day.sky
        val head = if (today) "${f.now.temp}° · ${MeteoText.sky(sky).lowercase()}" else "${MeteoText.sky(sky)} · ${day.min}°/${day.max}°"
        val wetHour = f.allHours.firstOrNull { h ->
            h.time.toLocalDate() == date && (!today || !h.time.isBefore(now.withMinute(0))) && wet(h.sky) && h.rainChance >= 50
        }
        val what = wetHour?.let {
            when (it.sky) { Sky.Snow -> "neve"; Sky.Storm -> "temporali"; else -> "pioggia" } + " dalle ${it.time.hour}"
        }
        val tail = buildList {
            if (today) add("min ${day.min}° max ${day.max}°")
            if (what != null) add(what) else if (day.rainChance >= 50) add("pioggia probabile")
        }.joinToString(" · ")
        return AgendaMeteo(head, tail, sky)
    }

    private fun wet(s: Sky) = s == Sky.Rain || s == Sky.Showers || s == Sky.Storm || s == Sky.Snow || s == Sky.Drizzle

    /** Quando partire per arrivare in tempo: [minutes] di strada più cinque di margine. */
    fun leaveAt(start: LocalDateTime, minutes: Int): LocalTime = start.minusMinutes(minutes + 5L).toLocalTime()

    /**
     * Il titolo di una cosa da fare senza le persone citate («Chiamare l'idraulico @Luca» → «Chiamare l'idraulico» e
     * «Luca»): le persone si mostrano a parte, come pillole.
     */
    fun splitPeople(title: String): Pair<String, List<String>> {
        val names = MENTION.findAll(title).map { it.groupValues[1] }.toList()
        if (names.isEmpty()) return title to emptyList()
        val clean = MENTION.replace(title, "").replace(Regex("\\s{2,}"), " ").trim()
        return clean.ifEmpty { title } to names
    }

    private val MENTION = Regex("""(?<![\p{L}\p{N}@._-])@(\p{L}[\p{L}\p{N}'’-]*)""")

    /** Il saluto per l'ora: buongiorno fino alle 12, buon pomeriggio fino alle 17, poi buonasera. */
    fun greeting(time: LocalTime): Greeting = when (time.hour) {
        in 5..12 -> Greeting.Morning
        in 13..17 -> Greeting.Afternoon
        else -> Greeting.Evening
    }

    enum class Greeting { Morning, Afternoon, Evening }
}

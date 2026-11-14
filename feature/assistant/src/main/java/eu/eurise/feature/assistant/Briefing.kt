package eu.eurise.feature.assistant

import eu.eurise.core.data.alfred.AlfredCatalog
import eu.eurise.core.data.alfred.Pearl

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs

/** Un impegno come lo racconta Alfred. */
data class BriefEvent(val title: String, val start: LocalDateTime, val allDay: Boolean, val place: String)

/** Una quotazione: nome leggibile e variazione percentuale sulla chiusura precedente. */
data class BriefQuote(val name: String, val changePercent: Double)

/**
 * Tutto ciò che Alfred sa quando prepara il punto: lo raccoglie l'app (calendario, meteo, Borse e notizie dalle schede
 * della Scrivania), qui si scrive soltanto. [weather] è già una frase (titolo e testo del meteo della suite).
 */
data class BriefFacts(
    val now: LocalDateTime,
    val address: String,
    val today: List<BriefEvent>,
    val tomorrow: List<BriefEvent>,
    val weatherTitle: String?,
    val weatherBody: String?,
    val markets: List<BriefQuote>,
    val italy: List<String>,
    val world: List<String>,
)

enum class BriefKind { Events, Weather, Markets, News, Music, Art, Place, Curiosity }

/** Un argomento del punto: si legge per intero, si salta con un tocco. */
data class BriefSection(val kind: BriefKind, val title: String, val text: String, val music: Pearl? = null)

data class Briefing(val evening: Boolean, val label: String, val greeting: String, val motto: String, val sections: List<BriefSection>) {
    /** Il testo da leggere ad alta voce, un pezzo per argomento (il saluto e la massima vanno col primo). */
    fun spoken(): List<String> = sections.mapIndexed { i, s ->
        if (i == 0) "$greeting $motto ${s.text}" else "${s.title}. ${s.text}"
    }
}

/**
 * Il punto della giornata nella voce di Alfred (tavole AlfGiornata e AlfSera): cortese, asciutto, con una massima. Il
 * discorso cambia con l'ora: la mattina la giornata che viene, la sera quel che resta e l'indomani. Le perle, le opere,
 * i luoghi e le curiosità vengono dal catalogo di Alfred e cambiano ogni giorno.
 */
object BriefingWriter {
    private val IT = Locale.ITALIAN

    fun write(f: BriefFacts): Briefing {
        val hour = f.now.hour
        val evening = hour >= 18 || hour < 4
        val day = f.now.toLocalDate()
        val sections = buildList {
            add(BriefSection(BriefKind.Events, "Gli impegni", if (evening) eveningEvents(f) else events(f)))
            if (f.weatherTitle != null || f.weatherBody != null) {
                val title = if (evening) "Il tempo di domani" else "Il tempo"
                add(BriefSection(BriefKind.Weather, title, listOfNotNull(f.weatherTitle?.trimEnd('.'), f.weatherBody).joinToString(". ")))
            }
            if (f.markets.isNotEmpty()) add(BriefSection(BriefKind.Markets, "Le Borse", markets(f.markets)))
            news(f)?.let { add(BriefSection(BriefKind.News, "Le notizie", it)) }
            val pearl = AlfredCatalog.pearl(day, evening)
            add(BriefSection(BriefKind.Music, "Una perla dal passato", pearl.spoken(), pearl))
            if (!evening) {
                AlfredCatalog.art(day).let { add(BriefSection(BriefKind.Art, "Un'opera", it)) }
                AlfredCatalog.place(day).let { add(BriefSection(BriefKind.Place, "Un luogo", it)) }
            }
            AlfredCatalog.curiosity(day).let { add(BriefSection(BriefKind.Curiosity, "Una curiosità", it)) }
        }
        return Briefing(
            evening = evening,
            label = if (evening) "Alfred · il punto della sera" else "Alfred · il punto del mattino",
            greeting = greeting(f, evening),
            motto = AlfredCatalog.motto(day, evening),
            sections = sections,
        )
    }

    internal fun greeting(f: BriefFacts, evening: Boolean): String {
        val hello = when (f.now.hour) {
            in 4..12 -> "Buongiorno"
            in 13..17 -> "Buon pomeriggio"
            else -> "Buonasera"
        }
        val timed = f.today.filter { !it.allDay }
        val left = timed.count { it.start.isAfter(f.now) }
        val mood = when {
            evening && left == 0 -> "La giornata si è chiusa; resta soltanto la sera."
            evening -> "Resta ancora qualcosa da fare, ma con calma."
            timed.isEmpty() -> "Una giornata libera: un lusso che non le farò notare due volte."
            timed.size <= 2 -> "Una giornata ben disposta."
            else -> "Una giornata piena, ma ben disposta."
        }
        return "$hello${f.address}. $mood"
    }

    internal fun events(f: BriefFacts): String {
        val timed = f.today.filter { !it.allDay && it.start.isAfter(f.now.minusMinutes(30)) }
        val allDay = f.today.filter { it.allDay }
        if (timed.isEmpty() && allDay.isEmpty()) return "Oggi l'agenda è sgombra. Se mi permette, è un'occasione rara: la usi bene."
        val parts = timed.map { e -> "alle ${time(e.start)} ${e.title}${place(e)}" }
        val text = StringBuilder()
        if (parts.isNotEmpty()) text.append(parts.joinToString(", ").replaceFirstChar { it.titlecase(IT) }).append('.')
        if (allDay.isNotEmpty()) {
            if (text.isNotEmpty()) text.append(' ')
            text.append("Per tutto il giorno: ").append(allDay.joinToString(", ") { it.title }).append('.')
        }
        lunchGap(timed)?.let { text.append(' ').append(it) }
        return text.toString()
    }

    /** Una nota se gli impegni lasciano scoperto il pranzo: Alfred se ne accorge, senza insistere. */
    private fun lunchGap(timed: List<BriefEvent>): String? {
        if (timed.size < 2) return null
        val before = timed.any { it.start.hour < 12 }
        val after = timed.any { it.start.hour >= 15 }
        val atLunch = timed.any { it.start.hour in 12..14 }
        return if (before && after && !atLunch) "Tra l'uno e l'altro, se posso permettermi, un pranzo vero." else null
    }

    internal fun eveningEvents(f: BriefFacts): String {
        val left = f.today.filter { !it.allDay && it.start.isAfter(f.now) }
        val first = f.tomorrow.filter { !it.allDay }.minByOrNull { it.start }
        val text = StringBuilder()
        if (left.isNotEmpty()) text.append("Stasera: ").append(left.joinToString(", ") { "alle ${time(it.start)} ${it.title}${place(it)}" }).append(". ")
        when {
            first != null -> {
                text.append("Domani si comincia alle ${time(first.start)} con ${first.title}${place(first)}")
                val more = f.tomorrow.count { !it.allDay } - 1
                text.append(when (more) { 0 -> ", e poi nient'altro."; 1 -> ", poi un altro impegno."; else -> ", poi altri $more impegni." })
                if (first.start.hour < 9) text.append(" Le suggerirei di non fare tardi.")
            }
            else -> text.append("Domani, per ora, nessun impegno in agenda.")
        }
        return text.toString().trim()
    }

    internal fun markets(list: List<BriefQuote>): String =
        list.take(4).joinToString(", ") { q -> "${q.name} ${percent(q.changePercent)}" } + ". Dati con circa un quarto d'ora di ritardo."

    private fun news(f: BriefFacts): String? {
        val it = f.italy.take(3)
        val w = f.world.take(3)
        if (it.isEmpty() && w.isEmpty()) return null
        val text = StringBuilder()
        if (it.isNotEmpty()) text.append("Dall'Italia: ").append(it.joinToString(". ") { s -> s.trimEnd('.', ' ') }).append(". ")
        if (w.isNotEmpty()) text.append("Dal mondo: ").append(w.joinToString(". ") { s -> s.trimEnd('.', ' ') }).append('.')
        return text.toString().trim()
    }

    private fun place(e: BriefEvent) = e.place.takeIf { it.isNotBlank() }?.let { ", a ${it.substringBefore(',').trim()}" } ?: ""

    /** «10», «20:30»: le ore piene senza minuti, come si dice. */
    internal fun time(t: LocalDateTime): String = if (t.minute == 0) "${t.hour}" else "%d:%02d".format(t.hour, t.minute)

    /** «più 0,8 per cento»: scritto per l'occhio e per la voce insieme. */
    internal fun percent(p: Double): String {
        val v = String.format(IT, "%.1f", abs(p))
        return when {
            abs(p) < 0.05 -> "invariato"
            p > 0 -> "+$v%"
            else -> "−$v%"
        }
    }
}

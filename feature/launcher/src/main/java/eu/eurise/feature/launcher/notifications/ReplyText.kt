package eu.eurise.feature.launcher.notifications

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Testi delle risposte rapide (tavole BolRispondi e BolAllega), senza Android: si provano con i test.
 * Dalle notifiche si può mandare solo testo, quindi citazione, contatto, posizione e disponibilità diventano testo.
 */
object ReplyText {

    /** Le reazioni rapide, nell'ordine della tavola. */
    val REACTIONS = listOf("👍", "❤️", "😂", "😮", "🙏")

    private val QUOTE_APPS = setOf("com.whatsapp", "com.whatsapp.w4b")

    /**
     * La risposta con la frase citata in testa. WhatsApp mostra «> frase» come citazione vera e propria;
     * per le altre app la frase va tra virgolette basse su una riga a sé.
     */
    fun quoted(packageName: String, quote: String, answer: String): String {
        val q = quote.trim().replace(Regex("\\s+"), " ").let { if (it.length > 160) it.take(157).trimEnd() + "…" else it }
        if (q.isEmpty()) return answer.trim()
        val head = if (packageName in QUOTE_APPS) "> $q" else "«$q»"
        return "$head\n${answer.trim()}"
    }

    /** Un contatto da mandare in chat: nome e numeri (e l'email, se c'è). */
    fun contact(name: String, phones: List<String>, emails: List<String> = emptyList()): String =
        (listOf(name.trim()) + phones.map { it.trim() } + emails.map { it.trim() }).filter { it.isNotEmpty() }.distinct().joinToString("\n")

    /** La posizione come collegamento a OpenStreetMap (si apre ovunque, anche senza app di mappe). */
    fun position(lat: Double, lon: Double): String {
        val la = "%.5f".format(Locale.ROOT, lat)
        val lo = "%.5f".format(Locale.ROOT, lon)
        return "Sono qui: https://www.openstreetmap.org/?mlat=$la&mlon=$lo#map=17/$la/$lo"
    }

    /**
     * Le disponibilità dei prossimi giorni in una frase: «Sono libero oggi alle 15 e alle 16:30, domani alle 9 e alle 11».
     * [free] contiene, per ogni giorno, gli inizi liberi già calcolati; si prendono al massimo [perDay] orari per giorno,
     * distanti almeno un'ora tra loro.
     */
    fun availability(free: Map<LocalDate, List<LocalTime>>, today: LocalDate, perDay: Int = 3): String? {
        val parts = free.toSortedMap().mapNotNull { (day, times) ->
            val picked = mutableListOf<LocalTime>()
            for (t in times.sorted()) {
                if (picked.size == perDay) break
                if (picked.isEmpty() || !t.isBefore(picked.last().plusHours(1))) picked += t
            }
            if (picked.isEmpty()) null else dayName(day, today) + " " + picked.joinToString(" e ") { "alle " + hour(it) }
        }
        if (parts.isEmpty()) return null
        return "Sono libero " + parts.joinToString(", ")
    }

    private fun hour(t: LocalTime) = if (t.minute == 0) "${t.hour}" else "${t.hour}:%02d".format(t.minute)

    private val WEEKDAYS = listOf("lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica")

    private fun dayName(day: LocalDate, today: LocalDate) = when (day) {
        today -> "oggi"
        today.plusDays(1) -> "domani"
        else -> WEEKDAYS[day.dayOfWeek.value - 1] + " " + day.dayOfMonth
    }

    /** I prossimi [count] giorni lavorativi a partire da oggi (oggi compreso se non è finito il giorno di lavoro). */
    fun nextWorkDays(now: LocalDateTime, count: Int = 3, dayEnd: LocalTime = LocalTime.of(19, 0)): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = if (now.toLocalTime().isBefore(dayEnd)) now.toLocalDate() else now.toLocalDate().plusDays(1)
        while (out.size < count) {
            if (d.dayOfWeek.value <= 5) out += d
            d = d.plusDays(1)
        }
        return out
    }

    private val COMMON = setOf(
        "ciao", "grazie", "domani", "oggi", "dopo", "anche", "quando", "dove", "come", "sono", "allora", "perché", "molto",
        "bene", "tutto", "tutti", "questo", "quello", "numero", "mandi", "manda", "puoi", "fammi", "sapere", "ancora", "sempre",
        "casa", "lavoro", "settimana", "mattina", "sera", "pomeriggio", "ufficio",
    )

    /**
     * I contatti nominati nella conversazione («mi mandi il numero di Mario?», «quello dell'idraulico»): le parole dei
     * messaggi confrontate con nomi, cognomi e mestieri scritti in rubrica. Chi scrive e le parole comuni non contano.
     */
    fun mentionedContacts(messages: List<String>, contacts: List<String>, sender: String, limit: Int = 3): List<String> {
        val skip = sender.lowercase().split(' ').filter { it.isNotBlank() }.toSet()
        val words = messages.flatMap { m -> Regex("[\\p{L}]{4,}").findAll(m.lowercase()).map { it.value }.toList() }
            .filter { it !in skip && it !in COMMON }.toSet()
        if (words.isEmpty()) return emptyList()
        return contacts.filter { c ->
            val parts = c.lowercase().split(' ', '-', '\'').filter { it.length >= 4 }
            parts.any { it in words } && parts.none { it in skip }
        }.take(limit)
    }
}

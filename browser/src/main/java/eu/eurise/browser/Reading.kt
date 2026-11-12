package eu.eurise.browser

import eu.eurise.core.data.messages.MessageInsights
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

/** Un articolo da leggere dopo: il testo resta sul telefono, si legge anche senza rete. */
data class LaterItem(val url: String, val title: String, val text: String, val time: Long)

/** Una data trovata nella pagina, da mettere nel Calendario (tavola BrwStrumenti: «16 dicembre · TARI prima rata»). */
data class PageDate(val date: LocalDate, val time: LocalTime?, val text: String)

/** La vista Lettura e i suoi strumenti (tavola BrwStrumenti): tempi, date, «Leggi dopo», note del sito. */
object Reading {
    /** Parole al minuto di una lettura tranquilla. */
    private const val WORDS_PER_MINUTE = 220

    fun minutes(text: String): Int {
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        return if (words == 0) 0 else maxOf(1, (words + WORDS_PER_MINUTE / 2) / WORDS_PER_MINUTE)
    }

    /** I blocchi della vista Lettura: paragrafi separati da una riga vuota; «# » davanti ai titoli. */
    fun blocks(text: String): List<String> = text.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }

    /**
     * La prima data da oggi in poi che compare nel testo (le scadenze, gli appuntamenti), cercata paragrafo per
     * paragrafo con lo stesso lettore dei messaggi. Sul telefono, nessun servizio.
     */
    fun firstDate(text: String, now: LocalDateTime, language: String = "it"): PageDate? {
        val today = now.toLocalDate()
        for (block in blocks(text).take(60)) {
            val plain = block.removePrefix("# ")
            val event = runCatching { MessageInsights.analyze(plain, now, language) }.getOrDefault(emptyList())
                .filterIsInstance<MessageInsights.Insight.Event>().firstOrNull() ?: continue
            if (!event.date.isBefore(today)) return PageDate(event.date, event.time, plain)
        }
        return null
    }

    /** Le posizioni di [query] nel testo (senza badare a maiuscole), per «Trova» nella Lettura. */
    fun matches(text: String, query: String): List<Int> {
        if (query.isBlank()) return emptyList()
        val out = mutableListOf<Int>()
        var i = text.indexOf(query, ignoreCase = true)
        while (i >= 0 && out.size < 999) { out += i; i = text.indexOf(query, i + query.length, ignoreCase = true) }
        return out
    }
}

/** «Leggi dopo»: articoli salvati con il loro testo, i più recenti prima. */
class LaterStore(private val file: File) {
    fun load(): List<LaterItem> = runCatching { decode(file.readText()) }.getOrDefault(emptyList())

    fun save(items: List<LaterItem>) {
        runCatching { file.writeText(encode(items)) }
    }

    companion object {
        const val MAX = 100
        /** Il testo si tiene fino a qui (un articolo lungo sta dentro). */
        const val MAX_TEXT = 60_000

        fun add(items: List<LaterItem>, item: LaterItem): List<LaterItem> =
            (listOf(item.copy(text = item.text.take(MAX_TEXT))) + items.filter { it.url != item.url }).take(MAX)

        fun encode(items: List<LaterItem>): String = JSONArray().apply {
            items.forEach { put(JSONObject().put("url", it.url).put("title", it.title).put("text", it.text).put("time", it.time)) }
        }.toString()

        fun decode(text: String): List<LaterItem> {
            val a = JSONArray(text)
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                .map { LaterItem(it.optString("url"), it.optString("title"), it.optString("text"), it.optLong("time")) }
                .filter { it.url.isNotBlank() }
        }
    }
}

/** «Nota del sito»: un appunto legato a un sito (per esempio il numero di pratica), che ritrovi quando ci torni. */
class SiteNotes(private val file: File) {
    private val notes: MutableMap<String, String> = runCatching {
        val o = JSONObject(file.readText())
        o.keys().asSequence().associateWith { o.optString(it) }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    fun get(host: String): String = notes[host].orEmpty()

    fun set(host: String, text: String) {
        if (host.isBlank()) return
        if (text.isBlank()) notes.remove(host) else notes[host] = text.trim()
        runCatching { file.writeText(JSONObject(notes as Map<*, *>).toString()) }
    }
}

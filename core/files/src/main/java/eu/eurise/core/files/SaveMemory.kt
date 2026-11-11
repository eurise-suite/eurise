package eu.eurise.core.files

import java.text.Normalizer
import java.util.Locale

/**
 * Dove si salvano le cose, per proporre la cartella giusta con il perché (tavola FileSalvaIn): «c'è già il contratto del
 * 2022», «l'ultima usata». Tutto sul telefono: si ricordano cartella, nome del file e ora, niente contenuti.
 *
 * La proposta somma le parole del nome nuovo che compaiono nei nomi già salvati in una cartella (o nel nome della
 * cartella stessa) e un poco di recentezza; [kind] separa i ricordi delle app (una scansione e un foglio di calcolo non
 * vanno di solito nello stesso posto).
 */
class SaveMemory(records: List<Record> = emptyList(), private val limit: Int = 300) {

    data class Record(val kind: String, val spot: Spot, val name: String, val at: Long)

    /** Una proposta: dove, il nome già salvato lì che la giustifica (se c'è) e se è solo l'ultima usata. */
    data class Suggestion(val spot: Spot, val because: String?, val lastUsed: Boolean)

    private val list = records.sortedBy { it.at }.takeLast(limit).toMutableList()
    val records: List<Record> get() = list.toList()

    fun remember(kind: String, spot: Spot, name: String, at: Long) {
        list += Record(kind, spot, name, at)
        while (list.size > limit) list.removeAt(0)
    }

    fun suggest(kind: String, name: String, now: Long, count: Int = 2): List<Suggestion> {
        val words = words(name)
        val mine = list.filter { it.kind == kind }
        val bySpot = mine.groupBy { it.spot }
        val scored = bySpot.map { (spot, recs) ->
            var best = 0
            var because: String? = null
            for (r in recs) {
                val shared = words.intersect(words(r.name)).size
                if (shared > best) { best = shared; because = r.name }
            }
            val folderWords = spot.path.split('/').flatMap(::words).toSet()
            val folderHit = words.intersect(folderWords).size
            val days = (now - recs.maxOf { it.at }).coerceAtLeast(0) / DAY
            val recency = 1.0 / (1 + days / 7.0)
            val score = best * 3.0 + folderHit * 2.0 + recency + recs.size * 0.05
            Triple(spot, score, because.takeIf { best > 0 })
        }.sortedByDescending { it.second }
        val last = mine.maxByOrNull { it.at }?.spot
        val out = mutableListOf<Suggestion>()
        scored.firstOrNull { it.third != null || it.second > 1.5 }?.let { out += Suggestion(it.first, it.third, lastUsed = false) }
        if (last != null && out.none { it.spot == last }) out += Suggestion(last, null, lastUsed = true)
        scored.filter { s -> out.none { it.spot == s.first } }.forEach { if (out.size < count) out += Suggestion(it.first, it.third, false) }
        return out.take(count)
    }

    /** Le cartelle usate più di recente per [kind], senza doppioni. */
    fun recentSpots(kind: String?, count: Int = 6): List<Spot> =
        list.asReversed().filter { kind == null || it.kind == kind }.map { it.spot }.distinct().take(count)

    /** I file salvati o aperti di recente (per «Apri da…»), dal più recente. */
    fun recentFiles(count: Int = 12): List<Record> = list.asReversed().distinctBy { it.spot.key() + "/" + it.name }.take(count)

    companion object {
        private const val DAY = 86_400_000L
        private val stop = setOf("di", "del", "della", "dei", "il", "la", "lo", "le", "e", "a", "da", "in", "per", "con", "pdf", "doc", "docx", "the", "of")

        /** Parole significative: minuscole, senza accenti, niente numeri brevi né parole vuote. Le date restano fuori. */
        fun words(text: String): Set<String> {
            val plain = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            return plain.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in stop && !it.all(Char::isDigit) }.toSet()
        }

        fun encode(r: Record): String = listOf(r.kind, r.spot.key(), r.name, r.at.toString()).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

        fun decode(line: String): Record? {
            val p = line.split('\t')
            if (p.size != 4) return null
            val spot = Spot.fromKey(p[1]) ?: return null
            return Record(p[0], spot, p[2], p[3].toLongOrNull() ?: return null)
        }
    }
}

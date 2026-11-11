package eu.eurise.browser

import java.io.File

/** Una pagina visitata. [time] in millisecondi. */
data class Visit(val url: String, val title: String, val time: Long)

/**
 * La cronologia, sul telefono in un file di testo (una riga per visita). Le schede private non la toccano.
 * Tiene al massimo [MAX] visite e niente più vecchio di 90 giorni. Senza Android: si prova con i test.
 */
class HistoryStore(private val file: File) {
    private val lock = Any()
    private var cache: MutableList<Visit>? = null

    private fun all(): MutableList<Visit> = cache ?: read().toMutableList().also { cache = it }

    private fun read(): List<Visit> = runCatching { parse(file.readText()) }.getOrDefault(emptyList())

    /** Annota una visita; la stessa pagina riaperta entro 30 minuti aggiorna la riga invece di aggiungerne una. */
    fun add(url: String, title: String, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (!url.startsWith("http")) return@synchronized
        val list = all()
        val last = list.lastOrNull()
        if (last != null && last.url == url && now - last.time < 30 * 60_000L) {
            list[list.lastIndex] = last.copy(title = title.ifBlank { last.title }, time = now)
        } else list += Visit(url, title, now)
        trim(list, now)
        write(list)
    }

    /** Aggiorna il titolo dell'ultima visita a [url] (il titolo arriva dopo l'indirizzo). */
    fun title(url: String, title: String) = synchronized(lock) {
        val list = all()
        val i = list.indexOfLast { it.url == url }
        if (i < 0 || title.isBlank() || list[i].title == title) return@synchronized
        list[i] = list[i].copy(title = title)
        write(list)
    }

    /** Dal più recente; con [query], solo le visite che la contengono nel titolo o nell'indirizzo. */
    fun list(query: String = "", limit: Int = 300): List<Visit> = synchronized(lock) {
        val q = query.trim().lowercase()
        all().asReversed().asSequence()
            .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            .take(limit).toList()
    }

    /** Cancella le visite da [since] in poi (0 = tutte). */
    fun clear(since: Long = 0L) = synchronized(lock) {
        val list = all()
        list.removeAll { it.time >= since }
        write(list)
    }

    fun remove(visit: Visit) = synchronized(lock) {
        val list = all()
        list.remove(visit)
        write(list)
    }

    private fun write(list: List<Visit>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(format(list))
        tmp.renameTo(file)
    }

    companion object {
        const val MAX = 5000
        private const val DAYS_90 = 90L * 86_400_000L

        internal fun trim(list: MutableList<Visit>, now: Long) {
            list.removeAll { now - it.time > DAYS_90 }
            while (list.size > MAX) list.removeAt(0)
        }

        internal fun format(list: List<Visit>) = list.joinToString("\n") {
            "${it.time}\t${it.url.replace('\t', ' ')}\t${it.title.replace('\t', ' ').replace('\n', ' ')}"
        }

        internal fun parse(text: String): List<Visit> = text.lineSequence().mapNotNull { line ->
            val p = line.split('\t', limit = 3)
            val time = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val url = p.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Visit(url, p.getOrNull(2).orEmpty(), time)
        }.toList()
    }
}

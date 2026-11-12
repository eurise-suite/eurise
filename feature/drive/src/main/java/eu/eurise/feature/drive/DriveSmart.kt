package eu.eurise.feature.drive

import eu.eurise.core.data.docs.DocReader
import eu.eurise.core.data.trips.PdfText
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un nome nuovo proposto per un file (una scansione con un nome da scanner). */
internal data class Rename(val item: DriveItem, val name: String)

/**
 * Le funzioni intelligenti di Drive che si appoggiano al contenuto dei file, tutte sul telefono:
 * doppioni, nomi proposti alle scansioni, testo dei PDF per la ricerca. Niente esce.
 */
internal object DriveSmart {
    /** Fino a qui si legge un PDF per la ricerca e per il nome (oltre si salta: memoria e batteria). */
    private const val MAX_PDF_BYTES = 12L * 1024 * 1024

    /** «(1)», «- Copia», «_2», «copy»: i segni delle copie che i telefoni e i browser aggiungono al nome. */
    private val COPY_MARK = Regex("""(?i)(\s*\(\d+\)|\s*[-_]\s*(copia|copy|kopie|copie)(\s*\d+)?|\s+(copia|copy)(\s*\d+)?|-\d{1,2})$""")

    /** I nomi che mettono gli scanner e le fotocamere: dicono quando, non cosa. */
    private val GENERIC = Regex(
        """(?i)^(scan(ned)?|scansione|scansionato|img|image|immagine|doc|document|documento|file|camscanner|cs|adobe scan|""" +
            """genius ?scan|download|unnamed|untitled|senza titolo|nuovo documento|new document|pdf)?[\s_\-.]*[\d\s_\-.:]*$""",
    )

    /** Il nome «di base» per riconoscere le copie: senza estensione, senza segni di copia, senza accenti né maiuscole. */
    fun baseName(name: String): String {
        val stem = name.substringBeforeLast('.', name)
        var s = stem.trim()
        repeat(2) { s = s.replace(COPY_MARK, "").trim() }
        return Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "") + "." + name.substringAfterLast('.', "").lowercase()
    }

    /**
     * Le copie in più: file con lo stesso peso e lo stesso nome di base (a meno di «(1)», «- Copia»…). Di ogni gruppo
     * resta il più recente; le altre si propongono per il cestino (da dove si recuperano per 30 giorni).
     */
    fun duplicateCopies(files: List<DriveItem>): List<DriveItem> =
        files.filter { !it.folder && (it.size ?: 0L) > 0L }
            .groupBy { (it.size ?: 0L) to baseName(it.name) }
            .values.filter { it.size > 1 }
            .flatMap { group -> group.sortedByDescending { it.modified ?: 0L }.drop(1) }

    /** Un nome da scanner o da fotocamera (Scan_0012.pdf, IMG_20261112.pdf, documento (3).pdf): vale la pena proporne uno. */
    fun isGenericName(name: String): Boolean {
        val stem = name.substringBeforeLast('.', name).replace(COPY_MARK, "").trim()
        return stem.isEmpty() || GENERIC.matches(stem)
    }

    /** Il testo di un PDF del telefono (vuoto se è fatto di sole immagini, troppo grande o non si legge). */
    suspend fun pdfText(data: DriveData, item: DriveItem): String = withContext(Dispatchers.IO) {
        if (item.extension != "pdf" || item.place.account != null || item.place.vaultRoot != null || item.place.tree != null) return@withContext ""
        if ((item.size ?: 0L) > MAX_PDF_BYTES) return@withContext ""
        val stamp = "${item.key}|${item.modified}|${item.size}"
        synchronized(cache) { cache[stamp] }?.let { return@withContext it }
        val text = runCatching { PdfText.extract(data.localCopy(item).readBytes()) }.getOrDefault("")
        synchronized(cache) {
            if (cache.size > 200) cache.clear()
            cache[stamp] = text
        }
        text
    }

    private val cache = HashMap<String, String>()

    /** I nomi proposti alle scansioni con un nome da scanner: tipo, ente e mese letti nel testo (es. «2026-11 Bolletta luce Enel»). */
    suspend fun proposedNames(data: DriveData, files: List<DriveItem>, limit: Int = 8): List<Rename> {
        val out = mutableListOf<Rename>()
        val taken = files.map { it.name.lowercase() }.toMutableSet()
        for (f in files.filter { !it.folder && it.extension == "pdf" && isGenericName(it.name) }.take(limit * 2)) {
            val text = pdfText(data, f)
            if (text.isBlank()) continue
            val proposed = runCatching { DocReader.analyze(text).fileName }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: continue
            val clean = proposed.removeSuffix(".pdf").replace(Regex("""[\\/:*?"<>|]"""), " ").trim()
            var name = "$clean.pdf"
            var n = 2
            while (name.lowercase() in taken) name = "$clean ($n).pdf".also { n++ }
            taken += name.lowercase()
            out += Rename(f, name)
            if (out.size >= limit) break
        }
        return out
    }

    /** Un pezzo di testo intorno alla parola trovata, per mostrare perché il file è nei risultati. */
    fun snippet(text: String, query: String, width: Int = 90): String? {
        val q = query.trim()
        if (q.length < 3) return null
        val flat = text.replace(Regex("\\s+"), " ")
        val i = flat.indexOf(q, ignoreCase = true)
        if (i < 0) return null
        val start = (i - width / 3).coerceAtLeast(0)
        val end = (i + q.length + width * 2 / 3).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end).trim() + (if (end < flat.length) "…" else "")
    }
}

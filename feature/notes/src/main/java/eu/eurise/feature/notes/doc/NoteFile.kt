package eu.eurise.feature.notes.doc

import java.time.Instant

/**
 * Una nota come file del cloud: un documento HTML che si apre in qualsiasi browser, con titolo, contenuto e,
 * nei `<meta name="eurise:…">`, i dati che servono all'app (taccuino, fissata, date, cestino).
 *
 * I legami con le Attività non vanno nel file: valgono solo su questo telefono.
 */
data class NoteFile(
    val uid: String,
    val title: String,
    val blocks: List<Block>,
    val notebook: String? = null,
    val notebookTint: String? = null,
    val pinned: Boolean = false,
    val created: Long = 0,
    val updated: Long = 0,
    val trashed: Long? = null,
) {
    fun write(): String = NoteHtml.document(
        title,
        blocks.map { if (it is TextBlock && it.taskId != null) it.copy(taskId = null) else it },
        buildMap {
            put("id", uid)
            notebook?.let { put("notebook", it) }
            notebookTint?.let { put("notebook-tint", it) }
            if (pinned) put("pinned", "true")
            put("created", Instant.ofEpochMilli(created).toString())
            put("updated", Instant.ofEpochMilli(updated).toString())
            trashed?.let { put("trashed", Instant.ofEpochMilli(it).toString()) }
        },
    )

    companion object {
        /** Legge un file di nota; [fallbackUid] vale se il file non dice il suo (per esempio scritto a mano). */
        fun read(html: String, fallbackUid: String, ids: () -> Long): NoteFile {
            val meta = NoteHtml.meta(html)
            fun time(key: String) = meta[key]?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            return NoteFile(
                uid = meta["id"] ?: fallbackUid,
                title = NoteHtml.title(html),
                blocks = NoteHtml.read(html, ids),
                notebook = meta["notebook"]?.takeIf { it.isNotBlank() },
                notebookTint = meta["notebook-tint"],
                pinned = meta["pinned"] == "true",
                created = time("created") ?: 0,
                updated = time("updated") ?: 0,
                trashed = time("trashed"),
            )
        }
    }
}

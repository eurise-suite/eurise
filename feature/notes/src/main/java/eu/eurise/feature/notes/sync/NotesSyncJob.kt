package eu.eurise.feature.notes.sync

import android.content.Context
import eu.eurise.core.cloud.CloudSync
import eu.eurise.core.cloud.DavException
import eu.eurise.core.cloud.SyncJob
import eu.eurise.core.cloud.WebDav
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.NotebookRepository
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.Notebook
import eu.eurise.feature.notes.R
import eu.eurise.feature.notes.doc.Block
import eu.eurise.feature.notes.doc.NoteFile
import eu.eurise.feature.notes.doc.NoteHtml
import eu.eurise.feature.notes.doc.NoteMerge
import eu.eurise.feature.notes.doc.TextBlock
import eu.eurise.feature.notes.doc.TextKind
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Sincronizza le note con la cartella `Eurise/Note` del cloud scelto: un file HTML per nota, che si apre anche
 * nel browser. Per ogni nota confronta tre versioni (telefono, cloud, ultima sincronizzazione): ciò che è cambiato
 * da una sola parte passa all'altra, i cambi in punti diversi si uniscono, e solo quando la stessa parte è stata
 * cambiata in due modi la nota aspetta una scelta. Ogni scrittura sul cloud è condizionata all'etag letto,
 * quindi un cambio fatto nel frattempo altrove non viene mai sovrascritto.
 */
class NotesSyncJob(
    private val notes: NoteRepository,
    private val notebooks: NotebookRepository,
) : SyncJob {

    private var nextId = 0L
    private val ids = { ++nextId }

    override suspend fun run(context: Context, cloud: CloudSync): List<String> {
        val prefs = cloud.settings.value
        if (!prefs.notesEnabled) return emptyList()
        val dav = cloud.accounts.dav(prefs.notesAccount) ?: return emptyList()
        val out = mutableListOf<String>()
        fun say(res: Int, title: String) {
            out += context.getString(res, title.ifBlank { context.getString(R.string.notes_untitled) })
        }

        dav.mkdirs(FOLDER)
        val remote = dav.list(FOLDER)
            .filter { !it.folder && it.name.endsWith(EXT) }
            .associateBy { it.name.removeSuffix(EXT) }

        // Note eliminate per sempre sul telefono.
        for ((uid, etag) in notes.tombstones()) {
            if (remote.containsKey(uid)) {
                try {
                    dav.delete(path(uid), ifMatch = etag)
                } catch (e: DavException) {
                    if (e.code != 412) throw e // 412: cambiata altrove nel frattempo, resta sul cloud.
                }
            }
            notes.removeTombstone(uid)
        }

        val books = notebooks.observe().first().map { it.notebook }
        val seen = mutableSetOf<String>()
        for (state in notes.syncStates()) {
            val uid = state.uid ?: UUID.randomUUID().toString().also { notes.assignUid(state.note.id, it) }
            seen += uid
            if (state.conflict != null) continue
            val note = state.note
            val text = export(note, uid, books)
            val entry = remote[uid]
            try {
                when {
                    entry == null && state.remoteEtag == null -> {
                        val etag = dav.put(path(uid), text, HTML, onlyIfNew = true)
                        notes.markSynced(note.id, etag, text)
                        say(R.string.sync_sent, note.title)
                    }
                    entry == null -> {
                        // Era sul cloud e lì è stata eliminata.
                        if (text == state.base) {
                            notes.markSynced(note.id, null, null)
                            notes.delete(note.id)
                            say(R.string.sync_deleted, note.title)
                        } else {
                            val etag = dav.put(path(uid), text, HTML, onlyIfNew = true)
                            notes.markSynced(note.id, etag, text)
                            say(R.string.sync_sent, note.title)
                        }
                    }
                    entry.etag == state.remoteEtag -> if (text != state.base) {
                        val etag = dav.put(path(uid), text, HTML, ifMatch = entry.etag)
                        notes.markSynced(note.id, etag, text)
                        say(R.string.sync_sent, note.title)
                    }
                    else -> {
                        val (remoteText, remoteEtag) = dav.get(path(uid)) ?: continue
                        val etag = remoteEtag ?: entry.etag
                        if (text == state.base || state.base == null && text == remoteText) {
                            val updated = apply(note.id, uid, remoteText, note, books)
                            notes.markSynced(note.id, etag, updated)
                            say(R.string.sync_received, NoteHtml.title(remoteText))
                        } else {
                            mergeOrHold(dav, note, uid, text, state.base, remoteText, etag, books, ::say)
                        }
                    }
                }
            } catch (e: DavException) {
                // 412: il file è cambiato tra la lettura e la scrittura; ci si riprova al prossimo giro.
                if (e.code != 412) throw e
            }
        }

        // Note nuove arrivate dal cloud.
        for ((uid, entry) in remote) {
            if (uid in seen) continue
            val (remoteText, remoteEtag) = dav.get(path(uid)) ?: continue
            val id = createFrom(uid, remoteText, books)
            val created = notes.syncStates().firstOrNull { it.note.id == id }?.note ?: continue
            notes.markSynced(id, remoteEtag ?: entry.etag, export(created, uid, notebooks.observe().first().map { it.notebook }))
            say(R.string.sync_received, created.title)
        }
        return out
    }

    private suspend fun mergeOrHold(
        dav: WebDav, note: Note, uid: String, local: String, base: String?, remote: String, remoteEtag: String?,
        books: List<Notebook>, say: (Int, String) -> Unit,
    ) {
        val baseFile = base?.let { NoteFile.read(it, uid, ids) }
        val localFile = NoteFile.read(local, uid, ids)
        val remoteFile = NoteFile.read(remote, uid, ids)
        val merge = NoteMerge.merge(
            baseFile?.title.orEmpty(), baseFile?.blocks.orEmpty(),
            localFile.title, localFile.blocks,
            remoteFile.title, remoteFile.blocks,
        )
        if (!merge.clean || baseFile == null) {
            notes.setConflict(note.id, remote, remoteEtag)
            say(R.string.sync_conflict, note.title)
            return
        }
        // Dati della nota: vale quello cambiato sul telefono, altrimenti quello del cloud.
        fun <T> pick(b: T, l: T, r: T) = if (l != b) l else r
        val merged = localFile.copy(
            title = merge.title,
            blocks = withTaskLinks(merge.blocks(), note),
            notebook = pick(baseFile.notebook, localFile.notebook, remoteFile.notebook),
            notebookTint = pick(baseFile.notebookTint, localFile.notebookTint, remoteFile.notebookTint),
            pinned = pick(baseFile.pinned, localFile.pinned, remoteFile.pinned),
            trashed = pick(baseFile.trashed, localFile.trashed, remoteFile.trashed),
            updated = maxOf(localFile.updated, remoteFile.updated),
        )
        save(note.id, merged)
        val text = export(notes.syncStates().first { it.note.id == note.id }.note, uid, books)
        val etag = dav.put(path(uid), text, HTML, ifMatch = remoteEtag)
        notes.markSynced(note.id, etag, text)
        say(R.string.sync_merged, merged.title)
    }

    /** Porta nella nota [id] la versione del cloud e restituisce il file della nota com'è ora sul telefono. */
    private suspend fun apply(id: Long, uid: String, remote: String, local: Note, books: List<Notebook>): String {
        val file = NoteFile.read(remote, uid, ids)
        save(id, file.copy(blocks = withTaskLinks(file.blocks, local)))
        val updated = notes.syncStates().first { it.note.id == id }.note
        return export(updated, uid, notebooks.observe().first().map { it.notebook }.ifEmpty { books })
    }

    private suspend fun createFrom(uid: String, remote: String, books: List<Notebook>): Long {
        val file = NoteFile.read(remote, uid, ids)
        return save(null, file)
    }

    private suspend fun save(id: Long?, file: NoteFile): Long {
        val notebookId = file.notebook?.let { notebooks.findOrCreate(it, file.notebookTint ?: DEFAULT_TINT) }
        val now = System.currentTimeMillis()
        return notes.importRemote(
            id = id,
            uid = file.uid,
            title = file.title,
            plain = NoteHtml.plainText(file.blocks),
            html = NoteHtml.write(file.blocks),
            pinned = file.pinned,
            notebookId = notebookId,
            createdAt = file.created.takeIf { it > 0 } ?: now,
            updatedAt = file.updated.takeIf { it > 0 } ?: now,
            trashedAt = file.trashed,
        )
    }

    /** Le voci collegate alle Attività restano collegate se il loro testo non è cambiato. */
    private fun withTaskLinks(blocks: List<Block>, local: Note): List<Block> {
        val links = localBlocks(local).filterIsInstance<TextBlock>()
            .filter { it.kind == TextKind.Check && it.taskId != null }
            .associate { it.text.text to it.taskId }
        if (links.isEmpty()) return blocks
        return blocks.map { b ->
            if (b is TextBlock && b.kind == TextKind.Check && b.taskId == null) b.copy(taskId = links[b.text.text]) else b
        }
    }

    private fun localBlocks(note: Note): List<Block> =
        if (note.content.isNotBlank()) NoteHtml.read(note.content, ids) else NoteHtml.fromPlain(note.body, ids)

    /** Il file della nota com'è sul telefono. */
    private fun export(note: Note, uid: String, books: List<Notebook>): String {
        val book = books.firstOrNull { it.id == note.notebookId }
        return NoteFile(
            uid = uid,
            title = note.title,
            blocks = localBlocks(note),
            notebook = book?.name,
            notebookTint = book?.tint,
            pinned = note.pinned,
            created = note.createdAt,
            updated = note.updatedAt,
            trashed = note.trashedAt,
        ).write()
    }

    private fun path(uid: String) = "$FOLDER/$uid$EXT"

    companion object {
        const val FOLDER = "Eurise/Note"
        private const val EXT = ".html"
        private const val HTML = "text/html; charset=utf-8"
        private const val DEFAULT_TINT = "Grafite"
    }
}

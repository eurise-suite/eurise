package eu.eurise.core.cloud.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import eu.eurise.core.cloud.WebDav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dove vanno i backup: un cloud WebDAV collegato o una cartella scelta dall'utente (telefono, chiavetta, app cloud). */
interface BackupTarget {
    suspend fun list(): List<BackupFile>
    suspend fun write(name: String, bytes: ByteArray)
    suspend fun read(name: String): ByteArray?
    suspend fun delete(name: String)
    suspend fun rename(from: String, to: String)
}

/** Murena, Nextcloud, kDrive o un altro WebDAV: i file stanno nella cartella Eurise/Backup. */
class DavTarget(private val dav: WebDav, private val folder: String = "Eurise/Backup") : BackupTarget {
    override suspend fun list() = dav.list(folder).filter { !it.folder }.mapNotNull { Retention.parse(it.name, it.size) }
    override suspend fun write(name: String, bytes: ByteArray) {
        dav.mkdirs(folder)
        dav.putBytes("$folder/$name", bytes)
    }
    override suspend fun read(name: String) = dav.getBytes("$folder/$name")
    override suspend fun delete(name: String) = dav.delete("$folder/$name")
    override suspend fun rename(from: String, to: String) = dav.move("$folder/$from", "$folder/$to")
}

/**
 * Una cartella scelta con il selettore di Android: del telefono, di una chiavetta o di un'app cloud che si mostra
 * nel selettore (Nextcloud, Google Drive, Dropbox, OneDrive, MEGA…). Il permesso resta alla sola cartella.
 */
class FolderTarget(private val context: Context, private val tree: Uri) : BackupTarget {
    private val resolver get() = context.contentResolver
    private val root get() = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private suspend fun children(): List<Triple<String, String, Long?>> = withContext(Dispatchers.IO) {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = mutableListOf<Triple<String, String, Long?>>()
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE),
            null, null, null,
        )?.use { c -> while (c.moveToNext()) out += Triple(c.getString(0), c.getString(1).orEmpty(), if (c.isNull(2)) null else c.getLong(2)) }
        out
    }

    private suspend fun doc(name: String): Uri? =
        children().firstOrNull { it.second == name }?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.first) }

    override suspend fun list() = children().mapNotNull { Retention.parse(it.second, it.third) }

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val uri = DocumentsContract.createDocument(resolver, root, "application/octet-stream", name) ?: error("Cartella non scrivibile")
        resolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("Cartella non scrivibile")
        // Alcuni selettori aggiungono un'estensione: si rimette il nome giusto.
        val written = children().firstOrNull { DocumentsContract.buildDocumentUriUsingTree(tree, it.first) == uri }?.second
        if (written != null && written != name) runCatching { DocumentsContract.renameDocument(resolver, uri, name) }
        Unit
    }

    override suspend fun read(name: String): ByteArray? {
        val uri = doc(name) ?: return null
        return withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } }
    }

    override suspend fun delete(name: String) {
        val uri = doc(name) ?: return
        withContext(Dispatchers.IO) { DocumentsContract.deleteDocument(resolver, uri) }
    }

    override suspend fun rename(from: String, to: String) {
        val uri = doc(from) ?: return
        val renamed = withContext(Dispatchers.IO) { runCatching { DocumentsContract.renameDocument(resolver, uri, to) }.getOrNull() }
        if (renamed == null) {
            // Chi non sa rinominare: si copia con il nuovo nome e si toglie il vecchio.
            val bytes = read(from) ?: return
            write(to, bytes)
            delete(from)
        }
    }
}

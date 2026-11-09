package eu.eurise.feature.drive

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Una voce di una cartella scelta dal selettore di Android. */
data class TreeEntry(val name: String, val id: String, val folder: Boolean, val size: Long?, val modified: Long?)

/**
 * Una cartella esposta ad Android da un'altra app (kDrive, Proton Drive, Nextcloud…), scelta una volta dal selettore di
 * sistema. L'accesso al servizio resta tutto in quell'app: qui non passano né password né token. I percorsi sono nomi.
 */
class TreeFs(context: Context, private val tree: Uri) {
    private val resolver = context.contentResolver
    private val ids = ConcurrentHashMap<String, String>()
    private val rootId = DocumentsContract.getTreeDocumentId(tree)

    private fun doc(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    /** Le app cloud spesso rispondono "sto caricando" e completano dopo: si riprova per qualche secondo. */
    private suspend fun children(id: String): List<TreeEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        repeat(8) { attempt ->
            val cursor = resolver.query(uri, columns, null, null, null) ?: throw IOException("nessuna risposta")
            val (loading, rows) = cursor.use { c ->
                val list = ArrayList<TreeEntry>()
                while (c.moveToNext()) {
                    list += TreeEntry(
                        c.getString(1).orEmpty(), c.getString(0),
                        c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        if (c.isNull(3)) null else c.getLong(3),
                        if (c.isNull(4)) null else c.getLong(4),
                    )
                }
                c.extras.getBoolean(DocumentsContract.EXTRA_LOADING) to list
            }
            if (!loading || attempt == 7) return rows
            delay(1_000)
        }
        return emptyList()
    }

    private suspend fun idOf(path: String): String {
        if (path.isEmpty()) return rootId
        ids[path]?.let { return it }
        val parent = idOf(path.substringBeforeLast('/', ""))
        val name = path.substringAfterLast('/')
        val found = children(parent).firstOrNull { it.name == name } ?: throw FileNotFoundException(path)
        ids[path] = found.id
        return found.id
    }

    private fun parent(path: String) = path.substringBeforeLast('/', "")
    private fun forget(path: String) = ids.keys.removeAll { it == path || it.startsWith("$path/") }

    suspend fun list(path: String): List<TreeEntry> = children(idOf(path)).filter { !it.name.startsWith(".") }

    suspend fun mkdir(path: String) {
        DocumentsContract.createDocument(resolver, doc(idOf(parent(path))), DocumentsContract.Document.MIME_TYPE_DIR, path.substringAfterLast('/')) ?: throw IOException("cartella non creata")
    }

    suspend fun read(path: String, target: File) {
        target.parentFile?.mkdirs()
        val input = resolver.openInputStream(doc(idOf(path))) ?: throw FileNotFoundException(path)
        input.use { i -> target.outputStream().use { i.copyTo(it) } }
    }

    suspend fun write(path: String, source: android.net.Uri) {
        val name = path.substringAfterLast('/')
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        val created = DocumentsContract.createDocument(resolver, doc(idOf(parent(path))), mime, name) ?: throw IOException("file non creato")
        val input = resolver.openInputStream(source) ?: throw IOException("file non leggibile")
        input.use { i -> (resolver.openOutputStream(created) ?: throw IOException("scrittura rifiutata")).use { i.copyTo(it) } }
    }

    suspend fun rename(path: String, newName: String) {
        DocumentsContract.renameDocument(resolver, doc(idOf(path)), newName)
        forget(path)
    }

    suspend fun move(path: String, folder: String) {
        DocumentsContract.moveDocument(resolver, doc(idOf(path)), doc(idOf(parent(path))), doc(idOf(folder))) ?: throw IOException("spostamento rifiutato")
        forget(path)
    }

    suspend fun delete(path: String) {
        if (!DocumentsContract.deleteDocument(resolver, doc(idOf(path)))) throw IOException("eliminazione rifiutata")
        forget(path)
    }
}

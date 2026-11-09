package eu.eurise.feature.drive

import eu.eurise.core.cloud.DavEntry
import eu.eurise.core.cloud.WebDav
import eu.eurise.core.cloud.crypto.Cryptomator
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Una voce di una cartella cifrata, con il nome in chiaro. */
data class VaultEntry(val name: String, val folder: Boolean, val size: Long?)

/**
 * Una cartella cifrata aperta, sopra un account WebDAV. Il navigatore parla per percorsi in chiaro; qui diventano i nomi
 * cifrati del formato Cryptomator. [temp] accoglie i file cifrati di passaggio.
 */
class VaultFs(private val dav: WebDav, private val root: String, val vault: Cryptomator.Vault, private val temp: File) {
    private val dirIds = ConcurrentHashMap<String, String>()

    private fun contents(dirId: String) = "$root/${vault.dirPath(dirId)}"

    private fun stored(name: String, dirId: String): String {
        val encrypted = vault.encryptName(name, dirId)
        return if (encrypted.length > vault.threshold) vault.shortened(encrypted) else encrypted
    }

    private fun node(name: String, dirId: String) = "${contents(dirId)}/${stored(name, dirId)}"
    private fun isShort(name: String, dirId: String) = stored(name, dirId).endsWith(".c9s")
    private fun fileAt(name: String, dirId: String) = node(name, dirId).let { if (it.endsWith(".c9s")) "$it/contents.c9r" else it }

    private fun parent(path: String) = path.substringBeforeLast('/', "")
    private fun base(path: String) = path.substringAfterLast('/')

    /** L'identificativo della cartella in chiaro [path]; la radice è la stringa vuota. */
    private suspend fun dirId(path: String): String {
        if (path.isEmpty()) return ""
        dirIds[path]?.let { return it }
        val id = readId(node(base(path), dirId(parent(path))))
        dirIds[path] = id
        return id
    }

    private suspend fun readId(node: String) = dav.getBytes("$node/dir.c9r")?.toString(Charsets.UTF_8)?.trim() ?: throw FileNotFoundException(node)

    suspend fun list(path: String): List<VaultEntry> {
        val id = dirId(path)
        val folder = contents(id)
        return dav.list(folder).mapNotNull { entry -> runCatching { decode(folder, entry, id) }.getOrNull() }
    }

    private suspend fun decode(folder: String, entry: DavEntry, id: String): VaultEntry? = when {
        entry.name.endsWith(".c9s") -> {
            val real = dav.get("$folder/${entry.name}/name.c9s")?.first?.trim() ?: throw FileNotFoundException(entry.name)
            val file = dav.stat("$folder/${entry.name}/contents.c9r")
            VaultEntry(vault.decryptName(real, id), file == null, file?.size?.let(Cryptomator::cleartextSize))
        }
        !entry.name.endsWith(".c9r") -> null
        else -> VaultEntry(vault.decryptName(entry.name, id), entry.folder, if (entry.folder) null else entry.size?.let(Cryptomator::cleartextSize))
    }

    private suspend fun prepareNode(name: String, id: String) {
        if (isShort(name, id)) {
            val node = node(name, id)
            dav.mkdirs(node)
            dav.putBytes("$node/name.c9s", vault.encryptName(name, id).toByteArray())
        }
    }

    private suspend fun writeMarker(name: String, parentId: String, childId: String) {
        val node = node(name, parentId)
        dav.mkdirs(node)
        if (isShort(name, parentId)) prepareNode(name, parentId)
        dav.putBytes("$node/dir.c9r", childId.toByteArray())
    }

    suspend fun mkdir(path: String) {
        val parentId = dirId(parent(path))
        val id = UUID.randomUUID().toString()
        dav.mkdirs(contents(id))
        writeMarker(base(path), parentId, id)
    }

    /** Cifra [source] e lo carica; il file in chiaro resta dov'è. */
    suspend fun write(path: String, source: File) {
        val id = dirId(parent(path))
        val sealed = File.createTempFile("vault", ".c9r", temp)
        try {
            source.inputStream().use { input -> sealed.outputStream().use { vault.encrypt(input, it) } }
            prepareNode(base(path), id)
            dav.upload(fileAt(base(path), id), sealed)
        } finally {
            sealed.delete()
        }
    }

    /** Scarica e decifra in [target]; se il file è stato manomesso non resta una copia a metà. */
    suspend fun read(path: String, target: File) {
        val id = dirId(parent(path))
        val sealed = File.createTempFile("vault", ".c9r", temp)
        try {
            if (!dav.download(fileAt(base(path), id), sealed)) throw FileNotFoundException(path)
            target.parentFile?.mkdirs()
            try {
                sealed.inputStream().use { input -> target.outputStream().use { vault.decrypt(input, it) } }
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        } finally {
            sealed.delete()
        }
    }

    /** Sposta o rinomina: il nome cifrato dipende dalla cartella, quindi cambia anche spostando. */
    suspend fun move(from: String, to: String, folder: Boolean) {
        val fromId = dirId(parent(from))
        val toId = dirId(parent(to))
        val oldName = base(from)
        val newName = base(to)
        val oldNode = node(oldName, fromId)
        when {
            !isShort(oldName, fromId) && !isShort(newName, toId) -> dav.move(oldNode, node(newName, toId))
            folder -> {
                writeMarker(newName, toId, readId(oldNode))
                dav.delete(oldNode)
            }
            else -> {
                prepareNode(newName, toId)
                dav.move(fileAt(oldName, fromId), fileAt(newName, toId))
                if (isShort(oldName, fromId)) dav.delete(oldNode)
            }
        }
        dirIds.keys.removeAll { it == from || it.startsWith("$from/") }
    }

    suspend fun delete(path: String, folder: Boolean) {
        if (folder) {
            list(path).forEach { delete(if (path.isEmpty()) it.name else "$path/${it.name}", it.folder) }
            dav.delete(contents(dirId(path)))
            dirIds.keys.removeAll { it == path || it.startsWith("$path/") }
        }
        dav.delete(node(base(path), dirId(parent(path))))
    }
}

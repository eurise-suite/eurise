package eu.eurise.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Size
import eu.eurise.core.cloud.backup.BackupCrypto
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.SecureMove
import eu.eurise.gallery.logic.SecureStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Una foto nella cartella protetta. */
data class SecureItem(val id: String, val name: String, val takenAt: Long, val isVideo: Boolean, val mime: String)

/**
 * Cartella protetta: le foto escono da MediaStore e restano solo qui, cifrate, nello spazio privato dell'app.
 * La chiave è casuale, chiusa con la frase e con il codice di recupero (stesso schema dei backup) e sta in memoria solo
 * a cartella aperta. L'originale si elimina solo dopo aver riletto e confrontato la copia cifrata.
 *
 * Accanto alla tua possono esserci cartelle arrivate **dal backup** ([fromBackup]): quelle del telefono di prima, che il
 * ripristino mette in `secure-dal-backup` quando qui c'erano già foto tue con un'altra frase. Si aprono con la loro frase
 * o il loro codice e si spostano nella tua ([moveAllInto]) o si tengono a parte (tavole GalDalBackup, 21 novembre 2026).
 */
class SecureFolder private constructor(context: Context, val dirName: String = MAIN) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, dirName).apply { if (dirName == MAIN) mkdirs() }
    private val headerFile = File(dir, "header.json")
    private var key: ByteArray? = null
    private val _items = MutableStateFlow<List<SecureItem>?>(null)

    /** Null finché la cartella è chiusa. */
    val items: StateFlow<List<SecureItem>?> = _items

    val isSetUp: Boolean get() = headerFile.exists()
    val isOpen: Boolean get() = key != null

    /** Quante foto e video ci sono, anche a cartella chiusa (si contano i file, non si legge nulla). */
    fun count(): Int = dir.listFiles { f -> f.name.endsWith(".meta") }?.size ?: 0

    /** Crea la cartella e restituisce il codice di recupero, da mostrare una volta sola. */
    fun setup(passphrase: String): String {
        val code = BackupCrypto.newRecoveryCode()
        val keys = BackupCrypto.setup(passphrase.toCharArray(), code)
        val h = keys.header
        fun b(x: ByteArray) = Base64.encodeToString(x, Base64.NO_WRAP)
        headerFile.writeText(
            JSONObject().put("v", 1).put("it", h.iterations).put("sp", b(h.saltPass)).put("wp", b(h.wrapPass)).put("sc", b(h.saltCode)).put("wc", b(h.wrapCode)).toString(),
        )
        key = keys.dataKey
        reload()
        return code
    }

    /** Apre con la frase o con il codice di recupero; falso se sono sbagliati. */
    fun unlock(secret: String): Boolean {
        val j = JSONObject(headerFile.readText())
        fun b(n: String) = Base64.decode(j.getString(n), Base64.NO_WRAP)
        val header = BackupCrypto.KeyHeader(j.getInt("it"), b("sp"), b("wp"), b("sc"), b("wc"))
        key = try { BackupCrypto.unlock(header, secret) } catch (e: BackupCrypto.WrongSecret) { return false }
        reload()
        return true
    }

    // Impronta: la chiave della cartella cifrata ancora una volta con una chiave del telefono (Keystore) che si usa solo
    // dopo l'impronta. La frase resta sempre valida; una nuova impronta registrata nel telefono annulla questa via.
    private val bioFile = File(dir, "bio.json")
    val bioEnabled: Boolean get() = bioFile.exists()

    private fun bioKey(create: Boolean): javax.crypto.SecretKey? {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(BIO_ALIAS, null) as? javax.crypto.SecretKey)?.let { if (!create) return it }
        if (!create) return null
        // Nel chip di sicurezza separato (StrongBox) se il telefono lo ha (SecureKeys).
        return eu.eurise.core.suite.SecureKeys.generateAes(
            {
                setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                setUserAuthenticationRequired(true)
                setUserAuthenticationParameters(0, android.security.keystore.KeyProperties.AUTH_BIOMETRIC_STRONG)
                setInvalidatedByBiometricEnrollment(true)
            },
            BIO_ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        )
    }

    /** Il cifrario da far approvare con l'impronta per accendere l'apertura con l'impronta (cartella aperta). */
    fun bioCipherToEnable(): javax.crypto.Cipher =
        javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply { init(javax.crypto.Cipher.ENCRYPT_MODE, bioKey(create = true)) }

    fun bioEnable(approved: javax.crypto.Cipher) {
        val k = key ?: return
        val sealed = approved.doFinal(k)
        fun b(x: ByteArray) = Base64.encodeToString(x, Base64.NO_WRAP)
        bioFile.writeText(JSONObject().put("iv", b(approved.iv)).put("k", b(sealed)).toString())
    }

    /** Il cifrario per aprire con l'impronta; null (e via dell'impronta spenta) se il telefono l'ha annullata. */
    fun bioCipherToUnlock(): javax.crypto.Cipher? = runCatching {
        val j = JSONObject(bioFile.readText())
        val iv = Base64.decode(j.getString("iv"), Base64.NO_WRAP)
        javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.DECRYPT_MODE, bioKey(create = false), javax.crypto.spec.GCMParameterSpec(128, iv))
        }
    }.getOrElse { bioDisable(); null }

    fun bioUnlock(approved: javax.crypto.Cipher): Boolean = runCatching {
        val j = JSONObject(bioFile.readText())
        key = approved.doFinal(Base64.decode(j.getString("k"), Base64.NO_WRAP))
        reload()
        true
    }.getOrDefault(false)

    fun bioDisable() {
        bioFile.delete()
        runCatching { java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(BIO_ALIAS) }
    }

    fun lock() {
        key?.fill(0)
        key = null
        _items.value = null
    }

    /** Toglie la copia in chiaro dell'ultimo video: si fa al ritorno dal lettore, che intanto la sta usando. */
    fun clearPlayback() {
        File(app.cacheDir, "secure-play").deleteRecursively()
    }

    private fun data(id: String) = File(dir, "$id.enc")
    private fun thumb(id: String) = File(dir, "$id.thumb")
    private fun meta(id: String) = File(dir, "$id.meta")

    private fun reload() {
        val k = key ?: return
        _items.value = dir.listFiles { f -> f.name.endsWith(".meta") }.orEmpty().mapNotNull { file ->
            val id = file.name.removeSuffix(".meta")
            runCatching {
                val out = ByteArrayOutputStream()
                file.inputStream().use { SecureStream.decrypt(k, "$id/meta", it, out) }
                val j = JSONObject(out.toString("UTF-8"))
                SecureItem(id, j.getString("name"), j.getLong("taken"), j.getBoolean("video"), j.getString("mime"))
            }.getOrNull()
        }.sortedByDescending { it.takenAt }
    }

    /** Copia e verifica; l'originale resta dov'è finché [drop] o la conferma di Android non decidono. */
    fun add(item: MediaItem): String {
        val k = key ?: throw IOException("chiusa")
        val id = UUID.randomUUID().toString()
        val resolver = app.contentResolver
        val uri = Uri.parse(item.uri)
        val source = MessageDigest.getInstance("SHA-256")
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("foto non leggibile")
            DigestInputStream(input, source).use { stream -> data(id).outputStream().use { SecureStream.encrypt(k, id, stream, it) } }
            val check = MessageDigest.getInstance("SHA-256")
            data(id).inputStream().use { SecureStream.decrypt(k, id, it, DigestOutputStream(NullOutput, check)) }
            if (!source.digest().contentEquals(check.digest())) throw IOException("copia diversa dall'originale")
            runCatching {
                val small = resolver.loadThumbnail(uri, Size(256, 256), null)
                val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
                thumb(id).outputStream().use { SecureStream.encrypt(k, "$id/thumb", ByteArrayInputStream(bytes), it) }
            }
            val json = JSONObject().put("name", item.name.ifEmpty { id }).put("taken", item.takenAt).put("video", item.isVideo)
                .put("mime", mimeOf(item.name, item.isVideo)).toString().toByteArray()
            meta(id).outputStream().use { SecureStream.encrypt(k, "$id/meta", ByteArrayInputStream(json), it) }
        } catch (e: Exception) {
            drop(id)
            throw e
        }
        return id
    }

    fun drop(id: String) {
        data(id).delete(); thumb(id).delete(); meta(id).delete()
        reload()
    }

    fun refresh() = reload()

    fun thumbnail(item: SecureItem): Bitmap? {
        val k = key ?: return null
        if (!thumb(item.id).exists()) return null
        val out = ByteArrayOutputStream()
        return runCatching {
            thumb(item.id).inputStream().use { SecureStream.decrypt(k, "${item.id}/thumb", it, out) }
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        }.getOrNull()
    }

    fun image(item: SecureItem, maxSide: Int = 2048): Bitmap? {
        val k = key ?: return null
        val out = ByteArrayOutputStream()
        return runCatching {
            data(item.id).inputStream().use { SecureStream.decrypt(k, item.id, it, out) }
            val bytes = out.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /** Copia in chiaro per il lettore video, cancellata alla chiusura della cartella. */
    fun videoFile(item: SecureItem): File? {
        val k = key ?: return null
        val target = File(File(app.cacheDir, "secure-play").apply { mkdirs() }, item.name)
        return runCatching { data(item.id).inputStream().use { i -> target.outputStream().use { SecureStream.decrypt(k, item.id, i, it) } }; target }.getOrNull()
    }

    /** Riporta la foto tra quelle del telefono; la copia cifrata sparisce solo a scrittura finita. */
    fun restore(item: SecureItem): Boolean {
        val k = key ?: return false
        val resolver = app.contentResolver
        val folder = if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val table = if (item.isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(table, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Eurise")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: return false
        val ok = runCatching {
            val output = resolver.openOutputStream(uri) ?: throw IOException()
            output.use { o -> data(item.id).inputStream().use { i -> SecureStream.decrypt(k, item.id, i, o) } }
        }.isSuccess
        if (!ok) { resolver.delete(uri, null, null); return false }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        drop(item.id)
        return true
    }

    private object NullOutput : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    /**
     * Sposta tutte le foto di questa cartella (aperta) in [target] (aperta): ognuna si ricifra con la chiave di [target]
     * in memoria ([SecureMove]), si rilegge e si confronta, e solo dopo sparisce da qui. Se una non passa resta qui e si
     * va avanti con le altre. Quando non resta niente la cartella sparisce, intestazione compresa. Restituisce quante
     * foto non sono passate. Fuori dal thread principale.
     */
    fun moveAllInto(target: SecureFolder, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        // Copie delle chiavi: se intanto una cartella si chiude (lock() azzera la chiave) il lavoro finisce con quella giusta.
        val k = key?.copyOf() ?: throw IOException("chiusa")
        val tk = target.key?.copyOf() ?: run { k.fill(0); throw IOException("chiusa") }
        try {
            return moveAll(k, tk, target, progress)
        } finally {
            k.fill(0); tk.fill(0)
        }
    }

    private fun moveAll(k: ByteArray, tk: ByteArray, target: SecureFolder, progress: (Int, Int) -> Unit): Int {
        val ids = dir.listFiles { f -> f.name.endsWith(".meta") }.orEmpty().map { it.name.removeSuffix(".meta") }
        var failed = 0
        ids.forEachIndexed { i, id ->
            val newId = if (target.meta(id).exists() || target.data(id).exists()) UUID.randomUUID().toString() else id
            val parts = listOf(Triple(data(id), target.data(newId), id to newId), Triple(thumb(id), target.thumb(newId), "$id/thumb" to "$newId/thumb"), Triple(meta(id), target.meta(newId), "$id/meta" to "$newId/meta"))
            val ok = runCatching {
                for ((from, to, labels) in parts) {
                    if (!from.exists()) continue
                    val part = File(to.path + ".arrivo")
                    val sha = from.inputStream().use { src -> part.outputStream().use { o -> SecureMove.reencrypt(k, labels.first, tk, labels.second, src, o) } }
                    val check = part.inputStream().use { SecureMove.digest(tk, labels.second, it) }
                    if (!sha.contentEquals(check)) throw IOException("copia diversa")
                    if (!part.renameTo(to)) throw IOException("non si scrive")
                }
            }.isSuccess
            if (ok) parts.forEach { it.first.delete() }
            else { failed++; parts.forEach { (_, to, _) -> to.delete(); File(to.path + ".arrivo").delete() } }
            progress(i + 1, ids.size)
        }
        target.reload()
        if (dir.listFiles { f -> f.name.endsWith(".enc") || f.name.endsWith(".meta") }.isNullOrEmpty()) discard() else reload()
        return failed
    }

    /** Toglie dal telefono questa cartella dal backup, chiusa o aperta (resta nei backup di prima). Mai la tua. */
    fun discard() {
        check(dirName != MAIN) { "la tua cartella non si toglie così" }
        lock()
        dir.deleteRecursively()
        synchronized(Companion) { backups.remove(dirName) }
    }

    companion object {
        private const val BIO_ALIAS = "eurise_gallery_secure_bio"
        const val MAIN = "secure"
        /** Il nome delle cartelle che arrivano dal backup: `secure-dal-backup`, poi `secure-dal-backup-2`… */
        const val FROM_BACKUP = "secure-dal-backup"

        @Volatile private var instance: SecureFolder? = null
        private val backups = HashMap<String, SecureFolder>()
        fun get(context: Context): SecureFolder = instance ?: synchronized(this) { instance ?: SecureFolder(context).also { instance = it } }

        /** Le cartelle arrivate dal backup che ci sono sul telefono (con la loro intestazione e almeno una foto). */
        fun fromBackup(context: Context): List<SecureFolder> = synchronized(this) {
            val root = context.applicationContext.filesDir
            root.listFiles { f -> f.isDirectory && (f.name == FROM_BACKUP || f.name.startsWith("$FROM_BACKUP-")) }.orEmpty()
                .filter { File(it, "header.json").exists() && it.listFiles { f -> f.name.endsWith(".meta") }.orEmpty().isNotEmpty() }
                .sortedBy { it.name }
                .map { d -> backups.getOrPut(d.name) { SecureFolder(context, d.name) } }
        }
    }
}

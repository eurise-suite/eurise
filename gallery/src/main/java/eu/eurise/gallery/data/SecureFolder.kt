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
 */
class SecureFolder private constructor(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "secure").apply { mkdirs() }
    private val headerFile = File(dir, "header.json")
    private var key: ByteArray? = null
    private val _items = MutableStateFlow<List<SecureItem>?>(null)

    /** Null finché la cartella è chiusa. */
    val items: StateFlow<List<SecureItem>?> = _items

    val isSetUp: Boolean get() = headerFile.exists()
    val isOpen: Boolean get() = key != null

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

    companion object {
        @Volatile private var instance: SecureFolder? = null
        fun get(context: Context): SecureFolder = instance ?: synchronized(this) { instance ?: SecureFolder(context).also { instance = it } }
    }
}

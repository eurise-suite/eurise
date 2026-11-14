package eu.eurise.feature.launcher.sounds

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Da dove viene un suono: originali di Eurise, grandi classici (sintetizzati da noi), brani dell'utente. */
enum class SoundGroup { Eurise, Classic, Mine }

/**
 * Un suono. L'id dice dove sta: «a:alba» nell'APK, «p:primavera» nel pacchetto scaricato, «m:nome» tra i pezzi
 * dell'utente. [short] per gli avvisi brevi (messaggi, email), altrimenti suoneria.
 */
data class Sound(
    val id: String,
    val group: SoundGroup,
    val short: Boolean,
    val title: String,
    val subtitle: String,
    val seconds: Int,
    /** Falso per i suoni del pacchetto non ancora scaricato. */
    val available: Boolean = true,
)

/**
 * I suoni di Eurise: pochi e piccoli nell'APK, il resto in un pacchetto che si scarica a richiesta (pre-release «suoni»
 * del repository, una sola richiesta del file, nessun dato dell'utente), i pezzi dell'utente nella cartella dell'app.
 * Per suoneria di sistema e suonerie per persona il file va copiato tra le suonerie del telefono ([forSystem]).
 */
object SoundLibrary {
    const val PACK_URL = "https://github.com/eurise-suite/eurise/releases/download/suoni/suoni.zip"
    const val PACK_HOST = "github.com"
    const val PACK_MB = "0,7"

    private fun packDir(context: Context) = File(context.filesDir, "sounds/pack")
    fun mineDir(context: Context) = File(context.filesDir, "sounds/mine").apply { mkdirs() }

    suspend fun all(context: Context): List<Sound> = withContext(Dispatchers.IO) {
        val out = ArrayList<Sound>()
        out += index(context.assets.open("sounds/index.txt").bufferedReader().readText(), "a:") { true }
        val pack = packDir(context)
        out += index(context.assets.open("sounds/pack.txt").bufferedReader().readText(), "p:") { File(pack, "$it.ogg").exists() }
        mineDir(context).listFiles().orEmpty().filter { it.extension == "m4a" }.sortedByDescending { it.lastModified() }.forEach { f ->
            out += Sound("m:${f.nameWithoutExtension}", SoundGroup.Mine, false, f.nameWithoutExtension, "", 0)
        }
        out
    }

    private fun index(text: String, prefix: String, available: (String) -> Boolean): List<Sound> = text.lines().mapNotNull { l ->
        val f = l.split('\t')
        if (f.size < 6) return@mapNotNull null
        Sound(prefix + f[0], if (f[1] == "classic") SoundGroup.Classic else SoundGroup.Eurise, f[2] == "short", f[3], f[4], f[5].toIntOrNull() ?: 0, available(f[0]))
    }

    fun packReady(context: Context) = File(packDir(context), "index.txt").exists()

    /** Scarica e apre il pacchetto; [progress] da 0 a 1. */
    suspend fun downloadPack(context: Context, progress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = packDir(context).apply { mkdirs() }
            val conn = (URL(PACK_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: 700_000L
            var read = 0L
            val counting = object : java.io.FilterInputStream(conn.inputStream.buffered()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) { read += it; progress((read.toFloat() / total).coerceAtMost(1f)) } }
            }
            ZipInputStream(counting).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = File(e.name).name
                    if (e.isDirectory || !(name.endsWith(".ogg") || name == "index.txt")) continue
                    File(dir, name).outputStream().use { zip.copyTo(it) }
                }
            }
            conn.disconnect()
            packReady(context)
        }.getOrDefault(false)
    }

    /** Il file del suono, per chi ha bisogno di un percorso (i suoni dell'APK si copiano una volta in cache). */
    fun file(context: Context, id: String): File? {
        val name = id.substringAfter(':')
        return when {
            id.startsWith("a:") -> File(context.cacheDir, "sounds/$name.ogg").takeIf { it.exists() } ?: runCatching {
                File(context.cacheDir, "sounds").mkdirs()
                File(context.cacheDir, "sounds/$name.ogg").also { f -> context.assets.open("sounds/$name.ogg").use { i -> f.outputStream().use { i.copyTo(it) } } }
            }.getOrNull()
            id.startsWith("p:") -> File(packDir(context), "$name.ogg").takeIf { it.exists() }
            id.startsWith("m:") -> File(mineDir(context), "$name.m4a").takeIf { it.exists() }
            else -> null
        }
    }

    suspend fun title(context: Context, id: String?): String? = when (id) {
        null -> null
        SoundIds.SILENT -> null
        else -> all(context).firstOrNull { it.id == id }?.title ?: id.substringAfter(':')
    }

    /**
     * Copia il suono tra le suonerie del telefono (Ringtones/Eurise) e ne dà l'indirizzo: serve alla suoneria di sistema
     * e alla suoneria di un contatto, che suonano anche quando Eurise non è aperta. Da Android 10 senza permessi.
     */
    suspend fun forSystem(context: Context, id: String): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext null
        val src = file(context, id) ?: return@withContext null
        val display = "Eurise ${src.nameWithoutExtension}.${src.extension}"
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), "${MediaStore.Audio.Media.DISPLAY_NAME} = ?", arrayOf(display), null)
            ?.use { c -> if (c.moveToFirst()) return@withContext android.content.ContentUris.withAppendedId(collection, c.getLong(0)) }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, display)
            put(MediaStore.Audio.Media.TITLE, src.nameWithoutExtension.replaceFirstChar { it.uppercase() })
            put(MediaStore.Audio.Media.MIME_TYPE, if (src.extension == "m4a") "audio/mp4" else "audio/ogg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Ringtones/Eurise")
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, true)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return@withContext null
        runCatching {
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            uri
        }.getOrElse { resolver.delete(uri, null, null); null }
    }

    /** Suoneria delle chiamate o avviso generale del telefono (serve il permesso «Modifica impostazioni di sistema»). */
    suspend fun setSystemDefault(context: Context, use: SoundUse, id: String): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val uri = forSystem(context, id) ?: return false
        val type = if (use == SoundUse.Calls) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION
        return runCatching { RingtoneManager.setActualDefaultRingtoneUri(context, type, uri); true }.getOrDefault(false)
    }

    /** Suoneria di una persona nella rubrica: vale con qualsiasi app Telefono. [id] null la toglie. */
    suspend fun setContactRingtone(context: Context, lookupKey: String, id: String?): Boolean = withContext(Dispatchers.IO) {
        if (!eu.eurise.core.contacts.Contacts.canWrite(context)) return@withContext false
        val uri = id?.let { forSystem(context, it) }
        if (id != null && uri == null) return@withContext false
        val values = ContentValues().apply { put(ContactsContract.Contacts.CUSTOM_RINGTONE, uri?.toString()) }
        runCatching {
            context.contentResolver.update(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey), values, null, null) > 0
        }.getOrDefault(false)
    }
}

package eu.eurise.keyboard

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.UserDictionary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Quello che la tastiera ha imparato, nel backup cifrato della suite (lavoro principale della Tastiera, 12 novembre).
 * L'app Eurise, quando fa il backup, legge da qui uno zip e lo mette nel suo; al ripristino lo riconsegna.
 *
 * Nello zip: le parole e le frasi imparate scrivendo (dizionari «UserHistoryDictionary» di HeliBoard), il dizionario
 * personale (parole aggiunte a mano, con le abbreviazioni) e il modo di toccare i tasti (`eurise_touch`).
 * Solo app Eurise con la stessa firma (permesso della suite nel manifest); nulla esce dal telefono da qui.
 *
 * - `content://<pacchetto>.eurise.backup/export`, lettura: lo zip.
 * - `content://<pacchetto>.eurise.backup/import`, scrittura: lo zip da ripristinare; poi `call("apply")`.
 */
class BackupProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException()
        return when (uri.lastPathSegment) {
            "export" -> {
                val f = File(ctx.cacheDir, "eurise-backup-export.zip")
                export(ctx, f)
                ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            "import" -> {
                val f = importFile(ctx).apply { delete() }
                ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
            }
            else -> throw FileNotFoundException(uri.toString())
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (method != "apply") return null
        // `call` non passa dal permesso del manifesto: lo si controlla qui.
        if (!SuiteTrust.callerAllowed(ctx)) throw SecurityException("Eurise: solo le app della suite")
        val f = importFile(ctx)
        val ok = f.exists() && runCatching { restore(ctx, f) }.isSuccess
        f.delete()
        // I dizionari aperti in memoria riscriverebbero i file vecchi: la tastiera riparte subito e rilegge tutto.
        if (ok) Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 300)
        return Bundle().apply { putBoolean("ok", ok) }
    }

    private fun importFile(ctx: Context) = File(ctx.cacheDir, "eurise-backup-import.zip")

    companion object {
        private const val HISTORY = "UserHistoryDictionary"
        private const val TOUCH = "eurise_touch.xml"
        private const val PERSONAL = "personal.json"

        fun export(ctx: Context, target: File) {
            TouchModel.save()
            ZipOutputStream(target.outputStream()).use { zip ->
                // Dizionari delle parole imparate: file o cartelle (formato v4) che cominciano con il nome.
                ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith(HISTORY) }.forEach { add(zip, it, "history/${it.name}") }
                File(ctx.applicationInfo.dataDir, "shared_prefs/$TOUCH").takeIf { it.exists() }?.let { add(zip, it, "prefs/$TOUCH") }
                zip.putNextEntry(ZipEntry(PERSONAL))
                zip.write(personalWords(ctx).toString().toByteArray())
                zip.closeEntry()
            }
        }

        private fun add(zip: ZipOutputStream, f: File, name: String) {
            if (f.isDirectory) { f.listFiles().orEmpty().forEach { add(zip, it, "$name/${it.name}") }; return }
            zip.putNextEntry(ZipEntry(name))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }

        private fun personalWords(ctx: Context): JSONArray {
            val out = JSONArray()
            runCatching {
                ctx.contentResolver.query(
                    UserDictionary.Words.CONTENT_URI,
                    arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY, UserDictionary.Words.LOCALE, UserDictionary.Words.SHORTCUT),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        out.put(JSONObject().put("w", c.getString(0)).put("f", c.getInt(1))
                            .put("l", c.getString(2) ?: "").put("s", c.getString(3) ?: ""))
                    }
                }
            }
            return out
        }

        /** Rimette al loro posto i file dello zip (solo nomi attesi, niente percorsi fuori dalla cartella). */
        fun restore(ctx: Context, zipFile: File) {
            ZipInputStream(zipFile.inputStream()).use { zip ->
                var cleared = false
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    if (e.isDirectory || name.contains("..") || name.startsWith("/")) continue
                    when {
                        name.startsWith("history/$HISTORY") -> {
                            if (!cleared) {
                                ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith(HISTORY) }.forEach { it.deleteRecursively() }
                                cleared = true
                            }
                            val target = File(ctx.filesDir, name.removePrefix("history/")).apply { parentFile?.mkdirs() }
                            target.outputStream().use { zip.copyTo(it) }
                        }
                        name == "prefs/$TOUCH" -> {
                            val target = File(ctx.applicationInfo.dataDir, "shared_prefs/$TOUCH").apply { parentFile?.mkdirs() }
                            target.outputStream().use { zip.copyTo(it) }
                        }
                        name == PERSONAL -> addPersonal(ctx, JSONArray(zip.readBytes().toString(Charsets.UTF_8)))
                    }
                }
            }
        }

        /** Aggiunge al dizionario personale le parole che mancano (quelle già presenti restano come sono). */
        private fun addPersonal(ctx: Context, words: JSONArray) {
            val have = personalWords(ctx).let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("w") + "\u0000" + o.getString("l") } }.toHashSet() }
            for (i in 0 until words.length()) {
                val o = words.getJSONObject(i)
                val w = o.optString("w").takeIf { it.isNotBlank() } ?: continue
                val l = o.optString("l")
                if (!have.add(w + "\u0000" + l)) continue
                runCatching {
                    ctx.contentResolver.insert(UserDictionary.Words.CONTENT_URI, ContentValues().apply {
                        put(UserDictionary.Words.WORD, w)
                        put(UserDictionary.Words.FREQUENCY, o.optInt("f", 250))
                        put(UserDictionary.Words.LOCALE, l.ifEmpty { null })
                        put(UserDictionary.Words.APP_ID, 0)
                        o.optString("s").takeIf { it.isNotEmpty() }?.let { put(UserDictionary.Words.SHORTCUT, it) }
                    })
                }
            }
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(uri: Uri): String? = if (uri.lastPathSegment == "export") "application/zip" else null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

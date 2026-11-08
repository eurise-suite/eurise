package eu.eurise.core.cloud.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Che cosa entra nel backup: il database di Eurise (note, attività, collegamenti…) e le impostazioni delle app
 * (Scrivania, bolle, Orologio, farmaci, date…). Restano fuori le cache, gli account cloud e le chiavi del
 * backup stesso: sono legati al Keystore di questo telefono e sul nuovo si ricollegano.
 */
object BackupArchive {
    private const val DB = "eurise.db"
    private const val PENDING = "restore_pending"

    private fun included(prefs: String): Boolean =
        (prefs.startsWith("eurise_") || prefs == "clock") && !prefs.startsWith("eurise_feeds_")

    /** Lo zip del backup, ancora in chiaro (si cifra subito dopo, senza toccare il disco). */
    fun create(context: Context, createdAt: Long): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(JSONObject().put("format", 1).put("createdAt", createdAt).put("app", context.packageName.removeSuffix(".debug")).toString().toByteArray())
            zip.closeEntry()
            snapshot(context)?.let { db ->
                zip.putNextEntry(ZipEntry("databases/$DB"))
                db.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                db.delete()
            }
            prefsDir(context).listFiles().orEmpty()
                .filter { it.name.endsWith(".xml") && included(it.name.removeSuffix(".xml")) }
                .forEach { f ->
                    zip.putNextEntry(ZipEntry("shared_prefs/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        return out.toByteArray()
    }

    /**
     * Una copia coerente del database mentre l'app lo usa: VACUUM INTO dove c'è (Android 11+), altrimenti si
     * riversa il diario nel file principale e lo si copia.
     */
    private fun snapshot(context: Context): File? {
        val src = context.getDatabasePath(DB)
        if (!src.exists()) return null
        val tmp = File(context.cacheDir, "backup-snapshot.db").apply { delete() }
        runCatching {
            SQLiteDatabase.openDatabase(src.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                if (Build.VERSION.SDK_INT >= 30) {
                    db.execSQL("VACUUM INTO ?", arrayOf(tmp.path))
                } else {
                    db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                }
            }
        }
        if (!tmp.exists()) src.copyTo(tmp, overwrite = true)
        return tmp
    }

    private fun prefsDir(context: Context) = File(context.applicationInfo.dataDir, "shared_prefs")

    /**
     * Prepara il ripristino: controlla lo zip e lo apre in una cartella d'attesa. I file prendono il loro posto al
     * prossimo avvio, prima che l'app apra il database ([applyPending]).
     */
    fun stage(context: Context, zipBytes: ByteArray) {
        val dir = File(context.filesDir, PENDING).apply { deleteRecursively(); mkdirs() }
        var hasDb = false
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                val ok = name == "databases/$DB" || (name.startsWith("shared_prefs/") && name.endsWith(".xml") &&
                    !name.contains("..") && included(name.removePrefix("shared_prefs/").removeSuffix(".xml")))
                if (!ok || e.isDirectory) continue
                val target = File(dir, name).apply { parentFile?.mkdirs() }
                target.outputStream().use { zip.copyTo(it) }
                if (name == "databases/$DB") hasDb = true
            }
        }
        if (!hasDb) {
            dir.deleteRecursively()
            throw BackupCrypto.BadFile()
        }
    }

    /** Vero se c'è un ripristino in attesa del riavvio. */
    fun hasPending(context: Context) = File(context.filesDir, PENDING).exists()

    /** Da chiamare all'avvio, prima di aprire il database: mette al loro posto i file del ripristino. */
    fun applyPending(context: Context) {
        val dir = File(context.filesDir, PENDING)
        if (!dir.exists()) return
        runCatching {
            File(dir, "databases/$DB").takeIf { it.exists() }?.let { db ->
                val target = context.getDatabasePath(DB)
                listOf("", "-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
                target.parentFile?.mkdirs()
                db.copyTo(target, overwrite = true)
            }
            File(dir, "shared_prefs").listFiles().orEmpty().forEach { f ->
                f.copyTo(File(prefsDir(context), f.name).also { it.parentFile?.mkdirs() }, overwrite = true)
            }
        }
        dir.deleteRecursively()
    }
}

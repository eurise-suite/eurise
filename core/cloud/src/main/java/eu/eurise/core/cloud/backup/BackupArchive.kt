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
import eu.eurise.core.cloud.lock.LockKeys
import eu.eurise.core.cloud.lock.SuiteSecrets

/** Che cosa c'è in un backup, da mostrare prima di ripristinarlo. Note e attività sono null nei backup di formato 1. */
class BackupContents(
    val createdAt: Long,
    val notes: Int?,
    val tasks: Int?,
    val settings: Boolean,
    val keyboard: Boolean,
    val lockedNotes: Boolean,
)

/** Che cosa c'è ora sul telefono e verrebbe sostituito. */
class PhoneData(val notes: Int, val tasks: Int) {
    val empty get() = notes == 0 && tasks == 0
}

/**
 * Che cosa entra nel backup: il database di Eurise (note, attività, collegamenti…), le impostazioni delle app
 * (Scrivania, bolle, Orologio, farmaci, date…) e quello che ha imparato Eurise Tastiera ([KeyboardBackup]). Restano fuori le cache, gli account cloud e le chiavi del
 * backup stesso: sono legati al Keystore di questo telefono e sul nuovo si ricollegano. La chiave delle note e i segreti
 * affidati dalle altre app ([SuiteSecrets]) sul telefono sono sigillati nel Keystore: entrano come voci a parte.
 */
object BackupArchive {
    private const val DB = "eurise.db"
    private const val PENDING = "restore_pending"
    private const val MANIFEST = "manifest.json"
    /** Nelle preferenze «backup»: quando il ripristino è stato applicato, per «Bentornato». */
    const val RESTORED_AT = "restored_at"
    /** 1 fino al 20 novembre 2026; 2 aggiunge al manifesto i conteggi di note e attività. */
    private const val FORMAT = 2

    private fun included(prefs: String): Boolean =
        (prefs.startsWith("eurise_") || prefs == "clock") && !prefs.startsWith("eurise_feeds_")

    /** Nei backup nuovi i segreti vanno come [SuiteSecrets.BACKUP_ENTRY]; il file in chiaro si accetta solo in ripristino. */
    private fun exported(prefs: String): Boolean = included(prefs) && prefs != SuiteSecrets.LEGACY_PREFS

    /** Lo zip del backup, ancora in chiaro (si cifra subito dopo, senza toccare il disco). */
    fun create(context: Context, createdAt: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val db = snapshot(context)
        ZipOutputStream(out).use { zip ->
            // Formato 2 (20 novembre 2026): il manifesto dice anche quante note e attività ci sono, per mostrarlo prima
            // di ripristinare. Chi legge il formato 1 non guarda il manifesto: resta compatibile nei due sensi.
            val manifest = JSONObject().put("format", FORMAT).put("createdAt", createdAt).put("app", context.packageName.removeSuffix(".debug"))
            db?.let { counts(it) }?.let { (notes, tasks) -> manifest.put("notes", notes).put("tasks", tasks) }
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            db?.let { file ->
                zip.putNextEntry(ZipEntry("databases/$DB"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                file.delete()
            }
            prefsDir(context).listFiles().orEmpty()
                .filter { it.name.endsWith(".xml") && exported(it.name.removeSuffix(".xml")) }
                .forEach { f ->
                    zip.putNextEntry(ZipEntry("shared_prefs/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            KeyboardBackup.export(context)?.let { bytes ->
                zip.putNextEntry(ZipEntry(KeyboardBackup.ENTRY))
                zip.write(bytes)
                zip.closeEntry()
            }
            LockKeys.exportForBackup(context)?.let { key ->
                zip.putNextEntry(ZipEntry(LockKeys.BACKUP_ENTRY))
                zip.write(key)
                zip.closeEntry()
            }
            SuiteSecrets.exportForBackup(context)?.let { secrets ->
                zip.putNextEntry(ZipEntry(SuiteSecrets.BACKUP_ENTRY))
                zip.write(secrets)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Note (fuori dal cestino) e attività di un database di Eurise; null se non si legge. */
    private fun counts(db: File): Pair<Int, Int>? = runCatching {
        SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
            val notes = d.rawQuery("SELECT COUNT(*) FROM notes WHERE trashed_at IS NULL", null).use { it.moveToFirst(); it.getInt(0) }
            val tasks = d.rawQuery("SELECT COUNT(*) FROM tasks", null).use { it.moveToFirst(); it.getInt(0) }
            notes to tasks
        }
    }.getOrNull()

    /** Che cosa c'è ora su questo telefono: serve a dire, prima di ripristinare, che cosa verrà sostituito. */
    fun onPhone(context: Context): PhoneData {
        val db = context.getDatabasePath(DB)
        val (notes, tasks) = (if (db.exists()) counts(db) else null) ?: (0 to 0)
        return PhoneData(notes, tasks)
    }

    /** Che cosa contiene lo zip di un backup, letto in memoria (niente su disco). */
    fun contents(zipBytes: ByteArray, createdAt: Long): BackupContents {
        var manifest: JSONObject? = null
        var hasDb = false
        var keyboard = false
        var locked = false
        var settings = 0
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == MANIFEST -> manifest = runCatching { JSONObject(zip.readBytes().toString(Charsets.UTF_8)) }.getOrNull()
                    e.name == "databases/$DB" -> hasDb = true
                    e.name == KeyboardBackup.ENTRY -> keyboard = true
                    e.name == LockKeys.BACKUP_ENTRY -> locked = true
                    e.name.startsWith("shared_prefs/") -> settings++
                }
            }
        }
        if (!hasDb) throw BackupCrypto.BadFile()
        val m = manifest
        return BackupContents(
            createdAt = createdAt,
            notes = m?.optInt("notes", -1)?.takeIf { it >= 0 },
            tasks = m?.optInt("tasks", -1)?.takeIf { it >= 0 },
            settings = settings > 0,
            keyboard = keyboard,
            lockedNotes = locked,
        )
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
                val ok = name == "databases/$DB" || name == KeyboardBackup.ENTRY || name == LockKeys.BACKUP_ENTRY ||
                    name == SuiteSecrets.BACKUP_ENTRY || (name.startsWith("shared_prefs/") && name.endsWith(".xml") &&
                    !name.contains("..") && included(name.removePrefix("shared_prefs/").removeSuffix(".xml")))
                if (!ok || e.isDirectory) continue
                // Le parole della Tastiera non passano dalla cartella d'attesa in chiaro: si sigillano subito.
                if (name == KeyboardBackup.ENTRY) { KeyboardBackup.keep(context, zip.readBytes()); continue }
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
            // Ripristini preparati da una versione precedente: lo zip della Tastiera stava ancora qui.
            File(dir, KeyboardBackup.ENTRY).takeIf { it.exists() }?.let { KeyboardBackup.keep(context, it.readBytes()) }
            File(dir, LockKeys.BACKUP_ENTRY).takeIf { it.exists() }?.let { LockKeys.restore(context, it.readBytes()) }
            File(dir, SuiteSecrets.BACKUP_ENTRY).takeIf { it.exists() }?.let { SuiteSecrets.restore(context, it.readBytes()) }
            context.getSharedPreferences("backup", Context.MODE_PRIVATE).edit().putLong(RESTORED_AT, System.currentTimeMillis()).commit()
        }
        dir.deleteRecursively()
    }
}

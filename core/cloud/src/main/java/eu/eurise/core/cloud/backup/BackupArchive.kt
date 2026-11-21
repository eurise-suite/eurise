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
import eu.eurise.core.cloud.lock.NoteSeal
import eu.eurise.core.cloud.lock.SuiteSecrets

/**
 * Che cosa entra nel backup: il database di Eurise (note, attività, collegamenti…), le impostazioni delle app
 * (Scrivania, bolle, Orologio, farmaci, date…) e quello che ha imparato Eurise Tastiera ([KeyboardBackup]). Restano fuori le cache, gli account cloud e le chiavi del
 * backup stesso: sono legati al Keystore di questo telefono e sul nuovo si ricollegano. Entra anche l'audio delle note
 * vocali tenuto nelle Note (fino a 60 MB, le più recenti). La chiave delle note e i segreti
 * affidati dalle altre app ([SuiteSecrets]) sul telefono sono sigillati nel Keystore: entrano come voci a parte.
 */
object BackupArchive {
    private const val DB = "eurise.db"
    private const val PENDING = "restore_pending"

    /** Audio delle note vocali tenuto nelle Note (cartella privata, e quelle del cloud non ancora caricate). */
    private const val VOICE_DIR = "note-vocali"
    private const val VOICE_ENTRY = "note-vocali/"

    /**
     * Quanto audio entra al massimo (il backup si prepara in memoria prima di cifrarlo): le note vocali più recenti
     * fino a 60 MB, circa quattro ore di parlato. Quelle in Registrazioni o nel cloud non servono: stanno già fuori.
     */
    private const val VOICE_MAX_BYTES = 60L * 1024 * 1024
    private val VOICE_NAME = Regex("^[A-Za-z0-9._-]{1,80}\\.m4a$")

    private fun included(prefs: String): Boolean =
        (prefs.startsWith("eurise_") || prefs == "clock") && !prefs.startsWith("eurise_feeds_")

    /** Nei backup nuovi i segreti vanno come [SuiteSecrets.BACKUP_ENTRY]; il file in chiaro si accetta solo in ripristino. */
    private fun exported(prefs: String): Boolean = included(prefs) && prefs != SuiteSecrets.LEGACY_PREFS

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
            LockKeys.exportForBackup(context).forEach { (entry, bytes) ->
                zip.putNextEntry(ZipEntry(entry))
                zip.write(bytes)
                zip.closeEntry()
            }
            SuiteSecrets.exportForBackup(context)?.let { secrets ->
                zip.putNextEntry(ZipEntry(SuiteSecrets.BACKUP_ENTRY))
                zip.write(secrets)
                zip.closeEntry()
            }
            var voice = 0L
            File(context.filesDir, VOICE_DIR).listFiles().orEmpty()
                .filter { it.isFile && VOICE_NAME.matches(it.name) }
                .sortedByDescending { it.lastModified() }
                .forEach { f ->
                    if (voice + f.length() > VOICE_MAX_BYTES) return@forEach
                    voice += f.length()
                    zip.putNextEntry(ZipEntry(VOICE_ENTRY + f.name).apply { time = f.lastModified() })
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
                val ok = name == "databases/$DB" || name == KeyboardBackup.ENTRY || name == LockKeys.BACKUP_ENTRY || name == LockKeys.SEALED_ENTRY ||
                    name == SuiteSecrets.BACKUP_ENTRY || (name.startsWith("shared_prefs/") && name.endsWith(".xml") &&
                    !name.contains("..") && included(name.removePrefix("shared_prefs/").removeSuffix(".xml"))) ||
                    (name.startsWith(VOICE_ENTRY) && VOICE_NAME.matches(name.removePrefix(VOICE_ENTRY)))
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

    /**
     * Nel ripristino in attesa, apre la copia della chiave delle note con [secret] (frase o codice) e la mette accanto in
     * chiaro, nei dati privati, per l'avvio: lì si risigilla e si lega all'impronta al primo sblocco. Un backup di prima
     * ha già la chiave in chiaro e non cambia nulla.
     */
    fun openSealedNotes(context: Context, secret: String) {
        val dir = File(context.filesDir, PENDING)
        val sealed = File(dir, LockKeys.SEALED_ENTRY).takeIf { it.exists() } ?: return
        val plain = File(dir, LockKeys.BACKUP_ENTRY)
        if (plain.exists()) return
        NoteSeal.open(sealed.readBytes(), secret)?.let { key -> plain.apply { parentFile?.mkdirs() }.writeBytes(key); key.fill(0) }
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
            File(dir, KeyboardBackup.ENTRY).takeIf { it.exists() }?.let { KeyboardBackup.keep(context, it) }
            File(dir, LockKeys.SEALED_ENTRY).takeIf { it.exists() }?.let { LockKeys.restoreSealed(context, it.readBytes()) }
            File(dir, LockKeys.BACKUP_ENTRY).takeIf { it.exists() }?.let { LockKeys.restore(context, it.readBytes()) }
            File(dir, SuiteSecrets.BACKUP_ENTRY).takeIf { it.exists() }?.let { SuiteSecrets.restore(context, it.readBytes()) }
            // Le note vocali tornano accanto a quelle che ci sono già (stesso nome = stessa registrazione).
            File(dir, VOICE_DIR).listFiles().orEmpty().forEach { f ->
                val target = File(File(context.filesDir, VOICE_DIR).apply { mkdirs() }, f.name)
                if (!target.exists()) f.copyTo(target)
            }
        }
        dir.deleteRecursively()
    }
}

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

/** Che cosa c'è in un backup, da mostrare prima di ripristinarlo. Note e attività sono null nei backup di formato 1. */
class BackupContents(
    val createdAt: Long,
    val notes: Int?,
    val tasks: Int?,
    val settings: Boolean,
    val keyboard: Boolean,
    val lockedNotes: Boolean,
    /** Le app separate nel backup ([SuiteApps]), nell'ordine di [SuiteApps.all], con la riga che hanno dato. */
    val apps: Map<String, String?> = emptyMap(),
)

/** Che cosa c'è ora sul telefono e verrebbe sostituito. */
class PhoneData(val notes: Int, val tasks: Int) {
    val empty get() = notes == 0 && tasks == 0
}

/**
 * Che cosa entra nel backup: il database di Eurise (note, attività, collegamenti…), le impostazioni delle app
 * (Scrivania, bolle, Orologio, farmaci, date…) e i dati delle app separate della suite ([SuiteApps]: Password, Messaggi, Telefono, Galleria, Tastiera…). Restano fuori le cache, gli account cloud e le chiavi del
 * backup stesso: sono legati al Keystore di questo telefono e sul nuovo si ricollegano. La chiave delle note e i segreti
 * affidati dalle altre app ([SuiteSecrets]) sul telefono sono sigillati nel Keystore: entrano come voci a parte.
 */
object BackupArchive {
    private const val DB = "eurise.db"
    private const val PENDING = "restore_pending"
    private const val MANIFEST = "manifest.json"
    /** Nelle preferenze «backup»: quando il ripristino è stato applicato, per «Bentornato». */
    const val RESTORED_AT = "restored_at"
    /** 1 fino al 20 novembre 2026; 2 aggiunge i conteggi di note e attività; 3 (21 novembre) le app separate. */
    private const val FORMAT = 3

    /**
     * Preferenze che entrano oltre a quelle `eurise_*`: Orologio, scelte della dock della Scrivania, servizi AI scelti
     * e aggiunti da Alfred (22 novembre 2026). Restano fuori, apposta: gli interruttori dei servizi che fanno uscire
     * dati (`privacy_center`, `doc_reading`: sul telefono nuovo ripartono spenti, regola «Spenti di base»), gli account
     * cloud e lo stato delle sincronizzazioni (`cloud_*`, `*_sync`, `assistant_agenda`), i file scaricati (`spam_list`,
     * `alfred_brain`) e lo stato del backup stesso.
     */
    private val EXTRA_PREFS = setOf("clock", "dock", "alfred_online")

    /**
     * Cartelle di file tuoi nella memoria privata di Eurise (22 novembre 2026): l'audio delle note vocali tenute
     * nell'app (senza, la nota torna con la registrazione mancante) e le suonerie fatte da te. Fuori il pacchetto dei
     * suoni (si riscarica), il meteo, i file del Drive tenuti offline (stanno sul cloud) e il cestino del Drive.
     */
    private val FILE_DIRS = listOf("note-vocali", "sounds/mine")
    private const val FILES = "files/"

    private fun included(prefs: String): Boolean =
        (prefs.startsWith("eurise_") || prefs in EXTRA_PREFS) && !prefs.startsWith("eurise_feeds_")

    /** La cartella di [FILE_DIRS] di una voce `files/<cartella>/<nome>` dello zip; null se non è una di queste. */
    private fun fileDirOf(entry: String): String? {
        if (!entry.startsWith(FILES)) return null
        val rest = entry.removePrefix(FILES)
        val dir = FILE_DIRS.firstOrNull { rest.startsWith("$it/") } ?: return null
        val name = rest.removePrefix("$dir/")
        return dir.takeIf { name.isNotEmpty() && '/' !in name && name != "." && name != ".." }
    }

    /** Nei backup nuovi i segreti vanno come [SuiteSecrets.BACKUP_ENTRY]; il file in chiaro si accetta solo in ripristino. */
    private fun exported(prefs: String): Boolean = included(prefs) && prefs != SuiteSecrets.LEGACY_PREFS

    /** Lo zip del backup in memoria: solo per backup piccoli e per le prove. */
    fun create(context: Context, createdAt: Long): ByteArray = ByteArrayOutputStream().also { create(context, createdAt, it) }.toByteArray()

    /**
     * Scrive lo zip del backup in [out], che lo cifra mentre arriva ([BackupCrypto.encryptingStream]): niente in chiaro su
     * disco, e non serve tenerlo tutto in memoria. Le app separate ([SuiteApps]) mandano il loro zip in un tubo, che va
     * dritto nella sua voce. Il manifesto va in fondo, con quello che le app hanno detto di sé.
     */
    fun create(context: Context, createdAt: Long, out: java.io.OutputStream) {
        val db = snapshot(context)
        // La copia del database si toglie anche se il backup si ferma a metà (un'app che non risponde, la rete…).
        try { writeZip(context, createdAt, out, db) } finally { db?.let(::dropSnapshot) }
    }

    private fun writeZip(context: Context, createdAt: Long, out: java.io.OutputStream, db: File?) {
        ZipOutputStream(out).use { zip ->
            // Formato 3 (21 novembre 2026): le app separate e le loro righe. Chi legge il formato 1 o 2 non guarda il
            // manifesto: resta compatibile nei due sensi.
            val manifest = JSONObject().put("format", FORMAT).put("createdAt", createdAt).put("app", context.packageName.removeSuffix(".debug"))
            db?.let { counts(it) }?.let { (notes, tasks) -> manifest.put("notes", notes).put("tasks", tasks) }
            db?.let { file ->
                zip.putNextEntry(ZipEntry("databases/$DB"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                dropSnapshot(file)
            }
            prefsDir(context).listFiles().orEmpty()
                .filter { it.name.endsWith(".xml") && exported(it.name.removeSuffix(".xml")) }
                .forEach { f ->
                    zip.putNextEntry(ZipEntry("shared_prefs/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            var files = 0
            FILE_DIRS.forEach { dir ->
                File(context.filesDir, dir).listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach { f ->
                    zip.putNextEntry(ZipEntry("$FILES$dir/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    files++
                }
            }
            if (files > 0) manifest.put("files", files)
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
            val apps = JSONObject()
            val missing = org.json.JSONArray()
            SuiteApps.all.filter { SuiteApps.available(context, it) }.forEach { app ->
                val summary = SuiteApps.summary(context, app)
                zip.putNextEntry(ZipEntry(SuiteApps.entry(app.key)))
                // Se l'app si ferma a metà, la sua voce resta rovinata e al ripristino non le arriva; il resto vale.
                val ok = SuiteApps.export(context, app, zip)
                zip.closeEntry()
                if (ok) apps.put(app.key, summary ?: JSONObject.NULL) else missing.put(app.key)
            }
            manifest.put("apps", apps)
            if (missing.length() > 0) manifest.put("missing", missing)
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
        }
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

    /** Come [contents], per uno zip in memoria. */
    fun contents(zipBytes: ByteArray, createdAt: Long): BackupContents = contents(ByteArrayInputStream(zipBytes), createdAt)

    /** Che cosa contiene lo zip di un backup, letto mentre arriva (lo si legge tutto: così è anche verificato). */
    fun contents(input: java.io.InputStream, createdAt: Long): BackupContents {
        var manifest: JSONObject? = null
        var hasDb = false
        var locked = false
        var settings = 0
        val apps = mutableSetOf<String>()
        val sink = ByteArray(64 * 1024)
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == MANIFEST -> manifest = runCatching { JSONObject(zip.readBytes().toString(Charsets.UTF_8)) }.getOrNull()
                    e.name == "databases/$DB" -> hasDb = true
                    e.name == LockKeys.BACKUP_ENTRY || e.name == LockKeys.SEALED_ENTRY -> locked = true
                    e.name.startsWith("shared_prefs/") -> settings++
                    else -> SuiteApps.keyOf(e.name)?.let { apps += it }
                }
                // Si legge fino in fondo ogni voce: un pezzo rovinato si scopre qui, prima di toccare il telefono.
                while (zip.read(sink) >= 0) Unit
            }
        }
        if (!hasDb) throw BackupCrypto.BadFile()
        val m = manifest
        val said = m?.optJSONObject("apps")
        val missing = m?.optJSONArray("missing")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
        return BackupContents(
            createdAt = createdAt,
            notes = m?.optInt("notes", -1)?.takeIf { it >= 0 },
            tasks = m?.optInt("tasks", -1)?.takeIf { it >= 0 },
            settings = settings > 0,
            keyboard = "keyboard" in apps && "keyboard" !in missing,
            lockedNotes = locked,
            apps = SuiteApps.all.map { it.key }.filter { it in apps && it !in missing }
                .associateWith { k -> said?.optString(k)?.takeIf { s -> s.isNotEmpty() && said.opt(k) != JSONObject.NULL } },
        )
    }

    /**
     * Una copia coerente del database mentre l'app lo usa, da mettere nello zip (21 novembre 2026, sera).
     *
     * Perché un file e non direttamente lo zip: le sole vie che Android dà per leggere un database SQLite coerente
     * mentre l'app ci scrive sono VACUUM INTO (scrive un file) e la copia dopo il checkpoint. Leggere le pagine in un
     * tubo (`sqlite_dbpage`, la «backup API») non è disponibile, e copiare il file vivo senza blocchi può dare un
     * database rotto. La copia ha gli stessi dati, in chiaro come il database stesso, nella cartella privata dell'app; vive
     * pochi secondi, si toglie sempre ([dropSnapshot], anche se il backup si ferma) e quelle rimaste da un'app chiusa a
     * metà si tolgono all'avvio ([dropLeftovers]).
     *
     * Android 11 e oltre: VACUUM INTO. Prima (o se non riesce): checkpoint TRUNCATE, copia del file e controllo della
     * copia (`quick_check`); se un'altra scrittura l'ha rovinata si riprova, fino a tre volte.
     */
    private fun snapshot(context: Context): File? {
        val src = context.getDatabasePath(DB)
        if (!src.exists()) return null
        val tmp = snapshotFile(context).also(::dropSnapshot)
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                SQLiteDatabase.openDatabase(src.path, null, SQLiteDatabase.OPEN_READWRITE).use { db -> db.execSQL("VACUUM INTO ?", arrayOf(tmp.path)) }
            }
            if (tmp.exists() && intact(tmp)) return tmp
            dropSnapshot(tmp)
        }
        repeat(3) {
            runCatching {
                SQLiteDatabase.openDatabase(src.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                }
                src.copyTo(tmp, overwrite = true)
            }
            if (tmp.exists() && intact(tmp)) return tmp
            dropSnapshot(tmp)
        }
        error("Il database non si copia")
    }

    private fun snapshotFile(context: Context) = File(context.cacheDir, "backup-snapshot.db")

    /** Vero se la copia si apre ed è integra (non presa a metà di una scrittura). */
    private fun intact(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
            d.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
        }
    }.getOrDefault(false)

    /** La copia e i file che SQLite le mette accanto. */
    private fun dropSnapshot(file: File) {
        listOf("", "-journal", "-wal", "-shm").forEach { File(file.path + it).delete() }
    }

    /**
     * All'avvio, fuori dal thread principale: la copia lasciata da un backup interrotto (app chiusa a metà) sparisce.
     * Solo i file fermi da qualche ora, così un backup appena partito nello stesso processo non perde i suoi.
     */
    fun dropLeftovers(context: Context, now: Long = System.currentTimeMillis()) {
        val old = { f: File -> f.exists() && now - f.lastModified() > LEFTOVER_AGE }
        snapshotFile(context).takeIf(old)?.let(::dropSnapshot)
        listOf("backup-uscita.eurb", "backup-verifica.eurb", "backup-rinomina.eurb").map { File(context.cacheDir, it) }.filter(old).forEach { it.delete() }
    }

    private val LEFTOVER_AGE = java.util.concurrent.TimeUnit.HOURS.toMillis(6)

    private fun prefsDir(context: Context) = File(context.applicationInfo.dataDir, "shared_prefs")

    /** Come [stage], per uno zip in memoria. */
    fun stage(context: Context, zipBytes: ByteArray) = stage(context, ByteArrayInputStream(zipBytes))

    /**
     * Prepara il ripristino: apre lo zip in una cartella d'attesa. I file di Eurise prendono il loro posto al prossimo
     * avvio, prima che l'app apra il database ([applyPending]); i dati delle app separate si sigillano subito in attesa
     * della loro app ([SuiteApps.keep]), senza passare dalla cartella in chiaro.
     */
    fun stage(context: Context, input: java.io.InputStream) {
        val dir = File(context.filesDir, PENDING).apply { deleteRecursively(); mkdirs() }
        var hasDb = false
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                if (e.isDirectory || name.contains("..")) continue
                val appKey = SuiteApps.keyOf(name)
                if (appKey != null) { SuiteApps.keep(context, appKey, zip); continue }
                val ok = fileDirOf(name) != null || name == "databases/$DB" || name == LockKeys.BACKUP_ENTRY || name == LockKeys.SEALED_ENTRY ||
                    name == SuiteSecrets.BACKUP_ENTRY || (name.startsWith("shared_prefs/") && name.endsWith(".xml") &&
                    included(name.removePrefix("shared_prefs/").removeSuffix(".xml")))
                if (!ok) continue
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
            // Note vocali e suonerie tue: si aggiungono (stesso nome = stesso file); quelli già sul telefono restano.
            FILE_DIRS.forEach { d ->
                File(dir, FILES + d).listFiles().orEmpty().filter { it.isFile }.forEach { f ->
                    f.copyTo(File(File(context.filesDir, d).apply { mkdirs() }, f.name), overwrite = true)
                }
            }
            // Ripristini preparati da una versione precedente: lo zip della Tastiera stava ancora qui.
            File(dir, KeyboardBackup.ENTRY).takeIf { it.exists() }?.let { f -> f.inputStream().use { SuiteApps.keep(context, "keyboard", it) } }
            File(dir, LockKeys.SEALED_ENTRY).takeIf { it.exists() }?.let { LockKeys.restoreSealed(context, it.readBytes()) }
            File(dir, LockKeys.BACKUP_ENTRY).takeIf { it.exists() }?.let { LockKeys.restore(context, it.readBytes()) }
            File(dir, SuiteSecrets.BACKUP_ENTRY).takeIf { it.exists() }?.let { SuiteSecrets.restore(context, it.readBytes()) }
            context.getSharedPreferences("backup", Context.MODE_PRIVATE).edit().putLong(RESTORED_AT, System.currentTimeMillis()).commit()
        }
        dir.deleteRecursively()
    }
}

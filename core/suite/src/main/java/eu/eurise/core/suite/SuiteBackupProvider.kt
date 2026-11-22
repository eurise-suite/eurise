package eu.eurise.core.suite

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Come un'app separata della suite consegna i suoi dati al backup cifrato di Eurise e li riceve al ripristino
 * (area Backup, 21 novembre 2026; docs/stato/BACKUP.md). Lo stesso accordo della Tastiera:
 * - `content://<pacchetto>.eurise.backup/export`, lettura: lo zip dei dati, in un tubo (niente file intermedi);
 * - `content://<pacchetto>.eurise.backup/import`, scrittura: lo zip da rimettere, letto mentre arriva;
 * - `call("apply")`: aspetta che il ripristino sia finito e dice se è riuscito; poi l'app riparte se serve;
 * - `call("summary")`: una riga per la schermata «Cosa rimetto» (per esempio «2.341 SMS»);
 * - `call("ready")`: se ora può rimettere i dati ([ready]).
 *
 * Il fornitore va dichiarato `exported` con `readPermission` e `writePermission` uguali al permesso della suite; qui si
 * controlla anche chi chiama ([SuiteAccess.callerAllowed]), perché `call` non passa dal manifesto. I dati escono solo
 * verso l'app Eurise, che li cifra subito con la frase dell'utente.
 */
abstract class SuiteBackupProvider : ContentProvider() {
    /** Scrive in [zip] i dati da salvare. Gira fuori dal thread principale. */
    protected abstract fun export(context: Context, zip: ZipOutputStream)

    /**
     * Rimette i dati da [zip] (l'utente ha già confermato in Eurise che vanno sostituiti). Gira fuori dal thread
     * principale, mentre lo zip arriva: i nomi con «..» o che cominciano con «/» vanno scartati ([safeName]).
     */
    protected abstract fun restore(context: Context, zip: ZipInputStream)

    /** Una riga breve su che cosa c'è, o null. */
    protected open fun summary(context: Context): String? = null

    /**
     * Vero se ora l'app può rimettere i dati (Messaggi deve essere l'app SMS predefinita, Telefono quella delle
     * chiamate). Se no Eurise li tiene sigillati e riprova più tardi: «import» rifiuta senza leggere niente.
     */
    protected open fun ready(context: Context): Boolean = true

    /** Dopo il ripristino l'app si chiude e riparte, così non riscrive i dati vecchi che aveva in memoria. */
    protected open val restartAfterRestore: Boolean = true

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException()
        SuiteAccess.enforceCaller(ctx)
        return when (uri.lastPathSegment) {
            "export" -> {
                val (read, write) = ParcelFileDescriptor.createPipe()
                Thread {
                    runCatching {
                        ZipOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(write)).use { export(ctx, it) }
                    }.onFailure { runCatching { write.closeWithError(it.message ?: "export") } }
                }.start()
                read
            }
            "import" -> {
                if (!ready(ctx)) throw FileNotFoundException("non ancora pronta")
                val (read, write) = ParcelFileDescriptor.createPipe()
                val worker = Thread {
                    val ok = runCatching {
                        ZipInputStream(ParcelFileDescriptor.AutoCloseInputStream(read)).use { restore(ctx, it) }
                    }.isSuccess
                    synchronized(lock) { result = ok }
                }
                synchronized(lock) { result = null; restoring = worker }
                worker.start()
                write
            }
            else -> throw FileNotFoundException(uri.toString())
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        SuiteAccess.enforceCaller(ctx)
        return when (method) {
            "summary" -> Bundle().apply { putString("summary", runCatching { summary(ctx) }.getOrNull()) }
            "ready" -> Bundle().apply { putBoolean("ready", runCatching { ready(ctx) }.getOrDefault(false)) }
            "apply" -> {
                // Eurise chiude il tubo e chiama subito: si aspetta che il ripristino abbia letto tutto.
                synchronized(lock) { restoring }?.join(APPLY_WAIT)
                val ok = synchronized(lock) { (result == true).also { result = null; restoring = null } }
                if (ok && restartAfterRestore) Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 300)
                Bundle().apply { putBoolean("ok", ok) }
            }
            else -> null
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = if (uri.lastPathSegment == "export") "application/zip" else null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private val lock = Any()
        private var restoring: Thread? = null
        private var result: Boolean? = null
        /** Una cartella protetta grande può metterci: si aspetta fino a mezz'ora. */
        private const val APPLY_WAIT = 30 * 60 * 1000L

        /** Il nome di una voce, o null se prova a uscire dalla cartella. */
        fun safeName(entry: ZipEntry): String? =
            entry.name.takeUnless { entry.isDirectory || it.contains("..") || it.startsWith("/") || it.contains('\\') }

        /** Aggiunge un file (o una cartella, ricorsiva) allo zip sotto [name]. */
        fun addFile(zip: ZipOutputStream, file: File, name: String) {
            if (!file.exists()) return
            if (file.isDirectory) { file.listFiles().orEmpty().forEach { addFile(zip, it, "$name/${it.name}") }; return }
            zip.putNextEntry(ZipEntry(name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }

        /** Copia la voce corrente di [zip] in [target], passando da un file accanto e rinominandolo alla fine. */
        fun writeEntry(zip: ZipInputStream, target: File) {
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".ripristino")
            tmp.outputStream().use { zip.copyTo(it) }
            target.delete()
            check(tmp.renameTo(target)) { "non si scrive ${target.name}" }
        }

        /** La copia di un database SQLite coerente (checkpoint del registro) da mettere nello zip. */
        fun addDatabase(context: Context, zip: ZipOutputStream, name: String) {
            val db = context.getDatabasePath(name)
            if (!db.exists()) return
            runCatching {
                android.database.sqlite.SQLiteDatabase.openDatabase(db.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { d ->
                    d.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                }
            }
            addFile(zip, db, "databases/$name")
        }

        /** Rimette un database: toglie il registro e le copie di prima, poi lo scrive. */
        fun restoreDatabase(context: Context, zip: ZipInputStream, name: String) {
            val db = context.getDatabasePath(name)
            listOf("-wal", "-shm", "-journal").forEach { File(db.path + it).delete() }
            writeEntry(zip, db)
        }

        /** Le preferenze [names] (file xml) nello zip, sotto `prefs/`. */
        fun addPrefs(context: Context, zip: ZipOutputStream, vararg names: String) {
            val dir = File(context.applicationInfo.dataDir, "shared_prefs")
            names.forEach { n -> addFile(zip, File(dir, "$n.xml"), "prefs/$n.xml") }
        }

        /** Solo alcune chiavi di [prefs] (le altre sono legate a questo telefono), con il loro tipo, per [restoreKeys]. */
        fun exportKeys(prefs: android.content.SharedPreferences, keys: Set<String>): ByteArray {
            val out = org.json.JSONObject()
            prefs.all.filterKeys { it in keys }.forEach { (k, v) ->
                val typed = when (v) {
                    is Boolean -> org.json.JSONObject().put("b", v)
                    is Int -> org.json.JSONObject().put("i", v)
                    is Long -> org.json.JSONObject().put("l", v)
                    is Float -> org.json.JSONObject().put("f", v.toDouble())
                    is String -> org.json.JSONObject().put("s", v)
                    is Set<*> -> org.json.JSONObject().put("ss", org.json.JSONArray(v.map { it.toString() }))
                    else -> null
                }
                typed?.let { out.put(k, it) }
            }
            return out.toString().toByteArray()
        }

        /** Rimette le chiavi scritte da [exportKeys], solo quelle in [keys]. */
        fun restoreKeys(prefs: android.content.SharedPreferences, json: String, keys: Set<String>) {
            val o = org.json.JSONObject(json)
            val edit = prefs.edit()
            o.keys().forEach { k ->
                if (k !in keys) return@forEach
                val t = o.getJSONObject(k)
                when {
                    t.has("b") -> edit.putBoolean(k, t.getBoolean("b"))
                    t.has("i") -> edit.putInt(k, t.getInt("i"))
                    t.has("l") -> edit.putLong(k, t.getLong("l"))
                    t.has("f") -> edit.putFloat(k, t.getDouble("f").toFloat())
                    t.has("s") -> edit.putString(k, t.getString("s"))
                    t.has("ss") -> edit.putStringSet(k, t.getJSONArray("ss").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
                }
            }
            edit.commit()
        }

        /** Una voce `prefs/<nome>.xml` torna al suo posto, se [allowed] la conosce. */
        fun restorePrefs(context: Context, zip: ZipInputStream, name: String, allowed: Set<String>): Boolean {
            val file = name.removePrefix("prefs/")
            if (!name.startsWith("prefs/") || file.removeSuffix(".xml") !in allowed) return false
            writeEntry(zip, File(File(context.applicationInfo.dataDir, "shared_prefs"), file))
            return true
        }
    }
}

package eu.eurise.gallery.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.provider.MediaStore
import eu.eurise.core.suite.SuiteBackupProvider
import eu.eurise.gallery.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Galleria nel backup della suite (area Backup, 21 novembre 2026; deciso dal proprietario il 20 novembre):
 * - la **cartella protetta** sempre, così com'è (file già cifrati con la sua chiave, più l'intestazione chiusa con la
 *   sua frase e il suo codice). Resta fuori solo l'impronta (Keystore di questo telefono: si riattiva);
 * - i **diari di viaggio** con le note; ogni foto del diario porta con sé nome e data, per ritrovarla sul telefono
 *   nuovo, dove gli indirizzi di MediaStore cambiano;
 * - i nomi dei luoghi già letti e le impostazioni che non dipendono dal telefono.
 * Le altre foto e i video restano fuori: hanno la loro sincronizzazione. Fuori anche lo stato dei caricamenti.
 *
 * Al ripristino una cartella protetta che c'è già e ha foto non si tocca: se è la stessa (stessa intestazione) si
 * aggiungono le foto che mancano, se è un'altra quella del backup va accanto, in `secure-dal-backup`.
 */
class GalleryBackupProvider : SuiteBackupProvider() {
    override fun summary(context: Context): String? {
        val secure = (listOf(secureDir(context)) + backupDirs(context)).sumOf { d -> d.listFiles().orEmpty().count { it.name.endsWith(".enc") } }
        val diaries = runCatching { db(context).let { d -> d.rawQuery("SELECT COUNT(*) FROM diaries", null).use { it.moveToFirst(); it.getInt(0) } } }.getOrDefault(0)
        if (secure == 0 && diaries == 0) return null
        return context.getString(R.string.backup_summary_gallery, secure, diaries)
    }

    override fun export(context: Context, zip: ZipOutputStream) {
        secureDir(context).listFiles().orEmpty().filter { it.isFile && it.name != "bio.json" }.forEach { addFile(zip, it, "secure/${it.name}") }
        // Le cartelle arrivate da un backup di prima e tenute a parte: anche loro, così non si perdono al prossimo cambio.
        backupDirs(context).forEach { dir ->
            dir.listFiles().orEmpty().filter { it.isFile && it.name != "bio.json" && !it.name.endsWith(".arrivo") }.forEach { addFile(zip, it, "$EXTRA${dir.name}/${it.name}") }
        }
        runCatching { db(context) }.getOrNull()?.let { d ->
            val diaries = JSONArray()
            d.rawQuery("SELECT id, title, created_at FROM diaries", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val items = JSONArray()
                    d.rawQuery("SELECT uri FROM diary_items WHERE diary_id = ?", arrayOf(id.toString())).use { i ->
                        while (i.moveToNext()) items.put(describe(context, i.getString(0)))
                    }
                    val notes = JSONObject()
                    d.rawQuery("SELECT day, text FROM diary_notes WHERE diary_id = ?", arrayOf(id.toString())).use { n -> while (n.moveToNext()) notes.put(n.getString(0), n.getString(1)) }
                    diaries.put(JSONObject().put("title", c.getString(1)).put("created_at", c.getLong(2)).put("items", items).put("notes", notes))
                }
            }
            val names = JSONObject()
            d.rawQuery("SELECT cell, name FROM place_names", null).use { c -> while (c.moveToNext()) names.put(c.getString(0), c.getString(1)) }
            zip.putNextEntry(ZipEntry(DIARIES))
            zip.write(JSONObject().put("diaries", diaries).put("place_names", names).toString().toByteArray())
            zip.closeEntry()
        }
        zip.putNextEntry(ZipEntry(SETTINGS))
        zip.write(exportKeys(context.getSharedPreferences("gallery", Context.MODE_PRIVATE), KEYS))
        zip.closeEntry()
    }

    override fun restore(context: Context, zip: ZipInputStream) {
        val secure = secureDir(context)
        val hadPhotos = secure.listFiles().orEmpty().any { it.name.endsWith(".enc") }
        val staged = File(context.filesDir, "secure-ripristino").apply { deleteRecursively(); mkdirs() }
        val stagedExtra = File(context.filesDir, "secure-ripristino-altre").apply { deleteRecursively(); mkdirs() }
        while (true) {
            val e = zip.nextEntry ?: break
            val name = safeName(e) ?: continue
            when {
                name.startsWith("secure/") -> {
                    val file = name.removePrefix("secure/").takeIf { it.isNotEmpty() && '/' !in it } ?: continue
                    writeEntry(zip, File(staged, file))
                }
                name.startsWith(EXTRA) -> {
                    val parts = name.removePrefix(EXTRA).split('/')
                    if (parts.size != 2 || parts.any { it.isEmpty() } || !parts[0].startsWith(eu.eurise.gallery.data.SecureFolder.FROM_BACKUP)) continue
                    writeEntry(zip, File(File(stagedExtra, parts[0]), parts[1]))
                }
                name == DIARIES -> restoreDiaries(context, JSONObject(zip.bufferedReader().readText()))
                name == SETTINGS -> restoreKeys(context.getSharedPreferences("gallery", Context.MODE_PRIVATE), zip.bufferedReader().readText(), KEYS)
            }
        }
        val stagedHeader = File(staged, "header.json")
        if (stagedHeader.exists()) {
            val sameFolder = File(secure, "header.json").let { it.exists() && it.readText() == stagedHeader.readText() }
            val target = when {
                !hadPhotos -> secure
                sameFolder -> secure
                else -> backupFolder(context, stagedHeader.readText())
            }
            // Nella stessa cartella si aggiunge solo quello che manca; l'intestazione di una cartella vuota si prende.
            moveFiles(staged, target, replace = target == secure && !hadPhotos)
        }
        staged.deleteRecursively()
        // Le altre cartelle del backup: nella tua se è la stessa, altrimenti a parte, ognuna nella sua.
        stagedExtra.listFiles().orEmpty().filter { it.isDirectory }.forEach { dir ->
            val header = File(dir, "header.json").takeIf { it.exists() }?.readText() ?: return@forEach
            val mine = File(secure, "header.json").takeIf { it.exists() }?.readText()
            moveFiles(dir, if (header == mine) secure else backupFolder(context, header), replace = false)
        }
        stagedExtra.deleteRecursively()
    }

    /** Sposta i file di [from] in [to]; quelli che ci sono già si tengono, a meno di [replace]. */
    private fun moveFiles(from: File, to: File, replace: Boolean) {
        to.mkdirs()
        from.listFiles().orEmpty().forEach { f ->
            val dest = File(to, f.name)
            if (dest.exists() && !replace) f.delete() else { dest.delete(); f.renameTo(dest) }
        }
    }

    /** Le cartelle protette arrivate da un backup e tenute a parte. */
    private fun backupDirs(context: Context): List<File> {
        val base = eu.eurise.gallery.data.SecureFolder.FROM_BACKUP
        return context.filesDir.listFiles { f -> f.isDirectory && (f.name == base || f.name.startsWith("$base-")) }.orEmpty()
            .filter { File(it, "header.json").exists() }
    }

    /**
     * Dove va una cartella protetta del backup diversa dalla tua: quella già arrivata con la stessa intestazione (si
     * aggiunge ciò che manca), altrimenti la prima `secure-dal-backup`, `secure-dal-backup-2`… vuota. Mai mescolate.
     */
    private fun backupFolder(context: Context, header: String): File {
        val base = eu.eurise.gallery.data.SecureFolder.FROM_BACKUP
        val names = generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }
        for (name in names) {
            val dir = File(context.filesDir, name)
            val h = File(dir, "header.json")
            if (h.exists() && h.readText() == header) return dir
            if (dir.listFiles { f -> f.name.endsWith(".meta") || f.name.endsWith(".enc") }.isNullOrEmpty()) {
                dir.deleteRecursively()
                return dir
            }
        }
        error("irraggiungibile")
    }

    /** Nome, data e peso di una foto di MediaStore, per ritrovarla su un altro telefono. */
    private fun describe(context: Context, uri: String): JSONObject {
        val o = JSONObject().put("uri", uri)
        runCatching {
            context.contentResolver.query(android.net.Uri.parse(uri), arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) o.put("name", c.getString(0)).put("taken", c.getLong(1)).put("size", c.getLong(2))
            }
        }
        return o
    }

    /** La stessa foto su questo telefono: per nome e data (o peso); altrimenti l'indirizzo di prima. */
    private fun find(context: Context, item: JSONObject): String {
        val name = item.optString("name").takeIf { it.isNotEmpty() } ?: return item.optString("uri")
        val taken = item.optLong("taken")
        val size = item.optLong("size")
        for (base in listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)) {
            runCatching {
                context.contentResolver.query(base, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.SIZE),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(name), null)?.use { c ->
                    while (c.moveToNext()) {
                        if ((taken != 0L && c.getLong(1) == taken) || (size != 0L && c.getLong(2) == size)) return ContentUris.withAppendedId(base, c.getLong(0)).toString()
                    }
                }
            }
        }
        return item.optString("uri")
    }

    private fun restoreDiaries(context: Context, json: JSONObject) {
        db(context).let { d ->
            d.beginTransaction()
            try {
                val names = json.optJSONObject("place_names")
                names?.keys()?.forEach { cell -> d.insertWithOnConflict("place_names", null, ContentValues().apply { put("cell", cell); put("name", names.getString(cell)) }, SQLiteDatabase.CONFLICT_IGNORE) }
                val diaries = json.optJSONArray("diaries") ?: JSONArray()
                for (i in 0 until diaries.length()) {
                    val o = diaries.getJSONObject(i)
                    val title = o.getString("title")
                    val created = o.getLong("created_at")
                    // Lo stesso diario (titolo e data) non si doppia.
                    val exists = d.rawQuery("SELECT 1 FROM diaries WHERE title = ? AND created_at = ?", arrayOf(title, created.toString())).use { it.moveToFirst() }
                    if (exists) continue
                    val id = d.insert("diaries", null, ContentValues().apply { put("title", title); put("created_at", created) })
                    val items = o.optJSONArray("items") ?: JSONArray()
                    for (j in 0 until items.length()) {
                        d.insertWithOnConflict("diary_items", null, ContentValues().apply { put("diary_id", id); put("uri", find(context, items.getJSONObject(j))) }, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                    val notes = o.optJSONObject("notes")
                    notes?.keys()?.forEach { day -> d.insertWithOnConflict("diary_notes", null, ContentValues().apply { put("diary_id", id); put("day", day); put("text", notes.getString(day)) }, SQLiteDatabase.CONFLICT_IGNORE) }
                }
                d.setTransactionSuccessful()
            } finally {
                d.endTransaction()
            }
        }
    }

    /** Il database della Galleria, quello della Galleria stessa (lo crea se manca; non va chiuso). */
    private fun db(context: Context): SQLiteDatabase = eu.eurise.gallery.data.GalleryStore.get(context).database()

    companion object {
        private const val DIARIES = "diaries.json"
        private const val SETTINGS = "settings.json"
        /** `altre-protette/<cartella>/<file>`: le cartelle dal backup tenute a parte. */
        private const val EXTRA = "altre-protette/"
        /** Le impostazioni che valgono anche su un altro telefono (fuori l'account, lo stato e gli errori del caricamento). */
        private val KEYS = setOf("charging", "wifi", "my_looks", "names", "save_options", "places", "kept_originals")
        fun secureDir(context: Context) = File(context.filesDir, "secure")
    }
}

package eu.eurise.gallery.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import eu.eurise.gallery.logic.UploadRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** Scelte della persona. Di base non esce nulla e non si legge nessuna posizione. */
data class GallerySettings(
    val uploadOn: Boolean = false,
    val accountId: String? = null,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val fromMillis: Long = 0,
    val buckets: Set<String> = setOf("Camera"),
    val placesOn: Boolean = false,
    val namesOn: Boolean = false,
)

data class Diary(val id: Long, val title: String, val createdAt: Long, val uris: List<String>, val notes: Map<String, String>)

/**
 * Ciò che la Galleria ricorda oltre a MediaStore: foto caricate, posizioni lette, nomi dei luoghi, diari.
 * SQLite del sistema, senza librerie. Le funzioni vanno chiamate fuori dal thread principale.
 */
class GalleryStore private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("gallery", Context.MODE_PRIVATE)
    private val helper = object : SQLiteOpenHelper(app, "gallery.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE uploads(id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT NOT NULL UNIQUE, remote TEXT NOT NULL, taken_at INTEGER NOT NULL, name TEXT NOT NULL, size INTEGER NOT NULL, is_video INTEGER NOT NULL, duration INTEGER NOT NULL, bucket TEXT NOT NULL, state INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX uploads_state ON uploads(state)")
            db.execSQL("CREATE TABLE places(uri TEXT PRIMARY KEY, cell TEXT)")
            db.execSQL("CREATE TABLE place_names(cell TEXT PRIMARY KEY, name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE diaries(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE diary_items(diary_id INTEGER NOT NULL, uri TEXT NOT NULL, PRIMARY KEY(diary_id, uri))")
            db.execSQL("CREATE TABLE diary_notes(diary_id INTEGER NOT NULL, day TEXT NOT NULL, text TEXT NOT NULL, PRIMARY KEY(diary_id, day))")
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    }

    private val _version = MutableStateFlow(0)

    /** Cresce a ogni modifica: chi mostra i dati li rilegge. */
    val version: StateFlow<Int> = _version
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<GallerySettings> = _settings

    private fun changed() = _version.update { it + 1 }

    private fun readSettings() = GallerySettings(
        uploadOn = prefs.getBoolean("upload", false),
        accountId = prefs.getString("account", null),
        wifiOnly = prefs.getBoolean("wifi", true),
        chargingOnly = prefs.getBoolean("charging", false),
        fromMillis = prefs.getLong("from", System.currentTimeMillis()),
        buckets = prefs.getStringSet("buckets", null)?.toSet() ?: setOf("Camera"),
        placesOn = prefs.getBoolean("places", false),
        namesOn = prefs.getBoolean("names", false),
    )

    fun update(change: (GallerySettings) -> GallerySettings) {
        val s = change(_settings.value)
        prefs.edit()
            .putBoolean("upload", s.uploadOn).putString("account", s.accountId).putBoolean("wifi", s.wifiOnly)
            .putBoolean("charging", s.chargingOnly).putLong("from", s.fromMillis).putStringSet("buckets", s.buckets)
            .putBoolean("places", s.placesOn).putBoolean("names", s.namesOn)
            .apply()
        _settings.value = s
    }

    var lastError: String?
        get() = prefs.getString("error", null)
        set(value) { prefs.edit().putString("error", value).apply(); changed() }

    // Caricamenti

    fun records(): List<UploadRecord> {
        val out = ArrayList<UploadRecord>()
        helper.readableDatabase.rawQuery("SELECT id, uri, remote, taken_at, name, size, is_video, duration, bucket, state FROM uploads", null).use { c ->
            while (c.moveToNext()) {
                out += UploadRecord(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4), c.getLong(5), c.getInt(6) == 1, c.getLong(7), c.getString(8), c.getInt(9))
            }
        }
        return out
    }

    /** Registra una foto caricata e ne restituisce il numero, che dà il nome alla sua miniatura. */
    fun addRecord(uri: String, remote: String, takenAt: Long, name: String, size: Long, isVideo: Boolean, durationMs: Long, bucket: String): Long {
        val id = helper.writableDatabase.insertWithOnConflict("uploads", null, ContentValues().apply {
            put("uri", uri); put("remote", remote); put("taken_at", takenAt); put("name", name); put("size", size)
            put("is_video", if (isVideo) 1 else 0); put("duration", durationMs); put("bucket", bucket); put("state", UploadRecord.UPLOADED)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        changed()
        return id
    }

    fun setState(uris: Collection<String>, state: Int) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            uris.forEach { db.update("uploads", ContentValues().apply { put("state", state) }, "uri = ?", arrayOf(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    fun thumbFile(recordId: Long) = File(File(app.filesDir, "cloud-thumbs").apply { mkdirs() }, "$recordId.jpg")

    // Luoghi

    /** Quale casella ha ogni foto letta; null se la foto non ha una posizione. */
    fun places(): Map<String, String?> {
        val out = HashMap<String, String?>()
        helper.readableDatabase.rawQuery("SELECT uri, cell FROM places", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = if (c.isNull(1)) null else c.getString(1)
        }
        return out
    }

    fun addPlaces(batch: Map<String, String?>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            batch.forEach { (uri, cell) ->
                db.insertWithOnConflict("places", null, ContentValues().apply { put("uri", uri); put("cell", cell) }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    /** Spegnere Luoghi cancella quanto è stato letto. */
    fun clearPlaces() {
        helper.writableDatabase.apply { delete("places", null, null); delete("place_names", null, null) }
        changed()
    }

    fun clearPlaceNames() {
        helper.writableDatabase.delete("place_names", null, null)
        changed()
    }

    fun placeNames(): Map<String, String> {
        val out = HashMap<String, String>()
        helper.readableDatabase.rawQuery("SELECT cell, name FROM place_names", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        }
        return out
    }

    fun addPlaceName(cell: String, name: String) {
        helper.writableDatabase.insertWithOnConflict("place_names", null, ContentValues().apply { put("cell", cell); put("name", name) }, SQLiteDatabase.CONFLICT_REPLACE)
        changed()
    }

    // Diari

    fun diaries(): List<Diary> {
        val db = helper.readableDatabase
        val items = HashMap<Long, MutableList<String>>()
        db.rawQuery("SELECT diary_id, uri FROM diary_items", null).use { c -> while (c.moveToNext()) items.getOrPut(c.getLong(0)) { ArrayList() } += c.getString(1) }
        val notes = HashMap<Long, MutableMap<String, String>>()
        db.rawQuery("SELECT diary_id, day, text FROM diary_notes", null).use { c -> while (c.moveToNext()) notes.getOrPut(c.getLong(0)) { HashMap() }[c.getString(1)] = c.getString(2) }
        val out = ArrayList<Diary>()
        db.rawQuery("SELECT id, title, created_at FROM diaries ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) { val id = c.getLong(0); out += Diary(id, c.getString(1), c.getLong(2), items[id].orEmpty(), notes[id].orEmpty()) }
        }
        return out
    }

    fun createDiary(title: String, uris: List<String>): Long {
        val id = helper.writableDatabase.insert("diaries", null, ContentValues().apply { put("title", title); put("created_at", System.currentTimeMillis()) })
        addToDiary(id, uris)
        return id
    }

    fun addToDiary(id: Long, uris: List<String>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            uris.forEach { db.insertWithOnConflict("diary_items", null, ContentValues().apply { put("diary_id", id); put("uri", it) }, SQLiteDatabase.CONFLICT_IGNORE) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    fun removeFromDiary(id: Long, uri: String) {
        helper.writableDatabase.delete("diary_items", "diary_id = ? AND uri = ?", arrayOf(id.toString(), uri))
        changed()
    }

    fun renameDiary(id: Long, title: String) {
        helper.writableDatabase.update("diaries", ContentValues().apply { put("title", title) }, "id = ?", arrayOf(id.toString()))
        changed()
    }

    fun setDiaryNote(id: Long, day: String, text: String) {
        val db = helper.writableDatabase
        if (text.isBlank()) db.delete("diary_notes", "diary_id = ? AND day = ?", arrayOf(id.toString(), day))
        else db.insertWithOnConflict("diary_notes", null, ContentValues().apply { put("diary_id", id); put("day", day); put("text", text) }, SQLiteDatabase.CONFLICT_REPLACE)
        changed()
    }

    fun deleteDiary(id: Long) {
        val args = arrayOf(id.toString())
        helper.writableDatabase.apply {
            delete("diary_items", "diary_id = ?", args); delete("diary_notes", "diary_id = ?", args); delete("diaries", "id = ?", args)
        }
        changed()
    }

    companion object {
        @Volatile private var instance: GalleryStore? = null
        fun get(context: Context): GalleryStore = instance ?: synchronized(this) { instance ?: GalleryStore(context).also { instance = it } }
    }
}

package eu.eurise.phone.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Le note scritte durante le chiamate, anche sul telefono (oltre che in Note): servono al filo di una persona nella
 * Chat («Una persona, tutti i canali»). Solo le ultime [MAX], in un file privato dell'app.
 */
object CallNotes {
    private const val MAX = 300
    private const val KEY = "notes"

    data class Note(val number: String, val time: Long, val text: String)

    private fun prefs(context: Context) = context.getSharedPreferences("call_notes", Context.MODE_PRIVATE)

    fun all(context: Context): List<Note> = runCatching {
        val a = JSONArray(prefs(context).getString(KEY, "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Note(it.getString("n"), it.getLong("t"), it.getString("x")) } }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(context: Context, number: String, time: Long, text: String) {
        if (number.isBlank() || text.isBlank()) return
        val list = (all(context) + Note(number, time, text.trim())).takeLast(MAX)
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("n", it.number).put("t", it.time).put("x", it.text)) }
        prefs(context).edit().putString(KEY, a.toString()).apply()
    }

    /**
     * La nota di una chiamata: stesso numero, scritta tra l'inizio della chiamata e dieci minuti dopo la fine (la nota si
     * salva quando la chiamata si chiude).
     */
    fun forCall(notes: List<Note>, number: String, start: Long, durationSeconds: Long): Note? =
        notes.firstOrNull { PhoneData.same(it.number, number) && it.time >= start && it.time <= start + durationSeconds * 1000 + 600_000L }
}

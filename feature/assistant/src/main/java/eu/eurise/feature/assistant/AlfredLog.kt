package eu.eurise.feature.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Il registro delle richieste (tavola AlfAvvio, passo 5): le ultime 100 frasi, cosa ha fatto Alfred e se si è annullato
 * o corretto. Spento di base; resta sul telefono e fuori dal backup (file «alfred_log», non «eurise_…»). Si manda solo
 * con «Mandalo a chi sviluppa…», con l'app che si sceglie: serve a capire se Alfred sbaglia ad ascoltare o a capire.
 */
object AlfredLog {
    enum class Result { Done, Corrected, Cancelled, Failed }

    data class Entry(val at: Long, val said: String, val result: Result, val detail: String)

    const val MAX = 100
    private const val PREFS = "alfred_log"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("on", false)

    /** Spegnerlo cancella anche ciò che c'era: nessun registro dimenticato acceso. */
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply { if (!on) remove("entries") }.apply()
    }

    fun add(context: Context, said: String, result: Result, detail: String, now: Long = System.currentTimeMillis()) {
        if (!enabled(context) || said.isBlank()) return
        val list = (listOf(Entry(now, said.trim().take(200), result, detail.trim().take(160))) + all(context)).take(MAX)
        prefs(context).edit().putString("entries", encode(list)).apply()
    }

    fun all(context: Context): List<Entry> = decode(prefs(context).getString("entries", null))

    fun clear(context: Context) = prefs(context).edit().remove("entries").apply()

    /** Il testo da mandare a chi sviluppa: una riga per richiesta, la più recente in alto. */
    fun text(entries: List<Entry>): String = entries.joinToString("\n") { e ->
        val mark = when (e.result) {
            Result.Done -> "fatta"
            Result.Corrected -> "corretta"
            Result.Cancelled -> "annullata"
            Result.Failed -> "non riuscita"
        }
        "«${e.said}» → $mark · ${e.detail}"
    }

    internal fun encode(list: List<Entry>): String = JSONArray(list.map { e ->
        JSONObject().put("t", e.at).put("s", e.said).put("r", e.result.name).put("d", e.detail)
    }).toString()

    internal fun decode(raw: String?): List<Entry> = runCatching {
        val a = JSONArray(raw ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Entry(o.optLong("t"), o.optString("s"), Result.entries.firstOrNull { it.name == o.optString("r") } ?: Result.Done, o.optString("d"))
        }
    }.getOrDefault(emptyList())
}

package eu.eurise.office.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Un documento aperto di recente: dove sta, come si chiama, quando l'hai toccato l'ultima volta. */
data class Recent(
    val uri: Uri,
    val name: String,
    val where: String,
    val kind: DocKind,
    val openedAt: Long,
    val writable: Boolean,
)

/**
 * I recenti di Office, sul telefono (preferenze private dell'app, mai fuori). Un file che non si riesce più ad aprire
 * (permesso scaduto, cancellato) resta finché l'utente non lo tocca: allora sparisce con un avviso.
 */
class Recents private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("recenti", Context.MODE_PRIVATE)
    private val state = MutableStateFlow<List<Recent>>(emptyList())
    val items: StateFlow<List<Recent>> = state.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        state.value = parse(prefs.getString(KEY, "[]").orEmpty())
    }

    suspend fun add(uri: Uri, name: String, where: String, kind: DocKind, writable: Boolean) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val list = listOf(Recent(uri, name, where, kind, now, writable)) + state.value.filter { it.uri != uri }
        save(list.take(MAX))
    }

    suspend fun remove(uri: Uri) = withContext(Dispatchers.IO) { save(state.value.filter { it.uri != uri }) }

    private fun save(list: List<Recent>) {
        state.value = list
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("uri", it.uri.toString()).put("name", it.name).put("where", it.where)
                    .put("kind", it.kind.name).put("at", it.openedAt).put("w", it.writable),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "elenco"
        private const val MAX = 40

        @Volatile private var instance: Recents? = null
        fun get(context: Context): Recents =
            instance ?: synchronized(this) { instance ?: Recents(context.applicationContext).also { instance = it } }

        fun parse(json: String): List<Recent> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Recent(
                    Uri.parse(o.getString("uri")), o.getString("name"), o.optString("where"),
                    runCatching { DocKind.valueOf(o.optString("kind")) }.getOrDefault(DocKind.Other),
                    o.optLong("at"), o.optBoolean("w"),
                )
            }
        }.getOrDefault(emptyList())
    }
}

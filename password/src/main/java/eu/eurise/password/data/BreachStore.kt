package eu.eurise.password.data

import android.content.Context
import eu.eurise.core.cloud.WebDav
import eu.eurise.password.kdbx.VaultEntry
import eu.eurise.password.logic.Breach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Il controllo delle password violate, spento di base. Sul telefono resta solo quali voci sono risultate violate, con
 * la data dell'ultima modifica: cambiando la password l'avviso sparisce da solo.
 */
class BreachStore(context: Context) {
    private val prefs = context.getSharedPreferences("password_breach", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ON, false)
        set(value) {
            if (value) prefs.edit().putBoolean(KEY_ON, true).apply() else prefs.edit().clear().apply()
        }

    val lastCheck: Long get() = prefs.getLong(KEY_LAST, 0)

    val due: Boolean get() = enabled && System.currentTimeMillis() - lastCheck > WEEK_MS

    fun results(): Map<String, Long> = prefs.all.filterKeys { it.startsWith(PREFIX) }
        .mapNotNull { (k, v) -> (v as? Long)?.let { k.removePrefix(PREFIX) to it } }.toMap()

    /** Vero se il controllo è arrivato in fondo. Senza rete i risultati precedenti restano. */
    suspend fun check(entries: List<VaultEntry>): Boolean = withContext(Dispatchers.IO) {
        val responses = HashMap<String, String>()
        val found = ArrayList<VaultEntry>()
        try {
            for (entry in entries.filter { it.password.isNotEmpty() }) {
                val hash = Breach.sha1(entry.password)
                val prefix = hash.take(5)
                val body = responses.getOrPut(prefix) { fetch(prefix) ?: return@withContext false }
                if (Breach.found(hash, body)) found += entry
            }
        } catch (e: java.io.IOException) {
            return@withContext false
        }
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith(PREFIX) }.forEach { edit.remove(it) }
        found.forEach { edit.putLong(PREFIX + it.uuid, it.modified) }
        edit.putLong(KEY_LAST, System.currentTimeMillis()).apply()
        true
    }

    private fun fetch(prefix: String): String? {
        val request = Request.Builder().url("https://api.pwnedpasswords.com/range/$prefix")
            .header("Add-Padding", "true").header("User-Agent", WebDav.USER_AGENT).build()
        return WebDav.sharedClient.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }

    private companion object {
        const val KEY_ON = "on"
        const val KEY_LAST = "last"
        const val PREFIX = "v_"
        const val WEEK_MS = 7 * 24 * 3600_000L
    }
}

package eu.eurise.password.data

import android.content.Context
import eu.eurise.core.cloud.Secrets
import eu.eurise.core.cloud.WebDav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class AliasProvider { None, SimpleLogin, Addy, Fixed }

enum class AliasCheck { Ok, Rejected, Offline }

/**
 * Da dove arrivano gli indirizzi per le iscrizioni. Il servizio si sceglie nelle impostazioni e la sua chiave resta
 * cifrata sul telefono; all'esterno va solo il nome del sito per cui si crea l'alias.
 */
class AliasStore(context: Context) {
    private val prefs = context.getSharedPreferences("password_alias", Context.MODE_PRIVATE)
    private val client = WebDav.sharedClient
    private val json = "application/json".toMediaType()

    var provider: AliasProvider
        get() = runCatching { AliasProvider.valueOf(prefs.getString(KEY_PROVIDER, "") ?: "") }.getOrDefault(AliasProvider.None)
        set(value) { prefs.edit().putString(KEY_PROVIDER, value.name).apply() }

    fun connected(which: AliasProvider): Boolean = prefs.contains(which.name)

    /** L'indirizzo fisso, se l'utente ne ha indicato uno. */
    val fixedAddress: String? get() = secret(AliasProvider.Fixed)

    /** Il servizio crea alias nuovi ed è collegato. */
    val generates: Boolean get() = (provider == AliasProvider.SimpleLogin || provider == AliasProvider.Addy) && connected(provider)

    private fun secret(which: AliasProvider): String? = prefs.getString(which.name, null)?.let { Secrets.decrypt(it) }

    private fun store(which: AliasProvider, value: String) {
        prefs.edit().putString(which.name, Secrets.encrypt(value)).apply()
    }

    /** Solo una risposta 401 o 403 fa scartare la chiave: un servizio irraggiungibile non dice nulla su di lei. */
    suspend fun connect(which: AliasProvider, value: String): AliasCheck {
        val clean = value.trim()
        if (which == AliasProvider.Fixed) {
            store(which, clean)
            return AliasCheck.Ok
        }
        val request = when (which) {
            AliasProvider.SimpleLogin -> simpleLogin("/api/user_info", clean)
            AliasProvider.Addy -> addy("/api/v1/api-token-details", clean)
            else -> return AliasCheck.Rejected
        }.get().build()
        val code = withContext(Dispatchers.IO) {
            runCatching { client.newCall(request).execute().use { it.code } }.getOrNull()
        } ?: return AliasCheck.Offline
        return when {
            code == 401 || code == 403 -> AliasCheck.Rejected
            code in 200..299 -> { store(which, clean); AliasCheck.Ok }
            else -> AliasCheck.Offline
        }
    }

    /** Crea un alias per [hostname] (dominio del sito o nome del pacchetto dell'app); null se non riesce. */
    suspend fun create(hostname: String): String? = withContext(Dispatchers.IO) {
        val key = secret(provider) ?: return@withContext null
        val request = when (provider) {
            AliasProvider.SimpleLogin -> simpleLogin("/api/alias/random/new?hostname=" + android.net.Uri.encode(hostname), key)
                .post(JSONObject().put("note", "Eurise: $hostname").toString().toRequestBody(json))
            AliasProvider.Addy -> addy("/api/v1/aliases", key)
                .post(JSONObject().put("domain", "anonaddy.me").put("format", "random_characters").put("description", hostname).toString().toRequestBody(json))
            else -> return@withContext null
        }.build()
        runCatching {
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = JSONObject(res.body?.string().orEmpty())
                if (provider == AliasProvider.Addy) body.getJSONObject("data").getString("email") else body.getString("alias")
            }
        }.getOrNull()
    }

    private fun simpleLogin(path: String, key: String) =
        Request.Builder().url("https://app.simplelogin.io$path").header("Authentication", key).header("User-Agent", WebDav.USER_AGENT)

    private fun addy(path: String, key: String) =
        Request.Builder().url("https://app.addy.io$path").header("Authorization", "Bearer $key")
            .header("X-Requested-With", "XMLHttpRequest").header("Accept", "application/json").header("User-Agent", WebDav.USER_AGENT)

    private companion object {
        const val KEY_PROVIDER = "provider"
    }
}

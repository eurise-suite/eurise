package eu.eurise.core.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Accesso a Nextcloud (e Murena) dal browser, il "Login Flow v2" di Nextcloud: l'app chiede al server una
 * pagina di accesso, la persona la apre nel browser, entra come sempre (anche con la verifica in due passaggi)
 * e approva; intanto l'app aspetta e riceve una password per le app creata apposta. Nessuna password passa
 * dall'app, e la si può revocare dalle impostazioni di sicurezza del cloud.
 */
class NextcloudLogin(private val client: OkHttpClient = WebDav.sharedClient) {

    /** Pagina da aprire nel browser e dati per chiedere l'esito. */
    data class Start(val loginUrl: String, val pollUrl: String, val token: String)

    /** Esito dell'accesso: indirizzo del server, nome utente e password per le app. */
    data class Result(val server: String, val loginName: String, val appPassword: String)

    /** Avvia l'accesso su [server] (per esempio "murena.io" o "https://cloud.esempio.it"). */
    suspend fun start(server: String): Start = withContext(Dispatchers.IO) {
        val url = normalizeServer(server) + "/index.php/login/v2"
        val req = Request.Builder().url(url).header("User-Agent", WebDav.USER_AGENT).post(ByteArray(0).toRequestBody()).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw DavException(res.code, "login/v2: ${res.code}")
            val json = JSONObject(res.body?.string().orEmpty())
            val poll = json.getJSONObject("poll")
            Start(json.getString("login"), poll.getString("endpoint"), poll.getString("token"))
        }
    }

    /**
     * Aspetta che la persona approvi nel browser, chiedendo l'esito ogni due secondi per al più
     * [timeoutMs]; null se il tempo scade.
     */
    suspend fun await(start: Start, timeoutMs: Long = 10 * 60_000L): Result? {
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            pollOnce(start)?.let { return it }
            delay(2_000)
        }
        return null
    }

    private suspend fun pollOnce(start: Start): Result? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(start.pollUrl).header("User-Agent", WebDav.USER_AGENT)
            .post(FormBody.Builder().add("token", start.token).build()).build()
        runCatching {
            client.newCall(req).execute().use { res ->
                if (res.code != 200) return@use null
                val json = JSONObject(res.body?.string().orEmpty())
                Result(json.getString("server"), json.getString("loginName"), json.getString("appPassword"))
            }
        }.getOrNull()
    }

    companion object {
        const val MURENA_SERVER = "https://murena.io"

        /** "cloud.esempio.it/" diventa "https://cloud.esempio.it". */
        fun normalizeServer(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }
}

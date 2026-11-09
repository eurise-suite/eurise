package eu.eurise.core.cloud

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Accesso a kDrive dal browser (OAuth2 con PKCE): nessuna chiave segreta nell'APK e nessun token da cercare per chi usa
 * l'app. L'identificativo dell'applicazione è pubblico per natura. Per ora serve solo a verificare che Infomaniak
 * conceda l'accesso ai file a un'app di terzi: il token ottenuto non viene salvato.
 */
object KDriveLogin {
    private const val CLIENT_ID = "a9bba3c7-42e3-4fae-89ad-0efc0111426b"
    private const val REDIRECT = "eurise://kdrive"
    private const val AUTHORIZE = "https://login.infomaniak.com/authorize"
    private const val TOKEN = "https://login.infomaniak.com/token"
    private const val DRIVES = "https://api.infomaniak.com/2/drive/init?with=drives"

    /** Esito della verifica: i codici HTTP e al più il codice d'errore del server, mai il token. */
    data class Probe(val authError: String? = null, val tokenCode: Int? = null, val tokenError: String? = null, val driveCode: Int? = null, val drives: Int? = null, val granted: String? = null, val driveError: String? = null, val profileCode: Int? = null)

    private class Pending(val verifier: String, val state: String, val result: CompletableDeferred<String>)

    @Volatile private var pending: Pending? = null
    private val random = SecureRandom()

    private fun urlSafe(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** L'indirizzo da aprire nel browser. */
    fun begin(withScope: Boolean): String {
        val verifier = urlSafe(ByteArray(48).also(random::nextBytes))
        val state = urlSafe(ByteArray(16).also(random::nextBytes))
        pending = Pending(verifier, state, CompletableDeferred())
        val challenge = urlSafe(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        return Uri.parse(AUTHORIZE).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .apply { if (withScope) appendQueryParameter("scope", "drive") }
            .build().toString()
    }

    /** Chiamato dall'attività che riceve il ritorno dal browser. */
    fun complete(uri: Uri?) {
        pending?.result?.complete(uri?.toString().orEmpty())
    }

    /** Aspetta il ritorno dal browser, chiede il token e prova a leggere l'elenco dei kDrive. */
    suspend fun probe(): Probe {
        val current = pending ?: return Probe(authError = "nessuna richiesta")
        val returned = withTimeoutOrNull(5 * 60_000L) { current.result.await() } ?: return Probe(authError = "tempo scaduto")
        val uri = Uri.parse(returned)
        uri.getQueryParameter("error")?.let { return Probe(authError = it) }
        if (uri.getQueryParameter("state") != current.state) return Probe(authError = "risposta non valida")
        val code = uri.getQueryParameter("code") ?: return Probe(authError = "codice assente")
        return withContext(Dispatchers.IO) {
            runCatching { exchange(code, current.verifier) }.getOrElse { Probe(authError = "rete non raggiungibile") }
        }
    }

    private fun exchange(code: String, verifier: String): Probe {
        val form = FormBody.Builder().add("grant_type", "authorization_code").add("client_id", CLIENT_ID)
            .add("code", code).add("code_verifier", verifier).add("redirect_uri", REDIRECT).build()
        val request = Request.Builder().url(TOKEN).header("User-Agent", WebDav.USER_AGENT).post(form).build()
        val (status, body) = WebDav.sharedClient.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        val json = runCatching { JSONObject(body) }.getOrNull()
        val token = json?.optString("access_token").orEmpty()
        if (token.isEmpty()) return Probe(tokenCode = status, tokenError = json?.optString("error")?.ifEmpty { null })
        val drives = Request.Builder().url(DRIVES).header("Authorization", "Bearer $token").header("User-Agent", WebDav.USER_AGENT).build()
        val granted = json?.optString("scope")?.ifEmpty { null }
        val profile = Request.Builder().url("https://api.infomaniak.com/1/profile").header("Authorization", "Bearer $token").header("User-Agent", WebDav.USER_AGENT).build()
        val profileCode = WebDav.sharedClient.newCall(profile).execute().use { it.code }
        return WebDav.sharedClient.newCall(drives).execute().use { res ->
            val answer = runCatching { JSONObject(res.body?.string().orEmpty()) }.getOrNull()
            val error = answer?.optJSONObject("error")?.let { e -> listOf(e.optString("code"), e.optString("description")).filter(String::isNotEmpty).joinToString(" · ") }
            Probe(tokenCode = status, driveCode = res.code, drives = answer?.optJSONObject("data")?.optJSONArray("drives")?.length(), granted = granted, driveError = error, profileCode = profileCode)
        }
    }
}

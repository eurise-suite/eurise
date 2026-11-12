package eu.eurise.chat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/** L'accesso a un server Matrix: dove sta, chi sei, il gettone d'accesso (cifrato sul telefono). */
data class Session(val homeserver: String, val userId: String, val deviceId: String, val token: String)

class MatrixException(val status: Int, val code: String, message: String) : Exception(message)

/**
 * L'API client-server di Matrix con HttpURLConnection e org.json: niente librerie nuove. Le chiamate sono bloccanti:
 * da usare fuori dal thread principale.
 */
class ChatClient(val session: Session) {

    private fun url(path: String) = session.homeserver + path

    fun get(path: String, timeoutMs: Int = 30_000): JSONObject = http("GET", url(path), null, session.token, timeoutMs)
    fun post(path: String, body: JSONObject): JSONObject = http("POST", url(path), body, session.token)
    fun put(path: String, body: JSONObject): JSONObject = http("PUT", url(path), body, session.token)

    /**
     * Una tornata di /sync. [since] null = la prima (con un filtro leggero: 20 messaggi per stanza, membri solo quelli
     * che servono). [timeoutMs] quanto il server può aspettare novità prima di rispondere (long polling).
     */
    fun sync(since: String?, timeoutMs: Int): JSONObject {
        val filter = """{"room":{"timeline":{"limit":20},"state":{"lazy_load_members":true}},"presence":{"not_types":["*"]},""" +
            """"account_data":{"types":["m.direct"]}}"""
        val q = buildString {
            append("/_matrix/client/v3/sync?timeout=").append(timeoutMs)
            append("&filter=").append(enc(filter))
            if (since != null) append("&since=").append(enc(since))
        }
        return get(q, timeoutMs + 20_000)
    }

    fun messages(roomId: String, from: String): JSONObject =
        get("/_matrix/client/v3/rooms/${enc(roomId)}/messages?dir=b&limit=30&from=${enc(from)}")

    fun sendText(roomId: String, text: String): String {
        val txn = UUID.randomUUID().toString()
        val body = JSONObject().put("msgtype", "m.text").put("body", text)
        return put("/_matrix/client/v3/rooms/${enc(roomId)}/send/m.room.message/$txn", body).optString("event_id")
    }

    fun read(roomId: String, eventId: String) {
        runCatching { post("/_matrix/client/v3/rooms/${enc(roomId)}/receipt/m.read/${enc(eventId)}", JSONObject()) }
    }

    /** Messaggio cifrato (contenuto già cifrato da [ChatCrypto]). */
    fun sendEncrypted(roomId: String, content: JSONObject): String =
        put("/_matrix/client/v3/rooms/${enc(roomId)}/send/m.room.encrypted/${UUID.randomUUID()}", content).optString("event_id")

    /** Chi è nella stanza (per condividere con loro la chiave della stanza). */
    fun joinedMembers(roomId: String): List<String> {
        val joined = get("/_matrix/client/v3/rooms/${enc(roomId)}/joined_members").optJSONObject("joined") ?: return emptyList()
        return joined.keys().asSequence().toList()
    }

    fun join(roomId: String) { post("/_matrix/client/v3/join/${enc(roomId)}", JSONObject()) }

    fun leave(roomId: String) { post("/_matrix/client/v3/rooms/${enc(roomId)}/leave", JSONObject()) }

    /** Una chat a due con [userId] (stanza privata, solo su invito). */
    fun createDirect(userId: String): String {
        val body = JSONObject().put("is_direct", true).put("preset", "trusted_private_chat").put("invite", JSONArray().put(userId))
            .put("initial_state", encryptedState())
        return post("/_matrix/client/v3/createRoom", body).getString("room_id")
    }

    /** Un gruppo con un nome e le persone invitate. */
    fun createGroup(name: String, users: List<String>): String {
        val body = JSONObject().put("name", name).put("preset", "private_chat").put("invite", JSONArray(users))
            .put("initial_state", encryptedState())
        return post("/_matrix/client/v3/createRoom", body).getString("room_id")
    }

    /** Il profilo (nome) di un utente: per controllare che esista prima di scrivergli. */
    fun profile(userId: String): JSONObject = get("/_matrix/client/v3/profile/${enc(userId)}")

    /** Un'immagine o un file della chat (media autenticati). */
    fun download(url: String, out: File): File {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer ${session.token}")
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        try {
            if (c.responseCode !in 200..299) throw MatrixException(c.responseCode, "M_UNKNOWN", "download")
            c.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        } finally { c.disconnect() }
        return out
    }

    /** Le chat nuove nascono cifrate end-to-end (Megolm, come Element). */
    private fun encryptedState() = JSONArray().put(
        JSONObject().put("type", "m.room.encryption").put("state_key", "")
            .put("content", JSONObject().put("algorithm", "m.megolm.v1.aes-sha2")),
    )

    fun logout() { runCatching { post("/_matrix/client/v3/logout", JSONObject()) } }

    companion object {
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /** Una richiesta al server; gli errori di Matrix ({errcode, error}) diventano [MatrixException]. */
        fun http(method: String, url: String, body: JSONObject?, token: String?, timeoutMs: Int = 30_000): JSONObject {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = timeoutMs
            c.setRequestProperty("Accept", "application/json")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            try {
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrDefault(JSONObject())
                if (code !in 200..299) throw MatrixException(code, json.optString("errcode", "M_UNKNOWN"), json.optString("error", "HTTP $code"))
                return json
            } finally { c.disconnect() }
        }

        /** Dove sta il server Matrix di un dominio («studio.it» → https://matrix.studio.it), dal suo /.well-known. */
        fun discover(domain: String): String {
            // «studio.it», «https://studio.it/» o un identificativo intero «@anna:studio.it»: serve solo il dominio.
            val raw = domain.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
            val d = if (raw.startsWith("@") && raw.contains(':')) raw.substringAfter(':') else raw
            val wellKnown = runCatching {
                val c = URL("https://$d/.well-known/matrix/client").openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 10_000
                try { if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null } finally { c.disconnect() }
            }.getOrNull()?.let(Matrix::wellKnown)
            return wellKnown ?: "https://$d"
        }

        /** I modi di accedere che offre il server. */
        fun flows(homeserver: String): List<String> =
            Matrix.loginFlows(http("GET", "$homeserver/_matrix/client/v3/login", null, null).toString())

        /** L'indirizzo dell'accesso dal sito del server (SSO): torna all'app con un gettone usa e getta. */
        fun ssoUrl(homeserver: String, redirect: String) =
            "$homeserver/_matrix/client/v3/login/sso/redirect?redirectUrl=" + enc(redirect)

        private fun loginBody(type: String) = JSONObject().put("type", type)
            .put("initial_device_display_name", "Eurise Chat (" + android.os.Build.MODEL + ")")

        fun loginWithToken(homeserver: String, loginToken: String): Session =
            session(homeserver, http("POST", "$homeserver/_matrix/client/v3/login", loginBody("m.login.token").put("token", loginToken), null))

        fun loginWithPassword(homeserver: String, user: String, password: String): Session {
            val id = JSONObject().put("type", "m.id.user").put("user", user)
            return session(homeserver, http("POST", "$homeserver/_matrix/client/v3/login", loginBody("m.login.password").put("identifier", id).put("password", password), null))
        }

        private fun session(homeserver: String, r: JSONObject) = Session(
            homeserver = r.optJSONObject("well_known")?.toString()?.let(Matrix::wellKnown) ?: homeserver,
            userId = r.getString("user_id"), deviceId = r.optString("device_id"), token = r.getString("access_token"),
        )
    }
}

/** La sessione sul telefono: il gettone d'accesso cifrato con una chiave del Keystore (non esce, non va nei backup). */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("chat_session", Context.MODE_PRIVATE)

    fun load(): Session? {
        val hs = prefs.getString("hs", null) ?: return null
        val sealed = prefs.getString("token", null) ?: return null
        val token = runCatching { open(sealed) }.getOrNull() ?: return null
        return Session(hs, prefs.getString("user", "").orEmpty(), prefs.getString("device", "").orEmpty(), token)
    }

    fun save(s: Session) {
        prefs.edit().putString("hs", s.homeserver).putString("user", s.userId).putString("device", s.deviceId)
            .putString("token", seal(s.token)).remove("since").apply()
    }

    fun clear() = prefs.edit().remove("hs").remove("user").remove("device").remove("token").remove("since").apply()

    /** La frase dell'archivio delle chiavi di cifratura: casuale, creata una volta, custodita cifrata dal Keystore. */
    fun cryptoPassphrase(): String {
        prefs.getString("crypto", null)?.let { sealed -> runCatching { open(sealed) }.getOrNull()?.let { return it } }
        val fresh = ChatCrypto.newPassphrase()
        prefs.edit().putString("crypto", seal(fresh)).apply()
        return fresh
    }

    var since: String?
        get() = prefs.getString("since", null)
        set(v) { prefs.edit().putString("since", v).apply() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return gen.generateKey()
    }

    private fun seal(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun open(sealed: String): String {
        val all = Base64.decode(sealed, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return String(c.doFinal(all.copyOfRange(12, all.size)))
    }

    private companion object { const val ALIAS = "eurise-chat" }
}

package eu.eurise.mail

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/** Una casella collegata. La password non sta qui in chiaro: [secret] è cifrato con una chiave del Keystore. */
data class Account(
    val id: String,
    val email: String,
    val name: String,
    val label: String,
    val settings: ServerSettings,
    val secret: String,
    /** Indice nelle tinte della suite (il pallino della casella). */
    val tint: Int,
) {
    val user get() = settings.user ?: email
}

/** Le tinte delle caselle: le stesse dei calendari (core/designsystem CalendarTint). */
val ACCOUNT_TINTS = longArrayOf(0xFF3E5C8A, 0xFF4F6B5A, 0xFF6B4A7A, 0xFF85672B, 0xFF2E6E73, 0xFFA0522D, 0xFF8C2F39, 0xFF55585E)

/**
 * La password cifrata sul telefono con AES-GCM e una chiave che non esce dal Keystore di Android (nemmeno nei backup):
 * senza il telefono non si apre.
 */
object Vault {
    private const val ALIAS = "eurise-mail"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    fun seal(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun open(sealed: String): String {
        val all = Base64.decode(sealed, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return String(c.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    }
}

/** Le caselle, in un file nella memoria dell'app (escluso dai backup di Android). */
class AccountStore(context: Context) {
    private val file = File(context.filesDir, "accounts.json")

    fun load(): List<Account> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i -> fromJson(a.getJSONObject(i)) }
    }.getOrDefault(emptyList())

    fun save(list: List<Account>) {
        val a = JSONArray()
        list.forEach { a.put(toJson(it)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
    }

    private fun server(s: Server) = JSONObject().put("host", s.host).put("port", s.port).put("sec", s.security.name)
    private fun server(o: JSONObject) = Server(o.getString("host"), o.getInt("port"), Security.valueOf(o.getString("sec")))

    private fun toJson(a: Account) = JSONObject()
        .put("id", a.id).put("email", a.email).put("name", a.name).put("label", a.label)
        .put("imap", server(a.settings.imap)).put("smtp", server(a.settings.smtp)).put("user", a.settings.user ?: "")
        .put("secret", a.secret).put("tint", a.tint)

    private fun fromJson(o: JSONObject) = Account(
        id = o.getString("id"), email = o.getString("email"), name = o.optString("name"), label = o.optString("label"),
        settings = ServerSettings(server(o.getJSONObject("imap")), server(o.getJSONObject("smtp")), o.optString("user").ifBlank { null }),
        secret = o.getString("secret"), tint = o.optInt("tint"),
    )
}

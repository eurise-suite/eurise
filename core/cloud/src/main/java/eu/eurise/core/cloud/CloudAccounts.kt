package eu.eurise.core.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** I tipi di account: Murena è un Nextcloud con l'indirizzo già noto; kDrive e gli altri usano WebDAV. */
enum class CloudKind { Murena, Nextcloud, KDrive, WebDav }

/**
 * Un account cloud collegato.
 *
 * @property davUrl indirizzo WebDAV della cartella principale dei file, con la barra finale.
 * @property name come lo si mostra (di solito l'indirizzo email o il nome utente).
 */
data class CloudAccount(
    val id: String,
    val kind: CloudKind,
    val name: String,
    val user: String,
    val davUrl: String,
)

/**
 * Gli account cloud del telefono. Le password (sempre password per le app, mai quella principale) sono
 * cifrate con una chiave del Keystore di Android, che non lascia mai il telefono.
 */
class CloudAccounts private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("cloud_accounts", Context.MODE_PRIVATE)
    private val _accounts = MutableStateFlow(load())
    val accounts: StateFlow<List<CloudAccount>> = _accounts.asStateFlow()

    fun get(id: String?): CloudAccount? = _accounts.value.firstOrNull { it.id == id }

    /** Aggiunge (o sostituisce, se c'è già lo stesso indirizzo e utente) un account e ne restituisce l'id. */
    fun add(kind: CloudKind, name: String, user: String, davUrl: String, password: String): String {
        val existing = _accounts.value.firstOrNull { it.davUrl == davUrl && it.user == user }
        val account = CloudAccount(existing?.id ?: UUID.randomUUID().toString(), kind, name, user, davUrl)
        val list = _accounts.value.filterNot { it.id == account.id } + account
        prefs.edit()
            .putString(KEY_LIST, JSONArray(list.map { it.toJson() }).toString())
            .putString(KEY_PW + account.id, Secrets.encrypt(password))
            .apply()
        _accounts.value = list
        return account.id
    }

    fun remove(id: String) {
        val list = _accounts.value.filterNot { it.id == id }
        prefs.edit()
            .putString(KEY_LIST, JSONArray(list.map { it.toJson() }).toString())
            .remove(KEY_PW + id)
            .apply()
        _accounts.value = list
    }

    /** Client WebDAV per l'account, oppure null se l'account o la sua password non ci sono più. */
    fun dav(id: String?): WebDav? {
        val account = get(id) ?: return null
        val password = prefs.getString(KEY_PW + account.id, null)?.let { Secrets.decrypt(it) } ?: return null
        return WebDav(account.davUrl, account.user, password)
    }

    private fun load(): List<CloudAccount> = runCatching {
        val array = JSONArray(prefs.getString(KEY_LIST, "[]"))
        (0 until array.length()).map { array.getJSONObject(it).toAccount() }
    }.getOrDefault(emptyList())

    private fun CloudAccount.toJson() = JSONObject()
        .put("id", id).put("kind", kind.name).put("name", name).put("user", user).put("dav", davUrl)

    private fun JSONObject.toAccount() = CloudAccount(
        id = getString("id"),
        kind = runCatching { CloudKind.valueOf(getString("kind")) }.getOrDefault(CloudKind.WebDav),
        name = getString("name"),
        user = getString("user"),
        davUrl = getString("dav"),
    )

    companion object {
        private const val KEY_LIST = "accounts"
        private const val KEY_PW = "pw_"

        @Volatile private var instance: CloudAccounts? = null

        fun get(context: Context): CloudAccounts =
            instance ?: synchronized(this) { instance ?: CloudAccounts(context.applicationContext).also { instance = it } }

        /** Indirizzo WebDAV dei file di un utente Nextcloud (Murena compreso). */
        fun nextcloudDav(server: String, loginName: String): String =
            server.trimEnd('/') + "/remote.php/dav/files/" + android.net.Uri.encode(loginName) + "/"

        /** Indirizzo WebDAV di un kDrive: il numero è quello dopo "/drive/" nell'indirizzo di kDrive sul web. */
        fun kDriveDav(driveId: String): String = "https://${driveId.trim()}.connect.kdrive.infomaniak.com/"
    }
}

/** Cifratura AES-GCM con una chiave del Keystore di Android. */
private object Secrets {
    private const val ALIAS = "eurise-cloud"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String? = runCatching {
        val (iv, data) = stored.split(':', limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()
}

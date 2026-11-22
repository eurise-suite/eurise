package eu.eurise.core.cloud.lock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * I segreti che gli altri APK della suite affidano all'app Eurise (SecretsContract: per ora la chiave di recupero della
 * Chat). Ognuno sta sigillato con una chiave AES-GCM del Keystore di questo telefono, in preferenze senza «eurise_» (fuori
 * dal backup); nel backup cifrato della suite entrano a parte, in chiaro solo dentro il backup ([BACKUP_ENTRY]), come
 * la chiave delle note ([LockKeys]). Sul telefono nuovo si risigillano con il suo Keystore.
 *
 * Fino al 21 novembre 2026 stavano in chiaro in «eurise_secrets»: al primo accesso passano qui (scritti, riletti uguali,
 * poi il file vecchio si cancella). Un backup vecchio rimette quel file e la migrazione lo riassorbe.
 */
object SuiteSecrets {
    /** Voce del backup della suite con i segreti (il backup è cifrato con la frase segreta). */
    const val BACKUP_ENTRY = "lock/secrets.json"
    /** Il file in chiaro di prima: va ancora accettato nei ripristini dei backup vecchi, poi migrato. */
    const val LEGACY_PREFS = "eurise_secrets"

    private const val PREFS = "suite_secrets"
    private const val ALIAS = "eurise_suite_secrets"

    @Synchronized
    fun get(context: Context, name: String): String? {
        migrate(context)
        return prefs(context).getString(name, null)?.let(::open)
    }

    @Synchronized
    fun put(context: Context, name: String, value: String) {
        migrate(context)
        prefs(context).edit().putString(name, seal(value)).commit()
    }

    @Synchronized
    fun remove(context: Context, name: String) {
        migrate(context)
        prefs(context).edit().remove(name).commit()
    }

    /** I segreti in chiaro per il backup cifrato della suite; null se non ce n'è nessuno. */
    @Synchronized
    fun exportForBackup(context: Context): ByteArray? {
        migrate(context)
        val all = JSONObject()
        prefs(context).all.forEach { (k, v) -> (v as? String)?.let(::open)?.let { all.put(k, it) } }
        return if (all.length() == 0) null else all.toString().toByteArray()
    }

    /** Rimette i segreti arrivati da un backup, sigillati dal Keystore di questo telefono. */
    @Synchronized
    fun restore(context: Context, json: ByteArray) {
        val all = runCatching { JSONObject(String(json)) }.getOrNull() ?: return
        val edit = prefs(context).edit()
        all.keys().forEach { k -> all.optString(k).let { edit.putString(k, seal(it)) } }
        edit.commit()
    }

    /**
     * Sposta i segreti dal vecchio file in chiaro. Il file si cancella solo se ogni valore si rilegge uguale dalla
     * versione sigillata: se il Keystore non risponde resta tutto com'era e si riprova al prossimo accesso.
     */
    private fun migrate(context: Context) {
        if (!java.io.File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFS.xml").exists()) return
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val old = legacy.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
        if (old.isEmpty()) {
            if (legacy.all.isEmpty()) context.deleteSharedPreferences(LEGACY_PREFS)
            return
        }
        val ok = runCatching {
            val edit = prefs(context).edit()
            old.forEach { (k, v) -> edit.putString(k, seal(v)) }
            edit.commit() && old.all { (k, v) -> prefs(context).getString(k, null)?.let(::open) == v }
        }.getOrDefault(false)
        if (ok) {
            legacy.edit().clear().commit()
            context.deleteSharedPreferences(LEGACY_PREFS)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
        )
        return generator.generateKey()
    }

    private fun seal(value: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return b64(c.iv) + ":" + b64(c.doFinal(value.toByteArray()))
    }

    private fun open(stored: String): String? = runCatching {
        val (iv, data) = stored.split(':', limit = 2)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, unb64(iv)))
        String(c.doFinal(unb64(data)))
    }.getOrNull()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}

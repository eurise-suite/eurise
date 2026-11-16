package eu.eurise.core.cloud.lock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * La chiave delle note bloccate.
 *
 * È una chiave AES a 256 bit nata sul telefono; sul disco sta solo sigillata da una chiave del portachiavi protetto di
 * Android (che non esce dal telefono). Il backup cifrato della suite la porta con sé ([exportForBackup]), così sul
 * telefono nuovo le note bloccate si riaprono ([restore]). Il cloud delle note riceve solo il testo già cifrato.
 */
object LockKeys {
    /** Voce del backup della suite con la chiave (il backup è cifrato con la frase segreta). */
    const val BACKUP_ENTRY = "lock/notes.key"

    private const val PREFS = "lock_keys" // senza «eurise_»: la versione sigillata non va nel backup
    private const val ALIAS = "eurise_lock_keys"
    private const val NOTES = "notes"

    /** La chiave delle note; nasce alla prima nota bloccata. */
    @Synchronized
    fun notes(context: Context): SecretKey {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(NOTES, null)?.let { stored -> open(stored)?.let { return SecretKeySpec(it, "AES") } }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(NOTES, seal(raw)).commit()
        return SecretKeySpec(raw, "AES")
    }

    /** Vero se esiste già una chiave delle note (c'è almeno una nota bloccata, ora o in passato). */
    fun hasNotes(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(NOTES, null)?.let { open(it) != null } == true

    /** La chiave in chiaro per il backup cifrato della suite; nulla se non è mai servita. */
    fun exportForBackup(context: Context): ByteArray? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(NOTES, null)?.let { open(it) }

    /** Rimette la chiave arrivata da un backup, sigillata dal portachiavi di questo telefono. */
    @Synchronized
    fun restore(context: Context, raw: ByteArray) {
        if (raw.size != 32) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(NOTES, seal(raw)).commit()
    }

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

    private fun seal(raw: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return b64(c.iv) + ":" + b64(c.doFinal(raw))
    }

    private fun open(stored: String): ByteArray? = runCatching {
        val (iv, data) = stored.split(':', limit = 2)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, unb64(iv)))
        c.doFinal(unb64(data))
    }.getOrNull()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}

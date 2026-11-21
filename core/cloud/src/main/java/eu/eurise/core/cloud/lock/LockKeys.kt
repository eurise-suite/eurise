package eu.eurise.core.cloud.lock

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * La chiave delle note bloccate (tavole NoteBlocca, NoteBloccata, NoteBloccaSenzaBackup, NoteRiapri).
 *
 * È una chiave AES a 256 bit nata sul telefono. Dal 21 novembre 2026 sul disco sta sigillata da una chiave del Keystore
 * che si usa **solo nei 30 secondi dopo l'impronta o il PIN** ([AUTH_SECONDS]): chi forza il telefono non la apre senza il
 * dito o il PIN. Aperta, resta in memoria finché la nota è aperta ([unlock], [endSession]). Android non la cancella se
 * aggiungi o cambi un'impronta; la cancella solo se togli del tutto il blocco schermo ([Opened.Invalidated]).
 *
 * Il backup automatico non ha nessuno davanti: porta una copia chiusa verso la coppia del backup ([NoteSeal]), che si apre
 * solo con la frase segreta o il codice di recupero. La stessa copia, tenuta sul telefono, riapre le note se il blocco
 * schermo è stato tolto ([reopen]).
 *
 * Prima la chiave era sigillata senza impronta («notes»): si lega all'impronta al primo sblocco, se il backup ha già la sua
 * coppia (altrimenti la copia non si potrebbe scrivere) o se l'utente ha scelto di bloccare senza backup ([acceptNoBackup]).
 */
object LockKeys {
    /** Voce del backup con la chiave in chiaro (backup di prima, e chiavi non ancora legate all'impronta). */
    const val BACKUP_ENTRY = "lock/notes.key"
    /** Voce del backup con la copia chiusa verso la coppia del backup ([NoteSeal]). */
    const val SEALED_ENTRY = "lock/notes.sealed"

    /** Quanto resta usabile la chiave del Keystore dopo l'impronta o il PIN. */
    const val AUTH_SECONDS = 30

    private const val PREFS = "lock_keys" // senza «eurise_»: le versioni sigillate non vanno nel backup delle preferenze
    private const val ALIAS = "eurise_lock_keys"
    private const val ALIAS_AUTH = "eurise_lock_keys_auth"
    private const val LEGACY = "notes"
    private const val BOUND = "notes_auth"
    private const val COPY = "notes_copy"
    private const val PAIR = "backup_pair"
    private const val NO_BACKUP_OK = "no_backup_ok"

    /** Esito di [unlock]. */
    sealed interface Opened {
        data object Ok : Opened
        /** Serve prima l'impronta o il PIN (sono passati più di [AUTH_SECONDS]). */
        data object NeedsAuth : Opened
        /** Il blocco schermo è stato tolto: la chiave del Keystore non c'è più. [canReopen]: c'è la copia da riaprire. */
        data class Invalidated(val canReopen: Boolean) : Opened
        /** Nessuna chiave su questo telefono (telefono nuovo senza ripristino). */
        data object Missing : Opened
    }

    @Volatile private var session: ByteArray? = null

    /** La chiave delle note aperta in questa sessione; null se serve [unlock]. */
    fun notes(): SecretKey? = session?.let { SecretKeySpec(it, "AES") }

    /** Vero se esiste già una chiave delle note su questo telefono (legata o no, o la sola copia da riaprire). */
    fun hasNotes(context: Context): Boolean = prefs(context).let { it.contains(BOUND) || it.contains(LEGACY) || it.contains(COPY) }

    /**
     * Apre la chiave delle note (da chiamare subito dopo l'impronta o il PIN) e la tiene in memoria per la sessione.
     * [create]: se non c'è ancora, ne nasce una (prima nota bloccata). Lega all'impronta una chiave di prima, se si può.
     */
    @Synchronized
    fun unlock(context: Context, create: Boolean = false): Opened {
        val p = prefs(context)
        p.getString(BOUND, null)?.let { stored ->
            return try {
                session = openAuth(stored)
                runCatching { refreshCopy(context) }
                Opened.Ok
            } catch (e: UserNotAuthenticatedException) {
                Opened.NeedsAuth
            } catch (e: KeyPermanentlyInvalidatedException) {
                Opened.Invalidated(p.contains(COPY))
            } catch (e: Exception) {
                if (e.cause is KeyPermanentlyInvalidatedException) Opened.Invalidated(p.contains(COPY)) else Opened.NeedsAuth
            }
        }
        val legacy = p.getString(LEGACY, null)?.let { openPlain(it) }
        val raw = legacy ?: if (create) ByteArray(32).also { SecureRandom().nextBytes(it) } else null
        if (raw == null) return if (p.contains(COPY)) Opened.Invalidated(true) else Opened.Missing
        if (canBind(context)) {
            try {
                bind(context, raw)
            } catch (e: UserNotAuthenticatedException) {
                if (legacy == null) return Opened.NeedsAuth
            } catch (e: Exception) {
                // Keystore che non risponde: resta com'era, si riprova al prossimo sblocco.
                if (legacy == null) p.edit().putString(LEGACY, sealPlain(raw)).commit()
            }
        } else if (legacy == null) {
            p.edit().putString(LEGACY, sealPlain(raw)).commit()
        }
        session = raw
        return Opened.Ok
    }

    /** Chiude la sessione: la chiave esce dalla memoria (nota richiusa, app in secondo piano). */
    fun endSession() {
        session?.fill(0)
        session = null
    }

    /** L'utente ha scelto «Blocca lo stesso» senza backup: la chiave si lega all'impronta anche senza copia. */
    fun acceptNoBackup(context: Context) { prefs(context).edit().putBoolean(NO_BACKUP_OK, true).apply() }

    /**
     * Riapre le note con la frase o il codice del backup (tavola NoteRiapri), dalla copia tenuta sul telefono. Dopo serve
     * l'impronta o il PIN nuovi ([unlock]) per legarla di nuovo. Lento di proposito (PBKDF2). Falso se il segreto è sbagliato.
     */
    @Synchronized
    fun reopen(context: Context, secret: String): Boolean {
        val p = prefs(context)
        val copy = p.getString(COPY, null)?.let(::unb64) ?: return false
        val raw = NoteSeal.open(copy, secret) ?: return false
        deleteAuthKey()
        p.edit().remove(BOUND).putString(LEGACY, sealPlain(raw)).commit()
        raw.fill(0)
        return true
    }

    // --- backup ---------------------------------------------------------------

    /** Nuova coppia del backup (prima attivazione: ci sono frase e codice). La copia si rifà al prossimo sblocco. */
    @Synchronized
    fun setBackupPair(context: Context, passphrase: String, recoveryCode: String) {
        val p = prefs(context)
        val pair = NoteSeal.newPair(passphrase.toCharArray(), recoveryCode)
        val raw = session ?: p.getString(LEGACY, null)?.let { openPlain(it) }
        val edit = p.edit().putString(PAIR, b64(NoteSeal.encodePair(pair)))
        if (raw != null) edit.putString(COPY, b64(NoteSeal.seal(pair, raw))).remove(STALE)
        else if (p.contains(BOUND)) edit.putBoolean(STALE, true) // legata e chiusa: la copia si rifà al prossimo sblocco
        edit.commit()
    }

    /** Frase del backup cambiata: la coppia si richiude con quella nuova ([current]: frase attuale o codice). */
    @Synchronized
    fun changeBackupPassphrase(context: Context, current: String, next: String) {
        val p = prefs(context)
        val pair = p.getString(PAIR, null)?.let { NoteSeal.decodePair(unb64(it)) } ?: return
        val changed = NoteSeal.rewrapPassphrase(pair, current, next.toCharArray()) ?: return
        val edit = p.edit().putString(PAIR, b64(NoteSeal.encodePair(changed)))
        // La copia porta con sé le chiusure: si richiude con la frase nuova senza bisogno della chiave delle note.
        p.getString(COPY, null)?.let { old -> NoteSeal.rewrapCopy(unb64(old), changed)?.let { edit.putString(COPY, b64(it)) } }
        edit.commit()
    }

    /** Le voci per il backup: la copia chiusa se c'è; la chiave in chiaro solo se non è ancora legata all'impronta. */
    fun exportForBackup(context: Context): Map<String, ByteArray> = buildMap {
        val p = prefs(context)
        p.getString(COPY, null)?.let { put(SEALED_ENTRY, unb64(it)) }
        if (!p.contains(BOUND)) p.getString(LEGACY, null)?.let { openPlain(it) }?.let { put(BACKUP_ENTRY, it) }
    }

    /** Rimette la chiave arrivata da un backup (in chiaro, o aperta con la frase al ripristino): si lega al primo sblocco. */
    @Synchronized
    fun restore(context: Context, raw: ByteArray) {
        if (raw.size != 32) return
        deleteAuthKey()
        prefs(context).edit().remove(BOUND).putString(LEGACY, sealPlain(raw)).commit()
    }

    /** Rimette la copia e la coppia arrivate da un backup, per i backup e le riaperture di questo telefono. */
    @Synchronized
    fun restoreSealed(context: Context, entry: ByteArray) {
        val pair = NoteSeal.pairOf(entry) ?: return
        prefs(context).edit().putString(COPY, b64(entry)).putString(PAIR, b64(NoteSeal.encodePair(pair))).commit()
    }

    // --- interno --------------------------------------------------------------

    private const val STALE = "copy_stale"

    private fun canBind(context: Context): Boolean =
        prefs(context).let { it.contains(PAIR) || it.getBoolean(NO_BACKUP_OK, false) }

    /** Sigilla [raw] con la chiave legata all'impronta, scrive la copia per il backup e toglie la versione di prima. */
    private fun bind(context: Context, raw: ByteArray) {
        val p = prefs(context)
        val sealed = sealAuth(raw)
        check(openAuth(sealed).contentEquals(raw))
        val edit = p.edit().putString(BOUND, sealed).remove(LEGACY).remove(STALE)
        p.getString(PAIR, null)?.let { NoteSeal.decodePair(unb64(it)) }?.let { edit.putString(COPY, b64(NoteSeal.seal(it, raw))) }
        edit.commit()
    }

    /** Con la chiave aperta, rifà la copia se la coppia del backup è cambiata nel frattempo. */
    private fun refreshCopy(context: Context) {
        val p = prefs(context)
        val raw = session ?: return
        val pair = p.getString(PAIR, null)?.let { NoteSeal.decodePair(unb64(it)) } ?: return
        val copyPair = p.getString(COPY, null)?.let { NoteSeal.pairOf(unb64(it)) }
        val same = copyPair != null && copyPair.publicKey.contentEquals(pair.publicKey) && copyPair.wrapPass.contentEquals(pair.wrapPass)
        if (!same || p.getBoolean(STALE, false)) p.edit().putString(COPY, b64(NoteSeal.seal(pair, raw))).remove(STALE).commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun plainKey(): SecretKey {
        (keystore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
        )
        return generator.generateKey()
    }

    /** La chiave del Keystore usabile solo per [AUTH_SECONDS] dopo l'impronta o il PIN; chiede un blocco schermo. */
    private fun authKey(): SecretKey {
        (keystore().getKey(ALIAS_AUTH, null) as? SecretKey)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(ALIAS_AUTH, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(AUTH_SECONDS, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION") builder.setUserAuthenticationValidityDurationSeconds(AUTH_SECONDS)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun deleteAuthKey() { runCatching { keystore().deleteEntry(ALIAS_AUTH) } }

    private fun sealAuth(raw: ByteArray): String = seal(authKey(), raw)

    private fun openAuth(stored: String): ByteArray = open(authKey(), stored)

    private fun sealPlain(raw: ByteArray): String = seal(plainKey(), raw)

    private fun openPlain(stored: String): ByteArray? = runCatching { open(plainKey(), stored) }.getOrNull()

    private fun seal(key: SecretKey, raw: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key)
        return b64(c.iv) + ":" + b64(c.doFinal(raw))
    }

    private fun open(key: SecretKey, stored: String): ByteArray {
        val (iv, data) = stored.split(':', limit = 2)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unb64(iv)))
        return c.doFinal(unb64(data))
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}

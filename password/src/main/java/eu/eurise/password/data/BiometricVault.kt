package eu.eurise.password.data

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiude la chiave della cassaforte con una chiave del Keystore che si usa solo dopo il dito e che Android annulla se
 * viene aggiunta un'impronta. Usa le API di sistema (Android 9+), così non serve la libreria androidx.biometric.
 */
class BiometricVault(private val context: Context) {
    private val prefs = context.getSharedPreferences("password_bio", Context.MODE_PRIVATE)

    val enabled: Boolean get() = available && prefs.contains(KEY_BLOB)

    val available: Boolean
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                @Suppress("DEPRECATION")
                context.getSystemService(BiometricManager::class.java).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
            Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
            else -> false
        }

    /** Sempre una chiave nuova: le impronte attuali sono quelle che valgono da qui in avanti. */
    fun encryptCipher(): Cipher {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, generator.generateKey()) }
    }

    /** Null se la chiave non vale più (impronte cambiate) o la scelta non è attiva. */
    fun decryptCipher(): Cipher? = try {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(ALIAS, null) as? SecretKey
        val iv = prefs.getString(KEY_IV, null)
        if (key == null || iv == null) null
        else Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        null
    }

    fun store(cipher: Cipher, key: ByteArray) {
        val blob = cipher.doFinal(key)
        prefs.edit()
            .putString(KEY_BLOB, Base64.encodeToString(blob, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun read(cipher: Cipher): ByteArray = cipher.doFinal(Base64.decode(prefs.getString(KEY_BLOB, ""), Base64.NO_WRAP))

    fun disable() {
        prefs.edit().clear().apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }

    private companion object {
        const val ALIAS = "eurise-password-bio"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val KEY_BLOB = "blob"
        const val KEY_IV = "iv"
    }
}

/** Chiede il dito; [onSuccess] riceve il cifrario ormai sbloccato. Annullare non fa nulla: resta la frase principale. */
fun askBiometric(context: Context, title: String, negative: String, cipher: Cipher, onSuccess: (Cipher) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val executor = context.mainExecutor
    val prompt = BiometricPrompt.Builder(context)
        .setTitle(title)
        .setNegativeButton(negative, executor) { _, _ -> }
        .build()
    prompt.authenticate(
        BiometricPrompt.CryptoObject(cipher),
        CancellationSignal(),
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject.cipher?.let(onSuccess)
            }
        },
    )
}

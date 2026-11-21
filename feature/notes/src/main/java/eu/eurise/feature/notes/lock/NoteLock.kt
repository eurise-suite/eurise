package eu.eurise.feature.notes.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import eu.eurise.core.cloud.lock.LockKeys
import eu.eurise.core.data.model.Note
import eu.eurise.feature.notes.doc.Block
import eu.eurise.feature.notes.doc.LockedBlock
import eu.eurise.feature.notes.doc.NoteHtml
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifratura delle note bloccate. Il contenuto (titolo compreso) diventa un documento HTML cifrato con AES-GCM e la chiave
 * di [LockKeys]; la nota salvata contiene solo quel [LockedBlock]. Nel database il testo per la ricerca resta vuoto: la
 * ricerca, Alfred, la Scrivania e il Ponte vedono al massimo il titolo. La chiave si apre solo dopo l'impronta o il PIN
 * ([LockKeys.unlock]) e resta in memoria finché la nota è aperta.
 */
object NoteLock {
    /** Quanto resta aperta una nota sbloccata senza essere toccata. */
    const val OPEN_MS = 5 * 60_000L

    /** Vero se la nota salvata è bloccata (il suo contenuto è un blocco cifrato). */
    fun isLocked(note: Note): Boolean = note.content.contains(MARKER)

    /** Vero se il telefono ha un blocco schermo: senza, una nota non si può bloccare. */
    fun canLock(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Cifra titolo e blocchi; null se la chiave non è aperta (serve [LockKeys.unlock] dopo l'impronta o il PIN). */
    fun seal(title: String, blocks: List<Block>): LockedBlock? {
        val key = LockKeys.notes() ?: return null
        val plain = NoteHtml.document(title, blocks).toByteArray(Charsets.UTF_8)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key)
        val sealed = b64(c.iv) + ":" + b64(c.doFinal(plain))
        return LockedBlock(0, sealed)
    }

    /** Titolo e blocchi di una nota bloccata; nullo se la chiave non è aperta o non è quella giusta. */
    fun open(block: LockedBlock, ids: () -> Long): Pair<String, List<Block>>? = runCatching {
        val key = LockKeys.notes() ?: return null
        val (iv, data) = block.sealed.trim().split(':', limit = 2)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unb64(iv)))
        val html = String(c.doFinal(unb64(data)), Charsets.UTF_8)
        NoteHtml.title(html) to NoteHtml.read(html, ids)
    }.getOrNull()

    private const val MARKER = "data-eurise-lock"
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}

/**
 * Chiede l'impronta o il PIN del telefono (Android 9 e successivi nell'app; su Android 8 con la schermata di sblocco del
 * sistema). Restituisce la funzione che apre la richiesta; [onSuccess] arriva solo se la conferma riesce.
 */
@Composable
fun rememberDeviceAuth(title: String, subtitle: String, onSuccess: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val success = rememberUpdatedState(onSuccess)
    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) success.value()
    }
    return {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure == true) {
            val fingerprint = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || Build.VERSION.SDK_INT == Build.VERSION_CODES.P && fingerprint) {
                val executor = ContextCompat.getMainExecutor(context)
                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = success.value()
                }
                val builder = BiometricPrompt.Builder(context).setTitle(title).setSubtitle(subtitle)
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> builder.setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
                    else -> builder.setNegativeButton(context.getString(android.R.string.cancel), executor) { _, _ -> }
                }
                runCatching { builder.build().authenticate(CancellationSignal(), executor, callback) }
            } else {
                @Suppress("DEPRECATION")
                val intent: Intent? = keyguard.createConfirmDeviceCredentialIntent(title, subtitle)
                if (intent != null) keyguardLauncher.launch(intent)
            }
        }
    }
}

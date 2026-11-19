package eu.eurise.core.suite

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Chiavi AES dell'Android Keystore nel posto più sicuro che il telefono ha (docs/SICUREZZA.md, «Crittografia»).
 *
 * Prima si prova il chip di sicurezza separato (StrongBox, per esempio Titan M sui Pixel): lì la chiave resiste anche a
 * chi supera l'area protetta del processore. Se il telefono non lo ha, la stessa chiave nasce nell'area protetta (TEE),
 * come prima. Solo per chiavi usate poche volte e con l'utente presente (sblocco con l'impronta): StrongBox è lento.
 */
object SecureKeys {
    /** Genera la chiave [alias] con le proprietà di [spec], nel chip separato se c'è. */
    fun generateAes(spec: (KeyGenParameterSpec.Builder.() -> Unit), alias: String, purposes: Int): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generator.init(KeyGenParameterSpec.Builder(alias, purposes).apply(spec).setIsStrongBoxBacked(true).build())
                return generator.generateKey()
            } catch (e: java.security.ProviderException) {
                // StrongBoxUnavailableException (nessun chip separato) o un chip che rifiuta questa chiave: area protetta
                // normale, qui sotto. Si prende la classe madre perché la figlia non esiste prima di Android 9.
            } catch (e: java.security.GeneralSecurityException) {
                // Parametri rifiutati dal chip separato: stessa strada.
            }
        }
        generator.init(KeyGenParameterSpec.Builder(alias, purposes).apply(spec).build())
        return generator.generateKey()
    }
}

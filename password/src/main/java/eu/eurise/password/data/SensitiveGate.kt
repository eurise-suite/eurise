package eu.eurise.password.data

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock

/**
 * I dati riservati (i miei dati, le carte) si vedono, si copiano e si compilano solo dopo l'impronta o il PIN del
 * telefono, anche a cassaforte aperta. Confermato una volta, il permesso dura [WINDOW_MS], così un modulo intero si
 * compila senza dieci conferme; bloccare la cassaforte lo chiude subito.
 */
object SensitiveGate {
    const val WINDOW_MS = 2 * 60_000L

    @Volatile private var until = 0L

    fun isOpen(): Boolean = SystemClock.elapsedRealtime() < until

    /** Secondi che restano prima che si richiuda; zero se è chiuso. */
    fun remainingSeconds(): Int = ((until - SystemClock.elapsedRealtime()).coerceAtLeast(0) / 1000).toInt()

    fun open() { until = SystemClock.elapsedRealtime() + WINDOW_MS }

    fun close() { until = 0L }
}

/**
 * Chiede l'impronta o, in alternativa, il PIN (o la sequenza) del telefono. Se il telefono non ha un blocco schermo non
 * c'è nulla da chiedere: vale lo sblocco della cassaforte con la frase principale. Annullare non fa nulla.
 */
fun askDeviceAuth(context: Context, title: String, subtitle: String, onSuccess: () -> Unit) {
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    if (keyguard?.isDeviceSecure != true) { onSuccess(); return }
    val executor = context.mainExecutor
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    }
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> BiometricPrompt.Builder(context)
            .setTitle(title).setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build().authenticate(CancellationSignal(), executor, callback)
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> @Suppress("DEPRECATION") BiometricPrompt.Builder(context)
            .setTitle(title).setSubtitle(subtitle)
            .setDeviceCredentialAllowed(true)
            .build().authenticate(CancellationSignal(), executor, callback)
        Build.VERSION.SDK_INT == Build.VERSION_CODES.P && context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ->
            BiometricPrompt.Builder(context)
                .setTitle(title).setSubtitle(subtitle)
                .setNegativeButton(context.getString(android.R.string.cancel), executor) { _, _ -> }
                .build().authenticate(CancellationSignal(), executor, callback)
        // Android 8: il sistema non offre una richiesta del PIN dentro l'app; resta lo sblocco con la frase.
        else -> onSuccess()
    }
}

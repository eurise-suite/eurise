package eu.eurise.core.cloud.backup

import android.content.Context

/**
 * Eurise Tastiera nel backup: dal 21 novembre 2026 passa da [SuiteApps] come le altre app separate (stessa voce
 * `apps/keyboard.zip`, stesso accordo con il suo `BackupProvider`). Restano qui i nomi usati dal resto dell'app.
 */
object KeyboardBackup {
    const val ENTRY = "apps/keyboard.zip"

    /** Le parole della Tastiera aspettano che la Tastiera sia installata. */
    fun waiting(context: Context): Boolean = "keyboard" in SuiteApps.waiting(context)

    /** Consegna i dati in attesa a tutte le app della suite installate (non solo alla Tastiera). */
    fun deliverPending(context: Context) = SuiteApps.deliverPending(context)
}

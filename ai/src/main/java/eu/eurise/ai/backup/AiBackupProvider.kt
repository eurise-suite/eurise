package eu.eurise.ai.backup

import android.content.Context
import eu.eurise.core.suite.SuiteBackupProvider
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * AI nel backup della suite: le impostazioni. Il modello (gigabyte) si riscarica.
 * Area Backup, 21 novembre 2026 (docs/stato/BACKUP.md): solo verso l'app Eurise, con il permesso della suite.
 */
class AiBackupProvider : SuiteBackupProvider() {
    override fun export(context: Context, zip: ZipOutputStream) = addPrefs(context, zip, *PREFS.toTypedArray())

    override fun restore(context: Context, zip: ZipInputStream) {
        while (true) {
            val e = zip.nextEntry ?: break
            safeName(e)?.let { restorePrefs(context, zip, it, PREFS) }
        }
    }

    companion object {
        private val PREFS = setOf("brain")
    }
}

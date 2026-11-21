package eu.eurise.chat.backup

import android.content.Context
import eu.eurise.core.suite.SuiteBackupProvider
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Chat nel backup della suite: le impostazioni, i nomi e le sigle abbinati, i turni. Restano fuori la sessione e le chiavi del dispositivo (crypto/): copiarle farebbe di un altro telefono un doppione dello stesso dispositivo, cosa che Matrix sconsiglia. Sul telefono nuovo si accede come dispositivo nuovo e la chiave di recupero (già nel backup di Eurise) riapre la cronologia cifrata. Fuori anche l'accesso allo studio (credenziali).
 * Area Backup, 21 novembre 2026 (docs/stato/BACKUP.md): solo verso l'app Eurise, con il permesso della suite.
 */
class ChatBackupProvider : SuiteBackupProvider() {
    override fun export(context: Context, zip: ZipOutputStream) = addPrefs(context, zip, *PREFS.toTypedArray())

    override fun restore(context: Context, zip: ZipInputStream) {
        while (true) {
            val e = zip.nextEntry ?: break
            safeName(e)?.let { restorePrefs(context, zip, it, PREFS) }
        }
    }

    companion object {
        private val PREFS = setOf("chat_ui", "persone", "shifts")
    }
}

package eu.eurise.chat.backup

import android.content.Context
import eu.eurise.core.suite.SuiteBackupProvider
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Chat nel backup della suite: le impostazioni (modalità semplice compresa), i nomi e le sigle abbinati, i turni, le cartelle
 * delle conversazioni, «Le palle che hai passato», le approvazioni chieste, il lavoro che tace e Fuori orario (22 novembre 2026). Fuori la posizione dal vivo (`chat_live`: dura poco ed è legata a questo telefono). Restano fuori la sessione e le chiavi del dispositivo (crypto/): copiarle farebbe di un altro telefono un doppione dello stesso dispositivo, cosa che Matrix sconsiglia. Sul telefono nuovo si accede come dispositivo nuovo e la chiave di recupero (già nel backup di Eurise) riapre la cronologia cifrata. Fuori anche l'accesso allo studio (credenziali).
 * Area Backup, 21 novembre 2026 (docs/stato/BACKUP.md): solo verso l'app Eurise, con il permesso della suite.
 */
class ChatBackupProvider : SuiteBackupProvider() {
    override fun export(context: Context, zip: ZipOutputStream) {
        addPrefs(context, zip, *PREFS.toTypedArray())
        zip.putNextEntry(ZipEntry(ACCOUNT_KEYS))
        zip.write(exportKeys(accounts(context), KEYS))
        zip.closeEntry()
    }

    override fun restore(context: Context, zip: ZipInputStream) {
        while (true) {
            val e = zip.nextEntry ?: break
            val name = safeName(e) ?: continue
            if (name == ACCOUNT_KEYS) restoreKeys(accounts(context), zip.bufferedReader().readText(), KEYS)
            else restorePrefs(context, zip, name, PREFS)
        }
    }

    private fun accounts(context: Context) = context.getSharedPreferences("chat_accounts", Context.MODE_PRIVATE)

    companion object {
        private val PREFS = setOf("chat_ui", "persone", "shifts", "chat_folders", "chat_balls", "chat_approvals")

        /** Da `chat_accounts` solo le scelte (il lavoro che tace, Fuori orario): l'account attivo e le sessioni no. */
        private const val ACCOUNT_KEYS = "chat_accounts.json"
        private val KEYS = setOf("work_quiet", "off_hours")
    }
}

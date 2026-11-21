package eu.eurise.mail.backup

import android.content.Context
import eu.eurise.core.suite.SuiteBackupProvider
import eu.eurise.mail.R
import org.json.JSONArray
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Posta nel backup della suite (area Backup, 21 novembre 2026): le caselle (indirizzo, nome, server, colore), le
 * impostazioni e «Le palle che hai passato» (`mail_balls`, 22 novembre). Fuori lo stato della sincronizzazione (`mail_sync`). La posta sta sul server. La password di ogni casella è chiusa nel Keystore di questo telefono e non si
 * può portare via: nel backup non c'è. Sul telefono nuovo le caselle aspettano «da ricollegare» ([PENDING]) e il foglio
 * «Collega» le propone già scritte, con i loro server: basta la password.
 */
class MailBackupProvider : SuiteBackupProvider() {
    override fun summary(context: Context): String? {
        val n = runCatching { JSONArray(File(context.filesDir, ACCOUNTS).readText()).length() }.getOrDefault(0)
        return if (n > 0) context.getString(R.string.backup_summary_mail, n) else null
    }

    override fun export(context: Context, zip: ZipOutputStream) {
        val accounts = runCatching { JSONArray(File(context.filesDir, ACCOUNTS).readText()) }.getOrNull() ?: JSONArray()
        for (i in 0 until accounts.length()) accounts.getJSONObject(i).remove("secret")
        zip.putNextEntry(ZipEntry(ENTRY))
        zip.write(accounts.toString().toByteArray())
        zip.closeEntry()
        addPrefs(context, zip, *PREFS.toTypedArray())
    }

    override fun restore(context: Context, zip: ZipInputStream) {
        while (true) {
            val e = zip.nextEntry ?: break
            val name = safeName(e) ?: continue
            if (name == ENTRY) {
                val incoming = JSONArray(zip.bufferedReader().readText())
                val have = runCatching { JSONArray(File(context.filesDir, ACCOUNTS).readText()) }.getOrNull() ?: JSONArray()
                val linked = (0 until have.length()).map { have.getJSONObject(it).optString("email").lowercase() }.toSet()
                val pending = runCatching { JSONArray(File(context.filesDir, PENDING).readText()) }.getOrNull() ?: JSONArray()
                val waiting = (0 until pending.length()).map { pending.getJSONObject(it).optString("email").lowercase() }.toMutableSet()
                for (i in 0 until incoming.length()) {
                    val a = incoming.getJSONObject(i)
                    val email = a.optString("email").lowercase()
                    if (email.isEmpty() || email in linked || !waiting.add(email)) continue
                    a.remove("secret")
                    pending.put(a)
                }
                File(context.filesDir, PENDING).writeText(pending.toString())
            } else restorePrefs(context, zip, name, PREFS)
        }
    }

    companion object {
        const val ACCOUNTS = "accounts.json"
        /** Le caselle arrivate da un backup, da ricollegare con la loro password. */
        const val PENDING = "accounts_dal_backup.json"
        private const val ENTRY = "accounts.json"
        private val PREFS = setOf("mail", "mail_balls")
    }
}

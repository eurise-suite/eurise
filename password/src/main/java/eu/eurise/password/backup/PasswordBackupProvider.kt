package eu.eurise.password.backup

import android.content.Context
import eu.eurise.core.suite.SuiteBackupProvider
import eu.eurise.password.R
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Password nel backup della suite (area Backup, 21 novembre 2026). Entra il file della cassaforte così com'è, già
 * chiuso con la frase principale: nel backup ha due lucchetti, la frase del backup e la sua. Entrano anche gli alias
 * e i browser abbinati. Restano fuori l'impronta (chiave del Keystore di questo telefono: si riattiva), il controllo
 * delle violazioni (si rifà) e lo stato della sincronizzazione (sul telefono nuovo la si riaccende).
 *
 * Al ripristino una cassaforte già presente non si sostituisce mai: quella del backup va accanto
 * ([RESTORED]) e, alla prossima apertura, Password propone di unirla (stesso percorso di «Importa da KeePass»).
 */
class PasswordBackupProvider : SuiteBackupProvider() {
    override fun export(context: Context, zip: ZipOutputStream) {
        addFile(zip, vault(context), VAULT_ENTRY)
        addPrefs(context, zip, *PREFS.toTypedArray())
    }

    override fun restore(context: Context, zip: ZipInputStream) {
        while (true) {
            val e = zip.nextEntry ?: break
            val name = safeName(e) ?: continue
            when {
                name == VAULT_ENTRY -> {
                    val current = vault(context)
                    // Mai sopra una cassaforte che c'è già: si mette accanto e si propone di unirla.
                    writeEntry(zip, if (current.exists() && current.length() > 0) restored(context) else current)
                    val side = restored(context)
                    if (side.exists() && current.exists() && side.readBytes().contentEquals(current.readBytes())) side.delete()
                }
                else -> restorePrefs(context, zip, name, PREFS)
            }
        }
    }

    override fun summary(context: Context): String? =
        if (vault(context).exists()) context.getString(R.string.backup_summary_vault) else null

    companion object {
        private const val VAULT_ENTRY = "vault/cassaforte.kdbx"
        private val PREFS = setOf("password_alias", "password_browsers")

        fun vault(context: Context) = File(context.filesDir, "cassaforte.kdbx")

        /** La cassaforte arrivata da un backup quando sul telefono ce n'era già una. */
        fun restored(context: Context) = File(context.filesDir, "cassaforte-dal-backup.kdbx")
    }
}

package eu.eurise.core.cloud.backup

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Eurise Tastiera nel backup della suite: è un APK a parte e custodisce da sé le parole imparate, il dizionario
 * personale, il modo di toccare i tasti e le frasi abituali. Qui si chiede il suo zip per metterlo nel backup e, al ripristino, glielo
 * si riconsegna (`BackupProvider` della tastiera, solo tra app con la stessa firma).
 *
 * Se al ripristino la tastiera non c'è ancora, lo zip aspetta sul telefono e si consegna a un avvio successivo
 * dell'app Eurise, appena la tastiera è installata ([deliverPending]).
 */
object KeyboardBackup {
    const val ENTRY = "apps/keyboard.zip"
    private const val WAITING = "keyboard_restore.zip"

    private fun authority(context: Context): String {
        val debug = context.packageName.endsWith(".debug")
        return (if (debug) "eu.eurise.keyboard.debug" else "eu.eurise.keyboard") + ".eurise.backup"
    }

    private fun uri(context: Context, path: String) = Uri.parse("content://${authority(context)}/$path")

    /** Lo zip della tastiera, o null se non è installata (o è una versione che non lo sa fare). */
    fun export(context: Context): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri(context, "export"))?.use { it.readBytes() }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** Mette da parte lo zip del ripristino, da consegnare alla tastiera. */
    fun keep(context: Context, zip: File) {
        zip.copyTo(File(context.filesDir, WAITING), overwrite = true)
    }

    /** Consegna lo zip in attesa, se c'è e la tastiera risponde. Da chiamare fuori dal thread principale. */
    fun deliverPending(context: Context) {
        val waiting = File(context.filesDir, WAITING)
        if (!waiting.exists()) return
        val delivered = runCatching {
            context.contentResolver.openOutputStream(uri(context, "import"))?.use { out -> waiting.inputStream().use { it.copyTo(out) } }
                ?: return
            context.contentResolver.call(uri(context, ""), "apply", null, null)?.getBoolean("ok") == true
        }.getOrDefault(false)
        if (delivered) waiting.delete()
    }
}

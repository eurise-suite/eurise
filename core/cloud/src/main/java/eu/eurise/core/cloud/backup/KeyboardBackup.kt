package eu.eurise.core.cloud.backup

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Eurise Tastiera nel backup della suite: è un APK a parte e custodisce da sé le parole imparate, il dizionario
 * personale e il modo di toccare i tasti. Qui si chiede il suo zip per metterlo nel backup e, al ripristino, glielo
 * si riconsegna (`BackupProvider` della tastiera, solo tra app con la stessa firma).
 *
 * Se al ripristino la tastiera non c'è ancora, lo zip aspetta sul telefono, sigillato con una chiave del Keystore
 * ([LocalSeal], mai in chiaro su disco), e si consegna a un avvio successivo dell'app Eurise, appena la tastiera è
 * installata ([deliverPending]). Dalla Tastiera lo zip arriva e riparte in un tubo, senza file intermedi.
 */
object KeyboardBackup {
    const val ENTRY = "apps/keyboard.zip"
    private const val WAITING = "keyboard_restore.sealed"
    /** Dove aspettava, in chiaro, fino al 20 novembre 2026: lo si sigilla alla prima occasione. */
    private const val WAITING_OLD = "keyboard_restore.zip"

    private fun authority(context: Context): String {
        val debug = context.packageName.endsWith(".debug")
        return (if (debug) "eu.eurise.keyboard.debug" else "eu.eurise.keyboard") + ".eurise.backup"
    }

    private fun uri(context: Context, path: String) = Uri.parse("content://${authority(context)}/$path")

    /** Lo zip della tastiera, o null se non è installata (o è una versione che non lo sa fare). */
    fun export(context: Context): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri(context, "export"))?.use { it.readBytes() }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** Mette da parte, sigillato, lo zip del ripristino da consegnare alla tastiera. */
    fun keep(context: Context, zip: ByteArray) {
        val target = File(context.filesDir, WAITING)
        val tmp = File(context.filesDir, "$WAITING.tmp")
        tmp.writeBytes(LocalSeal.seal(zip))
        tmp.renameTo(target)
    }

    /** Le parole della Tastiera aspettano che la Tastiera sia installata. */
    fun waiting(context: Context): Boolean = File(context.filesDir, WAITING).exists() || File(context.filesDir, WAITING_OLD).exists()

    /** Consegna lo zip in attesa, se c'è e la tastiera risponde. Da chiamare fuori dal thread principale. */
    fun deliverPending(context: Context) {
        File(context.filesDir, WAITING_OLD).takeIf { it.exists() }?.let { old ->
            runCatching { keep(context, old.readBytes()) }
            old.delete()
        }
        val waiting = File(context.filesDir, WAITING)
        if (!waiting.exists()) return
        val zip = runCatching { LocalSeal.open(waiting.readBytes()) }.getOrElse {
            // Chiave del Keystore sparita (dati dell'app cancellati a metà): il file non si apre più, lo si toglie.
            waiting.delete(); return
        }
        val delivered = runCatching {
            context.contentResolver.openOutputStream(uri(context, "import"))?.use { it.write(zip) } ?: return
            context.contentResolver.call(uri(context, ""), "apply", null, null)?.getBoolean("ok") == true
        }.getOrDefault(false)
        if (delivered) waiting.delete()
    }
}

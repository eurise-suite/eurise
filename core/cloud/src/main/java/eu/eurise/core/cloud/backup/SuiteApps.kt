package eu.eurise.core.cloud.backup

import android.content.Context
import android.net.Uri
import eu.eurise.core.suite.SuiteAccess
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Le app separate della suite nel backup (area Backup, 21 novembre 2026). Ognuna custodisce da sé i suoi dati e li
 * consegna con il suo fornitore protetto dal permesso della suite (`SuiteBackupProvider`, per la Tastiera il suo
 * `BackupProvider`): lo zip arriva in un tubo e va dritto nel backup, che si cifra mentre lo si scrive.
 *
 * Al ripristino ogni zip aspetta sul telefono sigillato con una chiave del Keystore ([LocalSeal], mai in chiaro su
 * disco), e si consegna appena la sua app è installata ([deliverPending], a ogni avvio di Eurise e dopo il ripristino).
 * Prima di mandare o chiedere qualcosa si controlla che il fornitore sia davvero di un'app della suite
 * ([SuiteAccess.providerTrusted]): un'app estranea con lo stesso indirizzo non riceve niente.
 */
object SuiteApps {
    /**
     * [key]: il nome della voce nel backup; [pkg]: il pacchetto della versione ufficiale. [samePackage]: l'app ha lo
     * stesso pacchetto in prova e in ufficiale (Mappe, compilata a parte da CoMaps): l'indirizzo non prende «.debug», e
     * la variante giusta la garantisce il permesso di firma ([SuiteAccess.providerTrusted]).
     */
    class App(val key: String, val pkg: String, val samePackage: Boolean = false)

    /** In ordine di importanza: se il ripristino si interrompe, le prime sono già al loro posto. */
    val all = listOf(
        App("password", "eu.eurise.password"),
        App("messages", "eu.eurise.messages"),
        App("phone", "eu.eurise.phone"),
        App("gallery", "eu.eurise.gallery"),
        App("keyboard", "eu.eurise.keyboard"),
        App("browser", "eu.eurise.browser"),
        App("office", "eu.eurise.office"),
        App("mail", "eu.eurise.mail"),
        App("chat", "eu.eurise.chat"),
        App("voice", "eu.eurise.voice"),
        App("ai", "eu.eurise.ai"),
        App("maps", "eu.eurise.maps", samePackage = true),
    )

    fun entry(key: String) = "apps/$key.zip"

    /** La chiave di una voce `apps/<chiave>.zip`, se è di un'app conosciuta. */
    fun keyOf(entry: String): String? =
        entry.removePrefix("apps/").removeSuffix(".zip").takeIf { entry.startsWith("apps/") && entry.endsWith(".zip") && all.any { a -> a.key == it } }

    private fun authority(context: Context, app: App) =
        app.pkg + (if (SuiteAccess.isDebug(context) && !app.samePackage) ".debug" else "") + ".eurise.backup"

    private fun uri(context: Context, app: App, path: String) = Uri.parse("content://${authority(context, app)}/$path")

    /** Vero se l'app è installata, è della suite e sa fare il backup. */
    fun available(context: Context, app: App) = SuiteAccess.providerTrusted(context, authority(context, app))

    /** La riga per «Cosa rimetto», chiesta all'app. */
    fun summary(context: Context, app: App): String? = runCatching {
        SuiteAccess.call(context, uri(context, app, ""), "summary", null, null)?.getString("summary")
    }.getOrNull()

    /** Copia lo zip dell'app in [out]; falso se l'app non c'è o non ha risposto. */
    fun export(context: Context, app: App, out: OutputStream): Boolean {
        if (!available(context, app)) return false
        return runCatching {
            context.contentResolver.openInputStream(uri(context, app, "export"))?.use { it.copyTo(out) } != null
        }.getOrDefault(false)
    }

    private fun waitingDir(context: Context) = File(context.filesDir, "apps_waiting")
    private fun waitingFile(context: Context, key: String) = File(waitingDir(context), "$key.sealed")

    /** Mette da parte, sigillato, lo zip [input] da consegnare all'app [key]. */
    fun keep(context: Context, key: String, input: InputStream) {
        waitingDir(context).mkdirs()
        LocalSeal.sealTo(input, waitingFile(context, key))
    }

    /** Le app che aspettano ancora i loro dati. */
    fun waiting(context: Context): List<String> {
        legacyKeyboard(context)
        return waitingDir(context).listFiles().orEmpty().filter { it.name.endsWith(".sealed") }.map { it.name.removeSuffix(".sealed") }
    }

    /** Consegna i dati in attesa alle app installate. Da chiamare fuori dal thread principale. */
    @Synchronized
    fun deliverPending(context: Context) {
        for (key in waiting(context)) {
            val app = all.firstOrNull { it.key == key } ?: continue
            if (!available(context, app)) continue
            // Chi non sa rispondere a «ready» (la Tastiera) è sempre pronta.
            val ready = runCatching { SuiteAccess.call(context, uri(context, app, ""), "ready", null, null) }.getOrNull()
            if (ready != null && !ready.getBoolean("ready", true)) continue
            val file = waitingFile(context, key)
            val delivered = runCatching {
                val out = context.contentResolver.openOutputStream(uri(context, app, "import")) ?: return@runCatching false
                out.use { o -> LocalSeal.openFrom(file).use { it.copyTo(o) } }
                SuiteAccess.call(context, uri(context, app, ""), "apply", null, null)?.getBoolean("ok") == true
            }.getOrDefault(false)
            if (delivered) file.delete()
        }
    }

    /** Le parole della Tastiera messe da parte dalle versioni del 20 novembre (un solo blocco): si passano al formato nuovo. */
    private fun legacyKeyboard(context: Context) {
        val sealed = File(context.filesDir, "keyboard_restore.sealed")
        val plain = File(context.filesDir, "keyboard_restore.zip")
        if (sealed.exists()) {
            runCatching { keep(context, "keyboard", LocalSeal.open(sealed.readBytes()).inputStream()) }
            sealed.delete()
        }
        if (plain.exists()) {
            runCatching { plain.inputStream().use { keep(context, "keyboard", it) } }
            plain.delete()
        }
    }
}

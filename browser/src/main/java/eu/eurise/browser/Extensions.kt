package eu.eurise.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** Un'estensione consigliata (tavola BrwEstensioni): [slug] su addons.mozilla.org, [reads] cosa può leggere. */
data class Suggested(val slug: String, val name: String, val about: Int, val reads: Int)

/**
 * Estensioni di Firefox da addons.mozilla.org (GeckoView le supporta). Prima di installarne una si mostra cosa può
 * leggere ([pending]: la scheda dei permessi; si installa solo con «Aggiungi»); ognuna si spegne con un tocco. L'estensione
 * Eurise integrata non compare.
 */
object Extensions {
    private var runtime: GeckoRuntime? = null

    /** Le estensioni installate dall'utente. */
    val installed = mutableStateListOf<WebExtension>()

    /** Un'installazione che aspetta la risposta: nome, permessi e siti che l'estensione chiede. */
    class Pending(
        val extension: WebExtension,
        val permissions: List<String>,
        val origins: List<String>,
        val result: GeckoResult<WebExtension.PermissionPromptResponse>,
    )

    var pending by mutableStateOf<Pending?>(null)
        private set
    /** Un'installazione in corso (rotella sul pulsante) o finita male. */
    var busy by mutableStateOf<String?>(null)
        private set
    var failed by mutableStateOf(false)

    val SUGGESTED = listOf(
        Suggested("languagetool", "LanguageTool", R.string.brw_ext_lt, R.string.brw_ext_lt_reads),
        Suggested("clearurls", "ClearURLs", R.string.brw_ext_clearurls, R.string.brw_ext_clearurls_reads),
        Suggested("sponsorblock", "SponsorBlock", R.string.brw_ext_sponsor, R.string.brw_ext_sponsor_reads),
        Suggested("videospeed", "Video Speed Controller", R.string.brw_ext_speed, R.string.brw_ext_speed_reads),
    )

    fun attach(rt: GeckoRuntime) {
        runtime = rt
        rt.webExtensionController.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                pending?.result?.complete(WebExtension.PermissionPromptResponse(false, false, false))
                pending = Pending(extension, permissions.toList(), origins.toList(), result)
                return result
            }
        })
        refresh()
    }

    fun refresh() {
        runtime?.webExtensionController?.list()?.accept({ list ->
            installed.clear()
            installed.addAll(list.orEmpty().filter { !it.isBuiltIn })
        }, { })
    }

    /** Scarica e installa: il motore chiede prima i permessi ([pending]). */
    fun install(uri: String, slug: String? = null) {
        val rt = runtime ?: return
        busy = slug ?: uri
        failed = false
        rt.webExtensionController.install(uri).accept(
            { busy = null; refresh() },
            { busy = null; failed = true; refresh() },
        )
    }

    fun install(s: Suggested) = install("https://addons.mozilla.org/firefox/downloads/latest/${s.slug}/latest.xpi", s.slug)

    /** La risposta alla scheda dei permessi: «Aggiungi» o «Annulla». Le private restano escluse. */
    fun answer(allow: Boolean) {
        val p = pending ?: return
        pending = null
        p.result.complete(WebExtension.PermissionPromptResponse(allow, false, false))
    }

    fun setEnabled(ext: WebExtension, on: Boolean) {
        val c = runtime?.webExtensionController ?: return
        val r = if (on) c.enable(ext, WebExtensionController.EnableSource.USER) else c.disable(ext, WebExtensionController.EnableSource.USER)
        r.accept({ refresh() }, { refresh() })
    }

    fun uninstall(ext: WebExtension) {
        runtime?.webExtensionController?.uninstall(ext)?.accept({ refresh() }, { refresh() })
    }

    /** L'estensione con questo nome è già installata? (Per il pulsante «Aggiungi» dei consigli.) */
    fun has(name: String) = installed.any { it.metaData.name.equals(name, ignoreCase = true) }

    /** I permessi di un'estensione detti in parole (le più comuni); gli altri restano col loro nome. */
    fun describe(permissions: List<String>, origins: List<String>, words: (String) -> String?): List<String> {
        val all = origins.any { it == "<all_urls>" || it.startsWith("*://*/") || it == "http://*/*" || it == "https://*/*" }
        val sites = origins.filter { it != "<all_urls>" && !it.startsWith("*://*/") }.mapNotNull { o ->
            o.substringAfter("://").substringBefore('/').removePrefix("*.").takeIf { it.isNotBlank() && it != "*" }
        }.distinct()
        val out = mutableListOf<String>()
        if (all) words("all_sites")?.let(out::add)
        else if (sites.isNotEmpty()) words("sites")?.let { out += it.format(sites.take(4).joinToString(", ")) }
        permissions.distinct().forEach { p -> words(p)?.let(out::add) }
        return out
    }
}

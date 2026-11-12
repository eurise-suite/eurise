package eu.eurise.browser

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.TranslationsController
import org.mozilla.geckoview.WebExtension

/**
 * Il motore Gecko, uno per l'app: protezione dai tracciatori «rigorosa» (la stessa di Firefox), cookie di terze parti
 * isolati, l'estensione Eurise integrata. Niente telemetria: GeckoView da solo non ne manda.
 */
object Gecko {
    private const val TAG = "EuriseBrowser"
    private const val EXTENSION_ID = "eurise@eurise.eu"
    private const val EXTENSION_URL = "resource://android/assets/eurise/"

    @Volatile private var runtime: GeckoRuntime? = null

    /** L'estensione Eurise, quando GeckoView l'ha caricata. */
    var extension: WebExtension? = null
        private set
    private val waiting = mutableListOf<(WebExtension) -> Unit>()

    /** Tracciatori bloccati da quando l'app è aperta (la nuova scheda lo dice). */
    var blockedTotal by mutableIntStateOf(0)
    /** Chiamata a ogni tracciatore fermato (il conteggio della settimana sta nel modello). */
    var onBlocked: () -> Unit = {}

    /** La vista di Gecko sullo schermo (per «Cattura»). */
    var view: org.mozilla.geckoview.GeckoView? = null
    /** Le viste vive (due con le schede affiancate): una sessione sta in una vista sola alla volta. */
    val views = java.util.Collections.newSetFromMap(java.util.WeakHashMap<org.mozilla.geckoview.GeckoView, Boolean>())

    /** Le impostazioni da applicare al motore appena nasce (le fissa il modello prima della prima scheda). */
    var settings: BrowserSettings? = null

    /** Riapplica le impostazioni al motore già acceso (dopo un cambio nelle Impostazioni). */
    fun apply(context: Context) {
        val rt = runtime(context)
        settings?.applyTo(rt.settings)
    }

    fun runtime(context: Context): GeckoRuntime = runtime ?: synchronized(this) {
        runtime ?: GeckoRuntime.create(
            context.applicationContext,
            GeckoRuntimeSettings.Builder()
                .contentBlocking(
                    ContentBlocking.Settings.Builder()
                        .antiTracking(ContentBlocking.AntiTracking.STRICT)
                        .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                        .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                        .build(),
                )
                .aboutConfigEnabled(false)
                .consoleOutput(false)
                .loginAutofillEnabled(true)
                .build(),
        ).also { rt ->
            runtime = rt
            // Gli accessi ai siti vengono da Eurise Password (Logins).
            rt.autocompleteStorageDelegate = Logins
            // Estensioni di Firefox scelte dall'utente (tavola BrwEstensioni): prima di installarne una, i permessi.
            Extensions.attach(rt)
            settings?.applyTo(rt.settings)
            rt.webExtensionController.ensureBuiltIn(EXTENSION_URL, EXTENSION_ID).accept(
                { ext -> if (ext != null) synchronized(waiting) { extension = ext; waiting.forEach { it(ext) }; waiting.clear() } },
                { e -> Log.w(TAG, "Estensione Eurise non caricata", e) },
            )
        }
    }

    /** Chiama [block] con l'estensione appena è pronta (subito, se lo è già). */
    fun withExtension(block: (WebExtension) -> Unit) {
        val ext = synchronized(waiting) { extension ?: run { waiting += block; null } }
        if (ext != null) block(ext)
    }
}

/**
 * Una scheda: la sessione Gecko e ciò che l'interfaccia mostra (indirizzo, titolo, avanzamento, tracciatori bloccati).
 * Le schede private non lasciano cronologia né cookie dopo la chiusura.
 */
class Tab(
    context: Context,
    val id: Long,
    val private: Boolean,
    onNewTab: (String) -> Unit,
    /** La cronologia (null nelle schede private: lì non si annota nulla). */
    private val history: HistoryStore? = null,
    /** Pagine scure accese? Lo chiede l'estensione a ogni pagina. */
    private val dark: () -> Boolean = { false },
    /** Immagini caricate? (Strumenti › Immagini sì/no: senza, le pagine pesano meno). */
    private val images: () -> Boolean = { true },
) {
    val session: GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder().usePrivateMode(private).allowJavascript(true).build(),
    )

    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var secure by mutableStateOf(true)
    var blocked by mutableIntStateOf(0)
    var fullscreen by mutableStateOf(false)
    /** Il permesso «protezione dai tracciatori» di questo sito, per lo sblocco rapido (BrwSblocca). */
    var trackingPermission: ContentPermission? = null
        private set
    var protectionOn by mutableStateOf(true)
    /** Bolla «Accedi come…» (più accessi per il sito) e bolla «Salvo l'accesso?»: le risposte vanno a Gecko. */
    var loginChoice by mutableStateOf<LoginChoice?>(null)
    var loginSave by mutableStateOf<LoginSave?>(null)
    /** Un file da scaricare (risposta che Gecko non mostra): lo passa al gestore dei download. */
    var onDownload: (String) -> Unit = {}
    /** «Sito PC»: la versione per il computer di questo sito. */
    var desktop by mutableStateOf(false)
        private set
    /** La lingua della pagina riconosciuta dal motore (per «Traduci») e se la pagina è tradotta. */
    var pageLanguage by mutableStateOf<String?>(null)
        private set
    var translated by mutableStateOf(false)
        private set
    /** Risultati di «Trova nella pagina»: posizione e totale. */
    var findCurrent by mutableIntStateOf(0)
    var findTotal by mutableIntStateOf(0)
    /** Ultima volta che la scheda era in vista (per chiudere da sole quelle dimenticate). */
    var lastActive: Long = System.currentTimeMillis()
    /** Il gruppo colorato della scheda (tavola BrwSessioni), o nessuno. */
    var group by mutableStateOf<String?>(null)
    /** Miniatura della pagina per la griglia delle schede (solo in memoria). */
    var thumb by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    /** Indirizzo da caricare quando la scheda torna in vista (schede riaperte all'avvio). */
    var pending: String? = null

    private var port: WebExtension.Port? = null
    private var pageRequest: ((PageInfo?) -> Unit)? = null

    init {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                this@Tab.url = url.orEmpty()
                trackingPermission = perms.firstOrNull { it.permission == GeckoSession.PermissionDelegate.PERMISSION_TRACKING }
                protectionOn = trackingPermission?.value != ContentPermission.VALUE_ALLOW
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { canBack = canGoBack }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { canForward = canGoForward }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                onNewTab(uri)
                return null
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                progress = 5; blocked = 0; this@Tab.url = url
                loginChoice?.dismiss(); loginChoice = null
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) { progress = 100 }
            override fun onProgressChange(session: GeckoSession, progress: Int) { this@Tab.progress = progress }
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                secure = securityInfo.isSecure
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                this@Tab.title = title.orEmpty()
                if (title != null && url.startsWith("http")) history?.let { h -> Thread { h.title(url, title) }.start() }
            }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) { fullscreen = fullScreen }
            override fun onExternalResponse(session: GeckoSession, response: org.mozilla.geckoview.WebResponse) {
                // Un'estensione di Firefox (pulsante «Aggiungi» su addons.mozilla.org): la installa il motore, che prima
                // chiede il permesso con la scheda dei permessi (Extensions.prompt).
                val type = response.headers["Content-Type"] ?: response.headers["content-type"]
                if (type?.startsWith("application/x-xpinstall") == true || response.uri.substringBefore('?').endsWith(".xpi")) {
                    Extensions.install(response.uri)
                    return
                }
                onDownload(response.uri)
            }
        }
        session.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int): GeckoResult<Boolean>? {
                val h = history ?: return null
                val skip = GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE or GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT or
                    GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR
                if ((flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL) == 0 || (flags and skip) != 0) return GeckoResult.fromValue(false)
                val t = title
                Thread { h.add(url, t) }.start()
                return GeckoResult.fromValue(true)
            }
        }
        session.setTranslationsSessionDelegate(object : TranslationsController.SessionTranslation.Delegate {
            override fun onTranslationStateChange(session: GeckoSession, translationState: TranslationsController.SessionTranslation.TranslationState?) {
                translationState?.detectedLanguages?.docLangTag?.let { pageLanguage = it }
                translated = translationState?.requestedTranslationPair != null
            }
        })
        // Accessi ai siti: quale accesso usare e se salvarne uno nuovo (bolle sopra la barra; Logins per i dati).
        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onLoginSelect(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption>,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                loginChoice?.dismiss()
                loginChoice = LoginChoice(request.options.toList(), request, result)
                return result
            }

            override fun onLoginSave(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                // Nelle schede private non si salva nulla.
                if (private) return GeckoResult.fromValue(request.dismiss())
                val option = request.options.firstOrNull() ?: return GeckoResult.fromValue(request.dismiss())
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                loginSave?.dismiss()
                loginSave = LoginSave(option.value, update = option.value.guid != null, request = request, result = result)
                return result
            }
        }
        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                blocked++
                Gecko.blockedTotal++
                Gecko.onBlocked()
            }
        }
        session.open(Gecko.runtime(context))
        Gecko.withExtension { ext ->
            session.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    this@Tab.port = port
                    runCatching { port.postMessage(JSONObject().put("type", "dark").put("on", dark())) }
                    runCatching { port.postMessage(JSONObject().put("type", "images").put("on", images())) }
                    port.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, port: WebExtension.Port) {
                            val o = message as? JSONObject ?: return
                            if (o.optString("type") == "page") {
                                pageRequest?.invoke(PageInfo.from(o))
                                pageRequest = null
                            }
                        }

                        override fun onDisconnect(port: WebExtension.Port) {
                            if (this@Tab.port === port) this@Tab.port = null
                        }
                    })
                }
            }, "browser")
        }
    }

    /** Titolo, selezione e testo della pagina, letti dall'estensione Eurise solo quando servono (In Note, Ascolta). */
    fun page(callback: (PageInfo?) -> Unit) {
        val p = port
        if (p == null) { callback(PageInfo(url, title, "", "", "")); return }
        pageRequest = callback
        runCatching { p.postMessage(JSONObject().put("type", "page")) }.onFailure { pageRequest = null; callback(PageInfo(url, title, "", "", "")) }
    }

    fun load(address: String) = session.loadUri(address)

    /** Pagine scure: l'estensione scurisce la pagina che non ha un suo tema scuro (o la riporta com'era). */
    fun setDark(on: Boolean) {
        runCatching { port?.postMessage(JSONObject().put("type", "dark").put("on", on)) }
    }

    /** Immagini sì/no: lo dice all'estensione, che le blocca prima che si scarichino. */
    fun setImages(on: Boolean) {
        runCatching { port?.postMessage(JSONObject().put("type", "images").put("on", on)) }
    }

    /** «Sito PC»: chiede la versione per il computer (o torna a quella per il telefono) e ricarica. */
    @JvmName("changeDesktop") fun setDesktop(on: Boolean) {
        desktop = on
        session.settings.setUserAgentMode(if (on) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        session.settings.setViewportMode(if (on) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        session.reload()
    }

    /** Trova nella pagina; vuoto toglie l'evidenziazione. */
    fun find(text: String, backwards: Boolean = false) {
        if (text.isEmpty()) { session.finder.clear(); findCurrent = 0; findTotal = 0; return }
        session.finder.find(text, if (backwards) GeckoSession.FINDER_FIND_BACKWARDS else 0).accept { r ->
            if (r != null) { findCurrent = r.current; findTotal = r.total }
        }
    }

    fun stopFind() { session.finder.clear(); findCurrent = 0; findTotal = 0 }

    /**
     * Traduce la pagina sul telefono (Firefox Translations: il modello della lingua si scarica una volta da Mozilla, il
     * testo della pagina non esce). Se è già tradotta, torna all'originale. [onError] riceve il motivo.
     */
    fun translate(to: String, onError: (String) -> Unit) {
        val t = session.sessionTranslation ?: return onError("engine")
        if (translated) { t.restoreOriginalPage(); return }
        val from = pageLanguage ?: return onError("language")
        if (from.substringBefore('-') == to) return onError("same")
        t.translate(from, to, null).accept({ }, { e -> onError(e?.message ?: "error") })
    }

    fun close() = runCatching { session.close() }
}

/** Ciò che l'estensione dice della pagina. */
data class PageInfo(val url: String, val title: String, val selection: String, val description: String, val text: String) {
    companion object {
        fun from(o: JSONObject) = PageInfo(
            o.optString("url"), o.optString("title"), o.optString("selection"), o.optString("description"), o.optString("text"),
        )
    }
}

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
                .build(),
        ).also { rt ->
            runtime = rt
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
class Tab(context: Context, val id: Long, val private: Boolean, onNewTab: (String) -> Unit) {
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
    /** Un file da scaricare (risposta che Gecko non mostra): lo passa al gestore dei download. */
    var onDownload: (String) -> Unit = {}

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
            override fun onPageStart(session: GeckoSession, url: String) { progress = 5; blocked = 0; this@Tab.url = url }
            override fun onPageStop(session: GeckoSession, success: Boolean) { progress = 100 }
            override fun onProgressChange(session: GeckoSession, progress: Int) { this@Tab.progress = progress }
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                secure = securityInfo.isSecure
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) { this@Tab.title = title.orEmpty() }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) { fullscreen = fullScreen }
            override fun onExternalResponse(session: GeckoSession, response: org.mozilla.geckoview.WebResponse) {
                onDownload(response.uri)
            }
        }
        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                blocked++
                Gecko.blockedTotal++
            }
        }
        session.open(Gecko.runtime(context))
        Gecko.withExtension { ext ->
            session.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    this@Tab.port = port
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

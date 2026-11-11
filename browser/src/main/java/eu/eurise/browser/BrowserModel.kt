package eu.eurise.browser

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.eurise.core.suite.HubBridge
import java.io.File
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission

/** Lo stato del Browser: schede, scheda in vista, segnalibri e siti fissati, motore di ricerca. */
class BrowserModel(private val context: Context) {
    private val prefs = context.getSharedPreferences("browser", Context.MODE_PRIVATE)
    private val store = BookmarkStore(File(context.filesDir, "bookmarks.json"))
    private var nextId = 1L
    val settings = BrowserSettings(context).also { Gecko.settings = it }
    val history = HistoryStore(File(context.filesDir, "history.txt"))

    val tabs = mutableStateListOf<Tab>()
    /** I file scelti nella schermata Importa, già letti. */
    val picked = mutableStateListOf<PickedFile>()
    /** Un avviso breve da mostrare (lo mostra l'interfaccia e lo azzera). */
    var notice by mutableStateOf<String?>(null)
    /** Download partiti in questa sessione (il numero nel menu). */
    var downloads by mutableIntStateOf(0)
    /** Dove vanno i download di tutte le schede (lo fissa l'attività). */
    var onDownload: (String) -> Unit = {}
    var current by mutableStateOf<Tab?>(null)
        private set
    val bookmarks = mutableStateListOf<Bookmark>().apply { addAll(store.load()) }
    var engine by mutableStateOf(Links.engine(prefs.getString("engine", null)))
        private set

    fun chooseEngine(e: Links.Engine) {
        engine = e
        prefs.edit().putString("engine", e.id).apply()
    }

    /** Una scheda nuova (vuota mostra la nuova scheda di Eurise) e la mette in vista. */
    fun open(address: String? = null, private: Boolean = false): Tab {
        val tab = newTab(private)
        tabs += tab
        show(tab)
        if (!address.isNullOrBlank()) tab.load(resolve(address))
        return tab
    }

    private fun newTab(private: Boolean): Tab {
        val tab = Tab(context, nextId++, private, onNewTab = { uri -> open(uri, private) },
            history = if (private) null else history, dark = { settings.darkPages })
        tab.onDownload = { onDownload(it) }
        return tab
    }

    /** Indirizzo, ricerca, o «? domanda» all'AI scelta nelle Impostazioni. */
    private fun resolve(input: String): String {
        val t = input.trim()
        val ai = settings.aiService
        if (t.startsWith("?") && t.length > 1 && ai != null) {
            return ai.url.format(java.net.URLEncoder.encode(t.drop(1).trim(), "UTF-8").replace("+", "%20"))
        }
        return Links.resolve(t, engine)
    }

    fun show(tab: Tab) {
        current?.lastActive = System.currentTimeMillis()
        current = tab
        tab.lastActive = System.currentTimeMillis()
        // Le schede riaperte all'avvio si caricano solo quando le guardi.
        tab.pending?.let { tab.pending = null; tab.load(it) }
    }

    /** Scheda accanto (gesto sulla barra): [step] +1 a destra, -1 a sinistra. */
    fun neighbour(step: Int) {
        val i = tabs.indexOf(current ?: return)
        tabs.getOrNull(i + step)?.let(::show)
    }

    // --- Schede all'avvio (Impostazioni › Schede) ---

    /** Ricorda le schede normali per riaprirle (le private no). */
    fun saveTabs() {
        val a = org.json.JSONArray()
        tabs.filter { !it.private && (it.url.startsWith("http") || it.pending != null) }.forEach { t ->
            a.put(org.json.JSONObject().put("url", t.pending ?: t.url).put("title", t.title).put("last", t.lastActive))
        }
        prefs.edit().putString("tabs", a.toString()).putLong("current", current?.id ?: -1).apply()
    }

    /** Riapre le schede dell'ultima volta, tranne quelle non guardate da più di N giorni; restituisce quante. */
    fun restoreTabs(): Int {
        if (!settings.restoreTabs) return 0
        val a = runCatching { org.json.JSONArray(prefs.getString("tabs", "[]")) }.getOrNull() ?: return 0
        val limit = settings.closeAfterDays.takeIf { it > 0 }?.let { System.currentTimeMillis() - it * 86_400_000L } ?: 0L
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val last = o.optLong("last")
            if (last < limit) continue
            val tab = newTab(private = false)
            tab.pending = o.optString("url")
            tab.url = o.optString("url")
            tab.title = o.optString("title")
            tab.lastActive = last
            tabs += tab
        }
        tabs.lastOrNull()?.let(::show)
        return tabs.size
    }

    fun close(tab: Tab) {
        val i = tabs.indexOf(tab)
        tabs.remove(tab)
        tab.close()
        if (current === tab) current = tabs.getOrNull(i.coerceAtMost(tabs.size - 1))
        if (tabs.isEmpty()) open()
    }

    /** Chiude tutte le schede private (pulsante nel foglio delle schede). */
    fun closePrivate() = tabs.filter { it.private }.forEach(::close)

    fun go(input: String) {
        val tab = current ?: open()
        tab.pending = null
        tab.load(resolve(input))
    }

    // --- Menu e Impostazioni (tavole BrwMenu, BrwImpostazioni) ---

    /** Riapplica al motore le impostazioni appena cambiate. */
    fun applySettings() = Gecko.apply(context)

    fun setDarkPages(on: Boolean) {
        settings.setDarkPages(on)
        tabs.forEach { it.setDark(on) }
    }

    fun setTextScale(v: Float) {
        settings.setTextScale(v)
        applySettings()
    }

    /** Segnalibri nel file che gli altri browser sanno importare. */
    fun exportBookmarks(): String = BookmarksExport.html(bookmarks.toList())

    /** Cosa cancellare (Impostazioni › Cancella dati). [since]: dal momento indicato (0 = tutto) per la cronologia. */
    fun clearData(history: Boolean, cookies: Boolean, cache: Boolean, permissions: Boolean, since: Long, done: () -> Unit) {
        var flags = 0L
        if (cookies) flags = flags or org.mozilla.geckoview.StorageController.ClearFlags.COOKIES or
            org.mozilla.geckoview.StorageController.ClearFlags.SITE_DATA or org.mozilla.geckoview.StorageController.ClearFlags.AUTH_SESSIONS
        if (cache) flags = flags or org.mozilla.geckoview.StorageController.ClearFlags.ALL_CACHES
        if (permissions) flags = flags or org.mozilla.geckoview.StorageController.ClearFlags.PERMISSIONS
        if (history) Thread { this.history.clear(since) }.start()
        if (flags == 0L) { done(); return }
        Gecko.runtime(context).storageController.clearData(flags).accept({ done() }, { done() })
    }

    /** I permessi dati ai siti (posizione, notifiche, fotocamera, protezione spenta…), per Impostazioni › Siti. */
    fun sitePermissions(callback: (List<ContentPermission>) -> Unit) {
        Gecko.runtime(context).storageController.allPermissions.accept(
            { list -> callback(list.orEmpty().filter { it.value != ContentPermission.VALUE_PROMPT && !it.privateMode }) },
            { callback(emptyList()) },
        )
    }

    /** Toglie un permesso: il sito lo chiederà di nuovo. */
    fun forget(permission: ContentPermission) {
        Gecko.runtime(context).storageController.setPermission(permission, ContentPermission.VALUE_PROMPT)
    }

    fun isPinned(url: String) = bookmarks.any { it.url == url && it.pinned }

    /** Fissa o toglie il sito tra quelli della nuova scheda. */
    fun togglePin(tab: Tab) {
        if (tab.url.isBlank() || tab.url == "about:blank") return
        val i = bookmarks.indexOfFirst { it.url == tab.url }
        if (i >= 0) bookmarks[i] = bookmarks[i].copy(pinned = !bookmarks[i].pinned)
        else bookmarks += Bookmark(tab.title.ifBlank { Links.host(tab.url) }, tab.url, pinned = true)
        store.save(bookmarks.toList())
    }

    fun removeBookmark(b: Bookmark) {
        bookmarks.remove(b)
        store.save(bookmarks.toList())
    }

    /** Segnalibri da un file esportato da un altro browser; restituisce quanti sono nuovi. */
    fun importBookmarks(html: String): Int {
        val (all, added) = store.merge(bookmarks.toList(), BookmarksHtml.parse(html))
        bookmarks.clear(); bookmarks.addAll(all)
        store.save(all)
        return added
    }

    /** Protezione dai tracciatori accesa o spenta per il sito della scheda (BrwSblocca); vale finché non la riaccendi. */
    fun setProtection(tab: Tab, on: Boolean) {
        val perm = tab.trackingPermission ?: return
        Gecko.runtime(context).storageController.setPermission(perm, if (on) ContentPermission.VALUE_DENY else ContentPermission.VALUE_ALLOW)
        tab.protectionOn = on
        tab.session.reload()
    }

    /** In Note o Da fare nell'app Eurise (permesso della suite): titolo, link pulito e, se c'è, la frase scelta. */
    fun toSuite(tab: Tab, task: Boolean, onDone: (Boolean) -> Unit) {
        tab.page { info ->
            val title = (info?.title ?: tab.title).ifBlank { Links.host(tab.url) }
            val link = Links.clean(info?.url?.ifBlank { null } ?: tab.url)
            val chosen = info?.selection.orEmpty()
            val text = if (task) "$title $link" else buildString {
                append(title).append('\n')
                if (chosen.isNotBlank()) append("«").append(chosen).append("»\n")
                else if (!info?.description.isNullOrBlank()) append(info!!.description).append('\n')
                append(link)
            }
            onDone(HubBridge.send(context, BuildConfig.HUB_PACKAGE, BuildConfig.SUITE_PERMISSION,
                if (task) HubBridge.ACTION_TASK else HubBridge.ACTION_NOTE, text))
        }
    }

    /** Condividi il link pulito, con il titolo (BrwCondividi). */
    fun share(tab: Tab) {
        val link = Links.clean(tab.url)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, link).putExtra(Intent.EXTRA_SUBJECT, tab.title)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

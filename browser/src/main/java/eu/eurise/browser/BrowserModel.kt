package eu.eurise.browser

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
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
    private val sessionStore = SessionStore(File(context.filesDir, "sessions.json"))
    private val laterStore = LaterStore(File(context.filesDir, "later.json"))
    val siteNotes = SiteNotes(File(context.filesDir, "site_notes.json"))

    /** Gruppi colorati, sessioni salvate, schede chiuse di recente (tavola BrwSessioni). */
    val groups = mutableStateListOf<TabGroup>()
    val sessions = mutableStateListOf<Session>()
    val closed = mutableStateListOf<SavedTab>()
    /** «Leggi dopo» (tavola BrwStrumenti). */
    val later = mutableStateListOf<LaterItem>()

    val tabs = mutableStateListOf<Tab>()
    /** I file scelti nella schermata Importa, già letti. */
    val picked = mutableStateListOf<PickedFile>()
    /** Arrivati file condivisi da un altro browser: la schermata apre Importa. */
    var importRequested by mutableStateOf(false)
    /** Un avviso breve da mostrare (lo mostra l'interfaccia e lo azzera). */
    var notice by mutableStateOf<String?>(null)
    /** Download partiti in questa sessione (il numero nel menu). */
    var downloads by mutableIntStateOf(0)
    /** Dove vanno i download di tutte le schede (lo fissa l'attività). */
    var onDownload: (String) -> Unit = {}
    var current by mutableStateOf<Tab?>(null)
        private set
    /** La schermata delle schede è aperta; [tabsSearch]: con la ricerca (Ctrl+K). */
    var tabsOpen by mutableStateOf(false)
    var tabsSearch by mutableStateOf(false)
    val bookmarks = mutableStateListOf<Bookmark>().apply { addAll(store.load()) }
    var engine by mutableStateOf(Links.engine(prefs.getString("engine", null)))
        private set

    init {
        sessionStore.load().let { d -> groups.addAll(d.groups); sessions.addAll(d.sessions); closed.addAll(d.closed) }
        later.addAll(laterStore.load())
    }

    private fun saveSessions() {
        val d = SessionStore.Data(groups.toList(), sessions.toList(), closed.toList())
        Thread { sessionStore.save(d) }.start()
    }

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
        val tab = Tab(context, nextId++, private, onNewTab = { uri -> val g = current?.group; open(uri, private).group = g },
            history = if (private) null else history, dark = { settings.darkPages }, images = { settings.images })
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

    /**
     * Sul tablet, due schede affiancate (tavola BrwSessioni): la coppia resta ferma nei due riquadri, [current] è quella
     * che usi (tocchi un riquadro per passarci). Se apri un'altra scheda prende il posto di quella che usavi.
     */
    var split by mutableStateOf<Pair<Tab, Tab>?>(null)
        private set

    /** Affianca [tab] alla scheda che stai usando. */
    fun splitWith(tab: Tab) {
        val c = current ?: return
        if (c === tab) return
        split = c to tab
        tab.pending?.let { tab.pending = null; tab.load(it) }
    }

    fun unsplit() { split = null }

    fun show(tab: Tab) {
        split?.let { (a, b) ->
            val prev = current
            if (tab !== a && tab !== b) split = if (prev === b) a to tab else tab to b
        }
        current?.takeIf { it !== tab }?.let(::snapshot)
        current?.lastActive = System.currentTimeMillis()
        current = tab
        tab.lastActive = System.currentTimeMillis()
        // Le schede riaperte all'avvio si caricano solo quando le guardi.
        tab.pending?.let { tab.pending = null; tab.load(it) }
    }

    /** Scheda accanto (gesto sulla barra, Ctrl+Tab): [step] +1 a destra, -1 a sinistra. Falso se non c'è. */
    fun neighbour(step: Int): Boolean {
        val i = tabs.indexOf(current ?: return false)
        val next = tabs.getOrNull(i + step) ?: return false
        show(next)
        return true
    }

    /** Una miniatura piccola della pagina in vista, per la griglia delle schede (resta in memoria, mai su disco). */
    fun snapshot(tab: Tab) {
        if (!tab.url.startsWith("http")) return
        val view = Gecko.view ?: return
        if (view.session !== tab.session) return
        runCatching {
            view.capturePixels().accept({ bmp ->
                if (bmp == null || bmp.width == 0) return@accept
                val w = 270
                val small = android.graphics.Bitmap.createScaledBitmap(bmp, w, (bmp.height * w / bmp.width).coerceAtLeast(1), true)
                if (small !== bmp) bmp.recycle()
                // Basta la parte alta della pagina: è quella che si riconosce.
                val crop = if (small.height > 360) android.graphics.Bitmap.createBitmap(small, 0, 0, w, 360).also { small.recycle() } else small
                tab.thumb = crop.asImageBitmap()
            }, { })
        }
    }

    /** Sposta una scheda di un posto (barra delle schede: tieni premuto › Sposta). */
    fun move(tab: Tab, step: Int) {
        val i = tabs.indexOf(tab)
        val j = i + step
        if (i < 0 || j !in tabs.indices) return
        tabs.removeAt(i)
        tabs.add(j, tab)
        saveTabs()
    }

    // --- Gruppi e sessioni (tavola BrwSessioni) ---

    fun group(name: String?) = groups.firstOrNull { it.name == name }

    /** Mette la scheda in un gruppo (nuovo se il nome non c'è ancora); null la toglie dal gruppo. */
    fun setGroup(tab: Tab, name: String?) {
        val clean = name?.trim()?.takeIf { it.isNotBlank() }
        if (clean != null && group(clean) == null) groups += TabGroup(clean, SessionStore.nextTint(groups))
        tab.group = clean
        dropEmptyGroups()
        saveSessions()
    }

    private fun dropEmptyGroups() {
        groups.removeAll { g -> tabs.none { it.group == g.name } }
    }

    /** Salva le schede (di un gruppo o tutte) come sessione da riaprire. */
    fun saveSession(name: String, list: List<Tab>) {
        val saved = list.filter { !it.private }.mapNotNull { t ->
            val url = t.pending ?: t.url
            if (url.startsWith("http")) SavedTab(url, t.title, t.lastActive) else null
        }
        if (saved.isEmpty()) return
        val updated = SessionStore.upsert(sessions.toList(), Session(name.trim(), saved, System.currentTimeMillis()))
        sessions.clear(); sessions.addAll(updated)
        saveSessions()
    }

    /** Riapre una sessione in un gruppo con il suo nome; le pagine si caricano quando le guardi. */
    fun openSession(s: Session) {
        if (group(s.name) == null) groups += TabGroup(s.name, SessionStore.nextTint(groups))
        s.tabs.forEach { saved ->
            val tab = newTab(private = false)
            tab.pending = saved.url; tab.url = saved.url; tab.title = saved.title; tab.group = s.name
            tabs += tab
        }
        tabs.lastOrNull { it.group == s.name }?.let(::show)
        saveSessions()
    }

    fun removeSession(s: Session) {
        sessions.remove(s)
        saveSessions()
    }

    /** Riapre una scheda chiusa di recente e la toglie dall'elenco. */
    fun reopen(saved: SavedTab) {
        closed.remove(saved)
        open(saved.url)
        saveSessions()
    }

    /** Chiude tutte le schede indicate (un gruppo, o tutte); finiscono in «Chiuse di recente». */
    fun closeAll(list: List<Tab>) {
        list.toList().forEach { close(it) }
    }

    // --- Leggi dopo (tavola BrwStrumenti) ---

    fun isLater(url: String) = later.any { it.url == url }

    /** Salva l'articolo con il suo testo (si legge anche senza rete); di nuovo lo toglie. Restituisce se ora c'è. */
    fun toggleLater(info: PageInfo): Boolean {
        val url = info.url.ifBlank { return false }
        val now = if (isLater(url)) { later.removeAll { it.url == url }; false } else {
            val updated = LaterStore.add(later.toList(), LaterItem(url, info.title, info.text, System.currentTimeMillis()))
            later.clear(); later.addAll(updated); true
        }
        val copy = later.toList()
        Thread { laterStore.save(copy) }.start()
        return now
    }

    fun removeLater(item: LaterItem) {
        later.remove(item)
        val copy = later.toList()
        Thread { laterStore.save(copy) }.start()
    }

    fun setImages(on: Boolean) {
        settings.setImages(on)
        tabs.forEach { it.setImages(on) }
        current?.session?.reload()
    }

    // --- Schede all'avvio (Impostazioni › Schede) ---

    /** Ricorda le schede normali per riaprirle (le private no). */
    fun saveTabs() {
        val a = org.json.JSONArray()
        tabs.filter { !it.private && (it.url.startsWith("http") || it.pending != null) }.forEach { t ->
            a.put(org.json.JSONObject().put("url", t.pending ?: t.url).put("title", t.title).put("last", t.lastActive).put("group", t.group ?: ""))
        }
        prefs.edit().putString("tabs", a.toString()).putLong("current", current?.id ?: -1).apply()
    }

    /**
     * Riapre le schede dell'ultima volta. Quelle non guardate da più di N giorni non si riaprono ma vanno in «Chiuse di
     * recente»: non si perdono (tavola BrwSessioni). Restituisce quante schede sono aperte.
     */
    fun restoreTabs(): Int {
        if (!settings.restoreTabs) return 0
        val a = runCatching { org.json.JSONArray(prefs.getString("tabs", "[]")) }.getOrNull() ?: return 0
        val limit = settings.closeAfterDays.takeIf { it > 0 }?.let { System.currentTimeMillis() - it * 86_400_000L } ?: 0L
        val forgotten = mutableListOf<SavedTab>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val last = o.optLong("last")
            if (last < limit) { forgotten += SavedTab(o.optString("url"), o.optString("title"), last); continue }
            val tab = newTab(private = false)
            tab.pending = o.optString("url")
            tab.url = o.optString("url")
            tab.title = o.optString("title")
            tab.group = o.optString("group").takeIf { it.isNotBlank() && group(it) != null }
            tab.lastActive = last
            tabs += tab
        }
        if (forgotten.isNotEmpty()) {
            val updated = SessionStore.remember(closed.toList(), forgotten)
            closed.clear(); closed.addAll(updated)
        }
        dropEmptyGroups()
        saveSessions()
        tabs.lastOrNull()?.let(::show)
        return tabs.size
    }

    fun close(tab: Tab) {
        if (!tab.private) {
            val url = tab.pending ?: tab.url
            val updated = SessionStore.remember(closed.toList(), listOf(SavedTab(url, tab.title, System.currentTimeMillis())))
            if (updated != closed.toList()) { closed.clear(); closed.addAll(updated) }
        }
        val i = tabs.indexOf(tab)
        split?.let { (a, b) -> if (tab === a || tab === b) split = null }
        tabs.remove(tab)
        tab.close()
        if (current === tab) current = tabs.getOrNull(i.coerceAtMost(tabs.size - 1))
        if (tabs.isEmpty()) open()
        dropEmptyGroups()
        saveSessions()
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

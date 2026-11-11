package eu.eurise.browser

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

    val tabs = mutableStateListOf<Tab>()
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
        val tab = Tab(context, nextId++, private) { uri -> open(uri, private) }
        tab.onDownload = { onDownload(it) }
        tabs += tab
        current = tab
        if (!address.isNullOrBlank()) tab.load(Links.resolve(address, engine))
        return tab
    }

    fun show(tab: Tab) { current = tab }

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
        tab.load(Links.resolve(input, engine))
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

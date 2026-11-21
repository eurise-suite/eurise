package eu.eurise.browser

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Telefono e computer (tavola BrwSincro, 21 novembre 2026). Il computer (Firefox con Eurise) manda attraverso il Ponte il
 * suo stato; l'app Eurise, che tiene il Ponte, lo passa qui ([SyncProvider]); il Browser unisce ([SyncMerge]) e risponde.
 * Niente server: passa solo tra il telefono e i computer abbinati, cifrato dal Ponte.
 *
 * Ogni cosa ha il suo interruttore, **spento di base** (CLAUDE.md, «Spenti di base»): schede aperte; segnalibri, siti
 * fissati e Leggi dopo. Le schede private non escono mai; di Leggi dopo esce solo indirizzo e titolo (il testo resta qui).
 */
object BrowserSync {
    private const val PREFS = "browser-sync"

    /** Cresce a ogni sincronizzazione: il modello ricarica segnalibri e Leggi dopo, le schede mostrano quelle del computer. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes

    data class Computer(val id: String, val name: String, val at: Long, val tabs: List<SyncItem>)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun dir(context: Context) = File(context.filesDir, "sync").apply { mkdirs() }

    fun tabsOn(context: Context) = prefs(context).getBoolean("tabs", false)
    fun bookmarksOn(context: Context) = prefs(context).getBoolean("bm", false)
    fun setTabs(context: Context, on: Boolean) = prefs(context).edit().putBoolean("tabs", on).apply()
    fun setBookmarks(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("bm", on).apply()
        // Spento, si dimentica lo stato comune: alla riaccensione si riparte da un'unione (non si cancella niente).
        if (!on) dir(context).listFiles { f -> f.name.startsWith("base-") }?.forEach { it.delete() }
    }

    /** I computer che si sono sincronizzati, il più recente prima, con le loro schede aperte (se le schede sono accese). */
    fun computers(context: Context): List<Computer> =
        dir(context).listFiles { f -> f.name.startsWith("pc-") }.orEmpty().mapNotNull { f ->
            runCatching {
                val o = JSONObject(f.readText())
                Computer(o.getString("id"), o.optString("name"), o.optLong("at"), SyncMerge.decode(o.optString("tabs")))
            }.getOrNull()
        }.sortedByDescending { it.at }

    /**
     * Una richiesta del computer [computerId]. [fields] sono i campi del messaggio [eu.eurise.core.ponte.Kind.SyncAsk];
     * restituisce quelli della risposta. Le parti spente qui non escono e non entrano, qualunque cosa mandi il computer.
     */
    @Synchronized
    fun handle(context: Context, computerId: String, computerName: String, fields: Map<String, String>): Map<String, String> {
        val tabsOn = tabsOn(context)
        val bmOn = bookmarksOn(context)
        if (!tabsOn && !bmOn) return mapOf("off" to "1")
        val id = computerId.filter { it.isLetterOrDigit() || it == '-' }.take(64).ifEmpty { "pc" }
        val out = mutableMapOf("on" to listOfNotNull("bm".takeIf { bmOn }, "tabs".takeIf { tabsOn }).joinToString(","))

        if (bmOn && fields.containsKey("bm")) {
            val store = BookmarkStore(File(context.filesDir, "bookmarks.json"))
            val mine = store.load()
            val merged = SyncMerge.merge(readBase(context, id, "bm"), mine.map { SyncItem(it.url, it.title, it.folder, it.pinned) }, SyncMerge.decode(fields["bm"]))
            val next = merged.map { Bookmark(it.title, it.url, it.folder, it.pinned) }
            if (next != mine) store.save(next)
            writeBase(context, id, "bm", merged)
            out["bm"] = SyncMerge.encode(merged)
        }
        if (bmOn && fields.containsKey("later")) {
            val store = LaterStore(File(context.filesDir, "later.json"))
            val mine = store.load()
            val merged = SyncMerge.merge(readBase(context, id, "later"), mine.map { SyncItem(it.url, it.title) }, SyncMerge.decode(fields["later"]))
            val byUrl = mine.associateBy { it.url }
            val now = System.currentTimeMillis()
            // Il testo salvato sul telefono resta; quelli nuovi dal computer arrivano senza (si apre la pagina).
            val next = merged.take(LaterStore.MAX).map { m -> byUrl[m.url]?.copy(title = m.title) ?: LaterItem(m.url, m.title, "", now) }
            if (next != mine) store.save(next)
            writeBase(context, id, "later", merged)
            out["later"] = SyncMerge.encode(merged.take(LaterStore.MAX))
        }

        val pcTabs = if (tabsOn) SyncMerge.decode(fields["tabs"]).take(MAX_TABS) else emptyList()
        if (tabsOn) out["tabs"] = SyncMerge.encode(phoneTabs(context))
        File(dir(context), "pc-$id.json").writeText(
            JSONObject().put("id", id).put("name", computerName.take(80)).put("at", System.currentTimeMillis())
                .put("tabs", SyncMerge.encode(pcTabs)).toString(),
        )
        _changes.value++
        return out
    }

    /** Le schede normali aperte sul telefono, come le ricorda il Browser per riaprirle (le private non ci sono mai). */
    private fun phoneTabs(context: Context): List<SyncItem> = runCatching {
        val a = JSONArray(context.getSharedPreferences("browser", Context.MODE_PRIVATE).getString("tabs", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val url = o.optString("url").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            SyncItem(url, o.optString("title").ifBlank { Links.host(url) })
        }.distinctBy { it.url }.take(MAX_TABS)
    }.getOrDefault(emptyList())

    private fun readBase(context: Context, id: String, what: String): List<SyncItem> =
        runCatching { SyncMerge.decode(File(dir(context), "base-$what-$id.json").readText()) }.getOrDefault(emptyList())

    private fun writeBase(context: Context, id: String, what: String, items: List<SyncItem>) {
        val f = File(dir(context), "base-$what-$id.json")
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(SyncMerge.encode(items))
        tmp.renameTo(f)
    }

    /** Dimentica un computer (schede e stato comune); i segnalibri restano. */
    fun forget(context: Context, id: String) {
        dir(context).listFiles { f -> f.name.endsWith("-$id.json") }?.forEach { it.delete() }
        _changes.value++
    }

    /** Il risultato per [SyncProvider]: i campi in un Bundle. */
    fun bundle(fields: Map<String, String>) = Bundle().apply { fields.forEach { (k, v) -> putString(k, v) } }

    private const val MAX_TABS = 100
}

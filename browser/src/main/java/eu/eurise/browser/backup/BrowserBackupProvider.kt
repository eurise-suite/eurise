package eu.eurise.browser.backup

import android.content.Context
import eu.eurise.browser.R
import eu.eurise.core.suite.SuiteBackupProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Browser nel backup della suite (area Backup, 21 novembre 2026): segnalibri, «Leggi dopo», note dei siti, schede
 * aperte, cronologia (deciso dal proprietario il 20 novembre) e impostazioni. Restano fuori cookie e accessi ai siti
 * (le password stanno in Password), la cache di GeckoView e le icone (si riscaricano).
 *
 * Al ripristino non si perde niente di quello che c'è già: segnalibri e «Leggi dopo» si uniscono per indirizzo, le note
 * per sito, la cronologia del backup va prima di quella di questo telefono; le schede aperte solo se non ce ne sono.
 */
class BrowserBackupProvider : SuiteBackupProvider() {
    override fun summary(context: Context): String? {
        val bookmarks = runCatching { JSONArray(file(context, BOOKMARKS).readText()).length() }.getOrDefault(0)
        return if (bookmarks > 0) context.getString(R.string.backup_summary_browser, bookmarks) else null
    }

    override fun export(context: Context, zip: ZipOutputStream) {
        FILES.forEach { addFile(zip, file(context, it), "files/$it") }
        addPrefs(context, zip, *PREFS.toTypedArray())
    }

    override fun restore(context: Context, zip: ZipInputStream) {
        while (true) {
            val e = zip.nextEntry ?: break
            val name = safeName(e) ?: continue
            if (!name.startsWith("files/")) { restorePrefs(context, zip, name, PREFS); continue }
            val what = name.removePrefix("files/").takeIf { it in FILES } ?: continue
            val incoming = zip.bufferedReader().readText()
            val target = file(context, what)
            val current = target.takeIf { it.exists() }?.readText()
            val merged = when {
                current.isNullOrBlank() -> incoming
                what == SESSIONS -> current
                what == HISTORY -> (incoming.lines() + current.lines()).filter { it.isNotBlank() }.distinct().joinToString("\n", postfix = "\n")
                else -> runCatching { merge(current, incoming) }.getOrDefault(current)
            }
            val tmp = File(target.path + ".ripristino")
            tmp.writeText(merged)
            target.delete()
            tmp.renameTo(target)
        }
    }

    /** Unisce due JSON: liste per «url» (o uguali), oggetti per chiave; vince quello che c'è già. */
    private fun merge(current: String, incoming: String): String {
        val a = current.trim()
        val b = incoming.trim()
        if (a.startsWith("{") && b.startsWith("{")) {
            val out = JSONObject(a)
            val inc = JSONObject(b)
            inc.keys().forEach { k -> if (!out.has(k)) out.put(k, inc.get(k)) }
            return out.toString()
        }
        val out = JSONArray(a)
        val inc = JSONArray(b)
        val seen = HashSet<String>()
        for (i in 0 until out.length()) seen += keyOf(out.get(i))
        for (i in 0 until inc.length()) { val x = inc.get(i); if (seen.add(keyOf(x))) out.put(x) }
        return out.toString()
    }

    private fun keyOf(x: Any): String = (x as? JSONObject)?.optString("url")?.takeIf { it.isNotEmpty() } ?: x.toString()

    private fun file(context: Context, name: String) = File(context.filesDir, name)

    companion object {
        private const val BOOKMARKS = "bookmarks.json"
        private const val HISTORY = "history.txt"
        private const val SESSIONS = "sessions.json"
        private val FILES = setOf(BOOKMARKS, HISTORY, SESSIONS, "later.json", "site_notes.json")
        private val PREFS = setOf("browser", "browser_settings")
    }
}

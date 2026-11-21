package eu.eurise.browser

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.SuiteAccess

/**
 * La porta della sincronizzazione ([BrowserSync]) per l'app Eurise, che tiene il Ponte con il computer. Solo `call`:
 * `sync` con gli extra `computer` (id), `name` e i campi del messaggio del computer; risponde con i campi da rimandare.
 * Solo le app della suite della stessa variante (permesso di firma, controllato qui: per `call` Android non lo fa).
 */
class SyncProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        SuiteAccess.enforceCaller(ctx)
        if (method != METHOD || extras == null) return null
        val computer = extras.getString("computer").orEmpty()
        val name = extras.getString("name").orEmpty()
        val fields = extras.keySet().filter { it in FIELDS }.associateWith { extras.getString(it).orEmpty() }
        return BrowserSync.bundle(BrowserSync.handle(ctx, computer, name, fields))
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        const val METHOD = "sync"
        /** I campi del computer che si leggono; il resto si ignora. */
        val FIELDS = setOf("bm", "later", "tabs")
    }
}

package eu.eurise.feature.ponte

import android.content.Context
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.SuiteAccess

/**
 * Telefono e computer per il Browser (tavola BrwSincro, 21 novembre 2026): la richiesta del computer
 * ([eu.eurise.core.ponte.Kind.SyncAsk]) va al Browser Eurise, che unisce e risponde; qui si passa e basta. Il Browser
 * decide cosa è acceso (spento di base) e controlla che a chiamarlo sia un'app della suite; da qui si chiama solo un
 * Browser che è davvero della suite ([SuiteAccess.call]).
 */
internal object PonteBrowser {
    private val FIELDS = setOf("bm", "later", "tabs")

    /** I campi della risposta ([eu.eurise.core.ponte.Kind.Sync]); null se il Browser non c'è o non risponde. */
    fun sync(context: Context, computerId: String, computerName: String, fields: Map<String, String>): Map<String, String>? {
        val pkg = if (SuiteAccess.isDebug(context)) "eu.eurise.browser.debug" else "eu.eurise.browser"
        val extras = Bundle().apply {
            putString("computer", computerId)
            putString("name", computerName)
            fields.filterKeys { it in FIELDS }.forEach { (k, v) -> putString(k, v) }
        }
        val out = runCatching { SuiteAccess.call(context, Uri.parse("content://$pkg.sync"), "sync", null, extras) }.getOrNull() ?: return null
        return out.keySet().associateWith { out.getString(it).orEmpty() }
    }
}

package eu.eurise.core.suite

import android.content.Context
import android.net.Uri
import android.os.Bundle

/**
 * I segreti che un APK della suite affida all'app Eurise perché finiscano nel backup cifrato della suite (per ora la
 * chiave di recupero della Chat, tavola ChatRecupero). L'app Eurise li tiene nei suoi dati privati (preferenze
 * «eurise_secrets», comprese nel backup che si apre solo con la frase segreta dell'utente) e li restituisce, sul telefono
 * nuovo, solo alle app firmate con la stessa chiave (permesso della suite, controllato dal fornitore a ogni chiamata).
 */
object SecretsContract {
    const val METHOD_PUT = "put"
    const val METHOD_GET = "get"
    const val METHOD_REMOVE = "remove"
    const val EXTRA_VALUE = "value"

    fun hubPackage(debug: Boolean) = if (debug) "eu.eurise.hub.debug" else "eu.eurise.hub"
    fun authority(hubPackage: String) = "$hubPackage.secrets"
    private fun uri(hub: String) = Uri.parse("content://${authority(hub)}")

    /** Affida [value] col nome [name]; falso se l'app Eurise non c'è o non risponde. */
    fun put(context: Context, hub: String, name: String, value: String): Boolean = runCatching {
        context.contentResolver.call(uri(hub), METHOD_PUT, name, Bundle().apply { putString(EXTRA_VALUE, value) }) != null
    }.getOrDefault(false)

    fun get(context: Context, hub: String, name: String): String? = runCatching {
        context.contentResolver.call(uri(hub), METHOD_GET, name, null)?.getString(EXTRA_VALUE)
    }.getOrNull()

    fun remove(context: Context, hub: String, name: String): Boolean = runCatching {
        context.contentResolver.call(uri(hub), METHOD_REMOVE, name, null) != null
    }.getOrDefault(false)
}

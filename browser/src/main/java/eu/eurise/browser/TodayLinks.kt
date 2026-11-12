package eu.eurise.browser

import android.content.Context
import android.net.Uri
import eu.eurise.core.suite.TodayLinksContract as C

/** Una riga di «Per oggi, dalla suite»: [kind] è uno dei tipi di [C]; [time] 0 se non c'è un'ora. */
data class TodayLink(val title: String, val url: String, val kind: String, val time: Long)

/** Legge «Per oggi» dall'app Eurise (permesso della suite). Senza l'app o senza dati, un elenco vuoto. */
object TodayLinks {
    fun read(context: Context, hubPackage: String): List<TodayLink> = runCatching {
        val b = context.contentResolver.call(Uri.parse("content://" + C.authority(hubPackage)), C.METHOD_LIST, null, null)
            ?: return emptyList()
        val titles = b.getStringArray(C.KEY_TITLE).orEmpty()
        val urls = b.getStringArray(C.KEY_URL).orEmpty()
        val kinds = b.getStringArray(C.KEY_KIND).orEmpty()
        val times = b.getLongArray(C.KEY_WHEN) ?: LongArray(0)
        titles.indices.mapNotNull { i ->
            TodayLink(titles[i], urls.getOrNull(i) ?: return@mapNotNull null, kinds.getOrNull(i) ?: C.KIND_EVENT, times.getOrElse(i) { 0L })
        }
    }.getOrDefault(emptyList())
}

package eu.eurise.phone.data

import android.content.Context

/**
 * «Non richiamare»: le chiamate tolte da «Da richiamare» (nel Telefono o nella Postazione). Si tiene il numero e l'ora
 * dell'ultima chiamata persa tolta: se quella persona richiama e non rispondi, torna nella lista. Solo sul telefono.
 */
object CallBackDismiss {
    private const val KEY = "list"
    private const val MAX = 300

    private fun prefs(context: Context) = context.getSharedPreferences("da_richiamare", Context.MODE_PRIVATE)

    fun all(context: Context): List<Pair<String, Long>> =
        prefs(context).getString(KEY, null).orEmpty().lines().mapNotNull { r ->
            val n = r.substringBeforeLast('|', "")
            val t = r.substringAfterLast('|').toLongOrNull()
            if (n.isBlank() || t == null) null else n to t
        }

    @Synchronized
    fun dismiss(context: Context, number: String, upTo: Long) {
        if (number.isBlank()) return
        val list = all(context).filterNot { PhoneData.same(it.first, number) } + (number to upTo)
        prefs(context).edit().putString(KEY, list.takeLast(MAX).joinToString("\n") { "${it.first}|${it.second}" }).apply()
    }
}

package eu.eurise.phone.data

import android.content.Context
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.SpamList
import eu.eurise.core.suite.SpamListContract
import eu.eurise.core.suite.SuiteAccess
import eu.eurise.phone.BuildConfig
import eu.eurise.phone.R

/**
 * L'elenco Eurise dei numeri molesti visto dal Telefono. Il Telefono non va su Internet: l'elenco lo scarica l'app
 * Eurise (se la persona l'ha acceso) e lo passa qui con il permesso della suite. Il confronto con le chiamate si fa sul
 * telefono. Senza l'app Eurise, o spento, non c'è elenco e il filtro fa quello che faceva.
 */
object SpamListClient {
    data class Status(val on: Boolean, val updated: Long, val count: Int, val available: Boolean)

    private val uri get() = Uri.parse("content://" + SpamListContract.authority(BuildConfig.HUB_PACKAGE))
    private const val CACHE_MS = 10 * 60_000L
    @Volatile private var cache: Pair<Long, List<SpamList.Entry>>? = null

    private fun call(context: Context, method: String): Bundle? =
        runCatching { SuiteAccess.call(context, uri, method, null, null) }.getOrNull()

    private fun toStatus(b: Bundle?) = Status(
        on = b?.getBoolean(SpamListContract.KEY_ON) == true,
        updated = b?.getLong(SpamListContract.KEY_UPDATED) ?: 0L,
        count = b?.getInt(SpamListContract.KEY_COUNT) ?: 0,
        available = b != null,
    )

    /** Da chiamare fuori dal thread principale (parla con un'altra app). */
    fun status(context: Context) = toStatus(call(context, SpamListContract.METHOD_STATUS))
    fun enable(context: Context) = toStatus(call(context, SpamListContract.METHOD_ENABLE)).also { cache = null }
    fun disable(context: Context) = toStatus(call(context, SpamListContract.METHOD_DISABLE)).also { cache = null }
    fun update(context: Context) = toStatus(call(context, SpamListContract.METHOD_UPDATE)).also { cache = null }

    /** Le voci dell'elenco (vuote se spento o senza l'app Eurise), tenute dieci minuti. */
    fun entries(context: Context): List<SpamList.Entry> {
        val now = System.currentTimeMillis()
        cache?.let { (at, list) -> if (now - at < CACHE_MS) return list }
        val text = call(context, SpamListContract.METHOD_LIST)?.getString(SpamListContract.KEY_TEXT)
        val list = text?.let(SpamList::parse).orEmpty()
        cache = now to list
        return list
    }

    /** La voce dell'elenco per [number], o null. */
    fun match(context: Context, number: String?): SpamList.Entry? =
        SpamList.match(number, entries(context), SpamFilter.countryCode(context))

    /** La categoria in parole («squillo dall'estero», «call center»…). */
    fun categoryText(context: Context, category: String): String = context.getString(
        when (category) {
            "wangiri" -> R.string.ph_list_cat_wangiri
            "callcenter" -> R.string.ph_list_cat_callcenter
            "truffa" -> R.string.ph_list_cat_scam
            else -> R.string.ph_list_cat_other
        },
    )

    /**
     * L'avviso da mostrare per un numero che non è in rubrica: prima l'elenco Eurise («Elenco Eurise: truffa»), poi i
     * prefissi dei call center e quelli segnati a mano («Sospetto spam (0844…)»). Null se niente. Fuori dal thread
     * principale.
     */
    fun warning(context: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        match(context, number)?.let { return context.getString(R.string.ph_list_label, categoryText(context, it.category)) }
        return SpamFilter.suspect(number, SpamFilter.suspectPrefixes(context), SpamFilter.countryCode(context))
            ?.let { context.getString(R.string.ph_suspect_spam, it) }
    }
}

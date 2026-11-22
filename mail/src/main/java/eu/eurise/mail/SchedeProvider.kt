package eu.eurise.mail

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.insieme.Scheda
import eu.eurise.core.insieme.SchedaContract as C

/**
 * Le schede della Chat per email (tavola InsStessaScheda, [C]): la Chat chiede di mandare una scheda a chi nel gruppo ha solo
 * l'email, e poi le risposte arrivate. Parte dalla prima casella; le risposte si leggono con la posta (app aperta o lavoro ogni
 * 15 minuti). Risponde solo alle app della suite.
 */
class SchedeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!C.allowed(ctx)) return null
        return runCatching {
            val accounts = AccountStore(ctx).load()
            when (method) {
                C.METHOD_READY -> Bundle().apply { putBoolean(C.EXTRA_RESULT, accounts.isNotEmpty()) }
                C.METHOD_SEND -> {
                    val card = Scheda.fromJson(org.json.JSONObject(extras?.getString(C.EXTRA_CARD) ?: return null)) ?: return null
                    val to = extras?.getStringArray(C.EXTRA_TO).orEmpty().toList()
                    val from = accounts.firstOrNull() ?: return Bundle().apply { putBoolean(C.EXTRA_RESULT, false) }
                    Bundle().apply { putBoolean(C.EXTRA_RESULT, CardStore.send(ctx, from, card, to)) }
                }
                C.METHOD_ANSWERS -> {
                    val origin = extras?.getString(C.EXTRA_ORIGIN).orEmpty()
                    val got = CardStore.load(ctx).filter { it.card.origin.startsWith(origin) && origin.isNotEmpty() }
                        .flatMap { s -> s.answers.map { (addr, g) -> C.Got(s.card.id, addr, g.answer, g.at) } }
                    Bundle().apply { putString(C.EXTRA_RESULT, C.pack(got)) }
                }
                else -> null
            }
        }.getOrNull()
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

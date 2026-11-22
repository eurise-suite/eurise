package eu.eurise.core.insieme

import android.content.Context
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.SuiteAccess
import org.json.JSONArray
import org.json.JSONObject

/**
 * La strada dell'email per le schede della Chat (tavola InsStessaScheda): chi nel gruppo ha solo l'email (faccende, uscite,
 * cose affidate) riceve la scheda da Eurise Email, dalla casella dell'utente, e le sue risposte tornano alla Chat. Tutto sul
 * telefono: la Chat chiama il fornitore `<Email>.schede`, che risponde solo alle app della suite (permesso di firma
 * controllato a ogni chiamata) e solo se l'app chiamata è davvero della suite ([SuiteAccess.call]).
 */
object SchedaContract {
    const val METHOD_READY = "ready"
    const val METHOD_SEND = "send"
    const val METHOD_ANSWERS = "answers"
    const val EXTRA_CARD = "card"
    const val EXTRA_TO = "to"
    const val EXTRA_ORIGIN = "origin"
    const val EXTRA_RESULT = "result"

    /** Una risposta arrivata per email: a quale scheda, da chi, cosa, quando. */
    data class Got(val id: String, val address: String, val answer: Scheda.Answer, val at: Long)

    fun allowed(context: Context) = SuiteAccess.callerAllowed(context)

    fun mailPackage(context: Context) = "eu.eurise.mail" + if (SuiteAccess.isDebug(context)) ".debug" else ""

    private fun uri(context: Context) = Uri.parse("content://" + mailPackage(context) + ".schede")

    private fun call(context: Context, method: String, extras: Bundle?): Bundle? =
        runCatching { SuiteAccess.call(context, uri(context), method, null, extras) }.getOrNull()

    /** Vero se Eurise Email c'è e ha almeno una casella. */
    fun ready(context: Context): Boolean = call(context, METHOD_READY, null)?.getBoolean(EXTRA_RESULT) == true

    /** Manda la scheda a ognuno degli indirizzi (un'email per persona: nessuno vede gli indirizzi degli altri). */
    fun send(context: Context, card: Scheda.Card, to: List<String>): Boolean {
        if (to.isEmpty()) return false
        val b = Bundle().apply { putString(EXTRA_CARD, Scheda.toJson(card).toString()); putStringArray(EXTRA_TO, to.toTypedArray()) }
        return call(context, METHOD_SEND, b)?.getBoolean(EXTRA_RESULT) == true
    }

    /** Le risposte arrivate per le schede che vengono da [origin] («chat:» per tutte quelle della Chat). */
    fun answers(context: Context, origin: String): List<Got> =
        unpack(call(context, METHOD_ANSWERS, Bundle().apply { putString(EXTRA_ORIGIN, origin) })?.getString(EXTRA_RESULT))

    fun pack(list: List<Got>): String = JSONArray().apply {
        list.forEach { g -> put(Scheda.answerToJson(g.answer).put("id", g.id).put("address", g.address).put("at", g.at)) }
    }.toString()

    fun unpack(s: String?): List<Got> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o: JSONObject = a.optJSONObject(i) ?: return@mapNotNull null
            val ans = Scheda.answerFromJson(o) ?: return@mapNotNull null
            Got(o.optString("id").ifEmpty { return@mapNotNull null }, o.optString("address"), ans, o.optLong("at"))
        }
    }.getOrDefault(emptyList())
}

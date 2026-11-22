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

    /** Email → Chat (tavola SpeEmail): «Aggiungi alle spese di un gruppo», con l'importo e cosa; nella Chat si sceglie il gruppo. */
    const val ACTION_ADD_EXPENSE = "eu.eurise.action.ADD_EXPENSE"
    const val EXTRA_CENTS = "cents"
    const val EXTRA_TITLE = "title"

    fun chatPackage(context: Context) = "eu.eurise.chat" + if (SuiteAccess.isDebug(context)) ".debug" else ""

    fun expenseIntent(context: Context, cents: Long, title: String): android.content.Intent? {
        val pkg = chatPackage(context)
        if (!SuiteAccess.isSuitePackage(context, pkg)) return null
        return android.content.Intent(ACTION_ADD_EXPENSE).setComponent(android.content.ComponentName(pkg, "eu.eurise.chat.ChatActivity"))
            .putExtra(EXTRA_CENTS, cents).putExtra(EXTRA_TITLE, title).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Email → Chat (tavole FamScadenze, FamScuola): una scadenza per le Scadenze di casa, una domanda «chi va?» per la famiglia. */
    const val ACTION_ADD_DEADLINE = "eu.eurise.action.ADD_DEADLINE"
    const val ACTION_ASK_FAMILY = "eu.eurise.action.ASK_FAMILY"
    const val EXTRA_DAY = "day"
    const val EXTRA_OPTIONS = "options"

    private fun chatIntent(context: Context, action: String): android.content.Intent? {
        val pkg = chatPackage(context)
        if (!SuiteAccess.isSuitePackage(context, pkg)) return null
        return android.content.Intent(action).setComponent(android.content.ComponentName(pkg, "eu.eurise.chat.ChatActivity")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun deadlineIntent(context: Context, title: String, cents: Long, day: java.time.LocalDate): android.content.Intent? =
        chatIntent(context, ACTION_ADD_DEADLINE)?.putExtra(EXTRA_TITLE, title)?.putExtra(EXTRA_CENTS, cents)?.putExtra(EXTRA_DAY, day.toEpochDay())

    fun askIntent(context: Context, question: String, options: List<String>): android.content.Intent? =
        chatIntent(context, ACTION_ASK_FAMILY)?.putExtra(EXTRA_TITLE, question)?.putExtra(EXTRA_OPTIONS, options.toTypedArray())

    /** Email → Chat (tavola AmiViaggio): una conferma di viaggio diventa una tappa dell'itinerario del gruppo. */
    const val ACTION_ADD_STOP = "eu.eurise.action.ADD_STOP"
    const val EXTRA_TIME = "time"
    const val EXTRA_PLACE = "place"

    fun stopIntent(context: Context, title: String, day: java.time.LocalDate, time: String, place: String): android.content.Intent? =
        chatIntent(context, ACTION_ADD_STOP)?.putExtra(EXTRA_TITLE, title)?.putExtra(EXTRA_DAY, day.toEpochDay())?.putExtra(EXTRA_TIME, time)?.putExtra(EXTRA_PLACE, place)

    /** Email → Chat (tavola LavDecisioni): «Deciso» su un'email la mette nel registro del gruppo scelto ([EXTRA_PLACE]: chi). */
    const val ACTION_ADD_DECISION = "eu.eurise.action.ADD_DECISION"

    fun decisionIntent(context: Context, text: String, who: String): android.content.Intent? =
        chatIntent(context, ACTION_ADD_DECISION)?.putExtra(EXTRA_TITLE, text)?.putExtra(EXTRA_PLACE, who)

    /** Chat → Email (tavola LavStato): la stessa assenza, per la risposta automatica delle caselle di lavoro. */
    const val METHOD_ABSENCE = "absence"
    const val EXTRA_ABSENCE = "absence"

    fun setAbsence(context: Context, s: Assenza.State?): Boolean =
        call(context, METHOD_ABSENCE, Bundle().apply { putString(EXTRA_ABSENCE, s?.let { Assenza.encode(it).toString() }.orEmpty()) })?.getBoolean(EXTRA_RESULT) == true

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

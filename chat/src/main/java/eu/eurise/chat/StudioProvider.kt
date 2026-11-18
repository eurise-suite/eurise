package eu.eurise.chat

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.studio.Channel
import eu.eurise.core.studio.Queue
import eu.eurise.core.studio.StudioContract as C

/**
 * Lo Studio per Email e Messaggi ([C]): ricevono un messaggio e lo offrono qui. Risponde solo alle app della suite
 * (permesso di firma, controllato a ogni chiamata: Android non lo fa da solo per call()) e solo con lo Studio acceso.
 */
class StudioProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!eu.eurise.core.suite.SuiteAccess.callerAllowed(ctx)) return null
        val state = StudioRepo.current(ctx)
        if (!state.enabled) return null
        return when (method) {
            C.METHOD_PATIENTS -> Bundle().apply {
                putStringArray(C.EXTRA_EMAILS, state.patients.mapNotNull { it.email }.toTypedArray())
                putStringArray(C.EXTRA_PHONES, state.patients.mapNotNull { it.phone }.toTypedArray())
            }
            C.METHOD_OFFER -> {
                val channel = runCatching { Channel.valueOf(arg ?: "") }.getOrNull() ?: return null
                // La Chat porta i suoi messaggi da sé: da fuori arrivano solo email e SMS.
                if (channel == Channel.Chat || extras == null) return null
                val address = extras.getString(C.EXTRA_ADDRESS) ?: return null
                val ref = extras.getString(C.EXTRA_REF) ?: return null
                val out = StudioRepo.receive(ctx, channel, address, extras.getString(C.EXTRA_TEXT).orEmpty(), extras.getLong(C.EXTRA_TIME), ref)
                Bundle().apply {
                    when (out) {
                        is Queue.Outcome.Queued -> {
                            putString(C.EXTRA_RESULT, C.RESULT_QUEUED); putString(C.EXTRA_PATIENT, out.patient.id)
                            putString(C.EXTRA_NAME, out.patient.name); putString(C.EXTRA_NEED, out.request.need.name)
                        }
                        is Queue.Outcome.Verified -> {
                            putString(C.EXTRA_RESULT, C.RESULT_VERIFIED); putString(C.EXTRA_PATIENT, out.patient.id); putString(C.EXTRA_NAME, out.patient.name)
                        }
                        Queue.Outcome.Ignored -> {
                            // Già in coda o non ancora: se l'indirizzo è di un paziente, lo si dice lo stesso (segno «Paziente»).
                            val p = eu.eurise.core.studio.Addresses.find(state.patients, channel, address)
                            putString(C.EXTRA_RESULT, C.RESULT_IGNORED); putString(C.EXTRA_PATIENT, p?.id); putString(C.EXTRA_NAME, p?.name)
                        }
                    }
                }
            }
            else -> null
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

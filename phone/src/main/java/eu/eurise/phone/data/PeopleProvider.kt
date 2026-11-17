package eu.eurise.phone.data

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import eu.eurise.core.contacts.PeopleLookup
import eu.eurise.core.suite.PeopleContract as P

/**
 * Le chiamate di una persona, con la nota scritta durante la chiamata, e la Rubrica per la Chat («Una persona, tutti i
 * canali», [P]). Risponde solo alle app della suite (permesso di firma, controllato a ogni chiamata).
 */
class PeopleProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!P.allowed(ctx)) return null
        return runCatching {
            when (method) {
                P.METHOD_RECENT -> P.packRecent(recent())
                P.METHOD_ITEMS -> P.packItems(items(extras?.getStringArray(P.EXTRA_PHONES).orEmpty().toList(), extras?.getInt(P.EXTRA_LIMIT, 200) ?: 200))
                P.METHOD_RESOLVE -> PeopleLookup.resolve(ctx, extras?.getStringArray(P.EXTRA_ADDRESSES).orEmpty().toList()).let { (people, index) -> P.packPeople(people, index) }
                P.METHOD_SEARCH -> P.packPeople(PeopleLookup.search(ctx, extras?.getString(P.EXTRA_QUERY).orEmpty()))
                else -> null
            }
        }.getOrNull()
    }

    private class Raw(val number: String, val type: Int, val date: Long, val duration: Long, val new: Boolean)

    private fun log(limit: Int, numbers: List<String>? = null): List<Raw> {
        val ctx = context ?: return emptyList()
        if (!PhoneData.has(ctx, Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = ArrayList<Raw>()
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.NEW),
            null, null, "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            var read = 0
            while (out.size < limit && read++ < 3000 && c.moveToNext()) {
                val n = c.getString(0).orEmpty()
                if (n.isBlank()) continue
                if (numbers != null && numbers.none { PhoneData.same(it, n) }) continue
                out += Raw(n, c.getInt(1), c.getLong(2), c.getLong(3), c.getInt(4) == 1)
            }
        }
        return out
    }

    private fun kind(type: Int) = when (type) {
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> P.Kind.CallMissed
        CallLog.Calls.OUTGOING_TYPE -> P.Kind.CallOut
        else -> P.Kind.CallIn
    }

    private fun item(r: Raw, notes: List<CallNotes.Note>) = P.Item(
        kind(r.type), r.number, r.date, duration = r.duration,
        note = CallNotes.forCall(notes, r.number, r.date, r.duration)?.text.orEmpty(),
        unread = r.new && kind(r.type) == P.Kind.CallMissed,
    )

    /** L'ultima chiamata per numero (dalle ultime 400), con le perse non ancora viste. */
    private fun recent(): List<P.Recent> {
        val ctx = context ?: return emptyList()
        val notes = CallNotes.all(ctx)
        val out = ArrayList<P.Recent>()
        val all = log(400)
        for (r in all) {
            if (out.any { PhoneData.same(it.item.address, r.number) }) continue
            val missed = all.count { PhoneData.same(it.number, r.number) && it.new && kind(it.type) == P.Kind.CallMissed }
            out += P.Recent(item(r, notes), missed)
        }
        return out
    }

    private fun items(phones: List<String>, limit: Int): List<P.Item> {
        val ctx = context ?: return emptyList()
        if (phones.isEmpty()) return emptyList()
        val notes = CallNotes.all(ctx)
        return log(limit, phones).map { item(it, notes) }.sortedBy { it.time }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

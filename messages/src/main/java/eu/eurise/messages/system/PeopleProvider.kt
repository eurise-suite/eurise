package eu.eurise.messages.system

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import eu.eurise.core.contacts.PeopleLookup
import eu.eurise.core.suite.PeopleContract as P
import eu.eurise.messages.data.SmsStore
import kotlinx.coroutines.runBlocking

/**
 * Gli SMS di una persona per la Chat («Una persona, tutti i canali», [P]): ultimi messaggi per numero, il filo con una
 * persona, l'invio di un SMS e i nomi della Rubrica. Risponde solo alle app della suite (permesso di firma, controllato
 * a ogni chiamata). Le conversazioni di gruppo restano in Messaggi.
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
                P.METHOD_SEND -> Bundle().apply {
                    putBoolean(P.EXTRA_RESULT, send(extras?.getString(P.EXTRA_ADDRESS).orEmpty(), extras?.getString(P.EXTRA_TEXT).orEmpty()))
                }
                P.METHOD_RESOLVE -> PeopleLookup.resolve(ctx, extras?.getStringArray(P.EXTRA_ADDRESSES).orEmpty().toList()).let { (people, index) -> P.packPeople(people, index) }
                P.METHOD_SEARCH -> P.packPeople(PeopleLookup.search(ctx, extras?.getString(P.EXTRA_QUERY).orEmpty()))
                else -> null
            }
        }.getOrNull()
    }

    /** Una riga per conversazione a due (al massimo 150), con quanti SMS da leggere. */
    private fun recent(): List<P.Recent> {
        val ctx = context ?: return emptyList()
        val unread = HashMap<Long, Int>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}", null, null,
        )?.use { c -> while (c.moveToNext()) c.getLong(0).let { unread[it] = (unread[it] ?: 0) + 1 } }
        return runBlocking { SmsStore.threads(ctx) }.filter { it.addresses.size == 1 }.take(150).map { t ->
            P.Recent(P.Item(P.Kind.SmsIn, t.addresses[0], t.date, t.snippet, unread = (unread[t.id] ?: 0) > 0, ref = t.id.toString()), unread[t.id] ?: 0)
        }
    }

    private fun items(phones: List<String>, limit: Int): List<P.Item> {
        val ctx = context ?: return emptyList()
        if (phones.isEmpty()) return emptyList()
        val threads = runBlocking { SmsStore.threads(ctx) }.filter { t -> t.addresses.size == 1 && phones.any { SmsStore.same(it, t.addresses[0]) } }
        return threads.flatMap { t ->
            runBlocking { SmsStore.messages(ctx, t.id, limit) }.map { m ->
                P.Item(
                    if (m.incoming) P.Kind.SmsIn else P.Kind.SmsOut, t.addresses[0], m.date,
                    m.body.ifEmpty { if (m.mms) "MMS" else "" }, unread = m.incoming && !m.read, ref = t.id.toString(),
                )
            }
        }.sortedBy { it.time }.takeLast(limit)
    }

    /**
     * Da app predefinita per gli SMS si passa da [Sender] (salvato in uscita, poi «mandato»); altrimenti si manda
     * direttamente e il sistema lo scrive da sé nella conversazione.
     */
    private fun send(address: String, text: String): Boolean {
        val ctx = context ?: return false
        if (address.isBlank() || text.isBlank()) return false
        return runCatching {
            if (Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName) Sender.sendSms(ctx, listOf(address), text)
            else {
                @Suppress("DEPRECATION")
                val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                val parts = sms.divideMessage(text)
                if (parts.size > 1) sms.sendMultipartTextMessage(address, null, parts, null, null) else sms.sendTextMessage(address, null, text, null, null)
            }
            true
        }.getOrDefault(false)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

package eu.eurise.mail

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.PeopleContract as P

/**
 * Le email di una persona per la Chat («Una persona, tutti i canali», [P]): dalla posta già sul telefono (intestazioni
 * di tutte le caselle), senza rete. Risponde solo alle app della suite (permesso di firma, controllato a ogni chiamata).
 */
class PeopleProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!P.allowed(ctx)) return null
        return runCatching {
            when (method) {
                P.METHOD_RECENT -> P.packRecent(recent())
                P.METHOD_ITEMS -> P.packItems(items(extras?.getStringArray(P.EXTRA_EMAILS).orEmpty().toList(), extras?.getInt(P.EXTRA_LIMIT, 200) ?: 200))
                // «Vista» nella Postazione: l'email si segna come letta (anche nella casella).
                P.METHOD_MARK_SEEN -> Bundle().apply { putBoolean(P.EXTRA_RESULT, MailCache.markSeen(ctx, extras?.getString(P.EXTRA_REF).orEmpty())) }
                else -> null
            }
        }.getOrNull()
    }

    private fun all(): List<Summary> {
        val ctx = context ?: return emptyList()
        return AccountStore(ctx).load().flatMap { MailCache.read(ctx, it.id) }
    }

    private fun item(s: Summary) = P.Item(
        P.Kind.MailIn, s.fromAddress.lowercase(), s.date, subject = s.subject, unread = !s.seen, ref = "${s.accountId}:${s.uid}", name = s.fromName,
    )

    /** Solo la posta delle persone (non avvisi e newsletter): l'ultima per indirizzo, con quante da leggere. */
    private fun recent(): List<P.Recent> = all().filter { it.kind == Kind.People && it.fromAddress.isNotBlank() }
        .groupBy { it.fromAddress.lowercase() }
        .map { (_, list) -> P.Recent(item(list.maxBy { it.date }), list.count { !it.seen }) }

    private fun items(emails: List<String>, limit: Int): List<P.Item> {
        if (emails.isEmpty()) return emptyList()
        val wanted = emails.map { it.trim().lowercase() }.toSet()
        return all().filter { it.fromAddress.lowercase() in wanted }.sortedBy { it.date }.takeLast(limit).map(::item)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

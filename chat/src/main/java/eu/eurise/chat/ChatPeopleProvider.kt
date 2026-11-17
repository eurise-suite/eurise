package eu.eurise.chat

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.PeopleContract as P

/**
 * La Chat per la Rubrica (tavola ChatInvita): chi sono sulla Chat, per il link dell'invito, e se un contatto è già unito a
 * una persona della Chat. Risponde solo alle app della suite (permesso di firma, controllato a ogni chiamata).
 */
class ChatPeopleProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!P.allowed(ctx)) return null
        return when (method) {
            // L'invito porta all'account personale; senza, a quello di lavoro.
            P.METHOD_ME -> Bundle().apply {
                putString(P.EXTRA_USER, SessionStore(ctx, Account.Personal).userId() ?: SessionStore(ctx, Account.Work).userId())
            }
            P.METHOD_KNOWS -> Bundle().apply { putBoolean(P.EXTRA_RESULT, arg != null && Uni.linkedKeys(ctx).contains(arg)) }
            else -> null
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

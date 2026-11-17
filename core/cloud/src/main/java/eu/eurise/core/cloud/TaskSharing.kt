package eu.eurise.core.cloud

import android.content.Context
import android.content.SharedPreferences

/**
 * Condivisione degli elenchi delle Attività sul cloud dell'utente (collaborazione rapida, 17 novembre 2026), usata dalle
 * Attività, dalla nota della riunione e da ogni parte che affida una cosa da fare. Un elenco è un calendario di attività
 * CalDAV su Murena o Nextcloud: condividerlo vuol dire condividere quel calendario. Eurise non ha server e non vede nulla.
 * Il legame tra elenco e calendario («list_<id>») lo scrive la sincronizzazione delle Attività.
 */
object TaskSharing {
    const val ME_NAME = "me_name"
    const val ME_EMAIL = "me_email"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("tasks_sync", Context.MODE_PRIVATE)

    /** Nome ed email dell'account sul server, come li ha letti l'ultima sincronizzazione. */
    fun me(context: Context): Pair<String, String?>? =
        prefs(context).let { p -> p.getString(ME_NAME, null)?.let { it to p.getString(ME_EMAIL, null) } }

    fun isMe(context: Context, email: String?): Boolean = email != null && me(context)?.second?.equals(email, ignoreCase = true) == true

    /** Il cloud delle attività, se è uno che sa condividere (Murena o Nextcloud) e la sincronizzazione è accesa. */
    fun cloudKind(context: Context): CloudKind? {
        val sync = CloudSync.get(context)
        val p = sync.settings.value
        if (!p.tasksEnabled) return null
        return sync.accounts.get(p.notesAccount)?.kind?.takeIf { it == CloudKind.Murena || it == CloudKind.Nextcloud }
    }

    private fun calendars(context: Context): WebDav? {
        val sync = CloudSync.get(context)
        return sync.accounts.dav(sync.settings.value.notesAccount)?.calendars()
    }

    private fun slug(context: Context, listId: Long): String? = prefs(context).getString("list_$listId", null)

    /** Le persone con cui è condiviso l'elenco, lette dal server; null se l'elenco non è ancora sul server. */
    suspend fun members(context: Context, listId: Long): List<DavMember>? {
        val slug = slug(context, listId) ?: return null
        val cal = calendars(context) ?: return null
        return cal.listCalendars().firstOrNull { it.slug == slug }?.members.orEmpty()
    }

    /**
     * Condivide l'elenco con [who] (email o nome utente dello stesso server). Se l'elenco non è ancora sul server la
     * condivisione aspetta la prossima sincronizzazione, che parte subito.
     */
    suspend fun share(context: Context, listId: Long, who: String, edit: Boolean) {
        val slug = slug(context, listId)
        val cal = calendars(context)
        if (slug != null && cal != null) {
            cal.shareCalendar(slug, who, edit)
        } else {
            val key = "share_$listId"
            val p = prefs(context)
            p.edit().putStringSet(key, p.getStringSet(key, emptySet())!! + (who.trim() + "|" + if (edit) "1" else "0")).apply()
        }
        CloudSync.get(context).requestSoon()
    }

    suspend fun unshare(context: Context, listId: Long, member: DavMember) {
        val slug = slug(context, listId) ?: return
        calendars(context)?.unshareCalendar(slug, member.href)
        CloudSync.get(context).requestSoon()
    }

    /** Dalla sincronizzazione: le condivisioni rimaste in attesa per l'elenco [listId], ora che è sul server. */
    suspend fun flushShares(cal: WebDav, store: SharedPreferences, listId: Long, slug: String) {
        val key = "share_$listId"
        val pending = store.getStringSet(key, null)?.takeIf { it.isNotEmpty() } ?: return
        val left = pending.filterNot { entry ->
            runCatching { cal.shareCalendar(slug, entry.substringBeforeLast('|'), entry.endsWith("|1")) }.isSuccess
        }.toSet()
        store.edit().putStringSet(key, left).apply()
    }
}

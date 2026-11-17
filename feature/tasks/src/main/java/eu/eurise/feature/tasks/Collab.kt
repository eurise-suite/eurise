package eu.eurise.feature.tasks

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.cloud.CloudSync
import eu.eurise.core.cloud.DavMember
import eu.eurise.core.cloud.WebDav
import eu.eurise.core.designsystem.component.DataOut
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.privacyScore

/**
 * Collaborazione rapida nelle Attività (pagina Collaborazione della tela, 17 novembre 2026): elenchi condivisi
 * (CollElenchi) e «Affida a…» (CollAffida). Tutto passa dal cloud scelto dall'utente (Murena o Nextcloud, CalDAV):
 * un elenco condiviso è un calendario di attività condiviso sul server; Eurise non ha server e non vede nulla.
 */
internal object Collab {
    const val ME_NAME = "me_name"
    const val ME_EMAIL = "me_email"
    private const val CHANNEL = "eurise_tasks_shared"

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

    /** Tacchette della privacy del cloud scelto (stesse regole di Drive). */
    fun privacy(kind: CloudKind?): Pair<Int, Jurisdiction> = when (kind) {
        CloudKind.Murena -> privacyScore(DataOut.Personal, Jurisdiction.EU) to Jurisdiction.EU
        else -> privacyScore(DataOut.Personal, Jurisdiction.Own) to Jurisdiction.Own
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

    fun assignedToYou(context: Context, owner: String?, title: String) =
        context.getString(R.string.collab_note_assigned, owner ?: context.getString(R.string.collab_someone)) to title

    fun doneBy(context: Context, assignee: String?, title: String) =
        context.getString(R.string.collab_note_done, assignee ?: context.getString(R.string.collab_someone)) to title

    fun notStarted(context: Context, assignee: String?, title: String) =
        context.getString(R.string.collab_note_late, assignee ?: context.getString(R.string.collab_someone)) to title

    /** Un avviso delle Attività condivise; tocca e si apre Eurise. */
    fun notify(context: Context, note: Pair<String, String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.collab_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val id = (note.first + note.second).hashCode()
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
            .setContentTitle(note.first)
            .setContentText(note.second)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

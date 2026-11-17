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
import eu.eurise.core.cloud.TaskSharing
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
    const val ME_NAME = TaskSharing.ME_NAME
    const val ME_EMAIL = TaskSharing.ME_EMAIL
    private const val CHANNEL = "eurise_tasks_shared"

    fun prefs(context: Context): SharedPreferences = TaskSharing.prefs(context)
    fun me(context: Context): Pair<String, String?>? = TaskSharing.me(context)
    fun isMe(context: Context, email: String?): Boolean = TaskSharing.isMe(context, email)
    fun cloudKind(context: Context): CloudKind? = TaskSharing.cloudKind(context)

    /** Tacchette della privacy del cloud scelto (stesse regole di Drive). */
    fun privacy(kind: CloudKind?): Pair<Int, Jurisdiction> = when (kind) {
        CloudKind.Murena -> privacyScore(DataOut.Personal, Jurisdiction.EU) to Jurisdiction.EU
        else -> privacyScore(DataOut.Personal, Jurisdiction.Own) to Jurisdiction.Own
    }

    suspend fun members(context: Context, listId: Long): List<DavMember>? = TaskSharing.members(context, listId)
    suspend fun share(context: Context, listId: Long, who: String, edit: Boolean) = TaskSharing.share(context, listId, who, edit)
    suspend fun unshare(context: Context, listId: Long, member: DavMember) = TaskSharing.unshare(context, listId, member)
    suspend fun flushShares(cal: WebDav, store: SharedPreferences, listId: Long, slug: String) = TaskSharing.flushShares(cal, store, listId, slug)

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

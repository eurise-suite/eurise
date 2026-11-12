package eu.eurise.chat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * App chiusa: ogni 15 minuti, solo con la rete, una tornata di /sync senza attesa per sapere se ci sono messaggi nuovi.
 * Niente servizi sempre accesi; le notifiche istantanee arriveranno con UnifiedPush (libero, senza Google).
 */
class ChatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = SessionStore(ctx)
        val s = store.load() ?: return Result.success()
        val since = store.since
        val json = runCatching { ChatClient(s).sync(since, 0) }.getOrNull() ?: return Result.retry()
        store.since = json.optString("next_batch").ifEmpty { since }
        if (since == null) return Result.success()
        var count = 0
        var lastFrom = ""
        var lastText = ""
        json.optJSONObject("rooms")?.optJSONObject("join")?.let { join ->
            join.keys().forEach { id ->
                val events = Matrix.events(join.getJSONObject(id).optJSONObject("timeline")?.optJSONArray("events"))
                    .filter { it.sender != s.userId && it.kind != Event.Kind.State }
                count += events.size
                events.lastOrNull()?.let { e ->
                    lastFrom = Matrix.localpart(e.sender)
                    lastText = if (e.kind == Event.Kind.Encrypted) ctx.getString(R.string.ch_encrypted_short) else e.text
                }
            }
        }
        val invites = json.optJSONObject("rooms")?.optJSONObject("invite")?.length() ?: 0
        if (count > 0 || invites > 0) notify(ctx, count, invites, lastFrom, lastText)
        return Result.success()
    }

    companion object {
        private const val WORK = "eurise-chat-sync"
        private const val CHANNEL = "chat"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChatWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

        private fun notify(context: Context, count: Int, invites: Int, from: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.ch_channel), NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(context, 0, Intent(context, ChatActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val title = when {
                count == 1 -> from
                count > 1 -> context.getString(R.string.ch_new_many, count)
                else -> context.getString(R.string.ch_new_invites, invites)
            }
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
                .setContentTitle(title)
                .setContentText(if (count >= 1) text else "")
                .setContentIntent(open).setAutoCancel(true).build()
            runCatching { NotificationManagerCompat.from(context).notify(11, n) }
        }
    }
}

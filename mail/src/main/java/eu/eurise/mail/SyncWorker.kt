package eu.eurise.mail

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
 * Ogni 15 minuti, solo con la rete: c'è posta nuova? Chiede al server solo il numero dell'ultimo messaggio; le
 * intestazioni le scarica solo se ce n'è uno nuovo. Niente servizi sempre accesi (regola della batteria).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val accounts = AccountStore(ctx).load()
        var newOnes = 0
        var lastFrom = ""
        var lastSubject = ""
        for (a in accounts) {
            val client = MailClient(a)
            val known = lastKnown(ctx, a.id)
            val top = runCatching { client.lastUid() }.getOrNull() ?: continue
            if (top <= known) continue
            val list = runCatching { client.latest(20) }.getOrNull() ?: continue
            val fresh = list.filter { it.uid > known && !it.seen }
            if (known > 0) {
                val people = fresh.filter { it.kind == Kind.People }
                newOnes += people.size
                people.maxByOrNull { it.date }?.let { lastFrom = it.fromName; lastSubject = it.subject }
            }
            remember(ctx, a.id, top)
        }
        if (newOnes > 0) notify(ctx, newOnes, lastFrom, lastSubject)
        return Result.success()
    }

    companion object {
        private const val WORK = "eurise-mail-sync"
        private const val CHANNEL = "posta"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

        private fun prefs(context: Context) = context.getSharedPreferences("mail_sync", Context.MODE_PRIVATE)

        fun lastKnown(context: Context, id: String) = prefs(context).getLong(id, 0L)

        /** Fin qui la persona ha già visto (o avuto notizia): niente notifica per questi. */
        fun remember(context: Context, id: String, uid: Long) {
            if (uid > lastKnown(context, id)) prefs(context).edit().putLong(id, uid).apply()
        }

        /** Solo i messaggi delle persone fanno arrivare la notifica; avvisi e newsletter aspettano. */
        private fun notify(context: Context, count: Int, from: String, subject: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.ml_channel), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MailActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
                .setContentTitle(if (count == 1) from else context.getString(R.string.ml_new_many, count))
                .setContentText(if (count == 1) subject else context.getString(R.string.ml_new_last, from, subject))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(7, n) }
        }
    }
}

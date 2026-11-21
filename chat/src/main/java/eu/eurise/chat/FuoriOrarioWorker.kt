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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Il riepilogo del mattino (tavola SpaFuoriOrario): al primo minuto dell'orario di lavoro, «Ti aspettano 7 messaggi di
 * lavoro»; il tocco apre la Chat sull'account di lavoro, dove i gruppi con molti messaggi hanno il riassunto.
 */
class FuoriOrarioWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val n = ChatAccounts.waiting(ctx)
        ChatAccounts.clearWaiting(ctx)
        if (n > 0 && !ChatAccounts.quietNow(ctx, Account.Work)) notify(ctx, n)
        return Result.success()
    }

    companion object {
        private const val WORK = "eurise-chat-mattino"
        private const val CHANNEL = "mattino"

        /** All'inizio del prossimo orario di lavoro (se c'è già, resta quello). */
        fun schedule(context: Context) {
            val start = Orari.nextStart(ChatAccounts.rules(context).hours, ZonedDateTime.now()) ?: return
            val delay = (start.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0)
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FuoriOrarioWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
        }

        private fun notify(context: Context, n: Int) {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.fo_channel), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 50, Intent(context, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_ACCOUNT, Account.Work.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n2 = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_chat)
                .setContentTitle(context.getString(R.string.fo_morning, n)).setContentText(context.getString(R.string.fo_morning_sub))
                .setContentIntent(open).setAutoCancel(true).build()
            runCatching { NotificationManagerCompat.from(context).notify(60, n2) }
        }
    }
}

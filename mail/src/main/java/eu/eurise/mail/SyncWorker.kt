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
        val studio by lazy { eu.eurise.core.studio.StudioContract.known(ctx) }
        // Fuori orario (tavola SpaFuoriOrario): le caselle di lavoro aspettano, tranne chi passa lo stesso.
        val off = rules(ctx)
        val quiet = MailOffHours.quiet(off, java.time.LocalDateTime.now())
        var waiting = 0
        for (a in accounts) {
            val client = MailClient(a)
            val known = lastKnown(ctx, a.id)
            val top = runCatching { client.lastUid() }.getOrNull() ?: continue
            if (top <= known) continue
            val list = runCatching { client.latest(20) }.getOrNull() ?: continue
            val fresh = list.filter { it.uid > known && !it.seen }
            // Studio medico: le email dei pazienti entrano in coda anche ad app chiusa.
            if (fresh.isNotEmpty()) offerToStudio(ctx, studio, fresh)
            // Le risposte alle schede (tavola InsStessaScheda) si leggono anche ad app chiusa: la Chat le trova pronte.
            if (fresh.isNotEmpty()) runCatching { CardStore.collect(ctx, accounts, fresh) }
            // L'assenza (tavola LavStato): la risposta automatica alle persone, una volta al giorno.
            if (fresh.isNotEmpty()) runCatching { MailAbsence.reply(ctx, accounts, fresh, off.work) }
            if (known > 0) {
                val all = fresh.filter { it.kind == Kind.People }
                val people = if (quiet && a.id in off.work) all.filter { MailOffHours.passes(off, it.fromAddress, it.subject) } else all
                waiting += all.size - people.size
                newOnes += people.size
                people.maxByOrNull { it.date }?.let { lastFrom = it.fromName; lastSubject = it.subject }
            }
            remember(ctx, a.id, top)
        }
        if (newOnes > 0) notify(ctx, newOnes, lastFrom, lastSubject)
        if (waiting > 0) { prefs(ctx).edit().putInt("waiting", prefs(ctx).getInt("waiting", 0) + waiting).apply(); MorningWorker.schedule(ctx) }
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

        internal fun prefs(context: Context) = context.getSharedPreferences("mail_sync", Context.MODE_PRIVATE)

        fun rules(context: Context) = MailOffHours.decode(prefs(context).getString("off_hours", null))
        fun setRules(context: Context, r: MailOffHours.Rules) = prefs(context).edit().putString("off_hours", MailOffHours.encode(r)).apply()

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
                .setSmallIcon(eu.eurise.mail.R.drawable.ic_stat_mail)
                .setContentTitle(if (count == 1) from else context.getString(R.string.ml_new_many, count))
                .setContentText(if (count == 1) subject else context.getString(R.string.ml_new_last, from, subject))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(7, n) }
        }
    }
}

/** Il riepilogo del mattino della posta di lavoro: «Ti aspettano 3 email di lavoro». */
class MorningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val p = SyncWorker.prefs(ctx)
        val n = p.getInt("waiting", 0)
        p.edit().putInt("waiting", 0).apply()
        if (n <= 0) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mattino", ctx.getString(R.string.ml_off_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MailActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val note = NotificationCompat.Builder(ctx, "mattino").setSmallIcon(R.drawable.ic_stat_mail)
            .setContentTitle(ctx.getString(R.string.ml_off_morning, n)).setContentIntent(open).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(8, note) }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val now = java.time.LocalDateTime.now()
            val at = MailOffHours.nextStart(SyncWorker.rules(context), now) ?: return
            val delay = java.time.Duration.between(now, at).toMillis().coerceAtLeast(0)
            WorkManager.getInstance(context).enqueueUniqueWork("eurise-mail-mattino", androidx.work.ExistingWorkPolicy.KEEP,
                androidx.work.OneTimeWorkRequestBuilder<MorningWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
        }
    }
}

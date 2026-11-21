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
 * App chiusa: ogni 15 minuti, solo con la rete, una tornata di /sync senza attesa per sapere se ci sono messaggi nuovi,
 * per ognuno dei due account (personale e lavoro). Niente servizi sempre accesi; le notifiche istantanee arriveranno con
 * UnifiedPush (libero, senza Google).
 */
class ChatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val results = Account.entries.map { sync(applicationContext, it) }
        return if (results.any { !it }) Result.retry() else Result.success()
    }

    /** Una tornata per [account]; false se la rete non ha risposto. */
    private suspend fun sync(ctx: Context, account: Account): Boolean {
        val store = SessionStore(ctx, account)
        val s = store.load() ?: return true
        // App aperta su questo account: sincronizza lei (e tiene lei le chiavi), qui non serve. L'altro account sì.
        if (ChatModel.active && ChatAccounts.active(ctx) == account) return true
        val since = store.since
        val client = ChatClient(s)
        val json = runCatching { client.sync(since, 0) }.getOrNull() ?: return false
        store.since = json.optString("next_batch").ifEmpty { since }
        // Le chiavi delle stanze arrivate ad app chiusa vanno al modulo crittografico, se no si perderebbero.
        val crypto = runCatching { ChatCrypto(ctx, s) }.getOrNull()
        if (crypto != null) {
            runCatching { crypto.receiveSync(json) }
            json.optJSONObject("rooms")?.optJSONObject("join")?.let { join ->
                join.keys().forEach { roomId ->
                    val a = join.getJSONObject(roomId).optJSONObject("timeline")?.optJSONArray("events") ?: return@forEach
                    for (i in 0 until a.length()) {
                        val e = a.getJSONObject(i)
                        if (e.optString("type") == "m.room.encrypted") crypto.decrypt(e, roomId)?.let { a.put(i, it) }
                    }
                }
            }
            runCatching { crypto.flush(client) }
        }
        // Tavola ChatProgramma: conferme di lettura per i messaggi a scomparsa, programmati arrivati all'ora, cancellazioni.
        runCatching { ProgrammaStore.receipts(ctx, account, s.userId, json) }
        runCatching { ProgrammaStore.work(ctx, account, client, crypto) }
        crypto?.let { runCatching { it.flush(client) } }
        // Studio medico: i messaggi dei pazienti arrivati ad app chiusa entrano in coda (i codici di verifica pure).
        if (StudioRepo.current(ctx).enabled) json.optJSONObject("rooms")?.optJSONObject("join")?.let { join ->
            join.keys().forEach { id ->
                Matrix.events(join.getJSONObject(id).optJSONObject("timeline")?.optJSONArray("events"))
                    .filter { it.sender != s.userId }
                    .forEach { e ->
                        val text = ChatModel.studioText(e) ?: return@forEach
                        StudioRepo.receive(ctx, eu.eurise.core.studio.Channel.Chat, e.sender, text, e.time, "$id|${e.id}")
                    }
            }
        }
        // I referti mandati da qui che hanno compiuto 45 giorni si cancellano dalla stanza.
        runCatching { BussolaReports.redactDue(ctx, client) }
        if (since == null) return true
        var count = 0
        var lastFrom = ""
        var lastText = ""
        // Notifiche senza dati sanitari: nelle stanze di La mia Bussola e per i messaggi dei pazienti dello Studio
        // solo la frase fissa, mai il testo né il nome.
        val bussolaRooms = BussolaRooms.all(ctx)
        val studio = StudioRepo.current(ctx)
        var patientSide = false
        var studioSide = false
        json.optJSONObject("rooms")?.optJSONObject("join")?.let { join ->
            join.keys().forEach { id ->
                val events = Matrix.events(join.getJSONObject(id).optJSONObject("timeline")?.optJSONArray("events"))
                val bussola = id in bussolaRooms || events.any { it.kind == Event.Kind.Bussola }
                if (bussola) BussolaRooms.add(ctx, id)
                val others = events.filter { it.sender != s.userId && it.kind != Event.Kind.State && it.kind != Event.Kind.Beacon }
                count += others.size
                others.forEach { e ->
                    val patient = studio.enabled && studio.patients.any { p -> p.matrixId.equals(e.sender, ignoreCase = true) }
                    if (patient) studioSide = true else if (bussola) patientSide = true
                }
                others.lastOrNull()?.let { e ->
                    lastFrom = Matrix.localpart(e.sender)
                    lastText = if (e.kind == Event.Kind.Encrypted) ctx.getString(R.string.ch_encrypted_short) else e.text
                }
            }
        }
        when {
            studioSide -> { lastFrom = ctx.getString(R.string.st_title); lastText = ctx.getString(R.string.bu_notice_studio); count = maxOf(count, 1) }
            patientSide -> { lastFrom = ctx.getString(R.string.bu_notice_title); lastText = eu.eurise.core.studio.Bussola.NOTICE }
        }
        val neutral = studioSide || patientSide
        val invites = json.optJSONObject("rooms")?.optJSONObject("invite")?.length() ?: 0
        if (count > 0 || invites > 0) notify(ctx, account, if (neutral) 1 else count, invites, lastFrom, lastText)
        return true
    }

    companion object {
        private const val WORK = "eurise-chat-sync"
        private const val CHANNEL = "chat"
        /** Il lavoro fuori orario: notifica senza suono né banner, si trova nella tendina la mattina. */
        private const val CHANNEL_QUIET = "chat_quiet"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChatWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

        private fun notify(context: Context, account: Account, count: Int, invites: Int, from: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.ch_channel), NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_QUIET, context.getString(R.string.ch_channel_quiet), NotificationManager.IMPORTANCE_LOW))
            val quiet = ChatAccounts.quietNow(context, account)
            // La notifica apre l'app sull'account giusto.
            val open = PendingIntent.getActivity(context, account.ordinal,
                Intent(context, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_ACCOUNT, account.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val title = when {
                count == 1 -> from
                count > 1 -> context.getString(R.string.ch_new_many, count)
                else -> context.getString(R.string.ch_new_invites, invites)
            }
            val two = ChatAccounts.signedIn(context).size > 1
            val n = NotificationCompat.Builder(context, if (quiet) CHANNEL_QUIET else CHANNEL)
                .setSmallIcon(eu.eurise.chat.R.drawable.ic_stat_chat)
                .apply { if (two) setSubText(context.getString(if (account == Account.Work) R.string.ch_acc_work else R.string.ch_acc_personal)) }
                .setContentTitle(title)
                .setContentText(if (count >= 1) text else "")
                .setContentIntent(open).setAutoCancel(true).build()
            runCatching { NotificationManagerCompat.from(context).notify(11 + account.ordinal, n) }
        }
    }
}

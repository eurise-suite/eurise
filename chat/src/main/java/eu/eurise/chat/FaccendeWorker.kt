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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Il promemoria delle faccende (tavola FamFaccende), 30 minuti prima, a chi tocca. Senza rete: la Chat lascia sul telefono
 * la copia delle faccende dei gruppi (aggiornata a ogni giro di sincronizzazione) e qui si sceglie il prossimo promemoria.
 */
class FaccendeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val now = LocalDateTime.now()
        snapshot(ctx).forEach { (me, room, list) ->
            list.filter { Faccende.whose(it) == me && Faccende.dueOn(it, now.toLocalDate()) && Faccende.status(it, now, { t -> Programma.toMs(t) }) == Faccende.Status.Todo }
                .filter { val at = now.toLocalDate().atTime(Faccende.time(it)); !now.isBefore(at.minusMinutes(Faccende.REMIND_BEFORE_MIN + 5)) && now.isBefore(at.plusHours(2)) }
                .forEach { notify(ctx, room, it) }
        }
        schedule(ctx, if (WORK in tags) WORK_NEXT else WORK, after = now.plusMinutes(10))
        return Result.success()
    }

    companion object {
        private const val WORK = "eurise-chat-faccende"
        private const val WORK_NEXT = "eurise-chat-faccende-2"
        private const val CHANNEL = "faccende"
        private const val PREFS = "chat_chores"

        /** La copia delle faccende in cui ci sei, per [account]: (te, nome del gruppo, faccende). */
        fun save(context: Context, account: Account, me: String, rooms: Map<String, Pair<String, List<Faccende.Chore>>>) {
            val a = JSONArray()
            rooms.forEach { (_, v) -> if (v.second.any { me in it.people }) a.put(JSONObject().put("me", me).put("room", v.first).put("chores", Faccende.encode(v.second))) }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(account.name, null) == a.toString()) return
            prefs.edit().putString(account.name, a.toString()).apply()
            schedule(context, null)
        }

        private fun snapshot(context: Context): List<Triple<String, String, List<Faccende.Chore>>> {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Account.entries.flatMap { acc ->
                runCatching {
                    val a = JSONArray(prefs.getString(acc.name, null) ?: return@runCatching emptyList())
                    (0 until a.length()).map { i -> a.getJSONObject(i).let { Triple(it.optString("me"), it.optString("room"), Faccende.decode(it.optJSONObject("chores"))) } }
                }.getOrDefault(emptyList())
            }
        }

        /** Il prossimo promemoria fra tutte le faccende; nessuno se non tocca mai a te. */
        fun schedule(context: Context, name: String?, after: LocalDateTime = LocalDateTime.now()) {
            val wm = WorkManager.getInstance(context)
            val at = snapshot(context).mapNotNull { (me, _, list) -> Faccende.nextReminder(list, me, after) { Programma.toMs(it) }?.second }.minOrNull()
            if (name == null) wm.cancelUniqueWork(WORK_NEXT)
            val unique = name ?: WORK
            if (at == null) { if (name == null) wm.cancelUniqueWork(WORK); return }
            val delay = (Programma.toMs(at) - System.currentTimeMillis()).coerceAtLeast(0)
            wm.enqueueUniqueWork(unique, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FaccendeWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).addTag(unique).build())
        }

        private fun notify(context: Context, room: String, c: Faccende.Chore) {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.fa_channel), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 40, Intent(context, ChatActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_chat)
                .setContentTitle(context.getString(R.string.fa_notify_title, c.title, c.time))
                .setContentText(room).setContentIntent(open).setAutoCancel(true).build()
            runCatching { NotificationManagerCompat.from(context).notify(400 + (c.id.hashCode() and 0xff), n) }
        }
    }
}

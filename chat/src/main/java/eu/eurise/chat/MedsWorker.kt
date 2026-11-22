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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * I farmaci di un genitore (tavola FamGenitori) ad app chiusa. All'ora della dose: a chi li prende con Eurise arriva la notifica;
 * se ha un telefono semplice, dal telefono di chi li ha messi parte l'SMS (da Eurise Messaggi) «rispondi 1 quando l'hai presa».
 * Mezz'ora dopo, se non c'è la conferma (un tocco nella Chat o «1» per SMS), avvisa chi li ha messi. La risposta «1» si legge
 * da Messaggi, sul telefono, e segna la dose come presa per tutti.
 */
class MedsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val sent = ctx.getSharedPreferences(PREFS + "_sent", Context.MODE_PRIVATE)
        snapshot(ctx).forEach { item ->
            var m = item.meds
            Casa.today(m, today).forEach { (med, taken) ->
                val at = today.atTime(time(med))
                val key = Casa.dose(med.id, today)
                if (taken != null || now.isBefore(at.minusMinutes(1))) return@forEach
                // All'ora: notifica a chi li prende (con Eurise), SMS se ha un telefono semplice.
                if (now.isBefore(at.plusMinutes(m.escalateMin.toLong())) && !sent.getBoolean("$key|r", false)) {
                    if (m.user == item.me) notify(ctx, 500 + (med.id.hashCode() and 0xff), ctx.getString(R.string.md_notify, med.name, med.time), item.roomName)
                    if (m.by == item.me && m.phone.isNotBlank())
                        runCatching { eu.eurise.core.suite.PeopleContract.sendSms(ctx, m.phone, ctx.getString(R.string.md_sms, med.name, med.time)) }
                    sent.edit().putBoolean("$key|r", true).apply()
                }
                // Mezz'ora dopo: la risposta «1» per SMS, o l'avviso a chi li ha messi.
                if (m.by == item.me && !now.isBefore(at.plusMinutes(m.escalateMin.toLong())) && !sent.getBoolean("$key|e", false)) {
                    val confirmed = m.phone.isNotBlank() && smsConfirmed(ctx, m.phone, Programma.toMs(at))
                    if (confirmed) {
                        m = Casa.take(m, med.id, today, System.currentTimeMillis())
                        runCatching { saveRemote(ctx, item, m) }
                    } else notify(ctx, 600 + (med.id.hashCode() and 0xff), ctx.getString(R.string.md_missed, m.person, med.name, med.time), item.roomName)
                    sent.edit().putBoolean("$key|e", true).apply()
                }
            }
        }
        schedule(ctx, now.plusMinutes(1))
        return Result.success()
    }

    data class Item(val account: Account, val me: String, val roomId: String, val roomName: String, val meds: Casa.Meds)

    companion object {
        private const val WORK = "eurise-chat-farmaci"
        private const val PREFS = "chat_meds"
        private const val CHANNEL = "farmaci"

        fun time(m: Casa.Med): LocalTime = runCatching { LocalTime.parse(m.time) }.getOrDefault(LocalTime.of(8, 0))

        fun save(context: Context, account: Account, me: String, rooms: List<Triple<String, String, Casa.Meds>>) {
            val a = JSONArray()
            rooms.forEach { (id, name, m) -> a.put(JSONObject().put("me", me).put("id", id).put("room", name).put("meds", Casa.encodeMeds(m))) }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(account.name, null) == a.toString()) return
            prefs.edit().putString(account.name, a.toString()).apply()
            schedule(context, LocalDateTime.now())
        }

        private fun snapshot(context: Context): List<Item> {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Account.entries.flatMap { acc ->
                runCatching {
                    val a = JSONArray(prefs.getString(acc.name, null) ?: return@runCatching emptyList())
                    (0 until a.length()).mapNotNull { i -> a.getJSONObject(i).let { o ->
                        Casa.decodeMeds(o.optJSONObject("meds"))?.let { Item(acc, o.optString("me"), o.optString("id"), o.optString("room"), it) } } }
                }.getOrDefault(emptyList())
            }
        }

        /** Il prossimo momento utile: l'ora di una dose o la mezz'ora dopo. */
        fun schedule(context: Context, after: LocalDateTime) {
            val today = after.toLocalDate()
            val times = snapshot(context).flatMap { item ->
                listOf(today, today.plusDays(1)).flatMap { d -> item.meds.meds.flatMap { med ->
                    val at = d.atTime(time(med)); listOf(at, at.plusMinutes(item.meds.escalateMin.toLong())) } }
            }.filter { it.isAfter(after) }
            val wm = WorkManager.getInstance(context)
            val next = times.minOrNull() ?: run { wm.cancelUniqueWork(WORK); return }
            val delay = (Programma.toMs(next) - System.currentTimeMillis()).coerceAtLeast(0)
            wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<MedsWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
        }

        /** Un SMS di conferma da quel numero arrivato dopo l'ora della dose (da Eurise Messaggi, sul telefono). */
        private fun smsConfirmed(context: Context, phone: String, since: Long): Boolean = runCatching {
            eu.eurise.core.suite.PeopleContract.items(context, eu.eurise.core.suite.PeopleContract.App.Messages, listOf(phone), emptyList(), 20)
                .any { it.kind == eu.eurise.core.suite.PeopleContract.Kind.SmsIn && it.time >= since && Casa.confirms(it.text) }
        }.getOrDefault(false)

        /** La dose presa per SMS si segna nello stato della stanza, per tutti. */
        private fun saveRemote(context: Context, item: Item, m: Casa.Meds) {
            val s = SessionStore(context, item.account).load() ?: return
            ChatClient(s).setState(item.roomId, Casa.MEDS, Casa.encodeMeds(m))
        }

        private fun notify(context: Context, id: Int, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.md_channel), NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(context, 41, Intent(context, ChatActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_chat).setContentTitle(title).setContentText(text)
                .setContentIntent(open).setAutoCancel(true).build()
            runCatching { NotificationManagerCompat.from(context).notify(id, n) }
        }

        @Suppress("unused") private fun day(d: LocalDate) = d.toEpochDay()
    }
}

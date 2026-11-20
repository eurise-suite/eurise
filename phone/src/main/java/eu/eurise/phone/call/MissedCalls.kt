package eu.eurise.phone.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.telecom.TelecomManager
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.contacts.Reach
import eu.eurise.phone.PhoneActivity
import eu.eurise.phone.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Le chiamate perse. Quando Eurise Telefono è l'app predefinita, Android le affida a lei
 * (`ACTION_SHOW_MISSED_CALLS_NOTIFICATION`): se nessuno risponde, il sistema mette la notifica sua, che apre il Telefono
 * di sistema (su /e/OS quello di Murena, che chiede di tornare predefinito). Qui la notifica è nostra e apre i Recenti.
 *
 * Il ricevitore è aperto (lo chiama il sistema), quindi non si fida di ciò che arriva: conta e nomi li legge dal
 * registro delle chiamate sul telefono; dall'esterno si può solo far ricontrollare il registro.
 */
class MissedCallsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0) <= 0) MissedCalls.cancel(context)
                else MissedCalls.show(context)
            } finally {
                pending.finish()
            }
        }
    }
}

object MissedCalls {
    private const val CHANNEL = "missed_calls"
    private const val ID = 2

    /** Le chiamate perse non ancora viste, dal registro (la più recente per prima). */
    private fun unseen(context: Context): List<String> = runCatching {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER),
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1", arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
            "${CallLog.Calls.DATE} DESC",
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getString(0).orEmpty()) } }.orEmpty()
    }.getOrDefault(emptyList())

    suspend fun show(context: Context) {
        val missed = unseen(context)
        if (missed.isEmpty()) { cancel(context); return }
        val last = missed.first()
        val who = Contacts.byNumber(context, last)?.name ?: last.takeIf { it.isNotBlank() }?.let(Contacts::format)
            ?: context.getString(R.string.ph_private_number)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.ph_channel_missed), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 20, Intent(context, PhoneActivity::class.java).setAction(PhoneActivity.ACTION_RECENTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (missed.size == 1) context.getString(R.string.ph_missed_one) else context.getString(R.string.ph_missed_many, missed.size)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(title)
            .setContentText(who)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setNumber(missed.size)
        if (last.isNotBlank()) {
            // Richiama chiama subito (dal ricevitore non esportato), SMS apre l'app dei messaggi.
            val back = PendingIntent.getBroadcast(
                context, 21, Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_CALL_BACK)
                    .setData(Uri.fromParts("tel", last, null)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val sms = PendingIntent.getActivity(context, 22, Reach.sms(last).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.ph_call_back_action), back).build())
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.ph_sms), sms).build())
        }
        runCatching { nm.notify(ID, builder.build()) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    /**
     * Aprendo i Recenti le chiamate perse sono viste: si toglie la notifica e si avvisa il sistema (che poi non la
     * rimette). Da app predefinita il sistema lo permette; altrimenti si toglie solo la nostra.
     */
    fun seen(context: Context) {
        cancel(context)
        runCatching { context.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification() }
        // Nel registro le perse diventano «viste», così la prossima notifica conta solo le nuove (fuori dal thread principale).
        val app = context.applicationContext
        Thread {
            runCatching {
                app.contentResolver.update(
                    CallLog.Calls.CONTENT_URI, android.content.ContentValues().apply { put(CallLog.Calls.NEW, 0) },
                    "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1", arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                )
            }
        }.start()
    }
}

package eu.eurise.phone.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import eu.eurise.phone.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notifica della chiamata: in arrivo (a schermo intero, con Rispondi e Rifiuta) o in corso (con Chiudi). Una sola,
 * aggiornata quando cambia lo stato. Il nome arriva dalla rubrica, sul telefono.
 */
object CallNotifications {
    private const val CHANNEL_INCOMING = "incoming"
    /** «ongoing» era a importanza bassa (finiva tra le silenziose, in fondo alla tendina): il canale nuovo la tiene in vista. */
    private const val CHANNEL_ONGOING = "ongoing_call"
    private const val OLD_ONGOING = "ongoing"
    private const val ID = 1
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_INCOMING, context.getString(R.string.ph_channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
            // Suona il sistema: il canale non aggiunge un altro suono.
            setSound(null, null)
            enableVibration(false)
        })
        nm.deleteNotificationChannel(OLD_ONGOING)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.ph_channel_ongoing), NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            enableVibration(false)
        })
    }

    fun cancel(context: Context) {
        (context as? android.app.Service ?: CallManager.service)?.let { runCatching { it.stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE) } }
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    fun update(context: Context) {
        val item = CallManager.primary()
        if (item == null || item.state == Call.STATE_DISCONNECTED) {
            cancel(context)
            return
        }
        scope.launch {
            val name = eu.eurise.core.contacts.Contacts.byNumber(context, item.number)?.name
            val title = name ?: item.number.ifEmpty { context.getString(R.string.ph_private_number) }
            val suspect = if (name != null) null else eu.eurise.phone.data.SpamFilter.suspect(
                item.number, eu.eurise.phone.data.SpamFilter.suspectPrefixes(context), eu.eurise.phone.data.SpamFilter.countryCode(context),
            )
            post(context, item, title, suspect)
        }
    }

    private fun pending(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        context, code, Intent(context, CallActionReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(context: Context, item: CallManager.Item, title: String, suspect: String? = null) {
        channels(context)
        val ringing = item.state == Call.STATE_RINGING
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Rispondi apre direttamente la schermata (da Android 12 una notifica non può aprire un'attività passando da un ricevitore).
        val answer = PendingIntent.getActivity(
            context, 1, Intent(context, CallActivity::class.java).setAction(CallActivity.ACTION_ANSWER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val decline = pending(context, CallActionReceiver.ACTION_DECLINE, 2)
        val hangup = pending(context, CallActionReceiver.ACTION_HANGUP, 3)
        val builder = Notification.Builder(context, if (ringing) CHANNEL_INCOMING else CHANNEL_ONGOING)
            .setSmallIcon(eu.eurise.phone.R.drawable.ic_stat_call)
            .setContentTitle(title)
            .setContentText(
                context.getString(if (ringing) R.string.ph_incoming else R.string.ph_ongoing) +
                    (suspect?.let { " · " + context.getString(R.string.ph_suspect_spam, it) } ?: ""),
            )
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (ringing) builder.setFullScreenIntent(open, true)
        // In arrivo: stile «chiamata» con lo schermo intero. In corso: il servizio delle chiamate passa in primo piano con
        // lo stile «chiamata in corso», così Android mostra la pillola verde nella barra di stato che riporta alla
        // chiamata (da Android 12). Se il sistema non lo permette resta la notifica normale, sempre in vista.
        if (!ringing && Build.VERSION.SDK_INT >= 31 && foreground(context, builder, title, hangup, item)) return
        if (Build.VERSION.SDK_INT >= 31 && ringing) {
            val person = Person.Builder().setName(title).setImportant(true).build()
            builder.setStyle(Notification.CallStyle.forIncomingCall(person, decline, answer))
        } else if (ringing) {
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.ph_decline), decline).build())
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.ph_answer), answer).build())
        } else {
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.ph_hangup), hangup).build())
        }
        if (!ringing && item.connectedAt > 0) builder.setUsesChronometer(true).setWhen(item.connectedAt)
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID, builder.build()) }
    }

    @android.annotation.SuppressLint("NewApi")
    private fun foreground(context: Context, builder: Notification.Builder, title: String, hangup: PendingIntent, item: CallManager.Item): Boolean {
        val service = CallManager.service ?: return false
        return runCatching {
            val person = Person.Builder().setName(title).setImportant(true).build()
            builder.setStyle(Notification.CallStyle.forOngoingCall(person, hangup))
            if (item.connectedAt > 0) builder.setUsesChronometer(true).setWhen(item.connectedAt)
            val n = builder.build()
            if (Build.VERSION.SDK_INT >= 34) service.startForeground(ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            else service.startForeground(ID, n)
        }.onFailure { builder.setStyle(null) }.isSuccess
    }
}

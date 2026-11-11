package eu.eurise.messages.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.data.messages.MessageInsights
import eu.eurise.messages.MessagesActivity
import eu.eurise.messages.R
import eu.eurise.messages.data.SmsStore
import kotlinx.coroutines.launch

/**
 * Notifiche dei messaggi: una per conversazione, con Rispondi (senza aprire l'app) e Letto; per i codici di verifica
 * anche Copia. Il testo resta sul telefono; sulla schermata di blocco si vede solo «Nuovo messaggio».
 */
object Notifications {
    private const val CHANNEL = "messages"
    private const val CHANNEL_CODES = "codes"
    private const val CHANNEL_FAILED = "failed"
    const val EXTRA_THREAD = "thread"
    const val EXTRA_ADDRESS = "address"
    const val KEY_REPLY = "reply"

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.ms_channel_messages), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_CODES, context.getString(R.string.ms_channel_codes), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_FAILED, context.getString(R.string.ms_channel_failed), NotificationManager.IMPORTANCE_DEFAULT))
    }

    suspend fun incoming(context: Context, address: String, text: String, threadId: Long) {
        channels(context)
        val name = Contacts.byNumber(context, address)?.name ?: address
        val code = MessageInsights.code(text)
        val id = threadId.toInt()
        val open = PendingIntent.getActivity(context, id,
            Intent(context, MessagesActivity::class.java).putExtra(EXTRA_THREAD, threadId).putExtra(EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(action: String, code: Int, mutable: Boolean = false) = PendingIntent.getBroadcast(context, id * 10 + code,
            Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_THREAD, threadId).putExtra(EXTRA_ADDRESS, address)
                .putExtra(Intent.EXTRA_TEXT, this.codeOrNull(code, text)),
            (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE) or PendingIntent.FLAG_UPDATE_CURRENT)
        val sender = Person.Builder().setName(name).build()
        val style = Notification.MessagingStyle(Person.Builder().setName(context.getString(R.string.ms_you)).build())
            .addMessage(text, System.currentTimeMillis(), sender)
        val builder = Notification.Builder(context, if (code != null) CHANNEL_CODES else CHANNEL)
            .setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, CHANNEL).setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
                    .setContentTitle(context.getString(R.string.ms_new_message)).build(),
            )
        if (code != null) {
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.ms_copy_code, code), action(NotificationActionReceiver.ACTION_COPY, 3)).build())
        } else {
            val input = RemoteInput.Builder(KEY_REPLY).setLabel(context.getString(R.string.ms_reply)).build()
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.ms_reply), action(NotificationActionReceiver.ACTION_REPLY, 1, mutable = true))
                .addRemoteInput(input).setAllowGeneratedReplies(false).build())
        }
        builder.addAction(Notification.Action.Builder(null, context.getString(R.string.ms_mark_read), action(NotificationActionReceiver.ACTION_READ, 2)).build())
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, builder.build()) }
    }

    private fun codeOrNull(kind: Int, text: String) = if (kind == 3) MessageInsights.code(text) else null

    fun cancel(context: Context, threadId: Long) = context.getSystemService(NotificationManager::class.java).cancel(threadId.toInt())

    fun failed(context: Context) {
        channels(context)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MessagesActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(context, CHANNEL_FAILED)
            .setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
            .setContentTitle(context.getString(R.string.ms_not_sent))
            .setContentText(context.getString(R.string.ms_not_sent_text))
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(Int.MAX_VALUE, n) }
    }
}

/** Pulsanti delle notifiche: rispondi, segna come letto, copia il codice. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(Notifications.EXTRA_THREAD, -1)
        val address = intent.getStringExtra(Notifications.EXTRA_ADDRESS).orEmpty()
        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifications.KEY_REPLY)?.toString().orEmpty()
                if (text.isNotBlank() && address.isNotBlank()) {
                    val pending = goAsync()
                    receiverScope.launch {
                        try {
                            Sender.sendSms(context, listOf(address), text)
                            SmsStore.markRead(context, threadId)
                        } finally {
                            Notifications.cancel(context, threadId)
                            pending.finish()
                        }
                    }
                }
            }
            ACTION_READ -> {
                val pending = goAsync()
                receiverScope.launch {
                    try { SmsStore.markRead(context, threadId) } finally { Notifications.cancel(context, threadId); pending.finish() }
                }
            }
            ACTION_COPY -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { code ->
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("codice", code))
                }
                Notifications.cancel(context, threadId)
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "eu.eurise.messages.REPLY"
        const val ACTION_READ = "eu.eurise.messages.READ"
        const val ACTION_COPY = "eu.eurise.messages.COPY"
    }
}

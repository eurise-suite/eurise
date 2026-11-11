package eu.eurise.messages.system

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.pdu_alt.RetrieveConf
import eu.eurise.messages.data.SmsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

internal val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** SMS ricevuto (solo l'app predefinita lo riceve): si salva, si avvisa, e un codice si programma per la cancellazione. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val address = parts[0].displayOriginatingAddress ?: parts[0].originatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
        val sentAt = parts[0].timestampMillis
        val subId = intent.getIntExtra("subscription", intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1))
        val pending = goAsync()
        receiverScope.launch {
            try {
                val uri = SmsStore.saveIncoming(context, address, body, sentAt, subId)
                Notifications.incoming(context, address, body, SmsStore.threadId(context, setOf(address)))
                if (uri != null) Codes.scheduleCleanup(context, uri, body)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Avviso di un MMS (WAP push): si legge dove sta il messaggio e lo si fa scaricare al servizio MMS del sistema;
 * quando arriva, [MmsDownloadedReceiver] lo salva.
 */
class MmsWapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        val pdu = runCatching { PduParser(data, true).parse() }.getOrNull() as? NotificationInd ?: return
        val location = pdu.contentLocation?.let { String(it) } ?: return
        val subId = intent.getIntExtra("subscription", -1)
        val file = File(File(context.cacheDir, "mms").apply { mkdirs() }, "recv-${System.currentTimeMillis()}.pdu")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".mms", file)
        val done = PendingIntent.getBroadcast(context, file.hashCode(),
            Intent(context, MmsDownloadedReceiver::class.java).putExtra(Sender.EXTRA_FILE, file.path).putExtra("sub", subId),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        context.grantUriPermission("com.android.phone", uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        @Suppress("DEPRECATION")
        val manager = if (subId >= 0) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
        runCatching { manager.downloadMultimediaMessage(context, location, uri, null, done) }
    }
}

/** MMS scaricato: si legge il file, si salva nella posta in arrivo degli MMS e si avvisa. */
class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra(Sender.EXTRA_FILE) ?: return
        val subId = intent.getIntExtra("sub", -1)
        val pending = goAsync()
        receiverScope.launch {
            val file = File(path)
            try {
                if (resultCode != Activity.RESULT_OK || !file.exists()) return@launch
                val pdu = PduParser(file.readBytes(), true).parse() as? RetrieveConf ?: return@launch
                val saved = PduPersister.getPduPersister(context).persist(pdu, Telephony.Mms.Inbox.CONTENT_URI, true, true, null, subId)
                val from = pdu.from?.string.orEmpty()
                val text = (0 until (pdu.body?.partsNum ?: 0)).mapNotNull { i ->
                    pdu.body.getPart(i)?.takeIf { String(it.contentType ?: ByteArray(0)).startsWith("text/") }?.data?.let { String(it) }
                }.joinToString("\n")
                val threadId = context.contentResolver.query(saved, arrayOf(Telephony.Mms.THREAD_ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
                Notifications.incoming(context, from, text.ifBlank { context.getString(eu.eurise.messages.R.string.ms_photo) }, threadId ?: -1L)
            } catch (_: Throwable) {
                // MMS illeggibile: il sistema lo riproverà o resterà l'avviso dell'operatore.
            } finally {
                file.delete()
                pending.finish()
            }
        }
    }
}

/** Esito dell'invio di SMS e MMS: il messaggio diventa «mandato», «consegnato» o «non mandato». */
class SentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.getStringExtra(Sender.EXTRA_URI)?.let(Uri::parse) ?: return
        val ok = resultCode == Activity.RESULT_OK
        when (intent.action) {
            Sender.ACTION_SMS_SENT -> if (!ok) SmsStore.setSmsType(context, uri, Telephony.Sms.MESSAGE_TYPE_FAILED)
                else if (intent.getBooleanExtra(Sender.EXTRA_LAST, true)) SmsStore.setSmsType(context, uri, Telephony.Sms.MESSAGE_TYPE_SENT)
            Sender.ACTION_SMS_DELIVERED -> if (intent.getBooleanExtra(Sender.EXTRA_LAST, true)) SmsStore.setDelivered(context, uri)
            Sender.ACTION_MMS_SENT -> {
                intent.getStringExtra(Sender.EXTRA_FILE)?.let { File(it).delete() }
                runCatching {
                    PduPersister.getPduPersister(context).move(uri, if (ok) Telephony.Mms.Sent.CONTENT_URI else Telephony.Mms.Outbox.CONTENT_URI)
                    if (!ok) context.contentResolver.update(uri, android.content.ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_FAILED) }, null, null)
                }
            }
        }
        if (!ok && intent.action != Sender.ACTION_SMS_DELIVERED) Notifications.failed(context)
    }
}

/** Risposta veloce dal sistema (es. «Rifiuta con un messaggio» durante una chiamata): manda un SMS senza aprire l'app. */
class HeadlessSmsSendService : android.app.Service() {
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val number = intent?.data?.schemeSpecificPart?.substringBefore('?').orEmpty()
        if (text.isNotBlank() && number.isNotBlank()) runCatching { Sender.sendSms(this, number.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }, text) }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

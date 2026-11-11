package eu.eurise.messages.system

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.FileProvider
import com.google.android.mms.ContentType
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.pdu_alt.SendReq
import eu.eurise.messages.data.SmsStore
import java.io.File

/**
 * Manda SMS e MMS con le API di sistema (SmsManager): il messaggio si salva prima in uscita e diventa «mandato» o «non
 * mandato» quando risponde la rete ([SentReceiver]). Gli MMS si compongono con il codice AOSP (PduComposer) e partono
 * dal servizio MMS del sistema, che usa la rete dell'operatore: Eurise non ha permessi Internet.
 */
object Sender {
    const val ACTION_SMS_SENT = "eu.eurise.messages.SMS_SENT"
    const val ACTION_SMS_DELIVERED = "eu.eurise.messages.SMS_DELIVERED"
    const val ACTION_MMS_SENT = "eu.eurise.messages.MMS_SENT"
    const val EXTRA_URI = "uri"
    const val EXTRA_LAST = "last"
    const val EXTRA_FILE = "file"

    @Suppress("DEPRECATION")
    private fun manager(context: Context, subId: Int = -1): SmsManager = when {
        Build.VERSION.SDK_INT >= 31 -> context.getSystemService(SmsManager::class.java).let { if (subId >= 0) it.createForSubscriptionId(subId) else it }
        subId >= 0 -> SmsManager.getSmsManagerForSubscriptionId(subId)
        else -> SmsManager.getDefault()
    }

    /** Manda un SMS (anche lungo, in più parti) a ogni numero; ritorna la conversazione. */
    fun sendSms(context: Context, addresses: List<String>, text: String): Long {
        val threadId = SmsStore.threadId(context, addresses.toSet())
        for (address in addresses) {
            val uri = SmsStore.saveOutgoing(context, address, text, threadId) ?: continue
            val sms = manager(context, SubscriptionManager.getDefaultSmsSubscriptionId())
            val parts = sms.divideMessage(text)
            val sent = ArrayList<PendingIntent>()
            val delivered = ArrayList<PendingIntent>()
            parts.indices.forEach { i ->
                val last = i == parts.size - 1
                sent += PendingIntent.getBroadcast(context, (uri.hashCode() * 31 + i),
                    Intent(context, SentReceiver::class.java).setAction(ACTION_SMS_SENT).putExtra(EXTRA_URI, uri.toString()).putExtra(EXTRA_LAST, last),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                delivered += PendingIntent.getBroadcast(context, (uri.hashCode() * 31 + i + 7919),
                    Intent(context, SentReceiver::class.java).setAction(ACTION_SMS_DELIVERED).putExtra(EXTRA_URI, uri.toString()).putExtra(EXTRA_LAST, last),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            runCatching {
                if (parts.size > 1) sms.sendMultipartTextMessage(address, null, parts, sent, delivered)
                else sms.sendTextMessage(address, null, text, sent.first(), delivered.first())
            }.onFailure { SmsStore.setSmsType(context, uri, Telephony.Sms.MESSAGE_TYPE_FAILED) }
        }
        return threadId
    }

    /**
     * Manda un MMS con un'immagine o un file ([attachment]) e un testo facoltativo. Si salva nella posta in uscita degli
     * MMS e parte dal servizio MMS del sistema.
     */
    fun sendMms(context: Context, addresses: List<String>, text: String, attachment: Uri?, mime: String?): Long {
        val threadId = SmsStore.threadId(context, addresses.toSet())
        val req = SendReq()
        addresses.forEach { req.addTo(EncodedStringValue(it)) }
        req.setDate(System.currentTimeMillis() / 1000)
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray())
        req.setExpiry(7 * 24 * 3600L)
        req.setPriority(PduHeaders.PRIORITY_NORMAL)
        req.setDeliveryReport(PduHeaders.VALUE_NO)
        req.setReadReport(PduHeaders.VALUE_NO)
        val body = PduBody()
        var size = 0L
        if (text.isNotBlank()) {
            val part = PduPart()
            part.setCharset(CharacterSets.UTF_8)
            part.setContentType(ContentType.TEXT_PLAIN.toByteArray())
            part.setContentLocation("text.txt".toByteArray())
            part.setContentId("<text>".toByteArray())
            part.setData(text.toByteArray(Charsets.UTF_8))
            body.addPart(part)
            size += part.getData().size
        }
        if (attachment != null) {
            val bytes = Attachments.prepare(context, attachment, mime) ?: error("allegato non leggibile")
            val part = PduPart()
            part.setContentType(bytes.second.toByteArray())
            val name = "file." + (bytes.second.substringAfter('/').substringBefore(';').ifEmpty { "bin" })
            part.setContentLocation(name.toByteArray())
            part.setContentId("<file>".toByteArray())
            part.setName(name.toByteArray())
            part.setData(bytes.first)
            body.addPart(part)
            size += bytes.first.size
        }
        req.setBody(body)
        req.setMessageSize(size)
        req.setContentType(ContentType.MULTIPART_MIXED.toByteArray())
        val persisted = PduPersister.getPduPersister(context).persist(req, Telephony.Mms.Outbox.CONTENT_URI, true, false, null, -1)
        runCatching {
            context.contentResolver.update(persisted, ContentValues().apply { put(Telephony.Mms.THREAD_ID, threadId) }, null, null)
        }
        val pdu = PduComposer(context, req).make() ?: error("MMS non composto")
        val dir = File(context.cacheDir, "mms").apply { mkdirs() }
        val file = File(dir, "send-${System.currentTimeMillis()}.pdu").apply { writeBytes(pdu) }
        val contentUri = FileProvider.getUriForFile(context, context.packageName + ".mms", file)
        val sent = PendingIntent.getBroadcast(context, file.hashCode(),
            Intent(context, SentReceiver::class.java).setAction(ACTION_MMS_SENT).putExtra(EXTRA_URI, persisted.toString()).putExtra(EXTRA_FILE, file.path),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        grantToMmsService(context, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        manager(context, SubscriptionManager.getDefaultSmsSubscriptionId()).sendMultimediaMessage(context, contentUri, null, null, sent)
        return threadId
    }

    /** Il servizio MMS del sistema sta in «com.android.phone» o in «com.android.mms.service», secondo il telefono. */
    fun grantToMmsService(context: Context, uri: Uri, flags: Int) {
        for (pkg in listOf("com.android.phone", "com.android.mms.service")) runCatching { context.grantUriPermission(pkg, uri, flags) }
    }
}

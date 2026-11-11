package eu.eurise.messages.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una conversazione: i numeri di chi c'è (più di uno nei gruppi), l'ultimo testo, se ci sono messaggi da leggere. */
data class Thread(val id: Long, val addresses: List<String>, val snippet: String, val date: Long, val unread: Boolean, val count: Int)

/** Un messaggio (SMS o MMS). [incoming] vero per quelli ricevuti; [attachments] sono le immagini e i file di un MMS. */
data class Message(
    val id: Long,
    val mms: Boolean,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val incoming: Boolean,
    val status: Status,
    val read: Boolean,
    val attachments: List<Attachment> = emptyList(),
) {
    enum class Status { Received, Sending, Sent, Delivered, Failed }
    /** Chiave stabile per le liste (SMS e MMS hanno id separati). */
    val key get() = (if (mms) "m" else "s") + id
}

data class Attachment(val uri: Uri, val type: String, val name: String?)

/**
 * Il database dei messaggi di sistema (Telephony): Eurise non ne tiene una copia. Leggere è permesso a chi ha READ_SMS;
 * scrivere solo all'app predefinita per gli SMS. Le funzioni sono sospese su [Dispatchers.IO].
 */
object SmsStore {
    private val CANONICAL = Uri.parse("content://mms-sms/canonical-addresses")
    private val THREADS = Uri.parse("content://mms-sms/conversations?simple=true")
    private val MMS_PART = Uri.parse("content://mms/part")

    suspend fun threads(context: Context): List<Thread> = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val addresses = HashMap<Long, String>()
        runCatching {
            cr.query(CANONICAL, arrayOf("_id", "address"), null, null, null)?.use { c ->
                while (c.moveToNext()) addresses[c.getLong(0)] = c.getString(1).orEmpty()
            }
        }
        val out = ArrayList<Thread>()
        runCatching {
            cr.query(
                THREADS, arrayOf(Telephony.Threads._ID, Telephony.Threads.DATE, Telephony.Threads.SNIPPET, Telephony.Threads.RECIPIENT_IDS,
                    Telephony.Threads.READ, Telephony.Threads.MESSAGE_COUNT),
                null, null, "${Telephony.Threads.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(5) == 0) continue
                    val ids = c.getString(3).orEmpty().split(' ').mapNotNull { it.toLongOrNull() }
                    out += Thread(c.getLong(0), ids.mapNotNull { addresses[it] }, c.getString(2).orEmpty(), c.getLong(1), c.getInt(4) == 0, c.getInt(5))
                }
            }
        }
        out
    }

    /** I messaggi di una conversazione, dal più vecchio al più recente (al massimo gli ultimi [limit]). */
    suspend fun messages(context: Context, threadId: Long, limit: Int = 400): List<Message> = withContext(Dispatchers.IO) {
        val out = ArrayList<Message>()
        val cr = context.contentResolver
        cr.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.STATUS, Telephony.Sms.READ),
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val type = c.getInt(4)
                val status = when (type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> Message.Status.Received
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> Message.Status.Sending
                    Telephony.Sms.MESSAGE_TYPE_FAILED -> Message.Status.Failed
                    else -> if (c.getInt(5) == Telephony.Sms.STATUS_COMPLETE) Message.Status.Delivered else Message.Status.Sent
                }
                out += Message(c.getLong(0), false, threadId, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getLong(3),
                    type == Telephony.Sms.MESSAGE_TYPE_INBOX, status, c.getInt(6) == 1)
            }
        }
        runCatching {
            cr.query(
                Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ),
                "${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Mms.DATE} DESC",
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < limit) {
                    val id = c.getLong(0)
                    val box = c.getInt(2)
                    val incoming = box == Telephony.Mms.MESSAGE_BOX_INBOX
                    val (text, files) = parts(context, id)
                    val status = when (box) {
                        Telephony.Mms.MESSAGE_BOX_INBOX -> Message.Status.Received
                        Telephony.Mms.MESSAGE_BOX_OUTBOX -> Message.Status.Sending
                        Telephony.Mms.MESSAGE_BOX_FAILED -> Message.Status.Failed
                        else -> Message.Status.Sent
                    }
                    out += Message(id, true, threadId, mmsAddress(context, id, incoming), text, c.getLong(1) * 1000, incoming, status, c.getInt(3) == 1, files)
                }
            }
        }
        out.sortedBy { it.date }.takeLast(limit)
    }

    /** Testo e allegati di un MMS. */
    private fun parts(context: Context, mmsId: Long): Pair<String, List<Attachment>> {
        val text = StringBuilder()
        val files = ArrayList<Attachment>()
        context.contentResolver.query(
            MMS_PART, arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT, Telephony.Mms.Part.NAME, Telephony.Mms.Part.FILENAME),
            "${Telephony.Mms.Part.MSG_ID} = ?", arrayOf(mmsId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getString(1).orEmpty()
                when {
                    type == "application/smil" -> Unit
                    type.startsWith("text/") -> c.getString(2)?.let { if (text.isNotEmpty()) text.append('\n'); text.append(it) }
                    else -> files += Attachment(Uri.parse("content://mms/part/${c.getLong(0)}"), type, c.getString(4) ?: c.getString(3))
                }
            }
        }
        return text.toString() to files
    }

    /** Chi ha mandato l'MMS (ricevuto) o a chi è andato (mandato). */
    private fun mmsAddress(context: Context, mmsId: Long, incoming: Boolean): String {
        val type = if (incoming) 137 else 151 // PduHeaders.FROM / TO
        return context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/addr"), arrayOf("address", "type"), "type = ?", arrayOf(type.toString()), null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() else "" }.orEmpty().takeIf { it != "insert-address-token" }.orEmpty()
    }

    /** La conversazione con questi numeri (la crea se non c'è). */
    fun threadId(context: Context, addresses: Set<String>): Long = Telephony.Threads.getOrCreateThreadId(context, addresses)

    /** Salva un SMS ricevuto (solo l'app predefinita può farlo). Ritorna l'indirizzo del messaggio salvato. */
    fun saveIncoming(context: Context, address: String, body: String, sentAt: Long, subId: Int): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, sentAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.THREAD_ID, threadId(context, setOf(address)))
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        return runCatching { context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }.getOrNull()
    }

    /** Salva un SMS in uscita (in coda) prima di mandarlo. */
    fun saveOutgoing(context: Context, address: String, body: String, threadId: Long): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.THREAD_ID, threadId)
        }
        return runCatching { context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values) }.getOrNull()
    }

    fun setSmsType(context: Context, uri: Uri, type: Int) {
        runCatching { context.contentResolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null) }
    }

    fun setDelivered(context: Context, uri: Uri) {
        runCatching { context.contentResolver.update(uri, ContentValues().apply { put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE) }, null, null) }
    }

    suspend fun markRead(context: Context, threadId: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }
        runCatching { context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString())) }
        runCatching {
            context.contentResolver.update(Telephony.Mms.CONTENT_URI, ContentValues().apply { put(Telephony.Mms.READ, 1); put(Telephony.Mms.SEEN, 1) },
                "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(threadId.toString()))
        }
    }

    suspend fun deleteThread(context: Context, threadId: Long) = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(Uri.parse("content://mms-sms/conversations/$threadId"), null, null) }
    }

    suspend fun deleteMessage(context: Context, m: Message) = withContext(Dispatchers.IO) {
        val uri = if (m.mms) Uri.parse("content://mms/${m.id}") else Uri.parse("content://sms/${m.id}")
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    fun deleteSms(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /** Ultimo messaggio ricevuto di una conversazione (per le proposte nell'elenco). */
    suspend fun lastIncoming(context: Context, threadId: Long): Message? = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}", arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c: Cursor -> if (c.moveToFirst()) Message(c.getLong(0), false, threadId, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getLong(3), true, Message.Status.Received, true) else null }
    }

    /** Vero se due numeri sono lo stesso («+39 347…» e «347…»). */
    fun same(a: String, b: String) = PhoneNumberUtils.compare(a, b)
}

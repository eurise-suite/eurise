package eu.eurise.messages.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import eu.eurise.core.suite.SuiteBackupProvider
import eu.eurise.messages.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Messaggi nel backup della suite (area Backup, 21 novembre 2026). Gli SMS e gli MMS stanno nel database di Android, non
 * nostro: si leggono da lì (`sms.json`, e per ogni MMS `mms/<n>/meta.json` con i suoi pezzi `mms/<n>/<pezzo>`), più le
 * impostazioni di Messaggi.
 *
 * Al ripristino Android li fa scrivere solo all'app SMS predefinita: finché Messaggi non lo è, [ready] dice di no ed
 * Eurise tiene i dati sigillati e riprova. I messaggi già presenti (stesso numero, ora e testo) non si doppiano.
 */
class MessagesBackupProvider : SuiteBackupProvider() {
    override fun ready(context: Context) = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    override fun summary(context: Context): String? {
        val sms = count(context, Telephony.Sms.CONTENT_URI)
        val mms = count(context, Telephony.Mms.CONTENT_URI)
        if (sms == 0 && mms == 0) return null
        return context.getString(R.string.backup_summary_messages, sms, mms)
    }

    override fun export(context: Context, zip: ZipOutputStream) {
        val r = context.contentResolver
        zip.putNextEntry(ZipEntry(SMS_ENTRY))
        val w = zip.bufferedWriter()
        w.write("[")
        var first = true
        r.query(Telephony.Sms.CONTENT_URI, SMS_COLUMNS, null, null, "${Telephony.Sms.DATE} ASC")?.use { c ->
            while (c.moveToNext()) {
                if (!first) w.write(",")
                first = false
                w.write(row(c).toString())
            }
        }
        w.write("]")
        w.flush()
        zip.closeEntry()

        var n = 0
        r.query(Telephony.Mms.CONTENT_URI, null, null, null, "${Telephony.Mms.DATE} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(Telephony.Mms._ID))
                val meta = JSONObject()
                MMS_COLUMNS.forEach { col -> c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let { meta.put(col, value(c, it)) } }
                val addrs = JSONArray()
                r.query(Uri.parse("content://mms/$id/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE, Telephony.Mms.Addr.CHARSET), null, null, null)?.use { a ->
                    while (a.moveToNext()) addrs.put(JSONObject().put("address", a.getString(0)).put("type", a.getInt(1)).put("charset", a.getInt(2)))
                }
                meta.put("addr", addrs)
                val parts = JSONArray()
                val data = mutableListOf<Pair<Long, Int>>()
                r.query(Uri.parse("content://mms/part"), null, "${Telephony.Mms.Part.MSG_ID} = ?", arrayOf(id.toString()), null)?.use { p ->
                    var i = 0
                    while (p.moveToNext()) {
                        val part = JSONObject()
                        PART_COLUMNS.forEach { col -> p.getColumnIndex(col).takeIf { it >= 0 && !p.isNull(it) }?.let { part.put(col, value(p, it)) } }
                        val hasData = !p.isNull(p.getColumnIndexOrThrow(Telephony.Mms.Part._DATA))
                        if (hasData) { part.put("file", i); data += p.getLong(p.getColumnIndexOrThrow(Telephony.Mms.Part._ID)) to i }
                        parts.put(part)
                        i++
                    }
                }
                meta.put("parts", parts)
                zip.putNextEntry(ZipEntry("mms/$n/meta.json"))
                zip.write(meta.toString().toByteArray())
                zip.closeEntry()
                data.forEach { (partId, i) ->
                    runCatching {
                        r.openInputStream(Uri.parse("content://mms/part/$partId"))?.use { input ->
                            zip.putNextEntry(ZipEntry("mms/$n/$i"))
                            input.copyTo(zip)
                            zip.closeEntry()
                        }
                    }
                }
                n++
            }
        }
        addPrefs(context, zip, *PREFS.toTypedArray())
    }

    override fun restore(context: Context, zip: ZipInputStream) {
        val r = context.contentResolver
        val have = HashSet<String>()
        r.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY), null, null, null)?.use { c ->
            while (c.moveToNext()) have += key(c.getString(0), c.getLong(1), c.getString(2))
        }
        val haveMms = HashSet<Long>()
        r.query(Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms.DATE), null, null, null)?.use { c -> while (c.moveToNext()) haveMms += c.getLong(0) }

        // L'MMS in corso: le sue righe di pezzi, per scrivere i dati man mano che arrivano.
        var partUris = emptyMap<Int, Uri>()
        var current = -1
        while (true) {
            val e = zip.nextEntry ?: break
            val name = safeName(e) ?: continue
            when {
                name == SMS_ENTRY -> {
                    val list = JSONArray(zip.bufferedReader().readText())
                    for (i in 0 until list.length()) {
                        val o = list.getJSONObject(i)
                        val k = key(o.optString(Telephony.Sms.ADDRESS), o.optLong(Telephony.Sms.DATE), o.optString(Telephony.Sms.BODY))
                        if (!have.add(k)) continue
                        val v = ContentValues()
                        SMS_COLUMNS.filter { it != Telephony.Sms._ID && o.has(it) }.forEach { col -> put(v, col, o.get(col)) }
                        runCatching { r.insert(Telephony.Sms.CONTENT_URI, v) }
                    }
                }
                name.startsWith("mms/") && name.endsWith("/meta.json") -> {
                    current = name.split('/')[1].toIntOrNull() ?: -1
                    partUris = restoreMms(context, JSONObject(zip.bufferedReader().readText()), haveMms)
                }
                name.startsWith("mms/") -> {
                    val (n, i) = name.split('/').let { it.getOrNull(1)?.toIntOrNull() to it.getOrNull(2)?.toIntOrNull() }
                    if (n == current && i != null) partUris[i]?.let { uri -> runCatching { r.openOutputStream(uri)?.use { zip.copyTo(it) } } }
                }
                else -> restorePrefs(context, zip, name, PREFS)
            }
        }
    }

    /** Rimette un MMS (riga, indirizzi, pezzi senza dati); torna gli indirizzi dei pezzi che aspettano i loro dati. */
    private fun restoreMms(context: Context, meta: JSONObject, have: HashSet<Long>): Map<Int, Uri> {
        val r = context.contentResolver
        val date = meta.optLong(Telephony.Mms.DATE)
        if (!have.add(date)) return emptyMap()
        val addrs = meta.optJSONArray("addr") ?: JSONArray()
        val recipients = (0 until addrs.length()).map { addrs.getJSONObject(it).optString("address") }
            .filter { it.isNotBlank() && it != "insert-address-token" }.toSet()
        if (recipients.isEmpty()) return emptyMap()
        val v = ContentValues()
        MMS_COLUMNS.filter { meta.has(it) }.forEach { put(v, it, meta.get(it)) }
        v.put(Telephony.Mms.THREAD_ID, Telephony.Threads.getOrCreateThreadId(context, recipients))
        val mms = runCatching { r.insert(Telephony.Mms.CONTENT_URI, v) }.getOrNull() ?: return emptyMap()
        val id = mms.lastPathSegment ?: return emptyMap()
        for (i in 0 until addrs.length()) {
            val a = addrs.getJSONObject(i)
            runCatching {
                r.insert(Uri.parse("content://mms/$id/addr"), ContentValues().apply {
                    put(Telephony.Mms.Addr.ADDRESS, a.optString("address"))
                    put(Telephony.Mms.Addr.TYPE, a.optInt("type"))
                    put(Telephony.Mms.Addr.CHARSET, a.optInt("charset", 106))
                })
            }
        }
        val out = mutableMapOf<Int, Uri>()
        val parts = meta.optJSONArray("parts") ?: JSONArray()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            val pv = ContentValues()
            PART_COLUMNS.filter { p.has(it) }.forEach { put(pv, it, p.get(it)) }
            val uri = runCatching { r.insert(Uri.parse("content://mms/$id/part"), pv) }.getOrNull() ?: continue
            if (p.has("file")) out[p.getInt("file")] = uri
        }
        return out
    }

    private fun row(c: Cursor) = JSONObject().apply {
        SMS_COLUMNS.forEach { col -> c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let { put(col, value(c, it)) } }
    }

    private fun value(c: Cursor, i: Int): Any = when (c.getType(i)) {
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        else -> c.getString(i)
    }

    private fun put(v: ContentValues, col: String, value: Any) {
        when (value) {
            is Int -> v.put(col, value)
            is Long -> v.put(col, value)
            is Double -> v.put(col, value)
            is Boolean -> v.put(col, value)
            else -> v.put(col, value.toString())
        }
    }

    private fun key(address: String?, date: Long, body: String?) = "${address.orEmpty()}\u0000$date\u0000${body.orEmpty()}"

    private fun count(context: Context, uri: Uri): Int =
        runCatching { context.contentResolver.query(uri, arrayOf("_id"), null, null, null)?.use { it.count } }.getOrNull() ?: 0

    companion object {
        private const val SMS_ENTRY = "sms.json"
        private val PREFS = setOf("messages")
        private val SMS_COLUMNS = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT, Telephony.Sms.TYPE,
            Telephony.Sms.READ, Telephony.Sms.SEEN, Telephony.Sms.STATUS, Telephony.Sms.SUBJECT, Telephony.Sms.BODY,
            Telephony.Sms.LOCKED, Telephony.Sms.SERVICE_CENTER, Telephony.Sms.PROTOCOL, Telephony.Sms.REPLY_PATH_PRESENT,
        )
        private val MMS_COLUMNS = listOf(
            Telephony.Mms.DATE, Telephony.Mms.DATE_SENT, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ, Telephony.Mms.SEEN,
            Telephony.Mms.SUBJECT, Telephony.Mms.SUBJECT_CHARSET, Telephony.Mms.CONTENT_TYPE, Telephony.Mms.MESSAGE_TYPE,
            Telephony.Mms.MMS_VERSION, Telephony.Mms.MESSAGE_CLASS, Telephony.Mms.PRIORITY, Telephony.Mms.TRANSACTION_ID,
            Telephony.Mms.MESSAGE_ID, Telephony.Mms.LOCKED, Telephony.Mms.TEXT_ONLY, Telephony.Mms.MESSAGE_SIZE,
        )
        private val PART_COLUMNS = listOf(
            Telephony.Mms.Part.SEQ, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.NAME, Telephony.Mms.Part.CHARSET,
            Telephony.Mms.Part.FILENAME, Telephony.Mms.Part.CONTENT_DISPOSITION, Telephony.Mms.Part.CONTENT_ID,
            Telephony.Mms.Part.CONTENT_LOCATION, Telephony.Mms.Part.TEXT,
        )
    }
}

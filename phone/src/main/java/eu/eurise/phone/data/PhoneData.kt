package eu.eurise.phone.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.BlockedNumberContract
import android.provider.CalendarContract
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import eu.eurise.core.contacts.Contact
import eu.eurise.core.contacts.Contacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Una riga dei recenti: chiamate di fila dello stesso numero e dello stesso tipo, raccolte insieme. */
data class CallEntry(
    val number: String,
    val type: Int,
    val date: Long,
    val duration: Long,
    val count: Int,
    val contact: Contact?,
    val blocked: Boolean,
) {
    val missed get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
}

/** Un impegno del Calendario con la persona, per «Prima di rispondere». */
data class Engagement(val title: String, val start: LocalDateTime, val location: String?)

/** Registro delle chiamate, chiamate da richiamare, numeri bloccati e impegni con una persona. Tutto sul telefono. */
object PhoneData {
    fun has(context: Context, permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Le ultime chiamate (fino a [limit] righe dopo il raggruppamento). */
    suspend fun recents(context: Context, limit: Int = 200): List<CallEntry> = withContext(Dispatchers.IO) {
        if (!has(context, Manifest.permission.READ_CALL_LOG)) return@withContext emptyList()
        data class Raw(val number: String, val type: Int, val date: Long, val duration: Long)
        val raw = ArrayList<Raw>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
            null, null, "${CallLog.Calls.DATE} DESC",
        )?.use { c -> while (raw.size < 600 && c.moveToNext()) raw += Raw(c.getString(0).orEmpty(), c.getInt(1), c.getLong(2), c.getLong(3)) }
        val grouped = ArrayList<Pair<Raw, Int>>()
        for (r in raw) {
            val last = grouped.lastOrNull()
            if (last != null && last.first.type == r.type && same(last.first.number, r.number) &&
                Instant.ofEpochMilli(last.first.date).atZone(ZoneId.systemDefault()).toLocalDate() ==
                Instant.ofEpochMilli(r.date).atZone(ZoneId.systemDefault()).toLocalDate()
            ) grouped[grouped.size - 1] = last.first to last.second + 1
            else grouped += r to 1
            if (grouped.size >= limit) break
        }
        val names = Contacts.names(context, grouped.map { it.first.number }.filter { it.isNotBlank() })
        val blocked = blockedNumbers(context)
        grouped.map { (r, n) ->
            CallEntry(r.number, r.type, r.date, r.duration, n, names[r.number], blocked.any { same(it, r.number) })
        }
    }

    /**
     * Da richiamare: chiamate perse negli ultimi tre giorni a cui non è seguita nessuna chiamata con lo stesso numero
     * (né fatta né ricevuta). I numeri bloccati non contano.
     */
    fun toCallBack(recents: List<CallEntry>, now: Long = System.currentTimeMillis()): List<CallEntry> {
        val out = ArrayList<CallEntry>()
        val seen = ArrayList<String>()
        for (e in recents) { // dal più recente
            if (e.number.isBlank() || seen.any { same(it, e.number) }) continue
            seen += e.number
            if (e.missed && !e.blocked && now - e.date < 3 * 86_400_000L) {
                val total = recents.filter { same(it.number, e.number) && it.missed && it.date >= e.date - 86_400_000L }.sumOf { it.count }
                out += e.copy(count = total)
            }
        }
        return out
    }

    fun same(a: String, b: String) = a.isNotBlank() && b.isNotBlank() && PhoneNumberUtils.compare(a, b)

    /** Numeri bloccati di sistema (leggibili solo dall'app per le chiamate o per gli SMS). */
    suspend fun blockedNumbers(context: Context): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) return@runCatching emptyList()
            val out = ArrayList<String>()
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null,
            )?.use { c -> while (c.moveToNext()) c.getString(0)?.let(out::add) }
            out
        }.getOrDefault(emptyList())
    }

    /** Blocca o sblocca un numero: il sistema rifiuta da solo le chiamate e gli SMS dei numeri bloccati. */
    suspend fun setBlocked(context: Context, number: String, block: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (block) {
                context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    android.content.ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) })
            } else {
                BlockedNumberContract.unblock(context, number)
            }
            true
        }.getOrDefault(false)
    }

    /** Toglie dal registro le chiamate con questo numero. */
    suspend fun deleteFromLog(context: Context, number: String) = withContext(Dispatchers.IO) {
        if (!has(context, Manifest.permission.WRITE_CALL_LOG)) return@withContext
        runCatching { context.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls.NUMBER} = ?", arrayOf(number)) }
    }

    /**
     * Impegni nei prossimi 30 giorni che nominano la persona (titolo, luogo, descrizione o invitati), dal Calendario di
     * sistema: lo stesso che usa l'app Eurise. Solo con il permesso del calendario, facoltativo.
     */
    suspend fun engagements(context: Context, name: String, emails: List<String> = emptyList()): List<Engagement> = withContext(Dispatchers.IO) {
        if (name.isBlank() || !has(context, Manifest.permission.READ_CALENDAR)) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + 30 * 86_400_000L)
        }.build()
        val first = name.trim().substringBefore(' ')
        val needles = listOf(name.trim(), first).filter { it.length >= 3 }.distinct()
        val out = ArrayList<Engagement>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.EVENT_ID),
                null, null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < 3) {
                    val title = c.getString(0).orEmpty()
                    val hay = listOf(title, c.getString(2).orEmpty(), c.getString(3).orEmpty()).joinToString(" ")
                    val byName = needles.any { Regex("(?iu)(?<![\\p{L}])" + Regex.escape(it) + "(?![\\p{L}])").containsMatchIn(hay) }
                    val byGuest = !byName && emails.isNotEmpty() && attends(context, c.getLong(4), emails)
                    if (byName || byGuest) out += Engagement(title, LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(1)), ZoneId.systemDefault()), c.getString(2))
                }
            }
        }
        out
    }

    private fun attends(context: Context, eventId: Long, emails: List<String>): Boolean = runCatching {
        context.contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI, arrayOf(CalendarContract.Attendees.ATTENDEE_EMAIL),
            "${CalendarContract.Attendees.EVENT_ID} = ?", arrayOf(eventId.toString()), null,
        )?.use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.any { e -> emails.any { it.equals(e, true) } } } ?: false
    }.getOrDefault(false)
}

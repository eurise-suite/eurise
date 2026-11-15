package eu.eurise.feature.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Dove Alfred cerca per rispondere alle domande sui dati (tavola AlfDati): Calendario, Attività, Note e registro delle
 * chiamate, tutto sul telefono e senza rete. Le frasi le scrive [DataWriter].
 */
internal class Records(private val context: Context, private val data: EuriseData) {

    suspend fun answer(ask: Command.Ask, now: LocalDateTime = LocalDateTime.now()): DataAnswer = withContext(Dispatchers.IO) {
        when (ask.kind) {
            AskKind.Agenda -> {
                if (!data.calendar.hasPermission()) return@withContext DataWriter.agenda(ask, now, emptyList(), tasksOn(ask, now)).copy(needs = Manifest.permission.READ_CALENDAR)
                val from = ask.day ?: now.toLocalDate()
                val to = from.plusDays(ask.days.toLong() - 1)
                val events = runCatching { data.calendar.observeEvents(from, to).first() }.getOrDefault(emptyList())
                    .filter { !it.start.toLocalDate().isAfter(to) && (it.allDay || !it.start.toLocalDate().isBefore(from) || it.end.toLocalDate().isAfter(from)) }
                    .map { it.toData() }
                DataWriter.agenda(ask, now, events, tasksOn(ask, now))
            }
            AskKind.When -> {
                val words = DataWriter.keywords(ask.about).ifEmpty { listOf(ask.about.lowercase()) }
                val longest = words.maxByOrNull { it.length } ?: ask.about
                val events = if (data.calendar.hasPermission()) runCatching { data.calendar.search(longest, 12) }.getOrDefault(emptyList())
                    .filter { e -> words.all { w -> Butler.fold(e.title + " " + e.location).contains(Butler.fold(w)) } }
                    .map { it.toData() } else emptyList()
                val tasks = runCatching { data.tasks.search(longest, 8) }.getOrDefault(emptyList()).filter { !it.done }
                    .filter { t -> words.all { w -> Butler.fold(t.title).contains(Butler.fold(w)) } }
                val names = listNames()
                DataWriter.whenIs(ask, now, events, tasks.map { DataTask(it.title, it.due, names[it.listId].orEmpty()) })
                    .let { if (!data.calendar.hasPermission()) it.copy(needs = Manifest.permission.READ_CALENDAR) else it }
            }
            AskKind.Calls -> {
                if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                    return@withContext DataWriter.calls(ask, now, emptyList()).copy(needs = Manifest.permission.READ_CALL_LOG, empty = true)
                }
                DataWriter.calls(ask, now, calls(ask, now))
            }
            AskKind.Notes -> {
                val words = DataWriter.keywords(ask.about).ifEmpty { listOf(ask.about) }
                // Si cerca la parola più lunga, poi si tengono le note che le hanno tutte (o quasi, se sono tre o più).
                val longest = words.maxByOrNull { it.length }.orEmpty()
                val found = runCatching { data.notes.observe(longest).first() }.getOrDefault(emptyList())
                val need = if (words.size >= 3) words.size - 1 else words.size
                val notes = found.filter { n -> val t = Butler.fold(n.title + " " + n.body); words.count { t.contains(Butler.fold(it)) } >= need }
                    .ifEmpty { found }.take(4)
                    .map { n -> DataNote(n.id, n.title, DataWriter.snippet(n.title.takeIf { n.body.isBlank() } ?: n.body, words)) }
                val tasks = runCatching { data.tasks.search(longest, 4) }.getOrDefault(emptyList()).filter { !it.done }
                val names = listNames()
                DataWriter.notes(ask, notes, tasks.map { DataTask(it.title, it.due, names[it.listId].orEmpty()) })
            }
        }
    }

    /** Le cose da fare del giorno (oggi anche quelle in ritardo) o della settimana. */
    private suspend fun tasksOn(ask: Command.Ask, now: LocalDateTime): List<DataTask> {
        val today = now.toLocalDate()
        val from = ask.day ?: today
        val to = from.plusDays(ask.days.toLong() - 1)
        val list = runCatching {
            if (from == today) data.tasks.observeDueBy(to).first() else data.tasks.observeDueBetween(from, to).first()
        }.getOrDefault(emptyList()).filter { !it.done && it.parentId == null }
        val names = listNames()
        return list.sortedWith(compareBy({ it.due }, { it.time })).take(8).map { DataTask(it.title, it.due, names[it.listId].orEmpty()) }
    }

    private suspend fun listNames(): Map<Long, String> =
        runCatching { data.tasks.observeLists().first().associate { it.list.id to it.list.name } }.getOrDefault(emptyMap())

    /** Le chiamate ricevute e perse del giorno chiesto, o delle ultime 24 ore. */
    private fun calls(ask: Command.Ask, now: LocalDateTime): List<DataCall> {
        val zone = ZoneId.systemDefault()
        val (from, to) = ask.day?.let { it.atStartOfDay(zone).toInstant().toEpochMilli() to it.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
            ?: (now.minusHours(24).atZone(zone).toInstant().toEpochMilli() to Long.MAX_VALUE)
        val types = listOf(CallLog.Calls.INCOMING_TYPE, CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE)
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} < ? AND ${CallLog.Calls.TYPE} IN (${types.joinToString()})",
                arrayOf(from.toString(), to.toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext() && size < 12) {
                        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(2)), zone)
                        add(DataCall(c.getString(0).orEmpty(), c.getString(1).orEmpty(), at, c.getInt(3) != CallLog.Calls.INCOMING_TYPE))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    private fun CalendarEvent.toData() = DataEvent(title, start, allDay, location, calendarName, color, eventId, beginMillis, movable = !recurring && writable)
}


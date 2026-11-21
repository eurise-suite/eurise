package eu.eurise.feature.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.suite.PeopleContract
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
            AskKind.Messages -> messages(ask, now)
        }
    }

    /**
     * Gli SMS da leggere (tavola AlfAzioni, «Messaggi»): li dà Eurise Messaggi alle sole app della suite. Prima quelli
     * non letti, uno o più per persona; senza non letti, gli ultimi arrivati. Con [Command.Ask.about] solo quella persona.
     */
    private fun messages(ask: Command.Ask, now: LocalDateTime): DataAnswer {
        val recent = PeopleContract.recent(context, PeopleContract.App.Messages)
        if (recent.isEmpty()) return DataWriter.messages(ask, now, emptyList(), available = installed())
        val zone = ZoneId.systemDefault()
        val addresses = recent.map { it.item.address }
        val (people, index) = runCatching { PeopleContract.resolve(context, addresses.map { PeopleContract.tel(it) }) }.getOrDefault(emptyList<PeopleContract.Person>() to emptyList())
        val nameOf = addresses.mapIndexed { i, a -> a to index.getOrNull(i)?.takeIf { it >= 0 }?.let { people.getOrNull(it)?.name }.orEmpty() }.toMap()
        val wanted = ask.about.takeIf { it.isNotBlank() }?.let { Butler.fold(it).split(' ') }
        val pool = recent.filter { r -> wanted == null || Butler.fold(nameOf[r.item.address].orEmpty()).split(' ').let { n -> wanted.all { w -> n.any { it.startsWith(w) } } } }
        val unread = pool.filter { it.unread > 0 }.sortedByDescending { it.item.time }
        val chosen = unread.ifEmpty { pool.filter { it.item.kind == PeopleContract.Kind.SmsIn }.sortedByDescending { it.item.time }.take(if (wanted != null) 1 else 3) }.take(4)
        val list = chosen.map { r ->
            val many = r.unread.coerceIn(1, 3)
            val texts = PeopleContract.items(context, PeopleContract.App.Messages, listOf(r.item.address), emptyList(), limit = 12)
                .filter { it.kind == PeopleContract.Kind.SmsIn }.takeLast(many).map { it.text }.filter { it.isNotBlank() }
                .ifEmpty { listOf(r.item.text) }
            DataMessage(nameOf[r.item.address].orEmpty(), r.item.address, LocalDateTime.ofInstant(Instant.ofEpochMilli(r.item.time), zone), texts, r.unread)
        }
        list.firstOrNull()?.let { LastMessages.read(it.name.ifBlank { it.number }) }
        return DataWriter.messages(ask, now, list, available = true, unreadTotal = unread.sumOf { it.unread })
    }

    private fun installed() = runCatching {
        context.packageManager.getPackageInfo(PeopleContract.packageOf(context, PeopleContract.App.Messages), 0); true
    }.getOrDefault(false)

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

/**
 * Di chi sono gli ultimi messaggi letti da Alfred, per «rispondi che arrivo» (dieci minuti, solo in memoria). Senza,
 * si risponde a chi ha scritto per ultimo.
 */
internal object LastMessages {
    private const val WINDOW_MS = 10 * 60 * 1000L
    @Volatile private var who: String? = null
    @Volatile private var at = 0L

    fun read(name: String) { who = name; at = System.currentTimeMillis() }

    fun sender(context: android.content.Context): String? {
        who?.takeIf { System.currentTimeMillis() - at <= WINDOW_MS }?.let { return it }
        val last = PeopleContract.recent(context, PeopleContract.App.Messages)
            .filter { it.item.kind == PeopleContract.Kind.SmsIn }.maxByOrNull { it.item.time } ?: return null
        val (people, index) = runCatching { PeopleContract.resolve(context, listOf(PeopleContract.tel(last.item.address))) }.getOrNull() ?: return last.item.address
        return index.firstOrNull()?.takeIf { it >= 0 }?.let { people.getOrNull(it)?.name } ?: last.item.address
    }
}

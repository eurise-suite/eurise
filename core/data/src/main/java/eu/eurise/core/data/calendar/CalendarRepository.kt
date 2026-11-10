package eu.eurise.core.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Accesso ai calendari del telefono tramite il Calendar Provider di Android.
 *
 * Su /e/OS i calendari di Murena sono già sincronizzati nel sistema: leggendo e scrivendo qui,
 * gli appuntamenti arrivano sul cloud senza un secondo collegamento. Tutte le funzioni restituiscono
 * risultati vuoti (o falliscono senza effetti) se il permesso del calendario non è stato concesso.
 */
class CalendarRepository internal constructor(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            appContext.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Eventi che toccano i giorni da [from] a [to] inclusi; si aggiorna a ogni modifica dei calendari. */
    fun observeEvents(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<CalendarEvent>> =
        changes()
            .map { queryEvents(from, to, zone) }
            .flowOn(Dispatchers.IO)

    /** Calendari visibili sul telefono, prima quelli in cui si può scrivere. */
    fun observeCalendars(): Flow<List<DeviceCalendar>> =
        changes()
            .map { queryCalendars() }
            .flowOn(Dispatchers.IO)

    /** L'occorrenza di [eventId] che inizia a [beginMillis], oppure null se non esiste più. */
    suspend fun getOccurrence(eventId: Long, beginMillis: Long, zone: ZoneId = ZoneId.systemDefault()): CalendarEvent? =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext null
            val uri = instancesUri(beginMillis - DAY_MILLIS, beginMillis + DAY_MILLIS)
            resolver.query(
                uri,
                INSTANCE_PROJECTION,
                "${Instances.EVENT_ID} = ? AND ${Instances.BEGIN} = ?",
                arrayOf(eventId.toString(), beginMillis.toString()),
                null,
            )?.use { c -> if (c.moveToFirst()) c.toEvent(zone) else null }
        }

    /**
     * L'occorrenza di [eventId] da mostrare in un collegamento: la prossima se l'evento si ripete, altrimenti
     * l'unica. Null se l'evento non esiste più o manca il permesso.
     */
    suspend fun getForLink(eventId: Long, zone: ZoneId = ZoneId.systemDefault()): CalendarEvent? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        runCatching {
            val now = System.currentTimeMillis()
            // Prima la prossima occorrenza (in un anno), poi l'ultima passata (negli ultimi cinque anni).
            nearestOccurrence(eventId, now, now + YEAR_MILLIS, "ASC", zone)
                ?: nearestOccurrence(eventId, now - 5 * YEAR_MILLIS, now, "DESC", zone)
        }.getOrNull()
    }

    /** Eventi il cui titolo contiene [query], da un anno fa a un anno avanti: prima i prossimi, poi i passati. */
    suspend fun search(query: String, limit: Int = 8, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            val text = query.trim()
            if (!hasPermission() || text.length < 2) return@withContext emptyList()
            val now = System.currentTimeMillis()
            val like = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            val found = runCatching {
                resolver.query(
                    instancesUri(now - YEAR_MILLIS, now + YEAR_MILLIS),
                    INSTANCE_PROJECTION,
                    "${Instances.VISIBLE} = 1 AND ${Instances.TITLE} LIKE ? ESCAPE '\\'",
                    arrayOf(like),
                    "${Instances.BEGIN} ASC",
                )?.use { c -> buildList { while (c.moveToNext()) add(c.toEvent(zone)) } }
            }.getOrNull().orEmpty()
            // Una riga per evento: la prossima occorrenza, o l'ultima se sono tutte passate.
            val (next, past) = found.partition { it.beginMillis >= now }
            (next.distinctBy { it.eventId } + past.reversed().distinctBy { it.eventId })
                .distinctBy { it.eventId }
                .take(limit)
        }

    private fun nearestOccurrence(eventId: Long, from: Long, to: Long, order: String, zone: ZoneId): CalendarEvent? =
        resolver.query(
            instancesUri(from, to),
            INSTANCE_PROJECTION,
            "${Instances.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            "${Instances.BEGIN} $order",
        )?.use { c -> if (c.moveToFirst()) c.toEvent(zone) else null }

    /**
     * I partecipanti di [eventId] come li salva il calendario (invitati e organizzatore). [Attendee.self] segna
     * chi usa il telefono: il suo indirizzo è il nome dell'account del calendario.
     */
    suspend fun attendees(eventId: Long): List<Attendee> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        runCatching {
            val owner = resolver.query(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events.OWNER_ACCOUNT, Events.ORGANIZER), null, null, null,
            )?.use { c -> if (c.moveToFirst()) listOfNotNull(c.getString(0), c.getString(1)).map { it.lowercase() } else emptyList() }.orEmpty()
            resolver.query(
                CalendarContract.Attendees.CONTENT_URI,
                arrayOf(CalendarContract.Attendees.ATTENDEE_NAME, CalendarContract.Attendees.ATTENDEE_EMAIL, CalendarContract.Attendees.ATTENDEE_RELATIONSHIP),
                "${CalendarContract.Attendees.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
                null,
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val email = c.getString(1).orEmpty()
                        val organizer = c.getInt(2) == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER
                        add(Attendee(c.getString(0).orEmpty().ifBlank { email.substringBefore('@') }, email, organizer, owner.firstOrNull() == email.lowercase()))
                    }
                }
            }.orEmpty().filter { it.name.isNotBlank() }.distinctBy { it.email.lowercase().ifBlank { it.name } }
        }.getOrNull().orEmpty()
    }

    /** Eventi con un orario (non di tutto il giorno) che iniziano tra [fromMillis] e [toMillis]. */
    suspend fun startingBetween(fromMillis: Long, toMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            runCatching {
                resolver.query(
                    instancesUri(fromMillis, toMillis), INSTANCE_PROJECTION,
                    "${Instances.VISIBLE} = 1 AND ${Instances.ALL_DAY} = 0 AND ${Instances.BEGIN} >= ? AND ${Instances.BEGIN} <= ?",
                    arrayOf(fromMillis.toString(), toMillis.toString()), "${Instances.BEGIN} ASC",
                )?.use { c -> buildList { while (c.moveToNext()) add(c.toEvent(zone)) } }
            }.getOrNull().orEmpty()
        }

    /** Crea o aggiorna un evento; restituisce il suo id, oppure null se il sistema ha rifiutato. */
    suspend fun save(draft: EventDraft, zone: ZoneId = ZoneId.systemDefault()): Long? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, draft.calendarId)
            put(Events.TITLE, draft.title.trim())
            put(Events.EVENT_LOCATION, draft.location.trim())
            put(Events.DESCRIPTION, draft.description.trim())
            if (draft.allDay) {
                // Il Calendar Provider vuole gli eventi di tutto il giorno a mezzanotte UTC.
                put(Events.ALL_DAY, 1)
                put(Events.EVENT_TIMEZONE, "UTC")
                put(Events.DTSTART, draft.start.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
                put(Events.DTEND, draft.end.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            } else {
                put(Events.ALL_DAY, 0)
                put(Events.EVENT_TIMEZONE, zone.id)
                put(Events.DTSTART, draft.start.atZone(zone).toInstant().toEpochMilli())
                put(Events.DTEND, draft.end.atZone(zone).toInstant().toEpochMilli())
            }
        }
        runCatching {
            if (draft.eventId == null) {
                resolver.insert(Events.CONTENT_URI, values)?.let(ContentUris::parseId)
            } else {
                val rows = resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, draft.eventId), values, null, null)
                if (rows > 0) draft.eventId else null
            }
        }.getOrNull()
    }

    suspend fun delete(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null) > 0 }
            .getOrDefault(false)
    }

    /**
     * Crea il calendario "Eurise" solo sul telefono, per chi non ha calendari sincronizzati.
     * Restituisce l'id del calendario, oppure null se il sistema ha rifiutato.
     */
    suspend fun createLocalCalendar(displayName: String, color: Int): Long? = withContext(Dispatchers.IO) {
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, LOCAL_ACCOUNT)
            put(Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(Calendars.CALENDAR_COLOR, color)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
        }
        runCatching { resolver.insert(uri, values)?.let(ContentUris::parseId) }.getOrNull()
    }

    /** Emette subito e poi a ogni modifica dei calendari (anche quelle arrivate dalla sincronizzazione). */
    private fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        trySend(Unit)
        val registered = runCatching { resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer) }.isSuccess
        awaitClose { if (registered) resolver.unregisterContentObserver(observer) }
    }.conflate()

    private fun queryEvents(from: LocalDate, to: LocalDate, zone: ZoneId): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        // Un giorno di margine ai lati: gli eventi di tutto il giorno sono espressi in UTC.
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = runCatching {
            resolver.query(instancesUri(begin, end), INSTANCE_PROJECTION, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")
                ?.use { c -> buildList { while (c.moveToNext()) add(c.toEvent(zone)) } }
        }.getOrNull().orEmpty()
        return events.filter { !it.lastDay.isBefore(from) && !it.firstDay.isAfter(to) }
    }

    private fun queryCalendars(): List<DeviceCalendar> {
        if (!hasPermission()) return emptyList()
        return runCatching {
            resolver.query(Calendars.CONTENT_URI, CALENDAR_PROJECTION, "${Calendars.VISIBLE} = 1", null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            DeviceCalendar(
                                id = c.getLong(0),
                                name = c.getString(1).orEmpty(),
                                accountName = c.getString(2).orEmpty(),
                                color = c.getInt(4),
                                writable = c.getInt(5) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                                local = c.getString(3) == CalendarContract.ACCOUNT_TYPE_LOCAL && c.getString(2) == LOCAL_ACCOUNT,
                            )
                        )
                    }
                }
            }
        }.getOrNull().orEmpty().sortedWith(compareByDescending<DeviceCalendar> { it.writable }.thenBy { it.name.lowercase() })
    }

    private fun instancesUri(begin: Long, end: Long): Uri =
        Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()

    private fun Cursor.toEvent(zone: ZoneId): CalendarEvent {
        val allDay = getInt(5) == 1
        val begin = getLong(6)
        val end = getLong(7).coerceAtLeast(begin)
        fun local(millis: Long): LocalDateTime =
            if (allDay) Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay()
            else Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
        return CalendarEvent(
            eventId = getLong(0),
            calendarId = getLong(1),
            title = getString(2).orEmpty(),
            location = getString(3).orEmpty(),
            description = getString(4).orEmpty(),
            allDay = allDay,
            start = local(begin),
            end = local(end),
            color = getInt(8),
            recurring = !getString(9).isNullOrBlank() || !getString(10).isNullOrBlank(),
            calendarName = getString(11).orEmpty(),
            writable = getInt(12) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
            beginMillis = begin,
        )
    }

    internal companion object {
        const val LOCAL_ACCOUNT = "Eurise"
        const val DAY_MILLIS = 86_400_000L
        const val YEAR_MILLIS = 366 * DAY_MILLIS

        val INSTANCE_PROJECTION = arrayOf(
            Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.EVENT_LOCATION,
            Instances.DESCRIPTION, Instances.ALL_DAY, Instances.BEGIN, Instances.END, Instances.DISPLAY_COLOR,
            Instances.RRULE, Instances.RDATE, Instances.CALENDAR_DISPLAY_NAME, Instances.CALENDAR_ACCESS_LEVEL,
        )

        val CALENDAR_PROJECTION = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR, Calendars.CALENDAR_ACCESS_LEVEL,
        )
    }
}

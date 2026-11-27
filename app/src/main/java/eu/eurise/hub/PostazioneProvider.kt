package eu.eurise.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.weather.MeteoRepository
import eu.eurise.feature.launcher.news.NewsRepository
import eu.eurise.core.suite.SuiteAccess
import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyService
import eu.eurise.core.data.model.TaskList
import eu.eurise.feature.launcher.feeds.DailyRepository
import eu.eurise.feature.launcher.feeds.Sign
import eu.eurise.feature.launcher.files.RecentFilesRepository
import eu.eurise.feature.notes.lock.NoteLock
import eu.eurise.core.suite.PostazioneContract as C
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * «La tua giornata» per Eurise Postazione ([C]): prossimi impegni e cose da fare fino a oggi. Solo per le app della suite;
 * nulla esce dal telefono. `call` arriva su un thread del Binder: leggere qui è lecito.
 */
class PostazioneProvider : ContentProvider() {
    /** Tenuto finché vive l'app: così la copia delle notizie (venti minuti) vale anche tra una chiamata e l'altra. */
    private var newsRepo: NewsRepository? = null

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (!SuiteAccess.callerAllowed(context)) throw SecurityException("Eurise: solo le app della suite")
        return when (method) {
            C.METHOD_TODAY -> today(context)
            C.METHOD_POSTPONE -> postpone(context)
            C.METHOD_SCHEDULE -> schedule(context, extras)
            C.METHOD_NEWS -> news(context)
            C.METHOD_TASK -> task(context, arg, extras)
            C.METHOD_EVENT -> event(context, arg, extras)
            C.METHOD_SEARCH -> search(context, arg)
            C.METHOD_HOROSCOPE -> horoscope(context, extras)
            else -> null
        }
    }

    /** «Mettile lì»: un orario alle attività indicate, una ogni 25 minuti dall'inizio dello spazio libero. */
    private fun schedule(context: android.content.Context, extras: Bundle?): Bundle {
        val ids = extras?.getLongArray(C.EXTRA_IDS) ?: LongArray(0)
        val start = extras?.getLong(C.EXTRA_START) ?: 0L
        val now = System.currentTimeMillis()
        var moved = 0
        if (ids.isNotEmpty() && ids.size <= C.MAX_TASKS && start > now - 3_600_000L && start < now + C.HOURS * 3_600_000L) {
            val data = EuriseData.create(context)
            val first = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDateTime().withSecond(0).withNano(0)
            runBlocking {
                ids.forEachIndexed { i, id ->
                    val task = runCatching { data.tasks.get(id) }.getOrNull()
                    if (task != null && !task.done) {
                        val at = first.plusMinutes(25L * i)
                        runCatching { data.tasks.update(task.copy(due = at.toLocalDate(), time = at.toLocalTime())) }.onSuccess { moved++ }
                    }
                }
            }
        }
        return Bundle().apply { putInt(C.KEY_MOVED, moved) }
    }

    /**
     * Fatta, a domani o eliminata: solo attività vere (non i passi), aperte. Le dosi dei farmaci non vanno a domani: si
     * prendono o si saltano (eliminate, come fa l'elenco dei Farmaci).
     */
    private fun task(context: android.content.Context, action: String?, extras: Bundle?): Bundle {
        val id = extras?.getLong(C.EXTRA_ID) ?: 0L
        val data = EuriseData.create(context)
        val ok = runCatching {
            runBlocking {
                val t = data.tasks.get(id)?.takeIf { !it.done && it.parentId == null } ?: return@runBlocking false
                when (action) {
                    C.ACT_DONE -> { data.tasks.setDone(id, true); true }
                    C.ACT_TOMORROW -> if (t.listId == TaskList.MEDICINES) false
                        else { data.tasks.update(t.copy(due = LocalDate.now().plusDays(1))); true }
                    C.ACT_DELETE -> { data.tasks.delete(id); true }
                    else -> false
                }
            }
        }.getOrDefault(false)
        return Bundle().apply { putBoolean(C.KEY_OK, ok) }
    }

    /** Elimina un impegno solo se si poteva già dalla Postazione: calendario scrivibile ed evento non ricorrente. */
    private fun event(context: android.content.Context, action: String?, extras: Bundle?): Bundle {
        val id = extras?.getLong(C.EXTRA_ID) ?: 0L
        val data = EuriseData.create(context)
        val ok = action == C.ACT_DELETE && data.calendar.hasPermission() && runCatching {
            runBlocking {
                val e = data.calendar.getForLink(id) ?: return@runBlocking false
                e.writable && !e.recurring && data.calendar.delete(id)
            }
        }.getOrDefault(false)
        return Bundle().apply { putBoolean(C.KEY_OK, ok) }
    }

    /**
     * La ricerca della lente: impegni, cose da fare, note (mai quelle bloccate) e documenti (solo il nome, e solo se l'app
     * Eurise ha già l'accesso ai file). Ogni ricerca resta sul telefono.
     */
    private fun search(context: android.content.Context, query: String?): Bundle {
        val q = query?.trim().orEmpty()
        val kinds = mutableListOf<String>(); val ids = mutableListOf<Long>(); val titles = mutableListOf<String>()
        val details = mutableListOf<String>(); val whens = mutableListOf<Long>()
        fun add(kind: String, id: Long, title: String, detail: String, at: Long) {
            kinds += kind; ids += id; titles += title; details += detail; whens += at
        }
        if (q.length in 2..C.MAX_QUERY) {
            val data = EuriseData.create(context)
            val zone = ZoneId.systemDefault()
            runCatching { runBlocking { withTimeoutOrNull(4_000L) { data.calendar.search(q, C.MAX_FOUND) } } }.getOrNull().orEmpty()
                .forEach { e -> add(C.KIND_EVENT, e.eventId, e.title, e.location.ifBlank { e.calendarName }, e.beginMillis) }
            runCatching { runBlocking { data.tasks.search(q, C.MAX_FOUND * 2) } }.getOrDefault(emptyList())
                .filter { !it.done }.take(C.MAX_FOUND)
                .forEach { t ->
                    val at = t.due?.let { d -> (t.time?.let { d.atTime(it) } ?: d.atStartOfDay()).atZone(zone).toInstant().toEpochMilli() } ?: 0L
                    add(C.KIND_TASK, t.id, t.title, "", at)
                }
            runCatching { runBlocking { withTimeoutOrNull(3_000L) { data.notes.observe(q).first() } } }.getOrNull().orEmpty()
                .filter { !NoteLock.isLocked(it) }.take(C.MAX_FOUND)
                .forEach { n -> add(C.KIND_NOTE, n.id, n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty().take(80) }, "", n.updatedAt) }
            val files = RecentFilesRepository(context)
            if (files.hasAccess()) {
                runCatching { runBlocking { withTimeoutOrNull(4_000L) { files.search(q, C.MAX_FOUND) } } }.getOrNull().orEmpty()
                    .forEach { f -> add(C.KIND_FILE, android.content.ContentUris.parseId(f.uri), f.name, f.folder, f.modified) }
            }
        }
        return Bundle().apply {
            putStringArray(C.KEY_S_KIND, kinds.toTypedArray())
            putLongArray(C.KEY_S_ID, ids.toLongArray())
            putStringArray(C.KEY_S_TITLE, titles.toTypedArray())
            putStringArray(C.KEY_S_DETAIL, details.toTypedArray())
            putLongArray(C.KEY_S_WHEN, whens.toLongArray())
        }
    }

    /** L'oroscopo con il servizio «Oroscopo» del centro privacy: spento, solo [C.KEY_H_ON] falso e i nomi dei segni. */
    private fun horoscope(context: android.content.Context, extras: Bundle?): Bundle {
        val daily = DailyRepository(context.applicationContext)
        val signs = Sign.entries
        extras?.takeIf { it.containsKey(C.EXTRA_SIGN) }?.getInt(C.EXTRA_SIGN)?.let { i -> signs.getOrNull(i)?.let { daily.sign = it } }
        val sign = daily.sign
        val on = PrivacyCenter.allowed(PrivacyService.Horoscope)
        val entry = if (on) runCatching { runBlocking { withTimeoutOrNull(15_000L) { daily.horoscopeFor(sign, context.getString(sign.label)) } } }.getOrNull() else null
        return Bundle().apply {
            putBoolean(C.KEY_H_ON, on)
            putInt(C.KEY_H_SIGN, sign.ordinal)
            putStringArray(C.KEY_H_SIGNS, signs.map { context.getString(it.label) }.toTypedArray())
            if (entry != null) {
                putString(C.KEY_H_TEXT, entry.text)
                putString(C.KEY_H_SOURCE, entry.source)
                putString(C.KEY_H_LINK, entry.link)
            }
        }
    }

    /** Le notizie le legge l'app Eurise, con il servizio «Notizie» del centro privacy: spento, la lista è vuota. */
    private fun news(context: android.content.Context): Bundle {
        val repo = newsRepo ?: NewsRepository(context.applicationContext).also { newsRepo = it }
        val items = runCatching { runBlocking { withTimeoutOrNull(12_000L) { repo.latest() } } }.getOrNull().orEmpty().take(C.MAX_NEWS)
        return Bundle().apply {
            putStringArray(C.KEY_N_TITLE, items.map { it.title }.toTypedArray())
            putStringArray(C.KEY_N_SOURCE, items.map { it.source }.toTypedArray())
            putStringArray(C.KEY_N_LINK, items.map { it.link }.toTypedArray())
            putLongArray(C.KEY_N_WHEN, items.map { it.publishedAt }.toLongArray())
        }
    }

    /** Le attività aperte con scadenza fino a oggi passano a domani (i passi seguono la loro attività). */
    private fun postpone(context: android.content.Context): Bundle {
        val data = EuriseData.create(context)
        val today = LocalDate.now()
        val open = runCatching { runBlocking { data.tasks.observeDueBy(today).first() } }.getOrDefault(emptyList())
            .filter { !it.done && it.parentId == null }
        var moved = 0
        runBlocking {
            open.forEach { t -> runCatching { data.tasks.update(t.copy(due = today.plusDays(1))) }.onSuccess { moved++ } }
        }
        return Bundle().apply { putInt(C.KEY_MOVED, moved) }
    }

    private fun today(context: android.content.Context): Bundle {
        val data = EuriseData.create(context)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val events = if (data.calendar.hasPermission()) {
            runCatching { runBlocking { data.calendar.startingBetween(now - 15 * 60_000L, now + C.HOURS * 3_600_000L) } }
                .getOrDefault(emptyList())
                .map { e -> Triple(e, e.start.atZone(zone).toInstant().toEpochMilli(), e.end.atZone(zone).toInstant().toEpochMilli()) }
                .filter { (_, _, end) -> end > now }
                // Prima gli impegni con un orario, poi quelli di tutto il giorno.
                .sortedWith(compareBy({ it.first.allDay }, { it.second }))
                .take(C.MAX_EVENTS)
        } else emptyList()
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tasks = runCatching { runBlocking { data.tasks.observeDueBy(today).first() } }.getOrDefault(emptyList())
        val open = tasks.filter { !it.done }
        val doneToday = tasks.count { it.done && (it.completedAt ?: 0L) >= startOfDay }
        val shown = open.sortedWith(compareBy({ it.due }, { it.time })).take(C.MAX_TASKS)
        return Bundle().apply {
            putStringArray(C.KEY_EV_TITLE, events.map { it.first.title }.toTypedArray())
            putStringArray(C.KEY_EV_PLACE, events.map { it.first.location }.toTypedArray())
            putLongArray(C.KEY_EV_BEGIN, events.map { it.second }.toLongArray())
            putLongArray(C.KEY_EV_END, events.map { it.third }.toLongArray())
            putBooleanArray(C.KEY_EV_ALLDAY, events.map { it.first.allDay }.toBooleanArray())
            putLongArray(C.KEY_EV_ID, events.map { it.first.eventId }.toLongArray())
            putBooleanArray(C.KEY_EV_EDIT, events.map { it.first.writable && !it.first.recurring }.toBooleanArray())
            putLongArray(C.KEY_TASK_ID, shown.map { it.id }.toLongArray())
            putStringArray(C.KEY_TASK_TITLE, shown.map { it.title }.toTypedArray())
            putLongArray(C.KEY_TASK_DUE, shown.map { t ->
                val day = t.due ?: return@map 0L
                (t.time?.let { day.atTime(it) } ?: day.atStartOfDay()).atZone(zone).toInstant().toEpochMilli()
            }.toLongArray())
            putBooleanArray(C.KEY_TASK_LATE, shown.map { t -> t.due?.isBefore(today) == true }.toBooleanArray())
            putBooleanArray(C.KEY_TASK_MED, shown.map { t -> t.listId == TaskList.MEDICINES }.toBooleanArray())
            putInt(C.KEY_TASK_OPEN, open.size)
            putInt(C.KEY_TASK_DONE, doneToday)
            // Il meteo già sul telefono (copia dell'app Eurise, al più di tre ore fa): questa chiamata non va in rete.
            val meteo = runCatching { runBlocking { MeteoRepository(context).cached() } }.getOrNull()
            val forecast = meteo?.forecast?.takeIf { meteo.updated > 0 && now - meteo.updated < 3 * 3_600_000L }
            putBoolean(C.KEY_W_OK, forecast != null)
            if (forecast != null) {
                putInt(C.KEY_W_TEMP, forecast.now.temp)
                putString(C.KEY_W_SKY, forecast.now.sky.name)
                val here = LocalDateTime.now(forecast.zone)
                val rain = forecast.hours.firstOrNull { it.time.isAfter(here) && it.rainChance >= 50 }
                putLong(C.KEY_W_RAIN, rain?.time?.atZone(forecast.zone)?.toInstant()?.toEpochMilli() ?: 0L)
            }
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

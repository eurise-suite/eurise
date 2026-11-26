package eu.eurise.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.weather.MeteoRepository
import eu.eurise.core.suite.SuiteAccess
import eu.eurise.core.suite.PostazioneContract as C
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * «La tua giornata» per Eurise Postazione ([C]): prossimi impegni e cose da fare fino a oggi. Solo per le app della suite;
 * nulla esce dal telefono. `call` arriva su un thread del Binder: leggere qui è lecito.
 */
class PostazioneProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (!SuiteAccess.callerAllowed(context)) throw SecurityException("Eurise: solo le app della suite")
        return when (method) {
            C.METHOD_TODAY -> today(context)
            C.METHOD_POSTPONE -> postpone(context)
            else -> null
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
            putLongArray(C.KEY_TASK_ID, shown.map { it.id }.toLongArray())
            putStringArray(C.KEY_TASK_TITLE, shown.map { it.title }.toTypedArray())
            putLongArray(C.KEY_TASK_DUE, shown.map { t ->
                val day = t.due ?: return@map 0L
                (t.time?.let { day.atTime(it) } ?: day.atStartOfDay()).atZone(zone).toInstant().toEpochMilli()
            }.toLongArray())
            putBooleanArray(C.KEY_TASK_LATE, shown.map { t -> t.due?.isBefore(today) == true }.toBooleanArray())
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

package eu.eurise.core.data.modes

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import eu.eurise.core.data.EuriseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Lo stato che le schermate leggono: la modalità accesa, come è nata e che cosa nasconde. */
data class ModeState(
    val loaded: Boolean = false,
    val active: Mode? = null,
    val manual: Boolean = false,
    /** Quando potrebbe cambiare da sola (per mostrare "fino alle 18:00"). */
    val until: LocalDateTime? = null,
    val configs: List<ModeConfig> = Mode.entries.map { ModeConfig.defaults(it) },
) {
    val config: ModeConfig? get() = active?.let { a -> configs.firstOrNull { it.mode == a } }
    val hiddenCalendars: Set<Long> get() = config?.hiddenCalendars.orEmpty()
    val hiddenLists: Set<Long> get() = config?.hiddenLists.orEmpty()
}

/**
 * Le modalità Lavoro, Casa, Viaggio e Notte: si accendono a mano dal Pannello, per orario (saltando festivi e ferie)
 * o durante gli eventi di un calendario. Non leggono mai la posizione. Tutto resta sul telefono.
 *
 * Entrando in una modalità si ricordano suoneria e notifiche com'erano; uscendo tornano così.
 */
object Modes {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val _state = MutableStateFlow(ModeState())
    val state: StateFlow<ModeState> = _state.asStateFlow()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("eurise_modes", Context.MODE_PRIVATE)

    /** Ricalcola, applica e programma il prossimo controllo. Da chiamare all'apertura e quando cambia qualcosa. */
    fun refresh(context: Context) = refreshAsync(context)

    /** Come [refresh], restituendo il lavoro (per i ricevitori, che devono aspettarlo). */
    fun refreshAsync(context: Context): kotlinx.coroutines.Job {
        val app = context.applicationContext
        return scope.launch { runCatching { update(app) } }
    }

    /** Accende [mode] a mano (nulla: spegne) fino al prossimo cambio automatico; tocca di nuovo per tornare all'automatico. */
    fun choose(context: Context, mode: Mode?) {
        val app = context.applicationContext
        scope.launch {
            lock.withLock {
                val p = prefs(app)
                val configs = loadConfigs(p)
                val now = LocalDateTime.now()
                val busy = busy(app, configs, now)
                val until = ModeRules.nextChange(now, configs, null, busy) ?: now.plusHours(12)
                if (_state.value.manual && _state.value.active == mode) p.edit().remove("manual").apply()
                else p.edit().putString("manual", (mode?.name ?: "-") + "|" + until).apply()
            }
            runCatching { update(app) }
        }
    }

    fun save(context: Context, config: ModeConfig) {
        val app = context.applicationContext
        scope.launch {
            lock.withLock {
                val p = prefs(app)
                val configs = loadConfigs(p).map { if (it.mode == config.mode) config else it }
                p.edit().putString("configs", encode(configs)).apply()
            }
            runCatching { update(app) }
        }
    }

    /** Un viaggio preparato da un biglietto: la modalità Viaggio si accende da sola dall'inizio alla fine. */
    fun addTrip(context: Context, start: LocalDateTime, end: LocalDateTime) {
        val app = context.applicationContext
        scope.launch {
            lock.withLock {
                val p = prefs(app)
                val trips = loadTrips(p).filter { it.end.isAfter(LocalDateTime.now().minusDays(1)) } + Busy(Busy.TRIP, start, end)
                p.edit().putString("trips", trips.joinToString("\n") { "${it.start}|${it.end}" }).apply()
            }
            runCatching { update(app) }
        }
    }

    private fun loadTrips(p: android.content.SharedPreferences): List<Busy> =
        p.getString("trips", "").orEmpty().lines().mapNotNull { line ->
            runCatching { line.split('|').let { Busy(Busy.TRIP, LocalDateTime.parse(it[0]), LocalDateTime.parse(it[1])) } }.getOrNull()
        }

    private suspend fun update(context: Context) = lock.withLock {
        val p = prefs(context)
        val configs = loadConfigs(p)
        val now = LocalDateTime.now()
        val manual = p.getString("manual", null)?.let { raw ->
            runCatching {
                val (m, u) = raw.split('|')
                ManualChoice(Mode.entries.firstOrNull { it.name == m }, LocalDateTime.parse(u))
            }.getOrNull()
        }?.takeIf { now.isBefore(it.until) }
        if (manual == null) p.edit().remove("manual").apply()
        val busy = busy(context, configs, now) + loadTrips(p)
        val daysOff = daysOff(context, now.toLocalDate())
        val active = ModeRules.resolve(now, configs, manual, busy) { d -> ItalianHolidays.isHoliday(d) || d in daysOff }
        val next = ModeRules.nextChange(now, configs, manual, busy)
        val applied = p.getString("applied", null)?.let { a -> Mode.entries.firstOrNull { it.name == a } }
        if (active != applied) apply(context, p, applied?.let { a -> configs.first { it.mode == a } }, active?.let { a -> configs.first { it.mode == a } })
        _state.value = ModeState(true, active, manual != null, next, configs)
        schedule(context, next)
    }

    /** Eventi in corso o vicini dei calendari scelti come accensione. */
    private suspend fun busy(context: Context, configs: List<ModeConfig>, now: LocalDateTime): List<Busy> {
        val calendars = configs.mapNotNull { it.calendarTrigger }.toSet()
        if (calendars.isEmpty()) return emptyList()
        val events = runCatching {
            EuriseData.create(context).calendar.observeEvents(now.toLocalDate().minusDays(1), now.toLocalDate().plusDays(2)).first()
        }.getOrNull().orEmpty()
        return events.filter { it.calendarId in calendars }.map { Busy(it.calendarId, it.start, it.end) }
    }

    /** Giorni di ferie: eventi di tutto il giorno chiamati "Ferie" o "Vacanze", in qualunque calendario. */
    private suspend fun daysOff(context: Context, today: LocalDate): Set<LocalDate> = runCatching {
        EuriseData.create(context).calendar.observeEvents(today.minusDays(1), today.plusDays(2)).first()
            .filter { it.allDay && ItalianHolidays.isTimeOffTitle(it.title) }
            .flatMap { e -> generateSequence(e.firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(e.lastDay) }.toList() }
            .toSet()
    }.getOrDefault(emptySet())

    private fun apply(context: Context, p: android.content.SharedPreferences, from: ModeConfig?, to: ModeConfig?) {
        val audio = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val policy = Build.VERSION.SDK_INT >= 23 && nm?.isNotificationPolicyAccessGranted == true
        // Prima di entrare da "nessuna modalità": si ricorda com'era il telefono.
        if (from == null && to != null) {
            p.edit()
                .putInt("baseRinger", audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL)
                .putInt("baseFilter", nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL)
                .apply()
        }
        val baseRinger = p.getInt("baseRinger", AudioManager.RINGER_MODE_NORMAL)
        val baseFilter = p.getInt("baseFilter", NotificationManager.INTERRUPTION_FILTER_ALL)
        val filter = when (to?.interruptions ?: Interruptions.Unchanged) {
            Interruptions.Unchanged -> if (from?.interruptions != null && from.interruptions != Interruptions.Unchanged) baseFilter else null
            Interruptions.All -> NotificationManager.INTERRUPTION_FILTER_ALL
            Interruptions.Priority -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            Interruptions.Alarms -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            Interruptions.None -> NotificationManager.INTERRUPTION_FILTER_NONE
        }
        if (filter != null && policy) runCatching { nm?.setInterruptionFilter(filter) }
        val ringer = when (to?.ringer ?: Ringer.Unchanged) {
            Ringer.Unchanged -> if (from?.ringer != null && from.ringer != Ringer.Unchanged) baseRinger else null
            Ringer.Sound -> AudioManager.RINGER_MODE_NORMAL
            Ringer.Vibrate -> AudioManager.RINGER_MODE_VIBRATE
            Ringer.Silent -> AudioManager.RINGER_MODE_SILENT
        }
        // Il silenzioso richiede l'accesso a "Non disturbare"; senza, si ripiega sulla vibrazione.
        if (ringer != null) runCatching {
            audio?.ringerMode = if (ringer == AudioManager.RINGER_MODE_SILENT && !policy) AudioManager.RINGER_MODE_VIBRATE else ringer
        }
        p.edit().putString("applied", to?.mode?.name).apply()
    }

    private fun schedule(context: Context, next: LocalDateTime?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 11, Intent(context, ModeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (next == null) { am.cancel(pi); return }
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun loadConfigs(p: android.content.SharedPreferences): List<ModeConfig> {
        val saved = p.getString("configs", null)?.let { runCatching { decode(it) }.getOrNull() }.orEmpty()
        return Mode.entries.map { m -> saved.firstOrNull { it.mode == m } ?: ModeConfig.defaults(m) }
    }

    private fun encode(configs: List<ModeConfig>): String = JSONArray(configs.map { c ->
        JSONObject()
            .put("mode", c.mode.name)
            .put("home", c.homeCategory ?: "")
            .put("int", c.interruptions.name)
            .put("ring", c.ringer.name)
            .put("cals", JSONArray(c.hiddenCalendars.toList()))
            .put("lists", JSONArray(c.hiddenLists.toList()))
            .put("trigger", c.calendarTrigger ?: -1L)
            .put("on", c.schedule.enabled)
            .put("days", JSONArray(c.schedule.days.map { it.value }))
            .put("start", c.schedule.start.toString())
            .put("end", c.schedule.end.toString())
            .put("skip", c.schedule.skipHolidays)
    }).toString()

    private fun decode(text: String): List<ModeConfig> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val mode = Mode.entries.firstOrNull { it.name == o.optString("mode") } ?: return@mapNotNull null
            fun longs(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getLong(it) }.toSet() }.orEmpty()
            ModeConfig(
                mode = mode,
                homeCategory = o.optString("home").ifBlank { null },
                interruptions = Interruptions.entries.firstOrNull { it.name == o.optString("int") } ?: Interruptions.Unchanged,
                ringer = Ringer.entries.firstOrNull { it.name == o.optString("ring") } ?: Ringer.Unchanged,
                hiddenCalendars = longs("cals"),
                hiddenLists = longs("lists"),
                calendarTrigger = o.optLong("trigger", -1L).takeIf { it >= 0 },
                schedule = Schedule(
                    enabled = o.optBoolean("on"),
                    days = longs("days").mapNotNull { v -> DayOfWeek.entries.firstOrNull { it.value.toLong() == v } }.toSet(),
                    start = runCatching { LocalTime.parse(o.optString("start")) }.getOrDefault(LocalTime.of(9, 0)),
                    end = runCatching { LocalTime.parse(o.optString("end")) }.getOrDefault(LocalTime.of(18, 0)),
                    skipHolidays = o.optBoolean("skip"),
                ),
            )
        }
    }
}

/** Svegliato a ogni possibile cambio di modalità e dopo il riavvio del telefono. */
class ModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        Modes.refreshAsync(context).invokeOnCompletion { pending.finish() }
    }
}

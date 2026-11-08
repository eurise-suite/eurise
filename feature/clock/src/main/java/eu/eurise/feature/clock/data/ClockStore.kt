package eu.eurise.feature.clock.data

import android.content.Context
import eu.eurise.feature.clock.logic.Alarm
import eu.eurise.feature.clock.logic.ClockTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.time.LocalDate

/** Impostazioni generali dell'Orologio. */
data class ClockSettings(
    /** Ore di sonno desiderate, in minuti, per la riga "Ora di dormire". */
    val sleepMinutes: Int = 450,
    /** Avviso la sera quando è ora di andare a dormire. */
    val bedtimeReminder: Boolean = false,
    /** Lettura del calendario (ferie, impegni presto): accesa solo dall'utente, dopo la spiegazione. */
    val useCalendar: Boolean = false,
    /** Fusi orari scelti (id di ZoneId). */
    val zones: List<String> = listOf("Europe/London", "America/New_York", "Asia/Tokyo"),
    /** Cronometro: istante di partenza (ms, orologio di sistema) se corre, tempo accumulato e giri. */
    val stopwatchStart: Long? = null,
    val stopwatchAccumulated: Long = 0,
    val laps: List<Long> = emptyList(),
)

/**
 * Sveglie, timer e impostazioni, salvati sul telefono nelle preferenze (pochi oggetti: niente database).
 * Un solo oggetto per processo, così schermate, ricevitori e servizio vedono gli stessi dati.
 */
class ClockStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("clock", Context.MODE_PRIVATE)

    private val _alarms = MutableStateFlow(loadAlarms())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _timers = MutableStateFlow(loadTimers())
    val timers: StateFlow<List<ClockTimer>> = _timers.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ClockSettings> = _settings.asStateFlow()

    /** Giorni di ferie letti dal calendario l'ultima volta (per non rileggerlo a ogni calcolo). */
    var vacationDays: Set<LocalDate>
        get() = prefs.getString("vacation", "").orEmpty().split(',').filter { it.isNotBlank() }.map(LocalDate::parse).toSet()
        set(value) = prefs.edit().putString("vacation", value.joinToString(",")).apply()

    fun nextId(): Int {
        val id = prefs.getInt("next_id", 1)
        prefs.edit().putInt("next_id", id + 1).apply()
        return id
    }

    fun saveAlarm(alarm: Alarm) = setAlarms(_alarms.value.filterNot { it.id == alarm.id } + alarm)

    fun deleteAlarm(id: Int) = setAlarms(_alarms.value.filterNot { it.id == id })

    fun alarm(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun setAlarms(list: List<Alarm>) {
        val sorted = list.sortedBy { it.time }
        _alarms.value = sorted
        prefs.edit().putString("alarms", JSONArray(sorted.map { it.toJson() }).toString()).apply()
    }

    fun saveTimer(timer: ClockTimer) = setTimers(_timers.value.map { if (it.id == timer.id) timer else it }.let { l -> if (l.any { it.id == timer.id }) l else l + timer })

    fun deleteTimer(id: Int) = setTimers(_timers.value.filterNot { it.id == id })

    fun timer(id: Int): ClockTimer? = _timers.value.firstOrNull { it.id == id }

    private fun setTimers(list: List<ClockTimer>) {
        _timers.value = list
        prefs.edit().putString("timers", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun updateSettings(transform: (ClockSettings) -> ClockSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putInt("sleep", s.sleepMinutes).putBoolean("bedtime", s.bedtimeReminder).putBoolean("calendar", s.useCalendar)
            .putString("zones", s.zones.joinToString(","))
            .putLong("sw_start", s.stopwatchStart ?: -1).putLong("sw_acc", s.stopwatchAccumulated)
            .putString("laps", s.laps.joinToString(","))
            .apply()
    }

    private fun loadAlarms(): List<Alarm> = runCatching {
        val a = JSONArray(prefs.getString("alarms", "[]"))
        (0 until a.length()).map { Alarm.fromJson(a.getJSONObject(it)) }.sortedBy { it.time }
    }.getOrDefault(emptyList())

    private fun loadTimers(): List<ClockTimer> = runCatching {
        val a = JSONArray(prefs.getString("timers", "[]"))
        (0 until a.length()).map { ClockTimer.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun loadSettings() = ClockSettings(
        sleepMinutes = prefs.getInt("sleep", 450),
        bedtimeReminder = prefs.getBoolean("bedtime", false),
        useCalendar = prefs.getBoolean("calendar", false),
        zones = prefs.getString("zones", null)?.split(',')?.filter { it.isNotBlank() } ?: ClockSettings().zones,
        stopwatchStart = prefs.getLong("sw_start", -1).takeIf { it >= 0 },
        stopwatchAccumulated = prefs.getLong("sw_acc", 0),
        laps = prefs.getString("laps", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() },
    )

    companion object {
        @Volatile private var instance: ClockStore? = null
        fun get(context: Context): ClockStore =
            instance ?: synchronized(this) { instance ?: ClockStore(context.applicationContext).also { instance = it } }
    }
}

package eu.eurise.feature.launcher

import android.app.Application
import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.Task
import eu.eurise.feature.launcher.apps.AppEntry
import eu.eurise.feature.launcher.apps.AppsRepository
import eu.eurise.feature.launcher.settings.LauncherSettings
import eu.eurise.feature.launcher.settings.SettingsStore
import eu.eurise.feature.launcher.weather.DayForecast
import eu.eurise.feature.launcher.weather.WeatherRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Quello che serve alla home e al riepilogo. */
data class LauncherState(
    val apps: List<AppEntry> = emptyList(),
    val favorites: List<AppEntry> = emptyList(),
    /** Ultime app aperte dalla Scrivania (contate solo qui, sul telefono). */
    val recents: List<AppEntry> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val eventsToday: List<CalendarEvent> = emptyList(),
    /** Appuntamenti dei prossimi sette giorni, per la scheda Calendario. */
    val upcoming: List<CalendarEvent> = emptyList(),
    val calendarConnected: Boolean = false,
    val tasksDue: List<Task> = emptyList(),
    /** Tutte le cose da fare aperte, per la ricerca. */
    val openTasks: List<Task> = emptyList(),
    val notes: List<Note> = emptyList(),
)

private data class LocalPart(val due: List<Task>, val open: List<Task>, val notes: List<Note>)

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModel(
    application: Application,
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    private val calendar: CalendarRepository,
) : AndroidViewModel(application) {

    private val apps = AppsRepository(application)
    private val weatherRepo = WeatherRepository(application)
    private val prefs = application.getSharedPreferences("eurise_launcher", Context.MODE_PRIVATE)
    private val settingsStore = SettingsStore(application)
    val settings: StateFlow<LauncherSettings> = settingsStore.settings
    private val today = LocalDate.now()

    private val favoriteKeys = MutableStateFlow(loadFavoriteKeys())
    private val recentKeys = MutableStateFlow(prefs.getString(KEY_RECENTS, "").orEmpty().split('|').filter { it.isNotBlank() })
    private val keys = combine(favoriteKeys, recentKeys) { f, r -> f to r }
    private val calendarPermission = MutableStateFlow(calendar.hasPermission())

    private val _weather = MutableStateFlow<List<DayForecast>?>(null)
    val weather: StateFlow<List<DayForecast>?> = _weather

    private val local = combine(tasks.observeDueBy(today), tasks.observeOpen(), notes.observeRecent(200)) { d, o, n -> LocalPart(d, o, n) }

    private val events = calendarPermission.flatMapLatest { granted ->
        if (granted) calendar.observeEvents(today, today.plusDays(6)) else flowOf(emptyList())
    }

    val state: StateFlow<LauncherState> = combine(apps.observe(), keys, local, events, calendarPermission) { list, (favs, recent), part, ev, granted ->
        val byKey = list.associateBy { it.key }
        LauncherState(
            apps = list,
            favorites = favs.mapNotNull { byKey[it] },
            recents = recent.mapNotNull { byKey[it] },
            today = today,
            eventsToday = ev.filter { it.occursOn(today) }.sortedBy { it.start },
            upcoming = ev.sortedBy { it.start },
            calendarConnected = granted,
            tasksDue = part.due,
            openTasks = part.open,
            notes = part.notes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherState())

    /** Da richiamare quando la Scrivania torna in primo piano: permessi e meteo possono essere cambiati. */
    fun refresh() {
        calendarPermission.value = calendar.hasPermission()
        viewModelScope.launch { _weather.value = weatherRepo.forecast() }
    }

    fun hasLocationPermission() = weatherRepo.hasLocationPermission()

    fun isFavorite(entry: AppEntry) = entry.key in favoriteKeys.value

    fun toggleFavorite(entry: AppEntry) {
        val current = favoriteKeys.value
        val next = if (entry.key in current) current - entry.key else (current + entry.key).take(settings.value.maxFavorites)
        favoriteKeys.value = next
        prefs.edit().putString(KEY_FAVORITES, next.joinToString("|")).apply()
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = settingsStore.update(transform)

    /** Ricorda l'app appena aperta per la fila "Recenti" della ricerca. */
    fun recordLaunch(entry: AppEntry) {
        val next = (listOf(entry.key) + recentKeys.value.filter { it != entry.key }).take(MAX_RECENTS)
        recentKeys.value = next
        prefs.edit().putString(KEY_RECENTS, next.joinToString("|")).apply()
    }

    fun addTask(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { tasks.add(text.trim(), due = today) }
    }

    fun addNote(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { notes.save(null, clean.substringBefore('\n').trim(), clean.substringAfter('\n', "").trim()) }
    }

    fun setTaskDone(task: Task, done: Boolean) {
        viewModelScope.launch { tasks.setDone(task.id, done) }
    }

    /** Preferiti predefiniti: telefono e messaggi di sistema, Note e Attività di Eurise. */
    private fun loadFavoriteKeys(): List<String> {
        prefs.getString(KEY_FAVORITES, null)?.let { saved -> return saved.split('|').filter { it.isNotBlank() } }
        val context = getApplication<Application>()
        val dialer = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        val pm = context.packageManager
        fun launcherKey(pkg: String?): String? = pkg?.let { p ->
            pm.getLaunchIntentForPackage(p)?.component?.let { "${it.packageName}/${it.className}" }
        }
        // Il Calendario non serve tra i preferiti: si apre toccando la data.
        return listOfNotNull(launcherKey(dialer), launcherKey(sms), "eurise:notes", "eurise:tasks")
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENTS = "recents"
        const val MAX_RECENTS = 8
    }
}

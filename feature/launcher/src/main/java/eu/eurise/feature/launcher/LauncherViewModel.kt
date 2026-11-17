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
import eu.eurise.feature.launcher.contacts.ContactsRepository
import eu.eurise.feature.launcher.feeds.DailyRepository
import eu.eurise.feature.launcher.health.HealthRepository
import eu.eurise.feature.launcher.health.HealthState
import eu.eurise.feature.launcher.health.HealthToday
import eu.eurise.feature.launcher.weather.SunTimes
import eu.eurise.feature.launcher.folders.AppFolder
import eu.eurise.feature.launcher.home.placing
import eu.eurise.feature.launcher.feeds.FeedEntry
import eu.eurise.feature.launcher.feeds.Sign
import eu.eurise.feature.launcher.files.RecentFile
import eu.eurise.feature.launcher.files.RecentFilesRepository
import eu.eurise.feature.launcher.notifications.NotificationStore
import eu.eurise.core.data.route.RouteRepository
import eu.eurise.core.data.route.Travel
import eu.eurise.feature.launcher.markets.MarketsRepository
import eu.eurise.feature.launcher.markets.Quote
import eu.eurise.feature.launcher.news.NewsItem
import eu.eurise.feature.launcher.news.NewsRepository
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
import java.time.YearMonth

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
    private val links: eu.eurise.core.data.LinkRepository? = null,
) : AndroidViewModel(application) {

    private val apps = AppsRepository(application)
    private val weatherRepo = WeatherRepository(application)
    private val prefs = application.getSharedPreferences("eurise_launcher", Context.MODE_PRIVATE)
    private val settingsStore = SettingsStore(application)
    val settings: StateFlow<LauncherSettings> = settingsStore.settings
    private val today = LocalDate.now()

    private val favoriteKeys = MutableStateFlow(loadFavoriteKeys())
    private val recentKeys = MutableStateFlow(prefs.getString(KEY_RECENTS, "").orEmpty().split('|').filter { it.isNotBlank() })
    private val keys = combine(favoriteKeys, recentKeys, settingsStore.settings) { f, r, s -> Triple(f, r, s.folders) }
    private val calendarPermission = MutableStateFlow(calendar.hasPermission())

    val news = NewsRepository(application)
    val markets = MarketsRepository(application)
    private val _newsItems = MutableStateFlow<List<NewsItem>?>(null)
    val newsItems: StateFlow<List<NewsItem>?> = _newsItems
    private val _quotes = MutableStateFlow<List<Quote>?>(null)
    val quotes: StateFlow<List<Quote>?> = _quotes

    /** Mese mostrato nella scheda Calendario, con appuntamenti e cose da fare di quel mese. */
    private val _calendarMonth = MutableStateFlow(YearMonth.now())
    val calendarMonth: StateFlow<YearMonth> = _calendarMonth

    fun setCalendarMonth(month: YearMonth) { _calendarMonth.value = month }

    val monthEvents: StateFlow<List<CalendarEvent>> = combine(calendarPermission, _calendarMonth) { granted, month -> granted to month }
        .flatMapLatest { (granted, month) ->
            if (granted) calendar.observeEvents(month.atDay(1), month.atEndOfMonth()) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthTasks: StateFlow<List<Task>> = _calendarMonth
        .flatMapLatest { month -> tasks.observeDueBetween(month.atDay(1), month.atEndOfMonth()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _weather = MutableStateFlow<List<DayForecast>?>(null)
    val weather: StateFlow<List<DayForecast>?> = _weather

    private val local = combine(tasks.observeDueBy(today), tasks.observeOpen(), notes.observeRecent(200)) { d, o, n -> LocalPart(d, o, n) }

    private val events = calendarPermission.flatMapLatest { granted ->
        if (granted) calendar.observeEvents(today, today.plusDays(6)) else flowOf(emptyList())
    }

    val state: StateFlow<LauncherState> = combine(apps.observe(), keys, local, events, calendarPermission) { list, (favs, recent, folders), part, ev, granted ->
        val byKey = list.associateBy { it.key }
        LauncherState(
            apps = list,
            // Le cartelle sulla home compaiono tra i preferiti come voci speciali "folder:…".
            favorites = favs.mapNotNull { k -> byKey[k] ?: folders.firstOrNull { it.key == k }?.let { folderEntry(it) } },
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
    val health = HealthRepository(application)
    private val _healthState = MutableStateFlow(HealthState.Unavailable)
    val healthState: StateFlow<HealthState> = _healthState
    private val _healthToday = MutableStateFlow<HealthToday?>(null)
    val healthToday: StateFlow<HealthToday?> = _healthToday

    fun refreshHealth() {
        viewModelScope.launch {
            _healthState.value = health.state()
            if (_healthState.value == HealthState.Ready) _healthToday.value = health.today()
        }
    }

    /** Alba e tramonto di oggi dalla posizione approssimata (calcolati sul telefono). */
    private val _sun = MutableStateFlow<Pair<java.time.LocalTime, java.time.LocalTime>?>(null)
    val sun: StateFlow<Pair<java.time.LocalTime, java.time.LocalTime>?> = _sun

    fun refreshSun() {
        val loc = weatherRepo.lastLocation() ?: return
        _sun.value = SunTimes.of(LocalDate.now(), loc.first, loc.second)
    }

    val daily = DailyRepository(application)
    private val _sign = MutableStateFlow(daily.sign)
    val sign: StateFlow<Sign> = _sign
    private val _horoscope = MutableStateFlow<FeedEntry?>(null)
    val horoscope: StateFlow<FeedEntry?> = _horoscope
    private val _horoscopeLoaded = MutableStateFlow(false)
    val horoscopeLoaded: StateFlow<Boolean> = _horoscopeLoaded
    private val _recipes = MutableStateFlow<List<FeedEntry>?>(null)
    val recipes: StateFlow<List<FeedEntry>?> = _recipes

    fun setSign(sign: Sign, name: String) {
        daily.sign = sign
        _sign.value = sign
        refreshHoroscope(name)
    }

    fun refreshHoroscope(name: String, force: Boolean = false) {
        viewModelScope.launch { _horoscope.value = daily.horoscopeFor(_sign.value, name, force); _horoscopeLoaded.value = true }
    }

    fun refreshRecipes(force: Boolean = false) {
        viewModelScope.launch { _recipes.value = daily.recipesOfDay(force) }
    }

    val contacts = ContactsRepository(application)
    val files = RecentFilesRepository(application)
    private val routes = RouteRepository(application)

    private val _recentFiles = MutableStateFlow<List<RecentFile>>(emptyList())
    val recentFiles: StateFlow<List<RecentFile>> = _recentFiles
    private val _travel = MutableStateFlow<Travel?>(null)
    val travel: StateFlow<Travel?> = _travel

    init {
        // Chi scrive in chat entra nei contatti recenti (solo sul telefono).
        viewModelScope.launch {
            NotificationStore.messages.collect { list -> list.forEach { contacts.remember(it.title, it.appLabel, it.postedAt) } }
        }
    }

    /** Tempo per arrivare al luogo del prossimo appuntamento di oggi, se ha un indirizzo. */
    fun refreshTravel(destination: String?) {
        if (destination.isNullOrBlank()) { _travel.value = null; return }
        viewModelScope.launch { _travel.value = routes.travelTo(destination) }
    }

    private val _travels = MutableStateFlow<Map<String, Travel>>(emptyMap())
    val travels: StateFlow<Map<String, Travel>> = _travels
    private var travelsJob: kotlinx.coroutines.Job? = null

    /** Tempo per arrivare a ciascun luogo di oggi (appuntamenti e cose da fare), al massimo otto. */
    fun refreshTravels(places: List<String>) {
        travelsJob?.cancel()
        travelsJob = viewModelScope.launch {
            val found = mutableMapOf<String, Travel>()
            places.distinct().take(8).forEach { place ->
                routes.travelTo(place)?.let { found[place] = it; _travels.value = found.toMap() }
            }
            _travels.value = found.toMap()
        }
    }

    suspend fun searchEvents(query: String) = calendar.search(query)

    val videos = eu.eurise.feature.launcher.videos.VideosRepository(application)
    private val _videoList = MutableStateFlow<List<eu.eurise.feature.launcher.videos.Video>?>(null)
    val videoList: StateFlow<List<eu.eurise.feature.launcher.videos.Video>?> = _videoList

    fun refreshVideos(force: Boolean = false) {
        if (videos.channels.value.isEmpty()) { _videoList.value = emptyList(); return }
        viewModelScope.launch { _videoList.value = videos.latest(force) }
    }

    fun refreshFiles() {
        viewModelScope.launch { _recentFiles.value = files.recent() }
    }

    fun refresh() {
        calendarPermission.value = calendar.hasPermission()
        refreshFiles()
        refreshVideos()
        refreshHealth()
        refreshSun()
        viewModelScope.launch { _weather.value = weatherRepo.forecast() }
        refreshNews()
        refreshQuotes()
    }

    fun refreshNews(force: Boolean = false) {
        viewModelScope.launch { _newsItems.value = news.latest(force) }
    }

    fun refreshQuotes() {
        viewModelScope.launch { _quotes.value = markets.quotes() }
    }

    fun hasLocationPermission() = weatherRepo.hasLocationPermission()

    fun isFavorite(entry: AppEntry) = entry.key in favoriteKeys.value

    private fun folderEntry(folder: AppFolder) =
        AppEntry(folder.key, folder.name, "", null, eu.eurise.feature.launcher.apps.Category.Other)

    /** Crea una cartella con [apps]; sulla home prende il posto della prima app tra i preferiti. */
    fun createFolder(apps: List<AppEntry>, place: String, name: String) {
        if (apps.size < 2) return
        val folder = AppFolder("f${System.currentTimeMillis()}", name, place, apps.map { it.key })
        settingsStore.update { it.copy(folders = it.folders + folder) }
        if (place == AppFolder.HOME) {
            val current = favoriteKeys.value
            val index = current.indexOfFirst { k -> apps.any { it.key == k } }.let { if (it < 0) current.size else it }
            val next = current.filterNot { k -> apps.any { it.key == k } }.toMutableList()
            next.add(index.coerceAtMost(next.size), folder.key)
            saveFavorites(next)
        }
    }

    fun renameFolder(folder: AppFolder, name: String) =
        settingsStore.update { s -> s.copy(folders = s.folders.map { if (it.id == folder.id) it.copy(name = name) else it }) }

    fun setFolderApps(folder: AppFolder, keys: List<String>) {
        if (keys.size <= 1) {
            // Una cartella con una sola app torna icona normale.
            settingsStore.update { s -> s.copy(folders = s.folders.filterNot { it.id == folder.id }) }
            if (folder.place == AppFolder.HOME) {
                saveFavorites(favoriteKeys.value.flatMap { if (it == folder.key) keys else listOf(it) })
            }
            return
        }
        settingsStore.update { s -> s.copy(folders = s.folders.map { if (it.id == folder.id) it.copy(keys = keys) else it }) }
    }

    private fun saveFavorites(list: List<String>) {
        favoriteKeys.value = list
        prefs.edit().putString(KEY_FAVORITES, list.joinToString("|")).apply()
    }

    fun toggleFavorite(entry: AppEntry) {
        val current = favoriteKeys.value
        val next = if (entry.key in current) current - entry.key else (current + entry.key).take(settings.value.maxFavorites)
        favoriteKeys.value = next
        prefs.edit().putString(KEY_FAVORITES, next.joinToString("|")).apply()
    }

    /** Nuovo ordine dei preferiti, scelto trascinandoli sulla home. Le chiavi sconosciute si ignorano. */
    fun setFavoriteOrder(keys: List<String>) {
        val current = favoriteKeys.value
        val next = keys.filter { it in current } + current.filter { it !in keys }
        if (next != current) saveFavorites(next)
    }

    /** Sposta un preferito di un posto avanti (+1) o indietro (-1). */
    fun moveFavorite(entry: AppEntry, delta: Int) {
        val list = favoriteKeys.value.toMutableList()
        val i = list.indexOf(entry.key)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        favoriteKeys.value = list
        prefs.edit().putString(KEY_FAVORITES, list.joinToString("|")).apply()
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = settingsStore.update(transform)

    /**
     * «Aggiungi alla Scrivania» (tavola ScrPagAggiungi): sulla home l'app entra nei preferiti (o, se sono pieni, tra le
     * bolle della home); sulle altre pagine diventa una bolla-app in fondo. Un'app già presente in quella pagina non si
     * ripete.
     */
    fun addToPage(key: String, page: String) {
        if (page == eu.eurise.feature.launcher.home.DeskPage.HOME) {
            val current = favoriteKeys.value
            if (key in current) return
            if (current.size < settings.value.maxFavorites) {
                saveFavorites(current + key)
                return
            }
        }
        updateSettings { s ->
            val slots = s.slotsOf(page)
            if (slots.any { it.kind == eu.eurise.feature.launcher.home.HomeKind.App && it.app == key }) s
            else s.withSlots(page, slots + eu.eurise.feature.launcher.home.appSlot(key))
        }
    }

    /**
     * Pagina nuova (tavola ScrPagNuova): dopo [after] (o in fondo), con le bolle di partenza o la copia di [copyOf];
     * [withApp] la riempie con quell'app. Restituisce l'id.
     */
    fun createPage(
        name: String,
        symbol: String,
        mode: eu.eurise.core.data.modes.Mode?,
        start: eu.eurise.feature.launcher.home.PageStart,
        copyOf: String? = null,
        startHere: Boolean = false,
        withApp: String? = null,
        after: String? = null,
    ): String {
        val id = eu.eurise.feature.launcher.home.DeskPage.newId(settings.value.pages)
        updateSettings { s ->
            val base = if (start == eu.eurise.feature.launcher.home.PageStart.Copy && copyOf != null) {
                // La copia della home porta con sé anche i preferiti, come app.
                if (copyOf == eu.eurise.feature.launcher.home.DeskPage.HOME) favoriteKeys.value.map { eu.eurise.feature.launcher.home.appSlot(it) } + s.homeSlots.filter { it.kind != eu.eurise.feature.launcher.home.HomeKind.AppWidget }
                else s.slotsOf(copyOf).filter { it.kind != eu.eurise.feature.launcher.home.HomeKind.AppWidget }
            } else eu.eurise.feature.launcher.home.startSlots(start)
            val slots = base + listOfNotNull(withApp?.let { eu.eurise.feature.launcher.home.appSlot(it) })
            val page = eu.eurise.feature.launcher.home.DeskPage(id, name.trim(), symbol, mode)
            val at = after?.let { a -> s.pages.indexOfFirst { it.id == a } }?.takeIf { it >= 0 }?.plus(1) ?: s.pages.size
            s.copy(
                pages = s.pages.toMutableList().apply { add(at, page) },
                pageSlots = s.pageSlots + (id to slots),
                startPage = if (startHere) id else s.startPage,
            )
        }
        return id
    }

    /**
     * Trascinamento tra le pagine (tavola ScrPagTrascina): [item] lasciato su [target]. Esce dalla pagina da cui veniva (o
     * dai preferiti) ed entra al posto scelto; nei preferiti della home solo un'app e solo se c'è posto, altrimenti tra le
     * bolle della home. Con [CarryTarget.newPage] nasce una pagina nuova dopo quella vista, con dentro l'oggetto.
     * Restituisce la pagina in cui è finito.
     */
    fun carryTo(item: eu.eurise.feature.launcher.home.CarryItem, target: eu.eurise.feature.launcher.home.CarryTarget): String {
        val home = eu.eurise.feature.launcher.home.DeskPage.HOME
        val slot = item.slot
        val page = if (target.newPage) {
            createPage("", eu.eurise.feature.launcher.home.PageSymbol.Star.id, null, eu.eurise.feature.launcher.home.PageStart.Empty, after = target.page)
        } else target.page
        val app = slot.app.takeIf { slot.kind == eu.eurise.feature.launcher.home.HomeKind.App }
        val favs = favoriteKeys.value
        val toFavorites = target.favorites && page == home && app != null &&
            (app in favs || favs.size < settings.value.maxFavorites)
        if (toFavorites) {
            if (app != null && app !in favs) saveFavorites(favs + app)
        } else if (item.fromFavorites && app != null) {
            saveFavorites(favs - app)
        }
        updateSettings { s ->
            var n = s
            item.from?.let { from -> if (from != page || toFavorites) n = n.withSlots(from, n.slotsOf(from).filterNot { it.key == slot.key }) }
            if (!toFavorites) {
                val index = if (target.newPage) -1 else target.index
                n = n.withSlots(page, n.slotsOf(page).placing(slot, index))
            }
            n
        }
        return page
    }

    /** Toglie una pagina dell'utente (o la bacheca, o il riepilogo); la home resta. Le app restano nelle categorie. */
    fun deletePage(id: String) {
        if (id == eu.eurise.feature.launcher.home.DeskPage.HOME) return
        updateSettings { s ->
            s.copy(
                pages = s.pages.filterNot { it.id == id },
                pageSlots = s.pageSlots - id,
                startPage = if (s.startPage == id) eu.eurise.feature.launcher.home.DeskPage.HOME else s.startPage,
                addPage = if (s.addPage == id) eu.eurise.feature.launcher.home.DeskPage.HOME else s.addPage,
            )
        }
    }

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

    /**
     * Le disponibilità dei prossimi tre giorni lavorativi per «Allega › Disponibilità»: orari liberi da un'ora,
     * calcolati sul telefono dagli impegni del calendario.
     */
    suspend fun availabilityText(): String? {
        if (!calendar.hasPermission()) return null
        val now = java.time.LocalDateTime.now()
        val days = eu.eurise.feature.launcher.notifications.ReplyText.nextWorkDays(now)
        val zone = java.time.ZoneId.systemDefault()
        val from = days.first().atStartOfDay(zone).toInstant().toEpochMilli()
        val to = days.last().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = runCatching { calendar.startingBetween(from - 12 * 3600_000L, to) }.getOrDefault(emptyList())
        val free = days.associateWith { d ->
            eu.eurise.core.data.calendar.FreeSlots.find(d, events, java.time.Duration.ofHours(1), now, dayStart = java.time.LocalTime.of(9, 0), dayEnd = java.time.LocalTime.of(19, 0))
        }
        return eu.eurise.feature.launcher.notifications.ReplyText.availability(free, now.toLocalDate())
    }

    fun setTaskDone(task: Task, done: Boolean) {
        viewModelScope.launch { tasks.setDone(task.id, done) }
    }

    /** Sposta a domani un'attività di oggi (o già scaduta). */
    fun postponeTask(task: Task) {
        viewModelScope.launch { tasks.update(task.copy(due = LocalDate.now().plusDays(1))) }
    }

    /** Ciò che è nato da un appunto al volo, per dirlo all'utente. */
    sealed interface Captured {
        data class AsTask(val title: String, val due: LocalDate?, val time: java.time.LocalTime?) : Captured
        data class AsNote(val id: Long) : Captured
    }

    /**
     * Aggiunta "come si parla" dalla bolla Da fare oggi: senza giorno, l'attività è per oggi.
     */
    fun quickTask(text: String) {
        val r = eu.eurise.core.data.model.QuickAdd.parse(text.trim(), java.time.LocalDateTime.now())
        if (r.title.isBlank()) return
        viewModelScope.launch {
            tasks.add(r.title, due = r.due ?: LocalDate.now(), time = r.time, repeat = r.repeat, priority = r.priority,
                remindBefore = if (r.time != null) 0 else null)
        }
    }

    /**
     * Appunto al volo: se la riga dice quando ("domani alle 10", "ogni lunedì") diventa un'attività, altrimenti
     * una nota. [then] riceve che cosa è nato.
     */
    fun capture(text: String, then: (Captured) -> Unit) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val r = eu.eurise.core.data.model.QuickAdd.parse(clean, java.time.LocalDateTime.now())
        viewModelScope.launch {
            if (r.title.isNotBlank() && (r.due != null || r.time != null || r.repeat != null)) {
                tasks.add(r.title, due = r.due ?: LocalDate.now(), time = r.time, repeat = r.repeat, priority = r.priority,
                    remindBefore = if (r.time != null) 0 else null)
                then(Captured.AsTask(r.title, r.due ?: LocalDate.now(), r.time))
            } else {
                val id = notes.save(null, clean.substringBefore('\n').trim(), clean.substringAfter('\n', "").trim())
                then(Captured.AsNote(id))
            }
        }
    }

    /** Nota della riunione: nasce col titolo dell'appuntamento, collegata a lui; [then] riceve l'id della nota. */
    fun meetingNote(event: CalendarEvent, then: (Long) -> Unit) {
        viewModelScope.launch {
            val day = event.start.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.ITALIAN))
            val id = notes.save(null, "${event.title} · $day", "")
            links?.link(eu.eurise.core.data.model.LinkRef.note(id), eu.eurise.core.data.model.LinkRef.event(event.eventId))
            then(id)
        }
    }

    /** Mette l'evento nel cestino unico (torna per 30 giorni con i suoi collegamenti). */
    fun deleteEvent(event: CalendarEvent, then: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                eu.eurise.core.data.EuriseData.create(getApplication()).trash.trashEvent(event.eventId, event.beginMillis)
            }.getOrDefault(false)
            then(ok)
        }
    }

    /** Sposta l'evento a [start] tenendo la durata. */
    fun moveEvent(event: CalendarEvent, start: java.time.LocalDateTime, then: (Boolean) -> Unit) {
        viewModelScope.launch {
            val length = java.time.Duration.between(event.start, event.end)
            val ok = runCatching {
                eu.eurise.core.data.EuriseData.create(getApplication()).calendar.save(
                    eu.eurise.core.data.calendar.EventDraft(
                        event.eventId, event.calendarId, event.title, event.location, event.description, event.allDay,
                        start, start.plus(length),
                    ),
                ) != null
            }.getOrDefault(false)
            then(ok)
        }
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

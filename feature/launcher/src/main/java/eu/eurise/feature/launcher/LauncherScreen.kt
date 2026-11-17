package eu.eurise.feature.launcher

import eu.eurise.feature.launcher.home.DeskPage as HD
import eu.eurise.feature.launcher.home.PagesHelper as HP
import android.Manifest
import eu.eurise.feature.launcher.home.HealthPill
import eu.eurise.feature.launcher.home.openOnSwipe
import eu.eurise.feature.launcher.home.PersonalDays
import eu.eurise.feature.launcher.home.SunTimesColumn
import eu.eurise.feature.launcher.home.rememberNextAlarm
import eu.eurise.feature.launcher.folders.AppPicker
import eu.eurise.feature.launcher.folders.FolderOverlay
import eu.eurise.feature.launcher.folders.FolderTile
import eu.eurise.feature.launcher.feeds.HoroscopeCard
import eu.eurise.feature.launcher.feeds.RecipeCard
import eu.eurise.feature.launcher.feeds.SignPicker
import eu.eurise.feature.launcher.summary.FeedSourcesSheet
import eu.eurise.feature.launcher.home.AgendaPill
import eu.eurise.feature.launcher.home.CommsPill
import eu.eurise.feature.launcher.home.MusicHalf
import eu.eurise.feature.launcher.home.PeopleSheet
import eu.eurise.feature.launcher.home.WorkPill
import eu.eurise.feature.launcher.home.greeting
import eu.eurise.feature.launcher.summary.NewsCard
import eu.eurise.feature.launcher.summary.NewsSourcesSheet
import eu.eurise.feature.launcher.summary.StocksCard
import eu.eurise.feature.launcher.summary.StocksSheet
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import eu.eurise.feature.launcher.panel.EurisePanel
import eu.eurise.feature.launcher.settings.AppearanceSheet
import eu.eurise.feature.launcher.settings.LauncherSettings
import eu.eurise.feature.launcher.summary.CardKind
import eu.eurise.feature.launcher.summary.PlacedCard
import eu.eurise.feature.launcher.summary.PinnedNotesCard
import eu.eurise.feature.launcher.summary.WeatherCard
import android.annotation.SuppressLint
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import android.app.ActivityOptions
import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.data.EuriseData
import eu.eurise.core.designsystem.component.GlassDefaults
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.feature.launcher.home.tellKeyboardTheme
import eu.eurise.feature.launcher.apps.AppEntry
import eu.eurise.feature.launcher.apps.AppIcon
import eu.eurise.feature.launcher.apps.BarCategory
import eu.eurise.feature.launcher.apps.cameraChoices
import eu.eurise.feature.launcher.apps.driveApp
import eu.eurise.feature.launcher.apps.officeApp
import eu.eurise.feature.launcher.apps.suiteOffice
import eu.eurise.feature.launcher.apps.pdfApp
import eu.eurise.feature.launcher.apps.galleryChoices
import eu.eurise.feature.launcher.apps.suiteGallery
import eu.eurise.feature.launcher.apps.LocalCategoryTint
import eu.eurise.feature.launcher.apps.resolveCategory
import eu.eurise.feature.launcher.settings.CategoryEditor
import eu.eurise.feature.launcher.apps.launch
import eu.eurise.feature.launcher.apps.openEurise
import eu.eurise.feature.launcher.home.AppTile
import eu.eurise.feature.launcher.home.searchWeb
import eu.eurise.feature.launcher.home.CategoryButton
import eu.eurise.feature.launcher.home.CategoryPages
import eu.eurise.feature.launcher.home.ClockBlock
import eu.eurise.feature.launcher.home.Label
import eu.eurise.feature.launcher.home.SceneBackground
import eu.eurise.feature.launcher.home.SearchCircle
import eu.eurise.feature.launcher.home.SearchOverlay
import eu.eurise.feature.launcher.home.TodayCard
import eu.eurise.feature.launcher.home.WeatherRow
import eu.eurise.feature.launcher.home.sceneFor
import eu.eurise.feature.launcher.home.scrimColor
import eu.eurise.feature.launcher.notifications.MessageItem
import eu.eurise.feature.launcher.notifications.NotificationStore
import eu.eurise.feature.launcher.summary.CalendarCard
import eu.eurise.feature.launcher.summary.MessagesCard
import eu.eurise.feature.launcher.summary.MusicCard
import eu.eurise.feature.launcher.summary.SetupCard
import eu.eurise.feature.launcher.summary.SetupStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Lato corto da cui il dispositivo è un tablet: un telefono in orizzontale resta un telefono. */
private const val TabletSmallestWidth = 600

/**
 * La Scrivania di Eurise: home con orario, meteo, Oggi e Preferiti; pagina Riepilogo; pagine delle categorie;
 * ricerca e cattura. I colori seguono lo sfondo: tema Notte solo sopra la scena di notte.
 *
 * @param homePresses aumenta ogni volta che si preme il tasto Home mentre la Scrivania è già aperta.
 */
@Composable
fun LauncherRoute(data: EuriseData, homePresses: Int, searchRequest: String? = null, onSearchHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel = viewModel { LauncherViewModel(app, data.notes, data.tasks, data.calendar, data.links) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val badges by NotificationStore.counts.collectAsStateWithLifecycle()
    val messages by NotificationStore.messages.collectAsStateWithLifecycle()
    val nowPlaying by NotificationStore.nowPlaying.collectAsStateWithLifecycle()
    val allNotifications by NotificationStore.all.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val newsItems by viewModel.newsItems.collectAsStateWithLifecycle()
    val quotes by viewModel.quotes.collectAsStateWithLifecycle()
    val horoscope by viewModel.horoscope.collectAsStateWithLifecycle()
    val horoscopeLoaded by viewModel.horoscopeLoaded.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val sign by viewModel.sign.collectAsStateWithLifecycle()
    val signName = stringResource(sign.label)
    LaunchedEffect(signName) { viewModel.refreshHoroscope(signName); viewModel.refreshRecipes() }
    // Un servizio appena acceso (o spento) dal centro privacy o da una scheda: si ricaricano subito i dati.
    val services = eu.eurise.core.designsystem.component.ServiceStates.version
    LaunchedEffect(services) {
        if (services > 0) {
            viewModel.refresh()
            viewModel.refreshNews(force = true)
            viewModel.refreshVideos(force = true)
            viewModel.refreshHoroscope(signName, force = true)
            viewModel.refreshRecipes(force = true)
        }
    }
    val calendarMonth by viewModel.calendarMonth.collectAsStateWithLifecycle()
    val monthEvents by viewModel.monthEvents.collectAsStateWithLifecycle()
    val monthTasks by viewModel.monthTasks.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val videoList by viewModel.videoList.collectAsStateWithLifecycle()
    val videoChannels by viewModel.videos.channels.collectAsStateWithLifecycle()
    val importScope = androidx.compose.runtime.rememberCoroutineScope()
    val importedLabel = stringResource(R.string.videos_imported, 0).substringBefore("0")
    val newPipeImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importScope.launch {
            val added = runCatching { viewModel.videos.importNewPipe(uri) }.getOrDefault(0)
            android.widget.Toast.makeText(context, importedLabel + added, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.refreshVideos(force = true)
        }
    }
    val sun by viewModel.sun.collectAsStateWithLifecycle()
    val healthState by viewModel.healthState.collectAsStateWithLifecycle()
    val healthToday by viewModel.healthToday.collectAsStateWithLifecycle()
    val healthPermission = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refreshHealth() }
    val importantDates by data.dates.dates.collectAsStateWithLifecycle()
    val travel by viewModel.travel.collectAsStateWithLifecycle()
    var filesAccess by remember { mutableStateOf(viewModel.files.hasAccess()) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        filesAccess = viewModel.files.hasAccess(); viewModel.refreshFiles()
    }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = LocalDateTime.now()
        }
    }
    var notificationsOn by remember { mutableStateOf(NotificationStore.isEnabled(context)) }
    var isHome by remember { mutableStateOf(isDefaultHome(context)) }
    var hasLocation by remember { mutableStateOf(viewModel.hasLocationPermission()) }
    // Meteo per la frase sotto il primo impegno: a ogni ritorno sulla Scrivania, con la copia di 30 minuti di Meteo.
    val meteoRepo = remember { eu.eurise.core.data.weather.MeteoRepository(context) }
    var meteo by remember { mutableStateOf<eu.eurise.core.data.weather.MeteoState?>(null) }
    var meteoTick by remember { mutableStateOf(0) }
    LaunchedEffect(hasLocation, meteoTick) {
        if (!hasLocation) { meteo = null; return@LaunchedEffect }
        if (meteo == null) meteo = meteoRepo.cached()
        meteo = meteoRepo.load(withPollen = false)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        meteoTick++
        now = LocalDateTime.now()
        notificationsOn = NotificationStore.isEnabled(context)
        isHome = isDefaultHome(context)
        hasLocation = viewModel.hasLocationPermission()
        filesAccess = viewModel.files.hasAccess()
        viewModel.refresh()
    }

    // Luogo del prossimo appuntamento di oggi con un indirizzo: per il tempo di viaggio.
    val destinations = state.eventsToday
        .filter { !it.allDay && it.end.isAfter(now) && it.location.isNotBlank() }
    val destination = destinations.firstOrNull()?.location
    LaunchedEffect(destination, hasLocation) { viewModel.refreshTravel(destination) }
    // Luoghi di oggi: appuntamenti con indirizzo e cose da fare con un #luogo.
    val travelPlaces = destinations.map { it.location } + state.tasksDue.mapNotNull {
        eu.eurise.feature.launcher.home.BubbleText.taskPlace(it.title) ?: eu.eurise.feature.launcher.home.BubbleText.taskPlace(it.notes)
    }
    LaunchedEffect(travelPlaces, hasLocation) { viewModel.refreshTravels(travelPlaces) }
    val travels by viewModel.travels.collectAsStateWithLifecycle()
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasLocation = it
        viewModel.refresh()
    }
    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isHome = isDefaultHome(context) }

    val actions = remember(context) {
        LauncherActions(
            openApp = { viewModel.recordLaunch(it); context.launch(it) },
            openLink = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            openClock = {
                context.openEurise("clock")
            },
            ownAppInfo = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            expandNotifications = { context.expandNotifications() },
            openEurise = { context.openEurise(it) },
            appInfo = { entry ->
                val web = entry.shortcut
                if (web != null) {
                    eu.eurise.feature.launcher.apps.WebApps.remove(context, web)
                } else {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${entry.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            },
            openMessage = { context.openMessage(it) },
            grantNotifications = {
                context.openNotificationAccess()
            },
            connectCalendar = { calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
            askLocation = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            becomeHome = { requestHomeRole(context)?.let { homeRole.launch(it) } },
        )
    }

    val personal = PersonalDays(
        birthday = importantDates.firstOrNull { it.mine && it.kind == eu.eurise.core.data.dates.ImportantDate.Kind.Birthday }?.monthDay,
        anniversary = importantDates.firstOrNull { it.mine && it.kind.isAnniversary }?.monthDay,
    )
    val scene = sceneFor(now, settings.wallpaper, weather?.firstOrNull()?.sky, personal)
        .let { s -> when (settings.themeMode) { 1 -> s.copy(forceDark = false); 2 -> s.copy(forceDark = true); else -> s } }
    // La schermata di blocco segue la stessa scena, se l'utente l'ha chiesto: si ridisegna solo quando cambia.
    androidx.compose.runtime.LaunchedEffect(scene.copy(forceDark = null)) {
        eu.eurise.feature.launcher.home.LockWallpaper.noteInputs(context, weather?.firstOrNull()?.sky, personal)
        eu.eurise.feature.launcher.home.LockWallpaper.refresh(context)
    }
    // Anche lo sfondo di sistema della home segue la scena: si vede per un attimo aprendo le app recenti.
    androidx.compose.runtime.LaunchedEffect(scene.copy(forceDark = null)) { eu.eurise.feature.launcher.home.LockWallpaper.refreshHome(context, scene) }
    // La tastiera di Eurise segue le bolle: scure qui, scura anche lei.
    androidx.compose.runtime.LaunchedEffect(scene.themeDark) { context.tellKeyboardTheme(scene.themeDark) }
    EuriseTheme(darkTheme = scene.themeDark) {
      // Di notte l'accento si abbassa e si intona allo sfondo: meno acceso, più scuro.
      val accent = settings.accent.color(scene.themeDark).let { if (scene.themeDark) nightAccent(it) else it }
      val onAccent = if (0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue > 0.55f) Color(0xFF14171C) else Color.White
      MaterialTheme(
          colorScheme = MaterialTheme.colorScheme.copy(
              primary = accent,
              onPrimary = onAccent,
              // Binari di cursori e interruttori spenti: grigi neutri, non la salvia del tema.
              secondaryContainer = if (scene.themeDark) Color(0xFF2E343E) else Color(0xFFE2E2DC),
              onSecondaryContainer = MaterialTheme.colorScheme.onSurface,
          ),
      ) {
      // Il colore del testo segue il tema: senza questo i testi senza colore esplicito restano neri anche di notte.
      androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
      // Testi più compatti: interlinea stretta su tutta la Scrivania.
      ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = 1.4.em)) {
      androidx.compose.runtime.CompositionLocalProvider(
          eu.eurise.feature.launcher.apps.LocalVariedIcons provides settings.variedIcons,
          eu.eurise.feature.launcher.apps.LocalAppIconColors provides settings.appIconColors,
          eu.eurise.feature.launcher.apps.LocalIconsFollowBubbles provides settings.iconsFollowBubbles,
          eu.eurise.feature.launcher.apps.LocalIconTheme provides settings.iconTheme,
          eu.eurise.feature.launcher.apps.LocalAccentGradient provides settings.accent.gradient(scene.themeDark),
          eu.eurise.feature.launcher.apps.LocalIconShape provides settings.iconShape,
          eu.eurise.feature.launcher.home.LocalMeteo provides meteo,
          LocalCategoryTint provides { entry -> resolveCategory(entry, settings.categories, settings.categoryOverrides)?.tint },
          // Sulla Scrivania le bolle si staccano appena un po' di più dallo sfondo.
          eu.eurise.core.designsystem.theme.LocalEuriseColors provides launcherGlass(eu.eurise.core.designsystem.theme.EuriseThemeExtras.colors, accent.takeIf { settings.iconTheme == eu.eurise.feature.launcher.apps.IconThemes.BUBBLES }),
      ) {
        val hazeState = remember { HazeState() }
        var weatherSheet by remember { mutableStateOf(false) }
        // Il meteo apre Eurise Meteo; senza posizione la chiede prima.
        val openWeather: () -> Unit = {
            if (hasLocation) actions.openEurise("weather") else actions.askLocation()
        }
        // Categoria scelta nella barra, per id; HomePageId è la home. I Preferiti sono una categoria come le altre.
        var category by remember { mutableStateOf(HomePageId) }
        var categoryEditor by remember { mutableStateOf(false) }
        val barCategories = settings.categories
        val pageCategories = barCategories
        val dockRoles = remember(state.apps) {
            eu.eurise.feature.launcher.home.DockRole.entries.associateWith { r -> eu.eurise.feature.launcher.home.roleApps(context, r, state.apps) }
        }
        if (category != HomePageId && pageCategories.none { it.id == category }) category = HomePageId
        val categoryOf: (AppEntry) -> BarCategory? = { entry -> resolveCategory(entry, barCategories, settings.categoryOverrides) }
        var searching by remember { mutableStateOf(false) }
        // «Nella suite» dalla ricerca rapida della Tastiera: la ricerca universale già con la domanda.
        var searchStart by remember { mutableStateOf("") }
        LaunchedEffect(searchRequest) {
            if (searchRequest != null) { searchStart = searchRequest; searching = true; onSearchHandled() }
        }
        LaunchedEffect(searching) { if (!searching) searchStart = "" }
        var panelOpen by remember { mutableStateOf(false) }
        var appearance by remember { mutableStateOf(false) }
        var modesSheet by remember { mutableStateOf<eu.eurise.core.data.modes.Mode?>(null) }
        var aodSettings by remember { mutableStateOf(false) }
        var soundsScreen by remember { mutableStateOf(false) }
        var lockWallpaperScreen by remember { mutableStateOf(false) }
        var editingCards by remember { mutableStateOf(false) }
        var editingHome by remember { mutableStateOf(false) }
        var homeCatalog by remember { mutableStateOf(false) }
        var sideCatalog by remember { mutableStateOf<String?>(null) }
        BackHandler(enabled = sideCatalog != null) { sideCatalog = null }
        var channelsSheet by remember { mutableStateOf(false) }
        var replyTo by remember { mutableStateOf<MessageItem?>(null) }
        // Elenco dei contatti aperto dalla pillola Comunicazioni: "recent" o "starred".
        var peopleSheet by remember { mutableStateOf<String?>(null) }
        var quick by remember { mutableStateOf<Pair<eu.eurise.feature.launcher.home.QuickAction, String>?>(null) }
        var searchPerson by remember { mutableStateOf<eu.eurise.feature.launcher.contacts.Person?>(null) }
        var signPicker by remember { mutableStateOf(false) }
        // Cartella aperta (id) e scelta delle app per crearne una o aggiungerne.
        var openFolder by remember { mutableStateOf<String?>(null) }
        var folderPicker by remember { mutableStateOf<FolderRequest?>(null) }
        // Fonti di oroscopo o ricette in modifica: "horoscope" o "recipes".
        var dailySheet by remember { mutableStateOf<String?>(null) }
        // La dock del telefono (tavole ScrDock, ScrDockTieni, ScrDockStella, BolComunicaApp): la pillola aperta sopra la dock.
        var dockPanel by remember { mutableStateOf<eu.eurise.feature.launcher.home.DockPanel?>(null) }
        val dockPrefs = remember { eu.eurise.feature.launcher.home.DockPrefs(context) }
        val peopleShortcuts = remember {
            eu.eurise.feature.launcher.home.PeopleShortcuts({ peopleSheet = "recent" }, { peopleSheet = "starred" }, { dockPanel = eu.eurise.feature.launcher.home.DockPanel.Comms })
        }
        val welcome = WelcomeData(
            travel = travel,
            destination = destination,
            destinations = destinations,
            travels = travels,
            messages = messages,
            notificationsOn = notificationsOn,
            nowPlaying = nowPlaying,
            files = recentFiles,
            filesAccess = filesAccess,
            onRoute = { place ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            onRecent = { peopleSheet = "recent" },
            onStarred = { peopleSheet = "starred" },
            onOpenFile = { viewModel.files.open(it) },
            onFilesGrant = {
                val intent = viewModel.files.accessIntent()
                if (intent != null) runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                else storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            },
            sun = sun,
            healthState = healthState,
            healthToday = healthToday,
            onHealthGrant = { healthPermission.launch(viewModel.health.permissions) },
            onGadgetbridge = { runCatching { context.startActivity(viewModel.health.gadgetbridgeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            hazeState = hazeState,
            // Lo scanner della suite (sezione «scanner» dell'app Eurise).
            onScan = { actions.openEurise("scanner") },
            onPlayer = {
                val pkg = nowPlaying?.controller?.packageName
                val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
                    ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
                runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
        )
        val noteSaved = stringResource(R.string.bubble_saved_note)
        val taskSaved = stringResource(R.string.bubble_saved_task)
        val eventTrashed = stringResource(R.string.bubble_event_trashed)
        val eventMoved = stringResource(R.string.bubble_event_moved)
        val eventFailed = stringResource(R.string.bubble_event_failed)
        val bubbleActions = eu.eurise.feature.launcher.home.BubbleActions(
            setDone = { viewModel.setTaskDone(it, true) },
            postpone = viewModel::postponeTask,
            quickTask = viewModel::quickTask,
            openTasks = { actions.openEurise("tasks") },
            openMedicines = { actions.openEurise("medicines") },
            openDay = { actions.openEurise("day:$it") },
            navigate = welcome.onRoute,
            // La nota della riunione dell'occorrenza (presenti, ordine del giorno, appunti, azioni).
            meetingNote = { e -> actions.openEurise("meeting:${e.eventId}:${e.beginMillis}") },
            openLink = actions.openLink,
            setReadCodes = { on -> viewModel.updateSettings { it.copy(readCodes = on) } },
            openClock = actions.openClock,
            capture = { text ->
                viewModel.capture(text) { c ->
                    val msg = when (c) {
                        is LauncherViewModel.Captured.AsNote -> noteSaved
                        is LauncherViewModel.Captured.AsTask -> taskSaved.format(c.title)
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            openNote = { actions.openEurise("note:${it.id}") },
            openNotes = { actions.openEurise("notes") },
            newNote = { actions.openEurise("new_note") },
            grantNotifications = actions.grantNotifications,
            addTask = { text ->
                viewModel.quickTask(text)
                android.widget.Toast.makeText(context, taskSaved.format(text), android.widget.Toast.LENGTH_SHORT).show()
            },
            callBack = { m -> eu.eurise.feature.launcher.notifications.CallBack.call(context, m) },
            deleteEvent = { e ->
                viewModel.deleteEvent(e) { ok ->
                    android.widget.Toast.makeText(context, if (ok) eventTrashed else eventFailed, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            moveEvent = { e, start ->
                viewModel.moveEvent(e, start) { ok ->
                    android.widget.Toast.makeText(context, if (ok) eventMoved else eventFailed, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            openWeather = openWeather,
            contactActions = { viewModel.contacts.actionsForName(it) },
            commsApps = { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { viewModel.contacts.bubbleApps() } },
            startAction = { viewModel.contacts.start(it) },
        )
        // Le pagine della Scrivania (tavole ScrPag*): di base bacheca, home e riepilogo; l'utente ne aggiunge quante vuole.
        val modeState by eu.eurise.core.data.modes.Modes.state.collectAsStateWithLifecycle()
        val deskPages = remember(settings.pages, modeState.active) { settings.pages.let { all -> HP.visible(all, modeState.active) } }
        val startIndex = deskPages.indexOfFirst { it.id == settings.startPage }.takeIf { it >= 0 }
            ?: deskPages.indexOfFirst { it.id == HD.HOME }.coerceAtLeast(0)
        val homePager = rememberPagerState(initialPage = startIndex) { deskPages.size }
        var pagesSheet by remember { mutableStateOf(false) }
        // Pagina nuova: null chiuso, "" senza app, altrimenti la chiave dell'app da metterci.
        var newPageFor by remember { mutableStateOf<String?>(null) }
        var pagePicker by remember { mutableStateOf<eu.eurise.feature.launcher.apps.AppEntry?>(null) }
        var goToPage by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(goToPage, deskPages) {
            val target = goToPage ?: return@LaunchedEffect
            val i = deskPages.indexOfFirst { it.id == target }
            if (i >= 0) { category = HomePageId; homePager.scrollToPage(i); goToPage = null }
        }
        var newsSheet by remember { mutableStateOf(false) }
        var stocksSheet by remember { mutableStateOf(false) }
        val categoryPager = rememberPagerState { pageCategories.size }
        val scope = rememberCoroutineScope()

        // La modalità accesa può scegliere la categoria da cui parte la Scrivania.
        val modeCategory = modeState.config?.homeCategory?.takeIf { id -> pageCategories.any { it.id == id } }
        LaunchedEffect(modeState.active) {
            if (modeCategory != null) {
                category = modeCategory
                categoryPager.scrollToPage(pageCategories.indexOfFirst { it.id == modeCategory }.coerceAtLeast(0))
            }
        }
        LaunchedEffect(homePresses) {
            if (homePresses > 0) {
                if (category == HomePageId && !searching) homePager.animateScrollToPage(startIndex)
                // Con una modalità che sceglie la categoria, il tasto Home porta lì; premuto di nuovo, alla home.
                category = if (modeCategory != null && category != modeCategory) modeCategory else HomePageId
                if (category == modeCategory) categoryPager.scrollToPage(pageCategories.indexOfFirst { it.id == modeCategory }.coerceAtLeast(0))
                categoryEditor = false
                searching = false
                panelOpen = false
                appearance = false
                editingCards = false
                editingHome = false
            }
        }
        LaunchedEffect(categoryPager.currentPage) {
            if (category != HomePageId) pageCategories.getOrNull(categoryPager.currentPage)?.let { category = it.id }
        }
        BackHandler(enabled = modesSheet != null || replyTo != null || homeCatalog || channelsSheet || editingHome || folderPicker != null || openFolder != null || signPicker || dailySheet != null || peopleSheet != null || weatherSheet || searching || panelOpen || appearance || editingCards || newsSheet || stocksSheet || categoryEditor || category != HomePageId || homePager.currentPage != startIndex) {
            when {
                modesSheet != null -> modesSheet = null
                replyTo != null -> replyTo = null
                homeCatalog -> homeCatalog = false
                channelsSheet -> { channelsSheet = false; viewModel.refreshVideos(force = true) }
                editingHome -> editingHome = false
                folderPicker != null -> folderPicker = null
                openFolder != null -> openFolder = null
                signPicker -> signPicker = false
                dailySheet != null -> dailySheet = null
                peopleSheet != null -> peopleSheet = null
                weatherSheet -> weatherSheet = false
                newsSheet -> { newsSheet = false; viewModel.refreshNews(force = true) }
                stocksSheet -> { stocksSheet = false; viewModel.refreshQuotes() }
                appearance -> appearance = false
                panelOpen -> panelOpen = false
                editingCards -> editingCards = false
                searching -> searching = false
                categoryEditor -> categoryEditor = false
                category != HomePageId -> category = HomePageId
                else -> scope.launch { homePager.animateScrollToPage(startIndex) }
            }
        }

        // Dichiarato dopo l'altro: con una pillola della dock aperta, «indietro» chiude prima quella.
        BackHandler(enabled = dockPanel != null) { dockPanel = null }

        fun select(c: BarCategory) {
            // Toccare la categoria già aperta riporta alla home.
            if (category == c.id) { category = HomePageId; return }
            category = c.id
            scope.launch { categoryPager.scrollToPage(pageCategories.indexOfFirst { it.id == c.id }.coerceAtLeast(0)) }
        }

        val setup = listOf(
            SetupStep(stringResource(R.string.setup_home), isHome, actions.becomeHome),
            SetupStep(stringResource(R.string.setup_notifications), notificationsOn, actions.grantNotifications),
            SetupStep(stringResource(R.string.setup_location), hasLocation, actions.askLocation),
            SetupStep(stringResource(R.string.setup_calendar), state.calendarConnected, actions.connectCalendar),
            // Telefono nuovo: si offre il ripristino finché non c'è ancora nessun dato.
            SetupStep(stringResource(R.string.setup_restore), state.notes.isNotEmpty() || state.openTasks.isNotEmpty()) { actions.openEurise("backup") },
        )
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val clearance = GlassDefaults.PillHeight + GlassDefaults.BottomMargin * 2 + navBottom

        androidx.compose.runtime.CompositionLocalProvider(eu.eurise.feature.launcher.home.LocalCommsApps provides { dockPanel = eu.eurise.feature.launcher.home.DockPanel.Comms }) {
        Box(Modifier.fillMaxSize()) {
            SceneBackground(scene, Modifier.fillMaxSize().hazeSource(hazeState))

            // Da ogni pagina: in su ricerca e app, in giù il Pannello Eurise (non mentre si modifica o c'è un foglio aperto).
            BoxWithConstraints(
                Modifier.fillMaxSize().then(
                    if (searching || panelOpen || editingCards || editingHome || categoryEditor || appearance || replyTo != null) Modifier
                    else Modifier.openOnSwipe(
                        onUp = { if (settings.swipeSearch) searching = true },
                        onDown = { if (settings.pullPanel) panelOpen = true },
                    ),
                ),
            ) {
                val tablet = LocalConfiguration.current.smallestScreenWidthDp >= TabletSmallestWidth
                val homeData = HomeBoardData(
                                        editing = editingHome,
                                        onEditing = { editingHome = it },
                                        onChange = { slots -> viewModel.updateSettings { it.copy(homeSlots = slots) } },
                                        onCatalog = { homeCatalog = true },
                                        videos = videoList,
                                        hasChannels = videoChannels.isNotEmpty(),
                                        onOpenVideo = { viewModel.videos.open(it) },
                                        onChannels = { channelsSheet = true },
                                        onReply = { replyTo = it },
                                        bubbles = bubbleActions,
                                        notifications = allNotifications,
                                        readCodes = settings.readCodes,
                                        importantDates = importantDates,
                                        apps = remember(state.apps) { state.apps.associateBy { it.key } },
                                        dark = scene.themeDark,
                                        badges = badges,
                                        iconSize = settings.iconSize.dp,
                                        showLabels = settings.showLabels,
                                        cardBody = { kind, modifier ->
                                            androidx.compose.runtime.CompositionLocalProvider(eu.eurise.feature.launcher.notifications.LocalQuickReply provides { replyTo = it }, eu.eurise.feature.launcher.home.LocalPeopleShortcuts provides peopleShortcuts, eu.eurise.feature.launcher.home.LocalBubbleActions provides bubbleActions) {
                                            SummaryCardBody(
                                                // Il Calendario in bolla è sempre grande: mese da toccare più gli impegni.
                                                PlacedCard(kind, eu.eurise.feature.launcher.summary.GRID_COLS, if (kind == CardKind.Calendar) 30 else 22), modifier, state, messages, nowPlaying, notificationsOn,
                                                hazeState, actions, weather, newsItems, quotes, { newsSheet = true }, { stocksSheet = true },
                                                DailyData(
                                                    horoscope = horoscope, horoscopeLoaded = horoscopeLoaded, sign = sign, dark = scene.themeDark, recipes = recipes,
                                                    onSign = { signPicker = true }, onHoroscopeSources = { dailySheet = "horoscope" }, onRecipeSources = { dailySheet = "recipes" },
                                                ),
                                                calendarMonth, monthEvents, monthTasks, viewModel::setCalendarMonth,
                                            )
                                            }
                                        },
                                    )
                val board: @Composable (String, Modifier) -> Unit = { pageId, modifier ->
                    androidx.compose.runtime.CompositionLocalProvider(eu.eurise.feature.launcher.notifications.LocalQuickReply provides { replyTo = it }, eu.eurise.feature.launcher.home.LocalPeopleShortcuts provides peopleShortcuts, eu.eurise.feature.launcher.home.LocalBubbleActions provides bubbleActions) {
                    androidx.compose.runtime.CompositionLocalProvider(eu.eurise.feature.launcher.home.LocalBubbleActions provides bubbleActions) {
                    val sideAlarm = rememberNextAlarm()
                    SidePage(
                        slots = settings.slotsOf(pageId),
                        editing = editingCards,
                        onEditingChange = { editingCards = it },
                        onChange = { slots -> viewModel.updateSettings { it.withSlots(pageId, slots) } },
                        onCatalog = { sideCatalog = pageId },
                        setup = if (pageId == HD.RIGHT) setup else emptyList(),
                        hazeState = hazeState,
                        clearance = clearance,
                        modifier = modifier,
                    ) { slot, mod -> HomeBubble(slot, mod, now, state, welcome, hazeState, false, actions, homeData, sideAlarm) }
                    }
                    }
                }
                val summary: @Composable (Modifier) -> Unit = { modifier -> board(HD.RIGHT, modifier) }

                if (searching || panelOpen) {
                    // Durante la ricerca e nel pannello resta solo lo sfondo sfocato.
                } else if (category == HomePageId) {
                    if (tablet) {
                        TabletHome(
                            now = now, weather = weather, hasLocation = hasLocation, state = state,
                            hazeState = hazeState, actions = actions, onTaskDone = { viewModel.setTaskDone(it, true) },
                            clearance = clearance, summary = summary, onWeather = openWeather,
                        )
                    } else {
                        HorizontalPager(homePager, Modifier.fillMaxSize(), userScrollEnabled = !editingCards && !editingHome, key = { deskPages.getOrNull(it)?.id ?: it }) { index ->
                            val pageId = deskPages.getOrNull(index)?.id ?: HD.HOME
                            if (pageId != HD.HOME) {
                                board(pageId, Modifier.fillMaxSize())
                            } else {
                                PhoneHome(
                                    now = now, weather = weather, hasLocation = hasLocation, state = state, badges = badges,
                                    dark = scene.themeDark, hazeState = hazeState, actions = actions,
                                    isFavorite = viewModel::isFavorite, onToggleFavorite = viewModel::toggleFavorite,
                                    onTaskDone = { viewModel.setTaskDone(it, true) }, clearance = clearance,
                                    isHome = isHome,
                                    onWeather = openWeather,
                                    onSearch = { searching = true },
                                    onPanel = { panelOpen = true },
                                    onAppearance = { appearance = true },
                                    settings = settings,
                                    onMoveFavorite = viewModel::moveFavorite,
                                    onReorderFavorites = viewModel::setFavoriteOrder,
                                    onQuickApp = { gallery, key -> viewModel.updateSettings { if (gallery) it.copy(quickGallery = key) else it.copy(quickCamera = key) } },
                                    welcome = welcome,
                                    onOpenFolder = { openFolder = it.id },
                                    onCreateFolder = { app -> folderPicker = FolderRequest(null, eu.eurise.feature.launcher.folders.AppFolder.HOME, app.key) },
                                    skyLight = scene.skyLight,
                                    home = homeData,
                                )
                            }
                        }
                        if (!editingCards && !editingHome) PageDots(
                            homePager.currentPage, deskPages.size,
                            Modifier.align(Alignment.BottomCenter).padding(bottom = clearance - 20.dp),
                            onClick = { pagesSheet = true },
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(scrimColor()))
                    CategoryPages(
                        pager = categoryPager,
                        categories = pageCategories,
                        categoryOf = categoryOf,
                        apps = state.apps,
                        folders = settings.folders,
                        onOpenFolder = { openFolder = it.id },
                        onCreateFolder = { app, cat -> folderPicker = FolderRequest(null, cat.id, app.key) },
                        dark = scene.themeDark,
                        badges = badges,
                        isFavorite = viewModel::isFavorite,
                        onOpen = actions.openApp,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onInfo = actions.appInfo,
                        onMoveTo = { app, target ->
                            viewModel.updateSettings { it.copy(categoryOverrides = it.categoryOverrides + (app.key to target.id)) }
                        },
                        orderOf = { settings.appOrder[it.id] },
                        onReorder = { target, keys -> viewModel.updateSettings { it.copy(appOrder = it.appOrder + (target.id to keys)) } },
                        bottomClearance = clearance,
                        columns = settings.columns,
                        iconSize = settings.iconSize.dp,
                        showLabels = settings.showLabels,
                        onClose = { category = HomePageId },
                        favorites = state.favorites,
                        onAppearance = { appearance = true },
                        onAddToPage = { pagePicker = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                val barModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = GlassDefaults.SideMargin, vertical = GlassDefaults.BottomMargin)
                if (searching || panelOpen || editingCards || editingHome || categoryEditor) {
                    // nessuna barra
                } else if (tablet) {
                    TabletDock(
                        favorites = state.favorites.take(settings.columns), badges = badges, dark = scene.themeDark,
                        categories = barCategories, selected = category,
                        onOpen = actions.openApp, onSelect = ::select, onSearch = { searching = true }, onEdit = { categoryEditor = true },
                        hazeState = hazeState, modifier = barModifier.widthIn(max = 960.dp),
                    )
                } else {
                    eu.eurise.feature.launcher.home.PhoneDock(
                        roles = dockRoles.mapValues { (r, list) -> eu.eurise.feature.launcher.home.dockApp(dockPrefs, r, list) },
                        badges = badges, open = dockPanel, hazeState = hazeState,
                        onAll = { dockPanel = if (dockPanel == eu.eurise.feature.launcher.home.DockPanel.All) null else eu.eurise.feature.launcher.home.DockPanel.All },
                        onStar = { dockPanel = if (dockPanel == eu.eurise.feature.launcher.home.DockPanel.Star) null else eu.eurise.feature.launcher.home.DockPanel.Star },
                        onRole = { r ->
                            dockPanel = null
                            val app = eu.eurise.feature.launcher.home.dockApp(dockPrefs, r, dockRoles[r].orEmpty())
                            if (app != null) actions.openApp(app) else dockPanel = eu.eurise.feature.launcher.home.DockPanel.Role(r)
                        },
                        onRoleHold = { r -> dockPanel = eu.eurise.feature.launcher.home.DockPanel.Role(r) },
                        onSearch = { dockPanel = null; searching = true },
                        modifier = barModifier.fillMaxWidth(),
                    )
                }
            }

            if (searching) {
                SearchOverlay(
                    initialQuery = searchStart,
                    apps = state.apps,
                    recents = state.recents,
                    notes = state.notes,
                    tasks = state.openTasks,
                    dark = scene.themeDark,
                    hazeState = hazeState,
                    leftHanded = settings.leftHanded,
                    onOpenApp = { searching = false; actions.openApp(it) },
                    onOpenNote = { searching = false; actions.openEurise("note:${it.id}") },
                    onOpenTask = { searching = false; actions.openEurise("tasks") },
                    onClose = { searching = false },
                    conversations = messages,
                    rankContacts = { viewModel.contacts.ranked(it) },
                    searchEvents = { viewModel.searchEvents(it) },
                    onOpenEvent = { searching = false; actions.openEurise("day:${it.start.toLocalDate()}") },
                    onOpenContact = { viewModel.contacts.rememberOpened(it); searchPerson = it; peopleSheet = "search" },
                    onOpenMessage = { searching = false; actions.openMessage(it) },
                    onQuick = { action, q ->
                        if (action == eu.eurise.feature.launcher.home.QuickAction.Web) {
                            searching = false
                            context.searchWeb(q)
                        } else {
                            quick = action to q
                        }
                    },
                )
            }
            quick?.let { (action, q) ->
                val done = { quick = null }
                when (action) {
                    eu.eurise.feature.launcher.home.QuickAction.Note -> eu.eurise.core.links.QuickNoteSheet(data, q, done)
                    eu.eurise.feature.launcher.home.QuickAction.Task -> eu.eurise.core.links.QuickTaskSheet(data, q, done)
                    eu.eurise.feature.launcher.home.QuickAction.Event -> eu.eurise.core.links.QuickEventSheet(data, q, done)
                    eu.eurise.feature.launcher.home.QuickAction.Documents -> eu.eurise.feature.launcher.files.DocumentsSheet(
                        query = q, files = viewModel.files, onOpenDrive = { searching = false; actions.openEurise("drive") }, onDismiss = done,
                    )
                    eu.eurise.feature.launcher.home.QuickAction.Web -> Unit
                }
            }
            if (weatherSheet && weather != null) {
                WeatherSheet(weather!!, onClose = { weatherSheet = false })
            }
            if (panelOpen) {
                EurisePanel(
                    now = now,
                    notifications = allNotifications,
                    nowPlaying = nowPlaying,
                    notificationsOn = notificationsOn,
                    hazeState = hazeState,
                    onOpen = { panelOpen = false; actions.openMessage(it) },
                    onGrant = actions.grantNotifications,
                    onSystemShade = { panelOpen = false; actions.expandNotifications() },
                    onClose = { panelOpen = false },
                    onReply = { replyTo = it },
                    onModeSettings = { modesSheet = it },
                )
            }
            modesSheet?.let { m ->
                eu.eurise.feature.launcher.settings.ModesSheet(settings.categories, start = m, onClose = { modesSheet = null })
            }
            if (newsSheet) {
                NewsSourcesSheet(viewModel.news, onClose = { newsSheet = false; viewModel.refreshNews(force = true) })
            }
            if (stocksSheet) {
                StocksSheet(viewModel.markets, onClose = { stocksSheet = false; viewModel.refreshQuotes() })
            }
            settings.folders.firstOrNull { it.id == openFolder }?.let { folder ->
                val byKey = state.apps.associateBy { it.key }
                FolderOverlay(
                    folder = folder,
                    apps = folder.keys.mapNotNull { byKey[it] },
                    dark = scene.themeDark,
                    iconSize = settings.iconSize.dp,
                    badges = badges,
                    onOpenApp = { openFolder = null; actions.openApp(it) },
                    onRename = { viewModel.renameFolder(folder, it) },
                    onAdd = { folderPicker = FolderRequest(folder.id, folder.place, null) },
                    onRemove = { app -> viewModel.setFolderApps(folder, folder.keys - app.key); if (folder.keys.size <= 2) openFolder = null },
                    onClose = { openFolder = null },
                )
            }
            folderPicker?.let { req ->
                val existing = settings.folders.firstOrNull { it.id == req.folderId }
                AppPicker(
                    title = stringResource(if (existing != null) R.string.folder_add_apps else R.string.folder_create),
                    apps = state.apps,
                    preselected = existing?.keys?.toSet() ?: setOfNotNull(req.startKey),
                    dark = scene.themeDark,
                    onDone = { chosen ->
                        folderPicker = null
                        if (existing != null) viewModel.setFolderApps(existing, chosen.map { it.key })
                        else viewModel.createFolder(chosen, req.place, context.getString(R.string.folder_default_name))
                    },
                    onClose = { folderPicker = null },
                )
            }
            if (signPicker) {
                SignPicker(sign, scene.themeDark, onPick = { picked ->
                    signPicker = false
                    viewModel.setSign(picked, context.getString(picked.label))
                }, onClose = { signPicker = false })
            }
            dailySheet?.let { kind ->
                val sources = if (kind == "horoscope") viewModel.daily.horoscope else viewModel.daily.recipes
                FeedSourcesSheet(
                    stringResource(if (kind == "horoscope") R.string.horoscope_sources else R.string.recipe_sources), sources,
                    onClose = {
                        dailySheet = null
                        if (kind == "horoscope") viewModel.refreshHoroscope(signName, force = true) else viewModel.refreshRecipes(force = true)
                    },
                )
            }
            peopleSheet?.let { kind ->
                var people by remember(kind) { mutableStateOf<List<eu.eurise.feature.launcher.contacts.Person>?>(null) }
                var granted by remember(kind) { mutableStateOf(viewModel.contacts.hasContacts()) }
                LaunchedEffect(kind, granted) {
                    people = when (kind) {
                        "recent" -> viewModel.contacts.recent()
                        "search" -> listOfNotNull(searchPerson)
                        else -> viewModel.contacts.starred()
                    }
                }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = viewModel.contacts.hasContacts() }
                PeopleSheet(
                    title = if (kind == "search") searchPerson?.name.orEmpty() else stringResource(if (kind == "recent") R.string.people_recent else R.string.people_starred),
                    initial = if (kind == "search") searchPerson else null,
                    people = people,
                    needsPermission = !granted || (kind == "recent" && !viewModel.contacts.hasCallLog()),
                    onGrant = {
                        contactsPermission.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG))
                    },
                    loadActions = { viewModel.contacts.actions(it) },
                    onAction = { peopleSheet = null; searching = false; viewModel.contacts.start(it) },
                    onOpenContact = { person ->
                        peopleSheet = null
                        scope.launch {
                            val key = viewModel.contacts.lookupKey(person)
                            if (key != null) actions.openEurise("person:$key") else viewModel.contacts.openContact(person)
                        }
                    },
                    onClose = { peopleSheet = null },
                    grouped = kind == "recent",
                    // Niente fila di app in testa ai recenti (richiesta del proprietario): le app sono nel menu della bolla.
                    quickApps = emptyList(),
                    onQuickApp = { peopleSheet = null; viewModel.contacts.startIntent(it) },
                )
            }
            if (categoryEditor) {
                CategoryEditor(
                    categories = barCategories,
                    appCount = { c -> if (c.isAll) state.apps.size else state.apps.count { categoryOf(it)?.id == c.id } },
                    hazeState = hazeState,
                    onChange = { list -> viewModel.updateSettings { it.copy(categories = list) } },
                    onClose = { categoryEditor = false },
                )
            }
            // Un solo «aggiungi widget» fuori dai fogli: il foglio si chiude mentre Android chiede il permesso.
            var widgetPage by remember { mutableStateOf(HD.HOME) }
            val addWidget = eu.eurise.feature.launcher.home.rememberWidgetAdder { id ->
                val w = eu.eurise.feature.launcher.home.HomeSlot(eu.eurise.feature.launcher.home.HomeKind.AppWidget, 1f, widget = id)
                viewModel.updateSettings { it.withSlots(widgetPage, it.slotsOf(widgetPage) + w) }
            }
            sideCatalog?.let { pageId ->
                val slots = settings.slotsOf(pageId)
                val save = { next: List<eu.eurise.feature.launcher.home.HomeSlot> ->
                    viewModel.updateSettings { it.withSlots(pageId, next) }
                }
                eu.eurise.feature.launcher.home.HomeCatalog(
                    onWidget = { info -> sideCatalog = null; widgetPage = pageId; addWidget(info) },
                    slots = slots,
                    onAdd = { kind ->
                        save(slots + eu.eurise.feature.launcher.home.HomeSlot(kind, kind.defaultWidth))
                        if (kind == eu.eurise.feature.launcher.home.HomeKind.Videos) viewModel.refreshVideos()
                    },
                    onRemove = { kind -> save(slots.filterNot { s -> s.kind == kind }) },
                    onClose = { sideCatalog = null },
                )
            }
            // Pagine della Scrivania (tavole ScrPagGestisci, ScrPagNuova, ScrPagAggiungi).
            BackHandler(enabled = pagesSheet || newPageFor != null || pagePicker != null) {
                when {
                    newPageFor != null -> newPageFor = null
                    pagePicker != null -> pagePicker = null
                    else -> pagesSheet = false
                }
            }
            val pageAdded = stringResource(R.string.pages_added)
            if (pagesSheet) {
                eu.eurise.feature.launcher.home.PagesSheet(
                    pages = settings.pages,
                    startPage = settings.startPage,
                    countOf = { p -> settings.slotsOf(p.id).size },
                    onPages = { list -> viewModel.updateSettings { it.copy(pages = list) } },
                    onStart = { id -> viewModel.updateSettings { it.copy(startPage = id) } },
                    onOpen = { p -> pagesSheet = false; goToPage = p.id },
                    onDuplicate = { p ->
                        viewModel.createPage(p.name, p.symbol.ifBlank { HP.symbolFor(p) }, p.mode, eu.eurise.feature.launcher.home.PageStart.Copy, copyOf = p.id, after = p.id)
                    },
                    onDelete = { p ->
                        // I widget della pagina liberano il loro numero presso Android.
                        settings.slotsOf(p.id).filter { it.kind == eu.eurise.feature.launcher.home.HomeKind.AppWidget }
                            .forEach { eu.eurise.feature.launcher.home.AppWidgets.delete(context, it.widget) }
                        viewModel.deletePage(p.id)
                    },
                    onNew = { newPageFor = "" },
                    onClose = { pagesSheet = false },
                )
            }
            newPageFor?.let { appKey ->
                eu.eurise.feature.launcher.home.PageDialog(
                    title = stringResource(R.string.pages_new),
                    initial = null,
                    forNew = true,
                    copyChoices = settings.pages,
                    onDismiss = { newPageFor = null },
                    onCreate = { name, symbol, mode, start, copyOf, startHere ->
                        val after = deskPages.getOrNull(homePager.currentPage)?.id
                        val id = viewModel.createPage(name, symbol, mode, start, copyOf, startHere, appKey.ifBlank { null }, after)
                        newPageFor = null
                        pagesSheet = false
                        pagePicker = null
                        goToPage = id
                    },
                )
            }
            pagePicker?.let { app ->
                eu.eurise.feature.launcher.home.PagePickerSheet(
                    appLabel = app.label,
                    pages = settings.pages,
                    addPage = settings.addPage,
                    onPick = { p ->
                        viewModel.addToPage(app.key, p.id)
                        pagePicker = null
                        android.widget.Toast.makeText(context, pageAdded.format(app.label), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onStar = { p -> viewModel.updateSettings { it.copy(addPage = p.id) } },
                    onNew = { newPageFor = app.key },
                    onClose = { pagePicker = null },
                )
            }
            if (homeCatalog) {
                eu.eurise.feature.launcher.home.HomeCatalog(
                    onWidget = { info -> homeCatalog = false; widgetPage = HD.HOME; addWidget(info) },
                    slots = settings.homeSlots,
                    onAdd = { kind ->
                        viewModel.updateSettings { it.copy(homeSlots = it.homeSlots + eu.eurise.feature.launcher.home.HomeSlot(kind, kind.defaultWidth)) }
                        if (kind == eu.eurise.feature.launcher.home.HomeKind.Videos) viewModel.refreshVideos()
                    },
                    onRemove = { kind -> viewModel.updateSettings { it.copy(homeSlots = it.homeSlots.filterNot { s -> s.kind == kind }) } },
                    onClose = { homeCatalog = false },
                )
            }
            if (channelsSheet) {
                eu.eurise.feature.launcher.videos.ChannelsSheet(
                    viewModel.videos,
                    onImport = { newPipeImport.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                    onClose = { channelsSheet = false; viewModel.refreshVideos(force = true) },
                )
            }
            if (appearance) {
                AppearanceSheet(
                    settings, scene.themeDark, onChange = viewModel::updateSettings, onClose = { appearance = false },
                    onEditCategories = { appearance = false; categoryEditor = true },
                    onAod = { appearance = false; aodSettings = true },
                    onSounds = { appearance = false; soundsScreen = true },
                    onLockWallpaper = { appearance = false; lockWallpaperScreen = true },
                )
            }
            if (aodSettings) eu.eurise.feature.launcher.aod.AodSettingsScreen(onClose = { aodSettings = false })
            if (soundsScreen) eu.eurise.feature.launcher.sounds.SoundsScreen(onClose = { soundsScreen = false })
            if (lockWallpaperScreen) eu.eurise.feature.launcher.home.LockWallpaperScreen(scene, onClose = { lockWallpaperScreen = false })
            // Risposta rapida: sopra a tutto, dal Pannello o dalla bolla "Ti hanno cercato".
            replyTo?.let { m ->
                eu.eurise.feature.launcher.notifications.QuickReplyDialog(
                    message = m,
                    onOpenChat = { replyTo = null; panelOpen = false; actions.openMessage(it) },
                    onClose = { replyTo = null },
                    onTask = bubbleActions.addTask,
                    onNote = viewModel::addNote,
                    availability = { viewModel.availabilityText() },
                )
            }
            dockPanel?.let { panel ->
                val dockBottom = clearance + 4.dp
                val close = { dockPanel = null }
                when (panel) {
                    eu.eurise.feature.launcher.home.DockPanel.All -> eu.eurise.feature.launcher.home.AllAppsPanel(
                        categories = barCategories,
                        start = dockPrefs.mostOpened(barCategories),
                        appsOf = { c -> eu.eurise.feature.launcher.home.categoryApps(c, state.apps, state.favorites, categoryOf, settings.appOrder[c.id]) },
                        badges = badges, dark = scene.themeDark, leftHanded = settings.leftHanded, hazeState = hazeState, bottom = dockBottom,
                        onOpen = { app, c -> dockPrefs.countOpen(c.id); close(); actions.openApp(app) },
                        onHold = { app -> pagePicker = app },
                        onPage = { c -> dockPrefs.countOpen(c.id); close(); select(c) },
                        onAddCategory = if (barCategories.size < eu.eurise.feature.launcher.apps.MAX_CATEGORIES) ({ close(); categoryEditor = true }) else null,
                        onClose = close,
                    )
                    eu.eurise.feature.launcher.home.DockPanel.Star -> eu.eurise.feature.launcher.home.StarPanel(
                        state.favorites, badges, scene.themeDark, hazeState, dockBottom,
                        onOpen = { close(); actions.openApp(it) },
                        onAdd = { close(); barCategories.firstOrNull { it.isAll }?.let(::select) ?: run { dockPanel = eu.eurise.feature.launcher.home.DockPanel.All } },
                        onClose = close,
                    )
                    is eu.eurise.feature.launcher.home.DockPanel.Role -> {
                        val candidates = dockRoles[panel.role].orEmpty()
                        eu.eurise.feature.launcher.home.RolePanel(
                            panel.role, candidates, eu.eurise.feature.launcher.home.dockApp(dockPrefs, panel.role, candidates), badges, scene.themeDark, hazeState, dockBottom,
                            onOpen = { close(); actions.openApp(it) },
                            onStar = { dockPrefs.setRef(panel.role, it.key) },
                            onClose = close,
                        )
                    }
                    eu.eurise.feature.launcher.home.DockPanel.Comms -> eu.eurise.feature.launcher.home.CommsAppsPanel(
                        remember(state.apps) { eu.eurise.feature.launcher.home.commsApps(context, state.apps) }, badges, scene.themeDark, hazeState, dockBottom,
                        onOpen = { close(); actions.openApp(it) }, onClose = close,
                    )
                }
            }
        }
        }
      }
      }
      }
      }
    }
}

package eu.eurise.feature.launcher

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
import eu.eurise.feature.launcher.home.openWebSearch
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
import eu.eurise.feature.launcher.summary.SummaryBoard
import eu.eurise.feature.launcher.summary.WeatherCard
import android.annotation.SuppressLint
import android.provider.AlarmClock
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import eu.eurise.feature.launcher.apps.AppEntry
import eu.eurise.feature.launcher.apps.AppIcon
import eu.eurise.feature.launcher.apps.BarCategory
import eu.eurise.feature.launcher.apps.cameraChoices
import eu.eurise.feature.launcher.apps.galleryChoices
import eu.eurise.feature.launcher.apps.suiteGallery
import eu.eurise.feature.launcher.apps.LocalCategoryTint
import eu.eurise.feature.launcher.apps.resolveCategory
import eu.eurise.feature.launcher.settings.CategoryEditor
import eu.eurise.feature.launcher.apps.launch
import eu.eurise.feature.launcher.apps.openEurise
import eu.eurise.feature.launcher.home.AppTile
import eu.eurise.feature.launcher.home.CaptureActions
import eu.eurise.feature.launcher.home.CategoryBar
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

/** Le categorie della barra; Preferiti riporta alla home. */

/** Lato corto da cui il dispositivo è un tablet: un telefono in orizzontale resta un telefono. */
private const val TabletSmallestWidth = 600

/**
 * La Scrivania di Eurise: home con orario, meteo, Oggi e Preferiti; pagina Riepilogo; pagine delle categorie;
 * ricerca e cattura. I colori seguono lo sfondo: tema Notte sopra le scene di sera e di notte.
 *
 * @param homePresses aumenta ogni volta che si preme il tasto Home mentre la Scrivania è già aperta.
 */
@Composable
fun LauncherRoute(data: EuriseData, homePresses: Int) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel = viewModel { LauncherViewModel(app, data.notes, data.tasks, data.calendar, data.links) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val badges by NotificationStore.counts.collectAsState()
    val messages by NotificationStore.messages.collectAsState()
    val nowPlaying by NotificationStore.nowPlaying.collectAsState()
    val allNotifications by NotificationStore.all.collectAsState()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val newsItems by viewModel.newsItems.collectAsStateWithLifecycle()
    val quotes by viewModel.quotes.collectAsStateWithLifecycle()
    val horoscope by viewModel.horoscope.collectAsStateWithLifecycle()
    val horoscopeLoaded by viewModel.horoscopeLoaded.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val sign by viewModel.sign.collectAsStateWithLifecycle()
    val signName = stringResource(sign.label)
    LaunchedEffect(signName) { viewModel.refreshHoroscope(signName); viewModel.refreshRecipes() }
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
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
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${entry.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
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
    EuriseTheme(darkTheme = scene.dark) {
      // Di notte l'accento si abbassa e si intona allo sfondo: meno acceso, più scuro.
      val accent = settings.accent.color(scene.dark).let { if (scene.dark) nightAccent(it) else it }
      val onAccent = if (0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue > 0.55f) Color(0xFF14171C) else Color.White
      MaterialTheme(
          colorScheme = MaterialTheme.colorScheme.copy(
              primary = accent,
              onPrimary = onAccent,
              // Binari di cursori e interruttori spenti: grigi neutri, non la salvia del tema.
              secondaryContainer = if (scene.dark) Color(0xFF2E343E) else Color(0xFFE2E2DC),
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
          LocalCategoryTint provides { entry -> resolveCategory(entry, settings.categories, settings.categoryOverrides)?.tint },
          // Sulla Scrivania le bolle si staccano appena un po' di più dallo sfondo.
          eu.eurise.core.designsystem.theme.LocalEuriseColors provides launcherGlass(eu.eurise.core.designsystem.theme.EuriseThemeExtras.colors),
      ) {
        val hazeState = remember { HazeState() }
        var weatherSheet by remember { mutableStateOf(false) }
        val openWeather: () -> Unit = {
            val app = state.apps.firstOrNull { a ->
                a.euriseSection == null && (a.packageName.contains("weather", true) || a.label.contains("meteo", true) || a.label.contains("weather", true))
            }
            when {
                app != null -> actions.openApp(app)
                weather != null -> { weatherSheet = true }
                else -> actions.askLocation()
            }
        }
        // Categoria scelta nella barra, per id; HomePageId è la home. I Preferiti sono una categoria come le altre.
        var category by remember { mutableStateOf(HomePageId) }
        var categoryEditor by remember { mutableStateOf(false) }
        val barCategories = settings.categories
        val pageCategories = barCategories
        if (category != HomePageId && pageCategories.none { it.id == category }) category = HomePageId
        val categoryOf: (AppEntry) -> BarCategory? = { entry -> resolveCategory(entry, barCategories, settings.categoryOverrides) }
        var searching by remember { mutableStateOf(false) }
        var panelOpen by remember { mutableStateOf(false) }
        var appearance by remember { mutableStateOf(false) }
        var aodSettings by remember { mutableStateOf(false) }
        var editingCards by remember { mutableStateOf(false) }
        var editingHome by remember { mutableStateOf(false) }
        var homeCatalog by remember { mutableStateOf(false) }
        var channelsSheet by remember { mutableStateOf(false) }
        var replyTo by remember { mutableStateOf<MessageItem?>(null) }
        // Elenco dei contatti aperto dalla pillola Comunicazioni: "recent" o "starred".
        var peopleSheet by remember { mutableStateOf<String?>(null) }
        var searchPerson by remember { mutableStateOf<eu.eurise.feature.launcher.contacts.Person?>(null) }
        var signPicker by remember { mutableStateOf(false) }
        // Cartella aperta (id) e scelta delle app per crearne una o aggiungerne.
        var openFolder by remember { mutableStateOf<String?>(null) }
        var folderPicker by remember { mutableStateOf<FolderRequest?>(null) }
        // Fonti di oroscopo o ricette in modifica: "horoscope" o "recipes".
        var dailySheet by remember { mutableStateOf<String?>(null) }
        val peopleShortcuts = remember { eu.eurise.feature.launcher.home.PeopleShortcuts({ peopleSheet = "recent" }, { peopleSheet = "starred" }) }
        val welcome = WelcomeData(
            travel = travel,
            destination = destination,
            destinations = destinations,
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
            onScan = {
                // Lo scanner della suite non c'è ancora: si apre un'app di scansione installata, altrimenti la fotocamera.
                val scanner = state.apps.firstOrNull { a ->
                    a.euriseSection == null && (a.label.contains("scan", ignoreCase = true) || a.packageName.contains("scanner", ignoreCase = true) ||
                        a.packageName.startsWith("de.schliweb.makeacopy"))
                }
                if (scanner != null) actions.openApp(scanner)
                else runCatching { context.startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            onPlayer = {
                val pkg = nowPlaying?.controller?.packageName
                val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
                    ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
                runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
        )
        val noteSaved = stringResource(R.string.bubble_saved_note)
        val taskSaved = stringResource(R.string.bubble_saved_task)
        val bubbleActions = eu.eurise.feature.launcher.home.BubbleActions(
            setDone = { viewModel.setTaskDone(it, true) },
            postpone = viewModel::postponeTask,
            quickTask = viewModel::quickTask,
            openTasks = { actions.openEurise("tasks") },
            openMedicines = { actions.openEurise("medicines") },
            openDay = { actions.openEurise("day:$it") },
            navigate = welcome.onRoute,
            meetingNote = { e -> viewModel.meetingNote(e) { id -> actions.openEurise("note:$id") } },
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
        )
        // Tre pagine: Notizie e mercati a sinistra, Home al centro, Riepilogo a destra.
        val homePager = rememberPagerState(initialPage = HomePage) { 3 }
        var newsSheet by remember { mutableStateOf(false) }
        var stocksSheet by remember { mutableStateOf(false) }
        val categoryPager = rememberPagerState { pageCategories.size }
        val scope = rememberCoroutineScope()

        LaunchedEffect(homePresses) {
            if (homePresses > 0) {
                if (category == HomePageId && !searching) homePager.animateScrollToPage(HomePage)
                category = HomePageId
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
        BackHandler(enabled = replyTo != null || homeCatalog || channelsSheet || editingHome || folderPicker != null || openFolder != null || signPicker || dailySheet != null || peopleSheet != null || weatherSheet || searching || panelOpen || appearance || editingCards || newsSheet || stocksSheet || categoryEditor || category != HomePageId || homePager.currentPage != HomePage) {
            when {
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
                else -> scope.launch { homePager.animateScrollToPage(HomePage) }
            }
        }

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
        val capture = CaptureActions(
            onTask = viewModel::addTask,
            onNote = viewModel::addNote,
            onEvent = { actions.openEurise("new_event") },
        )
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val clearance = GlassDefaults.PillHeight + GlassDefaults.BottomMargin * 2 + navBottom

        Box(Modifier.fillMaxSize()) {
            SceneBackground(scene, Modifier.fillMaxSize().hazeSource(hazeState))

            // Da ogni pagina: in su ricerca e app, in giù il Pannello Eurise (non mentre si modifica o c'è un foglio aperto).
            BoxWithConstraints(
                Modifier.fillMaxSize().then(
                    if (searching || panelOpen || editingCards || editingHome || categoryEditor || appearance || replyTo != null) Modifier
                    else Modifier.openOnSwipe(onUp = { searching = true }, onDown = { panelOpen = true }),
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
                                        cardBody = { kind, modifier ->
                                            androidx.compose.runtime.CompositionLocalProvider(eu.eurise.feature.launcher.notifications.LocalQuickReply provides { replyTo = it }, eu.eurise.feature.launcher.home.LocalPeopleShortcuts provides peopleShortcuts, eu.eurise.feature.launcher.home.LocalBubbleActions provides bubbleActions) {
                                            SummaryCardBody(
                                                PlacedCard(kind, eu.eurise.feature.launcher.summary.GRID_COLS, 22), modifier, state, messages, nowPlaying, notificationsOn,
                                                hazeState, actions, weather, newsItems, quotes, { newsSheet = true }, { stocksSheet = true },
                                                DailyData(
                                                    horoscope = horoscope, horoscopeLoaded = horoscopeLoaded, sign = sign, dark = scene.dark, recipes = recipes,
                                                    onSign = { signPicker = true }, onHoroscopeSources = { dailySheet = "horoscope" }, onRecipeSources = { dailySheet = "recipes" },
                                                ),
                                                calendarMonth, monthEvents, monthTasks, viewModel::setCalendarMonth,
                                            )
                                            }
                                        },
                                    )
                val board: @Composable (Boolean, Modifier) -> Unit = { left, modifier ->
                    androidx.compose.runtime.CompositionLocalProvider(eu.eurise.feature.launcher.notifications.LocalQuickReply provides { replyTo = it }, eu.eurise.feature.launcher.home.LocalPeopleShortcuts provides peopleShortcuts, eu.eurise.feature.launcher.home.LocalBubbleActions provides bubbleActions) {
                    androidx.compose.runtime.CompositionLocalProvider(eu.eurise.feature.launcher.home.LocalBubbleActions provides bubbleActions) {
                    val sideAlarm = rememberNextAlarm()
                    SummaryColumn(
                        bubble = { slot, mod -> HomeBubble(slot, mod, now, state, welcome, hazeState, false, actions, homeData, sideAlarm) },
                        state = state, messages = messages, nowPlaying = nowPlaying, notificationsOn = notificationsOn, setup = if (left) emptyList() else setup, hazeState = hazeState, actions = actions, clearance = clearance, modifier = modifier,
                        weather = weather,
                        cards = if (left) settings.leftCards else settings.cards,
                        news = newsItems, quotes = quotes,
                        onNewsSources = { newsSheet = true }, onSymbols = { stocksSheet = true },
                        daily = DailyData(
                            horoscope = horoscope, horoscopeLoaded = horoscopeLoaded, sign = sign, dark = scene.dark, recipes = recipes,
                            onSign = { signPicker = true }, onHoroscopeSources = { dailySheet = "horoscope" }, onRecipeSources = { dailySheet = "recipes" },
                        ),
                        calendarMonth = calendarMonth, monthEvents = monthEvents, monthTasks = monthTasks,
                        onCalendarMonth = viewModel::setCalendarMonth,
                        editing = editingCards, onEditingChange = { editingCards = it },
                        onCardsChange = { cards -> viewModel.updateSettings { if (left) it.copy(leftCards = cards) else it.copy(cards = cards) } },
                    )
                    }
                    }
                }
                val summary: @Composable (Modifier) -> Unit = { modifier -> board(false, modifier) }

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
                        HorizontalPager(homePager, Modifier.fillMaxSize(), userScrollEnabled = !editingCards && !editingHome) { page ->
                            if (page == 0) {
                                board(true, Modifier.fillMaxSize())
                            } else if (page == HomePage) {
                                PhoneHome(
                                    now = now, weather = weather, hasLocation = hasLocation, state = state, badges = badges,
                                    dark = scene.dark, hazeState = hazeState, actions = actions,
                                    isFavorite = viewModel::isFavorite, onToggleFavorite = viewModel::toggleFavorite,
                                    onTaskDone = { viewModel.setTaskDone(it, true) }, clearance = clearance,
                                    isHome = isHome,
                                    onWeather = openWeather,
                                    onSearch = { searching = true },
                                    onPanel = { panelOpen = true },
                                    onAppearance = { appearance = true },
                                    settings = settings,
                                    onMoveFavorite = viewModel::moveFavorite,
                                    onQuickApp = { gallery, key -> viewModel.updateSettings { if (gallery) it.copy(quickGallery = key) else it.copy(quickCamera = key) } },
                                    welcome = welcome,
                                    onOpenFolder = { openFolder = it.id },
                                    onCreateFolder = { app -> folderPicker = FolderRequest(null, eu.eurise.feature.launcher.folders.AppFolder.HOME, app.key) },
                                    skyLight = scene.skyLight,
                                    home = homeData,
                                )
                            } else {
                                summary(Modifier.fillMaxSize())
                            }
                        }
                        if (!editingCards && !editingHome) PageDots(homePager.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = clearance - 6.dp))
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
                        dark = scene.dark,
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
                        favorites = state.favorites.take(settings.columns), badges = badges, dark = scene.dark,
                        categories = barCategories, selected = category,
                        onOpen = actions.openApp, onSelect = ::select, onSearch = { searching = true }, onEdit = { categoryEditor = true },
                        hazeState = hazeState, modifier = barModifier.widthIn(max = 960.dp),
                    )
                } else {
                    CategoryBar(
                        categories = barCategories, selected = category, onSelect = ::select, onSearch = { context.openWebSearch() },
                        hazeState = hazeState, modifier = barModifier.fillMaxWidth(), onEdit = { categoryEditor = true },
                    )
                }
            }

            if (searching) {
                SearchOverlay(
                    columns = settings.columns,
                    iconSize = settings.iconSize.dp,
                    isFavorite = viewModel::isFavorite,
                    onToggleFavorite = viewModel::toggleFavorite,
                    apps = state.apps,
                    recents = state.recents,
                    notes = state.notes,
                    tasks = state.openTasks,
                    dark = scene.dark,
                    hazeState = hazeState,
                    capture = capture,
                    onOpenApp = { searching = false; actions.openApp(it) },
                    onOpenNote = { searching = false; actions.openEurise("note:${it.id}") },
                    onOpenTask = { searching = false; actions.openEurise("tasks") },
                    onClose = { searching = false },
                    conversations = messages,
                    searchContacts = { viewModel.contacts.search(it) },
                    searchFiles = { viewModel.files.search(it) },
                    onOpenContact = { searchPerson = it; peopleSheet = "search" },
                    onOpenMessage = { searching = false; actions.openMessage(it) },
                    onOpenFile = { searching = false; viewModel.files.open(it) },
                )
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
                )
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
                    dark = scene.dark,
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
                    dark = scene.dark,
                    onDone = { chosen ->
                        folderPicker = null
                        if (existing != null) viewModel.setFolderApps(existing, chosen.map { it.key })
                        else viewModel.createFolder(chosen, req.place, context.getString(R.string.folder_default_name))
                    },
                    onClose = { folderPicker = null },
                )
            }
            if (signPicker) {
                SignPicker(sign, scene.dark, onPick = { picked ->
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
                    onOpenContact = { peopleSheet = null; viewModel.contacts.openContact(it) },
                    onClose = { peopleSheet = null },
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
            if (homeCatalog) {
                eu.eurise.feature.launcher.home.HomeCatalog(
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
                    settings, scene.dark, onChange = viewModel::updateSettings, onClose = { appearance = false },
                    onEditCategories = { appearance = false; categoryEditor = true },
                    onAod = { appearance = false; aodSettings = true },
                )
            }
            if (aodSettings) eu.eurise.feature.launcher.aod.AodSettingsScreen(onClose = { aodSettings = false })
            // Risposta rapida: sopra a tutto, dal Pannello o dalla bolla "Ti hanno cercato".
            replyTo?.let { m ->
                eu.eurise.feature.launcher.notifications.QuickReplyDialog(
                    message = m,
                    onOpenChat = { replyTo = null; panelOpen = false; actions.openMessage(it) },
                    onClose = { replyTo = null },
                    onTask = bubbleActions.addTask,
                )
            }
        }
      }
      }
      }
      }
    }
}

/** Richiesta di scelta delle app: per una cartella nuova (con l'app di partenza) o per una esistente. */
private data class FolderRequest(val folderId: String?, val place: String, val startKey: String?)

/** Oroscopo e ricette per le schede delle pagine laterali. */
private class DailyData(
    val horoscope: eu.eurise.feature.launcher.feeds.FeedEntry?,
    val horoscopeLoaded: Boolean,
    val sign: eu.eurise.feature.launcher.feeds.Sign,
    val dark: Boolean,
    val recipes: List<eu.eurise.feature.launcher.feeds.FeedEntry>?,
    val onSign: () -> Unit,
    val onHoroscopeSources: () -> Unit,
    val onRecipeSources: () -> Unit,
)

/** Dati e azioni dell'accoglienza della home. */
private class WelcomeData(
    val travel: eu.eurise.feature.launcher.route.Travel?,
    val destination: String?,
    val destinations: List<eu.eurise.core.data.calendar.CalendarEvent>,
    val messages: List<MessageItem>,
    val notificationsOn: Boolean,
    val nowPlaying: eu.eurise.feature.launcher.notifications.NowPlaying?,
    val files: List<eu.eurise.feature.launcher.files.RecentFile>,
    val filesAccess: Boolean,
    val onRoute: (String) -> Unit,
    val onRecent: () -> Unit,
    val onStarred: () -> Unit,
    val onOpenFile: (eu.eurise.feature.launcher.files.RecentFile) -> Unit,
    val onFilesGrant: () -> Unit,
    val onPlayer: () -> Unit,
    val onScan: () -> Unit,
    val sun: Pair<java.time.LocalTime, java.time.LocalTime>?,
    val healthState: eu.eurise.feature.launcher.health.HealthState,
    val healthToday: eu.eurise.feature.launcher.health.HealthToday?,
    val onHealthGrant: () -> Unit,
    val onGadgetbridge: () -> Unit,
    val hazeState: HazeState,
)


/** Ciò che serve alla home per le sue schede modificabili e per le bolle nuove (video, risposta rapida). */
private class HomeBoardData(
    val editing: Boolean,
    val onEditing: (Boolean) -> Unit,
    val onChange: (List<eu.eurise.feature.launcher.home.HomeSlot>) -> Unit,
    val onCatalog: () -> Unit,
    val videos: List<eu.eurise.feature.launcher.videos.Video>?,
    val hasChannels: Boolean,
    val onOpenVideo: (eu.eurise.feature.launcher.videos.Video) -> Unit,
    val onChannels: () -> Unit,
    val onReply: (MessageItem) -> Unit,
    val cardBody: @Composable (CardKind, Modifier) -> Unit,
    val bubbles: eu.eurise.feature.launcher.home.BubbleActions,
    val notifications: List<MessageItem>,
    val readCodes: Boolean,
)

/** Il contenuto di una bolla: sulla home o, come scheda, in una pagina laterale. */
@Composable
private fun HomeBubble(
    slot: eu.eurise.feature.launcher.home.HomeSlot,
    mod: Modifier,
    now: LocalDateTime,
    state: LauncherState,
    welcome: WelcomeData,
    hazeState: HazeState,
    compact: Boolean,
    actions: LauncherActions,
    home: HomeBoardData,
    nextAlarm: LocalDateTime?,
) {
        when (slot.kind) {
            eu.eurise.feature.launcher.home.HomeKind.Agenda -> AgendaPill(
                now = now,
                events = state.eventsToday,
                calendarConnected = state.calendarConnected,
                tasks = state.tasksDue,
                travel = welcome.travel,
                destinations = welcome.destinations,
                hazeState = hazeState,
                compact = compact,
                onCalendar = { actions.openEurise("calendar") },
                onConnectCalendar = actions.connectCalendar,
                onTasks = { actions.openEurise("tasks") },
                onRoute = welcome.onRoute,
                modifier = mod,
            )
            eu.eurise.feature.launcher.home.HomeKind.Comms -> CommsPill(
                messages = welcome.messages,
                enabled = welcome.notificationsOn,
                hazeState = hazeState,
                compact = compact,
                onOpen = actions.openMessage,
                onRecent = welcome.onRecent,
                onStarred = welcome.onStarred,
                onGrant = actions.grantNotifications,
                onAppInfo = actions.ownAppInfo,
                onHub = { actions.openEurise("today") },
                onBubble = home.onReply,
                modifier = mod,
            )
            eu.eurise.feature.launcher.home.HomeKind.Health ->
                HealthPill(welcome.healthState, welcome.healthToday, hazeState, welcome.onHealthGrant, welcome.onGadgetbridge, mod)
            eu.eurise.feature.launcher.home.HomeKind.Work ->
                WorkPill(welcome.files, welcome.filesAccess, hazeState, mod, welcome.onOpenFile, welcome.onFilesGrant, welcome.onScan)
            eu.eurise.feature.launcher.home.HomeKind.Music ->
                MusicHalf(welcome.nowPlaying, welcome.notificationsOn, hazeState, mod, welcome.onPlayer, actions.grantNotifications)
            eu.eurise.feature.launcher.home.HomeKind.TodayTasks -> eu.eurise.feature.launcher.home.TodayTasksPill(
                state.tasksDue, state.today, compact, hazeState, home.bubbles, mod,
            )
            eu.eurise.feature.launcher.home.HomeKind.NextEvent -> eu.eurise.feature.launcher.home.NextEventPill(
                state.upcoming, state.calendarConnected, welcome.travel, now, hazeState, home.bubbles, actions.connectCalendar, mod,
            )
            eu.eurise.feature.launcher.home.HomeKind.Codes -> eu.eurise.feature.launcher.home.CodesPill(
                home.notifications, welcome.notificationsOn, home.readCodes, now, compact, hazeState, home.bubbles, mod,
            )
            eu.eurise.feature.launcher.home.HomeKind.Meds -> eu.eurise.feature.launcher.home.MedsPill(
                state.tasksDue, state.today, compact, hazeState, home.bubbles, mod,
            )
            eu.eurise.feature.launcher.home.HomeKind.Clock -> eu.eurise.feature.launcher.home.ClockPill(
                home.notifications, nextAlarm, now, hazeState, home.bubbles, mod,
            )
            eu.eurise.feature.launcher.home.HomeKind.Memo -> eu.eurise.feature.launcher.home.QuickNotePill(
                state.notes.maxByOrNull { it.updatedAt }, hazeState, home.bubbles, mod,
            )
            eu.eurise.feature.launcher.home.HomeKind.Videos -> eu.eurise.feature.launcher.videos.VideosPill(
                home.videos, home.hasChannels, hazeState, mod, home.onOpenVideo, home.onChannels,
            )
            else -> slot.kind.card?.let { kind ->
                // Le schede delle altre pagine hanno bisogno di un'altezza: se manca, quella di partenza.
                val sized = if (slot.height > 0) mod else mod.height(slot.kind.defaultHeight.coerceAtLeast(120).dp)
                home.cardBody(kind, sized)
            }
        }
}

/** Le azioni che escono dalla Scrivania (aprire app, impostazioni, permessi). */
private class LauncherActions(
    val openApp: (AppEntry) -> Unit,
    val openLink: (String) -> Unit,
    val openClock: () -> Unit,
    val ownAppInfo: () -> Unit,
    val expandNotifications: () -> Unit,
    val openEurise: (String) -> Unit,
    val appInfo: (AppEntry) -> Unit,
    val openMessage: (MessageItem) -> Unit,
    val grantNotifications: () -> Unit,
    val connectCalendar: () -> Unit,
    val askLocation: () -> Unit,
    val becomeHome: () -> Unit,
)

@Composable
private fun PhoneHome(
    now: LocalDateTime,
    weather: List<eu.eurise.feature.launcher.weather.DayForecast>?,
    hasLocation: Boolean,
    state: LauncherState,
    badges: Map<String, Int>,
    dark: Boolean,
    hazeState: HazeState,
    actions: LauncherActions,
    isFavorite: (AppEntry) -> Boolean,
    onToggleFavorite: (AppEntry) -> Unit,
    onTaskDone: (eu.eurise.core.data.model.Task) -> Unit,
    clearance: Dp,
    isHome: Boolean,
    onWeather: () -> Unit,
    onSearch: () -> Unit,
    onPanel: () -> Unit,
    onAppearance: () -> Unit,
    settings: LauncherSettings,
    onMoveFavorite: (AppEntry, Int) -> Unit,
    welcome: WelcomeData,
    onOpenFolder: (eu.eurise.feature.launcher.folders.AppFolder) -> Unit,
    onCreateFolder: (AppEntry) -> Unit,
    home: HomeBoardData,
    onQuickApp: (Boolean, String) -> Unit,
    skyLight: Boolean,
) {
    // Con più righe di preferiti, o su schermi bassi, le pillole mostrano una riga in meno.
    val compact = settings.rows >= 3 || (settings.rows == 2 && LocalConfiguration.current.screenHeightDp < 820)
    val nextAlarm = rememberNextAlarm()
    // Sullo sfondo libero: in su ricerca e recenti, in giù il Pannello Eurise, pressione lunga Griglia e icone.
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onAppearance() }) },
        )
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
        ) {
            if (!isHome) {
                Spacer(Modifier.height(12.dp))
                HomeBanner(hazeState, actions.becomeHome)
                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.height(16.dp))
            }
            // Accoglienza: saluto, orario, data e meteo, poi le pillole. Sul cielo ancora chiaro il testo prende
            // il colore delle pillole; quando il cielo si scurisce passa al chiaro.
            val headerColor = if (skyLight) (if (dark) Color(0xFF1B1F27) else Color(0xFF1E1D1B)) else Color(0xFFF1EEE8)
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    onBackground = headerColor, onSurface = headerColor, onSurfaceVariant = headerColor.copy(alpha = 0.82f),
                ),
            ) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides headerColor) {
            Column {
            androidx.compose.material3.Text(
                greeting(now, settings.userName),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            val context = LocalContext.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockBlock(
                    now, onClock = actions.openClock, onDate = { actions.openEurise("calendar") }, large = false, modifier = Modifier.weight(1f),
                    beside = { SunTimesColumn(welcome.sun, alarm = nextAlarm, now = now) },
                )
                // Accanto all'orario: galleria sopra, fotocamera sotto.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickIcon(
                        eu.eurise.core.designsystem.icon.EuriseIcons.image(2f), stringResource(R.string.quick_gallery), dark,
                        chosen = settings.quickGallery,
                        choices = { context.galleryChoices(state.apps) },
                        onChoose = { key -> onQuickApp(true, key) },
                    ) {
                        val app = state.apps.firstOrNull { it.key == settings.quickGallery } ?: state.apps.suiteGallery()
                        if (app != null) actions.openApp(app) else runCatching {
                            context.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    QuickIcon(
                        eu.eurise.core.designsystem.icon.EuriseIcons.camera(2f), stringResource(R.string.quick_camera), dark,
                        chosen = settings.quickCamera,
                        choices = { context.cameraChoices(state.apps) },
                        onChoose = { key -> onQuickApp(false, key) },
                    ) {
                        val app = state.apps.firstOrNull { it.key == settings.quickCamera }
                        if (app != null) actions.openApp(app)
                        else runCatching { context.startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            WeatherRow(weather, canAsk = !hasLocation, onAsk = actions.askLocation, onOpen = onWeather)
            }
            }
            }
            Spacer(Modifier.height(12.dp))
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            val imeOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible
            // Le bolle prendono lo spazio libero sopra i preferiti; se non basta, scorrono loro. I preferiti
            // restano ancorati in basso anche quando una pillola si riduce.
            Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                androidx.compose.runtime.CompositionLocalProvider(eu.eurise.feature.launcher.home.LocalBubbleActions provides home.bubbles) {
                eu.eurise.feature.launcher.home.HomeBoard(
                    slots = settings.homeSlots,
                    editing = home.editing,
                    onEditingChange = home.onEditing,
                    onChange = home.onChange,
                    onCatalog = home.onCatalog,
                ) { slot, mod ->
                    HomeBubble(slot, mod, now, state, welcome, hazeState, compact, actions, home, nextAlarm)
                }
                }
            }
            }
            Spacer(Modifier.height(6.dp))
            if (state.favorites.isEmpty() && !imeOpen) {
                androidx.compose.material3.Text(
                    stringResource(R.string.favorites_empty),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            } else if (!imeOpen) {
                // Griglia dei preferiti: colonne e righe scelte in Griglia e icone. Con la tastiera aperta si
                // toglie, per non restare appoggiata sopra i tasti e lasciare spazio alla bolla in cui si scrive.
                val shown = state.favorites.take(settings.maxFavorites)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    shown.chunked(settings.columns).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { entry ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    val folder = settings.folders.firstOrNull { it.key == entry.key }
                                    if (folder != null) {
                                        val byKey = state.apps.associateBy { it.key }
                                        FolderTile(folder, folder.keys.mapNotNull { byKey[it] }, dark, settings.iconSize.dp, false, onOpen = { onOpenFolder(folder) })
                                    } else AppTile(
                                        entry = entry,
                                        dark = dark,
                                        badge = badges[entry.packageName] ?: 0,
                                        favorite = isFavorite(entry),
                                        onOpen = { actions.openApp(entry) },
                                        onToggleFavorite = { onToggleFavorite(entry) },
                                        onInfo = if (entry.component != null) ({ actions.appInfo(entry) }) else null,
                                        onMove = { delta -> onMoveFavorite(entry, delta) },
                                        onCreateFolder = { onCreateFolder(entry) },
                                        iconSize = settings.iconSize.dp,
                                        // Sulla home le icone vanno senza nome: più spazio per l'accoglienza.
                                        showLabel = false,
                                    )
                                }
                            }
                            repeat(settings.columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            // Poco spazio tra i preferiti e i puntini delle pagine.
            Spacer(Modifier.height((clearance + 10.dp - WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()).coerceAtLeast(8.dp)))
        }
    }
}

@Composable
private fun SummaryColumn(
    state: LauncherState,
    messages: List<MessageItem>,
    nowPlaying: eu.eurise.feature.launcher.notifications.NowPlaying?,
    notificationsOn: Boolean,
    setup: List<SetupStep>,
    hazeState: HazeState,
    actions: LauncherActions,
    clearance: Dp,
    modifier: Modifier,
    weather: List<eu.eurise.feature.launcher.weather.DayForecast>?,
    cards: List<PlacedCard>,
    news: List<eu.eurise.feature.launcher.news.NewsItem>?,
    quotes: List<eu.eurise.feature.launcher.markets.Quote>?,
    onNewsSources: () -> Unit,
    onSymbols: () -> Unit,
    daily: DailyData,
    calendarMonth: java.time.YearMonth,
    monthEvents: List<eu.eurise.core.data.calendar.CalendarEvent>,
    monthTasks: List<eu.eurise.core.data.model.Task>,
    onCalendarMonth: (java.time.YearMonth) -> Unit,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onCardsChange: (List<PlacedCard>) -> Unit,
    bubble: @Composable (eu.eurise.feature.launcher.home.HomeSlot, Modifier) -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    SummaryBoard(
        cards = cards,
        editing = editing,
        onEditingChange = onEditingChange,
        onChange = onCardsChange,
        hazeState = hazeState,
        topPadding = 20.dp,
        bottomPadding = if (editing) insets.calculateBottomPadding() + 16.dp else clearance + 8.dp,
        modifier = modifier.padding(top = insets.calculateTopPadding()).clipToBounds(),
        footer = { if (setup.isNotEmpty()) SetupCard(setup, hazeState) },
    ) { card, cardModifier ->
        val bubbleKind = card.kind.bubbleId?.let { eu.eurise.feature.launcher.home.HomeKind.byId(it) }
        if (bubbleKind != null) bubble(eu.eurise.feature.launcher.home.HomeSlot(bubbleKind, 1f), cardModifier)
        else SummaryCardBody(
            card, cardModifier, state, messages, nowPlaying, notificationsOn, hazeState, actions, weather, news, quotes,
            onNewsSources, onSymbols, daily, calendarMonth, monthEvents, monthTasks, onCalendarMonth,
        )
    }
}

/** Il contenuto di una scheda delle pagine laterali; si usa anche quando la scheda sta sulla home. */
@Composable
private fun SummaryCardBody(
    card: PlacedCard,
    cardModifier: Modifier,
    state: LauncherState,
    messages: List<MessageItem>,
    nowPlaying: eu.eurise.feature.launcher.notifications.NowPlaying?,
    notificationsOn: Boolean,
    hazeState: HazeState,
    actions: LauncherActions,
    weather: List<eu.eurise.feature.launcher.weather.DayForecast>?,
    news: List<eu.eurise.feature.launcher.news.NewsItem>?,
    quotes: List<eu.eurise.feature.launcher.markets.Quote>?,
    onNewsSources: () -> Unit,
    onSymbols: () -> Unit,
    daily: DailyData,
    calendarMonth: java.time.YearMonth,
    monthEvents: List<eu.eurise.core.data.calendar.CalendarEvent>,
    monthTasks: List<eu.eurise.core.data.model.Task>,
    onCalendarMonth: (java.time.YearMonth) -> Unit,
) {
        when (card.kind) {
            CardKind.Calendar -> CalendarCard(
                today = state.today,
                upcoming = state.upcoming,
                connected = state.calendarConnected,
                hazeState = hazeState,
                onOpen = { actions.openEurise("calendar") },
                onConnect = actions.connectCalendar,
                size = card.size,
                modifier = cardModifier,
                month = calendarMonth,
                monthEvents = monthEvents,
                monthTasks = monthTasks,
                onMonthChange = onCalendarMonth,
                onOpenDay = { day -> actions.openEurise("day:$day") },
                onOpenTasks = { actions.openEurise("tasks") },
            )
            CardKind.Messages -> MessagesCard(
                messages, notificationsOn, hazeState, actions.grantNotifications, actions.ownAppInfo,
                actions.openMessage, onHub = { actions.openEurise("today") }, size = card.size, modifier = cardModifier,
            )
            CardKind.Music -> MusicCard(nowPlaying, notificationsOn, hazeState, actions.grantNotifications, actions.ownAppInfo, card.size, cardModifier)
            CardKind.Weather -> WeatherCard(weather, hazeState, actions.askLocation, card.size, cardModifier)
            CardKind.PinnedNotes -> PinnedNotesCard(
                notes = state.notes.filter { it.pinned },
                hazeState = hazeState,
                onOpen = { actions.openEurise("note:${it.id}") },
                onOpenAll = { actions.openEurise("notes") },
                size = card.size,
                modifier = cardModifier,
            )
            CardKind.News -> NewsCard(news, hazeState, { actions.openLink(it.link) }, onNewsSources, card.size, cardModifier)
            CardKind.Stocks -> StocksCard(
                quotes, hazeState, onSymbols, size = card.size, modifier = cardModifier,
                onOpenQuote = { q -> actions.openLink("https://finance.yahoo.com/quote/" + java.net.URLEncoder.encode(q.symbol, "UTF-8")) },
            )
            CardKind.Horoscope -> HoroscopeCard(
                daily.horoscope, daily.horoscopeLoaded, daily.sign, daily.dark, hazeState, card.size, cardModifier,
                onSign = daily.onSign, onSources = daily.onHoroscopeSources, onOpen = { actions.openLink(it.link) },
            )
            CardKind.Recipe -> RecipeCard(
                daily.recipes, hazeState, card.size, cardModifier,
                onSources = daily.onRecipeSources, onOpen = { actions.openLink(it.link) },
            )
            // Le bolle messe nelle pagine laterali passano da HomeBubble.
            else -> Unit
        }
}

/** Tablet a tocco: orario, meteo e Oggi a sinistra, Riepilogo a destra. */
@Composable
private fun TabletHome(
    now: LocalDateTime,
    weather: List<eu.eurise.feature.launcher.weather.DayForecast>?,
    hasLocation: Boolean,
    state: LauncherState,
    hazeState: HazeState,
    actions: LauncherActions,
    onTaskDone: (eu.eurise.core.data.model.Task) -> Unit,
    clearance: Dp,
    summary: @Composable (Modifier) -> Unit,
    onWeather: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = clearance),
        ) {
            Spacer(Modifier.height(32.dp))
            ClockBlock(now, onClock = actions.openClock, onDate = { actions.openEurise("calendar") })
            Spacer(Modifier.height(4.dp))
            WeatherRow(weather, canAsk = !hasLocation, onAsk = actions.askLocation, onOpen = onWeather)
            Spacer(Modifier.height(16.dp))
            TodayCard(
                now = now,
                events = state.eventsToday,
                calendarConnected = state.calendarConnected,
                tasks = state.tasksDue,
                hazeState = hazeState,
                onOpen = { actions.openEurise("today") },
                onConnectCalendar = actions.connectCalendar,
                onTaskDone = onTaskDone,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }
        summary(Modifier.weight(1f).fillMaxHeight())
    }
}

/** Barra unica del tablet: preferiti | categorie | ricerca, nella stessa pillola di vetro. */
@Composable
private fun TabletDock(
    favorites: List<AppEntry>,
    badges: Map<String, Int>,
    dark: Boolean,
    categories: List<BarCategory>,
    selected: String,
    onOpen: (AppEntry) -> Unit,
    onSelect: (BarCategory) -> Unit,
    onSearch: () -> Unit,
    onEdit: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .height(GlassDefaults.PillHeight + 12.dp)
                .glass(hazeState, RoundedCornerShape(50), outline = false)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            favorites.forEach { entry ->
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onOpen(entry) },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(entry, 42.dp, dark, badges[entry.packageName] ?: 0) }
            }
            if (favorites.isNotEmpty()) DockDivider()
            Row(Modifier.weight(1f)) {
                categories.forEach { c -> CategoryButton(c, c.id == selected, { onSelect(c) }, Modifier.weight(1f), onEdit) }
            }
        }
        SearchCircle(hazeState, onSearch, Modifier)
    }
}

@Composable
private fun DockDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)))
}

/** Finché la Scrivania non è la schermata iniziale, un invito in alto a renderla tale. */
@Composable
private fun HomeBanner(hazeState: HazeState, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(hazeState, RoundedCornerShape(50), elevation = 6.dp, outline = false)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            stringResource(R.string.setup_home),
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Text(
            stringResource(R.string.setup_home_action),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

/** Pagina della home nel pager (al centro tra Notizie e Riepilogo). */
private const val HomePage = 1

/** Piccola icona toccabile accanto all'orario, grande come i simboli del meteo. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QuickIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    dark: Boolean,
    chosen: String?,
    choices: () -> List<AppEntry>,
    onChoose: (String) -> Unit,
    onClick: () -> Unit,
) {
    var options by remember { mutableStateOf<List<AppEntry>?>(null) }
    Box(
        Modifier.size(36.dp).clip(CircleShape).combinedClickable(onClickLabel = label, onClick = onClick, onLongClick = { options = choices() }),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, label, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
        androidx.compose.material3.DropdownMenu(expanded = options != null, onDismissRequest = { options = null }) {
            androidx.compose.material3.Text(
                stringResource(R.string.quick_choose), fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            options.orEmpty().forEach { app ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { androidx.compose.material3.Text(app.label, fontWeight = if (app.key == chosen) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { eu.eurise.feature.launcher.apps.AppIcon(app, 30.dp, dark) },
                    onClick = { options = null; onChoose(app.key) },
                )
            }
        }
    }
}

/** Tre puntini sopra la barra: Notizie, Home e Riepilogo. */
@Composable
private fun PageDots(current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 16.dp else 6.dp, height = 6.dp)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (i == current) 0.85f else 0.4f), CircleShape),
            )
        }
    }
}

private fun isDefaultHome(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
    }
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == context.packageName
}

/** Chiede di diventare la schermata iniziale; prima di Android 10 apre le impostazioni. */
private fun requestHomeRole(context: Context): Intent? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }
    runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    return null
}

/** Apre la conversazione della notifica; se non si può, apre l'app. */
private fun Context.openMessage(item: MessageItem) {
    val sent = item.contentIntent?.let { pi ->
        runCatching {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else null
            pi.send(this, 0, null, null, null, null, options)
        }.isSuccess
    } ?: false
    if (!sent) {
        packageManager.getLaunchIntentForPackage(item.packageName)?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}

/** Apre l'accesso alle notifiche direttamente sulla voce di Eurise, dove Android lo permette. */
private fun Context.openNotificationAccess() {
    val component = android.content.ComponentName(this, eu.eurise.feature.launcher.notifications.EuriseNotificationListener::class.java)
    val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
    } else null
    val general = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    val ok = detail != null && runCatching { startActivity(detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (!ok) runCatching { startActivity(general.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Apre la tendina delle notifiche di sistema. Il Pannello Eurise la sostituirà nella seconda fase;
 * l'API non è pubblica, quindi se il sistema la rifiuta non succede nulla.
 */
@SuppressLint("WrongConstant")
private fun Context.expandNotifications() {
    runCatching {
        val service = getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
    }
}

/** Previsioni dei prossimi giorni, quando sul telefono non c'è un'app del meteo. */
@Composable
private fun WeatherSheet(days: List<eu.eurise.feature.launcher.weather.DayForecast>, onClose: () -> Unit) {
    val format = java.time.format.DateTimeFormatter.ofPattern("EEEE d", java.util.Locale.ITALIAN)
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Label(stringResource(R.string.weather_title))
            days.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        eu.eurise.feature.launcher.home.skyIcon(d.sky), null,
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.material3.Text(
                        d.date.format(format).replaceFirstChar { it.titlecase(java.util.Locale.ITALIAN) },
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.Text("${d.max}°", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    androidx.compose.material3.Text("  ${d.min}°", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


/**
 * Vetro della Scrivania: un velo un poco più pieno di quello delle app, così le bolle si distinguono
 * dallo sfondo illustrato anche quando i colori sono chiari e simili.
 */
private fun launcherGlass(base: eu.eurise.core.designsystem.theme.EuriseExtraColors) = base.copy(
    glass = if (base.isDark) androidx.compose.ui.graphics.Color(0xE6161A21) else androidx.compose.ui.graphics.Color(0xC2FBFBF9),
)

/** Accento per il tema scuro: si avvicina al blu-grafite dello sfondo e perde un po' di brillantezza. */
private fun nightAccent(color: Color): Color = androidx.compose.ui.graphics.lerp(color, Color(0xFF2A303B), 0.32f)


/** Valore di "categoria aperta" che indica la home (nessuna categoria). */
private const val HomePageId = "@home"

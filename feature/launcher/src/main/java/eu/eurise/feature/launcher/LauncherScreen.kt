package eu.eurise.feature.launcher

import android.Manifest
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
import eu.eurise.feature.launcher.apps.FAVORITES
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
    val viewModel = viewModel { LauncherViewModel(app, data.notes, data.tasks, data.calendar) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val badges by NotificationStore.counts.collectAsState()
    val messages by NotificationStore.messages.collectAsState()
    val nowPlaying by NotificationStore.nowPlaying.collectAsState()
    val allNotifications by NotificationStore.all.collectAsState()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val newsItems by viewModel.newsItems.collectAsStateWithLifecycle()
    val quotes by viewModel.quotes.collectAsStateWithLifecycle()
    val calendarMonth by viewModel.calendarMonth.collectAsStateWithLifecycle()
    val monthEvents by viewModel.monthEvents.collectAsStateWithLifecycle()
    val monthTasks by viewModel.monthTasks.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
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
                runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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

    val scene = sceneFor(now)
    EuriseTheme(darkTheme = scene.dark) {
      val accent = settings.accent.color(scene.dark)
      val onAccent = if (0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue > 0.55f) Color(0xFF14171C) else Color.White
      MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = accent, onPrimary = onAccent)) {
      // Testi più compatti: interlinea stretta su tutta la Scrivania.
      ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = 1.3.em)) {
      androidx.compose.runtime.CompositionLocalProvider(
          eu.eurise.feature.launcher.apps.LocalVariedIcons provides settings.variedIcons,
          LocalCategoryTint provides { entry -> resolveCategory(entry, settings.categories, settings.categoryOverrides)?.tint },
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
        // Categoria scelta nella barra, per id; "favorites" è la home.
        var category by remember { mutableStateOf(FAVORITES) }
        var categoryEditor by remember { mutableStateOf(false) }
        val barCategories = settings.categories
        val pageCategories = barCategories.filterNot { it.isFavorites }
        if (category != FAVORITES && pageCategories.none { it.id == category }) category = FAVORITES
        val categoryOf: (AppEntry) -> BarCategory? = { entry -> resolveCategory(entry, barCategories, settings.categoryOverrides) }
        var searching by remember { mutableStateOf(false) }
        var panelOpen by remember { mutableStateOf(false) }
        var appearance by remember { mutableStateOf(false) }
        var editingCards by remember { mutableStateOf(false) }
        // Elenco dei contatti aperto dalla pillola Comunicazioni: "recent" o "starred".
        var peopleSheet by remember { mutableStateOf<String?>(null) }
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
        // Tre pagine: Notizie e mercati a sinistra, Home al centro, Riepilogo a destra.
        val homePager = rememberPagerState(initialPage = HomePage) { 3 }
        var newsSheet by remember { mutableStateOf(false) }
        var stocksSheet by remember { mutableStateOf(false) }
        val categoryPager = rememberPagerState { pageCategories.size }
        val scope = rememberCoroutineScope()

        LaunchedEffect(homePresses) {
            if (homePresses > 0) {
                if (category == FAVORITES && !searching) homePager.animateScrollToPage(HomePage)
                category = FAVORITES
                categoryEditor = false
                searching = false
                panelOpen = false
                appearance = false
                editingCards = false
            }
        }
        LaunchedEffect(categoryPager.currentPage) {
            if (category != FAVORITES) pageCategories.getOrNull(categoryPager.currentPage)?.let { category = it.id }
        }
        BackHandler(enabled = peopleSheet != null || weatherSheet || searching || panelOpen || appearance || editingCards || newsSheet || stocksSheet || categoryEditor || category != FAVORITES || homePager.currentPage != HomePage) {
            when {
                peopleSheet != null -> peopleSheet = null
                weatherSheet -> weatherSheet = false
                newsSheet -> { newsSheet = false; viewModel.refreshNews(force = true) }
                stocksSheet -> { stocksSheet = false; viewModel.refreshQuotes() }
                appearance -> appearance = false
                panelOpen -> panelOpen = false
                editingCards -> editingCards = false
                searching -> searching = false
                categoryEditor -> categoryEditor = false
                category != FAVORITES -> category = FAVORITES
                else -> scope.launch { homePager.animateScrollToPage(HomePage) }
            }
        }

        fun select(c: BarCategory) {
            category = c.id
            if (!c.isFavorites) scope.launch { categoryPager.scrollToPage(pageCategories.indexOfFirst { it.id == c.id }.coerceAtLeast(0)) }
        }

        val setup = listOf(
            SetupStep(stringResource(R.string.setup_home), isHome, actions.becomeHome),
            SetupStep(stringResource(R.string.setup_notifications), notificationsOn, actions.grantNotifications),
            SetupStep(stringResource(R.string.setup_location), hasLocation, actions.askLocation),
            SetupStep(stringResource(R.string.setup_calendar), state.calendarConnected, actions.connectCalendar),
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

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val tablet = LocalConfiguration.current.smallestScreenWidthDp >= TabletSmallestWidth
                val board: @Composable (Boolean, Modifier) -> Unit = { left, modifier ->
                    SummaryColumn(
                        state, messages, nowPlaying, notificationsOn, if (left) emptyList() else setup, hazeState, actions, clearance, modifier,
                        weather = weather,
                        cards = if (left) settings.leftCards else settings.cards,
                        news = newsItems, quotes = quotes,
                        onNewsSources = { newsSheet = true }, onSymbols = { stocksSheet = true },
                        calendarMonth = calendarMonth, monthEvents = monthEvents, monthTasks = monthTasks,
                        onCalendarMonth = viewModel::setCalendarMonth,
                        editing = editingCards, onEditingChange = { editingCards = it },
                        onCardsChange = { cards -> viewModel.updateSettings { if (left) it.copy(leftCards = cards) else it.copy(cards = cards) } },
                    )
                }
                val summary: @Composable (Modifier) -> Unit = { modifier -> board(false, modifier) }

                if (searching || panelOpen) {
                    // Durante la ricerca e nel pannello resta solo lo sfondo sfocato.
                } else if (category == FAVORITES) {
                    if (tablet) {
                        TabletHome(
                            now = now, weather = weather, hasLocation = hasLocation, state = state,
                            hazeState = hazeState, actions = actions, onTaskDone = { viewModel.setTaskDone(it, true) },
                            clearance = clearance, summary = summary, onWeather = openWeather,
                        )
                    } else {
                        HorizontalPager(homePager, Modifier.fillMaxSize(), userScrollEnabled = !editingCards) { page ->
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
                                    welcome = welcome,
                                )
                            } else {
                                summary(Modifier.fillMaxSize())
                            }
                        }
                        if (!editingCards) PageDots(homePager.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = clearance - 6.dp))
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(scrimColor()))
                    CategoryPages(
                        pager = categoryPager,
                        categories = pageCategories,
                        categoryOf = categoryOf,
                        apps = state.apps,
                        dark = scene.dark,
                        badges = badges,
                        isFavorite = viewModel::isFavorite,
                        onOpen = actions.openApp,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onInfo = actions.appInfo,
                        onMoveTo = { app, target ->
                            viewModel.updateSettings { it.copy(categoryOverrides = it.categoryOverrides + (app.key to target.id)) }
                        },
                        bottomClearance = clearance,
                        columns = settings.columns,
                        iconSize = settings.iconSize.dp,
                        showLabels = settings.showLabels,
                        onClose = { category = FAVORITES },
                        onAppearance = { appearance = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                val barModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = GlassDefaults.SideMargin, vertical = GlassDefaults.BottomMargin)
                if (searching || panelOpen || editingCards || categoryEditor) {
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
                        categories = barCategories, selected = category, onSelect = ::select, onSearch = { searching = true },
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
                )
            }
            if (newsSheet) {
                NewsSourcesSheet(viewModel.news, onClose = { newsSheet = false; viewModel.refreshNews(force = true) })
            }
            if (stocksSheet) {
                StocksSheet(viewModel.markets, onClose = { stocksSheet = false; viewModel.refreshQuotes() })
            }
            peopleSheet?.let { kind ->
                var people by remember(kind) { mutableStateOf<List<eu.eurise.feature.launcher.contacts.Person>?>(null) }
                var granted by remember(kind) { mutableStateOf(viewModel.contacts.hasContacts()) }
                LaunchedEffect(kind, granted) {
                    people = if (kind == "recent") viewModel.contacts.recent() else viewModel.contacts.starred()
                }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = viewModel.contacts.hasContacts() }
                PeopleSheet(
                    title = stringResource(if (kind == "recent") R.string.people_recent else R.string.people_starred),
                    people = people,
                    needsPermission = !granted || (kind == "recent" && !viewModel.contacts.hasCallLog()),
                    onGrant = {
                        contactsPermission.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG))
                    },
                    loadActions = { viewModel.contacts.actions(it) },
                    onAction = { peopleSheet = null; viewModel.contacts.start(it) },
                    onOpenContact = { peopleSheet = null; viewModel.contacts.openContact(it) },
                    onClose = { peopleSheet = null },
                )
            }
            if (categoryEditor) {
                CategoryEditor(
                    categories = barCategories,
                    appCount = { c -> state.apps.count { categoryOf(it)?.id == c.id } },
                    hazeState = hazeState,
                    onChange = { list -> viewModel.updateSettings { it.copy(categories = list) } },
                    onClose = { categoryEditor = false },
                )
            }
            if (appearance) {
                AppearanceSheet(
                    settings, scene.dark, onChange = viewModel::updateSettings, onClose = { appearance = false },
                    onEditCategories = { appearance = false; categoryEditor = true },
                )
            }
        }
      }
      }
      }
    }
}

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
)

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
) {
    // Con più righe di preferiti, o su schermi bassi, le pillole mostrano una riga in meno.
    val compact = settings.rows >= 3 || (settings.rows == 2 && LocalConfiguration.current.screenHeightDp < 820)
    // Sullo sfondo libero: in su ricerca e recenti, in giù il Pannello Eurise, pressione lunga Griglia e icone.
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -threshold) onSearch() else if (total > threshold) onPanel() },
                        onVerticalDrag = { _, dy -> total += dy },
                    )
                }
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
            // Accoglienza: saluto, orario, data e meteo, poi le tre pillole.
            androidx.compose.material3.Text(
                greeting(now, settings.userName),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground,
            )
            val context = LocalContext.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockBlock(now, onClock = actions.openClock, onDate = { actions.openEurise("calendar") }, large = false, modifier = Modifier.weight(1f))
                // Accanto all'orario: fotocamera sopra, galleria sotto.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickIcon(eu.eurise.core.designsystem.icon.EuriseIcons.camera(2f), stringResource(R.string.quick_camera)) {
                        runCatching { context.startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    QuickIcon(eu.eurise.core.designsystem.icon.EuriseIcons.image(2f), stringResource(R.string.quick_gallery)) {
                        runCatching {
                            context.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            }
            WeatherRow(weather, canAsk = !hasLocation, onAsk = actions.askLocation, onOpen = onWeather)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(horizontal = (-6).dp.coerceAtLeast(0.dp)), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                AgendaPill(
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
                )
                CommsPill(
                    messages = welcome.messages,
                    enabled = welcome.notificationsOn,
                    hazeState = hazeState,
                    compact = compact,
                    onOpen = actions.openMessage,
                    onRecent = welcome.onRecent,
                    onStarred = welcome.onStarred,
                    onGrant = actions.grantNotifications,
                    onHub = { actions.openEurise("today") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    WorkPill(welcome.files, welcome.filesAccess, hazeState, Modifier.weight(1.35f), welcome.onOpenFile, welcome.onFilesGrant, welcome.onScan)
                    MusicHalf(welcome.nowPlaying, welcome.notificationsOn, hazeState, Modifier.weight(1f), welcome.onPlayer, actions.grantNotifications)
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.favorites.isEmpty()) {
                androidx.compose.material3.Text(
                    stringResource(R.string.favorites_empty),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            } else {
                // Griglia dei preferiti: colonne e righe scelte in Griglia e icone.
                val shown = state.favorites.take(settings.maxFavorites)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    shown.chunked(settings.columns).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { entry ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    AppTile(
                                        entry = entry,
                                        dark = dark,
                                        badge = badges[entry.packageName] ?: 0,
                                        favorite = isFavorite(entry),
                                        onOpen = { actions.openApp(entry) },
                                        onToggleFavorite = { onToggleFavorite(entry) },
                                        onInfo = if (entry.component != null) ({ actions.appInfo(entry) }) else null,
                                        onMove = { delta -> onMoveFavorite(entry, delta) },
                                        iconSize = settings.iconSize.dp,
                                        showLabel = settings.showLabels,
                                    )
                                }
                            }
                            repeat(settings.columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(clearance - 4.dp))
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
    calendarMonth: java.time.YearMonth,
    monthEvents: List<eu.eurise.core.data.calendar.CalendarEvent>,
    monthTasks: List<eu.eurise.core.data.model.Task>,
    onCalendarMonth: (java.time.YearMonth) -> Unit,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onCardsChange: (List<PlacedCard>) -> Unit,
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
                messages, notificationsOn, System.currentTimeMillis(), hazeState, actions.grantNotifications, actions.ownAppInfo,
                actions.openMessage, size = card.size, modifier = cardModifier,
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
        }
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
@Composable
private fun QuickIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(icon, label, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp)) }
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

package eu.eurise.feature.launcher

import android.Manifest
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
import eu.eurise.feature.launcher.apps.Category
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
private val BarCategories = Category.entries.toList()
private val PageCategories = BarCategories.drop(1)

/** Larghezza da cui si usa la disposizione per tablet. */
private val TabletWidth = 840.dp

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
        viewModel.refresh()
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasLocation = it
        viewModel.refresh()
    }
    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isHome = isDefaultHome(context) }

    val actions = remember(context) {
        LauncherActions(
            openApp = { context.launch(it) },
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
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            connectCalendar = { calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
            askLocation = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            becomeHome = { requestHomeRole(context)?.let { homeRole.launch(it) } },
        )
    }

    val scene = sceneFor(now)
    EuriseTheme(darkTheme = scene.dark) {
        val hazeState = remember { HazeState() }
        var category by remember { mutableStateOf(Category.Favorites) }
        var searching by remember { mutableStateOf(false) }
        val homePager = rememberPagerState { 2 }
        val categoryPager = rememberPagerState { PageCategories.size }
        val scope = rememberCoroutineScope()

        LaunchedEffect(homePresses) {
            if (homePresses > 0) {
                if (category == Category.Favorites && !searching) homePager.animateScrollToPage(0)
                category = Category.Favorites
                searching = false
            }
        }
        LaunchedEffect(categoryPager.currentPage) {
            if (category != Category.Favorites) category = PageCategories[categoryPager.currentPage]
        }
        BackHandler(enabled = searching || category != Category.Favorites || homePager.currentPage != 0) {
            when {
                searching -> searching = false
                category != Category.Favorites -> category = Category.Favorites
                else -> scope.launch { homePager.animateScrollToPage(0) }
            }
        }

        fun select(c: Category) {
            category = c
            if (c != Category.Favorites) scope.launch { categoryPager.scrollToPage(PageCategories.indexOf(c)) }
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
                val tablet = maxWidth >= TabletWidth
                val summary: @Composable (Modifier) -> Unit = { modifier ->
                    SummaryColumn(state, messages, nowPlaying, notificationsOn, setup, hazeState, actions, clearance, modifier)
                }

                if (category == Category.Favorites) {
                    if (tablet) {
                        TabletHome(
                            now = now, weather = weather, hasLocation = hasLocation, state = state,
                            hazeState = hazeState, actions = actions, onTaskDone = { viewModel.setTaskDone(it, true) },
                            clearance = clearance, summary = summary,
                        )
                    } else {
                        HorizontalPager(homePager, Modifier.fillMaxSize()) { page ->
                            if (page == 0) {
                                PhoneHome(
                                    now = now, weather = weather, hasLocation = hasLocation, state = state, badges = badges,
                                    dark = scene.dark, hazeState = hazeState, actions = actions,
                                    isFavorite = viewModel::isFavorite, onToggleFavorite = viewModel::toggleFavorite,
                                    onTaskDone = { viewModel.setTaskDone(it, true) }, clearance = clearance,
                                )
                            } else {
                                summary(Modifier.fillMaxSize())
                            }
                        }
                        PageDots(homePager.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = clearance - 6.dp))
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(scrimColor()))
                    CategoryPages(
                        pager = categoryPager,
                        categories = PageCategories,
                        apps = state.apps,
                        dark = scene.dark,
                        badges = badges,
                        isFavorite = viewModel::isFavorite,
                        onOpen = actions.openApp,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onInfo = actions.appInfo,
                        bottomClearance = clearance,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                val barModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = GlassDefaults.SideMargin, vertical = GlassDefaults.BottomMargin)
                if (tablet) {
                    TabletDock(
                        favorites = state.favorites, badges = badges, dark = scene.dark, selected = category,
                        onOpen = actions.openApp, onSelect = ::select, onSearch = { searching = true },
                        hazeState = hazeState, modifier = barModifier.widthIn(max = 960.dp),
                    )
                } else {
                    CategoryBar(
                        categories = BarCategories, selected = category, onSelect = ::select, onSearch = { searching = true },
                        hazeState = hazeState, modifier = barModifier.fillMaxWidth(),
                    )
                }
            }

            if (searching) {
                SearchOverlay(
                    apps = state.apps,
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
        }
    }
}

/** Le azioni che escono dalla Scrivania (aprire app, impostazioni, permessi). */
private class LauncherActions(
    val openApp: (AppEntry) -> Unit,
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
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        ClockBlock(now)
        Spacer(Modifier.height(10.dp))
        WeatherRow(weather, canAsk = !hasLocation, onAsk = actions.askLocation)
        Spacer(Modifier.weight(1f))
        TodayCard(
            now = now,
            events = state.eventsToday,
            calendarConnected = state.calendarConnected,
            tasks = state.tasksDue,
            hazeState = hazeState,
            onOpen = { actions.openEurise("today") },
            onConnectCalendar = actions.connectCalendar,
            onTaskDone = onTaskDone,
        )
        Spacer(Modifier.height(20.dp))
        Label(stringResource(R.string.favorites), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
        Spacer(Modifier.height(8.dp))
        if (state.favorites.isEmpty()) {
            androidx.compose.material3.Text(
                stringResource(R.string.favorites_empty),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                state.favorites.forEach { entry ->
                    AppTile(
                        entry = entry,
                        dark = dark,
                        badge = badges[entry.packageName] ?: 0,
                        favorite = isFavorite(entry),
                        onOpen = { actions.openApp(entry) },
                        onToggleFavorite = { onToggleFavorite(entry) },
                        onInfo = if (entry.component != null) ({ actions.appInfo(entry) }) else null,
                    )
                }
            }
        }
        Spacer(Modifier.height(clearance - 4.dp))
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
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = insets.calculateTopPadding() + 24.dp, bottom = clearance + 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            CalendarCard(
                today = state.today,
                upcoming = state.upcoming,
                connected = state.calendarConnected,
                hazeState = hazeState,
                onOpen = { actions.openEurise("calendar") },
                onConnect = actions.connectCalendar,
            )
        }
        item {
            MessagesCard(messages, notificationsOn, System.currentTimeMillis(), hazeState, actions.grantNotifications, actions.openMessage)
        }
        item { MusicCard(nowPlaying, notificationsOn, hazeState, actions.grantNotifications) }
        item { SetupCard(setup, hazeState) }
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
            Spacer(Modifier.height(48.dp))
            ClockBlock(now)
            Spacer(Modifier.height(10.dp))
            WeatherRow(weather, canAsk = !hasLocation, onAsk = actions.askLocation)
            Spacer(Modifier.height(32.dp))
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
    selected: Category,
    onOpen: (AppEntry) -> Unit,
    onSelect: (Category) -> Unit,
    onSearch: () -> Unit,
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
                BarCategories.forEach { c -> CategoryButton(c, c == selected, { onSelect(c) }, Modifier.weight(1f)) }
            }
        }
        SearchCircle(hazeState, onSearch, Modifier)
    }
}

@Composable
private fun DockDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)))
}

/** Due puntini sopra la barra: Home e Riepilogo. */
@Composable
private fun PageDots(current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(2) { i ->
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

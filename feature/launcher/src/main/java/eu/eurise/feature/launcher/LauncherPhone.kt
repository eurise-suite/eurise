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
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

// Pezzi della Scrivania sul telefono (bolle della home, pagine laterali, scorciatoie), staccati da LauncherScreen.kt.

/** Richiesta di scelta delle app: per una cartella nuova (con l'app di partenza) o per una esistente. */
internal data class FolderRequest(val folderId: String?, val place: String, val startKey: String?)

/** Oroscopo e ricette per le schede delle pagine laterali. */
internal class DailyData(
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
internal class WelcomeData(
    val travel: eu.eurise.core.data.route.Travel?,
    val destination: String?,
    val destinations: List<eu.eurise.core.data.calendar.CalendarEvent>,
    val travels: Map<String, eu.eurise.core.data.route.Travel>,
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
internal class HomeBoardData(
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
    /** Compleanni e ricorrenze (Date importanti): quelli di oggi entrano nella bolla «I tuoi impegni». */
    val importantDates: List<eu.eurise.core.data.dates.ImportantDate> = emptyList(),
    /** Per le app messe sulle pagine: le app installate per chiave, tema, numeri da leggere e misura delle icone. */
    val apps: Map<String, AppEntry> = emptyMap(),
    val dark: Boolean = false,
    val badges: Map<String, Int> = emptyMap(),
    val iconSize: Dp = 52.dp,
    val showLabels: Boolean = true,
)

/** Il contenuto di una bolla: sulla home o, come scheda, in una pagina laterale. */
@Composable
internal fun HomeBubble(
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
        val players by NotificationStore.players.collectAsStateWithLifecycle()
        when (slot.kind) {
            eu.eurise.feature.launcher.home.HomeKind.Agenda -> AgendaPill(
                now = now,
                events = state.eventsToday,
                calendarConnected = state.calendarConnected,
                tasks = state.tasksDue,
                today = state.today,
                compact = compact,
                travels = welcome.travels,
                hazeState = hazeState,
                actions = home.bubbles,
                dates = home.importantDates,
                onConnectCalendar = actions.connectCalendar,
                onCalendar = { actions.openEurise("calendar") },
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
                WorkPill(welcome.files, welcome.filesAccess, hazeState, mod, welcome.onOpenFile, welcome.onFilesGrant, documentShortcuts(state.apps, welcome, actions))
            eu.eurise.feature.launcher.home.HomeKind.Music ->
                MusicHalf(players, welcome.notificationsOn, hazeState, mod, welcome.onPlayer, actions.grantNotifications, remember(state.apps) { eu.eurise.feature.launcher.apps.musicServices(state.apps) }, actions.openApp)
            eu.eurise.feature.launcher.home.HomeKind.TodayTasks -> eu.eurise.feature.launcher.home.TodayTasksPill(
                state.tasksDue, state.today, compact, welcome.travels, hazeState, home.bubbles, mod,
            )
            eu.eurise.feature.launcher.home.HomeKind.NextEvent -> eu.eurise.feature.launcher.home.NextEventPill(
                state.upcoming, state.calendarConnected, welcome.travels, now, hazeState, home.bubbles, actions.connectCalendar, mod,
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
            eu.eurise.feature.launcher.home.HomeKind.AppWidget -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                val sized = if (slot.height > 0) mod else mod.height(slot.kind.defaultHeight.dp)
                eu.eurise.feature.launcher.home.WidgetBubble(slot.widget, hazeState, sized) { pkg ->
                    context.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                }
            }
            eu.eurise.feature.launcher.home.HomeKind.App -> PageApp(slot, home, actions, mod)
            else -> slot.kind.card?.let { kind ->
                // Le schede delle altre pagine hanno bisogno di un'altezza: se manca, quella di partenza.
                val sized = if (slot.height > 0) mod else mod.height(slot.kind.defaultHeight.coerceAtLeast(120).dp)
                home.cardBody(kind, sized)
            }
        }
}

/**
 * Un'app su una pagina (tavola ScrPagLibera): icona del tema ed etichetta, al centro della sua casella; un tocco la apre,
 * la pressione lunga porta in modifica come per le bolle. Se l'app non c'è più resta un segnaposto da togliere.
 */
@Composable
private fun PageApp(slot: eu.eurise.feature.launcher.home.HomeSlot, home: HomeBoardData, actions: LauncherActions, mod: Modifier) {
    val entry = home.apps[slot.app]
    val onSurface = MaterialTheme.colorScheme.onBackground
    Box(mod, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp))
                .then(if (entry != null) Modifier.clickable(onClickLabel = entry.label) { actions.openApp(entry) } else Modifier)
                .padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (entry != null) {
                eu.eurise.feature.launcher.apps.AppIcon(entry, home.iconSize, home.dark, home.badges[entry.packageName] ?: 0)
            } else {
                Box(Modifier.size(home.iconSize).clip(RoundedCornerShape(16.dp)).background(onSurface.copy(alpha = 0.12f)))
            }
            if (home.showLabels) {
                Spacer(Modifier.height(6.dp))
                Text(
                    entry?.label ?: stringResource(R.string.page_app_missing),
                    color = onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = home.iconSize + 24.dp),
                )
            }
        }
    }
}

/** Le azioni che escono dalla Scrivania (aprire app, impostazioni, permessi). */
internal class LauncherActions(
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
internal fun PhoneHome(
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
    onReorderFavorites: (List<String>) -> Unit,
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
                    beside = { SunTimesColumn(welcome.sun, alarm = nextAlarm, now = now, onClock = { tab -> actions.openEurise("clock:$tab") }) },
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
                eu.eurise.feature.launcher.home.FavoritesGrid(
                    favorites = state.favorites.take(settings.maxFavorites),
                    columns = settings.columns,
                    iconSize = settings.iconSize.dp,
                    dark = dark,
                    onReorder = onReorderFavorites,
                ) { entry, menuRequest, onMenuClosed, held ->
                                Box(contentAlignment = Alignment.Center) {
                                    val folder = settings.folders.firstOrNull { it.key == entry.key }
                                    if (folder != null) {
                                        val byKey = state.apps.associateBy { it.key }
                                        FolderTile(folder, folder.keys.mapNotNull { byKey[it] }, dark, settings.iconSize.dp, false, onOpen = { if (!held()) onOpenFolder(folder) })
                                    } else AppTile(
                                        entry = entry,
                                        dark = dark,
                                        badge = badges[entry.packageName] ?: 0,
                                        favorite = isFavorite(entry),
                                        // Durante una presa il rilascio non deve aprire l'app.
                                        onOpen = { if (!held()) actions.openApp(entry) },
                                        onToggleFavorite = { onToggleFavorite(entry) },
                                        onInfo = if (entry.component != null) ({ actions.appInfo(entry) }) else null,
                                        onCreateFolder = { onCreateFolder(entry) },
                                        iconSize = settings.iconSize.dp,
                                        // Sulla home le icone vanno senza nome: più spazio per l'accoglienza.
                                        showLabel = false,
                                        externalLongPress = true,
                                        menuRequest = menuRequest,
                                        onMenuClosed = onMenuClosed,
                                    )
                                }
                }
            }
            // Poco spazio tra i preferiti e i puntini delle pagine.
            Spacer(Modifier.height((clearance + 10.dp - WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()).coerceAtLeast(8.dp)))
        }
    }
}

/** Una pagina laterale: le stesse bolle della home, con gli stessi gesti di modifica. */
@Composable
internal fun SidePage(
    slots: List<eu.eurise.feature.launcher.home.HomeSlot>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onChange: (List<eu.eurise.feature.launcher.home.HomeSlot>) -> Unit,
    onCatalog: () -> Unit,
    setup: List<SetupStep>,
    hazeState: HazeState,
    clearance: Dp,
    modifier: Modifier,
    content: @Composable (eu.eurise.feature.launcher.home.HomeSlot, Modifier) -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Column(
        modifier
            .padding(top = insets.calculateTopPadding())
            .clipToBounds()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = if (editing) insets.calculateBottomPadding() + 16.dp else clearance + 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        eu.eurise.feature.launcher.home.HomeBoard(slots, editing, onEditingChange, onChange, onCatalog, content)
        if (!editing && setup.isNotEmpty()) SetupCard(setup, hazeState)
    }
}

/** «Apri da…» di Eurise File (FilesActivity dell'app Eurise, stesso processo della Scrivania). */
private const val OPEN_FROM = "eu.eurise.action.OPEN_FROM"
private const val EXTRA_EXTENSIONS = "eu.eurise.extra.EXTENSIONS"
private const val EXTRA_FROM = "eu.eurise.extra.FROM"

/** Le scorciatoie di Documenti, da sinistra: fotocamera, note, PDF, Office, Drive, scanner. Quelle senza app non compaiono. */
@Composable
internal fun documentShortcuts(apps: List<AppEntry>, welcome: WelcomeData, actions: LauncherActions): List<eu.eurise.feature.launcher.home.DocShortcut> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val camera = stringResource(R.string.quick_camera)
    val notes = stringResource(R.string.doc_notes)
    val pdf = stringResource(R.string.doc_pdf)
    val office = stringResource(R.string.doc_office)
    val drive = stringResource(R.string.doc_drive)
    val scanner = stringResource(R.string.welcome_scan)
    val icons = eu.eurise.core.designsystem.icon.EuriseIcons
    val suiteOffice = suiteOffice(apps)
    // PDF con Eurise Office: si sceglie il file da Eurise File («Apri da…», solo PDF) e lo apre Office (Annota, Pagine, Firma).
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val office = suiteOffice ?: return@rememberLauncherForActivityResult
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").setPackage(office.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
            )
        }.onFailure { actions.openApp(office) }
    }
    return remember(apps, welcome.onScan, suiteOffice) {
        val shortcut = { icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, run: () -> Unit -> eu.eurise.feature.launcher.home.DocShortcut(icon, label, run) }
        val launch = { entry: AppEntry -> { actions.openApp(entry) } }
        listOfNotNull(
            shortcut(icons.camera(2f), camera) {
                runCatching { context.startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            shortcut(icons.pen(2f), notes) { actions.openEurise("notes") },
            if (suiteOffice != null) {
                shortcut(icons.pdf(2f), pdf) {
                    val open = Intent(OPEN_FROM).setPackage(context.packageName)
                        .putExtra(EXTRA_EXTENSIONS, "pdf").putExtra(EXTRA_FROM, pdf)
                    runCatching { pickPdf.launch(open) }.onFailure { actions.openApp(suiteOffice) }
                }
            } else {
                context.pdfApp(apps)?.let { shortcut(icons.pdf(2f), pdf, launch(it)) }
            },
            officeApp(apps)?.let { shortcut(icons.office(2f), office, launch(it)) },
            driveApp(apps)?.let { shortcut(icons.cloud(2f), drive, launch(it)) },
            shortcut(icons.scanner(2f), scanner, welcome.onScan),
        )
    }
}

/** Il contenuto di una scheda delle pagine laterali; si usa anche quando la scheda sta sulla home. */
@Composable
internal fun SummaryCardBody(
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

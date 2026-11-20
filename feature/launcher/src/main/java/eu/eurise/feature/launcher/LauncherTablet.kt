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

// Scrivania sul tablet e piccoli aiuti comuni, staccati da LauncherScreen.kt.

/** Tablet a tocco: orario, meteo e Oggi a sinistra, Riepilogo a destra. */
@Composable
internal fun TabletHome(
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
            // Il backup fermo da qualche giorno, come sul telefono (largo quanto la scheda di oggi).
            Column(Modifier.widthIn(max = 520.dp)) { BackupBanner(hazeState) { actions.openEurise("backup") } }
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
internal fun TabletDock(
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
internal fun DockDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)))
}

/** Finché la Scrivania non è la schermata iniziale, un invito in alto a renderla tale. */
@Composable
internal fun HomeBanner(hazeState: HazeState, onClick: () -> Unit) =
    NoticeBanner(hazeState, stringResource(R.string.setup_home), stringResource(R.string.setup_home_action), onClick)

/** Un avviso a pillola in cima alla Scrivania: cosa c'è e, a destra, cosa fare. */
@Composable
internal fun NoticeBanner(hazeState: HazeState, text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(hazeState, RoundedCornerShape(50), elevation = 6.dp, outline = false)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            text,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Text(
            action,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

/** L'avviso del backup fermo da qualche giorno ([eu.eurise.core.cloud.backup.BackupHealth]); tocco: Cloud › Backup. */
@Composable
internal fun BackupBanner(hazeState: HazeState, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = androidx.compose.runtime.remember { eu.eurise.core.cloud.backup.BackupManager.get(context) }
    val backup by manager.state.collectAsStateWithLifecycle()
    backup.lateDays() ?: return
    val last = backup.lastAt
    val text = if (last == null) stringResource(R.string.backup_late_never)
    else stringResource(R.string.backup_late, java.util.concurrent.TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - last).toInt())
    NoticeBanner(hazeState, text, stringResource(R.string.backup_late_action), onClick)
    Spacer(Modifier.height(12.dp))
}

/** Pagina della home nel pager (al centro tra Notizie e Riepilogo). */
internal const val HomePage = 1

/** Piccola icona toccabile accanto all'orario, grande come i simboli del meteo. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun QuickIcon(
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
internal fun PageDots(current: Int, count: Int = 3, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    // Un tocco sui puntini apre «Le tue pagine» (tavola ScrPagGestisci): l'area tocca è alta 40 dp.
    Row(
        modifier.then(
            if (onClick != null) Modifier.clip(CircleShape)
                .clickable(onClickLabel = androidx.compose.ui.res.stringResource(eu.eurise.feature.launcher.R.string.pages_title), onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 17.dp)
            else Modifier,
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 16.dp else 6.dp, height = 6.dp)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (i == current) 0.85f else 0.4f), CircleShape),
            )
        }
    }
}

internal fun isDefaultHome(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
    }
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == context.packageName
}

/** Chiede di diventare la schermata iniziale; prima di Android 10 apre le impostazioni. */
internal fun requestHomeRole(context: Context): Intent? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }
    runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    return null
}

/** Apre la conversazione della notifica; se non si può, apre l'app. */
internal fun Context.openMessage(item: MessageItem) {
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
internal fun Context.openNotificationAccess() {
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
internal fun Context.expandNotifications() {
    runCatching {
        val service = getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
    }
}

/** Previsioni dei prossimi giorni, quando sul telefono non c'è un'app del meteo. */
@Composable
internal fun WeatherSheet(days: List<eu.eurise.feature.launcher.weather.DayForecast>, onClose: () -> Unit) {
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
internal fun launcherGlass(base: eu.eurise.core.designsystem.theme.EuriseExtraColors, bubbleTint: Color? = null): eu.eurise.core.designsystem.theme.EuriseExtraColors {
    val glass = if (base.isDark) Color(0xE6161A21) else Color(0xC2FBFBF9)
    // «Come le bolle» (tavola IcoColoreBolle): le bolle prendono un velo del colore scelto, le icone la tinta piena.
    val tinted = bubbleTint?.let { androidx.compose.ui.graphics.lerp(glass, it.copy(alpha = glass.alpha), if (base.isDark) 0.30f else 0.16f) }
    return base.copy(glass = tinted ?: glass)
}

/** Accento per il tema scuro: si avvicina al blu-grafite dello sfondo e perde un po' di brillantezza. */
internal fun nightAccent(color: Color): Color = androidx.compose.ui.graphics.lerp(color, Color(0xFF2A303B), 0.32f)


/** Valore di "categoria aperta" che indica la home (nessuna categoria). */
internal const val HomePageId = "@home"

package eu.eurise.feature.launcher.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.model.Task
import eu.eurise.core.designsystem.component.GlassDefaults
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.EuriseThemeExtras
import eu.eurise.core.designsystem.theme.PlusJakartaSans
import eu.eurise.core.designsystem.theme.colors
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.apps.AppEntry
import eu.eurise.feature.launcher.apps.AppIcon
import eu.eurise.feature.launcher.apps.BarCategory
import eu.eurise.feature.launcher.weather.DayForecast
import eu.eurise.feature.launcher.weather.Sky
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Italian = Locale.ITALIAN
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Italian)
private val WeekdayShort = DateTimeFormatter.ofPattern("EEE", Italian)

/**
 * Orario grande in Plus Jakarta Sans medio (variante A approvata), con la data sotto.
 * L'orario apre l'orologio, la data il calendario.
 */
@Composable
fun ClockBlock(
    now: LocalDateTime,
    onClock: () -> Unit,
    onDate: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = true,
    beside: @Composable () -> Unit = {},
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            now.format(TimeFormat),
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Medium,
            fontSize = if (large) 80.sp else 64.sp,
            lineHeight = if (large) 84.sp else 70.sp,
            letterSpacing = (-2).sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClock),
        )
        Spacer(Modifier.width(12.dp))
        beside()
        }
        Text(
            now.format(DateFormat).replaceFirstChar { it.titlecase(Italian) },
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDate).padding(vertical = 2.dp),
        )
    }
}

fun skyIcon(sky: Sky) = when (sky) {
    Sky.Clear -> EuriseIcons.sun()
    Sky.Cloudy -> EuriseIcons.cloud()
    Sky.Fog -> EuriseIcons.fog()
    Sky.Rain -> EuriseIcons.rain()
    Sky.Snow -> EuriseIcons.snow()
    Sky.Storm -> EuriseIcons.storm()
}

/** Meteo in una riga semplice, senza pillola: oggi e i due giorni seguenti. */
@Composable
fun WeatherRow(forecast: List<DayForecast>?, canAsk: Boolean, onAsk: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onBackground
    if (forecast.isNullOrEmpty()) {
        if (canAsk) {
            Row(
                modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAsk).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.sun(), null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.weather_grant), color = color.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        forecast.take(3).forEachIndexed { i, day ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(skyIcon(day.sky), null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                if (i == 0) {
                    Text("${day.max}°", color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(" / ${day.min}°", color = color.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(
                        day.date.format(WeekdayShort).replaceFirstChar { it.titlecase(Italian) } + " ${day.max}°",
                        color = color.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun eventTime(e: CalendarEvent, allDay: String) =
    if (e.allDay) allDay else "${e.start.format(TimeFormat)} – ${e.end.format(TimeFormat)}"

/** Scheda di vetro "Oggi": il prossimo appuntamento e le prime cose da fare. */
@Composable
fun TodayCard(
    now: LocalDateTime,
    events: List<CalendarEvent>,
    calendarConnected: Boolean,
    tasks: List<Task>,
    hazeState: HazeState,
    onOpen: () -> Unit,
    onConnectCalendar: () -> Unit,
    onTaskDone: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .glass(hazeState, shape, elevation = 8.dp, outline = false)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Label(stringResource(R.string.today_next))
        val next = events.firstOrNull { it.allDay || it.end.isAfter(now) }
        when {
            !calendarConnected -> Text(
                stringResource(R.string.today_connect_calendar),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onConnectCalendar),
            )
            next == null -> Text(stringResource(R.string.today_free), color = muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                val tint = CalendarTint.nearest(next.color).colors()
                Box(Modifier.width(3.dp).height(34.dp).background(tint.dot, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(next.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val where = next.location.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    Text(eventTime(next, stringResource(R.string.today_all_day)) + where, color = muted, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Label(stringResource(R.string.today_todo))
        if (tasks.isEmpty()) {
            Text(stringResource(R.string.today_no_tasks), color = muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } else {
            tasks.take(3).forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
                            .clickable(role = Role.Checkbox) { onTaskDone(task) },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(task.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (tasks.size > 3) Text(stringResource(R.string.today_more_tasks, tasks.size - 3), color = muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun Label(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(Italian), color = color, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

/**
 * Un'app con icona a tema ed etichetta. Tenendola premuta si aggiunge o toglie dai preferiti.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    entry: AppEntry,
    dark: Boolean,
    badge: Int,
    favorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onInfo: (() -> Unit)?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
    labelColor: Color = MaterialTheme.colorScheme.onBackground,
    showLabel: Boolean = true,
    /** Sposta il preferito di un posto: -1 prima, +1 dopo. Nullo fuori dai preferiti. */
    onMove: ((Int) -> Unit)? = null,
    /** Categorie in cui spostare l'app; vuoto per non mostrare la voce. */
    moveTargets: List<BarCategory> = emptyList(),
    onMoveTo: (BarCategory) -> Unit = {},
    /** Crea una cartella partendo da questa app; nullo per non mostrare la voce. */
    onCreateFolder: (() -> Unit)? = null,
    /**
     * Pressione lunga gestita da chi contiene l'icona (per il trascinamento): l'icona non la intercetta e apre
     * il menu quando [menuRequest] diventa vero.
     */
    externalLongPress: Boolean = false,
    menuRequest: Boolean = false,
    onMenuClosed: () -> Unit = {},
) {
    var moving by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(menuRequest) { if (menuRequest) { menu = true; onMenuClosed() } }
    Box(modifier) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (externalLongPress) Modifier.clickable(onClick = onOpen)
                    else Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
                )
                .padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(entry, iconSize, dark, badge)
            if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                entry.label,
                color = labelColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = iconSize + 20.dp),
            )
            }
        }
        DropdownMenu(expanded = menu && moving, onDismissRequest = { menu = false; moving = false }) {
            moveTargets.forEach { target ->
                DropdownMenuItem(
                    text = { Text(target.label) },
                    leadingIcon = { Icon(target.iconVector(2f), null) },
                    onClick = { menu = false; moving = false; onMoveTo(target) },
                )
            }
        }
        DropdownMenu(expanded = menu && !moving, onDismissRequest = { menu = false }) {
            if (onCreateFolder != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_create)) },
                    leadingIcon = { Icon(EuriseIcons.grid(), null) },
                    onClick = { menu = false; onCreateFolder() },
                )
            }
            if (moveTargets.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_move_to)) },
                    leadingIcon = { Icon(EuriseIcons.folder(), null) },
                    onClick = { moving = true },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add)) },
                leadingIcon = { Icon(EuriseIcons.star(), null) },
                onClick = { menu = false; onToggleFavorite() },
            )
            if (onMove != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.favorite_move_before)) },
                    leadingIcon = { Icon(EuriseIcons.chevronLeft(), null) },
                    onClick = { menu = false; onMove(-1) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.favorite_move_after)) },
                    leadingIcon = { Icon(EuriseIcons.chevronRight(), null) },
                    onClick = { menu = false; onMove(1) },
                )
            }
            if (onInfo != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (entry.shortcut != null) R.string.webapp_remove else R.string.app_info)) },
                    leadingIcon = { Icon(EuriseIcons.tools(), null) },
                    onClick = { menu = false; onInfo() },
                )
            }
        }
    }
}

/**
 * Barra in basso: un'unica pillola di vetro con le categorie, il browser come voce jolly se resta posto e, in fondo,
 * la lente della ricerca universale. La categoria attiva prende il colore d'accento e una lineetta sotto.
 */
@Composable
fun CategoryBar(
    categories: List<BarCategory>,
    selected: String,
    onSelect: (BarCategory) -> Unit,
    onSearch: () -> Unit,
    onBrowser: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
) {
    Row(
        modifier
            .height(GlassDefaults.PillHeight)
            .glass(hazeState, RoundedCornerShape(50), outline = false)
            .padding(GlassDefaults.Padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        categories.forEach { c -> CategoryButton(c, c.id == selected, { onSelect(c) }, Modifier.weight(1f), onEdit) }
        if (categories.size < eu.eurise.feature.launcher.apps.MAX_CATEGORIES) {
            val label = stringResource(R.string.search_web)
            Box(
                Modifier.weight(1f).height(GlassDefaults.ItemHeight).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onBrowser),
                contentAlignment = Alignment.Center,
            ) { Icon(EuriseIcons.globe(2.2f), label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)) }
        }
        val search = stringResource(R.string.search)
        Box(
            Modifier.weight(1f).height(GlassDefaults.ItemHeight).clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = search, onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            BreathingSearchIcon(search, EuriseIcons.search(2.2f), 24.dp)
        }
    }
}

/** Una categoria della barra; tenendola premuta si apre la modifica della barra. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryButton(category: BarCategory, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: () -> Unit = {}) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .height(GlassDefaults.ItemHeight)
            .clip(CircleShape)
            .combinedClickable(role = Role.Tab, onClickLabel = category.label, onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            category.iconVector(if (active) 2.8f else 2.2f),
            contentDescription = category.label,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.width(14.dp).height(3.dp).background(if (active) color else Color.Transparent, RoundedCornerShape(2.dp)))
    }
}

@Composable
fun SearchCircle(hazeState: HazeState, onSearch: () -> Unit, modifier: Modifier = Modifier, web: Boolean = false) {
    val label = stringResource(if (web) R.string.search_web else R.string.search)
    Box(
        modifier
            .size(GlassDefaults.PillHeight)
            .glass(hazeState, CircleShape, outline = false)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onSearch),
        contentAlignment = Alignment.Center,
    ) {
        if (web) Icon(EuriseIcons.globe(), label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        else BreathingSearchIcon(label, EuriseIcons.search(), 22.dp)
    }
}

/**
 * La lente respira: cresce e cala lentamente (2,4 s per verso), da più piccola a più grande del normale,
 * e al massimo prende in pieno il colore d'accento. Un'onda sola e morbida, non un battito.
 */
@Composable
private fun BreathingSearchIcon(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, size: androidx.compose.ui.unit.Dp) {
    val breath by rememberInfiniteTransition(label = "respiro").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "respiro",
    )
    Icon(
        icon, label,
        tint = lerp(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, breath),
        modifier = Modifier.size(size).graphicsLayer {
            val scale = 0.8f + 0.5f * breath
            scaleX = scale
            scaleY = scale
        },
    )
}

/** Apre la ricerca sul web con il browser predefinito; se il browser non la gestisce, apre il browser. */
fun android.content.Context.openWebSearch() {
    val search = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH)
        .putExtra(android.app.SearchManager.QUERY, "")
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = runCatching { startActivity(search) }.isSuccess
    if (!ok) {
        runCatching {
            startActivity(
                android.content.Intent.makeMainSelectorActivity(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_BROWSER)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Velo leggero dietro le pagine delle categorie e della ricerca, perché il testo resti leggibile. */
@Composable
fun scrimColor(): Color = if (EuriseThemeExtras.colors.isDark) Color(0xFF0E1116).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.25f)

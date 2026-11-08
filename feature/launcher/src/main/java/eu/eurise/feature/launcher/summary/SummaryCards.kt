package eu.eurise.feature.launcher.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import eu.eurise.core.data.model.Task
import java.time.YearMonth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.colors
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.home.Label
import eu.eurise.feature.launcher.home.skyIcon
import eu.eurise.feature.launcher.weather.DayForecast
import eu.eurise.feature.launcher.news.NewsItem
import eu.eurise.feature.launcher.markets.Quote
import eu.eurise.core.data.model.Note
import androidx.compose.ui.draw.clipToBounds
import eu.eurise.feature.launcher.notifications.MessageItem
import eu.eurise.feature.launcher.notifications.NowPlaying
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Italian = Locale.ITALIAN
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DayFormat = DateTimeFormatter.ofPattern("EEE d", Italian)

/** Contenitore comune delle schede del Riepilogo: vetro piatto, titolo in maiuscoletto. */
@Composable
fun SummaryCard(
    title: String,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .glass(hazeState, RoundedCornerShape(28.dp), elevation = 8.dp, outline = false)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (action != null && onAction != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Label(title) }
                Text(
                    action, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        } else if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Label(title) }
                trailing()
            }
        } else {
            Label(title)
        }
        // Il contenuto scorre dentro la scheda: si vede quanto entra, il resto scendendo.
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Calendario: mese piccolo a sinistra, prossimi appuntamenti a destra. */
@Composable
fun CalendarCard(
    today: LocalDate,
    upcoming: List<CalendarEvent>,
    connected: Boolean,
    hazeState: HazeState,
    onOpen: () -> Unit,
    onConnect: () -> Unit,
    size: CardSize = CardSize.Large,
    modifier: Modifier = Modifier,
    month: YearMonth = YearMonth.from(today),
    monthEvents: List<CalendarEvent> = emptyList(),
    monthTasks: List<Task> = emptyList(),
    onMonthChange: (YearMonth) -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    onOpenTasks: () -> Unit = {},
) {
    if (size == CardSize.Large && connected) {
        SummaryCard(stringResource(R.string.card_calendar), hazeState, modifier, action = stringResource(R.string.calendar_open), onAction = onOpen) {
            InteractiveMonth(today, month, monthEvents, monthTasks, onMonthChange, onOpenDay, onOpenTasks)
        }
        return
    }
    val list: @Composable (Int) -> Unit = { max ->
        when {
            !connected -> TextButton(onClick = onConnect) { Text(stringResource(R.string.today_connect_calendar)) }
            upcoming.isEmpty() -> Text(stringResource(R.string.card_calendar_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            else -> upcoming.take(maxOf(max, 9)).forEach { e -> UpcomingRow(e, today) }
        }
    }
    SummaryCard(stringResource(R.string.card_calendar), hazeState, modifier, onClick = onOpen) {
        when (size) {
            CardSize.Large -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MiniMonth(today, upcoming.map { it.firstDay }.toSet(), Modifier.weight(1f))
                Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { list(4) }
            }
            CardSize.Wide -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { list(2) }
            CardSize.Medium -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { list(3) }
            CardSize.Small -> list(1)
        }
    }
}

@Composable
private fun UpcomingRow(e: CalendarEvent, today: LocalDate) {
    val tint = CalendarTint.nearest(e.color).colors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(32.dp).background(tint.dot, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(e.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val day = if (e.firstDay == today) stringResource(R.string.weather_today) else e.start.format(DayFormat)
            val time = if (e.allDay) "" else " · " + e.start.format(TimeFormat)
            Text(day.replaceFirstChar { it.titlecase(Italian) } + time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

/**
 * Mese interattivo: frecce per cambiare mese, tocco sul titolo per scegliere l'anno, tocco su un giorno per
 * vederne appuntamenti e cose da fare accanto.
 */
@Composable
private fun InteractiveMonth(
    today: LocalDate,
    month: YearMonth,
    events: List<CalendarEvent>,
    tasks: List<Task>,
    onMonthChange: (YearMonth) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenTasks: () -> Unit,
) {
    var selected by remember { mutableStateOf(today) }
    var yearMenu by remember { mutableStateOf(false) }
    if (YearMonth.from(selected) != month) selected = if (YearMonth.from(today) == month) today else month.atDay(1)
    val busy = remember(events, tasks) {
        events.flatMap { e -> generateSequence(e.firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(e.lastDay) }.take(40).toList() }.toSet() +
            tasks.mapNotNull { it.due }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallArrow(EuriseIcons.chevronLeft(), stringResource(R.string.calendar_prev)) { onMonthChange(month.minusMonths(1)) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        month.month.getDisplayName(TextStyle.FULL_STANDALONE, Italian).replaceFirstChar { it.titlecase(Italian) } + " " + month.year,
                        fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { yearMenu = true }.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                        (month.year - 5..month.year + 5).forEach { y ->
                            DropdownMenuItem(
                                text = { Text("$y", fontWeight = if (y == month.year) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { yearMenu = false; onMonthChange(month.withYear(y)) },
                            )
                        }
                    }
                }
                SmallArrow(EuriseIcons.chevronRight(), stringResource(R.string.calendar_next)) { onMonthChange(month.plusMonths(1)) }
            }
            Row {
                DayOfWeek.values().forEach { d ->
                    Text(
                        d.getDisplayName(TextStyle.NARROW_STANDALONE, Italian),
                        color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val first = month.atDay(1)
            val lead = first.dayOfWeek.value - 1
            val days = month.lengthOfMonth()
            for (week in 0 until (lead + days + 6) / 7) {
                Row {
                    for (col in 0 until 7) {
                        val n = week * 7 + col - lead + 1
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                .then(if (n in 1..days) Modifier.clickable { selected = month.atDay(n) } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (n in 1..days) {
                                val date = month.atDay(n)
                                val isToday = date == today
                                val isSelected = date == selected
                                when {
                                    isToday -> Box(Modifier.size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                    isSelected -> Box(Modifier.size(22.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape))
                                }
                                Text(
                                    "$n", fontSize = 10.5.sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                                if (!isToday && date in busy) {
                                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(3.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
        // Il giorno scelto: appuntamenti e cose da fare.
        Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val dayEvents = events.filter { it.occursOn(selected) }.sortedBy { it.start }
            val dayTasks = tasks.filter { it.due == selected && !it.done }
            Text(
                (if (selected == today) stringResource(R.string.weather_today) else selected.format(DateTimeFormatter.ofPattern("EEEE d", Italian)))
                    .replaceFirstChar { it.titlecase(Italian) },
                fontWeight = FontWeight.Bold, fontSize = 13.5.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenDay(selected) }.padding(vertical = 2.dp),
            )
            if (dayEvents.isEmpty() && dayTasks.isEmpty()) {
                Text(stringResource(R.string.calendar_day_free), color = muted, fontSize = 12.5.sp, lineHeight = 16.sp)
            }
            dayEvents.take(4).forEach { e ->
                val tint = CalendarTint.nearest(e.color).colors()
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenDay(selected) }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).height(30.dp).background(tint.dot, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(e.title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (e.allDay) stringResource(R.string.today_all_day) else e.start.format(TimeFormat) + " – " + e.end.format(TimeFormat),
                            color = muted, fontSize = 11.5.sp, lineHeight = 14.sp,
                        )
                    }
                }
            }
            if (dayEvents.size > 4) Text(stringResource(R.string.today_more_tasks, dayEvents.size - 4), color = muted, fontSize = 11.5.sp)
            dayTasks.take(3).forEach { t ->
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenTasks), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(13.dp).border(1.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(t.title, fontSize = 12.5.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SmallArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, modifier = Modifier.size(16.dp)) }
}

/** Mese corrente in sette colonne, da lunedì; oggi in accento, un puntino sui giorni con appuntamenti. */
@Composable
private fun MiniMonth(today: LocalDate, busy: Set<LocalDate>, modifier: Modifier = Modifier) {
    val first = today.withDayOfMonth(1)
    val lead = first.dayOfWeek.value - 1
    val days = today.lengthOfMonth()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            today.month.getDisplayName(TextStyle.FULL_STANDALONE, Italian).replaceFirstChar { it.titlecase(Italian) },
            fontWeight = FontWeight.Bold, fontSize = 14.sp,
        )
        Row {
            DayOfWeek.values().forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW_STANDALONE, Italian),
                    color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val cells = lead + days
        for (week in 0 until (cells + 6) / 7) {
            Row {
                for (col in 0 until 7) {
                    val n = week * 7 + col - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (n in 1..days) {
                            val date = first.withDayOfMonth(n)
                            val isToday = date == today
                            if (isToday) Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            Text(
                                "$n",
                                fontSize = 10.5.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (!isToday && date in busy) {
                                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(3.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Ti hanno cercato" tra le schede: la stessa bolla della home, con più righe nelle schede alte. */
@Composable
fun MessagesCard(
    messages: List<MessageItem>,
    enabled: Boolean,
    hazeState: HazeState,
    onGrant: () -> Unit,
    onAppInfo: () -> Unit,
    onOpen: (MessageItem) -> Unit,
    onHub: () -> Unit,
    size: CardSize = CardSize.Large,
    modifier: Modifier = Modifier,
    onBubble: (MessageItem) -> Unit = eu.eurise.feature.launcher.notifications.LocalQuickReply.current,
) {
    val people = eu.eurise.feature.launcher.home.LocalPeopleShortcuts.current
    SummaryCard(
        stringResource(R.string.welcome_comms), hazeState, modifier,
        trailing = people?.let { p -> { eu.eurise.feature.launcher.home.CommsButtons(p.onRecent, p.onStarred) } },
    ) {
        eu.eurise.feature.launcher.home.CommsBody(
            messages, enabled, rows = if (size.tall) 6 else 3, onOpen, onBubble, onGrant, onAppInfo, onHub,
        )
    }
}

@Composable
internal fun ago(now: Long, at: Long): String {
    val min = ((now - at) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> stringResource(R.string.time_now)
        min < 60 -> "$min min"
        min < 24 * 60 -> "${min / 60} h"
        else -> "${min / (24 * 60)} g"
    }
}

/** Musica: il brano in ascolto in qualsiasi app, con i comandi. Nessun logo: solo titolo, artista e app. */
@Composable
fun MusicCard(
    playing: NowPlaying?,
    enabled: Boolean,
    hazeState: HazeState,
    onGrant: () -> Unit,
    onAppInfo: () -> Unit,
    size: CardSize = CardSize.Wide,
    modifier: Modifier = Modifier,
) {
    SummaryCard(stringResource(R.string.card_music), hazeState, modifier) {
        when {
            !enabled -> GrantText(stringResource(R.string.card_music_grant), onGrant, onAppInfo)
            playing == null -> Text(stringResource(R.string.card_music_idle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.5.sp)
            else -> MusicBody(playing, compact = size.half)
        }
    }
}

/** Titolo, artista e comandi; nelle schede strette i comandi vanno sotto. */
@Composable
fun MusicBody(playing: NowPlaying, compact: Boolean) {
    val controls = playing.controller.transportControls
    val info: @Composable () -> Unit = {
        Column {
            Text(playing.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(playing.artist, playing.appLabel).filter { it.isNotBlank() }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val buttons: @Composable () -> Unit = {
        MediaButton(EuriseIcons.previous(), stringResource(R.string.music_previous)) { controls.skipToPrevious() }
        MediaButton(
            if (playing.playing) EuriseIcons.pause() else EuriseIcons.play(),
            stringResource(if (playing.playing) R.string.music_pause else R.string.music_play),
            filled = true,
        ) { if (playing.playing) controls.pause() else controls.play() }
        MediaButton(EuriseIcons.next(), stringResource(R.string.music_next)) { controls.skipToNext() }
    }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            info()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { buttons() }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { info() }
            buttons()
        }
    }
}

/** Meteo: oggi, i prossimi giorni in riga o l'intera settimana. */
@Composable
fun WeatherCard(
    forecast: List<DayForecast>?,
    hazeState: HazeState,
    onAsk: () -> Unit,
    size: CardSize,
    modifier: Modifier = Modifier,
) {
    SummaryCard(stringResource(R.string.weather_title), hazeState, modifier, onClick = if (forecast == null) onAsk else null) {
        if (forecast.isNullOrEmpty()) {
            Text(stringResource(R.string.weather_grant), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else if (size == CardSize.Wide) {
            val days = 5
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                forecast.take(days).forEachIndexed { i, d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dayName(i, d), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Icon(skyIcon(d.sky), null, modifier = Modifier.padding(vertical = 4.dp).size(20.dp))
                        Text("${d.max}°", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${d.min}°", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            val days = when (size) { CardSize.Small -> 1; CardSize.Medium -> 3; else -> 7 }
            forecast.take(days).forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(skyIcon(d.sky), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(dayName(i, d), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${d.max}°", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Text(" ${d.min}°", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun dayName(index: Int, d: DayForecast): String =
    if (index == 0) stringResource(R.string.weather_today)
    else d.date.format(DateTimeFormatter.ofPattern("EEE", Italian)).replaceFirstChar { it.titlecase(Italian) }

/** Note fissate: le note in cima alla lista di Eurise. */
@Composable
fun PinnedNotesCard(
    notes: List<Note>,
    hazeState: HazeState,
    onOpen: (Note) -> Unit,
    onOpenAll: () -> Unit,
    size: CardSize,
    modifier: Modifier = Modifier,
) {
    val max = 12
    SummaryCard(stringResource(R.string.card_notes), hazeState, modifier, onClick = onOpenAll) {
        if (notes.isEmpty()) {
            Text(stringResource(R.string.card_notes_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp)
        }
        notes.take(max).forEach { n ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(n) }.padding(vertical = 2.dp)) {
                Text(n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty() }, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.title.isNotBlank() && n.body.isNotBlank()) {
                    Text(n.body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MediaButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

/**
 * Invito a concedere l'accesso alle notifiche. Da Android 13, per le app installate da file, Android blocca
 * questo permesso finché non si sblocca "impostazioni con restrizioni" nelle informazioni dell'app.
 */
@Composable
private fun GrantText(text: String, onGrant: () -> Unit, onAppInfo: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(text, color = muted, fontSize = 13.sp, lineHeight = 17.sp)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        Text(stringResource(R.string.grant_restricted), color = muted, fontSize = 12.sp, lineHeight = 16.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onGrant, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(stringResource(R.string.grant), fontWeight = FontWeight.Bold)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            TextButton(onClick = onAppInfo, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(stringResource(R.string.app_info), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Una voce della scheda "Prepara la Scrivania". */
data class SetupStep(val label: String, val done: Boolean, val onClick: () -> Unit)

/** Compare finché manca qualcosa: Scrivania come schermata iniziale, notifiche, posizione, calendario. */
@Composable
fun SetupCard(steps: List<SetupStep>, hazeState: HazeState) {
    if (steps.all { it.done }) return
    SummaryCard(stringResource(R.string.setup_title), hazeState) {
        steps.forEach { step ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = !step.done, onClick = step.onClick).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (step.done) EuriseIcons.task(EuriseIcons.StrokeSelected) else EuriseIcons.chevronRight(),
                    null,
                    tint = if (step.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(step.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (step.done) Text(stringResource(R.string.setup_done), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Notizie dalle fonti scelte; quelle della propria provincia in cima, con il nome della provincia. */
@Composable
fun NewsCard(
    items: List<NewsItem>?,
    hazeState: HazeState,
    onOpen: (NewsItem) -> Unit,
    onSources: () -> Unit,
    size: CardSize,
    modifier: Modifier = Modifier,
) {
    val max = 25
    val now = System.currentTimeMillis()
    SummaryCard(stringResource(R.string.card_news), hazeState, modifier, action = stringResource(R.string.news_sources), onAction = onSources) {
        when {
            items == null -> Text(stringResource(R.string.news_loading), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            items.isEmpty() -> Text(stringResource(R.string.news_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp)
            else -> items.take(max).forEach { n ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(n) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (n.image != null) {
                        NewsThumb(n.image)
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            n.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, lineHeight = 18.sp,
                            maxLines = if (size.half) 3 else 2, overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOfNotNull(n.province, n.source, if (n.publishedAt > 0) agoText(now, n.publishedAt) else null).joinToString(" · ")
                        Text(meta, color = if (n.province != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Foto piccola della notizia; le immagini già scaricate restano in memoria finché il launcher è aperto. */
@Composable
private fun NewsThumb(url: String) {
    var bitmap by remember(url) { mutableStateOf(NewsThumbs[url]) }
    LaunchedEffect(url) {
        if (bitmap == null) eu.eurise.feature.launcher.feeds.FeedReader.image(url, maxSide = 160)?.let { NewsThumbs[url] = it; bitmap = it }
    }
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(it, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

private val NewsThumbs = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, androidx.compose.ui.graphics.ImageBitmap>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.ImageBitmap>?) = size > 80
})

@Composable
private fun agoText(now: Long, at: Long): String = ago(now, at)

/** Borsa: indici, cambi e titoli scelti, con la variazione del giorno. */
@Composable
fun StocksCard(
    quotes: List<Quote>?,
    hazeState: HazeState,
    onSymbols: () -> Unit,
    onOpenQuote: (Quote) -> Unit = {},
    size: CardSize,
    modifier: Modifier = Modifier,
) {
    val max = 20
    SummaryCard(stringResource(R.string.card_stocks), hazeState, modifier, action = stringResource(R.string.stocks_symbols), onAction = onSymbols) {
        when {
            quotes == null -> Text(stringResource(R.string.news_loading), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            quotes.isEmpty() -> Text(stringResource(R.string.stocks_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp)
            else -> {
                quotes.take(max).forEach { q ->
                    val up = q.changePercent >= 0
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpenQuote(q) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(q.name, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (!size.half) {
                            Text(String.format(Italian, "%,.2f", q.price), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
                        }
                        Text(
                            String.format(Italian, "%+.2f%%", q.changePercent),
                            fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                            color = if (up) CalendarTint.Salvia.colors().dot else CalendarTint.Borgogna.colors().dot,
                        )
                    }
                }
                if (size.tall || !size.half) {
                    Text(stringResource(R.string.stocks_delay), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

package eu.eurise.feature.today

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.model.Capture
import eu.eurise.core.data.weather.Sky
import eu.eurise.core.designsystem.component.LocalDockClearance
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.colors
import eu.eurise.core.links.CaptureStart
import eu.eurise.core.links.ContactSuggestions
import eu.eurise.core.links.rememberPickedPeople
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Hm = DateTimeFormatter.ofPattern("H:mm")
private const val PAGES = 4001
private const val CENTER = PAGES / 2
private val IT = Locale.ITALIAN

/**
 * L'Agenda, prima «Oggi» (tavole AgeGiorno e AgeScrivi, approvate il 16 novembre): il giorno come un'agenda, si scorre
 * ai giorni prima e dopo; in basso si scrive una cosa da fare, una nota o un appuntamento, con i contatti suggeriti.
 *
 * @param onOpenEvent apre un appuntamento (id dell'evento, inizio dell'occorrenza in millisecondi).
 * @param onCapture apre il + unico già scritto (per un appuntamento da confermare).
 */
@Composable
fun AgendaRoute(
    data: EuriseData,
    onOpenEvent: (Long, Long) -> Unit,
    onShowCalendar: () -> Unit,
    onShowTasks: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenWeather: () -> Unit,
    onCapture: (CaptureStart) -> Unit,
) {
    val context = LocalContext.current
    val viewModel = viewModel { AgendaViewModel(context, data) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        onPauseOrDispose { }
    }
    val forecast by viewModel.forecast.collectAsStateWithLifecycle()
    val travels by viewModel.travels.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = CENTER) { PAGES }
    val scope = rememberCoroutineScope()
    val shown = viewModel.today.plusDays((pager.currentPage - CENTER).toLong())
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            value = LocalDateTime.now()
        }
    }
    val clearance = LocalDockClearance.current

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            DayHeader(
                date = shown,
                today = viewModel.today,
                now = now,
                meteo = AgendaText.meteo(forecast, shown, now),
                onToday = { scope.launch { pager.animateScrollToPage(CENTER) } },
                onWeather = onOpenWeather,
            )
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1, key = { it }) { page ->
                val date = viewModel.today.plusDays((page - CENTER).toLong())
                val flow = remember(date) { viewModel.day(date) }
                val day by flow.collectAsStateWithLifecycle(AgendaDay(date))
                if (date == viewModel.today) LaunchedEffect(day.events) { viewModel.routesFor(day.events) }
                DayPage(
                    day = day,
                    now = now,
                    today = viewModel.today,
                    travels = travels,
                    bottomSpace = clearance + 84.dp,
                    onOpenEvent = { onOpenEvent(it.eventId, it.beginMillis) },
                    onSetDone = viewModel::setDone,
                    onShowCalendar = onShowCalendar,
                    onShowTasks = onShowTasks,
                    onOpenPerson = onOpenPerson,
                )
            }
        }
        WriteBar(
            onTask = { text, people -> viewModel.addTask(text, shown, people) },
            onNote = viewModel::addNote,
            isEvent = viewModel::isEvent,
            onEvent = onCapture,
            modifier = Modifier.align(Alignment.BottomCenter).imePadding().padding(start = 12.dp, end = 12.dp, bottom = clearance + 8.dp),
        )
    }
}

/**
 * L'intestazione della pagina come una vecchia agenda (tavola AgePagina): in alto il saluto e, piccoli, anno, settimana e
 * giorno dell'anno; sotto, a sinistra, il nome del giorno, il numero grande e il mese, larghi uguali; a destra meteo e
 * massima. Il numero è blu notte nei feriali, salvia nei prefestivi, amaranto nei festivi (anche il nome del giorno).
 */
@Composable
private fun DayHeader(
    date: LocalDate,
    today: LocalDate,
    now: LocalDateTime,
    meteo: AgendaMeteo?,
    onToday: () -> Unit,
    onWeather: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("eurise_launcher", android.content.Context.MODE_PRIVATE) }
    val name = remember { prefs.getString("user_name", "").orEmpty().trim() }
    // Il numero: arrotondato di base, il carattere della suite se scelto in Aspetto della Scrivania.
    val ours = remember { prefs.getBoolean("agenda_number_ours", false) }
    val greeting = when (date) {
        today -> (stringResource(
            when (AgendaText.greeting(now.toLocalTime())) {
                AgendaText.Greeting.Morning -> R.string.today_good_morning
                AgendaText.Greeting.Afternoon -> R.string.today_good_afternoon
                AgendaText.Greeting.Evening -> R.string.today_good_evening
            }
        ) + " " + name).trim()
        today.plusDays(1) -> stringResource(R.string.agenda_tomorrow)
        today.minusDays(1) -> stringResource(R.string.agenda_yesterday)
        else -> null
    }
    val dark = scheme.background.luminance() < 0.5f
    val kind = remember(date) { AgendaText.dayKind(date) }
    val tint = when (kind) {
        AgendaText.DayKind.Weekday -> if (dark) Color(0xFF9DB0DA) else Color(0xFF1F2A44)
        AgendaText.DayKind.PreHoliday -> if (dark) Color(0xFF9BC2AB) else Color(0xFF4F6B5A)
        AgendaText.DayKind.Holiday -> if (dark) Color(0xFFE29AA3) else Color(0xFF8C2F39)
    }
    val plain = scheme.onSurface.copy(alpha = 0.8f)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, IT).uppercase(IT)
    val month = date.month.getDisplayName(TextStyle.FULL_STANDALONE, IT).uppercase(IT)
    val full = date.dayOfWeek.getDisplayName(TextStyle.FULL, IT) + " " + date.dayOfMonth + " " + date.month.getDisplayName(TextStyle.FULL, IT) + " " + date.year
    val maxim = remember(date) { Maxims.forDay(date) }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (greeting != null) {
                    Text(greeting, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                } else {
                    Text(
                        stringResource(R.string.agenda_back_to_today), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.primary,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onToday).padding(vertical = 4.dp),
                    )
                }
                Text(
                    stringResource(R.string.agenda_year_line, date.year, AgendaText.week(date), AgendaText.dayOfYear(date)),
                    fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant, maxLines = 1,
                )
            }
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Column(
                    Modifier.width(DateColumn).clickable(enabled = date != today, onClick = onToday)
                        .semantics(mergeDescendants = true) { heading(); contentDescription = full },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SpreadText(weekday, if (kind == AgendaText.DayKind.Weekday) plain else tint)
                    DayNumber(date.dayOfMonth, ours, tint)
                    SpreadText(month, plain)
                }
                Box(Modifier.padding(start = 14.dp).width(1.dp).fillMaxHeight().background(scheme.outlineVariant.copy(alpha = 0.45f)))
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                    if (meteo != null) {
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onWeather),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(skyIcon(meteo.sky), null, tint = Color(0xFFA8742A), modifier = Modifier.size(24.dp))
                            Column {
                                Text(meteo.head, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (meteo.tail.isNotBlank()) Text(meteo.tail, fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(maxim.text, fontSize = 12.5.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Text(maxim.author, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant.copy(alpha = 0.35f)))
    }
}

/** Larghezza della colonna della data: nome del giorno, numero e mese la occupano tutta. */
private val DateColumn = 88.dp

/** Le lettere di [text] distribuite su tutta la larghezza della colonna, come nelle intestazioni delle agende. */
@Composable
private fun SpreadText(text: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        text.forEach { Text(it.toString(), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.ExtraBold, color = color) }
    }
}

/** Il numero del giorno, grande quanto serve perché due cifre riempiano [DateColumn]. */
@Composable
private fun DayNumber(day: Int, ours: Boolean, color: Color) {
    val family = if (ours) eu.eurise.core.designsystem.theme.PlusJakartaSans else RoundDigits
    val weight = if (ours) FontWeight.Light else FontWeight.Bold
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val size = remember(ours, density) {
        val probe = measurer.measure("00", androidx.compose.ui.text.TextStyle(fontFamily = family, fontWeight = weight, fontSize = 100.sp)).size.width
        val target = with(density) { DateColumn.toPx() }
        if (probe > 0) (100f * target / probe).sp else 72.sp
    }
    Text(
        day.toString(),
        style = androidx.compose.ui.text.TextStyle(
            fontFamily = family, fontWeight = weight, fontSize = size, lineHeight = size, color = color,
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
            ),
        ),
        maxLines = 1,
    )
}

/** Nunito Bold con le sole dieci cifre (2,6 KB, licenza SIL OFL 1.1): il numero «arrotondato» della pagina d'agenda. */
private val RoundDigits = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.nunito_digits_bold, FontWeight.Bold))

private fun skyIcon(sky: Sky): ImageVector = when (sky) {
    Sky.Clear, Sky.PartlyCloudy -> EuriseIcons.sun()
    Sky.Cloudy -> EuriseIcons.cloud()
    Sky.Fog -> EuriseIcons.fog()
    Sky.Drizzle, Sky.Rain, Sky.Showers -> EuriseIcons.rain()
    Sky.Snow -> EuriseIcons.snow()
    Sky.Storm -> EuriseIcons.storm()
}

@Composable
private fun DayPage(
    day: AgendaDay,
    now: LocalDateTime,
    today: LocalDate,
    travels: Map<String, Int>,
    bottomSpace: androidx.compose.ui.unit.Dp,
    onOpenEvent: (CalendarEvent) -> Unit,
    onSetDone: (eu.eurise.core.data.model.Task, Boolean) -> Unit,
    onShowCalendar: () -> Unit,
    onShowTasks: () -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "events-title") {
            SectionTitle(EuriseIcons.calendar(), scheme.primary, stringResource(R.string.agenda_events), if (day.events.isEmpty()) "" else day.events.size.toString())
        }
        item(key = "events") {
            when {
                !day.loaded -> Spacer(Modifier.height(44.dp))
                !day.calendarConnected -> Card(Modifier.clickable(onClick = onShowCalendar)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.today_connect_title), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.today_connect_message), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                    }
                }
                day.events.isEmpty() -> Text(
                    stringResource(R.string.agenda_no_events), fontSize = 13.5.sp, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                else -> Card {
                    day.events.forEachIndexed { i, e ->
                        if (i > 0) Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(scheme.outlineVariant.copy(alpha = 0.5f)))
                        EventRow(e, now, today, travels[e.location], onOpenEvent)
                    }
                }
            }
        }

        item(key = "tasks-title") {
            val total = day.open.size + day.done.size
            SectionTitle(
                EuriseIcons.task(), Color(0xFF4F6B5A), stringResource(R.string.agenda_tasks),
                if (total == 0) "" else stringResource(R.string.agenda_done_of, day.done.size, total),
                onClick = onShowTasks,
            )
        }
        item(key = "tasks") {
            if (day.loaded && day.open.isEmpty() && day.done.isEmpty()) {
                Text(stringResource(R.string.agenda_no_tasks), fontSize = 13.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            } else if (day.loaded) {
                Card(Modifier.padding(vertical = 0.dp)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        day.open.forEach { TaskLine(it, false, today, onSetDone) }
                        day.done.forEach { TaskLine(it, true, today, onSetDone) }
                    }
                }
            }
        }

        if (day.people.isNotEmpty()) {
            item(key = "people-title") {
                SectionTitle(EuriseIcons.person(), Color(0xFF85672B), stringResource(R.string.agenda_people), stringResource(R.string.agenda_people_from))
            }
            item(key = "people") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(day.people, key = { it.lookupKey }) { p -> PersonCard(p, onOpenPerson) }
                }
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, tint: Color, title: String, trailing: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
        if (trailing.isNotEmpty()) Text(trailing, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EventRow(e: CalendarEvent, now: LocalDateTime, today: LocalDate, minutes: Int?, onOpen: (CalendarEvent) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val tint = CalendarTint.nearest(e.color).colors()
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(46.dp)) {
            if (e.allDay) {
                Text(stringResource(R.string.today_all_day), fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(e.start.toLocalTime().format(Hm), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(e.end.toLocalTime().format(Hm), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            }
        }
        Box(Modifier.width(4.dp).heightIn(min = 38.dp).clip(RoundedCornerShape(2.dp)).background(tint.dot))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val past = !e.allDay && e.end.isBefore(now)
            Text(
                e.title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (past) scheme.onSurfaceVariant else scheme.onSurface,
            )
            if (e.location.isNotBlank()) Text(e.location, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (minutes != null && e.start.toLocalDate() == today && e.start.isAfter(now)) {
                Row(
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(15.dp)).background(scheme.primaryContainer)
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(e.location))))
                            }
                        }
                        .heightIn(min = 30.dp).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(EuriseIcons.car(), null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.agenda_leave_at, AgendaText.leaveAt(e.start, minutes).format(Hm), minutes),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskLine(item: AgendaTask, done: Boolean, today: LocalDate, onSetDone: (eu.eurise.core.data.model.Task, Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (title, mentioned) = remember(item.task.title) { AgendaText.splitPeople(item.task.title) }
    val people = (mentioned + item.people).distinctBy { it.lowercase() }
    val late = !done && item.task.due != null && item.task.due!!.isBefore(today)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onSetDone(item.task, !done) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(19.dp).clip(RoundedCornerShape(6.dp))
                .background(if (done) scheme.primary else Color.Transparent)
                .border(2.dp, if (done) scheme.primary else scheme.onSurfaceVariant, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { if (done) Icon(EuriseIcons.check(), null, tint = scheme.onPrimary, modifier = Modifier.size(13.dp)) }
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 14.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (done) scheme.onSurfaceVariant else scheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            val sub = listOfNotNull(
                item.task.time?.format(Hm),
                if (late) stringResource(R.string.agenda_late) else null,
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = if (late) scheme.error else scheme.onSurfaceVariant)
        }
        people.take(2).forEach { name ->
            Text(
                "@$name", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF85672B), maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFEFE7D4)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private val PersonTints = listOf(Color(0xFF4F6B5A), Color(0xFFA8742A), Color(0xFF5B6FA8), Color(0xFF8C2F39))

@Composable
private fun PersonCard(p: DayPerson, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val initials = p.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Column(
        Modifier.width(116.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { onOpen(p.lookupKey) }.padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(PersonTints[(p.name.hashCode() and 0x7fffffff) % PersonTints.size]), contentAlignment = Alignment.Center) {
            Text(initials, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(p.why, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val phone = p.phone
        if (phone != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoundAction(EuriseIcons.phone(), stringResource(R.string.agenda_call, p.name), Color(0xFFE3EAE5), Color(0xFF4F6B5A)) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)))) }
                }
                RoundAction(EuriseIcons.chat(), stringResource(R.string.agenda_write, p.name), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)))) }
                }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp).clip(CircleShape).background(bg)) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(15.dp))
    }
}

/**
 * Il campo in basso: si scrive come si parla. «+» crea una cosa da fare (o, se la frase è un appuntamento, apre il +
 * unico per confermarlo); il foglio crea una nota. Con «@» compaiono i contatti del telefono.
 */
@Composable
private fun WriteBar(
    onTask: (String, List<eu.eurise.core.data.model.LinkRef>) -> Unit,
    onNote: (String, List<eu.eurise.core.data.model.LinkRef>) -> Unit,
    isEvent: (String) -> Boolean,
    onEvent: (CaptureStart) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val picked = rememberPickedPeople()
    val focus = LocalFocusManager.current
    val text = field.text
    fun clear() {
        field = TextFieldValue("")
        focus.clearFocus()
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ContactSuggestions(
            text = text,
            caret = field.selection.end,
            onText = { t, at -> field = TextFieldValue(t, TextRange(at)) },
            picked = picked,
            keepAt = true,
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = scheme.surface.copy(alpha = 0.96f),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val placeholder = stringResource(R.string.agenda_write_placeholder)
                BasicTextField(
                    value = field,
                    onValueChange = { field = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 4,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(placeholder, fontSize = 14.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    },
                )
                val enabled = text.isNotBlank()
                IconButton(
                    onClick = { onNote(text, picked.refsIn(text)); clear() },
                    enabled = enabled,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(scheme.surfaceVariant),
                ) { Icon(EuriseIcons.note(), stringResource(R.string.today_capture_note), tint = scheme.onSurface, modifier = Modifier.size(18.dp)) }
                IconButton(
                    onClick = {
                        if (isEvent(text)) onEvent(CaptureStart(text, picked.byFirstName(text), Capture.Target.Event))
                        else onTask(text, picked.refsIn(text))
                        clear()
                    },
                    enabled = enabled,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(if (enabled) scheme.primary else scheme.primary.copy(alpha = 0.5f)),
                ) { Icon(EuriseIcons.plus(), stringResource(R.string.agenda_add), tint = scheme.onPrimary, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

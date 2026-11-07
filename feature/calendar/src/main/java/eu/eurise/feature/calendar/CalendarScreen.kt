package eu.eurise.feature.calendar

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.data.model.Task
import eu.eurise.core.designsystem.component.GlassSegmented
import eu.eurise.core.designsystem.component.LocalDockClearance
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.EuriseThemeExtras
import eu.eurise.core.designsystem.theme.colors
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Calendario: testata fissa e satinata (mese, viste, giorni) con il contenuto che le scorre dietro.
 *
 * @param onOpenEvent apre un appuntamento: id dell'evento e inizio dell'occorrenza (millisecondi).
 * @param onNewEvent crea un appuntamento all'ora indicata (millisecondi).
 */
@Composable
fun CalendarRoute(
    calendar: CalendarRepository,
    tasks: TaskRepository,
    onOpenEvent: (eventId: Long, beginMillis: Long) -> Unit,
    onNewEvent: (startMillis: Long) -> Unit,
    openDay: LocalDate? = null,
    onDates: () -> Unit = {},
    onMedicines: () -> Unit = {},
) {
    val viewModel = viewModel { CalendarViewModel(calendar, tasks) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Giorno chiesto da fuori (per esempio dalla scheda Calendario della Scrivania): vista Giorno.
    LaunchedEffect(openDay) { openDay?.let { viewModel.openDay(it, switchToDay = true) } }
    val context = LocalContext.current
    var refusals by rememberSaveable { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.all { it }
        if (!granted) refusals++
        viewModel.onPermissionResult(granted)
    }
    // Al ritorno dalle impostazioni di sistema si ricontrolla il permesso.
    LifecycleResumeEffect(Unit) {
        viewModel.onPermissionResult(false)
        onPauseOrDispose { }
    }
    val zone = ZoneId.systemDefault()
    CalendarScreen(
        state = state,
        onViewChange = viewModel::setView,
        onToday = viewModel::goToToday,
        onDates = onDates,
        onMedicines = onMedicines,
        onStep = viewModel::step,
        onOpenDay = viewModel::openDay,
        onToggleTask = viewModel::setTaskDone,
        onEventClick = { onOpenEvent(it.eventId, it.beginMillis) },
        onSlotClick = { onNewEvent(it.atZone(zone).toInstant().toEpochMilli()) },
        showSettingsLink = refusals > 0,
        onRequestPermission = {
            launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        },
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
    )
}

@Composable
internal fun CalendarScreen(
    state: CalendarState,
    onViewChange: (CalendarView) -> Unit,
    onToday: () -> Unit,
    onStep: (Boolean) -> Unit,
    onOpenDay: (LocalDate, Boolean) -> Unit,
    onToggleTask: (Task, Boolean) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onSlotClick: (LocalDateTime) -> Unit,
    showSettingsLink: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDates: () -> Unit = {},
    onMedicines: () -> Unit = {},
) {
    val headerHaze = remember { HazeState() }
    val density = LocalDensity.current
    val clearance = LocalDockClearance.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }
    val timeline = state.view in setOf(CalendarView.Day, CalendarView.ThreeDays, CalendarView.Week)
    val swipeThreshold = with(density) { 72.dp.toPx() }
    var sheet by remember { mutableStateOf<EventsSheet?>(null) }
    sheet?.let { content ->
        EventsListSheet(
            content = content,
            now = now,
            onDismiss = { sheet = null },
            onEventClick = { sheet = null; onEventClick(it) },
        )
    }

    // La testata è piena e ferma: il calendario scorre solo sotto di lei, così mese, viste e giorni
    // restano sempre nitidi, qualunque cosa ci sia in agenda.
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 12.dp, bottom = 10.dp),
        ) {
            TitleRow(state.anchor, onStep, onToday, onDates, onMedicines)
            Spacer(Modifier.height(14.dp))
            GlassSegmented(
                options = listOf(
                    stringResource(R.string.cal_view_agenda),
                    stringResource(R.string.cal_view_day),
                    stringResource(R.string.cal_view_three),
                    stringResource(R.string.cal_view_week),
                    stringResource(R.string.cal_view_month),
                ),
                selected = state.view.ordinal,
                onSelect = { onViewChange(CalendarView.entries[it]) },
                hazeState = headerHaze,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (timeline && state.hasPermission) {
                Spacer(Modifier.height(10.dp))
                DayHeads(state.days, state.today, onClick = { onOpenDay(it, state.view != CalendarView.Day) })
                AllDayRow(
                    state = state,
                    now = now,
                    onToggleTask = onToggleTask,
                    onOpenDay = { onOpenDay(it, true) },
                    onNight = { day, events -> sheet = EventsSheet.Night(day, events) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Contenuto: si cambia periodo anche scorrendo di lato.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .hazeSource(headerHaze)
                .pointerInput(state.view) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total > swipeThreshold) onStep(false) else if (total < -swipeThreshold) onStep(true)
                        },
                    ) { _, dx -> total += dx }
                },
        ) {
            when {
                !state.hasPermission -> PermissionCard(
                    showSettingsLink = showSettingsLink,
                    onAllow = onRequestPermission,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.padding(top = 16.dp),
                )
                timeline -> {
                    val scroll = rememberScrollState()
                    val hour = hourPx()
                    LaunchedEffect(state.view, state.from, state.loaded) {
                        scroll.scrollTo((initialScrollHour(state.days, state.events) * hour).toInt())
                    }
                    TimelineGrid(
                        days = state.days,
                        events = state.events,
                        today = state.today,
                        now = now,
                        scrollState = scroll,
                        topPadding = 10.dp,
                        bottomPadding = clearance,
                        onGroupClick = { g -> if (g.events.size == 1) onEventClick(g.first) else sheet = EventsSheet.Group(g) },
                        onSlotClick = onSlotClick,
                    )
                }
                state.view == CalendarView.Month -> MonthView(
                    state = state,
                    now = now,
                    topPadding = 0.dp,
                    bottomPadding = clearance,
                    onSelectDay = { onOpenDay(it, false) },
                    onEventClick = onEventClick,
                    onToggleTask = onToggleTask,
                )
                else -> AgendaView(
                    state = state,
                    now = now,
                    topPadding = 0.dp,
                    bottomPadding = clearance,
                    onEventClick = onEventClick,
                    onToggleTask = onToggleTask,
                )
            }
        }
    }
}

@Composable
private fun TitleRow(anchor: LocalDate, onStep: (Boolean) -> Unit, onToday: () -> Unit, onDates: () -> Unit, onMedicines: () -> Unit) {
    val month = anchor.format(DateTimeFormatter.ofPattern("LLLL", Locale.ITALIAN)).replaceFirstChar { it.titlecase(Locale.ITALIAN) }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        // Frecce piccole, area di tocco piena: servono anche a TalkBack, che non può scorrere.
        Chevron(EuriseIcons.chevronLeft(), stringResource(R.string.cal_previous)) { onStep(false) }
        Text(
            buildString { append(month); append(' ') },
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text("${anchor.year}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Chevron(EuriseIcons.chevronRight(), stringResource(R.string.cal_next)) { onStep(true) }
        Spacer(Modifier.weight(1f))
        // Date importanti e Farmaci: due moduli del Calendario.
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.dates_title), onClick = onDates), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.star(), stringResource(R.string.dates_title), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.meds_title), onClick = onMedicines), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.heart(), stringResource(R.string.meds_title), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            onClick = onToday,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                stringResource(R.string.cal_today),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun Chevron(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 32.dp, height = 44.dp)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

private val WeekdayShort = DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN)

@Composable
private fun DayHeads(days: List<LocalDate>, today: LocalDate, onClick: (LocalDate) -> Unit) {
    val compact = days.size > 3
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(Gutter))
        days.forEach { day ->
            val isToday = day == today
            val wd = day.format(WeekdayShort).trimEnd('.').replaceFirstChar { it.titlecase(Locale.ITALIAN) }
            val content: @Composable () -> Unit = {
                Text(
                    wd,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${day.dayOfMonth}",
                        fontSize = if (compact) 13.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clickable { onClick(day) },
                contentAlignment = Alignment.Center,
            ) {
                if (compact) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
                }
            }
        }
    }
}

/**
 * Riga sotto i giorni: eventi di tutto il giorno e attività con scadenza. Con un solo giorno le attività
 * si vedono per intero; con più giorni compare un'etichetta compatta ("2 da fare") che apre quel giorno.
 */
@Composable
private fun AllDayRow(
    state: CalendarState,
    now: LocalDateTime,
    onToggleTask: (Task, Boolean) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onNight: (LocalDate, List<CalendarEvent>) -> Unit,
) {
    val days = state.days
    val anything = state.events.any { it.allDay || days.any { d -> it.continuesInto(d) } } || state.tasks.isNotEmpty()
    if (!anything) return
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.Top) {
        Spacer(Modifier.width(Gutter))
        days.forEach { day ->
            Column(Modifier.weight(1f).padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val night = state.events.filter { it.continuesInto(day) }
                if (night.isNotEmpty()) {
                    val running = day == now.toLocalDate() && night.any { it.end.isAfter(now) }
                    NightChip(night.size, running) { onNight(day, night) }
                }
                state.events.filter { it.allDay && it.occursOn(day) }.forEach { e ->
                    val tint = CalendarTint.nearest(e.color).colors()
                    Text(
                        e.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().background(tint.fill, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                val dayTasks = state.tasks.filter { it.due == day }
                if (dayTasks.isNotEmpty()) {
                    if (days.size == 1) {
                        dayTasks.forEach { t -> TaskLine(t, state.today, onToggleTask) }
                    } else {
                        val open = dayTasks.filter { !it.done }
                        val late = open.isNotEmpty() && day.isBefore(state.today)
                        if (open.isNotEmpty()) TasksChip(open.size, late) { onOpenDay(day) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskLine(task: Task, today: LocalDate, onToggle: (Task, Boolean) -> Unit) {
    val late = !task.done && task.due != null && task.due!!.isBefore(today)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 32.dp).clickable { onToggle(task, !task.done) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CheckBox(task.done, late)
        Text(
            task.title,
            fontSize = 13.sp,
            color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (task.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun CheckBox(checked: Boolean, late: Boolean, size: androidx.compose.ui.unit.Dp = 14.dp) {
    val color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(size)
            .border(1.6.dp, color, RoundedCornerShape(3.dp))
            .then(if (checked) Modifier.background(color, RoundedCornerShape(3.dp)) else Modifier),
    )
}

@Composable
private fun TasksChip(count: Int, late: Boolean, onClick: () -> Unit) {
    val label = if (late) stringResource(R.string.cal_tasks_count_late, count) else stringResource(R.string.cal_tasks_count, count)
    Row(
        Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (late) Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        Text(stringResource(R.string.cal_tasks_count, count), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun PermissionCard(
    showSettingsLink: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(EuriseIcons.calendar(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.cal_permission_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.cal_permission_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAllow) { Text(stringResource(R.string.cal_permission_allow)) }
        if (showSettingsLink) {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.cal_permission_settings)) }
        }
    }
}

@Composable
private fun NightChip(count: Int, running: Boolean, onClick: () -> Unit) {
    // Stesso aspetto satinato della capsula attiva nella pillola delle viste.
    val label = stringResource(if (running) R.string.cal_from_night_now else R.string.cal_from_night, count)
    val color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .shadow(2.dp, CircleShape)
            .background(EuriseThemeExtras.colors.glassSelected, CircleShape)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (running) Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (running) FontWeight.Bold else FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Contenuto del foglio dal basso: i turni dalla notte di un giorno, o le persone di un turno raggruppato. */
internal sealed interface EventsSheet {
    val events: List<CalendarEvent>

    data class Night(val day: LocalDate, override val events: List<CalendarEvent>) : EventsSheet
    data class Group(val group: EventGroup) : EventsSheet {
        override val events: List<CalendarEvent> get() = group.events
    }
}

private val SpanFormat = DateTimeFormatter.ofPattern("EEE H:mm", Locale.ITALIAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventsListSheet(
    content: EventsSheet,
    now: LocalDateTime,
    onDismiss: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
) {
    val first = content.events.first()
    val running = content.events.any { !it.start.isAfter(now) && it.end.isAfter(now) }
    val title = when (content) {
        is EventsSheet.Night -> stringResource(R.string.cal_night_title)
        is EventsSheet.Group -> content.group.shiftLabel ?: "${first.start.toLocalTime().format(TimeFormat)}–${first.end.toLocalTime().format(TimeFormat)}"
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (running) {
                    Text(
                        stringResource(R.string.cal_running_until, first.end.toLocalTime().format(TimeFormat)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (content is EventsSheet.Night) {
                Text(stringResource(R.string.cal_night_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content.events.forEach { e ->
                val tint = CalendarTint.nearest(e.color).colors()
                val name = if (content is EventsSheet.Group && content.group.shiftLabel != null) splitShift(e.title).first else e.title
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clickable { onEventClick(e) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.width(4.dp).height(36.dp).background(tint.dot, RoundedCornerShape(2.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        val span = stringResource(R.string.cal_span, e.start.format(SpanFormat), e.end.format(SpanFormat))
                        val sub = listOfNotNull(span, e.location.takeIf { it.isNotBlank() }).joinToString(" · ")
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

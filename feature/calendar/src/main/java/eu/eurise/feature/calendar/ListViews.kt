package eu.eurise.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.model.Task
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.colors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayLong = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

/** Elenco per giorni dal giorno di riferimento in avanti; i giorni vuoti non compaiono. */
@Composable
internal fun AgendaView(
    state: CalendarState,
    now: LocalDateTime,
    topPadding: Dp,
    bottomPadding: Dp,
    onEventClick: (CalendarEvent) -> Unit,
    onToggleTask: (Task, Boolean) -> Unit,
) {
    val days = state.days.filter { d -> state.events.any { it.occursOn(d) } || state.tasks.any { it.due == d } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 4.dp, bottom = bottomPadding + 16.dp),
    ) {
        if (state.loaded && days.isEmpty()) {
            item {
                EmptyState(
                    icon = EuriseIcons.calendar(),
                    title = stringResource(R.string.cal_agenda_empty_title),
                    message = stringResource(R.string.cal_agenda_empty_message),
                )
            }
        }
        days.forEach { day ->
            item(key = "h$day") { DayHeader(day, state.today) }
            dayItems(day, state, now, onEventClick, onToggleTask)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dayItems(
    day: LocalDate,
    state: CalendarState,
    now: LocalDateTime,
    onEventClick: (CalendarEvent) -> Unit,
    onToggleTask: (Task, Boolean) -> Unit,
) {
    val events = state.events.filter { it.occursOn(day) }.sortedWith(compareBy({ !it.allDay }, { it.start }))
    items(events, key = { "e${it.eventId}-${it.beginMillis}-$day" }) { e -> EventRow(e, day, now, onEventClick) }
    items(state.tasks.filter { it.due == day }, key = { "t${it.id}" }) { t -> TaskRow(t, state.today, onToggleTask) }
}

@Composable
internal fun DayHeader(day: LocalDate, today: LocalDate, modifier: Modifier = Modifier) {
    val label = when (day) {
        today -> stringResource(R.string.cal_label_today)
        today.plusDays(1) -> stringResource(R.string.cal_label_tomorrow)
        else -> day.format(DateTimeFormatter.ofPattern("EEEE", Locale.ITALIAN)).replaceFirstChar { it.titlecase(Locale.ITALIAN) }
    }
    val sub = if (day == today || day == today.plusDays(1)) day.format(DayLong) else day.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN))
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp).semantics { heading() },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label.uppercase(Locale.ITALIAN),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EventRow(event: CalendarEvent, day: LocalDate, now: LocalDateTime, onClick: (CalendarEvent) -> Unit) {
    val tint = CalendarTint.nearest(event.color).colors()
    val past = !event.end.isAfter(now)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick(event) }
            .alpha(if (past) 0.5f else 1f)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(48.dp)) {
            if (event.allDay) {
                Text(stringResource(R.string.cal_all_day), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
            } else {
                val startsToday = event.start.toLocalDate() == day
                Text(
                    if (startsToday) event.start.toLocalTime().format(TimeFormat) else stringResource(R.string.cal_night_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(event.end.toLocalTime().format(TimeFormat), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.width(4.dp).heightIn(min = 36.dp).background(tint.dot, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(event.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                event.location.takeIf { it.isNotBlank() },
                event.calendarName.takeIf { it.isNotBlank() },
                if (event.recurring) stringResource(R.string.cal_repeats) else null,
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun TaskRow(task: Task, today: LocalDate, onToggle: (Task, Boolean) -> Unit) {
    val late = !task.done && task.due != null && task.due!!.isBefore(today)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(task, !task.done) }
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { CheckBox(task.done, late, 18.dp) }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                task.title,
                fontSize = 15.sp,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
            if (late) Text(stringResource(R.string.cal_overdue), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Mese a griglia (settimane da lunedì) con l'elenco del giorno selezionato sotto. */
@Composable
internal fun MonthView(
    state: CalendarState,
    now: LocalDateTime,
    topPadding: Dp,
    bottomPadding: Dp,
    onSelectDay: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onToggleTask: (Task, Boolean) -> Unit,
) {
    val month = state.anchor.month
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = topPadding + 8.dp, bottom = bottomPadding + 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            listOf("L", "M", "M", "G", "V", "S", "D").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        state.days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                week.forEach { day ->
                    val inMonth = day.month == month
                    val isToday = day == state.today
                    val selected = day == state.anchor
                    val holiday = day.dayOfWeek == DayOfWeek.SUNDAY
                    val dots = state.events.filter { it.occursOn(day) }.take(3)
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clickable { onSelectDay(day) }
                            .alpha(if (inMonth) 1f else 0.45f)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val circle = when {
                            isToday -> MaterialTheme.colorScheme.primary
                            selected -> MaterialTheme.colorScheme.primaryContainer
                            else -> null
                        }
                        Box(
                            Modifier.size(32.dp).then(if (circle != null) Modifier.background(circle, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${day.dayOfMonth}",
                                fontSize = 14.sp,
                                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isToday -> MaterialTheme.colorScheme.onPrimary
                                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    holiday -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            dots.forEach { e ->
                                Box(Modifier.size(5.dp).background(CalendarTint.nearest(e.color).colors().dot, CircleShape))
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        DayHeader(state.anchor, state.today)
        val events = state.events.filter { it.occursOn(state.anchor) }.sortedWith(compareBy({ !it.allDay }, { it.start }))
        val tasks = state.tasks.filter { it.due == state.anchor }
        if (events.isEmpty() && tasks.isEmpty()) {
            Text(
                stringResource(R.string.cal_day_empty),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        events.forEach { EventRow(it, state.anchor, now, onEventClick) }
        tasks.forEach { TaskRow(it, state.today, onToggleTask) }
    }
}

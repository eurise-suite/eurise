package eu.eurise.feature.calendar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.EuriseThemeExtras
import eu.eurise.core.designsystem.theme.colors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal val HourHeight = 56.dp
internal val Gutter = 28.dp
internal val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

/**
 * Turni con lo stesso orario e lo stesso calendario, mostrati in un unico blocco. Un gruppo di un solo
 * evento è un appuntamento normale.
 */
internal data class EventGroup(val events: List<CalendarEvent>) {
    val first: CalendarEvent get() = events.first()
    val start: LocalDateTime get() = first.start
    val end: LocalDateTime get() = first.end

    /** Titoli nello schema "Nome · Turno": il turno comune, se tutti lo condividono. */
    val shiftLabel: String?
        get() {
            val parts = events.map { splitShift(it.title) }
            val shift = parts.firstOrNull()?.second ?: return null
            return if (parts.all { it.second == shift }) shift else null
        }

    /** Le righe del blocco: i nomi se c'è un turno comune, altrimenti i titoli interi. */
    val lines: List<String>
        get() = if (shiftLabel != null) events.map { splitShift(it.title).first } else events.map { it.title }
}

private val ShiftSeparators = listOf(" · ", " – ", " — ", " - ")

/** Divide "Panella Ilaria · Notte 20-8" in nome e turno; senza separatore il turno è null. */
internal fun splitShift(title: String): Pair<String, String?> {
    val sep = ShiftSeparators.map { it to title.indexOf(it) }.filter { it.second > 0 }.minByOrNull { it.second }
        ?: return title to null
    return title.substring(0, sep.second).trim() to title.substring(sep.second + sep.first.length).trim().ifEmpty { null }
}

/** Vero se l'evento a orario è iniziato prima di [day] (per esempio un turno di notte iniziato la sera prima). */
internal fun CalendarEvent.continuesInto(day: LocalDate): Boolean =
    !allDay && start.toLocalDate().isBefore(day) && occursOn(day)

/** Un gruppo ritagliato su un giorno, con la colonna che occupa quando si sovrappone ad altri. */
private data class Placed(val group: EventGroup, val startMin: Int, val endMin: Int, val column: Int, val columns: Int)

/**
 * Dispone gli eventi di un giorno. I turni iniziati il giorno prima non entrano nella griglia (li riassume
 * l'etichetta "Dalla notte"); quelli con lo stesso orario e calendario diventano un unico blocco; i blocchi
 * che si sovrappongono si dividono la larghezza in colonne.
 */
private fun layoutDay(day: LocalDate, events: List<CalendarEvent>): List<Placed> {
    val dayStart = day.atStartOfDay()
    val endOfDay = dayStart.plusDays(1)
    val segments = events
        .filter { !it.allDay && it.occursOn(day) && !it.continuesInto(day) }
        .groupBy { Triple(it.start, it.end, it.calendarId) }
        .values
        .map { list ->
            val g = EventGroup(list.sortedBy { it.title.lowercase() })
            val s = g.start.toLocalTime().toSecondOfDay() / 60
            val en = if (!g.end.isBefore(endOfDay)) 24 * 60 else g.end.toLocalTime().toSecondOfDay() / 60
            Triple(g, s, maxOf(en, s + 20))
        }
        .sortedWith(compareBy({ it.second }, { -it.third }))

    val result = mutableListOf<Placed>()
    val cluster = mutableListOf<Triple<EventGroup, Int, Int>>()
    val columnEnds = mutableListOf<Int>()
    val columnOf = mutableMapOf<EventGroup, Int>()
    var clusterEnd = -1

    fun flush() {
        val count = columnEnds.size.coerceAtLeast(1)
        cluster.forEach { (g, s, en) -> result += Placed(g, s, en, columnOf.getValue(g), count) }
        cluster.clear(); columnEnds.clear(); columnOf.clear(); clusterEnd = -1
    }

    for (seg in segments) {
        val (g, s, en) = seg
        if (cluster.isNotEmpty() && s >= clusterEnd) flush()
        val free = columnEnds.indexOfFirst { it <= s }
        val column = if (free >= 0) free.also { columnEnds[it] = en } else columnEnds.size.also { columnEnds += en }
        columnOf[g] = column
        cluster += seg
        clusterEnd = maxOf(clusterEnd, en)
    }
    flush()
    return result
}

/**
 * Griglia oraria per uno, tre o sette giorni. Il contenuto parte sotto la testata ([topPadding]) e le
 * scorre dietro; gli impegni già finiti sono attenuati, oggi e il weekend hanno un velo leggero.
 */
@Composable
internal fun TimelineGrid(
    days: List<LocalDate>,
    events: List<CalendarEvent>,
    today: LocalDate,
    now: LocalDateTime,
    scrollState: ScrollState,
    topPadding: Dp,
    bottomPadding: Dp,
    onGroupClick: (EventGroup) -> Unit,
    onSlotClick: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = EuriseThemeExtras.colors
    val compact = days.size > 3
    val gridColor = extra.grid
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = topPadding, bottom = bottomPadding),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            // Ore a lato: solo il numero, per lasciare spazio alle colonne.
            Column(Modifier.width(Gutter)) {
                for (h in 0 until 24) {
                    Box(Modifier.height(HourHeight)) {
                        if (h > 0) {
                            Text(
                                "$h",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.offset(y = (-7).dp),
                            )
                        }
                    }
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(HourHeight * 24)
                    .drawBehind {
                        val step = HourHeight.toPx()
                        for (h in 1 until 24) {
                            drawLine(gridColor, Offset(0f, h * step), Offset(size.width, h * step), 1.dp.toPx())
                        }
                    },
            ) {
                days.forEachIndexed { index, day ->
                    val veil = when {
                        day == today -> extra.todayVeil
                        day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY -> extra.weekendVeil
                        else -> null
                    }
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (veil != null) Modifier.background(veil) else Modifier)
                            .drawBehind { drawLine(gridColor, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                            .pointerInput(day) {
                                detectTapGestures { offset ->
                                    val hour = (offset.y / HourHeight.toPx()).toInt().coerceIn(0, 23)
                                    onSlotClick(day.atTime(hour, 0))
                                }
                            },
                    ) {
                        val colWidth = maxWidth
                        layoutDay(day, events).forEach { p ->
                            val top = HourHeight * (p.startMin / 60f)
                            val height = HourHeight * ((p.endMin - p.startMin) / 60f) - 3.dp
                            val width = (colWidth - 4.dp) / p.columns - 2.dp
                            val x = 2.dp + (colWidth - 4.dp) / p.columns * p.column
                            val past = !p.group.end.isAfter(now)
                            EventBlock(
                                group = p.group,
                                compact = compact,
                                short = height < 44.dp,
                                modifier = Modifier
                                    .offset(x = x, y = top + 1.dp)
                                    .size(width, height)
                                    .alpha(if (past) 0.5f else 1f),
                                onClick = { onGroupClick(p.group) },
                            )
                        }
                        if (day == today) {
                            val label = stringResource(R.string.cal_now, now.toLocalTime().format(TimeFormat))
                            val y = HourHeight * (now.toLocalTime().toSecondOfDay() / 3600f)
                            NowLine(Modifier.offset(y = y - 1.dp).semantics { contentDescription = label })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EventBlock(
    group: EventGroup,
    compact: Boolean,
    short: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val event = group.first
    val tint = CalendarTint.nearest(event.color).colors()
    val shape = RoundedCornerShape(8.dp)
    val time = event.start.toLocalTime().format(TimeFormat)
    val endTime = event.end.toLocalTime().format(TimeFormat)
    val label = group.shiftLabel
    Column(
        modifier
            .background(tint.fill, shape)
            .border(1.dp, tint.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 4.dp else 7.dp, vertical = if (compact) 3.dp else 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (label != null || group.events.size > 1) {
            // Turno raggruppato: tipo di turno e numero di persone, poi i nomi uno per riga.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label ?: "$time–$endTime",
                    fontSize = if (compact) 10.5.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (group.events.size > 1) {
                    Text("${group.events.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint.content.copy(alpha = 0.8f))
                }
            }
            if (!compact && label != null && !short) {
                Text("$time–$endTime", fontSize = 11.sp, color = tint.content.copy(alpha = 0.85f), maxLines = 1)
            }
            group.lines.forEach { line ->
                Text(
                    line,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tint.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (short && !compact) {
            Text(
                "${event.title}  $time",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = tint.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                event.title.ifBlank { "—" },
                fontSize = if (compact) 11.sp else 12.5.sp,
                lineHeight = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                color = tint.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                val sub = listOfNotNull("$time–$endTime", event.location.takeIf { it.isNotBlank() }).joinToString(" · ")
                Text(sub, fontSize = 11.5.sp, color = tint.content.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NowLine(modifier: Modifier = Modifier) {
    val color = EuriseThemeExtras.colors.now
    Box(modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(color))
        Box(Modifier.offset(x = (-4).dp).size(10.dp).background(color, CircleShape))
    }
}

/**
 * Ora da cui parte la griglia: le 7, o prima se c'è un appuntamento più presto. I turni iniziati la sera
 * prima non contano: li riassume già l'etichetta "Dalla notte".
 */
internal fun initialScrollHour(days: List<LocalDate>, events: List<CalendarEvent>): Int {
    val earliest = events
        .filter { e -> !e.allDay && days.any { d -> e.occursOn(d) && !e.continuesInto(d) } }
        .minOfOrNull { it.start.hour }
    return minOf(7, earliest ?: 7).coerceAtLeast(0)
}

@Composable
internal fun hourPx(): Float = with(LocalDensity.current) { HourHeight.toPx() }


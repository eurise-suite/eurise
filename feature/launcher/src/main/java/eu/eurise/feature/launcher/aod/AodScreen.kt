package eu.eurise.feature.launcher.aod

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.home.BubbleText
import eu.eurise.feature.launcher.home.nextEvent
import eu.eurise.feature.launcher.home.rememberNextAlarm
import eu.eurise.feature.launcher.notifications.NotificationStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val Bright = Color(0xFFECE9E3)
private val Soft = Color(0xFFA4A9B2)
private val Azzurro = Color(0xFFA9C1F2)
private val Salvia = Color(0xFF93BBA0)
private val Ottone = Color(0xFFD2B06A)
private val Borgogna = Color(0xFFE8939B)
private val Dim = Color(0xFF8A8F98)

/** Tempo entro cui un tocco successivo conta come doppio o triplo. */
private const val TAP_WINDOW_MS = 320L

/**
 * Contenuto del display a riposo. A telefono bloccato non compaiono mai testi di messaggi, mittenti, codici o nomi
 * di farmaci. Ogni minuto si sposta di pochi punti per non segnare lo schermo.
 */
@Composable
fun AodScreen(
    data: EuriseData,
    bubbles: List<AodBubble>,
    reduced: Boolean,
    dark: Boolean,
    seconds: Int?,
    timerKey: Int,
    onTimeout: () -> Unit,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onTripleTap: () -> Unit,
) {
    val timeout by rememberUpdatedState(onTimeout)
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val tripleTap by rememberUpdatedState(onTripleTap)
    LaunchedEffect(timerKey, seconds, dark) {
        if (seconds != null && !dark) {
            delay(seconds * 1000L)
            timeout()
        }
    }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var shiftX by remember { mutableIntStateOf(0) }
    var shiftY by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            val next = LocalDateTime.now()
            if (next.minute != now.minute) {
                shiftX = Random.nextInt(-6, 7)
                shiftY = Random.nextInt(-8, 9)
            }
            now = next
        }
    }
    val today = LocalDate.now()
    val tasks by remember(today) { data.tasks.observeDueBy(today) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val eventsFlow = remember(today) {
        if (data.calendar.hasPermission()) data.calendar.observeEvents(today, today.plusDays(1)) else flowOf(emptyList())
    }
    val events by eventsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val alarm = rememberNextAlarm()
    val messages by NotificationStore.messages.collectAsStateWithLifecycle()
    val all by NotificationStore.all.collectAsStateWithLifecycle()
    val playing by NotificationStore.nowPlaying.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pending = remember { arrayOfNulls<Job>(1) }
    var taps by remember { mutableIntStateOf(0) }

    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectTapGestures {
                tap()
                taps++
                pending[0]?.cancel()
                pending[0] = scope.launch {
                    delay(TAP_WINDOW_MS)
                    when {
                        taps == 2 -> doubleTap()
                        taps >= 3 -> tripleTap()
                    }
                    taps = 0
                }
            }
        },
    ) {
        if (dark) return@Box
        val locale = Locale.getDefault()
        val date = now.format(DateTimeFormatter.ofPattern(if (reduced) "EEE d MMM" else "EEEE d MMMM", locale)).replaceFirstChar { it.titlecase(locale) }
        val hour = now.format(DateTimeFormatter.ofPattern("H:mm"))
        val showComms = AodBubble.Comms in bubbles
        val waiting = messages.count { !it.service }
        Column(
            Modifier.fillMaxWidth().offset(shiftX.dp, shiftY.dp).padding(start = 32.dp, end = 32.dp, top = if (reduced) 240.dp else 132.dp),
            horizontalAlignment = if (reduced) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            if (reduced) {
                Text(hour, color = Soft, fontSize = 72.sp, lineHeight = 80.sp, fontWeight = FontWeight.Light)
                Text(date, color = Dim, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text(date, color = Soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(hour, color = Bright, fontSize = 92.sp, lineHeight = 100.sp, fontWeight = FontWeight.Medium)
            }
            Column(Modifier.padding(top = if (reduced) 26.dp else 40.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                bubbles.filter { it != AodBubble.Comms }.forEach { bubble ->
                    when (bubble) {
                        AodBubble.NextEvent -> nextEvent(events, now)?.let { EventLine(it, now, reduced) }
                        AodBubble.Today -> {
                            val count = tasks.count { it.listId != TaskList.MEDICINES && it.parentId == null }
                            if (count > 0) IconLine(EuriseIcons.task(2.2f), Salvia, pluralStringResource(R.plurals.aod_tasks, count, count))
                        }
                        AodBubble.Clock -> {
                            val running = all.any { BubbleText.isClock(it.packageName) }
                            alarmText(alarm, now)?.let { text ->
                                IconLine(EuriseIcons.alarm(2.2f), Ottone, if (running) "$text · ${stringResource(R.string.aod_timer_running)}" else text)
                            } ?: run { if (running) IconLine(EuriseIcons.alarm(2.2f), Ottone, stringResource(R.string.aod_timer_running)) }
                        }
                        AodBubble.Music -> playing?.takeIf { it.playing }?.let { IconLine(EuriseIcons.music(2.2f), Azzurro, it.title) }
                        AodBubble.Meds -> doseText(tasks, now)?.let { IconLine(EuriseIcons.medical(2.2f), Borgogna, it) }
                        AodBubble.Calendar -> MonthGrid(data, today, reduced)
                        AodBubble.Comms -> Unit
                    }
                }
            }
        }
        if (showComms && waiting > 0 || !reduced) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().offset(shiftX.dp, shiftY.dp).padding(horizontal = 32.dp, vertical = 74.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (showComms && waiting > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(EuriseIcons.chat(2.1f), null, tint = if (reduced) Dim else Bright, modifier = Modifier.size(21.dp))
                        Text(waiting.toString(), color = if (reduced) Dim else Bright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(Modifier.size(1.dp))
                }
                if (!reduced) BatteryLabel()
            }
        }
    }
}

/** Il mese in corso: oggi evidenziato e un punto nei giorni con impegni, senza mostrare quali. */
@Composable
private fun MonthGrid(data: EuriseData, today: LocalDate, reduced: Boolean) {
    val month = remember(today) { java.time.YearMonth.from(today) }
    val flow = remember(month) {
        if (data.calendar.hasPermission()) data.calendar.observeEvents(month.atDay(1), month.atEndOfMonth()) else flowOf(emptyList())
    }
    val events by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    val busy = remember(events) { month.atDay(1).datesUntil(month.atEndOfMonth().plusDays(1)).toList().filter { d -> events.any { it.occursOn(d) } }.toSet() }
    val text = if (reduced) Dim else Bright
    val locale = Locale.getDefault()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            today.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
            color = Soft, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            java.time.DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(java.time.format.TextStyle.NARROW, locale), color = Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        monthCells(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val isToday = day == today
                            Box(
                                Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (isToday) Azzurro else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(day.dayOfMonth.toString(), color = if (isToday) Color.Black else text, fontSize = 12.5.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium)
                            }
                            if (day in busy && !isToday) Box(Modifier.align(Alignment.BottomCenter).size(3.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Azzurro))
                        }
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BatteryLabel() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val level = remember { context.getSystemService(android.os.BatteryManager::class.java).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) }
    Text("$level%", color = Soft, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun EventLine(event: CalendarEvent, now: LocalDateTime, reduced: Boolean) {
    val time = event.start.format(DateTimeFormatter.ofPattern("H:mm"))
    if (reduced) {
        Text("$time · ${event.title}", color = Dim, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        return
    }
    val minutes = Duration.between(now, event.start).toMinutes()
    val whenText = when {
        minutes <= 0 -> stringResource(R.string.bubble_now)
        minutes < 60 -> stringResource(R.string.aod_in_minutes, minutes.toInt())
        minutes < 24 * 60 -> stringResource(R.string.aod_in_hours, (minutes / 60).toInt())
        else -> stringResource(R.string.aod_tomorrow)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(3.dp).height(62.dp).clip(RoundedCornerShape(2.dp)).background(Azzurro))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(whenText, color = Azzurro, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(event.title, color = Bright, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(listOf(time, event.location).filter { it.isNotBlank() }.joinToString(" · "), color = Soft, fontSize = 13.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun IconLine(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, color = Bright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun alarmText(alarm: LocalDateTime?, now: LocalDateTime): String? {
    if (alarm == null) return null
    val time = alarm.format(DateTimeFormatter.ofPattern("H:mm"))
    return when (alarm.toLocalDate()) {
        now.toLocalDate() -> stringResource(R.string.aod_alarm_today, time)
        now.toLocalDate().plusDays(1) -> stringResource(R.string.aod_alarm_tomorrow, time)
        else -> null
    }
}

/** La prossima dose senza il nome del farmaco, che a telefono bloccato non deve leggersi. */
@Composable
private fun doseText(tasks: List<Task>, now: LocalDateTime): String? {
    val doses = tasks.filter { it.listId == TaskList.MEDICINES && it.due != null }
    if (doses.isEmpty()) return null
    val next = doses.filter { it.due == now.toLocalDate() && it.time != null }.minByOrNull { it.time!! }
    return next?.time?.let { stringResource(R.string.aod_dose_at, it.format(DateTimeFormatter.ofPattern("H:mm"))) }
        ?: stringResource(R.string.aod_dose_due)
}

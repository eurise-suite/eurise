package eu.eurise.core.links

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.calendar.EventDraft
import eu.eurise.core.data.calendar.FreeSlots
import eu.eurise.core.data.model.Capture
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * Fogli rapidi della ricerca universale: ciò che si è scritto diventa nota, attività o appuntamento senza aprire
 * l'app. Si scrive, si salva, il foglio si chiude e si torna dov'eravamo. Tutto resta sul telefono.
 */

private const val LOCAL_CALENDAR_COLOR = 0xFF3E5C8A.toInt()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickSheet(
    icon: ImageVector,
    title: String,
    onTitle: (String) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = scheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitle,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = scheme.surfaceContainerLow,
                        focusedContainerColor = scheme.surfaceContainerLow,
                        focusedBorderColor = scheme.primary,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp).clip(CircleShape).background(scheme.surfaceContainerLow)) {
                    Icon(EuriseIcons.close(), stringResource(R.string.quick_close), tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            content()
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun SaveButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(enabled = enabled, onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Nota con il titolo già scritto nella ricerca; il testo si aggiunge qui. */
@Composable
fun QuickNoteSheet(data: EuriseData, initial: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial) }
    var body by remember { mutableStateOf("") }
    val done = stringResource(R.string.capture_done_note)
    QuickSheet(EuriseIcons.note(), title, { title = it }, onDismiss) {
        val scheme = MaterialTheme.colorScheme
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            placeholder = { Text(stringResource(R.string.quick_note_body)) },
            minLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = scheme.surfaceContainerLow, focusedContainerColor = scheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        )
        SaveButton(stringResource(R.string.capture_create_note), title.isNotBlank() || body.isNotBlank()) {
            scope.launch {
                data.notes.save(null, title.trim(), body.trim())
                Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }
}

/** Attività già scritta: si sceglie solo quando (oggi di base, o la data detta nella frase). */
@Composable
fun QuickTaskSheet(data: EuriseData, initial: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initial) }
    val today = remember { LocalDate.now() }
    val parsed = remember(text) { Capture.parse(text, LocalDateTime.now(), emptyList()) }
    var chosen by remember { mutableStateOf<LocalDate?>(null) }
    var noDate by remember { mutableStateOf(false) }
    val due = when {
        noDate -> null
        chosen != null -> chosen
        else -> parsed.quick.due ?: today
    }
    val done = stringResource(R.string.capture_done_task)
    QuickSheet(EuriseIcons.task(), text, { text = it }, onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill(stringResource(R.string.quick_today), null, due == today) { chosen = today; noDate = false }
            FilterPill(stringResource(R.string.quick_tomorrow), null, due == today.plusDays(1)) { chosen = today.plusDays(1); noDate = false }
            val other = due?.takeIf { it != today && it != today.plusDays(1) }
            if (other != null) FilterPill(shortDay(other), null, true) {}
            FilterPill(stringResource(R.string.quick_no_date), null, due == null) { noDate = true }
        }
        SaveButton(stringResource(R.string.capture_create_task), parsed.title.isNotBlank()) {
            scope.launch {
                val q = parsed.quick
                data.tasks.add(
                    parsed.title, due = due, time = q.time, repeat = q.repeat, priority = q.priority, listId = TaskList.MAIN,
                    remindBefore = if (q.time != null) 0 else null,
                )
                Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }
}

/**
 * Appuntamento: calendario del mese (frecce per gli altri mesi), il giorno scelto mostra gli orari liberi da
 * scorrere, la durata si cambia con un tocco. Salva nel primo calendario sincronizzato in cui si può scrivere.
 */
@Composable
fun QuickEventSheet(data: EuriseData, initial: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial) }
    val now = remember { LocalDateTime.now() }
    val parsedDay = remember { Capture.parse(initial, now, emptyList()).quick.due }
    var day by remember { mutableStateOf(parsedDay ?: now.toLocalDate()) }
    var month by remember { mutableStateOf(YearMonth.from(day)) }
    var minutes by remember { mutableIntStateOf(60) }
    var start by remember { mutableStateOf<LocalTime?>(null) }
    var allowed by remember { mutableStateOf(data.calendar.hasPermission()) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(day, allowed) {
        events = if (allowed) runCatching { data.calendar.observeEvents(day, day).first() }.getOrDefault(emptyList()) else emptyList()
    }
    val slots = remember(day, events, minutes) { FreeSlots.find(day, events, Duration.ofMinutes(minutes.toLong()), now) }
    LaunchedEffect(slots) { if (start !in slots) start = slots.firstOrNull() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed = data.calendar.hasPermission() }
    val localCalendar = stringResource(R.string.capture_local_calendar)
    val failed = stringResource(R.string.capture_failed)
    val done = stringResource(R.string.capture_done_event)

    QuickSheet(EuriseIcons.calendar(), title, { title = it }, onDismiss) {
        MonthGrid(month, day, now.toLocalDate(), onMonth = { month = it }, onDay = { day = it })
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.quick_free_on, longDay(day)).uppercase(Locale.getDefault()),
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            listOf(30, 60, 120).forEach { m ->
                val label = if (m < 60) "$m min" else "${m / 60} h"
                Text(
                    label, fontSize = 13.sp, fontWeight = if (m == minutes) FontWeight.Bold else FontWeight.Medium,
                    color = if (m == minutes) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.RadioButton) { minutes = m }.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        if (!allowed) {
            FilterPill(stringResource(R.string.quick_calendar_permission), null, false) {
                permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            }
        } else if (slots.isEmpty()) {
            Text(stringResource(R.string.quick_no_free), fontSize = 13.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                items(slots, key = { it.toString() }) { t -> FilterPill(t.format(TimeFormat), null, t == start) { start = t } }
            }
            val busyText = FreeSlots.busyRanges(day, events).joinToString(" · ") { "${it.first.format(TimeFormat)}–${it.second.format(TimeFormat)}" }
            if (busyText.isNotEmpty()) {
                Text(stringResource(R.string.quick_busy, busyText), fontSize = 12.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        val at = start
        SaveButton(
            if (at != null) stringResource(R.string.quick_save_event_at, shortDay(day), at.format(TimeFormat)) else stringResource(R.string.capture_create_event),
            enabled = !busy && title.isNotBlank() && at != null && allowed,
        ) {
            if (at == null) return@SaveButton
            busy = true
            scope.launch {
                val calendars = data.calendar.observeCalendars().first().filter { it.writable }
                val calendarId = (calendars.firstOrNull { !it.local } ?: calendars.firstOrNull())?.id
                    ?: data.calendar.createLocalCalendar(localCalendar, LOCAL_CALENDAR_COLOR)
                val begin = day.atTime(at)
                val id = calendarId?.let { data.calendar.save(EventDraft(null, it, title.trim(), "", "", false, begin, begin.plusMinutes(minutes.toLong()))) }
                busy = false
                Toast.makeText(context, if (id != null) done else failed, Toast.LENGTH_SHORT).show()
                if (id != null) onDismiss()
            }
        }
    }
}

private val TimeFormat = DateTimeFormatter.ofPattern("H:mm")

private fun shortDay(day: LocalDate): String =
    day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + day.dayOfMonth

private fun longDay(day: LocalDate): String =
    day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) } + " " + day.dayOfMonth

/** Il mese, con la settimana che parte di lunedì. I giorni passati restano visibili ma non si scelgono. */
@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, today: LocalDate, onMonth: (YearMonth) -> Unit, onDay: (LocalDate) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = month > YearMonth.from(today)) {
                Icon(EuriseIcons.chevronLeft(), stringResource(R.string.quick_month_before), tint = scheme.primary)
            }
            Text(
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) } + " " + month.year,
                fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
                Icon(EuriseIcons.chevronRight(), stringResource(R.string.quick_month_after), tint = scheme.primary)
            }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val cells = lead + month.lengthOfMonth()
        val weekStart = LocalDate.of(2024, 1, 1) // un lunedì
        Row {
            (0 until 7).forEach { i ->
                Text(
                    weekStart.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        (0 until (cells + 6) / 7).forEach { week ->
            Row {
                (0 until 7).forEach { col ->
                    val n = week * 7 + col - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1.25f), contentAlignment = Alignment.Center) {
                        if (n in 1..month.lengthOfMonth()) {
                            val date = month.atDay(n)
                            val past = date.isBefore(today)
                            val isSelected = date == selected
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (isSelected) scheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                    .then(if (past) Modifier else Modifier.clickable(role = Role.Button) { onDay(date) }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$n",
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected || date == today) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isSelected -> scheme.onPrimary
                                        past -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        date == today -> scheme.primary
                                        else -> scheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

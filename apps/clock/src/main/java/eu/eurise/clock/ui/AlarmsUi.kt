package eu.eurise.clock.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.clock.R
import eu.eurise.clock.data.ClockStore
import eu.eurise.clock.logic.Alarm
import eu.eurise.clock.logic.ItalianHolidays
import eu.eurise.clock.logic.Schedule
import eu.eurise.clock.logic.Silence
import eu.eurise.clock.system.CalendarReader
import eu.eurise.clock.system.Scheduler
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.GroupCard
import eu.eurise.core.designsystem.component.SectionLabel
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Time = DateTimeFormatter.ofPattern("H:mm")
private val DayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val Week = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** Sveglie: la frase della prossima, l'elenco e la riga "Ora di dormire". */
@Composable
fun AlarmsScreen(adding: Boolean, onAdded: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ClockStore.get(context) }
    val alarms by store.alarms.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()
    val nowMs = rememberNow(30_000)
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())
    val vacation = remember(alarms, settings.useCalendar) { store.vacationDays }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var sleepSheet by remember { mutableStateOf(false) }
    LaunchedEffect(adding) {
        if (adding) { editing = Alarm(0, LocalTime.of(7, 0), days = Alarm.Workdays); onAdded() }
    }
    val save: (Alarm) -> Unit = { a ->
        store.saveAlarm(if (a.id == 0) a.copy(id = store.nextId()) else a)
        Scheduler.rescheduleAll(context)
    }
    val first = Schedule.first(alarms, now) { it in vacation }

    ScreenList(stringResource(R.string.tab_alarms)) {
        item(key = "next") {
            Text(
                first?.let { (_, at) -> nextPhrase(at, now) } ?: stringResource(R.string.alarms_none),
                fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        items(alarms.size, key = { alarms[it].id }) { i ->
            val a = alarms[i]
            AlarmCard(a, now, vacation, onToggle = { on -> save(a.copy(enabled = on, skipped = if (on) a.skipped else null)) }, onOpen = { editing = a }, onUnskip = { save(a.copy(skipped = null)) })
        }
        if (first != null) item(key = "sleep") {
            val bed = Schedule.bedtime(first.second, Duration.ofMinutes(settings.sleepMinutes.toLong()))
            GroupCard {
                Row(Modifier.fillMaxWidth().clickable { sleepSheet = true }.padding(vertical = 14.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.bedtime_row, bed.format(Time)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.bedtime_sub, sleepLabel(settings.sleepMinutes), first.second.format(Time)), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (settings.useCalendar) item(key = "calendar") {
            // Spegnere la lettura del calendario non chiede conferme (regola della privacy).
            GroupCard {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    SwitchRow(stringResource(R.string.use_calendar), stringResource(R.string.use_calendar_sub), true) {
                        store.updateSettings { s -> s.copy(useCalendar = false) }
                        store.vacationDays = emptySet()
                        Scheduler.rescheduleAll(context)
                    }
                }
            }
        }
    }

    editing?.let { a ->
        AlarmEditor(a, onSave = { save(it); editing = null }, onDelete = {
            store.deleteAlarm(a.id); Scheduler.rescheduleAll(context); editing = null
        }, onDismiss = { editing = null })
    }
    if (sleepSheet) SleepDialog(settings.sleepMinutes, settings.bedtimeReminder, onDone = { m, r ->
        store.updateSettings { it.copy(sleepMinutes = m, bedtimeReminder = r) }
        sleepSheet = false
    }, onDismiss = { sleepSheet = false })
}

/** "La prossima suona domani alle 6:45, tra 7 ore e 12 minuti." */
@Composable
private fun nextPhrase(at: LocalDateTime, now: LocalDateTime): String {
    val day = when (at.toLocalDate()) {
        now.toLocalDate() -> stringResource(R.string.day_today)
        now.toLocalDate().plusDays(1) -> stringResource(R.string.day_tomorrow)
        else -> at.format(DayFormat)
    }
    val d = Duration.between(now, at)
    val hours = d.toHours().toInt()
    val minutes = (d.toMinutes() % 60).toInt()
    val inText = when {
        hours == 0 -> pluralStringResource(R.plurals.minutes, minutes, minutes)
        minutes == 0 -> pluralStringResource(R.plurals.hours, hours, hours)
        else -> stringResource(R.string.and, pluralStringResource(R.plurals.hours, hours, hours), pluralStringResource(R.plurals.minutes, minutes, minutes))
    }
    return stringResource(R.string.alarms_next, day, at.format(Time), inText)
}

@Composable
private fun AlarmCard(a: Alarm, now: LocalDateTime, vacation: Set<LocalDate>, onToggle: (Boolean) -> Unit, onOpen: () -> Unit, onUnskip: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val muted = if (a.enabled) scheme.onSurface else scheme.onSurfaceVariant
    GroupCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.time.format(Time), fontSize = 38.sp, fontWeight = FontWeight.Medium, color = muted)
                    Text(a.label.ifBlank { stringResource(R.string.alarm_default) }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = muted)
                }
                Switch(checked = a.enabled, onCheckedChange = onToggle)
            }
            DaysLine(a.days, a.enabled)
            val note = when {
                !a.enabled -> null
                a.skipped != null -> stringResource(R.string.alarm_skipped, a.skipped.format(DayFormat))
                else -> Schedule.nextSilence(a, now.toLocalDate()) { it in vacation }?.let { (day, why) ->
                    val reason = when (why) {
                        Silence.Holiday -> ItalianHolidays.name(day).orEmpty()
                        Silence.Vacation -> stringResource(R.string.reason_vacation)
                        Silence.Skipped -> stringResource(R.string.reason_skipped)
                    }
                    stringResource(R.string.alarm_silent_on, day.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)), reason) +
                        if (a.skipVacation && why != Silence.Vacation) stringResource(R.string.alarm_and_vacation) else ""
                } ?: if (a.skipVacation) stringResource(R.string.alarm_vacation_only) else null
            }
            if (note != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(note, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (a.skipped != null) Chip(stringResource(R.string.alarm_unskip), onClick = onUnskip)
                }
            }
        }
    }
}

/** Le iniziali dei giorni, accese quelle in cui suona. */
@Composable
private fun DaysLine(days: Set<DayOfWeek>, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    if (days.isEmpty()) {
        Text(stringResource(R.string.alarm_once), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Week.forEach { d ->
            val on = d in days
            Text(
                d.getDisplayName(TextStyle.NARROW, Locale.ITALIAN),
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Normal,
                color = if (on && enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = if (on) 1f else 0.6f),
            )
        }
    }
}

@Composable
private fun sleepLabel(minutes: Int): String =
    stringResource(if (minutes % 60 == 0) R.string.sleep_hours else R.string.sleep_hours_half, minutes / 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepDialog(minutes: Int, reminder: Boolean, onDone: (Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    var m by remember { mutableStateOf(minutes) }
    var r by remember { mutableStateOf(reminder) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bedtime_title_dialog)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (12..18).map { it * 30 }.forEach { v -> Chip(sleepLabel(v), filled = v == m) { m = v } }
                }
                SwitchRow(stringResource(R.string.bedtime_reminder), stringResource(R.string.bedtime_reminder_sub), r) { r = it }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(m, r) }) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Modifica di una sveglia: ora, giorni, nome; il calendario (festivi, ferie, impegni presto); il risveglio
 * (suono che cresce, rimanda, vibrazione, piccolo calcolo, routine del mattino).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlarmEditor(initial: Alarm, onSave: (Alarm) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ClockStore.get(context) }
    var a by remember { mutableStateOf(initial) }
    val picker = rememberTimePickerState(initial.time.hour, initial.time.minute, is24Hour = true)
    var explainCalendar by remember { mutableStateOf<((Alarm) -> Alarm)?>(null) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val change = explainCalendar
        explainCalendar = null
        if (ok && change != null) {
            store.updateSettings { it.copy(useCalendar = true) }
            a = change(a)
        }
    }
    val ringtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            a = a.copy(sound = uri?.toString())
        }
    }
    /** Le funzioni che leggono il calendario passano dalla spiegazione la prima volta. */
    fun withCalendar(change: (Alarm) -> Alarm) {
        if (store.settings.value.useCalendar && CalendarReader.granted(context)) a = change(a) else explainCalendar = change
    }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Text(stringResource(R.string.alarm_editor_title), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { onSave(a.copy(time = LocalTime.of(picker.hour, picker.minute), enabled = true, skipped = null)) }) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = picker) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Week.forEach { d ->
                    val on = d in a.days
                    val name = d.getDisplayName(TextStyle.FULL, Locale.ITALIAN)
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable(role = Role.Checkbox) { a = a.copy(days = if (on) a.days - d else a.days + d) }
                            .semantics { contentDescription = name },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d.getDisplayName(TextStyle.NARROW, Locale.ITALIAN), fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            OutlinedTextField(
                value = a.label, onValueChange = { a = a.copy(label = it.take(40)) }, singleLine = true,
                label = { Text(stringResource(R.string.alarm_name)) }, modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(stringResource(R.string.section_calendar))
            GroupCard {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    val nextHoliday = ItalianHolidays.next(LocalDate.now())
                    SwitchRow(
                        stringResource(R.string.opt_holidays),
                        nextHoliday?.let { stringResource(R.string.opt_holidays_sub, it.format(DayFormat), ItalianHolidays.name(it).orEmpty()) },
                        a.skipHolidays,
                    ) { a = a.copy(skipHolidays = it) }
                    CardDivider()
                    SwitchRow(stringResource(R.string.opt_vacation), stringResource(R.string.opt_vacation_sub), a.skipVacation) { on ->
                        if (on) withCalendar { it.copy(skipVacation = true) } else a = a.copy(skipVacation = false)
                    }
                    CardDivider()
                    SwitchRow(stringResource(R.string.opt_earlier), stringResource(R.string.opt_earlier_sub), a.earlier) { on ->
                        if (on) withCalendar { it.copy(earlier = true) } else a = a.copy(earlier = false)
                    }
                }
            }

            SectionLabel(stringResource(R.string.section_wake))
            GroupCard {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    val soundName = remember(a.sound) {
                        a.sound?.let { s -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(s))?.getTitle(context) }.getOrNull() }
                    } ?: stringResource(R.string.sound_default)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            ringtone.launch(
                                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, a.sound?.let(Uri::parse)),
                            )
                        }.padding(vertical = 12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.opt_sound), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(soundName, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    CardDivider()
                    SwitchRow(stringResource(R.string.opt_ramp), stringResource(R.string.opt_ramp_sub), a.rampSeconds > 0) { a = a.copy(rampSeconds = if (it) 60 else 0) }
                    CardDivider()
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.opt_snooze), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15).forEach { m -> Chip(stringResource(R.string.minutes_short, m), filled = a.snoozeMinutes == m && a.maxSnoozes > 0) { a = a.copy(snoozeMinutes = m, maxSnoozes = if (a.maxSnoozes == 0) 3 else a.maxSnoozes) } }
                            Chip(stringResource(R.string.snooze_never), filled = a.maxSnoozes == 0) { a = a.copy(maxSnoozes = 0) }
                        }
                        if (a.maxSnoozes > 0) Text(stringResource(R.string.opt_snooze_sub, a.snoozeMinutes, a.maxSnoozes), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CardDivider()
                    SwitchRow(stringResource(R.string.opt_vibrate), null, a.vibrate) { a = a.copy(vibrate = it) }
                    CardDivider()
                    SwitchRow(stringResource(R.string.opt_challenge), stringResource(R.string.opt_challenge_sub), a.challenge) { a = a.copy(challenge = it) }
                    CardDivider()
                    SwitchRow(stringResource(R.string.opt_routine), stringResource(R.string.opt_routine_sub), a.routine) { a = a.copy(routine = it) }
                }
            }
            if (initial.id != 0) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.alarm_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (explainCalendar != null) {
        AlertDialog(
            onDismissRequest = { explainCalendar = null },
            title = { Text(stringResource(R.string.calendar_privacy_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.calendar_privacy_read, R.string.calendar_privacy_where, R.string.calendar_privacy_who, R.string.calendar_privacy_back)
                        .forEach { Text(stringResource(it), fontSize = 14.sp, lineHeight = 19.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) { Text(stringResource(R.string.turn_on), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { explainCalendar = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

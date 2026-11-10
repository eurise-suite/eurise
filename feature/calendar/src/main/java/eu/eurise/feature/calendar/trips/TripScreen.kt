package eu.eurise.feature.calendar.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.data.trips.Leg
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.calendar.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ITALIAN)

/** "Prepara il viaggio": tratti letti dal biglietto (modificabili) e le cose da creare, ognuna togliibile. */
@Composable
fun TripRoute(source: TripSource, onClose: () -> Unit) {
    val context = LocalContext.current
    val viewModel = viewModel(key = source.toString()) { TripViewModel(context.applicationContext, source) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    TripScreen(state, viewModel, onClose)
}

private sealed interface Picking {
    data class Date(val leg: Int) : Picking
    data class Time(val leg: Int, val depart: Boolean) : Picking
    data object Alarm : Picking
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TripScreen(state: TripState, vm: TripViewModel, onClose: () -> Unit) {
    var picking by remember { mutableStateOf<Picking?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(EuriseIcons.close(), stringResource(R.string.cal_close)) } },
                title = { Text(stringResource(R.string.trip_title), style = MaterialTheme.typography.titleMedium) },
            )
        },
    ) { padding ->
        if (state.loading) {
            Row(Modifier.padding(padding).padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.trip_reading))
            }
            return@Scaffold
        }
        if (state.done) {
            Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.trip_done), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.trip_done_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onClose) { Text(stringResource(R.string.trip_close)) }
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (state.failed) R.string.trip_not_read else R.string.trip_read_ok),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(state.title, vm::setTitle, label = { Text(stringResource(R.string.trip_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (state.booking.isNotBlank()) Text(stringResource(R.string.trip_booking, state.booking), style = MaterialTheme.typography.bodyMedium)

            state.legs.forEachIndexed { index, leg ->
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (leg.kind == Leg.Kind.Train) EuriseIcons.train() else EuriseIcons.plane(), null, Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.trip_leg, index + 1), style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        if (state.legs.size > 1) TextButton(onClick = { vm.removeLeg(index) }) { Text(stringResource(R.string.trip_remove)) }
                    }
                    OutlinedTextField(leg.number, { vm.setLeg(index, leg.copy(number = it)) }, label = { Text(stringResource(R.string.trip_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(leg.from, { vm.setLeg(index, leg.copy(from = it)) }, label = { Text(stringResource(R.string.trip_from)) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(leg.to, { vm.setLeg(index, leg.copy(to = it)) }, label = { Text(stringResource(R.string.trip_to)) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(leg.depart?.toLocalDate()?.format(DayFormat) ?: stringResource(R.string.trip_pick_day)) { picking = Picking.Date(index) }
                        Chip(stringResource(R.string.trip_departs, leg.depart?.toLocalTime()?.takeIf { it != LocalTime.MIDNIGHT }?.toString() ?: "–")) { picking = Picking.Time(index, true) }
                        Chip(stringResource(R.string.trip_arrives, leg.arrive?.toLocalTime()?.toString() ?: "–")) { picking = Picking.Time(index, false) }
                        if (leg.seat.isNotBlank()) Chip(stringResource(R.string.trip_seat, leg.seat)) {}
                    }
                    if (leg.departZone != null && leg.departZone != java.time.ZoneId.systemDefault().id) {
                        Text(stringResource(R.string.trip_local_times), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = vm::addLeg) { Text(stringResource(R.string.trip_add_leg)) }

            Text(stringResource(R.string.trip_prepare), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Card {
                ToggleRow(stringResource(R.string.trip_to_calendar), state.toCalendar, enabled = state.calendars.isNotEmpty()) { vm.toggle("calendar") }
                if (state.toCalendar && state.calendars.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.calendars.forEach { c -> Chip(c.name, selected = c.id == state.calendarId) { vm.setCalendar(c.id) } }
                    }
                }
                ToggleRow(
                    stringResource(R.string.trip_alarm, state.alarmTime?.toString() ?: "–"), state.alarm && state.alarmTime != null,
                    enabled = state.alarmTime != null, onLabel = { picking = Picking.Alarm },
                ) { vm.toggle("alarm") }
                state.destinationZone?.takeIf { it != java.time.ZoneId.systemDefault().id }?.let { zone ->
                    ToggleRow(stringResource(R.string.trip_zone, zone.substringAfterLast('/').replace('_', ' ')), state.clockZone) { vm.toggle("zone") }
                }
                if (state.hasPdf) ToggleRow(stringResource(R.string.trip_folder), state.folder) { vm.toggle("folder") }
                ToggleRow(stringResource(R.string.trip_mode), state.travelMode) { vm.toggle("mode") }
                ToggleRow(stringResource(R.string.trip_gallery), false, enabled = false) {}
                ToggleRow(stringResource(R.string.trip_map), false, enabled = false) {}
                Text(stringResource(R.string.trip_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = vm::create, enabled = state.legs.any { it.depart != null }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.trip_create))
            }
            Spacer(Modifier.navigationBarsPadding().size(24.dp))
        }
    }

    when (val p = picking) {
        is Picking.Date -> {
            val leg = state.legs.getOrNull(p.leg)
            val initial = (leg?.depart?.toLocalDate() ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        val day = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        if (leg != null && day != null) {
                            val shift: (LocalDateTime?) -> LocalDateTime? = { it?.let { t -> day.atTime(t.toLocalTime()) } }
                            vm.setLeg(p.leg, leg.copy(depart = shift(leg.depart) ?: day.atStartOfDay(), arrive = shift(leg.arrive)))
                        }
                        picking = null
                    }) { Text(stringResource(R.string.trip_ok)) }
                },
            ) { DatePicker(picker) }
        }
        is Picking.Time -> {
            val leg = state.legs.getOrNull(p.leg)
            val current = (if (p.depart) leg?.depart else leg?.arrive)?.toLocalTime() ?: LocalTime.of(9, 0)
            TimeDialog(current, onDismiss = { picking = null }) { t ->
                if (leg != null) {
                    val day = leg.depart?.toLocalDate() ?: LocalDate.now()
                    vm.setLeg(
                        p.leg,
                        if (p.depart) leg.copy(depart = day.atTime(t))
                        else leg.copy(arrive = day.atTime(t).let { a -> leg.depart?.takeIf { a.isBefore(it) }?.let { a.plusDays(1) } ?: a }),
                    )
                }
                picking = null
            }
        }
        Picking.Alarm -> TimeDialog(state.alarmTime ?: LocalTime.of(6, 0), onDismiss = { picking = null }) { vm.setAlarmTime(it); picking = null }
        null -> Unit
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, enabled: Boolean = true, onLabel: (() -> Unit)? = null, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).let { m -> if (onLabel != null && enabled) m.clickable(onClick = onLabel) else m },
        )
        Switch(checked = on, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(R.string.trip_ok)) } },
        text = { TimePicker(state = picker) },
    )
}

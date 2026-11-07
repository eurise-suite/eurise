package eu.eurise.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.colors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Nuovo appuntamento o modifica di uno esistente.
 *
 * @param eventId e [beginMillis]: l'occorrenza da aprire; nulli per un appuntamento nuovo.
 * @param startMillis inizio proposto per un appuntamento nuovo.
 */
@Composable
fun EventEditorRoute(
    repository: CalendarRepository,
    eventId: Long?,
    beginMillis: Long?,
    startMillis: Long?,
    onClose: () -> Unit,
) {
    val viewModel = viewModel { EventEditorViewModel(repository, eventId, beginMillis, startMillis) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val localName = stringResource(R.string.cal_local_name)
    BackHandler(onBack = onClose)
    EventEditorScreen(
        state = state,
        onTitle = viewModel::onTitle,
        onLocation = viewModel::onLocation,
        onDescription = viewModel::onDescription,
        onAllDay = viewModel::onAllDay,
        onCalendar = viewModel::onCalendar,
        onStartDate = viewModel::onStartDate,
        onStartTime = viewModel::onStartTime,
        onEndDate = viewModel::onEndDate,
        onEndTime = viewModel::onEndTime,
        onCreateLocal = { viewModel.createLocalCalendar(localName, CalendarTint.BluNotte.light.toArgb()) },
        onSave = { viewModel.save(onClose) },
        onDelete = { viewModel.delete(onClose) },
        onClose = onClose,
    )
}

private val DateChip = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventEditorScreen(
    state: EventEditorState,
    onTitle: (String) -> Unit,
    onLocation: (String) -> Unit,
    onDescription: (String) -> Unit,
    onAllDay: (Boolean) -> Unit,
    onCalendar: (Long) -> Unit,
    onStartDate: (LocalDate) -> Unit,
    onStartTime: (LocalTime) -> Unit,
    onEndDate: (LocalDate) -> Unit,
    onEndTime: (LocalTime) -> Unit,
    onCreateLocal: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val editable = !state.readOnly
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cal_close)) }
                },
                title = {
                    Text(
                        stringResource(
                            when {
                                state.readOnly -> R.string.cal_event_details
                                state.isNew -> R.string.cal_new_event
                                else -> R.string.cal_edit_event
                            }
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    if (editable) {
                        Button(
                            onClick = onSave,
                            enabled = state.loaded && !state.busy && state.calendarId != null,
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text(stringResource(R.string.cal_save)) }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PlainField(
                value = state.title,
                onValueChange = onTitle,
                placeholder = stringResource(R.string.cal_field_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                enabled = editable,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            state.error?.let { err ->
                Text(
                    stringResource(
                        when (err) {
                            EditorError.Title -> R.string.cal_error_title
                            EditorError.End -> R.string.cal_error_end
                            EditorError.Save -> R.string.cal_error_save
                        }
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            FieldCard {
                FieldRow(stringResource(R.string.cal_field_all_day)) {
                    Switch(checked = state.allDay, onCheckedChange = onAllDay, enabled = editable)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FieldRow(stringResource(R.string.cal_field_start)) {
                    Chip(state.startDate.format(DateChip), editable) { picking = "startDate" }
                    if (!state.allDay) Chip(state.startTime.format(TimeFormat), editable) { picking = "startTime" }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FieldRow(stringResource(R.string.cal_field_end)) {
                    Chip(state.endDate.format(DateChip), editable) { picking = "endDate" }
                    if (!state.allDay) Chip(state.endTime.format(TimeFormat), editable) { picking = "endTime" }
                }
            }

            if (state.calendars.none { it.writable } && editable) {
                FieldCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.cal_no_calendar_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.cal_no_calendar_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onCreateLocal) { Text(stringResource(R.string.cal_create_local)) }
                    }
                }
            }

            FieldCard {
                CalendarPicker(state, editable, onCalendar)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PlainField(
                    value = state.location,
                    onValueChange = onLocation,
                    placeholder = stringResource(R.string.cal_field_location),
                    style = MaterialTheme.typography.bodyLarge,
                    enabled = editable,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PlainField(
                    value = state.description,
                    onValueChange = onDescription,
                    placeholder = stringResource(R.string.cal_field_notes),
                    style = MaterialTheme.typography.bodyLarge,
                    enabled = editable,
                    singleLine = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 48.dp),
                )
            }

            Text(
                stringResource(
                    when {
                        state.recurring -> R.string.cal_recurring_note
                        state.readOnly -> R.string.cal_readonly_note
                        state.selectedCalendar?.local == true -> R.string.cal_local_note
                        else -> R.string.cal_sync_note
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            if (!state.isNew && editable) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.cal_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    when (picking) {
        "startDate" -> DateDialog(state.startDate, { onStartDate(it); picking = null }) { picking = null }
        "endDate" -> DateDialog(state.endDate, { onEndDate(it); picking = null }) { picking = null }
        "startTime" -> TimeDialog(state.startTime, { onStartTime(it); picking = null }) { picking = null }
        "endTime" -> TimeDialog(state.endTime, { onEndTime(it); picking = null }) { picking = null }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.cal_delete_title)) },
            text = { Text(stringResource(R.string.cal_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.cal_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cal_cancel)) } },
        )
    }
}

@Composable
private fun FieldCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
    ) { Column { content() } }
}

@Composable
private fun FieldRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Chip(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun CalendarPicker(state: EventEditorState, enabled: Boolean, onCalendar: (Long) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selected = state.selectedCalendar
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selected != null) {
                Box(Modifier.size(10.dp).background(CalendarTint.nearest(selected.color).colors().dot, CircleShape))
                Text(selected.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(selected.accountName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.cal_field_calendar), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.calendars.filter { it.writable }.forEach { cal ->
                DropdownMenuItem(
                    text = { Text(cal.name) },
                    leadingIcon = { Box(Modifier.size(10.dp).background(CalendarTint.nearest(cal.color).colors().dot, CircleShape)) },
                    onClick = { onCalendar(cal.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            inner()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // Il selettore lavora in millisecondi UTC a mezzanotte.
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.cal_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cal_cancel)) } },
    ) { DatePicker(state = picker) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(R.string.cal_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cal_cancel)) } },
        text = { TimePicker(state = picker) },
    )
}


package eu.eurise.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.eurise.core.data.model.Task
import eu.eurise.core.designsystem.format.dueLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Foglio dal basso per creare o modificare un'attività.
 *
 * @param task attività da modificare; nulla per crearne una nuova.
 * @param onSave riceve titolo, scadenza e note.
 * @param onDelete presente solo in modifica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditorSheet(
    task: Task?,
    today: LocalDate,
    onSave: (title: String, due: LocalDate?, notes: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by rememberSaveable { mutableStateOf(task?.title.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(task?.notes.orEmpty()) }
    var dueDay by rememberSaveable { mutableStateOf(task?.due?.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val due = dueDay?.let(LocalDate::ofEpochDay)
    val focus = remember { FocusRequester() }
    val save = {
        if (title.isNotBlank()) {
            onSave(title, due, notes)
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(if (task == null) R.string.tasks_new else R.string.tasks_edit),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.tasks_field_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = due == null,
                    onClick = { dueDay = null },
                    label = { Text(stringResource(R.string.tasks_due_none)) },
                )
                FilterChip(
                    selected = due == today,
                    onClick = { dueDay = today.toEpochDay() },
                    label = { Text(stringResource(R.string.tasks_due_today)) },
                )
                FilterChip(
                    selected = due == today.plusDays(1),
                    onClick = { dueDay = today.plusDays(1).toEpochDay() },
                    label = { Text(stringResource(R.string.tasks_due_tomorrow)) },
                )
                val custom = due != null && due != today && due != today.plusDays(1)
                FilterChip(
                    selected = custom,
                    onClick = { pickingDate = true },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, Modifier.size(18.dp)) },
                    label = {
                        Text(if (custom) dueLabel(due!!, today).text else stringResource(R.string.tasks_due_pick))
                    },
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.tasks_field_notes)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = { onDelete(); onDismiss() }) {
                        Text(stringResource(R.string.tasks_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = save, enabled = title.isNotBlank()) {
                    Text(stringResource(R.string.tasks_save))
                }
            }
        }
    }

    if (task == null) {
        LaunchedEffect(Unit) { focus.requestFocus() }
    }

    if (pickingDate) {
        // Il selettore lavora in millisecondi UTC a mezzanotte: si converte in LocalDate senza fuso.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (due ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        dueDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.tasks_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.tasks_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

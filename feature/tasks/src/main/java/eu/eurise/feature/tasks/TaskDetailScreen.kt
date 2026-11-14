package eu.eurise.feature.tasks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.model.Task
import eu.eurise.core.designsystem.component.LocalDockClearance
import eu.eurise.core.designsystem.format.dueLabel
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.component.GroupCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Anticipi dell'avviso proposti, in minuti: all'ora della scadenza, 5, 15, 30 minuti, un'ora, un giorno. */
private val REMINDER_CHOICES = listOf(0, 5, 15, 30, 60, 1440)

/** Ripetizioni proposte (le altre arrivano dall'aggiunta rapida o dal cloud e restano come sono). */
private val REPEAT_CHOICES = listOf(
    "FREQ=DAILY", "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", "FREQ=WEEKLY", "FREQ=MONTHLY", "FREQ=YEARLY",
)

/**
 * Scheda di un'attività: elenco, scadenza con ora, avviso, ripetizione, importanza, passi, note e nota d'origine.
 * Le modifiche si salvano da sole poco dopo l'ultima e comunque alla chiusura.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDetailScreen(
    taskId: Long,
    repository: TaskRepository,
    state: TasksState,
    viewModel: TasksViewModel,
    onClose: () -> Unit,
    onOpenNote: (Long) -> Unit,
    linked: @Composable (taskId: Long, title: String) -> Unit,
) {
    val loaded by produceState<Task?>(null, taskId) { value = repository.get(taskId) }
    val original = loaded
    if (original == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    var draft by remember(original.id) { mutableStateOf(original) }
    val steps by remember(taskId) { repository.observeSteps(taskId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = LocalDate.now()
    val lists = state.lists.map { it.list }
    val list = lists.firstOrNull { it.id == draft.listId }
    val scheme = MaterialTheme.colorScheme

    // Se la scheda esce dallo schermo (per esempio aprendo una cosa collegata) le ultime modifiche si salvano;
    // non dopo un'eliminazione, né se sono già state salvate.
    val saved = remember(original.id) { SavedMark(original) }
    val latest by rememberUpdatedState(draft)
    LaunchedEffect(draft) {
        if (draft != saved.task) {
            delay(400)
            viewModel.update(draft)
            saved.task = draft
        }
    }
    DisposableEffect(original.id) {
        onDispose { if (!saved.deleted && latest != saved.task) viewModel.update(latest) }
    }
    val close = {
        if (draft != saved.task) viewModel.update(draft)
        saved.task = draft
        onClose()
    }
    BackHandler(onBack = close)

    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding(),
        ) {
            // Barra in alto: indietro, altre azioni, Fine.
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.height(44.dp).clip(CircleShape).clickable(onClick = close).padding(start = 4.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(EuriseIcons.chevronLeft(), null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.tasks_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary)
                }
                Spacer(Modifier.weight(1f))
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(EuriseIcons.trash(), contentDescription = stringResource(R.string.tasks_delete), modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tasks_delete), color = scheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
                TextButton(onClick = close) { Text(stringResource(R.string.tasks_done_button), fontWeight = FontWeight.Bold) }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = LocalDockClearance.current + 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Titolo con la casella.
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.Top) {
                    CheckCircle(
                        done = draft.done,
                        tint = tintColor(list?.tint),
                        label = stringResource(if (draft.done) R.string.tasks_mark_open else R.string.tasks_mark_done, draft.title),
                        onToggle = {
                            val done = !draft.done
                            viewModel.saveAndSetDone(draft, done)
                            // Una ripetizione spuntata passa alla prossima volta: questa resta come storico.
                            draft = draft.copy(
                                done = done,
                                completedAt = if (done) System.currentTimeMillis() else null,
                                repeat = if (done) null else draft.repeat,
                            )
                        },
                        size = 24,
                    )
                    Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicTextField(
                            value = draft.title,
                            onValueChange = { draft = draft.copy(title = it.replace("\n", " ")) },
                            textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp, color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.tasks_in_list, list?.name.orEmpty()),
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                GroupCard {
                    // Elenco.
                    var listMenu by remember { mutableStateOf(false) }
                    Box {
                        FieldRow(EuriseIcons.lists(), stringResource(R.string.tasks_field_list), list?.name.orEmpty(), dot = list?.tint) { listMenu = true }
                        DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                            lists.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text(l.name) },
                                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(tintColor(l.tint))) },
                                    onClick = { listMenu = false; draft = draft.copy(listId = l.id) },
                                )
                            }
                        }
                    }
                    CardDivider()
                    val due = draft.due
                    FieldRow(
                        EuriseIcons.calendar(), stringResource(R.string.tasks_field_due),
                        due?.let { dueLabel(it, today).text } ?: stringResource(R.string.tasks_none),
                    ) { pickingDate = true }
                    CardDivider()
                    val time = draft.time
                    FieldRow(
                        EuriseIcons.clock(), stringResource(R.string.tasks_field_time),
                        time?.format(TimeFormat) ?: stringResource(R.string.tasks_all_day),
                    ) { pickingTime = true }
                    CardDivider()
                    var reminderMenu by remember { mutableStateOf(false) }
                    Box {
                        FieldRow(
                            EuriseIcons.bell(), stringResource(R.string.tasks_field_reminder),
                            reminderLabel(draft.remindBefore, draft.time != null),
                            enabled = due != null,
                        ) { reminderMenu = true }
                        DropdownMenu(expanded = reminderMenu, onDismissRequest = { reminderMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.tasks_reminder_none)) }, onClick = {
                                reminderMenu = false; draft = draft.copy(remindBefore = null)
                            })
                            REMINDER_CHOICES.forEach { minutes ->
                                DropdownMenuItem(text = { Text(reminderLabel(minutes, draft.time != null)) }, onClick = {
                                    reminderMenu = false; draft = draft.copy(remindBefore = minutes); askNotifications()
                                })
                            }
                        }
                    }
                    CardDivider()
                    var repeatMenu by remember { mutableStateOf(false) }
                    Box {
                        FieldRow(
                            EuriseIcons.repeat(), stringResource(R.string.tasks_field_repeat),
                            repeatLabel(draft.repeat) ?: stringResource(R.string.tasks_never),
                        ) { repeatMenu = true }
                        DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.tasks_never)) }, onClick = {
                                repeatMenu = false; draft = draft.copy(repeat = null)
                            })
                            REPEAT_CHOICES.forEach { rule ->
                                DropdownMenuItem(text = { Text(repeatLabel(rule).orEmpty()) }, onClick = {
                                    repeatMenu = false
                                    draft = draft.copy(repeat = rule, due = draft.due ?: today)
                                })
                            }
                        }
                    }
                    CardDivider()
                    PriorityRow(draft.priority) { draft = draft.copy(priority = it) }
                }

                // Passi.
                val doneSteps = steps.count { it.done }
                SectionLabel(
                    if (steps.isEmpty()) stringResource(R.string.tasks_steps)
                    else stringResource(R.string.tasks_steps_count, doneSteps, steps.size),
                    modifier = Modifier.padding(top = 2.dp),
                )
                GroupCard {
                    steps.forEachIndexed { i, step ->
                        if (i > 0) CardDivider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CheckCircle(
                                step.done, tintColor(list?.tint),
                                stringResource(if (step.done) R.string.tasks_mark_open else R.string.tasks_mark_done, step.title),
                                { viewModel.setDone(step, !step.done) }, size = 19,
                            )
                            Text(
                                step.title,
                                fontSize = 15.sp,
                                color = if (step.done) scheme.onSurfaceVariant else scheme.onSurface,
                                textDecoration = if (step.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.delete(step) }) {
                                Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.tasks_step_delete, step.title), tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (steps.isNotEmpty()) CardDivider()
                    var newStep by remember { mutableStateOf("") }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Icon(EuriseIcons.plus(), null, tint = scheme.primary, modifier = Modifier.size(19.dp))
                        }
                        Box(Modifier.weight(1f)) {
                            if (newStep.isEmpty()) Text(stringResource(R.string.tasks_step_add), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary)
                            BasicTextField(
                                value = newStep,
                                onValueChange = { newStep = it.replace("\n", "") },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface),
                                cursorBrush = SolidColor(scheme.primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    viewModel.addStep(taskId, newStep)
                                    newStep = ""
                                }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // Note e nota d'origine.
                GroupCard {
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp, end = 14.dp)) {
                        if (draft.notes.isEmpty()) Text(stringResource(R.string.tasks_field_notes), fontSize = 15.sp, color = scheme.onSurfaceVariant)
                        BasicTextField(
                            value = draft.notes,
                            onValueChange = { draft = draft.copy(notes = it) },
                            textStyle = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        )
                    }
                    draft.noteId?.let { noteId ->
                        Row(
                            Modifier
                                .padding(bottom = 12.dp)
                                .height(34.dp)
                                .clip(CircleShape)
                                .background(scheme.primaryContainer)
                                .clickable { close(); onOpenNote(noteId) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(EuriseIcons.note(), null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
                            Text(stringResource(R.string.tasks_open_note), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onPrimaryContainer)
                        }
                    }
                }

                linked(taskId, draft.title)
            }
        }
    }

    if (pickingDate) {
        // Il selettore lavora in millisecondi UTC a mezzanotte: si converte in LocalDate senza fuso.
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = (draft.due ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        draft = draft.copy(due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.tasks_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    draft = draft.copy(due = null, time = null, remindBefore = null, repeat = null)
                    pickingDate = false
                }) { Text(stringResource(R.string.tasks_no_date)) }
            },
        ) { DatePicker(state = picker) }
    }

    if (pickingTime) {
        val start = draft.time ?: LocalTime.of(9, 0)
        val picker = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    draft = draft.copy(time = LocalTime.of(picker.hour, picker.minute), due = draft.due ?: today)
                    pickingTime = false
                }) { Text(stringResource(R.string.tasks_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { draft = draft.copy(time = null); pickingTime = false }) {
                    Text(stringResource(R.string.tasks_all_day))
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.tasks_delete_title)) },
            text = { Text(stringResource(R.string.tasks_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    saved.deleted = true
                    viewModel.delete(original)
                    onClose()
                }) { Text(stringResource(R.string.tasks_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.tasks_cancel)) } },
        )
    }
}


/** Riga di un campo: simbolo, nome a sinistra, valore a destra; si tocca per cambiarlo. */
@Composable
private fun FieldRow(icon: ImageVector, label: String, value: String, dot: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = scheme.onSurfaceVariant.copy(alpha = alpha), modifier = Modifier.size(19.dp))
        Text(label, fontSize = 15.sp, color = scheme.onSurface.copy(alpha = alpha), modifier = Modifier.weight(1f))
        if (dot != null) Box(Modifier.size(9.dp).clip(CircleShape).background(tintColor(dot)))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface.copy(alpha = alpha))
    }
}

/** Importanza a tre livelli: No, Media, Alta. */
@Composable
private fun PriorityRow(priority: Int, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(EuriseIcons.flag(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        Text(stringResource(R.string.tasks_field_priority), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Row(Modifier.clip(RoundedCornerShape(16.dp)).background(scheme.surfaceVariant).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                Task.PRIORITY_NONE to R.string.tasks_priority_none,
                Task.PRIORITY_MEDIUM to R.string.tasks_priority_medium,
                Task.PRIORITY_HIGH to R.string.tasks_priority_high,
            ).forEach { (value, label) ->
                val selected = when (value) {
                    Task.PRIORITY_HIGH -> priority in 1..4
                    Task.PRIORITY_MEDIUM -> priority == 5
                    else -> priority == 0 || priority > 5
                }
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (selected) scheme.surfaceContainerLow else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(role = Role.RadioButton) { onChange(value) }
                        .semantics { this.selected = selected }
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(label),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = when {
                            !selected -> scheme.onSurfaceVariant
                            value == Task.PRIORITY_HIGH -> importantColors().first
                            else -> scheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun reminderLabel(minutes: Int?, hasTime: Boolean): String = when (minutes) {
    null -> stringResource(R.string.tasks_reminder_none)
    0 -> stringResource(if (hasTime) R.string.tasks_reminder_at_time else R.string.tasks_reminder_morning)
    1440 -> stringResource(R.string.tasks_reminder_day_before)
    60 -> stringResource(R.string.tasks_reminder_hour)
    else -> stringResource(R.string.tasks_reminder_minutes, minutes)
}


/** Cosa è già stato salvato della scheda aperta, per non riscriverla all'uscita. */
private class SavedMark(var task: Task) {
    var deleted = false
}

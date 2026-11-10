package eu.eurise.feature.notes.meeting

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.meetings.MeetingActions
import eu.eurise.core.data.meetings.MeetingStore
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.notes.R
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * La riunione di un'occorrenza di un evento: presenti da toccare, ordine del giorno da spuntare, appunti.
 * Alla chiusura le righe con @persona e una data diventano attività collegate all'evento.
 */
@Composable
fun MeetingRoute(
    data: EuriseData,
    eventId: Long,
    beginMillis: Long,
    onClose: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val context = LocalContext.current
    val labels = MeetingLabels(
        people = stringResource(R.string.meeting_people),
        absent = stringResource(R.string.meeting_absent),
        agenda = stringResource(R.string.meeting_agenda),
        notes = stringResource(R.string.meeting_notes),
        actions = stringResource(R.string.meeting_actions),
    )
    val viewModel = viewModel(key = "meeting:$eventId:$beginMillis") {
        MeetingViewModel(data, MeetingStore(context), labels, eventId, beginMillis) { title -> context.getString(R.string.meeting_task_note, title) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close = { viewModel.flush(onClose) }
    BackHandler(onBack = close)
    MeetingScreen(
        state = state,
        onClose = close,
        onTogglePerson = viewModel::togglePerson,
        onAddPerson = viewModel::addPerson,
        onRemovePerson = viewModel::removePerson,
        onToggleAgenda = viewModel::toggleAgenda,
        onAddAgenda = viewModel::addAgenda,
        onRemoveAgenda = viewModel::removeAgenda,
        onNotes = viewModel::onNotes,
        onCloseMeeting = { chosen -> viewModel.close(chosen) },
        onShare = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, state.title)
                .putExtra(Intent.EXTRA_TEXT, viewModel.summary())
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
        },
        onOpenNote = { viewModel.noteId(onOpenNote) },
    )
}

private val DueFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun MeetingScreen(
    state: MeetingState,
    onClose: () -> Unit,
    onTogglePerson: (String) -> Unit,
    onAddPerson: (String) -> Unit,
    onRemovePerson: (String) -> Unit,
    onToggleAgenda: (Int) -> Unit,
    onAddAgenda: (String) -> Unit,
    onRemoveAgenda: (Int) -> Unit,
    onNotes: (String) -> Unit,
    onCloseMeeting: (List<MeetingActions.Action>) -> Unit,
    onShare: () -> Unit,
    onOpenNote: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var closing by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.meeting_close_screen)) }
                },
                title = {
                    Column {
                        Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.meeting_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onShare, enabled = state.loaded && !state.missing) {
                        Icon(EuriseIcons.share(), contentDescription = stringResource(R.string.meeting_share))
                    }
                    IconButton(onClick = { menu = true }) { Icon(EuriseIcons.more(), contentDescription = stringResource(R.string.meeting_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.meeting_open_note)) }, onClick = { menu = false; onOpenNote() })
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        if (state.missing) {
            Text(
                stringResource(R.string.meeting_missing),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            return@Scaffold
        }
        val content = state.content
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (content.intro.isNotBlank()) {
                Text(content.intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle(stringResource(R.string.meeting_people))
            if (content.people.isEmpty()) Hint(stringResource(R.string.meeting_people_empty))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                content.people.forEach { person ->
                    var personMenu by remember(person.name) { mutableStateOf(false) }
                    androidx.compose.foundation.layout.Box {
                        // Un tocco segna la presenza, una pressione lunga apre "Togli".
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            color = if (person.present) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            border = if (person.present) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.combinedClickable(onClick = { onTogglePerson(person.name) }, onLongClick = { personMenu = true }),
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (person.present) {
                                    Icon(EuriseIcons.check(), null, Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                }
                                Text(person.name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        DropdownMenu(expanded = personMenu, onDismissRequest = { personMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.meeting_remove_person)) }, onClick = { personMenu = false; onRemovePerson(person.name) })
                        }
                    }
                }
            }
            AddField(stringResource(R.string.meeting_add_person), KeyboardCapitalization.Words, onAddPerson)

            SectionTitle(stringResource(R.string.meeting_agenda))
            content.agenda.forEachIndexed { index, item ->
                var itemMenu by remember(index, item.text) { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box {
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(onClick = { onToggleAgenda(index) }, onLongClick = { itemMenu = true }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item.done, onCheckedChange = { onToggleAgenda(index) })
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DropdownMenu(expanded = itemMenu, onDismissRequest = { itemMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.meeting_remove_item)) }, onClick = { itemMenu = false; onRemoveAgenda(index) })
                    }
                }
            }
            AddField(stringResource(R.string.meeting_add_item), KeyboardCapitalization.Sentences, onAddAgenda)

            SectionTitle(stringResource(R.string.meeting_notes))
            OutlinedTextField(
                value = content.notes,
                onValueChange = onNotes,
                placeholder = { Text(stringResource(R.string.meeting_notes_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
            if (state.pending.isNotEmpty()) {
                Hint(pluralStringResource(R.plurals.meeting_pending, state.pending.size, state.pending.size))
            }

            if (content.actions.isNotEmpty()) {
                SectionTitle(stringResource(R.string.meeting_actions))
                content.actions.forEach { Text("– " + it.title, style = MaterialTheme.typography.bodyLarge) }
            }

            Spacer(Modifier.size(4.dp))
            when {
                !state.closed -> Button(onClick = { closing = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.meeting_end))
                }
                state.pending.isNotEmpty() -> OutlinedButton(onClick = { closing = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.meeting_add_actions))
                }
            }
            Spacer(Modifier.navigationBarsPadding().size(24.dp))
        }
    }

    if (closing) {
        CloseSheet(
            pending = state.pending,
            onDismiss = { closing = false },
            onConfirm = { chosen -> closing = false; onCloseMeeting(chosen) },
            onShare = onShare,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloseSheet(
    pending: List<MeetingActions.Action>,
    onDismiss: () -> Unit,
    onConfirm: (List<MeetingActions.Action>) -> Unit,
    onShare: () -> Unit,
) {
    val skipped = remember(pending) { mutableStateListOf<Int>() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.meeting_end_title), style = MaterialTheme.typography.titleLarge)
            if (pending.isEmpty()) {
                Hint(stringResource(R.string.meeting_end_none))
            } else {
                Hint(stringResource(R.string.meeting_end_message))
                pending.forEach { action ->
                    val on = action.line !in skipped
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = on, onCheckedChange = { if (on) skipped += action.line else skipped -= action.line })
                        Column {
                            Text(action.title, style = MaterialTheme.typography.bodyLarge)
                            val due = action.due.format(DueFormat) + (action.time?.let { " · $it" } ?: "")
                            Text(due, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onShare) { Text(stringResource(R.string.meeting_share)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onConfirm(pending.filter { it.line !in skipped }) }) {
                    Text(stringResource(if (pending.isEmpty()) R.string.meeting_end_confirm_empty else R.string.meeting_end_confirm))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun AddField(placeholder: String, capitalization: KeyboardCapitalization, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) { onAdd(text); text = "" } }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(placeholder) },
        singleLine = true,
        trailingIcon = if (text.isNotBlank()) ({ IconButton(onClick = submit) { Icon(EuriseIcons.plus(), contentDescription = placeholder) } }) else null,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

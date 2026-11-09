package eu.eurise.core.links

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.EventDraft
import eu.eurise.core.data.model.Capture
import eu.eurise.core.data.model.LinkRef
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private const val LOCAL_CALENDAR_COLOR = 0xFF3E5C8A.toInt()

/** Con che cosa aprire il + unico: testo già scritto, persone già riconosciute (nome minuscolo → contatto), scelta già fatta. */
data class CaptureStart(
    val text: String = "",
    val people: Map<String, LinkRef> = emptyMap(),
    val target: Capture.Target? = null,
) {
    companion object {
        /** Apre il + per fissare un appuntamento con una persona. */
        fun invite(name: String, contact: LinkRef): CaptureStart {
            val token = Capture.mentionToken(name)
            if (token.isEmpty()) return CaptureStart(target = Capture.Target.Event)
            return CaptureStart("@$token ", mapOf(token.lowercase() to contact), Capture.Target.Event)
        }
    }
}

/**
 * Il + unico: una frase diventa evento, attività o nota. La scelta è proposta dal contenuto e si può cambiare;
 * le persone citate con "@" vengono collegate a ciò che nasce, e lo si ritrova nella loro scheda. Tutto resta sul telefono.
 *
 * @param onScan se presente, mostra il pulsante per scansionare un documento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureSheet(
    data: EuriseData,
    onDismiss: () -> Unit,
    onScan: (() -> Unit)? = null,
    start: CaptureStart = CaptureStart(),
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val resolver = remember(data) { LinkResolver(context, data) }
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(start.text, TextRange(start.text.length))) }
    var chosen by remember { mutableStateOf(start.target) }
    var busy by remember { mutableStateOf(false) }
    val people = remember { mutableStateMapOf<String, LinkRef>().apply { putAll(start.people) } }
    val lists by produceState(emptyList<TaskList>(), data) { value = data.tasks.observeLists().first().map { it.list } }
    val now = remember { LocalDateTime.now() }
    val parsed = remember(field.text, lists) { Capture.parse(field.text, now, lists.map { it.name }) }
    val target = chosen ?: parsed.suggested

    val pending = parsed.mentions.lastOrNull { it.name.lowercase() !in people }
    var suggestions by remember { mutableStateOf(emptyList<LinkItem>()) }
    LaunchedEffect(pending?.name) {
        delay(180)
        suggestions = pending?.name?.takeIf { it.length >= 2 }
            ?.let { resolver.search(it, setOf(LinkRef.Kind.Contact), perKind = 5) }.orEmpty()
    }

    val localCalendar = stringResource(R.string.capture_local_calendar)
    val failed = stringResource(R.string.capture_failed)
    val done = stringResource(
        when (target) {
            Capture.Target.Event -> R.string.capture_done_event
            Capture.Target.Task -> R.string.capture_done_task
            Capture.Target.Note -> R.string.capture_done_note
        }
    )

    suspend fun createEvent(): LinkRef? {
        val calendars = data.calendar.observeCalendars().first().filter { it.writable }
        val calendarId = (calendars.firstOrNull { !it.local } ?: calendars.firstOrNull())?.id
            ?: data.calendar.createLocalCalendar(localCalendar, LOCAL_CALENDAR_COLOR)
            ?: return null
        val q = parsed.quick
        val allDay = q.due != null && q.time == null
        val begin = when {
            allDay -> q.due!!.atStartOfDay()
            q.due != null -> q.due!!.atTime(q.time!!)
            else -> now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        }
        val end = if (allDay) begin.plusDays(1) else begin.plusHours(1)
        return data.calendar.save(EventDraft(null, calendarId, parsed.title, "", "", allDay, begin, end))?.let { LinkRef.event(it) }
    }

    fun create() {
        if (busy) return
        busy = true
        scope.launch {
            val q = parsed.quick
            val created = when (target) {
                Capture.Target.Event -> createEvent()
                Capture.Target.Task -> LinkRef.task(
                    data.tasks.add(
                        parsed.title, due = q.due, time = q.time, repeat = q.repeat, priority = q.priority,
                        listId = lists.firstOrNull { it.name == q.listName }?.id ?: TaskList.MAIN,
                        remindBefore = if (q.time != null) 0 else null,
                    )
                )
                Capture.Target.Note -> LinkRef.note(
                    data.notes.save(null, parsed.noteText.substringBefore('\n').trim(), parsed.noteText.substringAfter('\n', "").trim())
                )
            }
            if (created == null) {
                busy = false
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            parsed.mentions.map { it.name }.distinctBy { it.lowercase() }.forEach { name ->
                val contact = people[name.lowercase()]
                    ?: resolver.search(name, setOf(LinkRef.Kind.Contact), perKind = 2).singleOrNull()?.ref
                if (contact != null) data.links.link(created, contact)
            }
            Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (data.calendar.hasPermission()) create()
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val canCreate = !busy && (if (target == Capture.Target.Note) parsed.noteText else parsed.title).isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = scheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.capture_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            val pieceFill = scheme.primaryContainer
            val personFill = scheme.tertiaryContainer
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                placeholder = { Text(stringResource(R.string.capture_placeholder)) },
                visualTransformation = { original ->
                    val annotated = buildAnnotatedString {
                        append(original.text)
                        parsed.quick.pieces.filter { it.end <= original.text.length }.forEach {
                            addStyle(SpanStyle(background = pieceFill, fontWeight = FontWeight.SemiBold), it.start, it.end)
                        }
                        parsed.mentions.filter { it.end <= original.text.length }.forEach {
                            addStyle(SpanStyle(background = personFill, fontWeight = FontWeight.SemiBold), it.start, it.end)
                        }
                    }
                    TransformedText(annotated, OffsetMapping.Identity)
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = scheme.surfaceContainerLow,
                    focusedContainerColor = scheme.surfaceContainerLow,
                    focusedBorderColor = scheme.primary,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )

            if (pending != null && !resolver.canReadContacts()) {
                FilterPill(stringResource(R.string.capture_contacts_permission), null, false) {
                    contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                }
            } else if (suggestions.isNotEmpty() && pending != null) {
                LazyRow(contentPadding = PaddingValues(0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions, key = { it.ref.toString() }) { person ->
                        FilterPill(person.title, null, false) {
                            val token = Capture.mentionToken(person.title)
                            if (token.isNotEmpty()) {
                                people[token.lowercase()] = person.ref
                                val text = field.text.replaceRange(pending.start, pending.end, "@$token")
                                val caret = pending.start + token.length + 1
                                field = TextFieldValue(text, TextRange(caret))
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.capture_becomes), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceVariant).padding(3.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TargetOption(R.string.capture_event, target == Capture.Target.Event, Modifier.weight(1f)) { chosen = Capture.Target.Event }
                    TargetOption(R.string.capture_task, target == Capture.Target.Task, Modifier.weight(1f)) { chosen = Capture.Target.Task }
                    TargetOption(R.string.capture_note, target == Capture.Target.Note, Modifier.weight(1f)) { chosen = Capture.Target.Note }
                }
                if (parsed.noteText.isNotBlank()) {
                    Text(explanation(parsed, target, chosen == null), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onScan != null) {
                    IconButton(onClick = onScan, modifier = Modifier.size(44.dp).clip(CircleShape).background(scheme.surfaceContainerLow)) {
                        Icon(EuriseIcons.scan(), stringResource(R.string.capture_scan), tint = scheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = canCreate,
                    onClick = {
                        if (target == Capture.Target.Event && !data.calendar.hasPermission()) {
                            calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        } else {
                            create()
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            when (target) {
                                Capture.Target.Event -> R.string.capture_create_event
                                Capture.Target.Task -> R.string.capture_create_task
                                Capture.Target.Note -> R.string.capture_create_note
                            }
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun TargetOption(label: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) scheme.surface else androidx.compose.ui.graphics.Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(label),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

/** Una riga che dice perché è stata fatta la scelta, cosa resta fuori e dove si ritroverà la persona. */
@Composable
private fun explanation(parsed: Capture.Result, target: Capture.Target, automatic: Boolean): String {
    val q = parsed.quick
    val reason = when {
        !automatic && target == Capture.Target.Event && q.repeat != null -> stringResource(R.string.capture_why_event_repeat)
        !automatic && target == Capture.Target.Event && q.due == null -> stringResource(R.string.capture_why_event_no_day)
        !automatic && target == Capture.Target.Event && q.time == null -> stringResource(R.string.capture_why_event_all_day)
        !automatic -> ""
        target == Capture.Target.Event -> stringResource(R.string.capture_why_event)
        target == Capture.Target.Note -> stringResource(R.string.capture_why_note)
        q.due != null || q.repeat != null -> stringResource(R.string.capture_why_task_day)
        else -> stringResource(R.string.capture_why_task)
    }
    val names = parsed.mentions.map { it.name }.distinctBy { it.lowercase() }
    val person = if (names.isEmpty()) "" else stringResource(R.string.capture_why_people, names.joinToString(", "))
    return listOf(reason, person).filter { it.isNotEmpty() }.joinToString(" ")
}

package eu.eurise.feature.calendar.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.cloud.DavCalendar
import eu.eurise.core.cloud.Proposals
import eu.eurise.core.cloud.TaskSharing
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.data.calendar.DeviceCalendar
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.links.MailContact
import eu.eurise.feature.calendar.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val IT = Locale.ITALIAN
private val PersonTints = listOf(Color(0xFF4F6B5A), Color(0xFFA8742A), Color(0xFF5B6FA8), Color(0xFF8C2F39), Color(0xFF6D4AA8))
private val Hm = DateTimeFormatter.ofPattern("HH:mm", IT)
private val DayShort = DateTimeFormatter.ofPattern("EEE d", IT)

/**
 * Agenda con l'assistente (tavole CollAssistente e InsAssistente, approvate il 17 novembre): chi ti aiuta (segretaria,
 * collega) scrive gli appuntamenti che propone nel calendario «Proposte» del tuo cloud; qui li confermi, scegli un'altra ora
 * o li rifiuti, e decidi quali calendari vede. Senza assistente lo schermo serve a condividere l'agenda.
 */
@Composable
fun AssistantAgendaRoute(calendar: CalendarRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { AssistantAgenda.load(context) }

    var version by remember { mutableIntStateOf(0) }
    val assistant = remember(version) { Proposals.assistant(context) }
    val kind = remember(version) { Proposals.cloudKind(context) }
    val proposals by AssistantAgenda.proposals.collectAsState()
    val devices by remember { calendar.observeCalendars() }.collectAsState(initial = emptyList())
    val today = remember { LocalDate.now() }
    val week by remember { calendar.observeEvents(today, today.plusDays(6)) }.collectAsState(initial = emptyList())
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.asst_failed)
    val own by produceState<List<DavCalendar>?>(null, version, kind) {
        value = if (kind == null) emptyList() else runCatching { Proposals.ownCalendars(context) }.getOrElse { emptyList() }
    }

    fun act(block: suspend () -> Unit) {
        error = null
        scope.launch {
            try { block() } catch (e: Exception) { error = failed + (e.message?.let { " ($it)" } ?: "") }
            version++
        }
    }

    fun refresh() {
        if (assistant == null || refreshing) return
        refreshing = true
        scope.launch {
            try { AssistantAgenda.refresh(context) } catch (e: Exception) { error = failed + (e.message?.let { " ($it)" } ?: "") }
            refreshing = false
        }
    }
    LaunchedEffect(assistant?.first) { refresh() }

    val seenNames = own.orEmpty().filter { it.slug in Proposals.sees(context) }.map { it.name }
    var targetId by remember { mutableStateOf<Long?>(null) }
    val target = devices.firstOrNull { it.id == targetId } ?: AssistantAgenda.defaultTarget(context, devices, seenNames)
    var moving by remember { mutableStateOf<Proposal?>(null) }

    fun confirm(p: Proposal, start: LocalDateTime = p.event.start, end: LocalDateTime = p.event.end) {
        val cal = target ?: run { error = context.getString(R.string.asst_no_target); return }
        act { if (!AssistantAgenda.confirm(context, calendar, p, cal.id, start, end)) error = context.getString(R.string.asst_not_saved) }
    }

    val scheme = MaterialTheme.colorScheme
    val me = TaskSharing.me(context)?.first ?: stringResource(R.string.asst_me)
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Header(
                title = assistant?.let { stringResource(R.string.asst_title_with, it.second.substringBefore(' ')) } ?: stringResource(R.string.asst_title),
                subtitle = stringResource(if (assistant != null) R.string.asst_subtitle else R.string.asst_subtitle_setup),
                people = listOfNotNull(me, assistant?.second),
                refreshing = refreshing,
                onBack = onBack,
                onRefresh = if (assistant != null) ::refresh else null,
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                error?.let { e -> item("error") { Text(e, fontSize = 12.5.sp, color = scheme.error, modifier = Modifier.padding(horizontal = 6.dp)) } }
                if (assistant == null) {
                    item("setup") {
                        SetupCard(kind, own) { who, name, sees -> act { Proposals.setUp(context, who, name, sees) } }
                    }
                } else {
                    item("week") { WeekStrip(today, week.map { it.firstDay to it.color }, proposals.map { it.event.start.toLocalDate() }) }
                    item("pending-title") {
                        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.asst_pending, proposals.size), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.asst_pending_hint), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        }
                    }
                    if (proposals.isEmpty()) {
                        item("empty") {
                            Card {
                                Text(
                                    stringResource(R.string.asst_empty, assistant.second.substringBefore(' ')),
                                    fontSize = 13.5.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }
                    proposals.forEach { p ->
                        item(p.key) {
                            ProposalCard(
                                p = p,
                                barColor = target?.let { Color(it.color) } ?: scheme.primary,
                                onConfirm = { confirm(p) },
                                onOtherTime = { moving = p },
                                onDecline = { act { AssistantAgenda.decline(context, p) } },
                            )
                        }
                    }
                    item("target") { TargetRow(target, devices) { targetId = it.id; Proposals.setTarget(context, it.id) } }
                    item("sees") {
                        SeesCard(assistant.second, own, Proposals.sees(context)) { sees ->
                            act { Proposals.setUp(context, assistant.first, assistant.second, sees) }
                        }
                    }
                    item("privacy") {
                        PrivacyRow(stringResource(R.string.asst_privacy_on, assistant.second.substringBefore(' '), cloudName(kind)), kind)
                    }
                    item("stop") {
                        TextButton(onClick = { act { Proposals.stop(context) } }, modifier = Modifier.padding(horizontal = 2.dp)) {
                            Text(stringResource(R.string.asst_stop, assistant.second.substringBefore(' ')), color = scheme.error)
                        }
                    }
                }
            }
        }
        // «Conferma tutte»: in basso, dal lato del pollice, quando le proposte sono più di una.
        if (assistant != null && proposals.size > 1) {
            Button(
                onClick = { proposals.toList().forEach { confirm(it) } },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 20.dp).heightIn(min = 48.dp),
            ) { Text(pluralStringResource(R.plurals.asst_confirm_all, proposals.size, proposals.size), fontWeight = FontWeight.Bold) }
        }
    }

    moving?.let { p ->
        OtherTimeDialogs(p, onDismiss = { moving = null }) { start ->
            moving = null
            val length = Duration.between(p.event.start, p.event.end).takeIf { !it.isNegative && !it.isZero } ?: Duration.ofHours(1)
            confirm(p, start, start.plus(length))
        }
    }
}

@Composable
private fun cloudName(kind: CloudKind?): String =
    stringResource(if (kind == CloudKind.Murena) R.string.asst_cloud_murena else R.string.asst_cloud_nextcloud)

@Composable
private fun Header(
    title: String,
    subtitle: String,
    people: List<String>,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(EuriseIcons.chevronLeft(), stringResource(R.string.asst_back), tint = scheme.primary, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            Text(subtitle, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(EuriseIcons.refresh(), stringResource(R.string.asst_refresh), tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Row {
            people.take(2).forEachIndexed { i, name ->
                Box(Modifier.offset(x = (-8 * i).dp)) { PersonDot(name, 28.dp, ring = scheme.background) }
            }
        }
    }
}

/** Le iniziali nel tondo colorato, come nelle Attività condivise (stesso colore per lo stesso nome). */
@Composable
private fun PersonDot(name: String, size: Dp, ring: Color? = null) {
    val letters = name.split(' ', '.', '@').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        Modifier.size(size).then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .clip(CircleShape).background(PersonTints[(name.lowercase().hashCode() and 0x7fffffff) % PersonTints.size]),
        contentAlignment = Alignment.Center,
    ) { Text(letters, color = Color.White, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) { content() }
}

/** I prossimi sette giorni con un punto per appuntamento (nel colore del calendario) e uno grigio per proposta. */
@Composable
private fun WeekStrip(today: LocalDate, events: List<Pair<LocalDate, Int>>, proposals: List<LocalDate>) {
    val scheme = MaterialTheme.colorScheme
    Card {
        Row(Modifier.padding(6.dp)) {
            (0L until 7L).map { today.plusDays(it) }.forEach { day ->
                val proposed = proposals.count { it == day }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (proposed > 0) scheme.secondaryContainer else Color.Transparent).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, IT), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                    Text("${day.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        events.filter { it.first == day }.take(3).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(Color(it.second))) }
                        repeat(proposed.coerceAtMost(2)) { Box(Modifier.size(5.dp).clip(CircleShape).background(Color(AssistantAgenda.PROPOSAL_COLOR))) }
                    }
                }
            }
        }
    }
}

/** Una proposta (tavola CollAssistente): chi la propone e perché, l'appuntamento, Conferma / Altra ora / ✕. */
@Composable
private fun ProposalCard(p: Proposal, barColor: Color, onConfirm: () -> Unit, onOtherTime: () -> Unit, onDecline: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val e = p.event
    val first = p.by.substringBefore(' ')
    val time = if (e.allDay) stringResource(R.string.asst_all_day) else e.start.format(Hm) + "–" + e.end.format(Hm)
    val details = listOf(e.start.format(DayShort), time, e.location).filter { it.isNotBlank() }.joinToString(" · ")
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonDot(p.by, 28.dp)
                Text(
                    listOf(stringResource(R.string.asst_proposes, first), p.reason).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(barColor))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(details, fontSize = 13.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            // Conferma e Altra ora della stessa larghezza (regola sull'ergonomia); rifiuta è il tondo piccolo.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(scheme.primary)
                        .clickable(role = Role.Button, onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(EuriseIcons.check(), null, tint = scheme.onPrimary, modifier = Modifier.size(17.dp))
                        Text(stringResource(R.string.asst_confirm), color = scheme.onPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(scheme.secondaryContainer)
                        .clickable(role = Role.Button, onClick = onOtherTime),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.asst_other_time), color = scheme.onSecondaryContainer, fontSize = 14.5.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(scheme.surfaceVariant).clickable(role = Role.Button, onClick = onDecline),
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.close(), stringResource(R.string.asst_decline), tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

/** «Le confermate vanno in»: il calendario del telefono dove nascono gli appuntamenti confermati. */
@Composable
private fun TargetRow(target: DeviceCalendar?, devices: List<DeviceCalendar>, onPick: (DeviceCalendar) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val usable = devices.filter { it.writable && !AssistantAgenda.isProposalCalendar(it.name) }
    Card(Modifier.clickable(enabled = usable.size > 1) { open = true }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(target?.let { Color(it.color) } ?: scheme.outline))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.asst_target), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                Text(target?.name ?: stringResource(R.string.asst_no_target), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            }
            if (usable.size > 1) Icon(EuriseIcons.chevronDown(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            usable.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name + if (c.accountName.isNotBlank() && !c.local) " · " + c.accountName else "") },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.color))) },
                    onClick = { open = false; onPick(c) },
                )
            }
        }
    }
}

/** Non vede / Vede i titoli: due tasti della stessa larghezza. */
@Composable
private fun SeeSegmented(sees: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(scheme.surfaceVariant).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(false to R.string.asst_sees_no, true to R.string.asst_sees_titles).forEach { (value, label) ->
            val on = sees == value
            Box(
                Modifier.size(width = 84.dp, height = 32.dp).clip(RoundedCornerShape(11.dp)).background(if (on) scheme.surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { if (!on) onChange(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), fontSize = 12.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) scheme.primary else scheme.onSurfaceVariant)
            }
        }
    }
}

/** «Cosa vede Laura» (tavola InsAssistente): per ogni tuo calendario, niente o titoli e orari. */
@Composable
private fun SeesCard(name: String, own: List<DavCalendar>?, sees: Set<String>, onChange: (Set<String>) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.asst_sees_title, name.substringBefore(' ')).uppercase(IT), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
            when {
                own == null -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                own.isEmpty() -> Text(stringResource(R.string.asst_sees_none), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                else -> own.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name.ifBlank { c.slug }, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        SeeSegmented(c.slug in sees) { on -> onChange(if (on) sees + c.slug else sees - c.slug) }
                    }
                }
            }
            Text(stringResource(R.string.asst_sees_note), fontSize = 11.5.sp, lineHeight = 15.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrivacyRow(text: String, kind: CloudKind?) {
    val scheme = MaterialTheme.colorScheme
    val (score, where) = AssistantAgenda.privacy(kind)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(EuriseIcons.lock(), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        PrivacyMeter(score, where)
    }
}

/** La prima volta: chi ti aiuta, cosa vede, e «Condividi l'agenda». */
@Composable
private fun SetupCard(kind: CloudKind?, own: List<DavCalendar>?, onShare: (who: String, name: String, sees: Set<String>) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var who by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var sees by remember { mutableStateOf<Set<String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var hints by remember { mutableStateOf(emptyList<MailContact>()) }
    // Di base vede i titoli dei calendari di lavoro, nient'altro.
    LaunchedEffect(own) {
        if (sees == null && own != null) sees = own.filter { c -> listOf("lavoro", "work", "ufficio", "studio").any { c.name.contains(it, ignoreCase = true) } }.map { it.slug }.toSet()
    }
    LaunchedEffect(who) {
        delay(200)
        hints = if (who.isBlank() || who.trim().equals(name, ignoreCase = true)) emptyList()
        else eu.eurise.core.links.searchEmails(context, who).filter { !it.email.equals(who.trim(), ignoreCase = true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.asst_intro), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 6.dp))
        if (kind == null) {
            Card { Text(stringResource(R.string.asst_need_cloud), fontSize = 13.5.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(14.dp)) }
            return@Column
        }
        OutlinedTextField(
            value = who, onValueChange = { who = it; name = "" }, singleLine = true,
            placeholder = { Text(stringResource(R.string.asst_who)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().imePadding(),
        )
        if (hints.isNotEmpty()) {
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow)) {
                hints.forEach { h ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { who = h.email; name = h.name; hints = emptyList() }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PersonDot(h.name.ifBlank { h.email }, 34.dp)
                        Column(Modifier.weight(1f)) {
                            Text(h.name.ifBlank { h.email }, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(h.email, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.asst_who_hint), fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
        SeesCard(name.ifBlank { stringResource(R.string.asst_her) }, own, sees.orEmpty()) { sees = it }
        Button(
            enabled = !busy && who.isNotBlank(),
            onClick = { busy = true; onShare(who.trim(), name.ifBlank { who.trim().substringBefore('@') }, sees.orEmpty()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
            else Text(stringResource(R.string.asst_share), fontWeight = FontWeight.Bold)
        }
        PrivacyRow(stringResource(R.string.asst_privacy_setup, cloudName(kind)), kind)
        Spacer(Modifier.height(4.dp))
    }
}

/** «Altra ora»: il giorno e poi l'ora; la durata resta quella proposta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtherTimeDialogs(p: Proposal, onDismiss: () -> Unit, onPick: (LocalDateTime) -> Unit) {
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val chosen = day
    if (chosen == null) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = p.event.start.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: onDismiss()
                }) { Text(stringResource(R.string.asst_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cal_cancel)) } },
        ) { DatePicker(state = picker) }
    } else {
        val start = p.event.start.toLocalTime()
        val picker = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(chosen.format(DateTimeFormatter.ofPattern("EEEE d MMMM", IT)).replaceFirstChar { it.titlecase(IT) }) },
            confirmButton = { TextButton(onClick = { onPick(chosen.atTime(LocalTime.of(picker.hour, picker.minute))) }) { Text(stringResource(R.string.asst_confirm)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cal_cancel)) } },
            text = { TimePicker(state = picker) },
        )
    }
}

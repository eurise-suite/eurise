package eu.eurise.feature.tasks

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.cloud.DavMember
import eu.eurise.core.data.model.Handoff
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.model.TaskCollab
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.component.SwitchRow
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.links.MailContact
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PersonTints = listOf(Color(0xFF4F6B5A), Color(0xFFA8742A), Color(0xFF5B6FA8), Color(0xFF8C2F39), Color(0xFF6D4AA8))

/** Le iniziali di una persona nel suo tondo colorato (lo stesso colore ovunque per lo stesso nome). */
@Composable
internal fun PersonDot(name: String, size: Dp = 26.dp, ring: Color? = null) {
    val letters = name.split(' ', '.', '@').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        Modifier.size(size).then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .clip(CircleShape).background(PersonTints[(name.lowercase().hashCode() and 0x7fffffff) % PersonTints.size]),
        contentAlignment = Alignment.Center,
    ) {
        Text(letters, color = Color.White, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** «10:12» se è di oggi, «ieri», altrimenti il giorno. */
@Composable
internal fun whenLabel(millis: Long): String {
    val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    return when (day.toLocalDate()) {
        today -> day.format(TimeFormat)
        today.minusDays(1) -> stringResource(R.string.collab_yesterday)
        else -> day.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))
    }
}

/** I nomi in una frase: «Marta», «Marta e Luca», «Marta, Luca e Sara». */
@Composable
internal fun namesPhrase(names: List<String>): String {
    val first = names.map { it.substringBefore(' ') }
    return when (first.size) {
        0 -> ""
        1 -> first[0]
        else -> first.dropLast(1).joinToString(", ") + stringResource(R.string.collab_and) + first.last()
    }
}

@Composable
internal fun stepLabel(step: Handoff): String = stringResource(
    when (step) {
        Handoff.Assigned -> R.string.collab_step_assigned
        Handoff.Seen -> R.string.collab_step_seen
        Handoff.Started -> R.string.collab_step_started
        Handoff.Done -> R.string.collab_step_done
    },
)

/**
 * La riga condivisa sotto il titolo di un'attività (tavola CollElenchi): «preso da Marta · 10:12» in ottone, «fatto da
 * Luca · ieri» in salvia, «affidata a Laura · vista»; con la persona da mostrare a destra. Null se non c'è nulla.
 */
@Composable
internal fun collabLine(task: Task): Triple<String, Color, String>? {
    val c = task.collab
    val ottone = tintColor(CalendarTint.Ottone.name)
    val salvia = tintColor(CalendarTint.Salvia.name)
    return when {
        c.assigned -> {
            val who = c.assignee ?: c.assigneeEmail.orEmpty()
            val step = c.step(task.done) ?: Handoff.Assigned
            Triple(
                stringResource(R.string.collab_assigned_to, who.substringBefore(' '), stepLabel(step).lowercase()),
                if (task.done) salvia else ottone, who,
            )
        }
        task.done && c.doneBy != null -> Triple(
            stringResource(R.string.collab_done_by, c.doneBy!!.substringBefore(' '), task.completedAt?.let { whenLabel(it) }.orEmpty()).trimEnd(' ', '·'),
            salvia, c.doneBy!!,
        )
        !task.done && c.takenBy != null -> Triple(
            stringResource(R.string.collab_taken, c.takenBy!!.substringBefore(' '), c.takenAt?.let { whenLabel(it) }.orEmpty()).trimEnd(' ', '·'),
            ottone, c.takenBy!!,
        )
        else -> null
    }
}

/**
 * In cima a un elenco condiviso: le persone e come si usa («tocca per prendere, di nuovo per fatto»). Per un elenco tuo
 * il tocco apre «Condividi l'elenco».
 */
@Composable
internal fun SharedStrip(list: TaskList, me: String?, onManage: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val people = (listOfNotNull(list.owner) + list.members).distinct()
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow)
            .then(if (onManage != null) Modifier.clickable(onClick = onManage) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row {
            (listOfNotNull(me) + people).take(4).forEachIndexed { i, name ->
                Box(Modifier.offset(x = (-8 * i).dp)) { PersonDot(name, 28.dp, ring = scheme.surfaceContainerLow) }
            }
        }
        Text(
            stringResource(
                if (list.kind == TaskList.KIND_SHARED) R.string.collab_strip_from else R.string.collab_strip_mine,
                namesPhrase(people),
            ),
            fontSize = 12.5.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
    }
}

/** Il fondo scuro e il foglio che sale dal basso, come «Condividi con…» di Drive. */
@Composable
private fun Sheet(onClose: () -> Unit, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.background)
                .clickable(enabled = false) {}.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** La riga della privacy in fondo ai fogli: dove sta l'elenco e le tacchette. */
@Composable
private fun PrivacyNote(text: String) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val (score, where) = Collab.privacy(Collab.cloudKind(context))
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(EuriseIcons.lock(), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        PrivacyMeter(score, where)
    }
}

@Composable
private fun cloudName(): String = stringResource(
    if (Collab.cloudKind(LocalContext.current) == eu.eurise.core.cloud.CloudKind.Murena) R.string.collab_cloud_murena else R.string.collab_cloud_nextcloud,
)

/** Il campo per cercare una persona tra i contatti con un'email, con i suggerimenti sotto. */
@Composable
private fun PersonField(who: String, onWho: (String) -> Unit, placeholder: String, onPick: (MailContact) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var hints by remember { mutableStateOf(emptyList<MailContact>()) }
    LaunchedEffect(who) {
        delay(200)
        hints = eu.eurise.core.links.searchEmails(context, who).filter { !it.email.equals(who.trim(), ignoreCase = true) }
    }
    OutlinedTextField(
        value = who, onValueChange = onWho, singleLine = true,
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    if (hints.isNotEmpty()) {
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow)) {
            hints.forEach { h ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(h); hints = emptyList() }.padding(horizontal = 14.dp),
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
}

/** Vede / Modifica: due tasti della stessa larghezza (regola sull'ergonomia). */
@Composable
private fun EditSegmented(edit: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(scheme.surfaceVariant).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(false to R.string.collab_share_view, true to R.string.collab_share_edit).forEach { (value, label) ->
            val on = edit == value
            Box(
                Modifier.size(width = 72.dp, height = 34.dp).clip(RoundedCornerShape(11.dp)).background(if (on) scheme.surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { if (!on) onChange(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), fontSize = 12.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) scheme.primary else scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * «Condividi l'elenco» (tavola CollElenchi): una persona per email o nome utente dello stesso server, Vede o Modifica,
 * chi c'è già con il tocco per cambiare o togliere. L'elenco resta nel cloud dell'utente.
 */
@Composable
internal fun ShareListSheet(list: TaskList, onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var who by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ready = Collab.cloudKind(context) != null
    val people by produceState<List<DavMember>?>(null, reload) {
        value = if (!ready) emptyList() else runCatching { Collab.members(context, list.id) }.getOrElse { emptyList() }
    }
    val failed = stringResource(R.string.collab_share_failed)

    fun act(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try { block(); reload++ } catch (e: Exception) { error = failed + (e.message?.let { " ($it)" } ?: "") } finally { busy = false }
        }
    }

    Sheet(onClose) {
        SheetTitle(stringResource(R.string.collab_share_list), list.name)
        if (!ready) {
            Text(stringResource(R.string.collab_need_cloud), fontSize = 13.5.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            return@Sheet
        }
        PersonField(who, { who = it }, stringResource(R.string.collab_share_who)) { who = it.email }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.collab_share_hint), fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            EditSegmented(edit) { edit = it }
        }
        Button(
            enabled = !busy && who.isNotBlank(),
            onClick = { val w = who; act { Collab.share(context, list.id, w, edit); who = "" } },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
            else Text(stringResource(R.string.collab_share_do), fontWeight = FontWeight.Bold)
        }
        error?.let { Text(it, fontSize = 12.5.sp, color = scheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        val members = people
        when {
            members == null -> Text(stringResource(R.string.collab_share_pending), fontSize = 12.5.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            members.isNotEmpty() -> {
                Text(stringResource(R.string.collab_share_already, members.size), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow)) {
                    members.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PersonDot(m.name, 34.dp)
                            Text(m.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            EditSegmented(m.edit) { e ->
                                val target = m.href.removePrefix("mailto:").substringAfterLast('/')
                                act { Collab.share(context, list.id, target, e) }
                            }
                            IconButton(onClick = { act { Collab.unshare(context, list.id, m) } }) {
                                Icon(EuriseIcons.close(), stringResource(R.string.collab_share_remove, m.name), tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
        PrivacyNote(stringResource(R.string.collab_share_privacy, cloudName()))
    }
}

/** Le scadenze rapide di «Affida a…»: oggi, domani, tra una settimana. */
private enum class DueChoice(val days: Long, val label: Int) {
    Today(0, R.string.collab_due_today),
    Tomorrow(1, R.string.collab_due_tomorrow),
    Week(7, R.string.collab_due_week),
}

/**
 * «Affida a…» (tavola CollAffida): la persona (tra i contatti con un'email), entro quando, l'avviso quando è fatta e se
 * il giorno della scadenza alle 9 non è ancora partita, una riga per lei. [onAssign] riceve persona, scadenza, avviso e riga.
 */
@Composable
internal fun AssignSheet(
    task: Task,
    onAssign: (person: MailContact, due: LocalDate?, notify: Boolean, message: String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var who by remember { mutableStateOf("") }
    var person by remember { mutableStateOf<MailContact?>(null) }
    var due by remember { mutableStateOf(task.due) }
    var notify by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    val ready = Collab.cloudKind(context) != null

    Sheet(onClose) {
        SheetTitle(stringResource(R.string.collab_assign), task.title)
        val chosen = person
        if (chosen == null) {
            PersonField(who, { who = it }, stringResource(R.string.collab_assign_who)) { person = it; who = "" }
            val typed = who.trim()
            if ('@' in typed && '.' in typed.substringAfter('@')) {
                OutlinedButton(onClick = { person = MailContact("", typed); who = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.collab_assign_typed, typed), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonDot(chosen.name.ifBlank { chosen.email }, 34.dp)
                Column(Modifier.weight(1f)) {
                    Text(chosen.name.ifBlank { chosen.email }, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chosen.email, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { person = null }) {
                    Icon(EuriseIcons.close(), stringResource(R.string.collab_assign_change), tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }

        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(EuriseIcons.calendar(), null, tint = tintColor(CalendarTint.Ottone.name), modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.collab_by), fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                DueChoice.entries.forEach { c ->
                    val day = LocalDate.now().plusDays(c.days)
                    val on = due == day
                    Box(
                        Modifier.height(32.dp).clip(CircleShape).background(if (on) tintColor(CalendarTint.Ottone.name).copy(alpha = 0.16f) else scheme.onSurface.copy(alpha = 0.06f))
                            .clickable(role = Role.RadioButton) { due = if (on) null else day }.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(c.label), fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) tintColor(CalendarTint.Ottone.name) else scheme.onSurfaceVariant)
                    }
                }
            }
            due?.takeIf { d -> DueChoice.entries.none { LocalDate.now().plusDays(it.days) == d } }?.let { d ->
                Text(
                    eu.eurise.core.designsystem.format.dueLabel(d, LocalDate.now()).text,
                    fontSize = 12.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 40.dp, bottom = 6.dp),
                )
            }
            SwitchRow(
                title = stringResource(R.string.collab_notify),
                hint = stringResource(R.string.collab_notify_hint),
                checked = notify,
                onChange = { notify = it },
            )
            OutlinedTextField(
                value = message, onValueChange = { message = it.take(200) },
                label = { Text(stringResource(R.string.collab_message, (chosen?.name?.ifBlank { null } ?: chosen?.email ?: "").substringBefore(' '))) },
                placeholder = { Text(stringResource(R.string.collab_message_hint)) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (!ready) {
            Text(stringResource(R.string.collab_assign_local), fontSize = 12.5.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        val name = (chosen?.name?.ifBlank { null } ?: chosen?.email.orEmpty()).substringBefore(' ')
        PrivacyNote(stringResource(R.string.collab_assign_privacy, name.ifEmpty { stringResource(R.string.collab_someone) }, cloudName()))
        Button(
            enabled = chosen != null,
            onClick = { chosen?.let { onAssign(it, due, notify, message.trim()) } },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(EuriseIcons.send(), null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (chosen == null) stringResource(R.string.collab_assign) else stringResource(R.string.collab_assign_do, name), fontWeight = FontWeight.Bold)
        }
    }
}

/** Apre l'app della posta con il messaggio per chi riceve la cosa affidata (finché Chat ed Email non la portano da sole). */
internal fun mailAssignee(context: android.content.Context, task: Task) {
    val c = task.collab
    val email = c.assigneeEmail ?: return
    val body = buildString {
        c.message?.takeIf { it.isNotBlank() }?.let { appendLine(it); appendLine() }
        appendLine(task.title)
        task.due?.let { appendLine(context.getString(R.string.collab_mail_due, it.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)))) }
    }
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(email)))
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.collab_mail_subject, task.title))
        .putExtra(Intent.EXTRA_TEXT, body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * «Come va» (tavola CollAffida): i quattro passi con l'ora in cui sono arrivati. Chi l'ha ricevuta ha «Ho iniziato»;
 * chi l'ha affidata «Scrivile un'email» e «Riprendi».
 */
@Composable
internal fun HandoffCard(task: Task, receiving: Boolean, onStart: () -> Unit, onMail: () -> Unit, onTakeBack: () -> Unit) {
    val c = task.collab
    val scheme = MaterialTheme.colorScheme
    val salvia = tintColor(CalendarTint.Salvia.name)
    val step = c.step(task.done) ?: return
    val times = mapOf(Handoff.Assigned to c.assignedAt, Handoff.Seen to c.seenAt, Handoff.Started to c.startedAt, Handoff.Done to task.completedAt)
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PersonDot(if (receiving) c.owner ?: "?" else c.assignee ?: c.assigneeEmail.orEmpty(), 30.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    if (receiving) stringResource(R.string.collab_assigned_by, c.owner ?: stringResource(R.string.collab_someone))
                    else stringResource(R.string.collab_assigned_to_full, c.assignee ?: c.assigneeEmail.orEmpty()),
                    fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                )
                c.message?.takeIf { it.isNotBlank() }?.let { Text("«$it»", fontSize = 12.5.sp, color = scheme.onSurfaceVariant) }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Handoff.entries.forEach { s ->
                val reached = s.ordinal <= step.ordinal
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (reached) salvia else scheme.onSurface.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                        if (reached) Icon(EuriseIcons.check(3f), null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    val at = times[s]?.takeIf { reached }
                    Text(
                        stepLabel(s) + (at?.let { "\n" + whenLabel(it) } ?: ""),
                        fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center,
                        fontWeight = if (s == step) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (reached) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!task.done) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (receiving) {
                if (step.ordinal < Handoff.Started.ordinal) Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.collab_start), fontWeight = FontWeight.Bold) }
            } else {
                OutlinedButton(onClick = onMail, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.collab_assign_email), maxLines = 1) }
                OutlinedButton(onClick = onTakeBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.collab_unassign), maxLines = 1) }
            }
        }
    }
}

/** La parte condivisa dopo «Affida a…»: chi, da chi, la riga, l'avviso, il primo passo con l'ora. */
internal fun assignment(person: MailContact, me: Pair<String, String?>?, notify: Boolean, message: String, now: Long): TaskCollab = TaskCollab(
    assignee = person.name.ifBlank { null }, assigneeEmail = person.email.trim(),
    handoff = Handoff.Assigned, assignedAt = TaskCollab.stamp(now),
    message = message.ifBlank { null }, owner = me?.first, ownerEmail = me?.second, notify = notify,
)

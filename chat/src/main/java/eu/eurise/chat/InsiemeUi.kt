@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package eu.eurise.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.insieme.Disponibilita
import eu.eurise.core.insieme.Riunione
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GREEN = Color(0xFF4F6B5A)
private val AMBER = Color(0xFF85672B)

// --- L'agenda con l'assistente (tavola InsAssistente) ---

/** Nella chat a due: «Fai vedere la tua agenda» — per ogni calendario: mai, libero/occupato, con i titoli. */
@Composable
internal fun AgendaShareSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var cals by remember { mutableStateOf<List<eu.eurise.core.insieme.Agenda.Cal>>(emptyList()) }
    val modes = remember { androidx.compose.runtime.mutableStateMapOf<Long, Assistente.Mode>().apply { putAll(model.agendaModes(roomId)) } }
    LaunchedEffect(Unit) { actions.askCalendar { allowed = it } }
    LaunchedEffect(allowed) { if (allowed == true) model.calendars { cals = it } }
    val peer = model.peerOf(roomId)?.let { model.nameOf(it).substringBefore(' ') }.orEmpty()
    Sheet(onClose) {
        Text(stringResource(R.string.as_title, peer), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Text(stringResource(R.string.as_note), fontSize = 12.5.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        if (allowed == false) Text(stringResource(R.string.di_no_calendar), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        Card {
            cals.forEachIndexed { i, c ->
                if (i > 0) Divider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (c.account.isNotBlank() && c.account != c.name) Text(c.account, fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val m = modes[c.id] ?: Assistente.Mode.Never
                        Chip(stringResource(R.string.as_never), m == Assistente.Mode.Never) { modes[c.id] = Assistente.Mode.Never }
                        Chip(stringResource(R.string.as_busy), m == Assistente.Mode.Busy) { modes[c.id] = Assistente.Mode.Busy }
                        Chip(stringResource(R.string.as_titles), m == Assistente.Mode.Titles) { modes[c.id] = Assistente.Mode.Titles }
                    }
                }
            }
        }
        Pill(stringResource(R.string.as_save), EuriseIcons.check(2.4f), Modifier.fillMaxWidth(), strong = true) {
            model.shareAgenda(roomId, modes.toMap())
            actions.toast(actions.text(if (modes.values.any { it != Assistente.Mode.Never }) R.string.as_on else R.string.as_off))
            onClose()
        }
    }
}

/** La segreteria vede l'agenda condivisa e propone un appuntamento: i liberi dei prossimi giorni, il titolo, una riga. */
@Composable
internal fun ProposeSheet(model: ChatModel, actions: ChatActions, roomId: String, shared: Assistente.Shared, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    var duration by remember { mutableStateOf(60) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<Long?>(null) }
    val failed = stringResource(R.string.ch_send_failed)
    val busy = shared.blocks.map { Disponibilita.Busy(it.start, it.end, it.title) }
    val today = java.time.LocalDate.now()
    val slots = remember(duration, shared.updated) { Disponibilita.free(busy, today, 10, duration, zone, System.currentTimeMillis(), max = 12) }
    val who = model.peerOf(roomId)?.let { model.nameOf(it).substringBefore(' ') }.orEmpty()
    val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    val hour = DateTimeFormatter.ofPattern("H:mm", Locale.getDefault())
    Sheet(onClose) {
        Text(stringResource(R.string.as_agenda_of, who), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Text(stringResource(R.string.as_updated, Disponibilita.label(shared.updated, zone)), fontSize = 11.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        // I prossimi impegni, come li vede la segreteria (titoli solo se lui li fa vedere).
        Card {
            shared.blocks.filter { it.end > System.currentTimeMillis() }.take(8).forEachIndexed { i, b ->
                if (i > 0) Divider()
                val t = Instant.ofEpochMilli(b.start).atZone(zone)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(t.format(day) + " · " + t.format(hour) + "–" + Instant.ofEpochMilli(b.end).atZone(zone).format(hour), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    Text(b.title.ifBlank { stringResource(R.string.as_busy_word) }, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallField(title, stringResource(R.string.as_what)) { title = it.take(100) }
            SmallField(note, stringResource(R.string.as_note_hint)) { note = it.take(200) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(30, 60, 120).forEach { d -> Chip(model.durationLabel(d), duration == d) { duration = d } } }
            Text(stringResource(R.string.di_free), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                slots.forEach { s -> Chip(Disponibilita.label(s.start, zone), pick == s.start) { pick = s.start } }
            }
        }
        Pill(stringResource(R.string.as_propose), EuriseIcons.send(2f), Modifier.fillMaxWidth(), strong = true) {
            val start = pick ?: return@Pill
            if (title.isBlank()) return@Pill
            model.propose(roomId, title, note, start, start + duration * 60_000L) { actions.toast(failed) }
            onClose()
        }
    }
}

/** In cima alla chat a due: l'agenda condivisa (per chi la riceve, «Proponi»; per chi la condivide, cosa esce). */
@Composable
internal fun AgendaBar(model: ChatModel, actions: ChatActions, roomId: String) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val shared = model.peerAgenda(roomId)
    val mine = model.agendaModes(roomId).isNotEmpty()
    var propose by remember(roomId) { mutableStateOf(false) }
    var share by remember(roomId) { mutableStateOf(false) }
    if (shared == null && !mine) return
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.08f))
        .clickable(role = Role.Button) { if (shared != null) propose = true else share = true }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(EuriseIcons.calendar(2f), null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        val peer = model.peerOf(roomId)?.let { model.nameOf(it).substringBefore(' ') }.orEmpty()
        Text(if (shared != null) stringResource(R.string.as_bar_peer, peer) else stringResource(R.string.as_bar_mine, peer), fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (shared != null) Text(stringResource(R.string.as_propose), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
    }
    if (propose && shared != null) ProposeSheet(model, actions, roomId, shared) { propose = false }
    if (share) AgendaShareSheet(model, actions, roomId) { share = false }
}

// --- Elenchi condivisi (tavola InsElenchi) ---

/** La scheda «Elenchi» del gruppo: Spesa, Casa, Ufficio (e altri), con chi lo prende e chi l'ha fatto. */
@Composable
internal fun ListsPane(model: ChatModel, actions: ChatActions, roomId: String) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val lists = model.lists(roomId)
    var current by remember(roomId) { mutableStateOf(lists.firstOrNull()?.id.orEmpty()) }
    var draft by remember(roomId) { mutableStateOf("") }
    var newList by remember(roomId) { mutableStateOf<String?>(null) }
    var mailing by remember(roomId) { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.ch_send_failed)
    val now = System.currentTimeMillis()
    val l = lists.firstOrNull { it.id == current } ?: lists.firstOrNull()
    fun save(next: List<Elenchi.Lista>) = model.saveLists(roomId, next) { actions.toast(failed) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lists.forEach { x -> Chip(x.name + (Elenchi.open(x).takeIf { it > 0 }?.let { " · $it" } ?: ""), x.id == l?.id) { current = x.id } }
            Chip("+", false) { newList = "" }
        }
        newList?.let { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SmallField(n, stringResource(R.string.el_new_hint)) { newList = it.take(30) } }
                Spacer(Modifier.width(6.dp))
                Chip(stringResource(R.string.el_add), true) { save(Elenchi.addList(lists, n, System.currentTimeMillis())); newList = null }
            }
        }
        if (l == null) return@Column
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SmallField(draft, stringResource(R.string.el_item_hint, l.name.lowercase())) { draft = it.take(80) } }
            Spacer(Modifier.width(6.dp))
            Chip(stringResource(R.string.el_add), true) {
                if (draft.isBlank()) return@Chip
                save(Elenchi.add(lists, l.id, draft, me, System.currentTimeMillis())); draft = ""
            }
        }
        Card {
            val items = Elenchi.shown(l, now)
            if (items.isEmpty()) Text(stringResource(R.string.el_empty), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
            items.forEachIndexed { i, it ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(if (it.doneAt > 0) GREEN else scheme.onSurface.copy(alpha = 0.08f))
                        .clickable(role = Role.Checkbox) { save(Elenchi.done(lists, l.id, it.id, me, System.currentTimeMillis())) }, contentAlignment = Alignment.Center) {
                        if (it.doneAt > 0) Icon(EuriseIcons.check(2.6f), null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(it.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textDecoration = if (it.doneAt > 0) TextDecoration.LineThrough else null)
                        val sub = when {
                            it.doneAt > 0 -> stringResource(R.string.el_done_by, if (it.doneBy == me) stringResource(R.string.fa_you) else model.nameOf(it.doneBy).substringBefore(' '),
                                Disponibilita.label(it.doneAt, ZoneId.systemDefault()))
                            it.taker.isNotEmpty() -> stringResource(R.string.el_taken_by, if (it.taker == me) stringResource(R.string.fa_you) else model.nameOf(it.taker).substringBefore(' '))
                            else -> stringResource(R.string.el_added_by, if (it.by == me) stringResource(R.string.fa_you) else model.nameOf(it.by).substringBefore(' '))
                        }
                        Text(sub, fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    }
                    if (it.doneAt == 0L && it.taker != me) Chip(stringResource(R.string.el_take), false) { save(Elenchi.take(lists, l.id, it.id, me)) }
                    else if (it.doneAt == 0L) Chip(stringResource(R.string.el_leave), false) { save(Elenchi.take(lists, l.id, it.id, me)) }
                    else Chip("✕", false) { save(Elenchi.remove(lists, l.id, it.id)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(stringResource(R.string.el_share_text), false) { actions.shareText(Elenchi.text(l)) }
            Chip(stringResource(R.string.el_mail), false) { mailing = "" }
        }
        mailing?.let { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SmallField(m, stringResource(R.string.cv_emails)) { mailing = it.take(200) } }
                Spacer(Modifier.width(6.dp))
                Chip(stringResource(R.string.ch_send), true) {
                    val mails = m.split(',', ';', ' ').map { it.trim() }.filter { it.contains('@') }
                    if (mails.isNotEmpty()) model.mailList(roomId, l, mails) { actions.toast(actions.text(R.string.af_mail_failed)) }
                    mailing = null
                }
            }
        }
        Text(stringResource(R.string.el_note), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 90.dp))
    }
}

// --- La nota della riunione (tavola InsRiunione) ---

/** La scheda «Riunione» del gruppo: una nota che scrivono tutti; le righe «@persona cosa entro» diventano cose da fare assegnate. */
@Composable
internal fun MeetingPane(model: ChatModel, actions: ChatActions, roomId: String) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val saved = model.meeting(roomId)
    var title by remember(roomId, saved?.at) { mutableStateOf(saved?.title.orEmpty()) }
    var text by remember(roomId, saved?.at) { mutableStateOf(saved?.text.orEmpty()) }
    val failed = stringResource(R.string.ch_send_failed)
    val people = model.members[roomId].orEmpty().map { it to model.nameOf(it) }
    val tasks = remember(text, people.size) { Riunione.tasks(text, people, me, java.time.LocalDate.now()) }
    val decisions = remember(text) { Riunione.decisions(text) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        saved?.let { s ->
            Text(stringResource(R.string.ri_last, if (s.by == me) stringResource(R.string.fa_you) else model.nameOf(s.by).substringBefore(' '),
                Disponibilita.label(s.at, ZoneId.systemDefault())), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
        }
        SmallField(title, stringResource(R.string.ri_title_hint)) { title = it.take(80) }
        Box(Modifier.fillMaxWidth().heightIn(min = 220.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(14.dp)) {
            if (text.isEmpty()) Text(stringResource(R.string.ri_hint), fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurfaceVariant)
            BasicTextField(value = text, onValueChange = { text = it.take(8000) }, textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
        }
        Pill(stringResource(R.string.ri_save), EuriseIcons.check(2.4f), Modifier.fillMaxWidth(), strong = false) { model.saveMeeting(roomId, title, text) { actions.toast(failed) } }
        if (tasks.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.primary.copy(alpha = 0.08f)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ri_found, tasks.size), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ri_found_note), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
            tasks.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((t.who?.let { if (it == me) stringResource(R.string.fa_you) else model.nameOf(it).substringBefore(' ') } ?: ("@" + t.name + " ?")) + " · " + t.text,
                        fontSize = 13.sp, modifier = Modifier.weight(1f), color = if (t.who == null) AMBER else scheme.onSurface)
                    t.due?.let { Text(it.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())), fontSize = 12.sp, color = scheme.onSurfaceVariant) }
                }
            }
            Pill(stringResource(R.string.ri_assign), EuriseIcons.task(2f), Modifier.fillMaxWidth(), strong = true) {
                model.saveMeeting(roomId, title, text) {}
                model.assignMeeting(roomId, tasks) { actions.toast(failed) }
                actions.toast(actions.text(R.string.ri_assigned))
            }
        }
        // Il registro delle decisioni (tavola LavDecisioni): le frasi «decidiamo che…» proposte, poi l'elenco del gruppo.
        val register = model.decisions(roomId)
        decisions.filter { d -> register.none { it.text.equals(d, true) } }.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ri_decisions, d), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Chip(stringResource(R.string.dc_add), false) { model.addDecision(roomId, d, me, "nota", "") { actions.toast(failed) } }
            }
        }
        Text(stringResource(R.string.dc_title, register.size), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
        if (register.isEmpty()) Text(stringResource(R.string.dc_empty), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        else Card {
            register.forEachIndexed { i, d ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(Disponibilita.label(d.at, ZoneId.systemDefault()).substringBefore(" · ") + " · " + model.nameOf(d.who).substringBefore(' ') + " · " +
                            stringResource(when (d.source) { "mail" -> R.string.dc_src_mail; "nota" -> R.string.dc_src_note; else -> R.string.dc_src_chat }), fontSize = 11.5.sp,
                            color = scheme.onSurfaceVariant)
                    }
                    Text("✕", fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.clickable(role = Role.Button) { model.removeDecision(roomId, d.id) { actions.toast(failed) } }.padding(6.dp))
                }
            }
        }
        Spacer(Modifier.heightIn(min = 90.dp))
    }
}

// --- La cartella di squadra (tavola InsSquadra) ---

/**
 * La scheda «File» del gruppo: chi c'è dentro, cosa è cambiato da ieri (file e foto arrivati, persone entrate), tutti i file
 * del gruppo e il link alla cartella di squadra sul cloud (Murena o Nextcloud), che chi esce dalla squadra non apre più.
 */
@Composable
internal fun TeamPane(model: ChatModel, actions: ChatActions, roomId: String) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val all = model.timeline(roomId)
    LaunchedEffect(roomId) { model.loadExpenses(roomId) }
    val files = all.filter { it.kind == Event.Kind.File || it.kind == Event.Kind.Image }.asReversed()
    val since = System.currentTimeMillis() - 86_400_000L
    val recent = all.filter { it.time >= since && (it.kind == Event.Kind.File || it.kind == Event.Kind.Image || (it.kind == Event.Kind.State && it.text.startsWith("join:"))) }
    val folder = model.teamFolder(roomId)
    var editing by remember(roomId) { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.ch_send_failed)
    val fileFailed = stringResource(R.string.ch_file_failed)
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tm_folder), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (folder != null && editing == null) {
                Text(folder, fontSize = 12.5.sp, color = scheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(role = Role.Button) { actions.openBrowser(folder) })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(stringResource(R.string.tm_open), true) { actions.openBrowser(folder) }
                    Chip(stringResource(R.string.tm_change), false) { editing = folder }
                }
            } else {
                Text(stringResource(R.string.tm_folder_hint), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SmallField(editing.orEmpty(), stringResource(R.string.tm_link_hint)) { editing = it.trim().take(300) } }
                    Spacer(Modifier.width(6.dp))
                    Chip(stringResource(R.string.as_save), true) {
                        val u = editing.orEmpty()
                        if (u.startsWith("https://")) model.setTeamFolder(roomId, u) { actions.toast(failed) }
                        editing = null
                    }
                }
            }
        }
        Text(stringResource(R.string.tm_who), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            model.members[roomId].orEmpty().forEach { u -> Text(if (u == me) stringResource(R.string.fa_you) else personLabel(model, u), fontSize = 12.5.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(scheme.surface).padding(horizontal = 10.dp, vertical = 5.dp)) }
        }
        Text(stringResource(R.string.tm_since, recent.size), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
        if (recent.isNotEmpty()) Card {
            recent.asReversed().take(10).forEachIndexed { i, e ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val who = if (e.sender == me) stringResource(R.string.fa_you) else model.nameOf(e.sender).substringBefore(' ')
                        Text(if (e.kind == Event.Kind.State) stringResource(R.string.tm_joined, e.text.substringAfter(':')) else stringResource(R.string.tm_added, who, e.text),
                            fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(Instant.ofEpochMilli(e.time).atZone(zone).format(DateTimeFormatter.ofPattern("H:mm", Locale.getDefault())), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
        Text(stringResource(R.string.tm_files, files.size), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
        Card {
            if (files.isEmpty()) Text(stringResource(R.string.tm_no_files), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
            files.take(60).forEachIndexed { i, e ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { model.media(e) { f -> if (f != null) actions.openFile(f) else actions.toast(fileFailed) } }
                    .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (e.kind == Event.Kind.Image) EuriseIcons.image(2f) else EuriseIcons.document(2f), null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.text.ifBlank { "—" }, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(model.nameOf(e.sender).substringBefore(' ') + " · " + Disponibilita.label(e.time, zone), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(stringResource(R.string.tm_note), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 90.dp))
    }
}

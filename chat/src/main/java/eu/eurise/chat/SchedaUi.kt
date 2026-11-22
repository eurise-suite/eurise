@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package eu.eurise.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.insieme.Scheda
import eu.eurise.core.insieme.SchedaMail
import org.json.JSONObject
import java.time.LocalDateTime

private val GREEN = Color(0xFF4F6B5A)
private val BLUE = Color(0xFF3E5C8A)
private val AMBER = Color(0xFF85672B)
private val RED = Color(0xFFA4502E)
private val GREY = Color(0xFF56564F)

@Composable
internal fun SmallField(value: String, hint: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().heightIn(min = 42.dp).clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(hint, fontSize = 13.sp, color = scheme.onSurfaceVariant)
        BasicTextField(value = value, onValueChange = onChange, textStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun CardStateChip(st: Scheda.State?) {
    val label = st?.let { stringResource(SchedaMail.label(it)) } ?: stringResource(R.string.af_st_given)
    val color = when (st) {
        Scheda.State.Done, Scheda.State.Yes, Scheda.State.Paid, Scheda.State.Confirmed -> GREEN
        Scheda.State.Seen, Scheda.State.Working -> BLUE
        Scheda.State.Declined, Scheda.State.No, Scheda.State.Other -> RED
        Scheda.State.Late -> AMBER
        Scheda.State.Back, null -> GREY
    }
    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp))
}

/**
 * Affida a… (tavola InsAffida): a chi (le persone delle tue chat a due, o un indirizzo email per chi non usa Eurise), entro
 * quando, una riga, «se non risponde entro 1 giorno torna a te».
 */
@Composable
internal fun EntrustSheet(model: ChatModel, actions: ChatActions, roomId: String, start: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.runtime.LaunchedEffect(Unit) { model.loadRoles() }
    // Le proposte partono dai ruoli (tavola InsRuoli): prima collaboratori, segreteria, colleghi.
    val people = model.colleagues().sortedBy { eu.eurise.core.insieme.Ruoli.order(model.roleOf(it.second)) }
    val here = model.room(roomId)?.takeIf { it.direct }?.let { r -> people.firstOrNull { it.first == r.id } }
    var to by remember { mutableStateOf(here ?: people.firstOrNull()) }
    var email by remember { mutableStateOf("") }
    var byMail by remember { mutableStateOf(people.isEmpty()) }
    var title by remember { mutableStateOf(start.take(120)) }
    var note by remember { mutableStateOf("") }
    val now = remember { LocalDateTime.now() }
    val evening = now.toLocalDate().atTime(18, 0).takeIf { now.hour < 17 }
    val tomorrow = Programma.tomorrowAt(now, 12)
    val friday = remember {
        var d = now.toLocalDate().plusDays(2)
        while (d.dayOfWeek != java.time.DayOfWeek.FRIDAY && d.dayOfWeek != java.time.DayOfWeek.MONDAY) d = d.plusDays(1)
        d.atTime(12, 0)
    }
    var due by remember { mutableStateOf<LocalDateTime?>(tomorrow) }
    var back by remember { mutableStateOf(1) }
    val failed = { id: Int -> actions.toast(actions.text(id)) }
    Sheet(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(EuriseIcons.task(2.2f), null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.af_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallField(title, stringResource(R.string.af_what)) { title = it.take(120) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                people.forEach { p -> Chip(personLabel(model, p.second), !byMail && to == p) { to = p; byMail = false } }
                Chip(stringResource(R.string.af_by_mail), byMail) { byMail = !byMail }
            }
            if (byMail) SmallField(email, stringResource(R.string.af_mail_hint)) { email = it.trim().take(80) }
            Text(stringResource(R.string.af_due), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                evening?.let { Chip(stringResource(R.string.pa_evening), due == it) { due = if (due == it) null else it } }
                Chip(stringResource(R.string.pa_tomorrow), due == tomorrow) { due = if (due == tomorrow) null else tomorrow }
                Chip(friday.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("EEE d", java.util.Locale.getDefault())), due == friday) { due = if (due == friday) null else friday }
            }
            Text(stringResource(R.string.af_line, if (byMail) email.substringBefore('@').ifBlank { "…" } else to?.let { model.nameOf(it.second).substringBefore(' ') } ?: "…"),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            SmallField(note, stringResource(R.string.af_note_hint)) { note = it.take(300) }
            Text(stringResource(R.string.af_back), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.af_back_never), back == 0) { back = 0 }
                Chip(stringResource(R.string.af_back_days, 1), back == 1) { back = 1 }
                Chip(stringResource(R.string.af_back_days, 2), back == 2) { back = 2 }
            }
        }
        val target = to
        val name = if (byMail) email.substringBefore('@') else target?.let { model.nameOf(it.second).substringBefore(' ') }.orEmpty()
        Pill(stringResource(R.string.af_go, name.ifBlank { "…" }), EuriseIcons.send(2f), Modifier.fillMaxWidth(), strong = true) {
            if (title.isBlank()) return@Pill
            if (byMail && (!email.contains('@') || !email.contains('.'))) return@Pill
            if (!byMail && target == null) return@Pill
            model.entrust(target?.first, target?.second.orEmpty(), if (byMail) email else "", title, note, due, back * 86_400_000L, failed)
            actions.toast(actions.text(R.string.af_sent))
            onClose()
        }
    }
}

/**
 * Una scheda nella conversazione (tavola InsStessaScheda): viva, cifrata nella stanza; chi la riceve ha i tasti, chi l'ha
 * mandata vede lo stato che cambia (vista quando l'altro l'ha letta).
 */
@Composable
internal fun CardBubble(model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean, onLong: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val card = remember(e.data) { Scheda.card(e.data?.let { runCatching { JSONObject(it) }.getOrNull() }) } ?: return
    val me = model.session?.userId.orEmpty()
    val answers = model.cardAnswers(roomId, e, card.id)
    val mineAnswer = answers[me]
    val others = answers.filterKeys { it != e.sender }
    val failed = stringResource(R.string.ch_send_failed)
    val context = androidx.compose.ui.platform.LocalContext.current
    val w = remember { SchedaMail.words(context) }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surface).border(1.5.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .combinedClickable(onClick = {}, onLongClick = onLong).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.task(2.2f), null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (mine) stringResource(R.string.af_you_gave, w.tag(card.kind)) else stringResource(R.string.af_given_by, w.tag(card.kind), card.from),
                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (mine && card.kind == Scheda.Kind.Affida) {
                    val peer = model.peerOf(roomId)
                    val read = peer?.let { model.readers[roomId]?.get(it) } ?: 0L
                    CardStateChip(Scheda.shown(others.values.lastOrNull()?.state, e.time, read))
                }
            }
            Text(card.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (card.due.isNotBlank()) Text(if (card.kind == Scheda.Kind.Affida) w.due(card.due) else card.due, fontSize = 12.sp, color = AMBER, fontWeight = FontWeight.Bold)
            if (card.note.isNotBlank()) Text("«" + card.note + "»", fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
            // Le risposte delle altre persone (gruppi): chi e cosa.
            if (card.kind != Scheda.Kind.Affida && others.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                others.forEach { (who, a) ->
                    Text(model.nameOf(who).substringBefore(' ') + " · " + (a.state?.let { stringResource(SchedaMail.label(it)) }
                        ?: a.choices?.sorted()?.joinToString(", ") { (it + 1).toString() }.orEmpty()), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                }
            }
            if (card.kind == Scheda.Kind.Orari) SlotChoices(model, actions, roomId, e, card, mine, answers)
            if (mine && card.kind == Scheda.Kind.Proposta) answers.filterKeys { it != me }.values.lastOrNull()?.let { CardStateChip(it.state) }
            val forMe = card.to.isEmpty() || card.to == me
            if (!mine && forMe && card.kind != Scheda.Kind.Orari) {
                val done = mineAnswer?.state in setOf<Scheda.State?>(Scheda.State.Done, Scheda.State.Declined, Scheda.State.Paid, Scheda.State.Confirmed, Scheda.State.Other)
                if (done) CardStateChip(mineAnswer?.state)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val list = Scheda.actions(card.kind)
                    list.forEachIndexed { i, st ->
                        if (mineAnswer?.state == st) return@forEachIndexed
                        Chip(stringResource(SchedaMail.label(st)), i == list.lastIndex) {
                            model.answerCard(roomId, e, card.id, Scheda.Answer(st)) { actions.toast(failed) }
                            // Conferma (tavola InsAssistente): l'appuntamento entra nel tuo calendario.
                            if (st == Scheda.State.Confirmed && card.dueAt > 0) model.toCalendar(card.title, card.dueAt, card.endAt.takeIf { it > card.dueAt } ?: (card.dueAt + 3_600_000L),
                                "", card.note, actions::launch) { ok -> if (ok) actions.toast(actions.text(R.string.di_in_calendar)) }
                        }
                    }
                }
            }
            // Un appuntamento condiviso (tavola InsCondividi): «Aggiungi al mio calendario».
            if (!mine && card.kind == Scheda.Kind.Condividi && card.dueAt > 0) Chip(stringResource(R.string.cv_add_calendar), false) {
                model.toCalendar(card.title, card.dueAt, card.endAt.takeIf { it > card.dueAt } ?: (card.dueAt + 3_600_000L), card.note, "", actions::launch) { ok ->
                    if (ok) actions.toast(actions.text(R.string.di_in_calendar))
                }
            }
            if (card.kind == Scheda.Kind.Condividi) Text(stringResource(if (card.edit) R.string.cv_can_edit else R.string.cv_can_see), fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }
    }
}

/** «Le cose affidate» (dal foglio dell'account): a chi, lo stato, «tornata a te» con Sollecita e Togli. */
@Composable
internal fun GivenSheet(model: ChatModel, actions: ChatActions, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.af_list), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("${model.given.size}", fontSize = 13.sp, color = scheme.onSurfaceVariant)
        }
        if (model.given.isEmpty()) Text(stringResource(R.string.af_list_empty), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        else Card {
            model.given.sortedByDescending { it.sentAt }.take(40).forEachIndexed { i, g ->
                if (i > 0) Divider()
                val read = model.readers[g.room]?.get(g.toUser) ?: 0L
                val st = Affida.shown(g, read, now)
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button, enabled = !g.byMail) { onClose(); model.open(g.room) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (g.byMail) EuriseIcons.mail(2.2f) else EuriseIcons.chat(2.2f), null, tint = GREEN, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(g.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        CardStateChip(st)
                    }
                    Text("→ " + g.toName + (if (g.due.isNotBlank()) " · " + g.due else ""), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    if (g.note.isNotBlank()) Text("«" + g.note + "»", fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    if (st == Scheda.State.Back && !g.retaken) Text(stringResource(R.string.af_back_note), fontSize = 11.5.sp, color = AMBER)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (st == Scheda.State.Back || st == null || st == Scheda.State.Seen) {
                            if (!g.byMail) Chip(stringResource(R.string.ap_remind), false) { model.remindGiven(g.id) { actions.toast(failed) } }
                            if (st != Scheda.State.Back) Chip(stringResource(R.string.af_retake), false) { model.retakeGiven(g.id) }
                        }
                        Chip(stringResource(R.string.af_forget), false) { model.forgetGiven(g.id) }
                    }
                }
            }
        }
    }
}

/** «Paolo · collaboratore»: il nome con il ruolo della Rubrica, se c'è. */
@Composable
internal fun personLabel(model: ChatModel, userId: String): String {
    val name = model.nameOf(userId).substringBefore(' ')
    return model.roleOf(userId)?.let { name + " · " + stringResource(eu.eurise.core.insieme.Ruoli.label(it)).lowercase() } ?: name
}

/** Persone e ruoli (tavola InsRuoli) dalla Chat: le persone delle chat a due con il loro ruolo; lo salva la Rubrica. */
@Composable
internal fun RolesSheet(model: ChatModel, actions: ChatActions, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.runtime.LaunchedEffect(Unit) { model.loadRoles() }
    val missing = stringResource(eu.eurise.core.insieme.R.string.ins_roles_missing)
    Sheet(onClose) {
        Text(stringResource(eu.eurise.core.insieme.R.string.ins_roles_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Text(stringResource(eu.eurise.core.insieme.R.string.ins_roles_note), fontSize = 12.5.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        val people = model.colleagues().map { it.second }.distinct().sortedBy { eu.eurise.core.insieme.Ruoli.order(model.roleOf(it)) }
        Card {
            people.forEachIndexed { i, u ->
                if (i > 0) Divider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(model.nameOf(u), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val now = model.roleOf(u)
                        eu.eurise.core.insieme.Ruoli.Role.entries.forEach { r ->
                            Chip(stringResource(eu.eurise.core.insieme.Ruoli.label(r)), now == r) { model.setRole(u, if (now == r) null else r) { actions.toast(missing) } }
                        }
                    }
                }
            }
        }
        Text(stringResource(eu.eurise.core.insieme.R.string.ins_roles_where), fontSize = 11.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** Gli orari liberi nella scheda (tavola InsDisponibilita): chi riceve sceglie con un tocco; poi «Aggiungi al calendario». */
@Composable
private fun SlotChoices(model: ChatModel, actions: ChatActions, roomId: String, e: Event, card: Scheda.Card, mine: Boolean, answers: Map<String, Scheda.Answer>) {
    val scheme = MaterialTheme.colorScheme
    val failed = stringResource(R.string.ch_send_failed)
    val chosen = answers.entries.firstOrNull { it.key != e.sender && !it.value.choices.isNullOrEmpty() }
    val pick = chosen?.value?.choices?.minOrNull()
    val expired = pick == null && card.dueAt in 1 until System.currentTimeMillis()
    if (pick != null) {
        val start = card.times.getOrNull(pick) ?: return
        val end = start + card.durationMin * 60_000L
        Text(stringResource(R.string.di_chosen, model.nameOf(chosen?.key.orEmpty()).substringBefore(' '), card.options.getOrNull(pick).orEmpty()), fontSize = 13.sp,
            fontWeight = FontWeight.Bold, color = GREEN)
        if (!mine) Chip(stringResource(R.string.cv_add_calendar), false) {
            model.toCalendar(card.title, start, end, "", "", actions::launch) { ok -> if (ok) actions.toast(actions.text(R.string.di_in_calendar)) }
        }
        return
    }
    if (expired) { Text(stringResource(R.string.di_expired), fontSize = 12.sp, color = scheme.onSurfaceVariant); return }
    if (mine) { Text(stringResource(R.string.di_waiting, card.options.size), fontSize = 12.sp, color = scheme.onSurfaceVariant); return }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        card.options.forEachIndexed { i, o -> Chip(o, false) { model.answerCard(roomId, e, card.id, Scheda.Answer(choices = setOf(i))) { actions.toast(failed) } } }
    }
    Text(stringResource(R.string.di_note), fontSize = 11.sp, color = scheme.onSurfaceVariant)
}

/** Disponibilità in un link (tavola InsDisponibilita): la durata, gli orari liberi della settimana prossima, scade fra 7 giorni. */
@Composable
internal fun SlotsSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var duration by remember { mutableStateOf(60) }
    var slots by remember { mutableStateOf<List<eu.eurise.core.insieme.Disponibilita.Slot>?>(null) }
    val off = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    val failed = stringResource(R.string.ch_send_failed)
    androidx.compose.runtime.LaunchedEffect(Unit) { actions.askCalendar { allowed = it } }
    androidx.compose.runtime.LaunchedEffect(duration, allowed) { if (allowed == true) { slots = null; model.freeSlots(duration) { slots = it } } }
    Sheet(onClose) {
        Text(stringResource(R.string.di_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.di_duration), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(30, 60, 120).forEach { d -> Chip(model.durationLabel(d), duration == d) { duration = d } } }
            Text(stringResource(R.string.di_free), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            when {
                allowed == false -> Text(stringResource(R.string.di_no_calendar), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                slots == null -> Text(stringResource(R.string.di_loading), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                slots!!.isEmpty() -> Text(stringResource(R.string.di_none), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    slots!!.forEach { sl ->
                        Chip(eu.eurise.core.insieme.Disponibilita.label(sl.start, java.time.ZoneId.systemDefault()), sl.start !in off) {
                            if (sl.start in off) off.remove(sl.start) else off.add(sl.start)
                        }
                    }
                }
            }
            Text(stringResource(R.string.di_privacy), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
        }
        val chosen = slots.orEmpty().filter { it.start !in off }
        Pill(stringResource(R.string.di_send, chosen.size), EuriseIcons.send(2f), Modifier.fillMaxWidth(), strong = true) {
            if (chosen.isEmpty()) return@Pill
            model.sendSlots(roomId, duration, chosen) { actions.toast(failed) }
            onClose()
        }
    }
}

/**
 * Condividi con… un appuntamento (tavola InsCondividi): l'impegno dal calendario, le persone proposte per ruolo (vede o
 * modifica), e per email chi non usa Eurise. Arriva con «Aggiungi al mio calendario».
 */
@Composable
internal fun ShareEventSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var items by remember { mutableStateOf<List<eu.eurise.core.insieme.Agenda.Item>?>(null) }
    var item by remember { mutableStateOf<eu.eurise.core.insieme.Agenda.Item?>(null) }
    val chosen = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, String>>() }
    var edit by remember { mutableStateOf(false) }
    var emails by remember { mutableStateOf("") }
    val zone = java.time.ZoneId.systemDefault()
    androidx.compose.runtime.LaunchedEffect(Unit) { model.loadRoles(); actions.askCalendar { allowed = it } }
    androidx.compose.runtime.LaunchedEffect(allowed) { if (allowed == true) model.upcoming { items = it } }
    val people = model.colleagues().sortedBy { eu.eurise.core.insieme.Ruoli.order(model.roleOf(it.second)) }
    androidx.compose.runtime.LaunchedEffect(people.size, model.roles.size) {
        if (chosen.isEmpty()) model.room(roomId)?.takeIf { it.direct }?.let { r -> people.firstOrNull { it.first == r.id }?.let { chosen.add(it) } }
    }
    Sheet(onClose) {
        Text(stringResource(R.string.cv_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        val it0 = item
        if (it0 == null) {
            when {
                allowed == false -> Text(stringResource(R.string.di_no_calendar), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                items == null -> Text(stringResource(R.string.di_loading), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                items!!.isEmpty() -> Text(stringResource(R.string.cv_none), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                else -> Card {
                    items!!.forEachIndexed { i, x ->
                        if (i > 0) Divider()
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { item = x }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(x.title.ifBlank { "—" }, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(eu.eurise.core.insieme.Disponibilita.range(x.start, x.end, zone) + (if (x.place.isNotBlank()) " · " + x.place else ""), fontSize = 12.sp,
                                color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
            return@Sheet
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(it0.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(eu.eurise.core.insieme.Disponibilita.range(it0.start, it0.end, zone) + (if (it0.place.isNotBlank()) " · " + it0.place else ""), fontSize = 12.5.sp,
                color = scheme.onSurfaceVariant)
            Text(stringResource(R.string.cv_who), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                people.forEach { p -> Chip(personLabel(model, p.second), p in chosen) { if (p in chosen) chosen.remove(p) else chosen.add(p) } }
            }
            SmallField(emails, stringResource(R.string.cv_emails)) { emails = it.take(300) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(eu.eurise.core.insieme.R.string.ins_level_vede), !edit) { edit = false }
                Chip(stringResource(eu.eurise.core.insieme.R.string.ins_level_modifica), edit) { edit = true }
            }
            Text(stringResource(R.string.cv_how), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
        }
        val mails = emails.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.contains('@') && it.contains('.') }
        Pill(stringResource(R.string.cv_go, chosen.size + mails.size), EuriseIcons.share(2f), Modifier.fillMaxWidth(), strong = true) {
            if (chosen.isEmpty() && mails.isEmpty()) return@Pill
            model.shareEvent(it0, chosen.toList(), mails, edit) { id -> actions.toast(actions.text(id)) }
            actions.toast(actions.text(R.string.cv_done))
            onClose()
        }
    }
}

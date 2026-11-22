@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package eu.eurise.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.insieme.Scheda
import eu.eurise.core.insieme.SchedaMail
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GREEN = Color(0xFF4F6B5A)
private val AMBER = Color(0xFF85672B)
private val RED = Color(0xFFA4502E)

/** Le scadenze proposte per «Affida» nello Scrivi: stasera, domani, venerdì (o lunedì se è già venerdì). */
internal fun dueChoices(context: android.content.Context, now: LocalDateTime = LocalDateTime.now()): List<Pair<String, Long>> {
    val zone = java.time.ZoneId.systemDefault()
    fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    val fmt = DateTimeFormatter.ofPattern("EEE d", Locale.ITALIAN)
    val out = mutableListOf<Pair<String, Long>>()
    if (now.hour < 17) out += context.getString(R.string.ml_cd_today) to ms(now.toLocalDate().atTime(18, 0))
    out += context.getString(R.string.ml_cd_tomorrow) to ms(now.toLocalDate().plusDays(1).atTime(12, 0))
    var d: LocalDate = now.toLocalDate().plusDays(2)
    while (d.dayOfWeek != java.time.DayOfWeek.FRIDAY && d.dayOfWeek != java.time.DayOfWeek.MONDAY) d = d.plusDays(1)
    out += d.format(fmt) to ms(d.atTime(12, 0))
    return out
}

/** Le righe dello Scrivi per una scheda: Affida (entro quando) o Uscita (le date da votare). */
@Composable
internal fun CardComposer(kind: Scheda.Kind, due: Pair<String, Long>?, onDue: (Pair<String, Long>?) -> Unit, options: SnapshotStateList<String>) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.08f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (kind == Scheda.Kind.Affida) R.string.ml_cd_affida_hint else R.string.ml_cd_uscita_hint), fontSize = 12.5.sp, lineHeight = 17.sp,
            color = scheme.onSurfaceVariant)
        if (kind == Scheda.Kind.Affida) {
            val choices = remember { dueChoices(context) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.ml_cd_due), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
                choices.forEach { c -> BallChip(c.first, due == c) { onDue(if (due == c) null else c) } }
            }
        } else {
            options.forEachIndexed { i, o ->
                Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(horizontal = 12.dp, vertical = 9.dp)) {
                    if (o.isEmpty()) Text(stringResource(R.string.ml_cd_option_hint, i + 1), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    BasicTextField(value = o, onValueChange = { options[i] = it.take(60) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
                }
            }
            if (options.size < 5) BallChip("+ " + stringResource(R.string.ml_cd_option_add), false) { options.add("") }
        }
    }
}

/** Una scheda ricevuta in Eurise (oggetto «[Da fare] …» con il riferimento): gli stessi tasti della Chat, rispondono con le parole dei pulsanti. */
@Composable
internal fun CardAnswer(model: MailModel, actions: MailActions, o: Opened) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val w = remember { SchedaMail.words(context) }
    val kind = SchedaMail.kindOf(o.summary.subject, w) ?: return
    if (SchedaMail.ref(o.text) == null) return
    var answered by remember(o.summary.uid) { mutableStateOf(o.summary.answered) }
    val chosen = remember(o.summary.uid) { androidx.compose.runtime.mutableStateListOf<Int>() }
    val failed = stringResource(R.string.ml_send_failed)
    val sent = stringResource(R.string.ml_cd_answered_toast)
    fun go(word: String) = model.answerCard(o, word) { ok -> if (ok) { answered = true; actions.toast(sent) } else actions.toast(failed) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.10f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ml_cd_received, w.tag(kind)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (answered) { Text(stringResource(R.string.ml_ap_answered), fontSize = 12.sp, color = scheme.onSurfaceVariant); return@Column }
        if (Scheda.choosing(kind)) {
            // Le scelte numerate nel testo: «1 · sab 29, 20:00».
            val opts = remember(o.text) { Regex("(?m)^(\\d{1,2}) · (.+)$").findAll(o.text).map { it.groupValues[1].toInt() to it.groupValues[2].trim() }.toList() }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                opts.forEach { (n, label) -> BallChip("$n · $label", n in chosen) { if (Scheda.single(kind)) { chosen.clear(); chosen.add(n) } else if (n in chosen) chosen.remove(n) else chosen.add(n) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BallChip(stringResource(R.string.ml_cd_none), false) { go(context.getString(R.string.ml_cd_none_word)) }
                BallChip(stringResource(R.string.ml_send), true) { if (chosen.isNotEmpty()) go(chosen.sorted().joinToString(" e ")) }
            }
        } else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val list = Scheda.actions(kind)
            list.forEachIndexed { i, st -> BallChip(context.getString(SchedaMail.label(st)), i == list.lastIndex) {
                go(context.getString(SchedaMail.replyWord(st)))
                // Un appuntamento confermato o condiviso: l'invito è allegato all'email.
                if (st == Scheda.State.Confirmed && o.attachments.isNotEmpty()) actions.toast(context.getString(R.string.ml_cd_invite_hint))
            } }
        }
    }
}

@Composable
private fun StateLabel(st: Scheda.State?, waiting: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val (text, color) = when {
        st != null -> stringResource(SchedaMail.label(st)) to when (st) {
            Scheda.State.Done, Scheda.State.Yes, Scheda.State.Paid -> GREEN
            Scheda.State.Declined, Scheda.State.No, Scheda.State.Back -> RED
            else -> scheme.primary
        }
        waiting -> stringResource(R.string.ml_cd_silent) to AMBER
        else -> stringResource(R.string.ml_cd_sent_state) to scheme.onSurfaceVariant
    }
    Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

/** «Schede mandate per email»: chi ha risposto e cosa, Sollecita dopo un giorno; per le uscite i voti e Fissa. */
@Composable
internal fun MailCardsList(model: MailModel, actions: MailActions, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val w = remember { SchedaMail.words(context) }
    val now = System.currentTimeMillis()
    val failed = stringResource(R.string.ml_send_failed)
    val reminded = stringResource(R.string.ml_cd_reminded)
    BallDialog(onClose) {
        Text(stringResource(R.string.ml_cd_list), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.ml_cd_list_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        val list = model.cards.filter { !it.closed }.sortedByDescending { it.sentAt }
        if (list.isEmpty()) Text(stringResource(R.string.ml_cd_empty), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        list.take(40).forEach { s ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("[" + w.tag(s.card.kind) + "] " + s.card.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (s.card.origin.startsWith("chat:")) Text(stringResource(R.string.ml_cd_from_chat), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                if (Scheda.choosing(s.card.kind)) {
                    val counts = Scheda.counts(s.card, s.answers.mapValues { it.value.answer })
                    s.card.options.forEachIndexed { i, o ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1} · $o", fontSize = 13.sp, fontWeight = if (s.fixed == i) FontWeight.ExtraBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.ml_cd_votes, counts[i]), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                            if (s.fixed < 0 && s.card.origin == "mail") BallChip(stringResource(R.string.ml_cd_fix), false) {
                                model.fixCard(s.card.id, i) { ok -> if (!ok) actions.toast(failed) }
                            }
                        }
                    }
                    if (s.fixed >= 0) Text(stringResource(R.string.ml_cd_fixed, s.card.options.getOrNull(s.fixed).orEmpty()), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = GREEN)
                }
                s.to.forEach { addr ->
                    val a = s.answers[addr.lowercase()]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(addr, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                        if (Scheda.choosing(s.card.kind)) Text(a?.answer?.choices?.let { c -> if (c.isEmpty()) stringResource(R.string.ml_cd_none) else c.sorted().joinToString(", ") { (it + 1).toString() } }
                            ?: stringResource(if (now - s.sentAt >= MailCards.QUIET_MS) R.string.ml_cd_silent else R.string.ml_cd_sent_state), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (a != null) GREEN else scheme.onSurfaceVariant)
                        else StateLabel(a?.answer?.state, now - s.sentAt >= MailCards.QUIET_MS)
                    }
                    a?.answer?.note?.takeIf { it.isNotBlank() }?.let { Text("«$it»", fontSize = 12.sp, color = scheme.onSurfaceVariant) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (MailCards.silent(s, now).isNotEmpty()) BallChip(stringResource(R.string.ml_ap_remind), false) {
                        model.remindCard(s.card.id) { ok -> actions.toast(if (ok) reminded else failed) }
                    }
                    BallChip(stringResource(R.string.ml_cd_close), false) { model.closeCard(s.card.id) }
                }
            }
        }
    }
}

/** Persone e ruoli (tavola InsRuoli) dall'Email: le persone con cui scrivi di più, con il loro ruolo; lo salva la Rubrica. */
@Composable
internal fun MailRolesList(model: MailModel, actions: MailActions, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.runtime.LaunchedEffect(Unit) { model.loadRoles() }
    val missing = stringResource(eu.eurise.core.insieme.R.string.ins_roles_missing)
    val mine = model.accounts.map { it.email.lowercase() }.toSet()
    val people = remember(model.messages.size) { MailBalls.people(model.messages, mine, max = 20) }
    BallDialog(onClose) {
        Text(stringResource(eu.eurise.core.insieme.R.string.ins_roles_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(eu.eurise.core.insieme.R.string.ins_roles_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        people.forEach { (addr, name) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                Text(addr, fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val now = model.roleOf(addr)
                    eu.eurise.core.insieme.Ruoli.Role.entries.forEach { r ->
                        BallChip(stringResource(eu.eurise.core.insieme.Ruoli.label(r)), now == r) { model.setRole(addr, name, if (now == r) null else r) { actions.toast(missing) } }
                    }
                }
            }
        }
        Text(stringResource(eu.eurise.core.insieme.R.string.ins_roles_where), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
    }
}

/** Disponibilità nello Scrivi (tavola InsDisponibilitaMail): la durata e gli orari liberi, che si tolgono con un tocco. */
@Composable
internal fun SlotsComposer(model: MailModel, actions: MailActions, duration: Int, onDuration: (Int) -> Unit,
                           slots: List<eu.eurise.core.insieme.Disponibilita.Slot>?, onSlots: (List<eu.eurise.core.insieme.Disponibilita.Slot>?) -> Unit,
                           off: SnapshotStateList<Long>) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { actions.askCalendar { allowed = it } }
    androidx.compose.runtime.LaunchedEffect(duration, allowed) { if (allowed == true) { onSlots(null); model.freeSlots(duration) { onSlots(it) } } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.08f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ml_cd_slots_hint), fontSize = 12.5.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ml_cd_duration), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
            listOf(30, 60, 120).forEach { d -> BallChip(durationLabel(context, d), duration == d) { onDuration(d) } }
        }
        when {
            allowed == false -> Text(stringResource(R.string.ml_cd_no_calendar), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
            slots == null -> Text(stringResource(R.string.ml_cd_loading), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
            slots.orEmpty().isEmpty() -> Text(stringResource(R.string.ml_cd_no_slots), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                slots.orEmpty().forEach { sl -> BallChip(eu.eurise.core.insieme.Disponibilita.label(sl.start, java.time.ZoneId.systemDefault()), sl.start !in off) {
                    if (sl.start in off) off.remove(sl.start) else off.add(sl.start) } }
            }
        }
    }
}

internal fun durationLabel(context: android.content.Context, min: Int) =
    if (min < 60) context.getString(R.string.ml_cd_min, min) else context.getString(R.string.ml_cd_hours, min / 60)

/** Un appuntamento dal calendario nello Scrivi (tavola InsCondividi): si sceglie, parte con l'invito allegato. */
@Composable
internal fun EventComposer(model: MailModel, actions: MailActions, chosen: eu.eurise.core.insieme.Agenda.Item?, onChoose: (eu.eurise.core.insieme.Agenda.Item?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var items by remember { mutableStateOf<List<eu.eurise.core.insieme.Agenda.Item>?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { actions.askCalendar { allowed = it } }
    androidx.compose.runtime.LaunchedEffect(allowed) { if (allowed == true) model.upcoming { items = it } }
    val zone = java.time.ZoneId.systemDefault()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.08f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ml_cd_share_hint), fontSize = 12.5.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        if (chosen != null) {
            Text(chosen.title + " · " + eu.eurise.core.insieme.Disponibilita.range(chosen.start, chosen.end, zone), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            BallChip(stringResource(R.string.ml_cd_pick_event), false) { onChoose(null) }
        } else when {
            allowed == false -> Text(stringResource(R.string.ml_cd_no_calendar), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
            items == null -> Text(stringResource(R.string.ml_cd_loading), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
            items!!.isEmpty() -> Text(stringResource(R.string.ml_cd_no_events), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
            else -> items!!.forEach { x ->
                Text(x.title.ifBlank { "—" } + " · " + eu.eurise.core.insieme.Disponibilita.range(x.start, x.end, zone), fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(scheme.surface)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onChoose(x) }.padding(10.dp))
            }
        }
    }
}

/** «Spesa di 528,00 € · Aggiungi alle spese di un gruppo»: si apre la Chat, si sceglie il gruppo, la spesa è già scritta. */
@Composable
internal fun ExpenseFromMail(actions: MailActions, o: Opened) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val cents = remember(o.summary.uid) { eu.eurise.core.insieme.Importi.total(o.text) } ?: return
    if (SchedaMail.ref(o.text) != null) return
    val title = remember(o.summary.uid) { o.summary.fromName.ifBlank { o.summary.subject }.take(60) }
    val intent = remember(o.summary.uid) { eu.eurise.core.insieme.SchedaContract.expenseIntent(context, cents, title) } ?: return
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.08f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ml_se_found, "%d,%02d €".format(cents / 100, cents % 100)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ml_se_hint), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
        }
        BallChip(stringResource(R.string.ml_se_add), true) { actions.start(intent) }
    }
}

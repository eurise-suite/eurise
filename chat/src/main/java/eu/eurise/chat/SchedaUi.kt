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
        Scheda.State.Done, Scheda.State.Yes, Scheda.State.Paid -> GREEN
        Scheda.State.Seen, Scheda.State.Working -> BLUE
        Scheda.State.Declined, Scheda.State.No -> RED
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
    val people = remember { model.colleagues() }
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
                people.forEach { p -> Chip(model.nameOf(p.second).substringBefore(' '), !byMail && to == p) { to = p; byMail = false } }
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
            if (card.due.isNotBlank()) Text(w.due(card.due), fontSize = 12.sp, color = AMBER, fontWeight = FontWeight.Bold)
            if (card.note.isNotBlank()) Text("«" + card.note + "»", fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
            // Le risposte delle altre persone (gruppi): chi e cosa.
            if (card.kind != Scheda.Kind.Affida && others.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                others.forEach { (who, a) ->
                    Text(model.nameOf(who).substringBefore(' ') + " · " + (a.state?.let { stringResource(SchedaMail.label(it)) }
                        ?: a.choices?.sorted()?.joinToString(", ") { (it + 1).toString() }.orEmpty()), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                }
            }
            if (!mine) {
                val done = mineAnswer?.state == Scheda.State.Done || mineAnswer?.state == Scheda.State.Declined || mineAnswer?.state == Scheda.State.Paid
                if (done) CardStateChip(mineAnswer?.state)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val list = Scheda.actions(card.kind)
                    list.forEachIndexed { i, st ->
                        if (mineAnswer?.state == st) return@forEachIndexed
                        Chip(stringResource(SchedaMail.label(st)), i == list.lastIndex) {
                            model.answerCard(roomId, e, card.id, Scheda.Answer(st)) { actions.toast(failed) }
                        }
                    }
                }
            }
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

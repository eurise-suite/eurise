@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package eu.eurise.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

private val GREEN = Color(0xFF4F6B5A)
private val AMBER = Color(0xFF85672B)
private val RED = Color(0xFFA4502E)

@Composable
private fun Input(value: String, hint: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(hint, fontSize = 13.sp, color = scheme.onSurfaceVariant)
        BasicTextField(value = value, onValueChange = onChange, textStyle = TextStyle(fontSize = 13.5.sp, color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
    }
}

/** «Chiedi un'approvazione» (tavola LavApprova): cosa (preventivo, spesa, ferie), l'importo, il file citato, una riga. */
@Composable
internal fun AskApprovalSheet(model: ChatModel, actions: ChatActions, roomId: String, file: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var title by remember { mutableStateOf(file.substringBeforeLast('.').replace('_', ' ')) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).border(2.dp, scheme.primary, RoundedCornerShape(18.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.check(2.4f), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ap_title), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(R.string.ap_kind_quote, R.string.ap_kind_expense, R.string.ap_kind_leave).forEach { k ->
                    val label = stringResource(k)
                    Chip(label, title.startsWith(label)) { if (!title.startsWith(label)) title = "$label " + title }
                }
            }
            Input(title, stringResource(R.string.ap_what)) { title = it.take(120) }
            Input(amount, stringResource(R.string.ap_amount)) { amount = it.take(30) }
            Input(note, stringResource(R.string.ap_note)) { note = it.take(300) }
            if (file.isNotBlank()) Text("📎 $file", fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Pill(stringResource(R.string.ap_send), EuriseIcons.send(2f), Modifier.fillMaxWidth(), strong = true) {
            if (title.isBlank()) return@Pill
            model.askApproval(roomId, title, amount, file, note) { actions.toast(failed) }
            onClose()
        }
        Text(stringResource(R.string.ap_sign_later), fontSize = 11.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/**
 * La richiesta nella chat. Chi la riceve: Rifiuta, Chiedi modifica (con una riga), Approva. Chi l'ha mandata: quando, chi l'ha
 * aperta, il verdetto.
 */
@Composable
internal fun ApprovalBubble(model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean, onLong: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val card = remember(e.data) { Approva.card(e.data?.let { runCatching { JSONObject(it) }.getOrNull() }) } ?: return
    val failed = stringResource(R.string.ch_send_failed)
    var changing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val answers = model.timeline(roomId).mapNotNull { ev ->
        if (ev.sender == e.sender) return@mapNotNull null
        Approva.answer(ev.data?.let { runCatching { JSONObject(it) }.getOrNull() })?.takeIf { it.first == card.id }?.let { ev to it }
    }
    val last = answers.lastOrNull()
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surface).border(1.5.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .combinedClickable(onClick = {}, onLongClick = onLong).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.check(2.4f), null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ap_request), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                last?.let { VerdictChip(it.second.second) }
            }
            Text(card.title + if (card.amount.isNotBlank()) " · " + card.amount else "", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (card.file.isNotBlank()) Text("📎 " + card.file, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (card.note.isNotBlank()) Text(card.note, fontSize = 13.sp, lineHeight = 18.sp)
            if (mine) {
                val opened = model.readers[roomId]?.filterKeys { it != e.sender }?.entries?.filter { it.value >= e.time }?.minByOrNull { it.value }
                Text(stringResource(R.string.ap_sent_at, time.format(Date(e.time))) +
                    (opened?.let { " · " + stringResource(R.string.ap_opened, model.nameOf(it.key).substringBefore(' '), time.format(Date(it.value))) } ?: ""),
                    fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
            }
            last?.let { (ev, a) ->
                if (a.third.isNotBlank()) Text(model.nameOf(ev.sender).substringBefore(' ') + ": «" + a.third + "»", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AMBER)
            }
            if (!mine && last?.second?.second != Approva.Verdict.Approved && last?.second?.second != Approva.Verdict.Rejected) {
                if (changing) {
                    Input(note, stringResource(R.string.ap_change_hint)) { note = it.take(300) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(stringResource(R.string.ap_cancel), false) { changing = false }
                        Chip(stringResource(R.string.ap_change_send), true) {
                            if (note.isBlank()) return@Chip
                            model.answerApproval(roomId, e, card.id, Approva.Verdict.Change, note) { actions.toast(failed) }
                            changing = false; note = ""
                        }
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Answer(stringResource(R.string.ap_reject), RED, Modifier.weight(1f)) {
                        model.answerApproval(roomId, e, card.id, Approva.Verdict.Rejected, "") { actions.toast(failed) }
                    }
                    Answer(stringResource(R.string.ap_change), AMBER, Modifier.weight(1f)) { changing = true }
                    Answer(stringResource(R.string.ap_approve), GREEN, Modifier.weight(1f), strong = true) {
                        model.answerApproval(roomId, e, card.id, Approva.Verdict.Approved, "") { actions.toast(failed) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Answer(label: String, color: Color, modifier: Modifier, strong: Boolean = false, onClick: () -> Unit) {
    Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (strong) Color.White else color, textAlign = TextAlign.Center, maxLines = 1,
        modifier = modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(if (strong) color else color.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick).padding(top = 10.dp))
}

@Composable
private fun VerdictChip(v: Approva.Verdict?) {
    val (label, color) = when (v) {
        Approva.Verdict.Approved -> stringResource(R.string.ap_st_ok) to GREEN
        Approva.Verdict.Change -> stringResource(R.string.ap_st_change) to AMBER
        Approva.Verdict.Rejected -> stringResource(R.string.ap_st_no) to RED
        null -> stringResource(R.string.ap_st_wait) to Color(0xFF56564F)
    }
    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp))
}

/** «Le tue richieste» (dal foglio dell'account): lo stato di ognuna, «In attesa da 2 giorni» con Sollecita. */
@Composable
internal fun ApprovalsSheet(model: ChatModel, actions: ChatActions, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val failed = stringResource(R.string.ch_send_failed)
    val reminded = stringResource(R.string.ap_reminded)
    val date = DateFormat.getDateInstance(DateFormat.SHORT)
    Sheet(onClose) {
        Text(stringResource(R.string.ap_list), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        if (model.approvals.isEmpty()) Text(stringResource(R.string.ap_list_empty), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        else Card {
            model.approvals.sortedByDescending { it.sentAt }.take(30).forEachIndexed { i, r ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onClose(); model.open(r.room) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title + if (r.amount.isNotBlank()) " · " + r.amount else "", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = when (r.verdict) {
                            Approva.Verdict.Approved -> stringResource(R.string.ap_by_on, model.nameOf(r.by).substringBefore(' '), date.format(Date(r.at)))
                            Approva.Verdict.Change, Approva.Verdict.Rejected -> model.nameOf(r.by).substringBefore(' ') + if (r.note.isNotBlank()) ": «" + r.note + "»" else ""
                            null -> ((now - r.sentAt) / Approva.QUIET_MS).toInt().let { d ->
                                when (d) { 0 -> stringResource(R.string.ap_st_wait); 1 -> stringResource(R.string.ap_waiting_one); else -> stringResource(R.string.ap_waiting_days, d) }
                            }
                        }
                        Text(sub, fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (Approva.waiting(r, now)) Chip(stringResource(R.string.ap_remind), false) { model.remindApproval(r.id) { actions.toast(failed) }; actions.toast(reminded) }
                    else VerdictChip(r.verdict)
                }
            }
        }
    }
}

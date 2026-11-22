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
import androidx.compose.material3.Switch
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
import org.json.JSONObject
import java.time.LocalDateTime

private val GREEN = Color(0xFF4F6B5A)
private val BLUE = Color(0xFF3E5C8A)
private val AMBER = Color(0xFF85672B)

/**
 * Passa la palla da un messaggio (tavola InsPallaChat): a chi (le persone delle tue chat a due), la riga sul da farsi,
 * entro quando, e se chi ti ha scritto vede che se ne occupa l'altro.
 */
@Composable
internal fun PassBallSheet(model: ChatModel, actions: ChatActions, roomId: String, e: Event, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val people = remember { model.colleagues().filter { it.first != roomId || model.room(roomId)?.direct != true } }
    var to by remember { mutableStateOf(people.firstOrNull()) }
    var task by remember { mutableStateOf("") }
    val now = remember { LocalDateTime.now() }
    val evening = now.toLocalDate().atTime(18, 0).takeIf { now.hour < 17 }
    val tomorrow = Programma.tomorrowAt(now, 9)
    var due by remember { mutableStateOf<LocalDateTime?>(evening ?: tomorrow) }
    var notify by remember { mutableStateOf(e.sender != model.session?.userId) }
    val failed = stringResource(R.string.ch_send_failed)
    val sent = to?.let { stringResource(R.string.pa_sent, model.nameOf(it.second)) }
    Sheet(onClose) {
        Text("«" + shownText(model, e).take(90) + "…»", fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(horizontal = 12.dp, vertical = 10.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).border(2.dp, scheme.primary, RoundedCornerShape(18.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.ball(2.2f), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pa_title), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            }
            if (people.isEmpty()) Text(stringResource(R.string.pa_nobody), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                people.forEach { p -> Chip(model.nameOf(p.second).substringBefore(' '), to == p) { to = p } }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (task.isEmpty()) Text(stringResource(R.string.pa_task_hint), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                BasicTextField(value = task, onValueChange = { task = it.take(200) }, textStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pa_due), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    evening?.let { Chip(stringResource(R.string.pa_evening), due == it) { due = if (due == it) null else it } }
                    Chip(stringResource(R.string.pa_tomorrow), due == tomorrow) { due = if (due == tomorrow) null else tomorrow }
                }
            }
            if (e.sender != model.session?.userId) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pa_notify, model.nameOf(e.sender).substringBefore(' '), to?.let { model.nameOf(it.second).substringBefore(' ') } ?: "…"),
                    fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Switch(checked = notify, onCheckedChange = { notify = it })
            }
        }
        val target = to
        Pill(if (target == null) stringResource(R.string.pa_title) else stringResource(R.string.pa_go, model.nameOf(target.second).substringBefore(' ')),
            EuriseIcons.send(2f), Modifier.fillMaxWidth(), strong = true) {
            if (target == null) return@Pill
            model.passBall(roomId, e, target.first, target.second, task, due, notify) { actions.toast(failed) }
            sent?.let(actions::toast)
            onClose()
        }
    }
}

/**
 * Una palla nella chat a due: chi la passa, il messaggio citato, la riga, la scadenza. Per chi la riceve «In corso» e
 * «Fatto»; per chi l'ha passata lo stato.
 */
@Composable
internal fun BallBubble(model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean, onLong: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val card = remember(e.data) { Palla.card(e.data?.let { runCatching { JSONObject(it) }.getOrNull() }) } ?: return
    val failed = stringResource(R.string.ch_send_failed)
    // L'ultima risposta di chi l'ha ricevuta (da qualunque dei due telefoni).
    val answer = model.timeline(roomId).lastOrNull { it.kind == Event.Kind.Notice && it.data?.contains(Palla.STATUS) == true &&
        Palla.status(it.data.let { d -> runCatching { JSONObject(d) }.getOrNull() })?.first == card.id }
        ?.let { Palla.status(JSONObject(it.data!!))?.second }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surface).border(1.5.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .combinedClickable(onClick = {}, onLongClick = onLong).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.ball(2.2f), null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (mine) stringResource(R.string.pa_you_passed) else stringResource(R.string.pa_passed_by, card.from), fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                answer?.let { StateChip(it) }
            }
            if (card.quote.isNotBlank()) Text("«" + card.quote + "»", fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (card.task.isNotBlank()) Text("→ " + card.task, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            if (card.due.isNotBlank()) Text(stringResource(R.string.pa_by, card.due), fontSize = 12.sp, color = AMBER, fontWeight = FontWeight.Bold)
            if (!mine && answer != Palla.State.Done && answer != Palla.State.Taken) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (answer != Palla.State.Working) Chip(stringResource(R.string.pa_working), false) { model.answerBall(roomId, e, card.id, Palla.State.Working) { actions.toast(failed) } }
                Chip(stringResource(R.string.pa_done), true) { model.answerBall(roomId, e, card.id, Palla.State.Done) { actions.toast(failed) } }
            }
        }
    }
}

@Composable
private fun StateChip(s: Palla.State) {
    val (label, color) = when (s) {
        Palla.State.Done -> stringResource(R.string.pa_st_done) to GREEN
        Palla.State.Working -> stringResource(R.string.pa_st_working) to AMBER
        Palla.State.Seen -> stringResource(R.string.pa_st_seen) to BLUE
        Palla.State.Taken -> stringResource(R.string.pa_st_taken) to Color(0xFF56564F)
        Palla.State.Passed -> stringResource(R.string.pa_st_passed) to Color(0xFF56564F)
    }
    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp))
}

/** «Le palle che hai passato» (dal foglio dell'account): a chi, lo stato, «Nessuna risposta da ieri» con Riprendi. */
@Composable
internal fun BallsSheet(model: ChatModel, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    Sheet(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.pa_list), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("${model.balls.size}", fontSize = 13.sp, color = scheme.onSurfaceVariant)
        }
        if (model.balls.isEmpty()) Text(stringResource(R.string.pa_list_empty), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        else Card {
            model.balls.sortedByDescending { it.sentAt }.take(30).forEachIndexed { i, b ->
                if (i > 0) Divider()
                val read = model.readers[b.toRoom]?.get(b.toUser) ?: 0L
                val st = Palla.shown(b, read)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onClose(); model.open(b.toRoom) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.chat(2.2f), null, tint = GREEN, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.task.ifBlank { b.quote }, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("→ " + b.toName, fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                        if (Palla.stale(b, read, now)) Text(stringResource(R.string.pa_stale), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    }
                    if (Palla.stale(b, read, now)) Chip(stringResource(R.string.pa_retake), false) { model.retakeBall(b.id) } else StateChip(st)
                }
            }
        }
    }
}

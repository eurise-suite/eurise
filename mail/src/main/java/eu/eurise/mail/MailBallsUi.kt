package eu.eurise.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.text.DateFormat
import java.util.Date

private val GREEN = Color(0xFF4F6B5A)
private val AMBER = Color(0xFF85672B)

@Composable
private fun BallDialog(onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content() }
        }
    }
}

@Composable
private fun BallChip(label: String, on: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (on) scheme.onPrimary else scheme.onSurface, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(if (on) scheme.primary else scheme.surface).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp))
}

/**
 * Passa la palla da un'email (tavola InsPalla): a chi (le persone con cui scrivi di più, o un indirizzo), la riga sul da
 * farsi, «Mettimi in copia quando risponde»; l'email parte inoltrata con la riga in cima.
 */
@Composable
internal fun MailBallSheet(model: MailModel, actions: MailActions, o: Opened, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val mine = remember { model.accounts.map { it.email.lowercase() }.toSet() }
    val people = remember { MailBalls.people(model.messages, mine + o.summary.fromAddress.lowercase()) }
    var to by remember { mutableStateOf(people.firstOrNull()?.first.orEmpty()) }
    var task by remember { mutableStateOf("") }
    var copyMe by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.ml_send_failed)
    BallDialog(onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).border(2.dp, scheme.primary, RoundedCornerShape(18.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.ball(2.2f), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ml_ball), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                people.forEach { (addr, name) -> BallChip(name.substringBefore(' '), to.equals(addr, true)) { to = addr } }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (to.isEmpty()) Text(stringResource(R.string.ml_ball_to), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                BasicTextField(value = to, onValueChange = { to = it.trim() }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    textStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (task.isEmpty()) Text(stringResource(R.string.ml_ball_task), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                BasicTextField(value = task, onValueChange = { task = it.take(200) }, textStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ml_ball_copy), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Switch(checked = copyMe, onCheckedChange = { copyMe = it })
            }
        }
        val valid = to.contains('@') && to.contains('.') && !sending
        val name = people.firstOrNull { it.first.equals(to, true) }?.second?.substringBefore(' ') ?: to.substringBefore('@')
        Text(stringResource(R.string.ml_ball_go, name), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (valid) scheme.onPrimary else scheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (valid) scheme.primary else scheme.surface)
                .clickable(enabled = valid, role = Role.Button) {
                    sending = true
                    val me = model.account(o.summary.accountId)?.email
                    val header = context.getString(R.string.ml_forward_header, o.summary.fromName, o.summary.fromAddress,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(o.summary.date)), o.summary.subject)
                    model.passBall(o, to, task, if (copyMe && me != null) context.getString(R.string.ml_ball_copy_line, me) else null, header) { ok ->
                        sending = false
                        if (ok) { actions.toast(context.getString(R.string.ml_ball_sent, name)); onClose() } else actions.toast(failed)
                    }
                }.padding(top = 13.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** «Le palle che hai passato» per email: a chi, fatta (ha risposto) o ferma da un giorno con Riprendi. */
@Composable
internal fun MailBallsList(model: MailModel, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    BallDialog(onClose) {
        Text(stringResource(R.string.ml_balls_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        model.balls.sortedByDescending { it.sentAt }.take(30).forEach { b ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.mail(2.2f), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ml_balls_mail, b.subject), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("→ " + b.to + (if (b.task.isNotBlank()) " · " + b.task else ""), fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (MailBalls.stale(b, now)) Text(stringResource(R.string.ml_balls_stale), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                }
                when {
                    b.done -> Text(stringResource(R.string.ml_balls_done), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GREEN)
                    b.taken -> Text(stringResource(R.string.ml_balls_taken), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                    MailBalls.stale(b, now) -> BallChip(stringResource(R.string.ml_balls_retake), false) { model.retakeBall(b.id) }
                    else -> Text(stringResource(R.string.ml_balls_passed), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AMBER)
                }
            }
        }
    }
}

/** La richiesta di approvazione ricevuta: «Chiedi modifica» (con una riga) e «Approva» rispondono all'email. */
@Composable
internal fun ApprovalAnswer(model: MailModel, actions: MailActions, o: Opened) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var changing by remember(o.summary.uid) { mutableStateOf(false) }
    var note by remember(o.summary.uid) { mutableStateOf("") }
    var answered by remember(o.summary.uid) { mutableStateOf(o.summary.answered) }
    val failed = stringResource(R.string.ml_send_failed)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.10f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ml_ap_received, MailApprovals.title(o.summary.subject)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (answered) Text(stringResource(R.string.ml_ap_answered), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        else if (changing) {
            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (note.isEmpty()) Text(stringResource(R.string.ml_ap_change_hint), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                BasicTextField(value = note, onValueChange = { note = it.take(500) }, textStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BallChip(stringResource(R.string.ml_cancel), false) { changing = false }
                BallChip(stringResource(R.string.ml_send), true) {
                    if (note.isBlank()) return@BallChip
                    model.answerApproval(o, approve = false, note = note) { ok -> if (ok) { answered = true; changing = false } else actions.toast(failed) }
                }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BallChip(stringResource(R.string.ml_ap_change), false) { changing = true }
            BallChip(stringResource(R.string.ml_ap_approve), true) {
                model.answerApproval(o, approve = true, note = "") { ok ->
                    if (ok) { answered = true; actions.toast(context.getString(R.string.ml_ap_approved_toast)) } else actions.toast(failed)
                }
            }
        }
    }
}

/** «Le tue richieste» per email: il verdetto letto dalla risposta (da confermare), «In attesa» con Sollecita. */
@Composable
internal fun MailApprovalsList(model: MailModel, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    BallDialog(onClose) {
        Text(stringResource(R.string.ml_ap_list), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        model.approvals.sortedByDescending { it.sentAt }.take(30).forEach { r ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when (r.verdict) {
                        null -> "→ " + r.to
                        MailApprovals.Verdict.Approved -> stringResource(R.string.ml_ap_said_ok, r.to.substringBefore('@'))
                        else -> r.to.substringBefore('@') + if (r.note.isNotBlank()) ": «" + r.note.lines().first().take(80) + "»" else ""
                    }
                    Text(sub, fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val label = when (r.verdict) {
                    MailApprovals.Verdict.Approved -> stringResource(R.string.ml_ap_st_ok)
                    MailApprovals.Verdict.Change -> stringResource(R.string.ml_ap_st_change)
                    MailApprovals.Verdict.Rejected -> stringResource(R.string.ml_ap_st_no)
                    null -> stringResource(R.string.ml_ap_st_wait)
                }
                val color = when (r.verdict) {
                    MailApprovals.Verdict.Approved -> GREEN
                    MailApprovals.Verdict.Rejected -> Color(0xFFA4502E)
                    else -> AMBER
                }
                when {
                    r.verdict != null && !r.confirmed -> Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
                        BallChip(stringResource(R.string.ml_ap_confirm), true) { model.confirmApproval(r.id) }
                    }
                    MailApprovals.waiting(r, now) -> BallChip(stringResource(R.string.ml_ap_remind), false) { model.remindApproval(r.id) {} }
                    else -> Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
    }
}

/** Fuori orario della posta di lavoro: giorni, inizio, fine, parole che passano lo stesso. */
@Composable
internal fun OffHoursCard(model: MailModel) {
    val scheme = MaterialTheme.colorScheme
    val r = model.offRules
    var word by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ml_off_title), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.ml_off_sub), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            java.time.DayOfWeek.entries.forEach { d ->
                BallChip(d.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()), d.value in r.days) {
                    val days = if (d.value in r.days) r.days - d.value else r.days + d.value
                    if (days.isNotEmpty()) model.changeOff(r.copy(days = days))
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("7:30", "8:00", "8:30", "9:00", "9:30").map { java.time.LocalTime.parse(it.padStart(5, '0')) }.forEach { t ->
                BallChip(stringResource(R.string.ml_off_from, t.toString()), r.from == t) { if (r.to.isAfter(t)) model.changeOff(r.copy(from = t)) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("16:00", "17:00", "18:00", "18:30", "19:00", "20:00").map { java.time.LocalTime.parse(it) }.forEach { t ->
                BallChip(stringResource(R.string.ml_off_to, t.toString()), r.to == t) { if (t.isAfter(r.from)) model.changeOff(r.copy(to = t)) }
            }
        }
        Text(stringResource(R.string.ml_off_pass), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (r.words.map { "«$it»" } + r.people).forEach { label ->
                BallChip("$label  ✕", true) {
                    val w = label.removePrefix("«").removeSuffix("»")
                    model.changeOff(r.copy(words = r.words - w, people = r.people - label))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (word.isEmpty()) Text(stringResource(R.string.ml_off_add_hint), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                BasicTextField(value = word, onValueChange = { word = it.take(60) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(6.dp))
            BallChip("+", false) {
                val w = word.trim()
                if (w.isNotEmpty()) { model.changeOff(if ('@' in w) r.copy(people = r.people + w.lowercase()) else r.copy(words = r.words + w.lowercase())); word = "" }
            }
        }
    }
}

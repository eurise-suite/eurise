package eu.eurise.mail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Le viste della Posta: le tre da lavoro (tavola MailSmart) e le tre di sempre. */
enum class MailView { ForYou, Waiting, Team, People, Alerts, Newsletters }

private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Quando parte un'email in uscita, detto bene: «oggi alle 17:00», «domani alle 8:30», «lun 30 alle 8:30». */
fun whenText(at: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
    val day = when (at.toLocalDate()) {
        now.toLocalDate() -> "oggi"
        now.toLocalDate().plusDays(1) -> "domani"
        else -> at.format(DateTimeFormatter.ofPattern("EEE d"))
    }
    return "$day alle ${at.format(timeFmt)}"
}

/** Il punto del mattino (tavola MailSmart): in cima a «Per te», calcolato sul telefono; si chiude per oggi. */
@Composable
fun DigestCard(model: MailModel, mine: Set<String>) {
    val scheme = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val d = MailWork.digest(model.messages, model.waiting, model.balls, mine, now)
    val lines = buildList {
        if (d.asks > 0) add(pluralStringResource(R.plurals.ml_digest_asks, d.asks, d.asks))
        else if (d.unread > 0) add(pluralStringResource(R.plurals.ml_digest_unread, d.unread, d.unread))
        d.longest?.let { add(stringResource(R.string.ml_digest_wait, it.name.ifBlank { it.to }, it.subject, d.longestDays)) }
        if (d.stale > 0) add(pluralStringResource(R.plurals.ml_digest_stale, d.stale, d.stale))
        if (isEmpty()) add(stringResource(R.string.ml_digest_calm))
    }
    Row(
        Modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(scheme.surface), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.spark(2.2f), null, tint = scheme.primary, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.ml_digest_title, java.time.LocalTime.now().format(timeFmt)), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(lines.joinToString(" "), fontSize = 12.5.sp, lineHeight = 18.sp, color = scheme.onSurface.copy(alpha = 0.85f))
            Text(stringResource(R.string.ml_digest_local), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
        }
        Box(Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = model::closeDigest), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.close(2.2f), stringResource(R.string.ml_digest_close), tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}

/** Sotto la riga di «Per te»: cosa ti chiede e, se si può, le risposte in un tocco (diventano una bozza da rileggere). */
@Composable
fun AskLine(model: MailModel, m: Summary) {
    val ask = MailWork.ask(m.subject) ?: return
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(Modifier.padding(top = 5.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Tag(stringResource(when (ask) {
            MailWork.Ask.Approve -> R.string.ml_ask_approve; MailWork.Ask.Confirm -> R.string.ml_ask_confirm
            MailWork.Ask.Question -> R.string.ml_ask_question; MailWork.Ask.Urgent -> R.string.ml_ask_urgent
        }), if (ask == MailWork.Ask.Urgent) scheme.error else scheme.tertiary)
        val yes = when (ask) { MailWork.Ask.Approve -> R.string.ml_q_approve to R.string.ml_q_approve_text; MailWork.Ask.Confirm -> R.string.ml_q_confirm to R.string.ml_q_confirm_text; else -> null }
        yes?.let { (label, text) ->
            QuickButton(stringResource(label), strong = true) { model.quickReply(m, context.getString(text)) }
            QuickButton(stringResource(R.string.ml_q_talk), strong = false) { model.quickReply(m, context.getString(R.string.ml_q_talk_text)) }
        }
    }
}

@Composable
private fun Tag(text: String, tint: Color) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.13f)).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun QuickButton(label: String, strong: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (strong) scheme.primaryContainer else scheme.surfaceVariant)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (strong) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
    }
}

/** «Aspetti» (tavola MailSmart): le risposte che aspetti, le palle passate ancora in giro e le email in uscita. */
@Composable
fun WaitingList(model: MailModel, actions: MailActions) {
    val scheme = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val open = MailWork.open(model.waiting, model.messages)
    val balls = model.balls.filter { !it.done && !it.taken }
    val reminded = stringResource(R.string.ml_wait_reminded)
    val noHub = stringResource(R.string.ml_no_hub)
    val cancelled = stringResource(R.string.ml_out_cancelled)
    Column {
        if (model.outbox.isNotEmpty()) {
            Text(stringResource(R.string.ml_out_title), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            model.outbox.forEach { o ->
                val at = Instant.ofEpochMilli(o.at).atZone(ZoneId.systemDefault()).toLocalDateTime()
                WaitRow(o.name.ifBlank { o.to }, o.subject, stringResource(R.string.ml_out_at, whenText(at)), null) {
                    QuickButton(stringResource(R.string.ml_out_now), strong = true) { model.sendNow(o.id) }
                    QuickButton(stringResource(R.string.ml_out_cancel), strong = false) { model.cancelOutgoing(o.id); actions.toast(cancelled) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (open.isEmpty() && balls.isEmpty() && model.outbox.isEmpty()) {
            Text(stringResource(R.string.ml_wait_empty), fontSize = 14.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
        }
        open.forEach { w ->
            val days = MailWork.days(w.sentAt, now)
            val late = w.by < LocalDate.now().toEpochDay()
            WaitRow(w.name.ifBlank { w.to }, stringResource(R.string.ml_wait_you, w.subject),
                if (days == 0) stringResource(R.string.ml_wait_today) else stringResource(R.string.ml_wait_since, days), late) {
                if (w.nudged) Tag(stringResource(R.string.ml_wait_nudged), scheme.tertiary)
                else QuickButton(stringResource(R.string.ml_wait_remind), strong = true) { actions.toast(if (model.remindWaiting(w)) reminded else noHub) }
                QuickButton(stringResource(R.string.ml_wait_forget), strong = false) { model.forgetWaiting(w.id) }
            }
        }
        balls.forEach { b ->
            WaitRow(b.to, "🏐 " + b.task, stringResource(R.string.ml_wait_since, MailWork.days(b.sentAt, now)), MailBalls.stale(b, now)) {
                QuickButton(stringResource(R.string.ml_balls_retake), strong = false) { model.retakeBall(b.id) }
            }
        }
    }
}

@Composable
private fun WaitRow(who: String, what: String, since: String, late: Boolean?, actions: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(scheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(MailText.initials(who, who), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(who, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(since, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (late == true) scheme.error else scheme.onSurfaceVariant)
            }
            Text(what, fontSize = 13.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
    HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
}

/** Le scelte di MailScriviLavoro sotto il testo: «Se non risponde» e «Quando parte», con l'ora giusta per lui. */
@Composable
fun WriterWork(model: MailModel, to: String, waitDays: Int, onWait: (Int) -> Unit, at: LocalDateTime?, onAt: (LocalDateTime?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val now = LocalDateTime.now()
    val first = to.split(',').firstOrNull()?.trim().orEmpty()
    val best = if (first.contains('@')) MailWork.bestTime(model.messages, first, now) else null
    val morning = MailWork.nextMorning(now)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ml_w_wait, if (waitDays > 0) waitDays else 3), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ml_w_wait_sub), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
            }
            Switch(checked = waitDays > 0, onCheckedChange = { onWait(if (it) 3 else 0) })
        }
        HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
        Text(stringResource(R.string.ml_w_when), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WhenChip(stringResource(R.string.ml_w_now), at == null) { onAt(null) }
            if (best != null && best != morning) {
                val part = stringResource(when (MailWork.partOfDay(best.toLocalTime())) { 0 -> R.string.ml_w_part0; 1 -> R.string.ml_w_part1; else -> R.string.ml_w_part2 })
                WhenChip(stringResource(R.string.ml_w_best, whenText(best, now), part), at == best) { onAt(best) }
            }
            WhenChip(if (morning.toLocalDate() == now.toLocalDate().plusDays(1)) stringResource(R.string.ml_w_morning) else whenText(morning, now), at == morning) { onAt(morning) }
        }
    }
}

@Composable
private fun WhenChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) scheme.primary else scheme.surfaceVariant)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = if (selected) scheme.onPrimary else scheme.onSurface)
    }
}


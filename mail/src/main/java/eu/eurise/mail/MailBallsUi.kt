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

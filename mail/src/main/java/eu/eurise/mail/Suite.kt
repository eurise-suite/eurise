package eu.eurise.mail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.contacts.Reach
import eu.eurise.core.data.messages.MessageInsights
import eu.eurise.core.data.messages.MessageInsights.Insight
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Email con il resto della suite (richiesta del 12 novembre: «il più interattiva possibile»). Le proposte nascono sul
 * telefono dal testo della mail (MessageInsights, lo stesso di Messaggi): appuntamento → Calendario, cosa da fare →
 * Attività, appunto → Note, numero → chiama, indirizzo → Mappe, mittente → Rubrica; e un avviso se sembra una truffa.
 */
@Composable
internal fun SuiteProposals(o: Opened, actions: MailActions) {
    val text = remember(o) { (o.summary.subject + "\n" + o.text).take(6000) }
    val insights = remember(text) { runCatching { MessageInsights.analyze(text, LocalDateTime.now(), Locale.getDefault().language) }.getOrDefault(emptyList()) }
    val suspicious = remember(text) { runCatching { MessageInsights.isSuspicious(text) }.getOrDefault(false) }
    val title = o.summary.subject.ifBlank { o.summary.fromName }
    val noteText = title + "\n" + stringResource(R.string.ml_from_note, o.summary.fromName, o.summary.fromAddress) + "\n\n" + o.text.take(3000)
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }

    if (suspicious) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x338C2F39)).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.shield(2f), null, tint = Color(0xFF8C2F39), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ml_suspicious), fontSize = 12.5.sp, lineHeight = 18.sp)
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        insights.filterIsInstance<Insight.Event>().take(2).forEach { e ->
            val label = e.date.format(dayFormat) + (e.time?.let { " " + it.format(timeFormat) } ?: "")
            Proposal(EuriseIcons.calendar(2f), stringResource(R.string.ml_to_calendar, label), Tint.Sage) {
                // L'ora è di un altro modulo: niente smart cast, si prende con let.
                val intent = e.time?.let { t -> Reach.event(title, e.date.atTime(t), description = noteText.take(1500)) }
                    ?: Reach.allDayEvent(title, e.date)
                if (!actions.start(intent)) actions.toast(actions.text(R.string.ml_no_calendar))
            }
        }
        val task = insights.filterIsInstance<Insight.Task>().firstOrNull()?.text
        Proposal(EuriseIcons.task(2f), stringResource(R.string.ml_to_task), Tint.Blue) { actions.toHub(task = true, text = task ?: title) }
        Proposal(EuriseIcons.note(2f), stringResource(R.string.ml_to_note), Tint.Blue) { actions.toHub(task = false, text = noteText) }
        insights.filterIsInstance<Insight.Phone>().take(1).forEach { p ->
            Proposal(EuriseIcons.phone(2f), p.number, Tint.Blue) { actions.start(Reach.dial(p.number)) }
        }
        insights.filterIsInstance<Insight.Address>().take(1).forEach { a ->
            Proposal(EuriseIcons.map(2f), a.text.take(28), Tint.Blue) {
                actions.start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.text))))
            }
        }
        Proposal(EuriseIcons.person(2f), stringResource(R.string.ml_to_contacts), Tint.Blue) {
            actions.start(Reach.addContact(email = o.summary.fromAddress, name = o.summary.fromName.takeIf { it != o.summary.fromAddress }))
        }
    }
}

private enum class Tint { Blue, Sage }

@Composable
private fun Proposal(icon: ImageVector, label: String, tint: Tint, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tint) {
        Tint.Sage -> Color(0xFF4F6B5A).copy(alpha = 0.16f) to Color(0xFF4F6B5A)
        Tint.Blue -> scheme.primary.copy(alpha = 0.10f) to scheme.primary
    }
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

/** Un allegato toccato: aprirlo (Office, PDF, Galleria…) o metterlo in File (telefono o cloud). */
@Composable
internal fun AttachmentChoice(name: String, onOpen: () -> Unit, onSave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
        Proposal(EuriseIcons.external(2f), stringResource(R.string.ml_open), Tint.Blue, onOpen)
        Proposal(EuriseIcons.folder(2f), stringResource(R.string.ml_save_in), Tint.Blue, onSave)
    }
}

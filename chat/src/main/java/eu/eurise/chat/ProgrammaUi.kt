@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package eu.eurise.chat

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** L'ambra dei messaggi che aspettano (tavola ChatQuandoLavora). */
private val WAIT = Color(0xFF85672B)

/** «domani alle 9:00», «lunedì alle 8:30», «il 2/12 alle 9:00», «oggi alle 18:00». */
@Composable
internal fun whenText(at: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
    val t = Programma.time(at.toLocalTime())
    return when (Programma.day(at, now)) {
        Programma.Day.Today -> stringResource(R.string.pr_today, t)
        Programma.Day.Tomorrow -> stringResource(R.string.pr_tomorrow, t)
        Programma.Day.Week -> stringResource(R.string.pr_weekday, at.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), t)
        Programma.Day.Date -> stringResource(R.string.pr_date, Programma.shortDate(at.toLocalDate()), t)
    }
}

/** Giorno e ora con i selettori del telefono (dalle 9 di domani di partenza). */
internal fun pickDateTime(context: Context, done: (LocalDateTime) -> Unit) {
    val start = LocalDateTime.now().plusDays(1)
    DatePickerDialog(context, { _, y, m, d ->
        TimePickerDialog(context, { _, h, min ->
            val at = LocalDateTime.of(y, m + 1, d, h, min)
            if (at.isAfter(LocalDateTime.now())) done(at)
        }, 9, 0, true).show()
    }, start.year, start.monthValue - 1, start.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
}

/**
 * Pressione lunga su Invia (tavola ChatProgramma): invia più tardi, quando lavora (account di lavoro), a scomparsa.
 * Il tocco breve resta «invia adesso». [onSent] svuota il campo.
 */
@Composable
internal fun SendLaterSheet(model: ChatModel, actions: ChatActions, roomId: String, text: String, onSent: () -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val now = remember { LocalDateTime.now() }
    val failed = stringResource(R.string.ch_send_failed)
    fun later(at: LocalDateTime, work: Boolean = false) {
        model.plan(roomId, text, Programma.toMs(at), work)
        onSent(); onClose()
    }
    Sheet(onClose) {
        Text(stringResource(R.string.pr_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Text(stringResource(R.string.pr_sub), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Card {
            OptionRow(EuriseIcons.clock(2.2f), stringResource(R.string.pr_later), stringResource(R.string.pr_later_sub), extra = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val tomorrow = Programma.tomorrowAt(now, 8, 30)
                    val monday = Programma.nextMonday(now)
                    Chip(stringResource(R.string.pr_tomorrow_short, Programma.time(tomorrow.toLocalTime())), false) { later(tomorrow) }
                    Chip(stringResource(R.string.pr_monday_short), false) { later(monday) }
                    Chip(stringResource(R.string.pr_pick), false) { pickDateTime(context) { later(it) } }
                }
            })
            if (model.account == Account.Work) {
                Divider()
                val start = Programma.workStart(now)
                OptionRow(
                    EuriseIcons.calendar(2.2f), stringResource(R.string.pr_work),
                    if (start == null) stringResource(R.string.pr_work_now) else stringResource(R.string.pr_work_sub, whenText(start, now)),
                    enabled = start != null,
                    onClick = { start?.let { later(it, work = true) } },
                )
            }
            // Presa visione (tavola ChatPresaVisione): nei gruppi.
            if (model.room(roomId)?.direct != true) {
                Divider()
                OptionRow(EuriseIcons.check(2.2f), stringResource(R.string.pv_ask), stringResource(R.string.pv_ask_sub), onClick = {
                    model.sendAck(roomId, text) { actions.toast(failed) }
                    onSent(); onClose()
                })
            }
            Divider()
            OptionRow(EuriseIcons.eye(2.2f), stringResource(R.string.pr_fade), stringResource(R.string.pr_fade_sub), onClick = {
                model.sendFading(roomId, text, { actions.toast(failed) })
                onSent(); onClose()
            })
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector, title: String, sub: String, enabled: Boolean = true, onClick: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).let { m -> if (onClick != null && enabled) m.clickable(role = Role.Button, onClick = onClick) else m }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (enabled) scheme.onSurface else scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant)
                Text(sub, fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
        }
        extra?.invoke()
    }
}

/**
 * Un messaggio programmato, in fondo alla conversazione: il fumetto tuo con l'orologio ambra «arriva domani alle 9:00».
 * Pressione lunga: invia adesso o annulla.
 */
@Composable
internal fun PlannedBubble(model: ChatModel, p: Programma.Planned, onLong: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val at = Programma.toLocal(p.at)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.End) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)).background(scheme.primary.copy(alpha = 0.16f))
                .combinedClickable(onClick = onLong, onLongClick = onLong).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(p.text, fontSize = 14.5.sp, lineHeight = 20.sp, color = scheme.onSurface)
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.clock(2.2f), null, tint = WAIT, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.pr_arrives, whenText(at)) + if (p.fadeMs > 0) " · " + stringResource(R.string.pr_fade_short) else "",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WAIT)
            }
        }
    }
}

/** Il foglio di un programmato: invia adesso, annulla (il testo torna nel campo). */
@Composable
internal fun PlannedMenu(model: ChatModel, actions: ChatActions, p: Programma.Planned, onEdit: (String) -> Unit, onClose: () -> Unit) {
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Text(p.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Card {
            OptionRow(EuriseIcons.send(2f), stringResource(R.string.pr_send_now), stringResource(R.string.pr_arrives, whenText(Programma.toLocal(p.at))), onClick = {
                model.sendPlannedNow(p.id) { actions.toast(failed) }; onClose()
            })
            Divider()
            OptionRow(EuriseIcons.pencil(2f), stringResource(R.string.pr_edit), stringResource(R.string.pr_edit_sub), onClick = {
                model.cancelPlanned(p.id); onEdit(p.text); onClose()
            })
            Divider()
            OptionRow(EuriseIcons.trash(2f), stringResource(R.string.pr_cancel), stringResource(R.string.pr_cancel_sub), onClick = {
                model.cancelPlanned(p.id); onClose()
            })
        }
    }
}

/**
 * Sotto un messaggio a scomparsa: tuo, «sparisce 1 giorno dopo la lettura» (poi l'ora esatta, quando l'altro l'ha
 * letto); dell'altro, «a scomparsa».
 */
@Composable
internal fun FadeLabel(model: ChatModel, e: Event, mine: Boolean) {
    val fade = model.fading[e.id]
    val after = fade?.afterMs ?: e.data?.let { runCatching { Programma.fadeOf(JSONObject(it)) }.getOrNull() } ?: 0L
    if (after <= 0) return
    val text = when {
        fade != null && fade.readAt > 0 -> stringResource(R.string.pr_fade_at, whenText(Programma.toLocal(fade.goneAt)))
        mine -> stringResource(R.string.pr_fade_sub)
        else -> stringResource(R.string.pr_fade_short)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EuriseIcons.eye(2.2f), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ricordamelo nel menu di un messaggio ricevuto: tra un'ora, stasera, domani alle 9, scegli. Va in Attività. */
@Composable
internal fun RemindRow(model: ChatModel, actions: ChatActions, roomId: String, e: Event, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val now = remember { LocalDateTime.now() }
    val noHub = stringResource(R.string.pr_no_hub)
    var done by remember { mutableStateOf<String?>(null) }
    val sentText = done?.let { stringResource(R.string.pr_reminded, it) }
    fun go(at: LocalDateTime, label: String) {
        if (model.remind(roomId, e, at)) { done = label; actions.toast(context.getString(R.string.pr_reminded, label)); onClose() }
        else actions.toast(noHub)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.bell(2f), null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(sentText ?: stringResource(R.string.pr_remind), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Programma.remindChoices(now).forEach { (kind, at) ->
                val label = when (kind) {
                    Programma.Remind.Hour -> stringResource(R.string.pr_in_hour)
                    Programma.Remind.Evening -> stringResource(R.string.pr_evening)
                    Programma.Remind.Tomorrow -> stringResource(R.string.pr_tomorrow9)
                }
                val spoken = whenText(at, now)
                Chip(label, false) { go(at, spoken) }
            }
            val pickLabel = stringResource(R.string.pr_pick)
            val fmt = LocalContext.current
            Chip(pickLabel, false) {
                pickDateTime(fmt) { at ->
                    val t = Programma.time(at.toLocalTime())
                    go(at, fmt.getString(R.string.pr_date, Programma.shortDate(at.toLocalDate()), t))
                }
            }
        }
        Text(stringResource(R.string.pr_remind_sub), fontSize = 12.sp, color = scheme.onSurfaceVariant)
    }
}

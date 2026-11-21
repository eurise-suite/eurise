package eu.eurise.chat

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GREEN = Color(0xFF4F6B5A)
private fun day(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN))
private fun weekday(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("EEEE d", Locale.ITALIAN))

/** Un giorno con il selettore del telefono. */
private fun pickDate(context: Context, start: LocalDate, done: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> done(LocalDate.of(y, m + 1, d)) }, start.year, start.monthValue - 1, start.dayOfMonth).show()
}

/**
 * In cima a un gruppo a tempo (tavola ChatGruppoTempo): «Gruppo a tempo: dal 18 al 30 novembre» e, finita la cosa,
 * il riepilogo «148 foto · 9 spese · 4 documenti» con Spese e «Il gruppo va in archivio domenica 30 · Tieni aperto».
 */
@Composable
internal fun TimeboxBar(model: ChatModel, actions: ChatActions, roomId: String, box: Tempo.Box, events: List<Event>, onExpenses: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val kept = roomId in model.keptOpen
    if (!Tempo.showSummary(box, today) && !Tempo.archived(box, today)) {
        Text(stringResource(R.string.tb_line, day(box.from), day(box.to)), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp))
        return
    }
    val r = Tempo.recap(events)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.parcel(2.2f), null, tint = GREEN, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.tb_done), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = GREEN)
        }
        Text(stringResource(R.string.tb_recap, r.photos, r.expenses, r.files), fontSize = 14.sp, lineHeight = 21.sp)
        if (r.expenses > 0) Chip(stringResource(R.string.tb_expenses), false, onExpenses)
        if (!kept) {
            Text(stringResource(if (Tempo.archived(box, today)) R.string.tb_archived else R.string.tb_will_archive, weekday(box.to)),
                fontSize = 12.sp, color = scheme.onSurfaceVariant)
            Row(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(scheme.background).clickable(role = Role.Button) { model.keepOpen(roomId) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(stringResource(R.string.tb_keep), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        } else Text(stringResource(R.string.tb_kept), fontSize = 12.sp, color = scheme.onSurfaceVariant)
    }
}

/** Il periodo del gruppo: dal, al, e (se si vuole) quando finisce il viaggio o la festa, per il riepilogo. */
@Composable
internal fun TimeboxSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val old = model.timebox(roomId)
    val today = LocalDate.now()
    var from by remember { mutableStateOf(old?.from ?: today) }
    var to by remember { mutableStateOf(old?.to ?: today.plusDays(14)) }
    var end by remember { mutableStateOf(old?.end) }
    val failed = stringResource(R.string.tb_failed)
    Sheet(onClose) {
        Text(stringResource(R.string.tb_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Text(stringResource(R.string.tb_explain), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Card {
            DateRow(stringResource(R.string.tb_from), day(from)) { pickDate(context, from) { from = it; if (to.isBefore(it)) to = it } }
            Divider()
            DateRow(stringResource(R.string.tb_to), day(to)) { pickDate(context, to) { if (!it.isBefore(from)) to = it } }
            Divider()
            DateRow(stringResource(R.string.tb_end), end?.let(::day) ?: stringResource(R.string.tb_end_none)) {
                pickDate(context, end ?: to) { if (!it.isBefore(from) && !it.isAfter(to)) end = it }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (old != null) Pill(stringResource(R.string.tb_remove), EuriseIcons.close(2f), Modifier.weight(1f), strong = false) {
                model.setTimebox(roomId, null) { actions.toast(failed) }; onClose()
            }
            Pill(stringResource(R.string.cr_save), EuriseIcons.check(2f), Modifier.weight(1f), strong = true) {
                model.setTimebox(roomId, Tempo.Box(from, to, end)) { actions.toast(failed) }; onClose()
            }
        }
    }
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

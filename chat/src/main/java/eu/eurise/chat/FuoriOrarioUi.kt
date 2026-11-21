@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package eu.eurise.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.format.TextStyle as DayStyle
import java.util.Locale

/**
 * Fuori orario (tavola SpaFuoriOrario): il tuo orario di lavoro; fuori da lì Chat di lavoro in silenzio fino al mattino,
 * tranne chi passa lo stesso (persone, parole, stanze). Al mattino il riepilogo. L'orario è anche quello che gli altri
 * vedono quando ti scrivono, se lo condividi.
 */
@Composable
internal fun OffHoursSheet(model: ChatModel, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val r = model.offRules
    val h = r.hours
    var word by remember { mutableStateOf("") }
    Sheet(onClose) {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.fo_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(stringResource(R.string.fo_quiet), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = model.workQuiet, onCheckedChange = model::changeWorkQuiet)
            }
            Text(stringResource(R.string.fo_hours), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { d ->
                    Chip(d.getDisplayName(DayStyle.SHORT, Locale.getDefault()), d.value in h.days) {
                        val days = if (d.value in h.days) h.days - d.value else h.days + d.value
                        if (days.isNotEmpty()) model.changeOffRules(r.copy(hours = h.copy(days = days)))
                    }
                }
            }
            Text(stringResource(R.string.fo_from), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FuoriOrario.STARTS.forEach { t -> Chip(Programma.time(t), h.from == t) { if (h.to.isAfter(t)) model.changeOffRules(r.copy(hours = h.copy(from = t))) } }
            }
            Text(stringResource(R.string.fo_to), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FuoriOrario.ENDS.forEach { t -> Chip(Programma.time(t), h.to == t) { if (t.isAfter(h.from)) model.changeOffRules(r.copy(hours = h.copy(to = t))) } }
            }
            Text(stringResource(R.string.fo_pass), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            Text(stringResource(R.string.fo_people), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (model.colleagues().map { it.second } + r.people).distinct().forEach { u ->
                    Chip(model.nameOf(u).substringBefore(' '), u in r.people) { model.changeOffRules(r.copy(people = if (u in r.people) r.people - u else r.people + u)) }
                }
            }
            Text(stringResource(R.string.fo_rooms), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                model.rooms.filter { !it.direct && !it.invite }.take(12).forEach { room ->
                    Chip(room.name, room.id in r.rooms) { model.changeOffRules(r.copy(rooms = if (room.id in r.rooms) r.rooms - room.id else r.rooms + room.id)) }
                }
            }
            Text(stringResource(R.string.fo_words), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.words.sorted().forEach { w -> Chip("«$w»  ✕", true) { model.changeOffRules(r.copy(words = r.words - w)) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (word.isEmpty()) Text(stringResource(R.string.fo_word_hint), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    BasicTextField(value = word, onValueChange = { word = it.take(30) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
                }
                Chip("+", false) { if (word.isNotBlank()) { model.changeOffRules(r.copy(words = r.words + word.trim().lowercase())); word = "" } }
            }
            Text(stringResource(R.string.fo_morning_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

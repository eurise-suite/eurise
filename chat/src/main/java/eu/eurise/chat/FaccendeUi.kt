@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package eu.eurise.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle as DayStyle
import java.util.Date
import java.util.Locale

private val GREEN = Color(0xFF4F6B5A)
private val RED = Color(0xFFA4502E)

@Composable
private fun Initials(model: ChatModel, user: String?, me: String) {
    val scheme = MaterialTheme.colorScheme
    val label = if (user == me) stringResource(R.string.fa_you_short) else user?.let { model.nameOf(it).split(' ').take(2).joinToString("") { p -> p.take(1) }.uppercase() } ?: "?"
    Box(Modifier.size(36.dp).clip(CircleShape).background(if (user == me) scheme.primary else scheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (user == me) scheme.onPrimary else scheme.primary)
    }
}

/** La scheda Faccende del gruppo: chi tocca, «Fatto» in un tocco, in cima quelle non fatte; + Faccenda. */
@Composable
internal fun ChoresPane(model: ChatModel, actions: ChatActions, roomId: String) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    LaunchedEffect(roomId) { model.loadMembers(roomId) }
    val now = LocalDateTime.now()
    val list = Faccende.sorted(model.chores(roomId), now) { Programma.toMs(it) }
    val failed = stringResource(R.string.ch_send_failed)
    var adding by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Faccende.Chore?>(null) }
    val days = DayOfWeek.entries.associateWith { it.getDisplayName(DayStyle.SHORT, Locale.getDefault()).replaceFirstChar { c -> c.uppercase() } }
    val everyDay = stringResource(R.string.fa_every_day)
    val alternate = stringResource(R.string.fa_alternate)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.fa_week), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
            if (list.isEmpty()) item { Text(stringResource(R.string.fa_empty), fontSize = 13.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant) }
            items(list, key = { it.id }) { c ->
                val st = Faccende.status(c, now) { Programma.toMs(it) }
                val who = Faccende.whose(c)
                val whoName = if (who == me) stringResource(R.string.fa_you) else who?.let { model.nameOf(it).substringBefore(' ') }.orEmpty()
                val nextName = Faccende.next(c)?.let { if (it == me) stringResource(R.string.fa_you) else model.nameOf(it).substringBefore(' ') }.orEmpty()
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).clickable(role = Role.Button) { menu = c }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Initials(model, who, me)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Faccende.daysLabel(c, days, everyDay, alternate) { n -> actions.text(R.string.fa_every_n).format(n) } + " · " + c.time + " · " +
                            stringResource(R.string.fa_turn, whoName, nextName), fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (st == Faccende.Status.Late) Text(stringResource(R.string.fa_late), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = RED)
                    }
                    Spacer(Modifier.width(8.dp))
                    when (st) {
                        Faccende.Status.Done -> Text(
                            stringResource(R.string.fa_done_at, if (java.time.Instant.ofEpochMilli(c.doneAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == now.toLocalDate())
                                time.format(Date(c.doneAt)) else stringResource(R.string.fa_yesterday)),
                            fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = GREEN)
                        Faccende.Status.Free -> Text(stringResource(R.string.fa_free), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                        else -> Chip(stringResource(R.string.fa_done), who == me) { model.choreDone(roomId, c.id) { actions.toast(failed) } }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.fa_how), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
                    Text("· " + stringResource(R.string.fa_how_1), fontSize = 12.5.sp)
                    Text("· " + stringResource(R.string.fa_how_2), fontSize = 12.5.sp)
                    Text("· " + stringResource(R.string.fa_how_3), fontSize = 12.5.sp)
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).height(52.dp).clip(RoundedCornerShape(26.dp))
                .background(scheme.primary).clickable(role = Role.Button) { adding = true }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.plus(2.4f), null, tint = scheme.onPrimary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.fa_add), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
        }
    }
    if (adding) AddChoreSheet(model, actions, roomId) { adding = false }
    menu?.let { c ->
        Sheet({ menu = null }) {
            Text(c.title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
            Card {
                if (Faccende.whose(c) == me && Faccende.next(c) != me) {
                    MenuRow(EuriseIcons.chat(2f), stringResource(R.string.fa_swap, Faccende.next(c)?.let { model.nameOf(it).substringBefore(' ') }.orEmpty())) {
                        model.askChoreSwap(roomId, c.id) { actions.toast(failed) }; menu = null
                    }
                    Divider()
                }
                if (Faccende.whose(c) != me) {
                    MenuRow(EuriseIcons.check(2.4f), stringResource(R.string.fa_i_do)) { model.choreDone(roomId, c.id) { actions.toast(failed) }; menu = null }
                    Divider()
                }
                MenuRow(EuriseIcons.close(2f), stringResource(R.string.fa_remove)) { model.removeChore(roomId, c.id) { actions.toast(failed) }; menu = null }
            }
        }
    }
}

/** + Faccenda: cosa, quando (giorni, ogni giorno, a giorni alterni), a che ora, chi è nel giro (in ordine). */
@Composable
private fun AddChoreSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val members = model.members[roomId].orEmpty().ifEmpty { listOf(me) }
    var title by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(setOf<Int>()) }
    var every by remember { mutableStateOf(1) }
    var hour by remember { mutableStateOf(20) }
    val people = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(members) } }
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Text(stringResource(R.string.fa_add_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surface).padding(horizontal = 12.dp, vertical = 11.dp)) {
            if (title.isEmpty()) Text(stringResource(R.string.fa_what), fontSize = 14.sp, color = scheme.onSurfaceVariant)
            BasicTextField(value = title, onValueChange = { title = it.take(60) }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
        }
        Text(stringResource(R.string.fa_when), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(stringResource(R.string.fa_every_day), days.isEmpty() && every == 1) { days = emptySet(); every = 1 }
            Chip(stringResource(R.string.fa_alternate), days.isEmpty() && every == 2) { days = emptySet(); every = 2 }
            DayOfWeek.entries.forEach { d ->
                Chip(d.getDisplayName(DayStyle.SHORT, Locale.getDefault()), d.value in days) { days = if (d.value in days) days - d.value else days + d.value }
            }
        }
        Text(stringResource(R.string.fa_hour), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(8, 12, 18, 20, 21).forEach { h -> Chip("%02d:00".format(h), hour == h) { hour = h } }
        }
        Text(stringResource(R.string.fa_who), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            members.forEach { u ->
                val i = people.indexOf(u)
                Chip((if (i >= 0) "${i + 1}. " else "") + if (u == me) stringResource(R.string.fa_you) else model.nameOf(u).substringBefore(' '), i >= 0) {
                    if (i >= 0) people.remove(u) else people.add(u)
                }
            }
        }
        Pill(stringResource(R.string.fa_save), EuriseIcons.check(2.4f), Modifier.fillMaxWidth(), strong = true) {
            if (title.isBlank() || people.isEmpty()) return@Pill
            model.addChore(roomId, title, days, if (days.isEmpty()) every else 0, "%02d:00".format(hour), people.toList()) { actions.toast(failed) }
            onClose()
        }
    }
}

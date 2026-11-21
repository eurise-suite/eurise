@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/** L'azzurro delle discussioni (tavola ChatCartelle). */
private val THREAD = Color(0xFF3E5C8A)

/** In cima alla discussione: indietro, il titolo (prima riga del messaggio di partenza), «discussione in «Casa al mare» · 6 risposte». */
@Composable
internal fun ThreadHeader(model: ChatModel, roomName: String, root: Event?, count: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.ch_back), flat = true) { model.thread = null }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(root?.let { Threads.title(shownText(model, it)) }?.ifBlank { null } ?: stringResource(R.string.th_title),
                fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.th_sub, roomName, pluralStringResource(R.plurals.th_replies, count, count)),
                fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Il messaggio di partenza, in una scheda: «Luca · messaggio di partenza». */
@Composable
internal fun ThreadRootCard(model: ChatModel, root: Event?) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (root == null) {
            Text(stringResource(R.string.th_loading), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            return@Column
        }
        Text(stringResource(R.string.th_root_by, model.nameOf(root.sender)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        Text(shownText(model, root), fontSize = 14.5.sp, lineHeight = 20.sp)
    }
}

/** Sotto le risposte: «Le risposte restano qui: il gruppo vede solo «6 risposte»». */
@Composable
internal fun ThreadHint(count: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.th_hint, pluralStringResource(R.plurals.th_replies, count, count)), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(scheme.onSurface.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Sotto il messaggio di partenza nella conversazione del gruppo: «6 risposte · 10:11», tocco = apri la discussione. */
@Composable
internal fun ThreadChip(info: Threads.Info, mine: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(THREAD.copy(alpha = 0.12f)).clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.chat(2.2f), null, tint = THREAD, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(pluralStringResource(R.plurals.th_replies, info.count, info.count) + " · " + shortTime(info.last),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = THREAD)
        }
    }
}

/** Nell'elenco delle chat, sotto l'anteprima: «2 discussioni · Turni pulizie (6), Bollette (3)». */
@Composable
internal fun RoomThreadsLine(model: ChatModel, roomId: String) {
    val list = Threads.summary(model.timeline(roomId))
    if (list.isEmpty()) return
    val names = list.take(2).joinToString(", ") { "${it.title.ifBlank { "…" }} (${it.count})" }
    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(EuriseIcons.chat(2.2f), null, tint = THREAD, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(pluralStringResource(R.plurals.th_threads, list.size, list.size) + " · " + names, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
            color = THREAD, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(0.dp))
}

/**
 * L'avviso di truffa sotto un messaggio (tavola ChatTruffa): che copione è, cosa fare, la rubrica per chiamare chi conosci
 * davvero, Blocca (con un secondo tocco). Il controllo si fa sul telefono, il messaggio non esce.
 */
@Composable
internal fun ScamCard(model: ChatModel, actions: ChatActions, roomId: String, sender: String, v: eu.eurise.core.suite.ScamCheck.Verdict) {
    val red = Color(0xFFA4502E)
    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val blocked = stringResource(R.string.sc_blocked)
    val failed = stringResource(R.string.sc_block_failed)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(red.copy(alpha = 0.10f))
            .border(1.5.dp, red.copy(alpha = 0.45f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.shield(2.2f), null, tint = red, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sc_title), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = red)
        }
        Text(stringResource(when (v.kind) {
            eu.eurise.core.suite.ScamCheck.Kind.Family -> R.string.sc_family
            eu.eurise.core.suite.ScamCheck.Kind.Money -> R.string.sc_money
            eu.eurise.core.suite.ScamCheck.Kind.Bank -> R.string.sc_bank
            eu.eurise.core.suite.ScamCheck.Kind.Parcel -> R.string.sc_parcel
            eu.eurise.core.suite.ScamCheck.Kind.Code -> R.string.sc_code
        }), fontSize = 13.5.sp, lineHeight = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (v.kind == eu.eurise.core.suite.ScamCheck.Kind.Family || v.kind == eu.eurise.core.suite.ScamCheck.Kind.Money) {
                Row(
                    Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(red).clickable(role = Role.Button) {
                        actions.launch(android.content.Intent(android.content.Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(EuriseIcons.phone(2.2f), null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sc_contacts), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).clickable(role = Role.Button) {
                    if (!confirm) { confirm = true; return@clickable }
                    model.block(roomId, sender) { ok -> actions.toast(if (ok) blocked else failed) }
                }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(if (confirm) R.string.sc_block_confirm else R.string.sc_block), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = red) }
        }
        Text(stringResource(R.string.sc_local), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Un messaggio che chiede la presa visione (tavola ChatPresaVisione). Tuo: barra «Ricevuto da 4 su 6», chi manca (letto
 * e non confermato, non letto) e «Ricorda a chi manca». Di altri: il testo e il tasto «Ricevuto» (una reazione ✅).
 */
@Composable
internal fun AckBubble(model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean, onLong: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val amber = Color(0xFF85672B)
    val green = Color(0xFF4F6B5A)
    val failed = stringResource(R.string.ch_send_failed)
    val content = androidx.compose.runtime.remember(e.data) { e.data?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } }
    val text = Ack.text(content, e.text)
    val me = model.session?.userId.orEmpty()
    val confirmed = model.relations.reactors(e.id, Ack.KEY)
    androidx.compose.runtime.LaunchedEffect(roomId) { model.loadMembers(roomId) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface)
            .combinedClickable(onClick = {}, onLongClick = onLong).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.eye(2.2f), null, tint = amber, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (mine) stringResource(R.string.pv_asks_me) else stringResource(R.string.pv_asks, model.nameOf(e.sender)),
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = amber)
        }
        Text(text, fontSize = 14.5.sp, lineHeight = 20.sp)
        if (mine) {
            val s = Ack.status(model.members[roomId].orEmpty(), me, confirmed, model.readers[roomId].orEmpty(), e.time)
            if (s.total > 0) {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(scheme.onSurface.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(s.confirmed.size / s.total.toFloat()).height(8.dp).background(green))
                }
                Text(stringResource(R.string.pv_count, s.confirmed.size, s.total), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                val missing = s.readOnly.map { stringResource(R.string.pv_read_only, model.nameOf(it)) } + s.unread.map { stringResource(R.string.pv_unread, model.nameOf(it)) }
                if (missing.isNotEmpty()) {
                    Text(stringResource(R.string.pv_missing, missing.joinToString(", ")), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    Row(
                        Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(scheme.background).clickable(role = Role.Button) {
                            model.remindAck(roomId, e, s.readOnly + s.unread) { actions.toast(failed) }
                        }.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(EuriseIcons.clock(2.2f), null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pv_remind), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                } else Text(stringResource(R.string.pv_all), fontSize = 12.sp, color = green, fontWeight = FontWeight.Bold)
            }
        } else {
            val done = me in confirmed
            Row(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (done) green.copy(alpha = 0.15f) else green)
                    .clickable(enabled = !done, role = Role.Button) { model.confirmAck(roomId, e) { actions.toast(failed) } }.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.check(2.4f), null, tint = if (done) green else Color.White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (done) R.string.pv_done else R.string.pv_confirm), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (done) green else Color.White)
            }
        }
    }
}

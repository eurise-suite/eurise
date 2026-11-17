package eu.eurise.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.chat.People.Channel
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.suite.PeopleContract
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

// «Una persona, tutti i canali»: tavole UniPersone (vista Persone), UniConversazione (il filo) e UniCanale (il canale).

private val SAGE = Color(0xFF4F6B5A)
private val BLUE = Color(0xFF3E5C8A)
private val BRASS = Color(0xFF85672B)
private val RUST = Color(0xFFA4502E)

private fun color(c: Channel) = when (c) { Channel.Chat -> SAGE; Channel.Sms -> BLUE; Channel.Email -> BRASS; Channel.Call -> RUST }
private fun icon(c: Channel): ImageVector = when (c) {
    Channel.Chat -> EuriseIcons.lock(2.2f); Channel.Sms -> EuriseIcons.message(2.2f); Channel.Email -> EuriseIcons.mail(2.2f); Channel.Call -> EuriseIcons.phone(2.2f)
}

@Composable
private fun channelName(c: Channel) = stringResource(when (c) {
    Channel.Chat -> R.string.pe_ch_chat; Channel.Sms -> R.string.pe_ch_sms; Channel.Email -> R.string.pe_ch_email; Channel.Call -> R.string.pe_ch_call
})

/** Le viste dell'elenco: Persone, Solo Chat, Non lette (con quante). */
@Composable
internal fun ViewChips(model: ChatModel) {
    val uni = model.uni
    val unread = uni.rows.sumOf { it.unread } + model.rooms.filter { model.peerOf(it.id) == null }.sumOf { it.unread }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.pe_view_people), uni.view == Uni.View.People) { uni.changeView(Uni.View.People) }
        Chip(stringResource(R.string.pe_view_chat), uni.view == Uni.View.Chat) { uni.changeView(Uni.View.Chat) }
        Chip(if (unread > 0) stringResource(R.string.pe_view_unread_n, unread) else stringResource(R.string.pe_view_unread), uni.view == Uni.View.Unread) {
            uni.changeView(Uni.View.Unread)
        }
    }
}

/** Tiene la vista Persone al passo: all'apertura rilegge tutto, a ogni messaggio della Chat solo la sua parte. */
@Composable
internal fun PeopleRefresher(model: ChatModel) {
    val uni = model.uni
    LaunchedEffect(model.session?.userId) { uni.refresh() }
    val signature = model.rooms.joinToString(",") { "${it.id}:${it.lastTime}:${it.unread}" }
    LaunchedEffect(signature) { delay(300); uni.refresh(full = false) }
}

/** Una cosa dell'elenco misto: una persona (tutti i canali) o una stanza (gruppi e inviti). */
private sealed interface Mix {
    val time: Long
    data class Person(val row: People.Row) : Mix { override val time get() = row.last?.time ?: 0L }
    data class Group(val room: Room) : Mix { override val time get() = room.lastTime }
}

/** Le righe della vista Persone (o Non lette) dentro l'elenco della Chat. */
internal fun LazyListScope.peopleItems(model: ChatModel, query: String, unreadOnly: Boolean) {
    val q = query.trim().lowercase()
    val invites = model.rooms.filter { it.invite }
    val groups = model.rooms.filter { !it.invite && model.peerOf(it.id) == null }.map { Mix.Group(it) }
    val persons = model.uni.rows.map { Mix.Person(it) }
    val mixed = (groups + persons).filter { m ->
        val name = when (m) { is Mix.Person -> m.row.who.name; is Mix.Group -> m.room.name }
        val unread = when (m) { is Mix.Person -> m.row.unread; is Mix.Group -> m.room.unread }
        (q.isEmpty() || name.lowercase().contains(q)) && (!unreadOnly || unread > 0)
    }.sortedByDescending { it.time }
    items(invites, key = { "i:" + it.id }) { r -> RoomRow(model, r) }
    items(mixed, key = { m -> when (m) { is Mix.Person -> "p:" + m.row.who.key; is Mix.Group -> "g:" + m.room.id } }) { m ->
        when (m) {
            is Mix.Person -> PersonRow(model, m.row)
            is Mix.Group -> RoomRow(model, m.room)
        }
    }
    if (!unreadOnly && q.isEmpty() && People.inviteCandidates(model.uni.rows).isNotEmpty()) item(key = "invite") {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(SAGE.copy(alpha = 0.12f))
                .clickable(role = Role.Button) { model.uni.openInviteList(true) }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.send(2.2f), null, tint = SAGE, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.inv_row), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(EuriseIcons.chevronRight(2.2f), null, tint = SAGE, modifier = Modifier.size(15.dp))
        }
    }
    item(key = "note") {
        Text(stringResource(R.string.pe_note), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp))
    }
}

/** Il testo breve dell'ultima cosa, con il canale quando serve («Email: …», «Chiamata persa»). */
@Composable
private fun preview(e: People.Entry?): String = when {
    e == null -> ""
    e.channel == Channel.Email -> stringResource(R.string.pe_prev_email, e.subject.ifBlank { e.text })
    e.channel == Channel.Call && e.missed -> stringResource(R.string.pe_call_missed)
    e.channel == Channel.Call && e.note.isNotBlank() -> stringResource(R.string.pe_prev_call_note, e.note)
    e.channel == Channel.Call -> stringResource(R.string.pe_call_min, ((e.duration + 59) / 60).coerceAtLeast(1))
    else -> e.text
}

@Composable
private fun ChannelIcons(channels: Set<Channel>, size: Int = 13) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Channel.entries.filter { it in channels }.forEach { c -> Icon(icon(c), null, tint = color(c), modifier = Modifier.size(size.dp)) }
    }
}

@Composable
private fun PersonRow(model: ChatModel, r: People.Row) {
    val scheme = MaterialTheme.colorScheme
    val tint = tintFor(r.who.key)
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable { model.uni.open(r) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(initials(r.who.name), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.who.name, fontSize = 15.5.sp, fontWeight = if (r.unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    r.last?.time?.takeIf { it > 0 }?.let { Text(shortTime(it), fontSize = 12.sp, color = if (r.unread > 0) scheme.primary else scheme.onSurfaceVariant) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelIcons(r.channels)
                    Spacer(Modifier.width(6.dp))
                    Text(preview(r.last), fontSize = 13.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (r.unread > 0) {
                        Box(Modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).clip(RoundedCornerShape(10.dp)).background(scheme.primary).padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center) { Text("${r.unread}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimary) }
                    }
                }
            }
        }
        HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
    }
}

// --- Il filo di una persona (tavola UniConversazione) ---

private enum class Filter { All, Chat, Sms, Email, Call }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PersonScreen(model: ChatModel, actions: ChatActions) {
    val uni = model.uni
    val who = uni.who ?: return
    val scheme = MaterialTheme.colorScheme
    BackHandler { uni.close() }
    val me = model.session?.userId.orEmpty()
    val roomId = who.roomId
    val room = roomId?.let(model::room)
    val events = roomId?.let { model.timeline(it) }
    // Le cose della Chat sono vive: arrivano con la sincronizzazione mentre il filo è aperto.
    val chat = events.orEmpty().filter { it.kind != Event.Kind.State }.map { e ->
        People.Entry(e.id, Channel.Chat, e.time, e.sender != me, when (e.kind) {
            Event.Kind.Encrypted -> stringResource(R.string.ch_encrypted_short)
            Event.Kind.Image -> stringResource(R.string.ch_photo)
            else -> e.text
        })
    }
    LaunchedEffect(roomId) { if (roomId != null && events.orEmpty().size < 20) model.older(roomId) }
    LaunchedEffect(roomId, events?.size) { roomId?.let(model::markRead) }
    var filter by remember(who.key) { mutableStateOf(Filter.All) }
    val all = People.merge(chat, uni.thread)
    val shown = all.filter {
        when (filter) {
            Filter.All -> true; Filter.Chat -> it.channel == Channel.Chat; Filter.Sms -> it.channel == Channel.Sms
            Filter.Email -> it.channel == Channel.Email; Filter.Call -> it.channel == Channel.Call
        }
    }
    val writable = People.writable(who)
    val lastIn = People.lastIncoming(all)
    var draft by remember(who.key) { mutableStateOf("") }
    var override by remember(who.key) { mutableStateOf<Channel?>(null) }
    var sheet by remember(who.key) { mutableStateOf(false) }
    var linking by remember(who.key) { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val tick = uni.preferredTick
    val auto = People.choose(writable, uni.preferred(who.key), uni.auto, lastIn, draft.length)
    val channel = override?.takeIf { it in writable } ?: auto
    val list = rememberLazyListState()
    LaunchedEffect(shown.size) { if (shown.isNotEmpty()) list.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().windowInsetsPadding(WindowInsets.ime)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.ch_back), flat = true) { uni.close() }
            val tint = tintFor(who.key)
            Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(initials(who.name), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tint)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(who.name, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.pe_together), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
            }
            // Chi è solo nella Chat si unisce al suo contatto della Rubrica: da lì arrivano SMS, chiamate ed email.
            if (!who.contact && who.matrix.isNotEmpty()) RoundIcon(EuriseIcons.link(2.2f), stringResource(R.string.pe_link)) { linking = true }
            if (roomId != null) {
                Spacer(Modifier.width(6.dp))
                RoundIcon(EuriseIcons.chat(2.2f), stringResource(R.string.pe_open_chat)) { model.open(roomId) }
            }
            if (who.phones.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                RoundIcon(EuriseIcons.phone(2.2f), stringResource(R.string.pe_ch_call)) { uni.call(actions) }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(stringResource(R.string.pe_f_all), filter == Filter.All) { filter = Filter.All }
            Chip(stringResource(R.string.pe_ch_chat), filter == Filter.Chat) { filter = Filter.Chat }
            Chip(stringResource(R.string.pe_ch_sms), filter == Filter.Sms) { filter = Filter.Sms }
            Chip(stringResource(R.string.pe_ch_email), filter == Filter.Email) { filter = Filter.Email }
            Chip(stringResource(R.string.pe_f_calls), filter == Filter.Call) { filter = Filter.Call }
        }
        if (uni.threadLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty() && !uni.threadLoading) Text(stringResource(R.string.pe_empty_thread), fontSize = 14.sp, lineHeight = 20.sp,
                color = scheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = list, reverseLayout = true) {
                val reversed = shown.asReversed()
                items(reversed.size, key = { reversed[it].id }) { i ->
                    val e = reversed[i]
                    EntryView(uni, actions, e, room?.encrypted == true)
                    // Il giorno, sopra la prima cosa di ogni giorno (l'elenco è rovesciato).
                    val next = reversed.getOrNull(i + 1)
                    if (next == null || day(next.time) != day(e.time)) DayLabel(e.time)
                }
            }
        }
        // «Rispondi dove ti ha scritto»: si dice dove andrà il messaggio.
        if (uni.auto && lastIn != null && lastIn in writable && override == null && draft.length <= People.LONG_TEXT) {
            Text(stringResource(R.string.pe_hint_where, channelLabel(lastIn, room?.encrypted == true)), fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(SAGE.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp))
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            val pick = stringResource(R.string.pe_pick_channel, channel?.let { channelName(it) } ?: "")
            Row(
                Modifier.height(46.dp).clip(RoundedCornerShape(23.dp)).background(scheme.surface).clickable(role = Role.Button) { sheet = true }
                    .semantics { contentDescription = pick }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (channel != null) {
                    Icon(icon(channel), null, tint = color(channel), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(channelName(channel), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = color(channel))
                } else Text(stringResource(R.string.pe_no_channel), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                Icon(EuriseIcons.chevronDown(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp)).background(scheme.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (draft.isEmpty()) Text(stringResource(R.string.pe_write_to, who.name.substringBefore(' ')), fontSize = 15.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                BasicTextField(value = draft, onValueChange = { draft = it }, maxLines = 6,
                    textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(8.dp))
            val send = stringResource(R.string.ch_send)
            val ready = draft.isNotBlank() && channel != null
            // Tocco breve: il canale scelto (la stellina o «dove ti ha scritto»); pressione lunga: l'elenco dei canali.
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(if (ready) scheme.primary else scheme.primary.copy(alpha = 0.4f))
                    .combinedClickable(
                        role = Role.Button,
                        onLongClick = { sheet = true },
                        onClick = {
                            val c = channel ?: return@combinedClickable
                            if (draft.isBlank()) return@combinedClickable
                            val text = draft.trim()
                            draft = ""
                            uni.send(text, c, actions)
                        },
                    ).semantics { contentDescription = send },
                contentAlignment = Alignment.Center,
            ) { Icon(EuriseIcons.send(2.2f), null, tint = scheme.onPrimary, modifier = Modifier.size(19.dp)) }
        }
    }
    if (sheet) ChannelSheet(model, actions, who, writable, channel, room?.encrypted == true, onPick = { override = it; sheet = false }, onClose = { sheet = false })
    if (linking) LinkSheet(model, who) { linking = false }
}

private fun day(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun DayLabel(t: Long) {
    val d = day(t)
    val today = LocalDate.now()
    val label = when (d) {
        today -> stringResource(R.string.pe_today)
        today.minusDays(1) -> stringResource(R.string.pe_yesterday)
        else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEEE d MMMM" else "d MMMM yyyy", Locale.getDefault()))
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun channelLabel(c: Channel, encrypted: Boolean): String =
    if (c == Channel.Chat && encrypted) stringResource(R.string.pe_chat_encrypted) else channelName(c)

private fun clock(t: Long) = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t))

/** Una cosa del filo: fumetto (Chat, SMS), scheda (email) o riga (chiamata, con la nota; persa, con Richiama). */
@Composable
private fun EntryView(uni: Uni, actions: ChatActions, e: People.Entry, encrypted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    when (e.channel) {
        Channel.Call -> {
            val tint = if (e.missed) RUST else SAGE
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = if (e.missed) 0.10f else 0.12f))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.phone(2.2f), null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            e.missed -> stringResource(R.string.pe_call_missed)
                            e.incoming -> stringResource(R.string.pe_call_in, ((e.duration + 59) / 60).coerceAtLeast(1))
                            else -> stringResource(R.string.pe_call_out, ((e.duration + 59) / 60).coerceAtLeast(1))
                        },
                        fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (e.missed) RUST else scheme.onSurface,
                    )
                    if (e.note.isNotBlank()) Text(stringResource(R.string.pe_note_quote, e.note), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                if (e.missed) Text(stringResource(R.string.pe_call_back), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = scheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { uni.call(actions, e.address) }.padding(6.dp))
                else Text(clock(e.time), fontSize = 11.sp, color = scheme.onSurfaceVariant)
            }
        }
        Channel.Email -> Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Column(
                Modifier.fillMaxWidth(0.85f).clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)).background(scheme.surface)
                    .clickable(enabled = e.ref.isNotBlank()) { uni.openMail(e.ref, actions) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.mail(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.pe_email_from, e.address), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(e.subject.ifBlank { stringResource(R.string.pe_no_subject) }, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(clock(e.time) + " · " + stringResource(R.string.pe_open_email), fontSize = 11.sp, color = scheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End))
            }
        }
        else -> {
            val mine = !e.incoming
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                Column(
                    Modifier.widthIn(max = 300.dp).clip(if (mine) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                        .background(if (mine) BLUE.copy(alpha = 0.16f) else scheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon(e.channel), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(channelLabel(e.channel, encrypted), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                    }
                    Text(e.text, fontSize = 14.5.sp, lineHeight = 20.sp)
                    Text(if (e.id.startsWith("local:")) "…" else clock(e.time), fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                }
            }
        }
    }
}

// --- Scegli il canale (tavola UniCanale) ---

@Composable
private fun ChannelSheet(
    model: ChatModel, actions: ChatActions, who: People.Who, writable: List<Channel>, current: Channel?, encrypted: Boolean,
    onPick: (Channel) -> Unit, onClose: () -> Unit,
) {
    val uni = model.uni
    val scheme = MaterialTheme.colorScheme
    @Suppress("UNUSED_VARIABLE") val tick = uni.preferredTick
    val preferred = uni.preferred(who.key)
    Sheet(onClose) {
        Text(stringResource(R.string.pe_sheet_title, who.name.substringBefore(' ')), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.pe_sheet_sub), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        Card {
            Channel.entries.forEachIndexed { i, c ->
                val can = if (c == Channel.Call) who.phones.isNotEmpty() else c in writable
                val sub = when (c) {
                    Channel.Chat -> if (can) stringResource(if (encrypted || who.roomId == null) R.string.pe_sub_chat else R.string.pe_sub_chat_plain)
                        else stringResource(R.string.pe_sub_chat_missing)
                    Channel.Sms -> who.phones.firstOrNull()?.let { stringResource(R.string.pe_sub_sms, it) } ?: stringResource(R.string.pe_sub_no_number)
                    Channel.Email -> who.emails.firstOrNull()?.let { stringResource(R.string.pe_sub_email, it) } ?: stringResource(R.string.pe_sub_no_email)
                    Channel.Call -> if (can) stringResource(R.string.pe_sub_call) else stringResource(R.string.pe_sub_no_number)
                }
                // Chi non è ancora sulla Chat: la riga della Chat apre l'invito (tavola ChatInvita).
                val invite = c == Channel.Chat && !can && (who.phones.isNotEmpty() || who.emails.isNotEmpty())
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable(enabled = can || invite, role = Role.Button) {
                        when {
                            invite -> { onClose(); uni.openInvite(who, uni.rows.firstOrNull { it.who.key == who.key }?.channels.orEmpty()) }
                            c == Channel.Call -> { onClose(); uni.call(actions) }
                            else -> onPick(c)
                        }
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (can) color(c) else scheme.onSurfaceVariant
                    Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(icon(c), null, tint = tint, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (c == Channel.Chat) stringResource(R.string.pe_ch_chat_ciarly) else channelName(c), fontSize = 15.sp,
                            fontWeight = if (c == current) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (can) scheme.onSurface else scheme.onSurfaceVariant)
                        Text(sub, fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
                    }
                    Ticks(People.privacy(c, encrypted || (c == Channel.Chat && who.roomId == null)))
                    if (c != Channel.Call) {
                        val star = stringResource(R.string.pe_star)
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).clickable(enabled = can, role = Role.Button) { uni.setPreferred(who.key, c) }
                                .semantics { contentDescription = star },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(EuriseIcons.star(2f), null, tint = if (preferred == c) BRASS else scheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                        }
                    } else Spacer(Modifier.width(36.dp))
                }
                if (i < Channel.entries.lastIndex) Divider()
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.pe_auto_title), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pe_auto_where), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = uni.auto, onCheckedChange = uni::changeAuto)
            }
            Text(stringResource(R.string.pe_auto_rules), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        }
    }
}

// --- Unisci una persona della Chat al suo contatto ---

@Composable
private fun LinkSheet(model: ChatModel, who: People.Who, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf(who.name.takeUnless { it.startsWith("@") }.orEmpty()) }
    var found by remember { mutableStateOf<List<PeopleContract.Person>>(emptyList()) }
    LaunchedEffect(query) { delay(250); model.uni.search(query) { found = it } }
    Sheet(onClose) {
        Text(stringResource(R.string.pe_link_title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.pe_link_sub, who.name), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        SearchField(query, stringResource(R.string.pe_link_search)) { query = it }
        if (found.isEmpty()) Text(stringResource(if (query.isBlank()) R.string.pe_link_hint else R.string.pe_link_none), fontSize = 13.sp,
            color = scheme.onSurfaceVariant, textAlign = TextAlign.Start)
        else Card {
            found.take(8).forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
                        who.matrix.firstOrNull()?.let { model.uni.linkTo(it, p) }
                        onClose()
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text((p.phones + p.emails).take(2).joinToString(" · "), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ChannelIcons(buildSet {
                        add(Channel.Chat); if (p.phones.isNotEmpty()) { add(Channel.Sms); add(Channel.Call) }; if (p.emails.isNotEmpty()) add(Channel.Email)
                    }, size = 14)
                }
                if (i < found.size - 1) Divider()
            }
        }
    }
}

package eu.eurise.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

// Le basi della chat (tavola ChatBasi): risposte, reazioni, modifica, cancella, fissa, inoltra, sondaggi.

/** Le reazioni veloci del menu del messaggio. */
private val QUICK = listOf("👍", "❤️", "😂", "😮", "🙏", "👌")

private val PIN = Color(0xFF85672B)
private val QUOTE = Color(0xFF3E5C8A)

/** Il testo da mostrare di un messaggio: l'ultima modifica di chi l'ha scritto, se c'è. */
internal fun shownText(model: ChatModel, e: Event): String = model.relations.edited(e.id, e.sender) ?: e.text

/** La citazione del messaggio a cui si risponde, dentro il fumetto. */
@Composable
internal fun ReplyQuote(model: ChatModel, roomId: String, e: Event, fg: Color) {
    val target = e.replyTo ?: return
    val t = model.timeline(roomId).firstOrNull { it.id == target }
    Row(
        Modifier.padding(top = 4.dp, bottom = 2.dp).clip(RoundedCornerShape(6.dp)).background(fg.copy(alpha = 0.08f)),
    ) {
        Box(Modifier.width(3.dp).heightIn(min = 34.dp).background(QUOTE))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(t?.let { model.nameOf(it.sender) } ?: stringResource(R.string.ba_reply_unknown), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (t == null) fg.copy(alpha = 0.7f) else QUOTE, maxLines = 1)
            if (t != null) Text(
                when (t.kind) { Event.Kind.Image -> stringResource(R.string.ch_photo); Event.Kind.Encrypted -> stringResource(R.string.ch_encrypted_short); else -> shownText(model, t) },
                fontSize = 12.5.sp, color = fg.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Le reazioni sotto il fumetto: tocco per mettere o togliere la propria. */
@Composable
internal fun ReactionChips(model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val chips = model.relations.chips(e.id, model.session?.userId.orEmpty())
    if (chips.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val failed = stringResource(R.string.ch_send_failed)
    Row(Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            chips.forEach { c ->
                val desc = stringResource(R.string.ba_reaction_desc, c.key, c.count)
                Text(
                    "${c.key} ${c.count}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                    color = if (c.mine != null) scheme.primary else scheme.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (c.mine != null) scheme.primary.copy(alpha = 0.14f) else scheme.surface)
                        .clickable(role = Role.Button) { model.react(roomId, e.id, c.key) { actions.toast(failed) } }
                        .semantics { contentDescription = desc }.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** La barra dei messaggi fissati in cima: tocco = va al messaggio; con più fissati, il tocco passa al successivo. */
@Composable
internal fun PinnedBar(model: ChatModel, roomId: String, onJump: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val ids = model.pinned(roomId)
    val events = model.timeline(roomId)
    if (ids.isEmpty()) return
    var index by remember(roomId, ids.size) { mutableStateOf(0) }
    val id = ids[index.coerceIn(0, ids.lastIndex)]
    val e = events.firstOrNull { it.id == id }
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.surface)
            .clickable(role = Role.Button) { onJump(id); if (ids.size > 1) index = (index + 1) % ids.size }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EuriseIcons.pin(2.2f), null, tint = PIN, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.ba_pinned), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        Text(" · " + (e?.let { if (it.kind == Event.Kind.Poll) it.text else shownText(model, it) } ?: stringResource(R.string.ba_pinned_old)),
            fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (ids.size > 1) Text("${index + 1}/${ids.size}", fontSize = 11.sp, color = scheme.onSurfaceVariant)
    }
}

/** Sopra il campo di scrittura: a chi si risponde, o che si sta modificando; × per lasciar perdere. */
@Composable
internal fun ComposerContext(title: String, text: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surface)
            .padding(start = 0.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(40.dp).background(QUOTE))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = QUOTE, maxLines = 1)
            Text(text, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundIcon(EuriseIcons.close(2.2f), stringResource(R.string.ba_cancel), flat = true, onClick = onClose)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Il menu di un messaggio tenuto premuto: reazioni veloci, poi rispondi, copia, modifica, fissa, inoltra, cancella. */
@Composable
internal fun MessageMenu(
    model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean,
    onReply: () -> Unit, onEdit: () -> Unit, onForward: () -> Unit, onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val failed = stringResource(R.string.ch_send_failed)
    val clipboard = LocalClipboardManager.current
    val copied = stringResource(R.string.ba_copied)
    val pinned = e.id in model.pinned(roomId)
    val local = e.id.startsWith("local:")
    var confirmDelete by remember { mutableStateOf(false) }
    val me = model.session?.userId.orEmpty()
    Sheet(onClose) {
        Text(
            if (e.kind == Event.Kind.Poll) e.text else shownText(model, e).ifBlank { stringResource(R.string.ch_photo) },
            fontSize = 13.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (!local) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val chips = model.relations.chips(e.id, me)
            QUICK.forEach { k ->
                val on = chips.any { it.key == k && it.mine != null }
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(if (on) scheme.primary.copy(alpha = 0.18f) else scheme.surface)
                        .clickable(role = Role.Button) { model.react(roomId, e.id, k) { actions.toast(failed) }; onClose() },
                    contentAlignment = Alignment.Center,
                ) { Text(k, fontSize = 22.sp) }
            }
        }
        Card {
            if (!local) { MenuRow(EuriseIcons.reply(2f), stringResource(R.string.ba_reply)) { onReply(); onClose() }; Divider() }
            if (e.kind == Event.Kind.Text || e.kind == Event.Kind.Notice) {
                MenuRow(EuriseIcons.copy(2f), stringResource(R.string.ba_copy)) {
                    clipboard.setText(AnnotatedString(shownText(model, e))); actions.toast(copied); onClose()
                }
                Divider()
            }
            if (mine && !local && e.kind == Event.Kind.Text) { MenuRow(EuriseIcons.pencil(2f), stringResource(R.string.ba_edit)) { onEdit(); onClose() }; Divider() }
            if (!local) {
                MenuRow(EuriseIcons.pin(2f), stringResource(if (pinned) R.string.ba_unpin else R.string.ba_pin)) {
                    model.pin(roomId, e.id, !pinned) { actions.toast(actions.text(R.string.ba_pin_failed)) }; onClose()
                }
                Divider()
            }
            if (e.kind == Event.Kind.Text || e.kind == Event.Kind.Notice || e.kind == Event.Kind.Image || e.kind == Event.Kind.File) {
                MenuRow(EuriseIcons.send(2f), stringResource(R.string.ba_forward)) { onForward() }
            }
            if (mine && !local && e.kind == Event.Kind.Poll) {
                val poll = e.data?.let { runCatching { Relations.poll(JSONObject(it)) }.getOrNull() }
                val ended = poll != null && model.relations.tally(e.id, poll, me, System.currentTimeMillis()).ended
                if (!ended) {
                    Divider()
                    val text = stringResource(R.string.ba_poll_closed_text)
                    MenuRow(EuriseIcons.check(2f), stringResource(R.string.ba_poll_close)) { model.endPoll(roomId, e.id, text) { actions.toast(failed) }; onClose() }
                }
            }
            if (mine && !local) {
                Divider()
                MenuRow(EuriseIcons.trash(2f), stringResource(if (confirmDelete) R.string.ba_delete_confirm else R.string.ba_delete), color = scheme.error) {
                    if (!confirmDelete) confirmDelete = true
                    else { model.delete(roomId, e.id) { actions.toast(failed) }; onClose() }
                }
            }
        }
    }
}

/** Inoltra: si sceglie la chat. */
@Composable
internal fun ForwardSheet(model: ChatModel, actions: ChatActions, e: Event, onClose: () -> Unit) {
    val failed = stringResource(R.string.ch_send_failed)
    val scheme = MaterialTheme.colorScheme
    Sheet(onClose) {
        Text(stringResource(R.string.ba_forward_to), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Card {
                val rooms = model.rooms.filter { !it.invite }
                rooms.forEachIndexed { i, r ->
                    val sent = stringResource(R.string.ba_forwarded, r.name)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(role = Role.Button) {
                            model.forward(e, r.id) { actions.toast(failed) }; actions.toast(sent); onClose()
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(tintFor(r.id).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Text(initials(r.name), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tintFor(r.id))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(r.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (r.encrypted) Icon(EuriseIcons.lock(2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                    if (i < rooms.lastIndex) Divider()
                }
            }
        }
    }
}

/** Un sondaggio nella conversazione: tocco su una risposta per votare (o cambiare voto finché è aperto). */
@Composable
internal fun PollBubble(model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean, onLong: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val poll = remember(e.data) { e.data?.let { runCatching { Relations.poll(JSONObject(it)) }.getOrNull() } } ?: return
    val me = model.session?.userId.orEmpty()
    val now = System.currentTimeMillis()
    val t = model.relations.tally(e.id, poll, me, now)
    val scheme = MaterialTheme.colorScheme
    val failed = stringResource(R.string.ch_send_failed)
    val closedText = stringResource(R.string.ba_poll_closed_text)
    // Chi l'ha aperto lo chiude quando arriva l'ora (gli altri client non conoscono l'ora di chiusura).
    LaunchedEffect(e.id, t.ended) {
        if (mine && poll.closes != null && poll.closes <= System.currentTimeMillis() && !model.relations.endSent(e.id) && !e.id.startsWith("local:")) {
            model.endPoll(roomId, e.id, closedText) { }
        }
    }
    val total = t.counts.values.sum().coerceAtLeast(1)
    val showResults = poll.disclosed || t.ended
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp).fillMaxWidth(0.88f).clip(RoundedCornerShape(16.dp)).background(scheme.surface)
                .longPress(onLong).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.chart(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ba_poll_by, model.nameOf(e.sender)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            }
            Text(poll.question, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            poll.answers.forEach { (id, label) ->
                val n = t.counts[id] ?: 0
                val chosen = id in t.mine
                val frac = if (showResults) n.toFloat() / total else 0f
                Box(
                    Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(scheme.background)
                        .clickable(enabled = !t.ended && !e.id.startsWith("local:"), role = Role.RadioButton) { model.vote(roomId, e.id, id) { actions.toast(failed) } },
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(QUOTE.copy(alpha = if (chosen) 0.30f else 0.18f)))
                    Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (chosen) { Icon(EuriseIcons.check(2.6f), null, tint = QUOTE, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
                        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (showResults) Text(if (n == 1) stringResource(R.string.ba_poll_vote_one) else stringResource(R.string.ba_poll_votes, n),
                            fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                listOfNotNull(
                    when {
                        t.ended -> stringResource(R.string.ba_poll_ended)
                        poll.closes != null -> stringResource(R.string.ba_poll_closes, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(poll.closes)))
                        else -> null
                    },
                    stringResource(if (poll.disclosed) R.string.ba_poll_open_vote else R.string.ba_poll_secret_vote),
                ).joinToString(" · "),
                fontSize = 11.5.sp, color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Un sondaggio nuovo: domanda, da due a sei risposte, quando si chiude. */
@Composable
internal fun PollSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var question by remember { mutableStateOf("") }
    val answers = remember { mutableStateListOf("", "") }
    var closes by remember { mutableStateOf(0) }
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Text(stringResource(R.string.ba_poll_new), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(question, stringResource(R.string.ba_poll_question)) { question = it }
            answers.forEachIndexed { i, a -> Field(a, stringResource(R.string.ba_poll_answer, i + 1)) { answers[i] = it } }
            if (answers.size < 6) Chip(stringResource(R.string.ba_poll_add), selected = false) { answers.add("") }
            Text(stringResource(R.string.ba_poll_when), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(R.string.ba_poll_never, R.string.ba_poll_1h, R.string.ba_poll_tomorrow, R.string.ba_poll_week).forEachIndexed { i, res ->
                    Chip(stringResource(res), selected = closes == i) { closes = i }
                }
            }
        }
        val ready = question.isNotBlank() && answers.count { it.isNotBlank() } >= 2
        Pill(stringResource(R.string.ba_poll_send), EuriseIcons.send(2.2f), Modifier.fillMaxWidth(), strong = ready) {
            if (!ready) return@Pill
            val at = when (closes) {
                1 -> System.currentTimeMillis() + 3_600_000L
                2 -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
                3 -> System.currentTimeMillis() + 7 * 86_400_000L
                else -> null
            }
            model.sendPoll(roomId, question.trim(), answers.map { it.trim() }.filter { it.isNotEmpty() }, at) { actions.toast(failed) }
            onClose()
        }
    }
}

@Composable
private fun Field(value: String, hint: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, fontSize = 15.sp, color = scheme.onSurfaceVariant)
        BasicTextField(value = value, onValueChange = onChange, singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
    }
}

/** Pressione lunga (e niente altro) su un elemento. */
internal fun Modifier.longPress(onLong: () -> Unit): Modifier =
    pointerInput(onLong) { detectTapGestures(onLongPress = { onLong() }) }

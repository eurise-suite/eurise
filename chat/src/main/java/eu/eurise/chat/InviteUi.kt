package eu.eurise.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.chat.People.Channel
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.text.DateFormat
import java.util.Date

// Invito sulla Chat: tavole ChatInvita (una persona, dalla Rubrica o dal filo) e ChatInvitaElenco (più persone).

@Composable
private fun InviteHeader(title: String, sub: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.ch_back), flat = true, onClick = onBack)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun inviteText(name: String, link: String) = stringResource(R.string.inv_text, name.substringBefore(' '), link)

/** Un indirizzo per l'invito: il canale e il numero o l'email. */
private data class Target(val channel: Channel, val address: String)

@Composable
internal fun InviteScreen(model: ChatModel, actions: ChatActions) {
    val uni = model.uni
    val who = uni.invite ?: return
    val scheme = MaterialTheme.colorScheme
    BackHandler { uni.closeInvite() }
    val link = uni.inviteLink()
    val targets = who.phones.take(2).map { Target(Channel.Sms, it) } + who.emails.take(2).map { Target(Channel.Email, it) }
    val first = People.inviteChannel(who, uni.inviteUsed)
    var target by remember(who.key) { mutableStateOf(targets.firstOrNull { it.channel == first } ?: targets.firstOrNull()) }
    val start = link?.let { inviteText(who.name, it) }.orEmpty()
    var text by remember(who.key, link) { mutableStateOf(start) }
    var sending by remember(who.key) { mutableStateOf(false) }
    val sent = stringResource(R.string.inv_sent, who.name.substringBefore(' '))
    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().windowInsetsPadding(WindowInsets.ime)) {
        InviteHeader(stringResource(R.string.inv_title), null) { uni.closeInvite() }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tint = tintFor(who.key)
                Box(Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text(initials(who.name), fontSize = 23.sp, fontWeight = FontWeight.Bold, color = tint)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(who.name, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                    Text(uni.invited(who.key)?.let { stringResource(R.string.inv_already, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) }
                        ?: stringResource(R.string.inv_not_yet), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                }
            }
            if (link == null) {
                Text(stringResource(R.string.inv_no_account), fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurfaceVariant)
                return@Column
            }
            if (targets.isEmpty()) {
                Text(stringResource(R.string.inv_no_address), fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurfaceVariant)
                return@Column
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.inv_title), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    targets.forEach { t ->
                        Chip(stringResource(if (t.channel == Channel.Sms) R.string.inv_by_sms else R.string.inv_by_email, t.address), target == t) { target = t }
                    }
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.background).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    BasicTextField(value = text, onValueChange = { text = it }, textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
                }
                Text(stringResource(R.string.inv_how), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
            }
            val t = target
            Pill(stringResource(R.string.inv_send), EuriseIcons.send(2.2f), Modifier.fillMaxWidth(), strong = true) {
                if (t == null || sending || text.isBlank()) return@Pill
                sending = true
                uni.sendInvite(who, t.channel, t.address, text.trim(), actions) { ok ->
                    sending = false
                    if (ok && t.channel == Channel.Sms) actions.toast(sent)
                    uni.closeInvite()
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.inv_meanwhile), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.inv_meanwhile_text), fontSize = 13.sp, lineHeight = 19.sp)
            }
            Spacer(Modifier.navigationBarsPadding().heightIn(min = 16.dp))
        }
    }
}

@Composable
internal fun InviteListScreen(model: ChatModel, actions: ChatActions) {
    val uni = model.uni
    val scheme = MaterialTheme.colorScheme
    BackHandler { uni.openInviteList(false) }
    @Suppress("UNUSED_VARIABLE") val tick = uni.preferredTick
    val candidates = People.inviteCandidates(uni.rows).take(30)
    // Le prime tre non ancora invitate sono già scelte, come nella tavola.
    var chosen by remember { mutableStateOf(candidates.filter { uni.invited(it.who.key) == null }.take(3).map { it.who.key }.toSet()) }
    var pendingMail by remember { mutableStateOf<List<Pair<People.Who, String>>>(emptyList()) }
    val link = uni.inviteLink()
    val sentText = stringResource(R.string.inv_sent_n)
    val texts = candidates.associate { it.who.key to (link?.let { l -> inviteText(it.who.name, l) } ?: "") }
    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding()) {
        InviteHeader(stringResource(R.string.inv_title), stringResource(R.string.inv_list_sub)) { uni.openInviteList(false) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (link == null) Text(stringResource(R.string.inv_no_account), fontSize = 14.sp, color = scheme.onSurfaceVariant)
            else if (candidates.isEmpty()) Text(stringResource(R.string.inv_list_empty), fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurfaceVariant)
            else Card {
                candidates.forEachIndexed { i, r ->
                    val key = r.who.key
                    val on = key in chosen
                    val channel = People.inviteChannel(r.who, r.channels)
                    val sub = uni.invited(key)?.let { stringResource(R.string.inv_already, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) } ?: when {
                        channel == Channel.Email && Channel.Email in r.channels && r.channels.size == 1 -> stringResource(R.string.inv_why_email_only)
                        channel == Channel.Email -> stringResource(R.string.inv_why_email)
                        Channel.Sms in r.channels -> stringResource(R.string.inv_why_sms)
                        else -> stringResource(R.string.inv_why_calls)
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(role = Role.Checkbox) { chosen = if (on) chosen - key else chosen + key }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = tintFor(key)
                        Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Text(initials(r.who.name), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.who.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sub, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (on) scheme.primary else Color.Transparent)
                                .border(2.dp, scheme.primary, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(EuriseIcons.check(3f), null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp)) }
                    }
                    if (i < candidates.lastIndex) Divider()
                }
            }
            if (link != null && candidates.isNotEmpty()) Text(stringResource(R.string.inv_list_note), fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
            // Le email si scrivono una alla volta (ognuna con il suo destinatario, nessuno vede gli altri).
            if (pendingMail.isNotEmpty()) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.inv_mail_left), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                pendingMail.forEach { (w, address) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Chip(stringResource(R.string.inv_open_mail), selected = true) {
                            uni.sendInvite(w, Channel.Email, address, texts[w.key].orEmpty(), actions) { }
                            pendingMail = pendingMail.filterNot { it.first.key == w.key }
                        }
                    }
                }
            }
            if (link != null && chosen.isNotEmpty()) {
                Pill(if (chosen.size == 1) stringResource(R.string.inv_send_one) else stringResource(R.string.inv_send_n, chosen.size), EuriseIcons.send(2.2f), Modifier.fillMaxWidth(), strong = true) {
                    val picked = candidates.filter { it.who.key in chosen }
                    var sms = 0
                    val mails = ArrayList<Pair<People.Who, String>>()
                    picked.forEach { r ->
                        when (People.inviteChannel(r.who, r.channels)) {
                            Channel.Sms -> { sms++; uni.sendInvite(r.who, Channel.Sms, r.who.phones.first(), texts[r.who.key].orEmpty(), actions) { } }
                            Channel.Email -> mails += r.who to r.who.emails.first()
                            else -> Unit
                        }
                    }
                    chosen = emptySet()
                    if (sms > 0) actions.toast(String.format(sentText, sms))
                    pendingMail = mails
                }
            }
            Spacer(Modifier.navigationBarsPadding().heightIn(min = 16.dp))
        }
    }
}

package eu.eurise.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import eu.eurise.core.designsystem.icon.EuriseIcons

private val HELP = Color(0xFFA4502E)

/**
 * La schermata della modalità semplice (tavola ChatSemplice): «Ciao Bruna», le persone scelte in grande (tocco = la
 * chat, con i messaggi nuovi scritti per esteso), «Manda un vocale», il tasto rosso «Aiuto» e, sempre in fondo, che la
 * modalità è accesa e come toglierla.
 */
@Composable
internal fun SimpleHome(model: ChatModel, actions: ChatActions) {
    val scheme = MaterialTheme.colorScheme
    val c = model.simple
    val rooms = Semplice.shown(c, model.rooms)
    var voicePick by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.sm_hello, Semplice.greetingName(c, model.session?.userId.orEmpty())), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        if (rooms.isEmpty()) Text(stringResource(R.string.sm_empty), fontSize = 18.sp, color = scheme.onSurfaceVariant)
        rooms.forEach { r -> BigPerson(r) { model.open(r.id) } }
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(22.dp)).background(scheme.surface)
                .clickable(enabled = rooms.isNotEmpty(), role = Role.Button) { if (rooms.size == 1) model.open(rooms[0].id) else voicePick = true },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(EuriseIcons.mic(2.2f), null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.sm_voice), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(12.dp))
        if (c.help != null) Row(
            Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(24.dp)).background(HELP).clickable(role = Role.Button) { help = true },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(EuriseIcons.warning(2.6f), null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.sm_help), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Text(stringResource(R.string.sm_footer), fontSize = 13.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
        Text(stringResource(if (leave) R.string.sm_leave_confirm else R.string.sm_leave), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = scheme.primary, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                if (!leave) leave = true else model.changeSimple(c.copy(on = false))
            }.padding(10.dp))
    }
    if (voicePick) Sheet({ voicePick = false }) {
        Text(stringResource(R.string.sm_voice_to), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        rooms.forEach { r -> BigPerson(r) { voicePick = false; model.open(r.id) } }
        Text(stringResource(R.string.sm_voice_hint), fontSize = 15.sp, color = scheme.onSurfaceVariant)
    }
    if (help) HelpSheet(model, actions) { help = false }
}

@Composable
private fun BigPerson(r: Room, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = tintFor(r.id)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(RoundedCornerShape(22.dp)).background(scheme.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(initials(r.name), fontSize = 23.sp, fontWeight = FontWeight.Bold, color = tint)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(r.name, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (r.unread > 0) pluralStringResource(R.plurals.sm_new, r.unread, r.unread) else r.lastText, fontSize = 16.sp,
                color = if (r.unread > 0) scheme.primary else scheme.onSurfaceVariant, fontWeight = if (r.unread > 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(EuriseIcons.chevronRight(2.4f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
    }
}

/** «Chiedo aiuto a Luca?»: un messaggio e, se c'è il permesso, dove sei per un'ora. */
@Composable
private fun HelpSheet(model: ChatModel, actions: ChatActions, onClose: () -> Unit) {
    val context = LocalContext.current
    val who = model.simple.help?.let { id -> model.room(id)?.name } ?: ""
    val sent = stringResource(R.string.sm_help_sent, who)
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Text(stringResource(R.string.sm_help_ask, who), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.sm_help_what), fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(24.dp)).background(HELP).clickable(role = Role.Button) {
                val has = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                fun go(loc: Boolean) = model.askHelp(loc) { ok -> actions.toast(if (ok) sent else failed) }
                if (has) go(true) else actions.askLocation { ok -> go(ok) }
                onClose()
            },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) { Text(stringResource(R.string.sm_help_yes), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable(role = Role.Button, onClick = onClose),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) { Text(stringResource(R.string.sm_help_no), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

/** Dal foglio dell'account: accendere la modalità semplice, scegliere fino a quattro persone, chi riceve «Aiuto», il nome. */
@Composable
internal fun SimpleSetupSheet(model: ChatModel, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var c by remember { mutableStateOf(model.simple) }
    Sheet(onClose) {
        Text(stringResource(R.string.sm_menu), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Text(stringResource(R.string.sm_setup_explain), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp)).background(scheme.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (c.name.isEmpty()) Text(stringResource(R.string.sm_name_hint), fontSize = 15.sp, color = scheme.onSurfaceVariant)
            BasicTextField(value = c.name, onValueChange = { c = c.copy(name = it.take(24)) }, singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
        }
        Text(stringResource(R.string.sm_pick, Semplice.MAX), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface)) {
            items(model.rooms.filter { !it.invite && it.id != model.selfRoom }, key = { it.id }) { r ->
                val on = r.id in c.rooms
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Checkbox) { c = Semplice.toggle(c, r.id) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.name, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (on && c.help == r.id) Text(stringResource(R.string.sm_help_label), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = HELP,
                        modifier = Modifier.padding(end = 8.dp))
                    else if (on) Text(stringResource(R.string.sm_make_help), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary,
                        modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { c = c.copy(help = r.id) }.padding(4.dp))
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) scheme.primary else scheme.onSurface.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        if (on) Icon(EuriseIcons.check(2.6f), null, tint = scheme.onPrimary, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sm_on), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = c.on, onCheckedChange = { c = c.copy(on = it) }, enabled = c.rooms.isNotEmpty())
        }
        Pill(stringResource(R.string.cr_save), EuriseIcons.check(2f), Modifier.fillMaxWidth(), strong = true) { model.changeSimple(c); onClose() }
    }
}

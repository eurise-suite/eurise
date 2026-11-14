package eu.eurise.feature.assistant.online

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.assistant.R

private val Muted = Color(0xFFA9B4C4)
private val Soft = Color(0xFFCBD5E1)
private val Gold = Color(0xFFE9C77B)
private val Navy = Color(0xFF1F2A44)
private val Ink = Color(0xFF1E1D1B)
private val InkSoft = Color(0xFF56564F)
private val Rule = Color(0x121E1D1B)

fun AiService.jurisdiction(): Jurisdiction = when (where) {
    "CH" -> Jurisdiction.CH
    "UE" -> Jurisdiction.EU
    "USA" -> Jurisdiction.US
    "UK" -> Jurisdiction.UK
    else -> Jurisdiction.Various
}

/**
 * Il campo di Alfred con i tre tasti larghi uguali (tavole AlfUnico e AlfUnicoMancino, approvate il 14 novembre):
 * dal lato della mano dominante il PC (il più lontano dal pollice), l'AI online accanto, Invio sotto il pollice.
 * Il globo porta il nome del preferito: tocco = preferito, pressione lunga = «Chiedi a…».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AskBox(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    favorite: String,
    leftHanded: Boolean,
    voice: Boolean,
    onVoice: () -> Unit,
    onSend: () -> Unit,
    onOnline: () -> Unit,
    onChoose: () -> Unit,
    onPc: () -> Unit,
) {
    val label = stringResource(R.string.alfred_input_label)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, fontSize = 14.5.sp, color = InkSoft)
                BasicTextField(
                    value, onValue,
                    textStyle = TextStyle(color = Ink, fontSize = 14.5.sp),
                    cursorBrush = SolidColor(Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
            }
            if (voice) {
                val speak = stringResource(R.string.alfred_key_voice)
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onVoice).semantics { contentDescription = speak },
                    contentAlignment = Alignment.Center) {
                    Icon(EuriseIcons.mic(2.1f), null, tint = Navy, modifier = Modifier.size(19.dp))
                }
            }
        }
        val pc = @Composable { Key(EuriseIcons.monitor(2.1f), stringResource(R.string.alfred_key_pc), stringResource(R.string.alfred_key_pc_d), false, onPc, null) }
        val online = @Composable {
            Key(EuriseIcons.globe(2.1f), favorite, stringResource(R.string.alfred_key_online_d, favorite), false, onOnline, onChoose, star = true)
        }
        val send = @Composable { Key(EuriseIcons.send(2.1f), stringResource(R.string.alfred_key_send), stringResource(R.string.alfred_key_send_d), true, onSend, null) }
        val hintText = @Composable { modifier: Modifier ->
            Text(stringResource(R.string.alfred_key_hint), fontSize = 11.sp, lineHeight = 15.sp, color = Muted,
                textAlign = if (leftHanded) TextAlign.End else TextAlign.Start, modifier = modifier)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (leftHanded) { send(); online(); pc(); hintText(Modifier.weight(1f)) }
            else { hintText(Modifier.weight(1f)); pc(); online(); send() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    said: String,
    primary: Boolean,
    onClick: () -> Unit,
    onLong: (() -> Unit)?,
    star: Boolean = false,
) {
    val fg = if (primary) Navy else Color.White
    Box(
        Modifier.width(68.dp).height(50.dp).clip(RoundedCornerShape(18.dp))
            .background(if (primary) Color.White else Color(0x1AFFFFFF))
            .combinedClickable(onClick = onClick, onLongClick = onLong, role = Role.Button)
            .semantics { contentDescription = said },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp))
            Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1)
        }
        if (star) Icon(EuriseIcons.star(2.4f), null, tint = Gold, modifier = Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 6.dp).size(11.dp))
    }
}

/** Le tre vie, col semaforo: Invio sul telefono, l'AI preferita, il computer (tavola AlfUnico). */
@Composable
fun WaysLegend(favorite: AiService) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x0FFFFFFF)).padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        LegendRow(EuriseIcons.send(2.2f), stringResource(R.string.alfred_way_phone), 10)
        HorizontalDivider(color = Color(0x14FFFFFF))
        LegendRow(EuriseIcons.globe(2.2f), stringResource(R.string.alfred_way_online, favorite.name), favorite.score)
        HorizontalDivider(color = Color(0x14FFFFFF))
        LegendRow(EuriseIcons.monitor(2.2f), stringResource(R.string.alfred_way_pc), 9)
    }
}

@Composable
private fun LegendRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, score: Int) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = Soft, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
        DarkMeter(score)
    }
}

/**
 * «Chiedi a…» (tavola AlfScegli): la domanda come esce, i servizi col semaforo e la stellina del preferito, «Aggiungi»
 * da un'app o da un indirizzo, i servizi chiusi per ultimi con l'avviso.
 */
@Composable
fun ChooseView(
    services: AiServices,
    question: String,
    favorite: AiService,
    onFavorite: (AiService) -> Unit,
    onPick: (AiService) -> Unit,
    onBack: () -> Unit,
) {
    var mode by remember { mutableStateOf(0) } // 0 elenco, 1 app, 2 indirizzo
    var refresh by remember { mutableStateOf(0) }
    val list = remember(refresh) { services.all() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.alfred_choose_kicker).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.9.sp, color = Gold)
        Text(stringResource(R.string.alfred_choose_title), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (question.isNotBlank()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(10.dp)) {
                Text(stringResource(R.string.alfred_choose_out), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = InkSoft)
                Text("«$question»", fontSize = 13.5.sp, lineHeight = 19.sp, color = Ink)
            }
        }
        when (mode) {
            1 -> {
                val apps = remember { services.installedApps() }
                WhiteList {
                    if (apps.isEmpty()) Text(stringResource(R.string.alfred_no_apps), fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(vertical = 12.dp))
                    apps.forEachIndexed { i, a ->
                        if (i > 0) HorizontalDivider(color = Rule)
                        ServiceRow(a, fav = false, onStar = null, onClick = { services.add(a); refresh++; mode = 0 })
                    }
                }
                Chip(stringResource(R.string.alfred_cancel)) { mode = 0 }
            }
            2 -> {
                var address by remember { mutableStateOf("") }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.alfred_add_web_hint), fontSize = 12.sp, lineHeight = 17.sp, color = InkSoft)
                    BasicTextField(address, { address = it }, singleLine = true, textStyle = TextStyle(color = Ink, fontSize = 14.5.sp),
                        cursorBrush = SolidColor(Ink), modifier = Modifier.fillMaxWidth().border(1.dp, Rule, RoundedCornerShape(10.dp)).padding(10.dp))
                    services.fromAddress(address)?.takeIf { it.closed }?.let {
                        Text(stringResource(R.string.alfred_on_closed_warn), fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFF6E2530),
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFF6E3E5)).padding(8.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.alfred_cancel)) { mode = 0 }
                    Chip(stringResource(R.string.alfred_add_ok)) { services.fromAddress(address)?.let { services.add(it); refresh++; mode = 0 } }
                }
            }
            else -> {
                WhiteList {
                    list.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = Rule)
                        ServiceRow(s, fav = s.id == favorite.id, onStar = { onFavorite(s) }, onClick = { onPick(s) },
                            onRemove = if (s.custom) ({ services.remove(s); refresh++ }) else null)
                    }
                }
                Text(stringResource(R.string.alfred_choose_add).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.9.sp, color = Soft)
                WhiteList {
                    AddRow(EuriseIcons.grid(2.1f), stringResource(R.string.alfred_add_app), stringResource(R.string.alfred_add_app_d)) { mode = 1 }
                    HorizontalDivider(color = Rule)
                    AddRow(EuriseIcons.link(2.1f), stringResource(R.string.alfred_add_web), stringResource(R.string.alfred_add_web_d)) { mode = 2 }
                }
                Text(stringResource(R.string.alfred_choose_closed).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.9.sp, color = Color(0xFFE88A93))
                WhiteList {
                    val addable = services.closedOptions.filter { c -> list.none { it.id == c.id } }
                    addable.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = Rule)
                        ServiceRow(s, fav = false, onStar = null, onClick = { services.add(s.copy(custom = true)); refresh++ }, addLabel = true)
                    }
                    if (addable.isEmpty()) Text(stringResource(R.string.alfred_on_closed_warn), fontSize = 12.sp, color = InkSoft, modifier = Modifier.padding(vertical = 10.dp))
                }
                Text(stringResource(R.string.alfred_choose_foot), fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
                Chip(stringResource(R.string.alfred_on_back)) { onBack() }
            }
        }
    }
}

@Composable
private fun WhiteList(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp)) { content() }
}

@Composable
private fun ServiceRow(
    s: AiService,
    fav: Boolean,
    onStar: (() -> Unit)?,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    addLabel: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFECECE7)), contentAlignment = Alignment.Center) {
            Icon(if (s.isApp) EuriseIcons.grid(2.1f) else EuriseIcons.globe(2.1f), null, tint = Navy, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (s.company.isNotEmpty() && s.company != "app") "${s.name} · ${s.company}" else s.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ink)
            if (s.noteRes != 0) Text(stringResource(s.noteRes), fontSize = 12.sp, lineHeight = 16.sp, color = InkSoft)
            PrivacyMeter(s.score, s.jurisdiction())
            if (s.warnRes != 0) Text(stringResource(s.warnRes), fontSize = 11.5.sp, lineHeight = 16.sp, color = Color(0xFF6E2530),
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFF6E3E5)).padding(horizontal = 8.dp, vertical = 6.dp))
            if (onRemove != null) Text(stringResource(R.string.alfred_remove), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Navy,
                modifier = Modifier.clickable(onClick = onRemove).padding(vertical = 4.dp))
        }
        if (addLabel) Text(stringResource(R.string.alfred_add_ok), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy)
        if (onStar != null) {
            val said = stringResource(if (fav) R.string.alfred_fav else R.string.alfred_make_fav, s.name)
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onStar).semantics { contentDescription = said }, contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.star(if (fav) 3.2f else 1.8f), null, tint = if (fav) Color(0xFFB8862B) else Color(0xFF8A8A82), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AddRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFECECE7)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Navy, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(detail, fontSize = 12.sp, lineHeight = 16.sp, color = InkSoft)
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(17.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White) }
}

/** La risposta tornata (tavola AlfRisposta): i dati rimessi, e In Note, Promemoria, Manda a… con la scheda di conferma. */
@Composable
fun AnswerView(text: String, onNote: () -> Unit, onTask: () -> Unit, onSend: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = Ink,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(EuriseIcons.note(2f), stringResource(R.string.alfred_answer_note), Modifier.weight(1f), onNote)
            Action(EuriseIcons.task(2f), stringResource(R.string.alfred_answer_task), Modifier.weight(1f), onTask)
            Action(EuriseIcons.message(2f), stringResource(R.string.alfred_answer_send), Modifier.weight(1f), onSend)
        }
        Text(stringResource(R.string.alfred_answer_foot), fontSize = 12.sp, lineHeight = 17.sp, color = Soft)
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(64.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 5.dp))
    }
}

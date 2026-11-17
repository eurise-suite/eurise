package eu.eurise.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.studio.Channel
import eu.eurise.core.studio.Consent
import eu.eurise.core.studio.Need
import eu.eurise.core.studio.Patient
import eu.eurise.core.studio.Practice
import eu.eurise.core.studio.Queue
import eu.eurise.core.studio.Request
import eu.eurise.core.studio.StudioState
import eu.eurise.core.studio.Therapy
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Le pagine dello Studio, una sopra l'altra (Indietro torna alla precedente). */
sealed interface StudioPage {
    data object Home : StudioPage
    data object Patients : StudioPage
    data class Card(val id: String) : StudioPage
    data class Line(val need: Need) : StudioPage
    /** Paziente nuovo ([id] null) o da modificare; dalla chat arrivano già l'indirizzo Matrix e il nome. */
    data class Edit(val id: String?, val matrixId: String? = null, val name: String? = null) : StudioPage
}

private val AMBER = Color(0xFFA4502E)
private val AMBER_BG = Color(0x33C9962E)
private val GREEN = Color(0xFF4F6B5A)
private val GREEN_BG = Color(0x334F6B5A)

private fun needIcon(n: Need): ImageVector = when (n) {
    Need.Urgent -> EuriseIcons.bell(2.2f)
    Need.Prescription -> EuriseIcons.medical(2.2f)
    Need.Appointment -> EuriseIcons.calendar(2.2f)
    Need.Report -> EuriseIcons.document(2.2f)
    Need.Question -> EuriseIcons.chat(2.2f)
    Need.Thanks -> EuriseIcons.heart(2.2f)
}

private fun channelIcon(c: Channel): ImageVector = when (c) {
    Channel.Chat -> EuriseIcons.lock(2.2f)
    Channel.Email -> EuriseIcons.mail(2.2f)
    Channel.Sms -> EuriseIcons.message(2.2f)
}

private fun needLabel(n: Need) = when (n) {
    Need.Urgent -> R.string.st_need_urgent
    Need.Prescription -> R.string.st_need_prescription
    Need.Appointment -> R.string.st_need_appointment
    Need.Report -> R.string.st_need_report
    Need.Question -> R.string.st_need_question
    Need.Thanks -> R.string.st_need_thanks
}

private fun channelLabel(c: Channel) = when (c) {
    Channel.Chat -> R.string.st_ch_chat
    Channel.Email -> R.string.st_ch_email
    Channel.Sms -> R.string.st_ch_sms
}

private fun day(t: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))
private fun moment(t: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(t))

/** Lo Studio a schermo intero, sopra l'elenco delle chat. */
@Composable
fun StudioScreen(model: ChatModel, actions: ChatActions) {
    val ctx = LocalContext.current
    val state by StudioRepo.state.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { StudioRepo.load(ctx) }; loaded = true }
    val page = model.studioPage ?: return
    BackHandler { model.studioBack() }
    if (!loaded) { LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding().height(2.dp)); return }
    if (!state.enabled) { StudioIntro(model); return }
    when (page) {
        StudioPage.Home -> StudioHome(model, actions, state)
        StudioPage.Patients -> PatientList(model, state)
        is StudioPage.Card -> {
            val p = state.patient(page.id)
            if (p != null) PatientCard(model, actions, state, p) else LaunchedEffect(page) { model.studioBack() }
        }
        is StudioPage.Line -> QueueLine(model, actions, state, page.need)
        is StudioPage.Edit -> PatientEdit(model, state, page)
    }
}

@Composable
private fun Header(title: String, sub: String?, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.ch_back), flat = true, onClick = onBack)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        action?.invoke()
    }
}

@Composable
private fun Page(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
    )
}

// --- Studio spento: che cos'è e dove stanno i dati ---

@Composable
private fun StudioIntro(model: ChatModel) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Header(stringResource(R.string.st_title), null, onBack = { model.studioBack() })
        Page {
            Text(stringResource(R.string.st_intro), fontSize = 14.5.sp, lineHeight = 21.sp)
            Card {
                IntroRow(EuriseIcons.lock(2.2f), R.string.st_intro_where, R.string.st_intro_where_sub)
                Divider()
                IntroRow(EuriseIcons.shield(2.2f), R.string.st_intro_apps, R.string.st_intro_apps_sub)
                Divider()
                IntroRow(EuriseIcons.person(2.2f), R.string.st_intro_patients, R.string.st_intro_patients_sub)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AMBER_BG).padding(14.dp)) {
                Text(stringResource(R.string.st_intro_legal), fontSize = 12.5.sp, lineHeight = 18.sp)
            }
            Pill(stringResource(R.string.st_turn_on), EuriseIcons.medical(2f), Modifier.fillMaxWidth(), strong = true) {
                model.studioIo { StudioRepo.setEnabled(ctx, true) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IntroRow(icon: ImageVector, title: Int, sub: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(sub), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --- Spazio Studio (tavola StuSpazio) ---

@Composable
private fun StudioHome(model: ChatModel, actions: ChatActions, state: StudioState) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var practice by remember { mutableStateOf<Practice?>(null) }
    var off by remember { mutableStateOf(false) }
    val sum = Queue.summary(state, practice)
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Header(stringResource(R.string.st_title), DateFormat.getDateInstance(DateFormat.FULL).format(Date()), onBack = { model.studioBack() }) {
                RoundIcon(EuriseIcons.person(2.2f), stringResource(R.string.st_patients)) { model.studioGo(StudioPage.Patients) }
            }
            Page {
                if (state.patients.any { Practice.Acupuncture in it.practices }) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.st_all), selected = practice == null) { practice = null }
                    Chip(stringResource(R.string.st_general), selected = practice == Practice.General) { practice = Practice.General }
                    Chip(stringResource(R.string.st_acupuncture), selected = practice == Practice.Acupuncture) { practice = Practice.Acupuncture }
                }
                // Le urgenze in cima, sempre (tavola StuSpazio, «Da guardare prima»).
                sum.urgent.firstOrNull()?.let { r ->
                    val who = state.patient(r.patientId)?.name.orEmpty()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AMBER_BG).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(EuriseIcons.bell(2.2f), null, tint = AMBER, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.st_urgent_first, sum.urgent.size), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text("$who: «${r.text.take(90)}»", fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Chip(stringResource(R.string.st_open), selected = true) { model.studioGo(StudioPage.Line(Need.Urgent)) }
                    }
                }
                if (state.patients.isEmpty()) {
                    Card {
                        Text(stringResource(R.string.st_empty), fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 12.dp))
                    }
                } else {
                    SectionTitle(stringResource(R.string.st_today))
                    Card {
                        if (sum.byNeed.isEmpty()) Text(stringResource(R.string.st_nothing), fontSize = 13.5.sp, color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp))
                        sum.byNeed.entries.forEachIndexed { i, (need, list) ->
                            if (i > 0) Divider()
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { model.studioGo(StudioPage.Line(need)) }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(needIcon(need), null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(needLabel(need)) + " · ${list.size}", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                    Text(Queue.names(state, list).joinToString(", "), fontSize = 11.5.sp, color = scheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Chip(stringResource(R.string.st_open_line), selected = need != Need.Thanks) { model.studioGo(StudioPage.Line(need)) }
                            }
                        }
                    }
                    SectionTitle(stringResource(R.string.st_from_where))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Channel.entries.forEach { c ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(channelIcon(c), null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                                Text("${sum.byChannel[c] ?: 0}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                Text(stringResource(channelLabel(c)), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.onSurface.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.lock(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.st_space_note), fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                }
                Text(stringResource(R.string.st_turn_off), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant,
                    modifier = Modifier.clickable(role = Role.Button) { off = true }.padding(vertical = 8.dp, horizontal = 4.dp))
                Spacer(Modifier.height(90.dp))
            }
        }
        AddPatient(Modifier.align(Alignment.BottomEnd)) { model.studioGo(StudioPage.Edit(null)) }
    }
    if (off) Sheet({ off = false }) {
        Text(stringResource(R.string.st_turn_off), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.st_turn_off_sub), fontSize = 13.5.sp, lineHeight = 19.sp)
        Pill(stringResource(R.string.st_turn_off), EuriseIcons.close(2f), Modifier.fillMaxWidth(), strong = true) {
            off = false
            model.studioIo { StudioRepo.setEnabled(ctx, false) }
            model.studioClose()
        }
    }
}

@Composable
private fun AddPatient(modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.navigationBarsPadding().padding(16.dp).height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(scheme.primary).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EuriseIcons.plus(2.4f), null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.st_add_patient), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
    }
}

// --- La coda di un bisogno ---

@Composable
private fun QueueLine(model: ChatModel, actions: ChatActions, state: StudioState, need: Need) {
    val ctx = LocalContext.current
    val open = state.requests.filter { it.need == need && it.open }.sortedBy { it.time }
    val done = state.requests.filter { it.need == need && !it.open }.sortedByDescending { it.doneAt }.take(20)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header(stringResource(needLabel(need)), stringResource(R.string.st_open_count, open.size), onBack = { model.studioBack() })
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (open.isEmpty()) item { Text(stringResource(R.string.st_nothing), fontSize = 13.5.sp, modifier = Modifier.padding(8.dp)) }
            items(open, key = { it.id }) { r ->
                RequestCard(model, actions, state, r, primary = stringResource(R.string.st_done)) { model.studioIo { StudioRepo.setDone(ctx, r.id, true) } }
            }
            if (done.isNotEmpty()) item { SectionTitle(stringResource(R.string.st_done_recent)) }
            items(done, key = { "d" + it.id }) { r ->
                RequestCard(model, actions, state, r, primary = stringResource(R.string.st_undo)) { model.studioIo { StudioRepo.setDone(ctx, r.id, false) } }
            }
            item { Spacer(Modifier.height(40.dp).navigationBarsPadding()) }
        }
    }
}

@Composable
private fun RequestCard(model: ChatModel, actions: ChatActions, state: StudioState, r: Request, primary: String, onPrimary: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val p = state.patient(r.patientId)
    Card {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(channelIcon(r.channel), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(p?.name.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(moment(r.time), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
        }
        Text(r.text, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 6, overflow = TextOverflow.Ellipsis,
            color = if (r.open) scheme.onSurface else scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.st_reply), selected = false) { openSource(model, actions, p, r) }
            if (p != null) Chip(stringResource(R.string.st_card), selected = false) { model.studioGo(StudioPage.Card(p.id)) }
            Spacer(Modifier.weight(1f))
            Chip(primary, selected = r.open, onClick = onPrimary)
        }
    }
}

/** Torna da dove è arrivata la richiesta: la chat, l'app Email, la conversazione SMS. */
private fun openSource(model: ChatModel, actions: ChatActions, p: Patient?, r: Request) {
    when (r.channel) {
        Channel.Chat -> model.open(r.ref.substringBefore('|'))
        Channel.Email -> actions.launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + (p?.email ?: ""))))
        Channel.Sms -> actions.launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + (p?.phone ?: ""))))
    }
}

/** Scrive al paziente sul suo canale preferito (la Chat, se c'è). */
private fun contact(model: ChatModel, actions: ChatActions, p: Patient) {
    val chat = p.matrixId
    when {
        chat != null && (p.preferred == Channel.Chat || p.email == null && p.phone == null) -> {
            val room = model.directWith(chat)
            if (room != null) model.open(room) else model.newChat(chat) { id, err ->
                if (id != null) model.open(id) else actions.toast(actions.text(err ?: R.string.ch_err_server))
            }
        }
        p.preferred == Channel.Sms && p.phone != null || p.email == null && p.phone != null ->
            actions.launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + p.phone)))
        p.email != null -> actions.launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + p.email)))
    }
}

// --- Pazienti ---

@Composable
private fun PatientList(model: ChatModel, state: StudioState) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = state.patients.filter { q.isEmpty() || it.name.lowercase().contains(q) }.sortedBy { it.name.lowercase() }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Header(stringResource(R.string.st_patients), stringResource(R.string.st_patients_count, state.patients.size), onBack = { model.studioBack() })
            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { SearchField(query, stringResource(R.string.st_search)) { query = it } }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(shown, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable { model.studioGo(StudioPage.Card(p.id)) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(practicesText(p.practices) + " · " +
                                p.channels().joinToString(", ") { c -> c.name.let { if (it == "Sms") "SMS" else it } },
                                fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (p.verified) Tag(stringResource(R.string.st_verified), GREEN, GREEN_BG)
                        else Tag(stringResource(R.string.st_to_verify), AMBER, AMBER_BG)
                    }
                    Divider()
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
        AddPatient(Modifier.align(Alignment.BottomEnd)) { model.studioGo(StudioPage.Edit(null)) }
    }
}

/** «Medicina generale · Agopuntura», nell'ordine di sempre. */
@Composable
private fun practicesText(set: Set<Practice>): String {
    val general = stringResource(R.string.st_general)
    val acupuncture = stringResource(R.string.st_acupuncture)
    return set.sorted().joinToString(" · ") { if (it == Practice.General) general else acupuncture }
}

@Composable
private fun Tag(text: String, fg: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun PatientEdit(model: ChatModel, state: StudioState, page: StudioPage.Edit) {
    val ctx = LocalContext.current
    val old = page.id?.let { state.patient(it) }
    var name by remember { mutableStateOf(old?.name ?: page.name.orEmpty()) }
    var general by remember { mutableStateOf(old?.let { Practice.General in it.practices } ?: true) }
    var acupuncture by remember { mutableStateOf(old?.let { Practice.Acupuncture in it.practices } ?: false) }
    var matrix by remember { mutableStateOf(old?.matrixId ?: page.matrixId.orEmpty()) }
    var email by remember { mutableStateOf(old?.email.orEmpty()) }
    var phone by remember { mutableStateOf(old?.phone.orEmpty()) }
    var problem by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Header(stringResource(if (old == null) R.string.st_new_patient else R.string.st_edit_patient), null, onBack = { model.studioBack() })
        Page {
            Field(stringResource(R.string.st_name), name, KeyboardType.Text) { name = it; problem = null }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.st_general), selected = general) { general = !general || !acupuncture }
                Chip(stringResource(R.string.st_acupuncture), selected = acupuncture) { acupuncture = !acupuncture || !general }
            }
            SectionTitle(stringResource(R.string.st_where))
            Field(stringResource(R.string.st_matrix), matrix, KeyboardType.Email) { matrix = it; problem = null }
            Field(stringResource(R.string.st_email), email, KeyboardType.Email) { email = it }
            Field(stringResource(R.string.st_phone), phone, KeyboardType.Phone) { phone = it }
            Text(stringResource(R.string.st_where_note), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            problem?.let { Text(stringResource(it), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
            Pill(stringResource(R.string.st_save), EuriseIcons.check(2f), Modifier.fillMaxWidth(), strong = name.isNotBlank()) {
                if (name.isBlank()) { problem = R.string.st_err_name; return@Pill }
                val mx = matrix.trim().takeIf { it.isNotEmpty() }?.let { Matrix.normalizeId(it) ?: run { problem = R.string.st_err_matrix; return@Pill } }
                val em = email.trim().takeIf { it.isNotEmpty() }
                val ph = phone.trim().takeIf { it.isNotEmpty() }
                val practices = buildSet { if (general) add(Practice.General); if (acupuncture) add(Practice.Acupuncture) }.ifEmpty { setOf(Practice.General) }
                val base = old ?: Patient(UUID.randomUUID().toString(), name.trim(), createdAt = System.currentTimeMillis(),
                    preferred = when { mx != null -> Channel.Chat; ph != null -> Channel.Sms; else -> Channel.Email })
                // Cambiato l'indirizzo che era stato verificato: la verifica non vale più.
                val stillVerified = when (base.verifiedBy) {
                    Channel.Chat -> mx == base.matrixId
                    Channel.Email -> em == base.email
                    Channel.Sms -> ph == base.phone
                    null -> false
                }
                val p = base.copy(name = name.trim(), practices = practices, matrixId = mx, email = em, phone = ph,
                    verifiedAt = base.verifiedAt.takeIf { stillVerified }, verifiedBy = base.verifiedBy.takeIf { stillVerified })
                    .let { if (it.preferred !in it.channels() && it.channels().isNotEmpty()) it.copy(preferred = it.channels().first()) else it }
                model.studioIo { StudioRepo.savePatient(ctx, p) }
                model.studioBack()
                if (old == null) model.studioGo(StudioPage.Card(p.id))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// --- Scheda del paziente (tavola StuPaziente) ---

@Composable
private fun PatientCard(model: ChatModel, actions: ChatActions, state: StudioState, p: Patient) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var verifying by remember { mutableStateOf(false) }
    var addingDrug by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var consentLog by remember { mutableStateOf(false) }
    fun save(change: (Patient) -> Patient) = model.studioIo { StudioRepo.update(ctx) { s -> s.copy(patients = s.patients.map { if (it.id == p.id) change(it) else it }) } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header(p.name, stringResource(R.string.st_patient_sub, practicesText(p.practices).lowercase()), onBack = { model.studioBack() }) {
            if (p.channels().isNotEmpty()) RoundIcon(EuriseIcons.chat(2.2f), stringResource(R.string.st_write_to)) { contact(model, actions, p) }
        }
        Page {
            // Chi è: verifica, canale preferito, indirizzi.
            SectionTitle(stringResource(R.string.st_who))
            Card {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.qr(2.2f), null, tint = if (p.verified) GREEN else AMBER, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (p.verified) R.string.st_verified_qr else R.string.st_not_verified), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (p.verified) stringResource(R.string.st_verified_when, day(p.verifiedAt!!), stringResource(channelLabel(p.verifiedBy ?: Channel.Chat)))
                            else stringResource(R.string.st_not_verified_sub),
                            fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (p.verified) Tag(stringResource(R.string.st_verified), GREEN, GREEN_BG)
                    Chip(stringResource(if (p.verified) R.string.st_verify_again else R.string.st_verify), selected = !p.verified) { verifying = true }
                }
                Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.st_preferred), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.channels().forEach { c -> Chip(stringResource(channelLabel(c)), selected = p.preferred == c) { save { it.copy(preferred = c) } } }
                    }
                }
                Divider()
                Column(Modifier.fillMaxWidth().clickable { model.studioGo(StudioPage.Edit(p.id)) }.padding(vertical = 10.dp)) {
                    listOfNotNull(p.matrixId, p.email, p.phone).ifEmpty { listOf(stringResource(R.string.st_no_address)) }.forEach {
                        Text(it, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(stringResource(R.string.st_edit_patient), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                }
            }
            // Consensi: la revoca vale subito e resta scritta.
            SectionTitle(stringResource(R.string.st_consents))
            Card {
                Consent.entries.forEachIndexed { i, c ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(consentLabel(c)), fontSize = 13.sp)
                            val last = p.consents.lastOrNull { it.consent == c }
                            if (last != null) Text(stringResource(if (last.granted) R.string.st_consent_since else R.string.st_consent_revoked, day(last.at)),
                                fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = p.has(c), onCheckedChange = { on -> save { it.withConsent(c, on, System.currentTimeMillis()) } })
                    }
                }
                Divider()
                Text(stringResource(R.string.st_consent_note), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = p.consents.isNotEmpty()) { consentLog = !consentLog }.padding(vertical = 10.dp))
                if (consentLog) p.consents.asReversed().forEach { e ->
                    Text("${moment(e.at)} · ${stringResource(consentLabel(e.consent))}: " +
                        stringResource(if (e.granted) R.string.st_given else R.string.st_revoked), fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            // Terapia abituale: quella che può chiedere senza visita.
            SectionTitle(stringResource(R.string.st_therapy))
            Card {
                p.therapies.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(EuriseIcons.medical(2.2f), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            val sub = listOf(t.dose, t.since.takeIf { it.isNotBlank() }?.let { ctx.getString(R.string.st_since, it) }.orEmpty()).filter { it.isNotBlank() }
                            if (sub.isNotEmpty()) Text(sub.joinToString(" · "), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                        }
                        RoundIcon(EuriseIcons.trash(2f), stringResource(R.string.st_remove_drug, t.name), flat = true) { save { it.copy(therapies = it.therapies.filter { x -> x.id != t.id }) } }
                    }
                    Divider()
                }
                Text(stringResource(R.string.st_add_drug), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { addingDrug = true }.padding(vertical = 12.dp))
            }
            // Storia degli scambi.
            val history = Queue.history(state, p.id)
            if (history.isNotEmpty()) {
                SectionTitle(stringResource(R.string.st_history))
                Card {
                    history.take(30).forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(needIcon(r.need), null, tint = if (r.need == Need.Urgent) AMBER else scheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(needLabel(r.need)) + " · " + day(r.time), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(stringResource(channelLabel(r.channel)) + " · " + r.text, fontSize = 11.5.sp, color = scheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (!r.open) Icon(EuriseIcons.check(2.2f), null, tint = GREEN, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            Text(stringResource(R.string.st_remove_patient), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.error,
                modifier = Modifier.clickable(role = Role.Button) { removing = true }.padding(vertical = 10.dp, horizontal = 4.dp))
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
    if (verifying) VerifySheet(model, state, p) { verifying = false }
    if (addingDrug) DrugSheet(onClose = { addingDrug = false }) { t -> save { it.copy(therapies = it.therapies + t) } }
    if (removing) Sheet({ removing = false }) {
        Text(stringResource(R.string.st_remove_patient), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.st_remove_sub, p.name), fontSize = 13.5.sp, lineHeight = 19.sp)
        Pill(stringResource(R.string.st_remove_patient), EuriseIcons.trash(2f), Modifier.fillMaxWidth(), strong = true) {
            removing = false
            model.studioIo { StudioRepo.removePatient(ctx, p.id) }
            model.studioBack()
        }
    }
}

private fun consentLabel(c: Consent) = when (c) {
    Consent.Notice -> R.string.st_consent_notice
    Consent.OnlineReports -> R.string.st_consent_reports
    Consent.Reminders -> R.string.st_consent_reminders
}

@Composable
private fun DrugSheet(onClose: () -> Unit, onAdd: (Therapy) -> Unit) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var since by remember { mutableStateOf("") }
    Sheet(onClose) {
        Text(stringResource(R.string.st_add_drug), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Field(stringResource(R.string.st_drug), name, KeyboardType.Text) { name = it }
        Field(stringResource(R.string.st_dose), dose, KeyboardType.Text) { dose = it }
        Field(stringResource(R.string.st_drug_since), since, KeyboardType.Text) { since = it }
        Pill(stringResource(R.string.st_save), EuriseIcons.check(2f), Modifier.fillMaxWidth(), strong = name.isNotBlank()) {
            if (name.isBlank()) return@Pill
            onAdd(Therapy(UUID.randomUUID().toString(), name.trim(), dose.trim(), since.trim()))
            onClose()
        }
    }
}

/**
 * La verifica in studio: il QR apre la chat con lo studio (matrix.to, va bene anche Element), il codice lo scrive il
 * paziente in Chat, per email o per SMS. Quando arriva, la scheda diventa verde da sola.
 */
@Composable
private fun VerifySheet(model: ChatModel, state: StudioState, p: Patient, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val me = model.session?.userId.orEmpty()
    var code by remember { mutableStateOf<String?>(null) }
    val startedAt = remember { System.currentTimeMillis() }
    LaunchedEffect(p.id) { code = withContext(Dispatchers.IO) { StudioRepo.newCode(ctx, p.id) } }
    val done = (p.verifiedAt ?: 0L) >= startedAt
    Sheet(onClose) {
        Text(stringResource(R.string.st_verify_title, p.name), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        if (done) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GREEN_BG).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.check(2.4f), null, tint = GREEN, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.st_verify_done, stringResource(channelLabel(p.verifiedBy ?: Channel.Chat))), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Pill(stringResource(R.string.st_close), EuriseIcons.check(2f), Modifier.fillMaxWidth(), strong = true, onClick = onClose)
        } else {
            Text(stringResource(R.string.st_verify_how), fontSize = 13.sp, lineHeight = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (me.isNotEmpty()) Box(Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) { Qr("https://matrix.to/#/$me") }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.st_code), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(code ?: "…", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Text(stringResource(R.string.st_code_valid), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.st_verify_other), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Un QR disegnato nella schermata (ZXing, già nella suite), un quadratino per modulo. */
@Composable
private fun Qr(text: String) {
    val m = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1))
    }
    Canvas(Modifier.fillMaxSize().aspectRatio(1f)) {
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) if (m.get(x, y)) {
            drawRect(Color(0xFF1E1D1B), topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

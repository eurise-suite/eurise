package eu.eurise.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.studio.Bussola
import eu.eurise.core.studio.Bussola.Prescription
import eu.eurise.core.studio.Bussola.RxState
import eu.eurise.core.studio.Bussola.RxStatus
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import org.json.JSONObject

/** Verde petrolio di BUSSOLA (chiaro e scuro) e le tinte degli avvisi della suite. */
@Composable
private fun petrol() = if (isSystemInDarkTheme()) Color(0xFF79BCC1) else Color(0xFF135C63)
private val BORDEAUX = Color(0xFF8C2F39)
private val BORDEAUX_BG = Color(0x338C2F39)
private val BRASS = Color(0xFF85672B)
private val BRASS_BG = Color(0x3385672B)
private val SAGE = Color(0xFF4F6B5A)
private val SAGE_BG = Color(0x334F6B5A)

private fun day(t: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))

/** Il promemoria fisso del 118, in ogni schermata con la chat dello studio (lato paziente). */
@Composable
fun UrgentBanner() {
    Row(Modifier.fillMaxWidth().background(BORDEAUX_BG).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(EuriseIcons.phone(2.2f), null, tint = BORDEAUX, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.bu_urgent), fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
fun UrgentHint() {
    Text(stringResource(R.string.bu_urgent_short), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BORDEAUX,
        modifier = Modifier.padding(start = 20.dp, bottom = 4.dp))
}

/** Le azioni grandi sopra la chat (tavola BusChat; per il medico, tavola BusStudio). Tasti dello stesso gruppo, stessa larghezza. */
@Composable
fun BussolaBar(model: ChatModel, actions: ChatActions, roomId: String, role: ChatModel.BussolaRole) {
    var sheet by remember(roomId) { mutableStateOf<String?>(null) }
    if (role == ChatModel.BussolaRole.Patient) {
        UrgentBanner()
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigAction(EuriseIcons.medical(2.2f), stringResource(R.string.bu_renew), Modifier.weight(1f)) { sheet = "renew" }
            BigAction(EuriseIcons.document(2.2f), stringResource(R.string.bu_prescriptions), Modifier.weight(1f)) { sheet = "rx" }
            BigAction(EuriseIcons.clock(2.2f), stringResource(R.string.bu_hours), Modifier.weight(1f)) { sheet = "info" }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigAction(EuriseIcons.calendar(2.2f), stringResource(R.string.bu_invite), Modifier.weight(1f)) { sheet = "invite" }
            BigAction(EuriseIcons.refresh(2.2f), stringResource(R.string.bu_update), Modifier.weight(1f)) { sheet = "update" }
            BigAction(EuriseIcons.document(2.2f), stringResource(R.string.bu_rep_send_short), Modifier.weight(1f)) { sheet = "report" }
        }
    }
    when (sheet) {
        "renew" -> RenewSheet(model, actions, roomId) { sheet = null }
        "rx" -> PrescriptionsSheet(model, actions, roomId) { sheet = null }
        "info" -> InfoSheet(model, actions, roomId) { sheet = null }
        "invite" -> InviteSheet(model, actions, roomId) { sheet = null }
        "update" -> UpdateSheet(model, actions, roomId) { sheet = null }
        "report" -> ReportSheet(model, actions, roomId, role) { sheet = null }
    }
}

@Composable
private fun BigAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = petrol(), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun BigButton(label: String, strong: Boolean, onClick: () -> Unit) {
    val p = petrol()
    Box(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(25.dp)).background(if (strong) p else MaterialTheme.colorScheme.surface)
            .then(if (strong) Modifier else Modifier.border(1.dp, p.copy(alpha = 0.5f), RoundedCornerShape(25.dp)))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (strong) MaterialTheme.colorScheme.surface else p, textAlign = TextAlign.Center) }
}

@Composable
private fun Tag(text: String, fg: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(9.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}

/** Le tre tappe del rinnovo: ricevuta, in preparazione, ricetta pronta. */
@Composable
private fun Steps(state: RxState) {
    val reached = when (state) { RxState.Received -> 1; RxState.Preparing -> 2; else -> 3 }
    val labels = listOf(R.string.bu_step_received, R.string.bu_step_preparing, R.string.bu_step_ready)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, l ->
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i < reached) petrol() else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))
                Text(stringResource(l), fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp,
                    color = if (i < reached) petrol() else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

// --- I messaggi con un tipo nella conversazione ---

@Composable
private fun CenterLine(text: String) {
    Text(text, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
}

/** Un messaggio di La mia Bussola nella conversazione. */
@Composable
fun BussolaBubble(model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val c = runCatching { JSONObject(e.data ?: "{}") }.getOrNull() ?: return
    val items = model.bussolaItems(roomId)
    val studio = Bussola.studioSender(items)
    val me = model.session?.userId.orEmpty()
    val iAmStudio = studio == me
    val type = c.optString("msgtype")
    val report = Bussola.readReport(c, e.time)
    // Dopo 45 giorni un referto non si mostra più (e chi l'ha mandato lo cancella dalla stanza).
    if (report != null && !e.id.startsWith("local:") && Bussola.reportExpired(e.time, System.currentTimeMillis())) {
        CenterLine(stringResource(R.string.bu_rep_gone)); return
    }
    // Orari, terapia e stati: una riga al centro (lo stato si vede già nella richiesta).
    when (type) {
        Bussola.STUDIO -> { CenterLine(stringResource(R.string.bu_line_studio)); return }
        Bussola.THERAPY -> { CenterLine(stringResource(R.string.bu_line_therapy)); return }
        Bussola.RX_STATUS -> {
            val s = Bussola.readStatus(c)
            if (s != null && items.any { it.eventId == s.requestId }) return
            CenterLine(e.text); return
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surface)
                .then(if (mine) Modifier.border(1.5.dp, petrol().copy(alpha = 0.6f), RoundedCornerShape(18.dp)) else Modifier)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (mine) stringResource(R.string.bu_you) else model.nameOf(e.sender) + if (e.sender == studio) " · " + stringResource(R.string.bu_studio) else "",
                    fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = petrol(), modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Tag(stringResource(when {
                    report != null -> R.string.bu_tag_report
                    type == Bussola.RX_REQUEST -> R.string.bu_tag_renew
                    type == Bussola.CHECKUP_INVITE -> R.string.bu_tag_checkup
                    else -> R.string.bu_tag_reply
                }), petrol(), petrol().copy(alpha = 0.15f))
            }
            if (report != null) ReportBody(model, actions, e, report)
            else when (type) {
                Bussola.RX_REQUEST -> RequestBody(model, actions, roomId, e, c, items, studio, iAmStudio)
                Bussola.CHECKUP_INVITE -> InviteBody(model, actions, roomId, c, items, me, iAmStudio)
                else -> Text(e.text, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.time)), fontSize = 10.5.sp,
                color = scheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
        }
    }
}

@Composable
private fun RequestBody(
    model: ChatModel, actions: ChatActions, roomId: String, e: Event, c: JSONObject,
    items: List<Bussola.Item>, studio: String?, iAmStudio: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val r = Bussola.readRequest(c) ?: return
    val (status, at) = Bussola.statusOf(e.id, items, studio)
    val shown = Bussola.prescription(status, at, System.currentTimeMillis())
    val failed = stringResource(R.string.ch_send_failed)
    var nreSheet by remember(e.id) { mutableStateOf(false) }
    Text(r.drugs.joinToString("\n"), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    if (e.id.startsWith("local:")) return
    when (shown) {
        Prescription.Review -> Row(Modifier.padding(top = 8.dp)) { Tag(stringResource(R.string.bu_review), BRASS, BRASS_BG) }
        Prescription.Collected -> Row(Modifier.padding(top = 8.dp)) { Tag(stringResource(R.string.bu_collected), SAGE, SAGE_BG) }
        else -> Steps(status.state)
    }
    val code = status.nre
    if (status.state == RxState.Ready && code != null) {
        val nre = Bussola.nreGroups(code)
        Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(petrol().copy(alpha = 0.10f)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bu_nre), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (shown == Prescription.Expired) Tag(stringResource(R.string.bu_expired), BORDEAUX, BORDEAUX_BG)
                else if (shown == Prescription.Valid) Tag(stringResource(R.string.bu_valid), SAGE, SAGE_BG)
            }
            Text(nre, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            if (at != null) Text(stringResource(if (shown == Prescription.Expired) R.string.bu_expired_long else R.string.bu_valid_until, day(Bussola.validUntil(at))),
                fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
            Text(stringResource(R.string.bu_pharmacy), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
        }
        if (!iAmStudio && shown == Prescription.Valid) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.bu_copy), selected = false) { actions.copySecret(code); actions.toast(actions.text(R.string.bu_copied)) }
                Chip(stringResource(R.string.bu_share), selected = false) {
                    val text = actions.text(R.string.bu_share_text).format(r.drugs.joinToString(", "), nre)
                    actions.launch(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                }
                Chip(stringResource(R.string.bu_taken), selected = false) {
                    model.sendBussola(roomId, Bussola.status(RxStatus(e.id, RxState.Collected))) { actions.toast(failed) }
                }
            }
        }
    }
    // Lato studio: l'assistente o il medico portano avanti il rinnovo; il codice NRE viene dalla ricetta fatta in Millewin.
    if (iAmStudio && status.state != RxState.Ready && status.state != RxState.Collected) {
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (status.state == RxState.Received) Chip(stringResource(R.string.bu_step_preparing), selected = false) {
                model.sendBussola(roomId, Bussola.status(RxStatus(e.id, RxState.Preparing))) { actions.toast(failed) }
            }
            Chip(stringResource(R.string.bu_review), selected = false) {
                model.sendBussola(roomId, Bussola.status(RxStatus(e.id, RxState.Review))) { actions.toast(failed) }
            }
            Chip(stringResource(R.string.bu_step_ready), selected = true) { nreSheet = true }
        }
    }
    if (nreSheet) NreSheet(model, actions, roomId, r.drugs, onClose = { nreSheet = false }) { nre ->
        model.sendBussola(roomId, Bussola.status(RxStatus(e.id, RxState.Ready, nre))) { actions.toast(failed) }
    }
}

@Composable
private fun InviteBody(model: ChatModel, actions: ChatActions, roomId: String, c: JSONObject, items: List<Bussola.Item>, me: String, iAmStudio: Boolean) {
    val inv = Bussola.readInvite(c) ?: return
    val failed = stringResource(R.string.ch_send_failed)
    Text(inv.text, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    if (iAmStudio) {
        val patient = items.firstOrNull { it.sender != me }?.sender
        val answer = patient?.let { Bussola.replyTo(inv.id, items, it) }
        Text(if (answer != null) stringResource(R.string.bu_answered_by, answer) else stringResource(R.string.bu_no_answer),
            fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val answer = Bussola.replyTo(inv.id, items, me)
    if (answer != null) {
        Tag(stringResource(R.string.bu_you_answered, answer), SAGE, SAGE_BG)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Bussola.CHECKUP_CHOICES.forEach { choice ->
            BigButton(choice, strong = false) { model.sendBussola(roomId, Bussola.reply(Bussola.Reply(inv.id, choice))) { actions.toast(failed) } }
        }
    }
}

/** Un referto nella conversazione: nome, misura, le due righe, fino a quando si vede, «Apri». */
@Composable
private fun ReportBody(model: ChatModel, actions: ChatActions, e: Event, r: Bussola.Report) {
    val scheme = MaterialTheme.colorScheme
    val local = e.id.startsWith("local:")
    val failed = stringResource(R.string.ch_file_failed)
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(petrol().copy(alpha = 0.10f))
            .clickable(enabled = !local && e.mediaUrl != null, role = Role.Button) { model.media(e) { f -> if (f != null) actions.openFile(f) else actions.toast(failed) } }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (r.mime.startsWith("image/")) EuriseIcons.image(2.2f) else EuriseIcons.document(2.2f), null, tint = petrol(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(if (local) stringResource(R.string.bu_rep_sending) else sizeLabel(r.size), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        if (!local) Text(stringResource(R.string.bu_rep_open), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = petrol())
    }
    if (r.note.isNotBlank()) Text(r.note, fontSize = 14.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
    if (!local) Text(stringResource(R.string.bu_rep_until, day(r.expiresAt)), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

private fun sizeLabel(bytes: Long) = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> "%d KB".format(maxOf(1, bytes / 1024))
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/**
 * Invia un referto (tavola BusReferto): PDF, JPG o PNG fino a 10 MB, due righe facoltative per il medico, visibile 45
 * giorni. Test genetici ed esami HIV no: si consegnano di persona. Dallo studio serve il consenso ai referti online
 * nella scheda del paziente.
 */
@Composable
internal fun ReportSheet(model: ChatModel, actions: ChatActions, roomId: String, role: ChatModel.BussolaRole, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var file by remember { mutableStateOf<ChatModel.Picked?>(null) }
    var note by remember { mutableStateOf("") }
    val problem = file?.let { f -> if (f.size > 0) Bussola.checkReport(f.mime, f.size) else Bussola.checkReport(f.mime, 1) }
    // Lato studio: il consenso ai referti online del paziente (scheda nello Studio).
    val studio by StudioRepo.state.collectAsStateWithLifecycle()
    val peer = model.peerOf(roomId)
    val patient = studio.patients.firstOrNull { it.matrixId.equals(peer, ignoreCase = true) }
    val consentMissing = role == ChatModel.BussolaRole.Studio && patient?.has(eu.eurise.core.studio.Consent.OnlineReports) != true
    Sheet(onClose) {
        Text(stringResource(R.string.bu_rep_title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.bu_rep_sub), fontSize = 13.5.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BRASS_BG).padding(12.dp)) {
            Text(stringResource(R.string.bu_rep_never), fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (consentMissing) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BORDEAUX_BG).padding(12.dp)) {
                Text(stringResource(if (patient == null) R.string.bu_need_patient else R.string.bu_rep_consent), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        BigButton(file?.name ?: stringResource(R.string.bu_rep_pick), strong = false) {
            actions.pickFile(Bussola.REPORT_TYPES.toTypedArray()) { uri -> uri?.let(model::describe)?.let { file = it } }
        }
        file?.let { f ->
            if (problem != null) Text(stringResource(model.reportProblem(problem)), fontSize = 12.5.sp, color = scheme.error)
            else if (f.size > 0) Text(sizeLabel(f.size), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        Field(stringResource(R.string.bu_rep_note), note, KeyboardType.Text) { if (it.length <= Bussola.REPORT_NOTE_MAX) note = it }
        val ready = file != null && problem == null && !consentMissing
        Pill(stringResource(R.string.ch_send), EuriseIcons.send(2f), Modifier.fillMaxWidth(), strong = ready) {
            val f = file ?: return@Pill
            if (!ready) return@Pill
            model.sendFile(roomId, f, note) { actions.toast(actions.text(it)) }
            onClose()
        }
    }
}

// --- Fogli ---

/** Chiedi un rinnovo (tavola BusRinnovo): un tocco sulla terapia abituale che lo studio ha indicato. */
@Composable
private fun RenewSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val items = model.bussolaItems(roomId)
    val drugs = Bussola.latestTherapy(items, Bussola.studioSender(items))
    var chosen by remember { mutableStateOf(setOf<String>()) }
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Text(stringResource(R.string.bu_renew_title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.bu_renew_sub), fontSize = 13.5.sp, color = scheme.onSurfaceVariant)
        if (drugs.isEmpty()) Text(stringResource(R.string.bu_renew_empty), fontSize = 14.sp, lineHeight = 20.sp)
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            drugs.forEach { d ->
                val on = d in chosen
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Checkbox) { chosen = if (on) chosen - d else chosen + d }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(EuriseIcons.medical(2.2f), null, tint = petrol(), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(d, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (on) petrol() else Color.Transparent)
                        .border(2.dp, if (on) petrol() else scheme.onSurfaceVariant, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        if (on) Icon(EuriseIcons.check(2.6f), null, tint = scheme.surface, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BRASS_BG).padding(12.dp)) {
            Text(stringResource(R.string.bu_renew_new), fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (drugs.isNotEmpty()) BigButton(
            if (chosen.isEmpty()) stringResource(R.string.bu_renew_pick) else stringResource(R.string.bu_renew_send, chosen.size), strong = chosen.isNotEmpty(),
        ) {
            if (chosen.isEmpty()) return@BigButton
            model.sendBussola(roomId, Bussola.request(Bussola.RxRequest(UUID.randomUUID().toString(), drugs.filter { it in chosen }))) { actions.toast(failed) }
            onClose()
        }
    }
}

/** Le mie ricette (tavola BusRicette): le richieste, il loro stato e il codice. */
@Composable
private fun PrescriptionsSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val items = model.bussolaItems(roomId)
    val studio = Bussola.studioSender(items)
    val now = System.currentTimeMillis()
    val requests = items.filter { it.sender != studio }.mapNotNull { i -> Bussola.readRequest(i.content)?.let { i to it } }.sortedByDescending { it.first.time }
    Sheet(onClose) {
        Text(stringResource(R.string.bu_prescriptions), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        if (requests.isEmpty()) Text(stringResource(R.string.bu_rx_empty), fontSize = 14.sp, lineHeight = 20.sp)
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            requests.take(30).forEach { (item, r) ->
                val (status, at) = Bussola.statusOf(item.eventId, items, studio)
                val shown = Bussola.prescription(status, at, now)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.drugs.joinToString(", "), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        when (shown) {
                            Prescription.Preparing -> Tag(stringResource(R.string.bu_step_preparing), BRASS, BRASS_BG)
                            Prescription.Valid -> Tag(stringResource(R.string.bu_valid), SAGE, SAGE_BG)
                            Prescription.Collected -> Tag(stringResource(R.string.bu_collected), scheme.onSurfaceVariant, scheme.onSurface.copy(alpha = 0.08f))
                            Prescription.Expired -> Tag(stringResource(R.string.bu_expired), BORDEAUX, BORDEAUX_BG)
                            Prescription.Review -> Tag(stringResource(R.string.bu_review), BRASS, BRASS_BG)
                        }
                    }
                    Text(stringResource(R.string.bu_asked_on, day(item.time)), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    val code = status.nre
                    if (shown == Prescription.Valid && code != null) {
                        Text(Bussola.nreGroups(code), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
                        if (at != null) Text(stringResource(R.string.bu_valid_until, day(Bussola.validUntil(at))), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(stringResource(R.string.bu_rx_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
    }
}

/** Orari e contatti (tavola BusOrari). */
@Composable
private fun InfoSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val items = model.bussolaItems(roomId)
    val info = Bussola.latestStudio(items, Bussola.studioSender(items))
    Sheet(onClose) {
        Text(info?.name?.ifBlank { null } ?: stringResource(R.string.bu_hours), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (info != null && info.absence.isNotBlank()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BRASS_BG).padding(12.dp)) {
                    Text(stringResource(R.string.bu_absent, info.absence), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    if (info.substitute.isNotBlank()) Text(stringResource(R.string.bu_substitute, info.substitute), fontSize = 13.5.sp, lineHeight = 19.sp)
                }
            }
            if (info != null && info.hours.isNotBlank()) Text(info.hours, fontSize = 15.sp, lineHeight = 22.sp)
            Text(stringResource(R.string.bu_reply_hours), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            NumberRow(stringResource(R.string.bu_118), "118", BORDEAUX, actions)
            if (info != null && info.guard.isNotBlank()) NumberRow(stringResource(R.string.bu_guard), info.guard, BRASS, actions)
            if (info != null && info.phone.isNotBlank()) NumberRow(stringResource(R.string.bu_studio_phone), info.phone, petrol(), actions)
            if (info != null && info.address.isNotBlank()) Text(info.address, fontSize = 13.5.sp, color = scheme.onSurfaceVariant)
            Text(stringResource(R.string.bu_owner), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumberRow(label: String, number: String, tint: Color, actions: ChatActions) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button) { actions.launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' }))) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EuriseIcons.phone(2.2f), null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Il codice NRE della ricetta fatta in Millewin (lato studio, tavola StuMillewin). Si compila da sé: dalla cartella dei
 * promemoria PDF (quello del paziente con i farmaci chiesti) o da un promemoria scelto a mano; si può sempre scrivere.
 */
@Composable
private fun NreSheet(model: ChatModel, actions: ChatActions, roomId: String, drugs: List<String>, onClose: () -> Unit, onSend: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    /** Da dove viene il codice: null = scritto a mano. */
    var source by remember { mutableStateOf<Int?>(null) }
    var warning by remember { mutableStateOf<Int?>(null) }
    var folder by remember { mutableStateOf(model.memoFolder) }
    val studio by StudioRepo.state.collectAsStateWithLifecycle()
    val peer = model.peerOf(roomId)
    val patient = studio.patients.firstOrNull { it.matrixId.equals(peer, ignoreCase = true) }
    val nre = Bussola.nre(text)
    fun take(memo: eu.eurise.core.studio.Millewin.Memo?, from: Int) {
        if (memo == null) { warning = R.string.bu_memo_unreadable; return }
        text = Bussola.nreGroups(memo.nre)
        source = from
        warning = when {
            patient == null -> null
            !eu.eurise.core.studio.Millewin.forPatient(memo, patient.fiscalCode, patient.name) -> R.string.bu_memo_other
            eu.eurise.core.studio.Millewin.drugsFound(memo, drugs) == 0 -> R.string.bu_memo_drugs
            else -> null
        }
    }
    // Con la cartella scelta, il promemoria giusto si cerca appena si apre il foglio.
    androidx.compose.runtime.LaunchedEffect(folder, patient?.id) {
        if (folder == null || patient == null) return@LaunchedEffect
        model.findMemo(patient.fiscalCode, patient.name, drugs) { memo -> if (memo != null && text.isBlank()) take(memo, R.string.bu_memo_from_folder) }
    }
    Sheet(onClose) {
        Text(stringResource(R.string.bu_nre_title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.bu_nre_sub), fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
        Field(stringResource(R.string.bu_nre), text, KeyboardType.Ascii) { text = it; source = null; warning = null }
        if (text.isNotBlank() && nre == null) Text(stringResource(R.string.bu_nre_bad), fontSize = 12.5.sp, color = scheme.error)
        source?.let { Text(stringResource(it), fontSize = 12.5.sp, color = SAGE, fontWeight = FontWeight.Bold) }
        warning?.let { Text(stringResource(it), fontSize = 12.5.sp, lineHeight = 17.sp, color = BORDEAUX) }
        if (patient != null && patient.fiscalCode == null) Text(stringResource(R.string.bu_memo_no_cf), fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.bu_memo_pick), selected = false) {
                actions.pickFile(arrayOf("application/pdf")) { uri -> uri?.let { model.readMemo(it) { m -> take(m, R.string.bu_memo_from_pdf) } } }
            }
            Chip(stringResource(if (folder == null) R.string.bu_memo_folder else R.string.bu_memo_folder_change), selected = false) {
                actions.pickFolder { uri -> if (uri != null) { model.memoFolder = uri; folder = uri } }
            }
        }
        Pill(stringResource(R.string.bu_nre_send), EuriseIcons.send(2f), Modifier.fillMaxWidth(), strong = nre != null) {
            if (nre == null) return@Pill
            onSend(nre)
            onClose()
        }
    }
}

/** Invito a un controllo, con le quattro risposte rapide per il paziente (lato studio). */
@Composable
private fun InviteSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val default = stringResource(R.string.bu_invite_default)
    var text by remember { mutableStateOf(default) }
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Text(stringResource(R.string.bu_invite), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Field(stringResource(R.string.bu_invite_text), text, KeyboardType.Text) { text = it }
        Text(stringResource(R.string.bu_invite_choices, Bussola.CHECKUP_CHOICES.joinToString(" · ")), fontSize = 12.sp, lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Pill(stringResource(R.string.ch_send), EuriseIcons.send(2f), Modifier.fillMaxWidth(), strong = text.isNotBlank()) {
            if (text.isBlank()) return@Pill
            model.sendBussola(roomId, Bussola.invite(Bussola.Invite(UUID.randomUUID().toString(), text.trim()))) { actions.toast(failed) }
            onClose()
        }
    }
}

/** Manda di nuovo orari e terapia abituale (dalla scheda del paziente nello Studio) in questa chat. */
@Composable
private fun UpdateSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val state by StudioRepo.state.collectAsStateWithLifecycle()
    val peer = model.peerOf(roomId)
    val patient = state.patients.firstOrNull { it.matrixId.equals(peer, ignoreCase = true) }
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Text(stringResource(R.string.bu_update), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        val info = state.info
        when {
            info == null -> Text(stringResource(R.string.bu_need_info), fontSize = 14.sp, lineHeight = 20.sp)
            patient == null -> Text(stringResource(R.string.bu_need_patient), fontSize = 14.sp, lineHeight = 20.sp)
            else -> {
                Text(stringResource(R.string.bu_update_sub, patient.therapies.size), fontSize = 14.sp, lineHeight = 20.sp)
                Pill(stringResource(R.string.ch_send), EuriseIcons.send(2f), Modifier.fillMaxWidth(), strong = true) {
                    model.bussolaActivate(roomId, info, patient.therapies.map { listOf(it.name, it.dose).filter { s -> s.isNotBlank() }.joinToString(" ") }) { actions.toast(failed) }
                    onClose()
                }
            }
        }
    }
}

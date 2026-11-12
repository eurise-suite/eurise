package eu.eurise.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.text.DateFormat
import java.util.Date

private val SALVIA = Color(0xFF4F6B5A)
private val OTTONE = Color(0xFF85672B)
private val RED = Color(0xFF8C2F39)

/** La conferma d'identità chiesta dal server, con cosa riprovare dopo. */
private class AuthAsk(val need: AuthNeeded, val retry: (session: String, password: String?) -> Unit)

/**
 * La chiave di recupero (tavola ChatRecupero): la si crea la prima volta, la si inserisce se c'è già (Element, telefono
 * vecchio, backup della suite), si sceglie dove tenerla. Lo stato della copia cifrata delle chiavi sul server in fondo.
 */
@Composable
internal fun RecoveryScreen(model: ChatModel, actions: ChatActions, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { model.refreshSecurity() }
    val status = model.security
    var key by remember { mutableStateOf(model.recoveryKey) }
    var shown by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var entering by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<Int?>(null) }
    var ask by remember { mutableStateOf<AuthAsk?>(null) }
    var suiteKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(status?.serverStorage) { if (status?.serverStorage == true && key == null) suiteKey = model.suiteKey() }

    fun run(text: String?, auth: org.json.JSONObject? = null) {
        busy = true; problem = null
        model.recovery(text, auth) { r ->
            busy = false
            r.onSuccess { key = it; entering = false; typed = ""; shown = text == null }
            r.onFailure { e ->
                when (e) {
                    is AuthNeeded -> ask = AuthAsk(e) { session, password ->
                        val a = org.json.JSONObject().put("session", session)
                        if (password != null) a.put("type", "m.login.password")
                            .put("identifier", org.json.JSONObject().put("type", "m.id.user").put("user", model.session?.userId.orEmpty()))
                            .put("password", password)
                        run(text, a)
                    }
                    is AlreadySetUp -> { entering = true; problem = R.string.ch_rec_exists }
                    is WrongKey -> problem = R.string.ch_rec_wrong
                    else -> problem = R.string.ch_rec_failed
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.ch_back), onClick = onClose)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.ch_rec_title), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(stringResource(R.string.ch_rec_intro), fontSize = 14.sp, lineHeight = 21.sp, color = scheme.onSurface.copy(alpha = 0.85f))
            if (status == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }

            val k = key
            if (k != null) {
                Card {
                    val groups = k.split(" ")
                    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.chunked(4).forEachIndexed { row, line ->
                            Row(Modifier.fillMaxWidth()) {
                                line.forEachIndexed { i, g ->
                                    val visible = shown || row * 4 + i < 3
                                    Text(if (visible) g else "••••", fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                        color = if (visible) scheme.onSurface else scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(stringResource(if (shown) R.string.ch_rec_hide else R.string.ch_rec_show), selected = false) { shown = !shown }
                            Chip(stringResource(R.string.ch_rec_copy), selected = false) { actions.copySecret(k); actions.toast(actions.text(R.string.ch_rec_copied)) }
                            Spacer(Modifier.weight(1f))
                            Text(stringResource(R.string.ch_rec_chars, k.count { !it.isWhitespace() }), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                SectionTitle(stringResource(R.string.ch_rec_where))
                Card {
                    KeepRow(EuriseIcons.shield(2.2f), stringResource(R.string.ch_rec_suite), stringResource(R.string.ch_rec_suite_sub)) {
                        Switch(checked = model.keptInSuite, onCheckedChange = { on ->
                            model.keepInSuite(on) { ok -> if (!ok && on) actions.toast(actions.text(R.string.ch_rec_suite_missing)) }
                        })
                    }
                    Divider()
                    KeepRow(EuriseIcons.key(2.2f), stringResource(R.string.ch_rec_password), stringResource(R.string.ch_rec_password_sub)) {
                        Chip(stringResource(R.string.ch_rec_password_open), selected = false) { actions.openPassword(k) }
                    }
                    Divider()
                    KeepRow(EuriseIcons.printer(2.2f), stringResource(R.string.ch_rec_paper), stringResource(R.string.ch_rec_paper_sub)) {
                        Chip(stringResource(R.string.ch_rec_print), selected = false) { actions.printKey(k, model.session?.userId.orEmpty()) }
                    }
                }
            } else if (status != null) {
                if (status.serverStorage || entering) {
                    Card {
                        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.ch_rec_enter), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.ch_rec_enter_sub), fontSize = 12.5.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
                            Field(stringResource(R.string.ch_rec_key), typed, KeyboardType.Ascii) { typed = it }
                            Pill(stringResource(R.string.ch_rec_open), EuriseIcons.key(2f), Modifier.fillMaxWidth(), strong = true) { if (!busy) run(typed) }
                            suiteKey?.let { sk ->
                                Pill(stringResource(R.string.ch_rec_from_suite), EuriseIcons.shield(2f), Modifier.fillMaxWidth(), strong = false) { if (!busy) run(sk) }
                            }
                        }
                    }
                } else {
                    Card {
                        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.ch_rec_create_title), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.ch_rec_create_sub), fontSize = 12.5.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
                            Pill(stringResource(R.string.ch_rec_create), EuriseIcons.key(2f), Modifier.fillMaxWidth(), strong = true) { if (!busy) run(null) }
                        }
                    }
                }
            }
            if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.ch_rec_working), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            }
            problem?.let { Text(stringResource(it), fontSize = 13.sp, lineHeight = 18.sp, color = RED) }

            status?.let { st ->
                Card {
                    KeepRow(EuriseIcons.cloud(2.2f), stringResource(R.string.ch_rec_backup),
                        if (st.backup) stringResource(R.string.ch_rec_backup_on, st.backedUp, st.keys) else stringResource(R.string.ch_rec_backup_off)) {
                        Text(stringResource(if (st.backup) R.string.ch_rec_active else R.string.ch_rec_inactive), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (st.backup) SALVIA else OTTONE)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
        if (key != null) Box(Modifier.navigationBarsPadding().padding(16.dp)) {
            Pill(stringResource(R.string.ch_rec_done), EuriseIcons.check(2f), Modifier.fillMaxWidth(), strong = true, onClick = onClose)
        }
    }
    ask?.let { a -> AuthSheet(model, actions, a.need, onDone = { session, password -> ask = null; a.retry(session, password) }, onClose = { ask = null }) }
}

@Composable
private fun KeepRow(icon: ImageVector, title: String, sub: String, end: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        end()
    }
}

/** Il server chiede chi sei: password, oppure conferma nel browser e poi «Fatto». */
@Composable
private fun AuthSheet(model: ChatModel, actions: ChatActions, need: AuthNeeded, onDone: (String, String?) -> Unit, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf(false) }
    Sheet(onClose) {
        Text(stringResource(R.string.ch_auth_title), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.ch_auth_sub), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (need.password) {
            Field(stringResource(R.string.ch_password), password, KeyboardType.Password, secret = true) { password = it }
            Pill(stringResource(R.string.ch_auth_go), EuriseIcons.check(2f), Modifier.fillMaxWidth(), strong = true) { if (password.isNotEmpty()) onDone(need.session, password) }
        } else if (need.sso) {
            if (!opened) Pill(stringResource(R.string.ch_auth_browser), EuriseIcons.lock(2f), Modifier.fillMaxWidth(), strong = true) {
                model.ssoConfirmUrl(need.session)?.let(actions::openBrowser); opened = true
            } else Pill(stringResource(R.string.ch_auth_done), EuriseIcons.check(2f), Modifier.fillMaxWidth(), strong = true) { onDone(need.session, null) }
        } else {
            Text(stringResource(R.string.ch_auth_unsupported), fontSize = 13.sp, color = RED)
        }
    }
}

/**
 * La verifica dei dispositivi (tavola ChatVerifica): le sette figure da confrontare con l'altro schermo, poi l'elenco
 * dei dispositivi dell'account con «Esci» per quelli che non usi più.
 */
@Composable
internal fun VerifyScreen(model: ChatModel, actions: ChatActions, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BackHandler(onBack = onClose)
    var devices by remember { mutableStateOf<List<ChatDevice>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var ask by remember { mutableStateOf<AuthAsk?>(null) }
    LaunchedEffect(reload) { model.devices { r -> devices = r.getOrNull() ?: devices.orEmpty() } }
    val v = model.verify
    LaunchedEffect(v is Verify.Done) { if (v is Verify.Done) reload++ }
    // Verifica di una persona (dalla chat): stesse figure, testi con il suo nome, niente elenco dei dispositivi.
    val person = remember { v?.user }
    val personName = person?.let(model::nameOf)
    LaunchedEffect(v == null) { if (person != null && v == null) onClose() }

    fun logout(id: String, session: String? = null, password: String? = null) {
        model.logoutDevice(id, session, password) { r ->
            r.onSuccess { reload++ }
            r.onFailure { e ->
                if (e is AuthNeeded) ask = AuthAsk(e) { s, p -> logout(id, s, p) } else actions.toast(actions.text(R.string.ch_dev_failed))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.ch_back), onClick = onClose)
            Spacer(Modifier.width(10.dp))
            Text(if (personName != null) stringResource(R.string.ch_ver_person_title, personName) else stringResource(R.string.ch_ver_title),
                fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
        }
        val other = personName ?: v?.device?.let { id -> devices?.firstOrNull { it.id == id }?.name ?: id }
        when (v) {
            null -> {
                Text(stringResource(R.string.ch_ver_intro), fontSize = 14.sp, lineHeight = 21.sp)
                Pill(stringResource(R.string.ch_ver_start), EuriseIcons.shield(2f), Modifier.fillMaxWidth(), strong = true) { model.startVerify() }
            }
            is Verify.Incoming -> {
                Text(if (personName != null) stringResource(R.string.ch_ver_person_incoming, personName)
                    else stringResource(R.string.ch_ver_incoming, other ?: stringResource(R.string.ch_ver_other)), fontSize = 14.sp, lineHeight = 21.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.ch_ver_refuse), EuriseIcons.close(2f), Modifier.weight(1f), strong = false) { model.cancelVerify(false) }
                    Pill(stringResource(R.string.ch_ver_accept), EuriseIcons.check(2f), Modifier.weight(1f), strong = true) { model.acceptVerify() }
                }
            }
            is Verify.Waiting, is Verify.Confirmed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(when {
                        personName != null && v is Verify.Confirmed -> stringResource(R.string.ch_ver_person_confirmed, personName)
                        personName != null -> stringResource(R.string.ch_ver_person_waiting, personName)
                        v is Verify.Confirmed -> stringResource(R.string.ch_ver_confirmed)
                        else -> stringResource(R.string.ch_ver_waiting)
                    }, fontSize = 14.sp, lineHeight = 20.sp)
                }
                Pill(stringResource(R.string.ch_cancel), EuriseIcons.close(2f), Modifier.fillMaxWidth(), strong = false) { model.cancelVerify(false) }
            }
            is Verify.Compare -> {
                Text(if (personName != null) stringResource(R.string.ch_ver_person_compare, personName)
                    else stringResource(R.string.ch_ver_compare, other ?: stringResource(R.string.ch_ver_other)), fontSize = 14.sp, lineHeight = 21.sp)
                Card {
                    Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        v.emojis.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { i ->
                                    val (e, name) = ChatSecurity.EMOJI.getOrElse(i) { "?" to "?" }
                                    Column(Modifier.weight(1f).semantics { contentDescription = name }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(e, fontSize = 34.sp)
                                        Text(name, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Text(stringResource(R.string.ch_ver_why), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.ch_ver_different), EuriseIcons.close(2f), Modifier.weight(1f), strong = false) { model.cancelVerify(true) }
                    Pill(stringResource(R.string.ch_ver_same), EuriseIcons.check(2f), Modifier.weight(1f), strong = true) { model.confirmVerify() }
                }
            }
            is Verify.Done -> {
                Text(if (personName != null) stringResource(R.string.ch_ver_person_done, personName) else stringResource(R.string.ch_ver_done), fontSize = 14.sp, lineHeight = 21.sp, color = SALVIA, fontWeight = FontWeight.Bold)
                Pill(stringResource(R.string.ch_rec_done), EuriseIcons.check(2f), Modifier.fillMaxWidth(), strong = true) { model.cancelVerify(false) }
            }
            is Verify.Cancelled -> {
                Text(stringResource(if (personName != null) R.string.ch_ver_person_cancelled else R.string.ch_ver_cancelled), fontSize = 14.sp, lineHeight = 21.sp, color = RED)
                if (person == null) Pill(stringResource(R.string.ch_ver_again), EuriseIcons.shield(2f), Modifier.fillMaxWidth(), strong = true) { model.cancelVerify(false); model.startVerify() }
                else Pill(stringResource(R.string.ch_rec_done), EuriseIcons.check(2f), Modifier.fillMaxWidth(), strong = true, onClick = onClose)
            }
        }
        if (person != null) {
            Spacer(Modifier.navigationBarsPadding().size(16.dp))
            return@Column
        }

        SectionTitle(stringResource(R.string.ch_dev_title))
        Card {
            val list = devices
            if (list == null) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp)) }
            list?.forEachIndexed { i, d ->
                if (i > 0) Divider()
                val seen = if (d.lastSeen > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(d.lastSeen)) else ""
                KeepRow(
                    if (d.current) EuriseIcons.phone(2.2f) else EuriseIcons.monitor(2.2f),
                    if (d.current) stringResource(R.string.ch_dev_this) else d.name,
                    listOf(if (d.current) d.name else "", seen).filter { it.isNotBlank() }.joinToString(" · "),
                ) {
                    when {
                        d.verified -> Text(stringResource(R.string.ch_dev_verified), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = SALVIA)
                        d.current -> Text(stringResource(R.string.ch_dev_unverified), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = OTTONE)
                        else -> Chip(stringResource(R.string.ch_dev_logout), selected = false) { logout(d.id) }
                    }
                }
            }
        }
        Text(stringResource(R.string.ch_dev_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.navigationBarsPadding().size(16.dp))
    }
    ask?.let { a -> AuthSheet(model, actions, a.need, onDone = { s, p -> ask = null; a.retry(s, p) }, onClose = { ask = null }) }
}

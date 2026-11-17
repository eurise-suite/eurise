package eu.eurise.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Le azioni che passano dall'attività. */
interface ChatActions {
    fun openBrowser(url: String)
    fun openFile(file: java.io.File)
    fun toast(text: String)
    fun text(id: Int): String
    fun askNotifications()
    fun finish()
    /** Copia un segreto (la chiave di recupero): segnato come sensibile, non compare nelle anteprime degli appunti. */
    fun copySecret(text: String)
    /** «In Eurise Password»: copia la chiave e apre Password per incollarla in una voce nuova. */
    fun openPassword(key: String, userId: String)
    /** «Su carta»: un foglio con la chiave da stampare. */
    fun printKey(key: String, userId: String)
    /** Apre un'altra app (Email, Messaggi) con [intent]; se non c'è, un avviso. */
    fun launch(intent: android.content.Intent)
    /** Il selettore di file di sistema (anche Eurise File) per i tipi [mimes]; null se si chiude senza scegliere. */
    fun pickFile(mimes: Array<String>, done: (android.net.Uri?) -> Unit)
    /** Una cartella da leggere anche dopo (i promemoria di Millewin): il permesso resta. */
    fun pickFolder(done: (android.net.Uri?) -> Unit)
    /** Il permesso del calendario, per il calendario «Turni» sul telefono; true se c'è. */
    fun askCalendar(done: (Boolean) -> Unit)
    /** Condivide un testo (riepilogo dei turni per chi non usa la Chat) con l'app che sceglie l'utente. */
    fun shareText(text: String)
    /** Condivide un file della cartella della Chat (PDF, CSV, calendario dei turni) con l'app che sceglie l'utente. */
    fun shareFile(file: java.io.File, mime: String)
}

/** Le schermate della sicurezza (tavole ChatRecupero e ChatVerifica). */
private enum class SecurityPage { Recovery, Verify }

private val VERIFIED = Color(0xFF4F6B5A)
private val TINTS = listOf(0xFF3E5C8A, 0xFF4F6B5A, 0xFF6B4A7A, 0xFF85672B, 0xFF2E6E73, 0xFFA0522D, 0xFF8C2F39, 0xFF55585E)
internal fun tintFor(key: String) = Color(TINTS[(key.hashCode() and 0x7fffffff) % TINTS.size])
internal fun initials(name: String): String {
    val w = name.split(Regex("[\\s._\\-@:]+")).filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
    return (if (w.size >= 2) "${w[0].first()}${w[1].first()}" else w.firstOrNull()?.take(2) ?: "?").uppercase()
}

@Composable
fun ChatApp(model: ChatModel, actions: ChatActions) {
    val open = model.openRoom
    var page by remember { mutableStateOf<SecurityPage?>(null) }
    BackHandler(enabled = open != null && page == null) { model.open(null) }
    // Un altro dispositivo chiede la verifica: si apre la schermata delle figure.
    LaunchedEffect(model.verify is Verify.Incoming) { if (model.verify is Verify.Incoming) page = SecurityPage.Verify }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            model.session == null -> Access(model, actions)
            page == SecurityPage.Recovery -> RecoveryScreen(model, actions, onClose = { page = null })
            page == SecurityPage.Verify -> VerifyScreen(model, actions, onClose = { if (model.verify !is Verify.Incoming) model.cancelVerify(false); page = null })
            open != null -> Conversation(model, actions, open, onVerify = { page = SecurityPage.Verify })
            // Il filo di una persona con Chat, SMS, email e chiamate (tavola UniConversazione).
            model.uni.who != null -> PersonScreen(model, actions)
            model.studioPage != null -> StudioScreen(model, actions)
            else -> RoomList(model, actions, onRecovery = { page = SecurityPage.Recovery }, onVerify = { page = SecurityPage.Verify })
        }
    }
}

// --- Accesso (tavola ChatAccesso) ---

@Composable
private fun Access(model: ChatModel, actions: ChatActions) {
    val scheme = MaterialTheme.colorScheme
    var custom by remember { mutableStateOf(false) }
    var domain by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<Int?>(null) }
    fun go(server: String) {
        if (working) return
        working = true
        problem = null
        model.begin(server) { step ->
            working = false
            when (step) {
                is LoginStep.Browser -> actions.openBrowser(step.url)
                is LoginStep.Password -> password = step.homeserver
                is LoginStep.Failed -> problem = step.reason
            }
        }
    }
    val other = model.otherSession
    val work = model.account == Account.Work
    var email by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Si sta aggiungendo il secondo account: si può tornare al primo.
        if (other != null) Row(
            Modifier.padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).background(scheme.surface)
                .clickable(role = Role.Button) { model.switchTo(model.account.other) }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.chevronLeft(2.4f), null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ch_acc_back, accountName(model.account.other)), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
        Text(stringResource(if (work) R.string.ch_acc_add_work else R.string.ch_access_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = if (other != null) 0.dp else 16.dp))
        Text(stringResource(if (work) R.string.ch_acc_add_work_intro else R.string.ch_access_intro), fontSize = 14.sp, lineHeight = 21.sp)
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        // Lavoro: con l'email di lavoro il server si trova dal dominio (nessun indirizzo tecnico da scrivere).
        if (work) Card {
            Text(stringResource(R.string.ch_acc_email), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Field(stringResource(R.string.ch_acc_email_hint), email, KeyboardType.Email) { email = it; problem = null }
            Box(Modifier.padding(bottom = 10.dp)) {
                Pill(stringResource(R.string.ch_link), EuriseIcons.link(2f), Modifier.fillMaxWidth(), strong = Quiet.domainOf(email) != null) {
                    val d = Quiet.domainOf(email) ?: run { problem = R.string.ch_acc_email_bad; return@Pill }
                    go(d)
                }
            }
        }
        Card {
            ServerRow(EuriseIcons.shield(2.2f), stringResource(R.string.ch_eurise), stringResource(R.string.ch_eurise_sub), null, strong = false) { }
            Divider()
            ServerRow(EuriseIcons.globe(2.2f), stringResource(R.string.ch_own), stringResource(R.string.ch_own_sub), stringResource(R.string.ch_link), strong = true) { custom = !custom }
            if (custom) {
                Field(stringResource(R.string.ch_own_hint), domain, KeyboardType.Uri) { domain = it }
                Box(Modifier.padding(bottom = 10.dp)) { Pill(stringResource(R.string.ch_link), EuriseIcons.link(2f), Modifier.fillMaxWidth(), strong = domain.isNotBlank()) { if (domain.isNotBlank()) go(domain) } }
            }
            Divider()
            ServerRow(EuriseIcons.person(2.2f), "matrix.org", stringResource(R.string.ch_matrixorg_sub), stringResource(R.string.ch_link), strong = false) { go("matrix.org") }
        }
        problem?.let { Text(stringResource(it), fontSize = 13.sp, color = scheme.error) }
        SectionTitle(stringResource(R.string.ch_who_sees))
        Card {
            SeesRow(EuriseIcons.lock(2.2f), stringResource(R.string.ch_sees_messages), stringResource(R.string.ch_sees_messages_sub), stringResource(R.string.ch_yours), Color(0xFF4F6B5A))
            Divider()
            SeesRow(EuriseIcons.eye(2.2f), stringResource(R.string.ch_sees_server), stringResource(R.string.ch_sees_server_sub), stringResource(R.string.ch_server), Color(0xFF85672B))
            Divider()
            SeesRow(EuriseIcons.person(2.2f), stringResource(R.string.ch_sees_contacts), stringResource(R.string.ch_sees_contacts_sub), stringResource(R.string.ch_yours), Color(0xFF4F6B5A))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x3385672B)).padding(14.dp)) {
            Text(stringResource(R.string.ch_whatsapp_note), fontSize = 12.5.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
    password?.let { hs -> PasswordSheet(model, actions, hs, onClose = { password = null }) }
}

@Composable
private fun PasswordSheet(model: ChatModel, actions: ChatActions, homeserver: String, onClose: () -> Unit) {
    var user by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Sheet(onClose) {
        Text(stringResource(R.string.ch_password_title, homeserver.removePrefix("https://")), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Field(stringResource(R.string.ch_user), user, KeyboardType.Text) { user = it }
        Field(stringResource(R.string.ch_password), pwd, KeyboardType.Password, secret = true) { pwd = it }
        if (failed) Text(stringResource(R.string.ch_err_password), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        Pill(stringResource(if (working) R.string.ch_working else R.string.ch_link), EuriseIcons.link(2f), Modifier.fillMaxWidth(), strong = !working) {
            if (working || user.isBlank() || pwd.isEmpty()) return@Pill
            working = true
            model.loginPassword(homeserver, user, pwd) { ok -> working = false; failed = !ok; if (ok) { actions.askNotifications(); onClose() } }
        }
    }
}

// --- Elenco (tavola ChatElenco) ---

@Composable
private fun RoomList(model: ChatModel, actions: ChatActions, onRecovery: () -> Unit, onVerify: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(false) }
    val studio by StudioRepo.state.collectAsStateWithLifecycle()
    val q = query.trim().lowercase()
    val shown = model.rooms.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.lastText.lowercase().contains(q) }
    if (model.uni.view != Uni.View.Chat) PeopleRefresher(model)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ch_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    // Con due account si vede sempre in quale si è.
                    if (model.otherSession != null) Text(accountName(model.account) + " · " + Matrix.localpart(model.session?.userId.orEmpty()),
                        fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = accountColor(model.account), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Lo Studio medico acceso: l'ingresso sta qui, con le urgenze segnate.
                if (studio.enabled) {
                    val urgent = studio.requests.count { it.open && it.need == eu.eurise.core.studio.Need.Urgent }
                    Box {
                        RoundIcon(EuriseIcons.medical(2.2f), stringResource(R.string.st_title)) { model.studioOpen() }
                        if (urgent > 0) Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(Color(0xFFA4502E)))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                RoundIcon(EuriseIcons.person(2.2f), stringResource(R.string.ch_account)) { account = true }
            }
            SearchField(query, stringResource(R.string.ch_search)) { query = it }
            // Persone (tutti i canali insieme), Solo Chat, Non lette (tavola UniPersone).
            ViewChips(model)
            // Senza chiave di recupero l'avviso invita a crearla (cambiando telefono i messaggi cifrati non si riaprirebbero).
            val safe = model.security?.backup == true
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (safe) Color(0x334F6B5A) else Color(0x3385672B))
                    .clickable(role = Role.Button, onClick = onRecovery).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (safe) EuriseIcons.lock(2f) else EuriseIcons.key(2f), null, tint = if (safe) Color(0xFF4F6B5A) else Color(0xFF85672B), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (safe) R.string.ch_e2ee_safe else R.string.ch_e2ee_key), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }
        if (!model.ready) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        model.error?.let { Text(it, fontSize = 12.5.sp, color = scheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
        Box(Modifier.weight(1f)) {
            if (model.ready && shown.isEmpty() && model.uni.view == Uni.View.Chat) Text(stringResource(R.string.ch_empty), fontSize = 14.sp, lineHeight = 20.sp,
                color = scheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (model.uni.view == Uni.View.Chat) items(shown, key = { it.id }) { r -> RoomRow(model, r) }
                else peopleItems(model, query, unreadOnly = model.uni.view == Uni.View.Unread)
                item { Spacer(Modifier.height(100.dp)) }
            }
            if (model.otherSession != null) AccountBar(model, Modifier.align(Alignment.BottomCenter)) { creating = true }
            else Row(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).height(56.dp).clip(RoundedCornerShape(28.dp))
                    .background(scheme.primary).clickable(role = Role.Button) { creating = true }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.plus(2.4f), null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ch_new), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
            }
        }
    }
    if (creating) NewChatSheet(model, actions, onClose = { creating = false })
    if (account) Sheet({ account = false }) {
        AccountsPanel(model) { account = false }
        Pill(stringResource(R.string.ch_rec_title), EuriseIcons.key(2f), Modifier.fillMaxWidth(), strong = false) { account = false; onRecovery() }
        Pill(stringResource(R.string.ch_ver_menu), EuriseIcons.shield(2f), Modifier.fillMaxWidth(), strong = false) { account = false; onVerify() }
        Pill(stringResource(R.string.st_title), EuriseIcons.medical(2f), Modifier.fillMaxWidth(), strong = false) { account = false; model.studioOpen() }
        Pill(stringResource(R.string.ch_logout), EuriseIcons.close(2f), Modifier.fillMaxWidth(), strong = false) { account = false; model.logout() }
    }
}

// --- Due account (tavole ChatDueAccount e ChatAccountAggiungi) ---

@Composable
private fun accountName(a: Account) = stringResource(if (a == Account.Work) R.string.ch_acc_work else R.string.ch_acc_personal)

/** Personale in blu, Lavoro in ottone, come nella tavola. */
private fun accountColor(a: Account) = if (a == Account.Work) Color(0xFF85672B) else Color(0xFF3E5C8A)

/**
 * La barra in basso con due account: Personale e Lavoro, con le non lette dell'altro; accanto, sotto il pollice, la
 * nuova chat dell'account che si sta guardando.
 */
@Composable
private fun AccountBar(model: ChatModel, modifier: Modifier, onNew: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val mine = model.rooms.sumOf { it.unread }
    Row(modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(scheme.surface.copy(alpha = 0.92f)).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Account.entries.forEach { a ->
                val on = a == model.account
                val count = if (on) mine else model.otherUnread ?: 0
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(23.dp))
                        .background(if (on) accountColor(a).copy(alpha = 0.18f) else Color.Transparent)
                        .clickable(role = Role.Tab) { model.switchTo(a) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(accountName(a), fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = accountColor(a))
                    if (count > 0) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).clip(RoundedCornerShape(9.dp)).background(accountColor(a)).padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center) { Text("$count", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        val label = stringResource(R.string.ch_new)
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(scheme.primary).clickable(role = Role.Button, onClick = onNew).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.plus(2.4f), null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp)) }
    }
}

/** I tuoi account: server, tacchette di privacy, fuori orario per il lavoro, e l'aggiunta del secondo. */
@Composable
private fun AccountsPanel(model: ChatModel, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(stringResource(R.string.ch_acc_title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
    Account.entries.forEach { a ->
        val s = if (a == model.account) model.session else model.otherSession
        if (s != null) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface)
                .clickable(enabled = a != model.account, role = Role.Button) { onClose(); model.switchTo(a) }.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(accountColor(a).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text(accountName(a).take(1), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = accountColor(a))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(accountName(a) + if (a == model.account) " · " + stringResource(R.string.ch_acc_here) else "", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(s.userId, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Ticks(Quiet.privacyOf(s.homeserver))
            }
            Text(stringResource(if (Quiet.privacyOf(s.homeserver) <= 5) R.string.ch_acc_public else R.string.ch_acc_private), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
            if (a == Account.Work) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ch_acc_quiet), fontSize = 13.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = model.workQuiet, onCheckedChange = model::changeWorkQuiet)
            }
        }
    }
    if (model.otherSession == null) {
        val missing = model.account.other
        Pill(stringResource(if (missing == Account.Work) R.string.ch_acc_add_work_btn else R.string.ch_acc_add_personal_btn), EuriseIcons.plus(2f), Modifier.fillMaxWidth(), strong = true) {
            onClose(); model.switchTo(missing)
        }
        Text(stringResource(R.string.ch_acc_two), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
    }
}

/** Le 10 tacchette di privacy a semaforo (regola della suite). */
@Composable
internal fun Ticks(n: Int) {
    val on = when { n >= 8 -> Color(0xFF4F8A5B); n >= 5 -> Color(0xFFC9A227); else -> Color(0xFFA4502E) }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.semantics { contentDescription = "$n/10" }) {
        repeat(10) { i -> Box(Modifier.width(4.dp).height(12.dp).clip(RoundedCornerShape(1.dp)).background(if (i < n) on else Color(0xFFDADAD3))) }
    }
}

@Composable
internal fun RoomRow(model: ChatModel, r: Room) {
    val scheme = MaterialTheme.colorScheme
    val tint = tintFor(r.id)
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(enabled = !r.invite) { model.open(r.id) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(initials(r.name), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, fontSize = 15.5.sp, fontWeight = if (r.unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (r.lastTime > 0) Text(shortTime(r.lastTime), fontSize = 12.sp, color = if (r.unread > 0) scheme.primary else scheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (r.encrypted) { Icon(EuriseIcons.lock(2.4f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(if (r.invite) stringResource(R.string.ch_invite) else r.lastText, fontSize = 13.sp, color = scheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (r.unread > 0) {
                        Box(Modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).clip(RoundedCornerShape(10.dp)).background(scheme.primary).padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center) { Text("${r.unread}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimary) }
                    }
                }
            }
        }
        if (r.invite) Row(Modifier.padding(start = 58.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.ch_accept), selected = true) { model.accept(r.id) }
            Chip(stringResource(R.string.ch_decline), selected = false) { model.decline(r.id) }
        }
        HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
    }
}

internal fun shortTime(t: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val now = Calendar.getInstance()
    return if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR))
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t)) else DateFormat.getDateInstance(DateFormat.SHORT).format(Date(t))
}

@Composable
private fun NewChatSheet(model: ChatModel, actions: ChatActions, onClose: () -> Unit) {
    var group by remember { mutableStateOf(false) }
    var who by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<Int?>(null) }
    var working by remember { mutableStateOf(false) }
    Sheet(onClose) {
        Text(stringResource(R.string.ch_new), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.ch_new_direct), selected = !group) { group = false }
            Chip(stringResource(R.string.ch_new_group), selected = group) { group = true }
        }
        if (group) Field(stringResource(R.string.ch_group_name), name, KeyboardType.Text) { name = it }
        Field(stringResource(if (group) R.string.ch_who_many else R.string.ch_who), who, KeyboardType.Email) { who = it; problem = null }
        Text(stringResource(R.string.ch_new_note), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        problem?.let { Text(stringResource(it), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
        Pill(stringResource(if (working) R.string.ch_working else R.string.ch_create), EuriseIcons.chat(2f), Modifier.fillMaxWidth(), strong = !working) {
            if (working) return@Pill
            working = true
            val done: (String?, Int?) -> Unit = { room, err ->
                working = false
                if (room != null) { onClose(); model.open(room) } else problem = err
            }
            if (group) model.newGroup(name, who.split(',', ' ', ';').filter { it.isNotBlank() }, done) else model.newChat(who, done)
        }
    }
}

// --- Conversazione (tavola ChatConversazione) ---

@Composable
private fun Conversation(model: ChatModel, actions: ChatActions, roomId: String, onVerify: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val room = model.room(roomId)
    val me = model.session?.userId.orEmpty()
    val events = model.timeline(roomId)
    val list = rememberLazyListState()
    var draft by remember(roomId) { mutableStateOf("") }
    val failed = stringResource(R.string.ch_send_failed)
    val fileFailed = stringResource(R.string.ch_file_failed)
    val attachLabel = stringResource(R.string.ch_attach)
    LaunchedEffect(events.size) { if (events.isNotEmpty()) list.animateScrollToItem(0); model.open(roomId) }
    LaunchedEffect(roomId) { model.checkPeer(roomId) }
    val peer = model.peerOf(roomId)
    val peerVerified = peer != null && model.verifiedPeople[peer] == true
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= events.size - 2) model.older(roomId)
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().windowInsetsPadding(WindowInsets.ime)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.ch_back), flat = true) { model.open(null) }
            val name = room?.name ?: ""
            Box(Modifier.size(38.dp).clip(CircleShape).background(tintFor(roomId).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(initials(name), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tintFor(roomId))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(when {
                    room?.encrypted != true -> R.string.ch_room_plain
                    peerVerified -> R.string.ch_room_verified
                    else -> R.string.ch_room_encrypted
                }), fontSize = 11.5.sp, color = if (peerVerified) VERIFIED else scheme.onSurfaceVariant, maxLines = 1)
            }
            // Studio medico: dalla chat a due, la scheda del paziente (o «Aggiungi allo Studio»).
            val studio by StudioRepo.state.collectAsStateWithLifecycle()
            if (studio.enabled && peer != null) {
                val patient = studio.patients.firstOrNull { it.matrixId.equals(peer, ignoreCase = true) }
                RoundIcon(EuriseIcons.medical(2.2f), stringResource(if (patient != null) R.string.st_card else R.string.st_add_to_studio), flat = true) {
                    model.studioOpen(patient?.id)
                    if (patient == null) model.studioGo(StudioPage.Edit(null, matrixId = peer, name = room?.name))
                }
            }
            // Verifica della persona (tavola ChatVerifica): solo nelle chat a due cifrate.
            if (peer != null && room?.encrypted == true) {
                RoundIcon(EuriseIcons.shield(2.2f), stringResource(if (peerVerified) R.string.ch_ver_person_again else R.string.ch_ver_person), flat = true) {
                    model.startVerifyUser(roomId) { actions.toast(actions.text(it)) }
                    onVerify()
                }
            }
        }
        // La mia Bussola: promemoria del 118 e azioni grandi per il paziente; per lo studio, invito e aggiornamento.
        val bussola = model.bussolaRole(roomId)
        if (bussola != null) BussolaBar(model, actions, roomId, bussola)
        // Nei gruppi: Messaggi e Spese (tavola SpeGruppo).
        val group = room != null && !room.direct && peer == null
        var tab by remember(roomId) { mutableStateOf(0) }
        if (group) Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.ex_tab_messages), selected = tab == 0) { tab = 0 }
            Chip(stringResource(R.string.ex_tab_expenses), selected = tab == 1) { tab = 1 }
            // Turni del gruppo (tavola ChatTurni): la settimana sempre a portata, chi è in turno ora, scambi controllati.
            Chip(stringResource(R.string.sh_tab), selected = tab == 2) { tab = 2 }
        }
        if (group && tab == 1) {
            Box(Modifier.weight(1f).fillMaxWidth()) { ExpensesPane(model, actions, roomId) }
        } else if (group && tab == 2) {
            Box(Modifier.weight(1f).fillMaxWidth()) { ShiftsPane(model, actions, roomId) }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, reverseLayout = true) {
                items(events.asReversed(), key = { it.id }) { e ->
                    if (e.kind == Event.Kind.Bussola) BussolaBubble(model, actions, roomId, e, mine = e.sender == me)
                    else if (e.kind == Event.Kind.Shift) ShiftBubble(model, actions, roomId, e, mine = e.sender == me)
                    else Bubble(model, e, mine = e.sender == me) { model.media(e) { f -> if (f != null) actions.openFile(f) else actions.toast(fileFailed) } }
                }
            }
            if (bussola == ChatModel.BussolaRole.Patient) UrgentHint()
            // La graffetta (tavola ChatAllega): un file qualsiasi, cifrato se la chat lo è; nelle chat di La mia Bussola
            // apre «Invia un referto», con le sue regole (tavola BusReferto).
            var reportSheet by remember(roomId) { mutableStateOf(false) }
            if (reportSheet && bussola != null) ReportSheet(model, actions, roomId, bussola) { reportSheet = false }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(scheme.surface).clickable(role = Role.Button) {
                        if (bussola != null) { reportSheet = true; return@clickable }
                        actions.pickFile(arrayOf("*/*")) { uri ->
                            val p = uri?.let(model::describe) ?: return@pickFile
                            model.sendFile(roomId, p, null) { actions.toast(actions.text(it)) }
                        }
                    }.semantics { contentDescription = attachLabel },
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.attach(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp)).background(scheme.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (draft.isEmpty()) Text(stringResource(R.string.ch_write), fontSize = 15.sp, color = scheme.onSurfaceVariant)
                    BasicTextField(value = draft, onValueChange = { draft = it }, maxLines = 6,
                        textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                        modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(8.dp))
                val send = stringResource(R.string.ch_send)
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(if (draft.isBlank()) scheme.primary.copy(alpha = 0.4f) else scheme.primary)
                        .clickable(enabled = draft.isNotBlank(), role = Role.Button) {
                            val text = draft.trim()
                            draft = ""
                            model.send(roomId, text) { actions.toast(failed) }
                        }.semantics { contentDescription = send },
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.send(2.2f), null, tint = scheme.onPrimary, modifier = Modifier.size(19.dp)) }
            }
        }
    }
}

@Composable
private fun Bubble(model: ChatModel, e: Event, mine: Boolean, onMedia: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (e.kind == Event.Kind.Expense || e.kind == Event.Kind.Settlement) { ExpenseBubble(model, e, mine); return }
    if (e.kind == Event.Kind.State) {
        val (what, who) = e.text.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val label = when (what) {
            "join" -> stringResource(R.string.ch_state_join, who)
            "leave" -> stringResource(R.string.ch_state_leave, who)
            "verify" -> stringResource(R.string.ch_state_verify, who)
            else -> stringResource(R.string.ch_state_invite, who)
        }
        Text(label, fontSize = 11.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        return
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                .background(if (mine) scheme.primary else scheme.surface)
                .clickable(enabled = e.mediaUrl != null, onClick = onMedia)
                .padding(horizontal = 13.dp, vertical = 8.dp),
        ) {
            val fg = if (mine) scheme.onPrimary else scheme.onSurface
            if (!mine && !model.room(model.openRoom ?: "").let { it?.direct ?: false }) {
                Text(model.nameOf(e.sender), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tintFor(e.sender))
            }
            when (e.kind) {
                Event.Kind.Encrypted -> Text(stringResource(R.string.ch_encrypted_long), fontSize = 13.5.sp, fontStyle = FontStyle.Italic, color = fg.copy(alpha = 0.75f))
                Event.Kind.Image -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.image(2f), null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ch_photo_open), fontSize = 14.sp, color = fg)
                }
                Event.Kind.File -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.attach(2f), null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(e.text, fontSize = 14.sp, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                else -> Text(e.text, fontSize = 15.sp, lineHeight = 21.sp, color = fg,
                    fontStyle = if (e.kind == Event.Kind.Notice) FontStyle.Italic else FontStyle.Normal)
            }
            Text(if (e.id.startsWith("local:")) "…" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.time)),
                fontSize = 10.5.sp, color = fg.copy(alpha = 0.65f), modifier = Modifier.align(Alignment.End))
        }
    }
}

// --- Pezzi comuni ---

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp), content = content)
}

@Composable
internal fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))

@Composable
internal fun SectionTitle(text: String) {
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@Composable
private fun ServerRow(icon: ImageVector, title: String, sub: String, button: String?, strong: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = if (button == null) scheme.onSurfaceVariant else scheme.onSurface)
            Text(sub, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        if (button != null) Chip(button, selected = strong, onClick = onClick)
    }
}

@Composable
private fun SeesRow(icon: ImageVector, title: String, sub: String, who: String, color: Color) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(who, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
internal fun IconBox(icon: ImageVector) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
    }
}

@Composable
internal fun Sheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).pointerInput(Unit) { detectTapGestures { onClose() } }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) { detectTapGestures { } }.navigationBarsPadding().windowInsetsPadding(WindowInsets.ime).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
        )
    }
}

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) scheme.primary else scheme.primary.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (selected) scheme.onPrimary else scheme.primary, maxLines = 1) }
}

@Composable
internal fun Pill(label: String, icon: ImageVector, modifier: Modifier, strong: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (strong) scheme.primary else scheme.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (strong) scheme.onPrimary else scheme.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = if (strong) scheme.onPrimary else scheme.onSurface)
    }
}

@Composable
internal fun RoundIcon(icon: ImageVector, label: String, flat: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (flat) Color.Transparent else scheme.surface)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(18.dp)) }
}

@Composable
internal fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(scheme.surface).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(EuriseIcons.search(2.3f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, fontSize = 14.5.sp, color = scheme.onSurfaceVariant, maxLines = 1)
            BasicTextField(value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(fontSize = 14.5.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun Field(label: String, value: String, type: KeyboardType, secret: Boolean = false, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp)).background(scheme.background).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        BasicTextField(value = value, onValueChange = onChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
    }
}

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package eu.eurise.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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
    /** Il permesso del microfono per i vocali (tavola ChatVocali), chiesto solo al primo vocale. */
    fun askMic(done: (Boolean) -> Unit)
    /** Il permesso della posizione (tavola ChatPosizione), chiesto solo alla prima condivisione. */
    fun askLocation(done: (Boolean) -> Unit)
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
            // Invito sulla Chat (tavole ChatInvita e ChatInvitaElenco).
            model.uni.invite != null -> InviteScreen(model, actions)
            model.uni.inviteList -> InviteListScreen(model, actions)
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
    // Modalità semplice (tavola ChatSemplice): poche persone grandi, un vocale, Aiuto.
    if (model.simple.on) { SimpleHome(model, actions); return }
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(false) }
    val studio by StudioRepo.state.collectAsStateWithLifecycle()
    val q = query.trim().lowercase()
    // Cartelle (tavola ChatCartelle): la scelta filtra l'elenco; «Messaggi a me» sta in cima e non si ripete.
    // Gruppi a tempo scaduti (tavola ChatGruppoTempo): solo nell'archivio.
    val archived = model.archived()
    val inFolder = if (model.folder == ARCHIVE) model.rooms.filter { it.id in archived }
        else Cartelle.shown(model.rooms, model.folders.firstOrNull { it.id == model.folder }, model.selfRoom).filter { it.id !in archived }
    val shown = inFolder.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.lastText.lowercase().contains(q) }
    var foldersSheet by remember { mutableStateOf(false) }
    var editFolder by remember { mutableStateOf<Pair<String?, Set<String>>?>(null) }
    var roomFolders by remember { mutableStateOf<Room?>(null) }
    var simpleSetup by remember { mutableStateOf(false) }
    var ballsSheet by remember { mutableStateOf(false) }
    var approvalsSheet by remember { mutableStateOf(false) }
    var givenSheet by remember { mutableStateOf(false) }
    var rolesSheet by remember { mutableStateOf(false) }
    var offSheet by remember { mutableStateOf(false) }
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
                RoundIcon(EuriseIcons.folder(2.2f), stringResource(R.string.cr_title)) { foldersSheet = true }
                Spacer(Modifier.width(8.dp))
                RoundIcon(EuriseIcons.person(2.2f), stringResource(R.string.ch_account)) { account = true }
            }
            SearchField(query, stringResource(R.string.ch_search)) { query = it }
            // Persone (tutti i canali insieme), Solo Chat, Non lette (tavola UniPersone).
            ViewChips(model)
            if (model.uni.view == Uni.View.Chat) FolderChips(model, archived.size) { editFolder = null to emptySet() }
            // Una spesa arrivata dall'Email (tavola SpeEmail): si sceglie il gruppo.
            (model.pendingDeadline?.first ?: model.pendingAsk?.first)?.let { what ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.se_pick_any, what), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    Text("✕", fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button) { model.pendingDeadline = null; model.pendingAsk = null }.padding(4.dp))
                }
            }
            model.pendingExpense?.let { (cents, what) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.euro(2f), null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.se_pick_group, Expenses.money(cents), what.ifBlank { "—" }), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    Text("✕", fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button) { model.pendingExpense = null }.padding(4.dp))
                }
            }
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
                if (model.uni.view == Uni.View.Chat && model.folder == null && q.isEmpty() && model.ready) item(key = "self") { SelfRow(model, actions) }
                if (model.uni.view == Uni.View.Chat) items(shown, key = { it.id }) { r -> RoomRow(model, r) { roomFolders = r } }
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
    if (simpleSetup) SimpleSetupSheet(model) { simpleSetup = false }
    if (ballsSheet) BallsSheet(model) { ballsSheet = false }
    if (approvalsSheet) ApprovalsSheet(model, actions) { approvalsSheet = false }
    if (givenSheet) GivenSheet(model, actions) { givenSheet = false }
    if (rolesSheet) RolesSheet(model, actions) { rolesSheet = false }
    if (offSheet) OffHoursSheet(model) { offSheet = false }
    if (foldersSheet) FoldersSheet(model, onEdit = { id -> foldersSheet = false; editFolder = id to emptySet() }) { foldersSheet = false }
    editFolder?.let { (id, preset) -> FolderEditSheet(model, actions, id, preset) { editFolder = null } }
    roomFolders?.let { r -> RoomFoldersSheet(model, r, onNew = { roomFolders = null; editFolder = null to setOf(r.id) }) { roomFolders = null } }
    if (account) Sheet({ account = false }) {
        AccountsPanel(model, onOffHours = { account = false; offSheet = true }) { account = false }
        Pill(stringResource(R.string.sm_menu), EuriseIcons.heart(2f), Modifier.fillMaxWidth(), strong = false) { account = false; simpleSetup = true }
        Pill(stringResource(R.string.pa_list) + if (model.balls.isNotEmpty()) " · ${model.balls.size}" else "", EuriseIcons.ball(2f), Modifier.fillMaxWidth(), strong = false) {
            account = false; ballsSheet = true
        }
        Pill(stringResource(R.string.ap_list) + if (model.approvals.isNotEmpty()) " · ${model.approvals.size}" else "", EuriseIcons.check(2.4f), Modifier.fillMaxWidth(), strong = false) {
            account = false; approvalsSheet = true
        }
        Pill(stringResource(R.string.af_list) + if (model.given.isNotEmpty()) " · ${model.given.size}" else "", EuriseIcons.task(2f), Modifier.fillMaxWidth(), strong = false) {
            account = false; givenSheet = true
        }
        Pill(stringResource(eu.eurise.core.insieme.R.string.ins_roles_title), EuriseIcons.person(2f), Modifier.fillMaxWidth(), strong = false) {
            account = false; rolesSheet = true
        }
        Pill(stringResource(R.string.ch_rec_title), EuriseIcons.key(2f), Modifier.fillMaxWidth(), strong = false) { account = false; onRecovery() }
        Pill(stringResource(R.string.ch_ver_menu), EuriseIcons.shield(2f), Modifier.fillMaxWidth(), strong = false) { account = false; onVerify() }
        Pill(stringResource(R.string.inv_menu), EuriseIcons.send(2f), Modifier.fillMaxWidth(), strong = false) { account = false; model.uni.openInviteList(true) }
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
private fun AccountsPanel(model: ChatModel, onOffHours: () -> Unit = {}, onClose: () -> Unit) {
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
            // Fuori orario (tavola SpaFuoriOrario): l'orario e chi passa lo stesso.
            if (a == Account.Work) Text(stringResource(R.string.fo_line, Orari.label(model.offRules.hours),
                model.offRules.people.size + model.offRules.rooms.size + model.offRules.words.size), fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                color = scheme.primary, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onOffHours).padding(vertical = 4.dp))
        }
    }
    // Per chi vede poco (tavola ChatVocali): la voce del telefono legge i messaggi che arrivano. Nulla esce dal telefono.
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.volume(2f), null, tint = scheme.onSurface, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.vo_auto_title), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = model.voice.autoRead, onCheckedChange = { model.voice.changeAutoRead(it); model.readHintDone() })
        }
        Text(stringResource(R.string.vo_auto_sub), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
    }
    // Il tuo orario di lavoro alle persone con cui scrivi (tavola ChatQuandoLavora): solo l'account di lavoro, spento di base.
    if (model.account == Account.Work) Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.work(2f), null, tint = scheme.onSurface, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.ql_share), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = model.shareHours, onCheckedChange = { model.changeShareHours(it) })
        }
        Text(stringResource(R.string.ql_share_sub), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
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
internal fun RoomRow(model: ChatModel, r: Room, onLong: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val tint = tintFor(r.id)
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp)
                .combinedClickable(enabled = !r.invite, onLongClick = onLong) { model.open(r.id) }.padding(vertical = 8.dp),
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
                // Le discussioni a filo della chat (tavola ChatCartelle e ChatThread).
                if (!r.invite) RoomThreadsLine(model, r.id)
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
    // Discussioni a filo (tavola ChatThread): nella conversazione del gruppo non ci sono le risposte delle discussioni;
    // con una discussione aperta ci sono solo quelle (il messaggio di partenza sta in cima).
    val thread = model.thread
    val all = model.timeline(roomId)
    val events by remember(roomId, thread) { derivedStateOf { all.filter { it.thread == thread && it.kind != Event.Kind.Beacon && !(it.kind == Event.Kind.Notice && (it.data?.contains(Uscita.ANSWER) == true || it.data?.contains(eu.eurise.core.insieme.Scheda.ANSWER) == true)) } } }
    val threadRoot = thread?.let { id -> all.firstOrNull { it.id == id } }
    // Proposte (tavola ChatProposta) sotto gli ultimi messaggi ricevuti; riassunto (tavola ChatRiassunto) se entrando
    // c'erano tanti messaggi da leggere.
    val recentIds = remember(events.size) { events.takeLast(6).map { it.id }.toSet() }
    val unreadAtOpen = remember(roomId) { model.room(roomId)?.unread ?: 0 }
    var summaryClosed by remember(roomId) { mutableStateOf(false) }
    val threads by remember(roomId) { derivedStateOf { Threads.summary(all).associateBy { it.root } } }
    LaunchedEffect(roomId, thread) { thread?.let { model.loadThread(roomId, it) } }
    BackHandler(enabled = thread != null) { model.thread = null }
    val list = androidx.compose.runtime.key(thread) { rememberLazyListState() }
    var draft by remember(roomId) { mutableStateOf("") }
    // Le basi (tavola ChatBasi): il messaggio tenuto premuto, a cui si risponde, che si modifica o si inoltra.
    var menuFor by remember(roomId) { mutableStateOf<Event?>(null) }
    var replyTo by remember(roomId) { mutableStateOf<Event?>(null) }
    var editing by remember(roomId) { mutableStateOf<Event?>(null) }
    var approvalFile by remember(roomId) { mutableStateOf<String?>(null) }
    var outingSheet by remember(roomId) { mutableStateOf(false) }
    var forwarding by remember(roomId) { mutableStateOf<Event?>(null) }
    var pollSheet by remember(roomId) { mutableStateOf(false) }
    var moreSheet by remember(roomId) { mutableStateOf(false) }
    var timeboxSheet by remember(roomId) { mutableStateOf(false) }
    var ballFor by remember(roomId) { mutableStateOf<Event?>(null) }
    var entrustFrom by remember(roomId) { mutableStateOf<String?>(null) }
    var slotsSheet by remember(roomId) { mutableStateOf(false) }
    var shareSheet by remember(roomId) { mutableStateOf(false) }
    var agendaSheet by remember(roomId) { mutableStateOf(false) }
    var liveSheet by remember(roomId) { mutableStateOf(false) }
    var laterSheet by remember(roomId) { mutableStateOf<String?>(null) }
    var plannedMenu by remember(roomId) { mutableStateOf<Programma.Planned?>(null) }
    var recording by remember(roomId) { mutableStateOf<VoiceRecorder?>(null) }
    var cancelling by remember(roomId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.ch_send_failed)
    val fileFailed = stringResource(R.string.ch_file_failed)
    val attachLabel = stringResource(R.string.ch_attach)
    val pollLabel = stringResource(R.string.ba_poll_new)
    LaunchedEffect(events.size) { if (events.isNotEmpty()) list.animateScrollToItem(0); model.open(roomId) }
    LaunchedEffect(roomId) { model.checkPeer(roomId) }
    // Per chi vede poco: i messaggi che arrivano mentre la conversazione è aperta si leggono da soli (non la storia).
    var readFrom by remember(roomId) { mutableStateOf(events.lastOrNull()?.time ?: 0L) }
    LaunchedEffect(events.size, model.voice.autoRead) {
        if (!model.voice.autoRead) { readFrom = events.lastOrNull()?.time ?: 0L; return@LaunchedEffect }
        events.filter { it.time > readFrom && it.sender != me && (it.kind == Event.Kind.Text || it.kind == Event.Kind.Notice) }.forEach { e ->
            model.voice.speak(e.id, Voice.spoken(model.nameOf(e.sender), shownText(model, e)), queue = true)
        }
        readFrom = maxOf(readFrom, events.lastOrNull()?.time ?: 0L)
    }
    val peer = model.peerOf(roomId)
    val peerVerified = peer != null && model.verifiedPeople[peer] == true
    // Arriva quando l'altro lavora (tavola ChatQuandoLavora): fuori dal suo orario il messaggio aspetta, salvo «Manda subito».
    val peerHours = if (peer != null) model.peerHours(roomId) else null
    var minute by remember { mutableStateOf(System.currentTimeMillis() / 60_000) }
    LaunchedEffect(roomId) { while (true) { kotlinx.coroutines.delay(30_000); minute = System.currentTimeMillis() / 60_000 } }
    val peerStart = remember(peerHours, minute) { peerHours?.let { Orari.nextStart(it, java.time.ZonedDateTime.now()) } }
    var urgent by remember(roomId) { mutableStateOf(false) }
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (thread == null && last != null && last >= events.size - 2) model.older(roomId)
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().windowInsetsPadding(WindowInsets.ime)) {
        if (thread != null) ThreadHeader(model, room?.name.orEmpty(), threadRoot, threads[thread]?.count ?: events.size)
        else Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.ch_back), flat = true) { model.open(null) }
            val name = room?.name ?: ""
            Box(Modifier.size(38.dp).clip(CircleShape).background(tintFor(roomId).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(initials(name), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tintFor(roomId))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((peerStart?.let { stringResource(R.string.ql_off_until, Programma.time(it.withZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalTime())) + " · " } ?: "") +
                    stringResource(when {
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
        if (bussola != null && thread == null) BussolaBar(model, actions, roomId, bussola)
        // L'agenda con l'assistente (tavola InsAssistente), nelle chat a due.
        if (bussola == null && thread == null && peer != null) AgendaBar(model, actions, roomId)
        // Nei gruppi: Messaggi e Spese (tavola SpeGruppo).
        val group = room != null && !room.direct && peer == null && thread == null
        // Una spesa dall'Email (tavola SpeEmail): il gruppo si apre sulle Spese, con «Nuova spesa» già compilata.
        var tab by remember(roomId) { mutableStateOf(if (group && model.pendingExpense != null) 1 else if (group && model.pendingDeadline != null) 7 else 0) }
        LaunchedEffect(roomId) { if (group) model.usePending(roomId) }
        if (group) Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.ex_tab_messages), selected = tab == 0) { tab = 0 }
            Chip(stringResource(R.string.ex_tab_expenses), selected = tab == 1) { tab = 1 }
            // Turni del gruppo (tavola ChatTurni): la settimana sempre a portata, chi è in turno ora, scambi controllati.
            Chip(stringResource(R.string.sh_tab), selected = tab == 2) { tab = 2 }
            // Faccende a turno (tavola FamFaccende).
            Chip(stringResource(R.string.fa_tab), selected = tab == 3) { tab = 3 }
            // Elenchi condivisi (tavola InsElenchi) e la nota della riunione (tavola InsRiunione).
            Chip(stringResource(R.string.el_tab), selected = tab == 4) { tab = 4 }
            Chip(stringResource(R.string.ri_tab), selected = tab == 5) { tab = 5 }
            // La cartella di squadra (tavola InsSquadra).
            Chip(stringResource(R.string.tm_tab), selected = tab == 6) { tab = 6 }
            // La casa (tavole FamScadenze, FamDocumenti, FamCena, FamGenitori).
            Chip(stringResource(R.string.fs_tab), selected = tab == 7) { tab = 7 }
            // Gruppo a tempo (tavola ChatGruppoTempo).
            Chip(stringResource(R.string.tb_chip), selected = false) { timeboxSheet = true }
        }
        if (group && tab == 1) {
            Box(Modifier.weight(1f).fillMaxWidth()) { ExpensesPane(model, actions, roomId) }
        } else if (group && tab == 2) {
            Box(Modifier.weight(1f).fillMaxWidth()) { ShiftsPane(model, actions, roomId) }
        } else if (group && tab == 3) {
            Box(Modifier.weight(1f).fillMaxWidth()) { ChoresPane(model, actions, roomId) }
        } else if (group && tab == 4) {
            Box(Modifier.weight(1f).fillMaxWidth()) { ListsPane(model, actions, roomId) }
        } else if (group && tab == 5) {
            Box(Modifier.weight(1f).fillMaxWidth()) { MeetingPane(model, actions, roomId) }
        } else if (group && tab == 6) {
            Box(Modifier.weight(1f).fillMaxWidth()) { TeamPane(model, actions, roomId) }
        } else if (group && tab == 7) {
            Box(Modifier.weight(1f).fillMaxWidth()) { HomePane(model, actions, roomId) }
        } else {
            val roomPlanned = if (thread == null) model.planned.filter { it.roomId == roomId } else emptyList()
            // I messaggi fissati: tocco per andarci.
            if (thread == null) PinnedBar(model, roomId) { id ->
                val i = events.asReversed().indexOfFirst { it.id == id }
                if (i >= 0) scope.launch { list.animateScrollToItem(i + roomPlanned.size) }
            }
            // Gruppo a tempo: il periodo e, finita la cosa, il riepilogo con «Tieni aperto».
            if (thread == null) model.timebox(roomId)?.let { box -> TimeboxBar(model, actions, roomId, box, events) { tab = 1 } }
            if (thread == null && !summaryClosed && unreadAtOpen >= 15 && room?.direct != true && peer == null) {
                SummaryCard(model, actions, roomId, events.takeLast(unreadAtOpen)) { summaryClosed = true }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, reverseLayout = true) {
                // I programmati (tavola ChatProgramma): in fondo (la lista è capovolta), con l'ora in cui partono.
                items(roomPlanned.asReversed(), key = { "plan-" + it.id }) { p ->
                    PlannedBubble(model, p) { plannedMenu = p }
                }
                items(events.asReversed(), key = { it.id }) { e ->
                    val mine = e.sender == me
                    if (e.kind == Event.Kind.Bussola) BussolaBubble(model, actions, roomId, e, mine = mine)
                    else if (e.kind == Event.Kind.Shift) ShiftBubble(model, actions, roomId, e, mine = mine)
                    else if (e.kind == Event.Kind.Audio) Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        AudioBubble(model, actions, roomId, e, mine) { menuFor = e }
                        ReactionChips(model, actions, roomId, e, mine)
                        threads[e.id]?.let { ThreadChip(it, mine) { model.thread = e.id } }
                    }
                    // Organizza un'uscita (tavola AmiUscita).
                    else if (e.kind == Event.Kind.Text && e.data?.contains(Uscita.CARD) == true) Column {
                        OutingBubble(model, actions, roomId, e, mine) { menuFor = e }
                        ReactionChips(model, actions, roomId, e, mine)
                    }
                    // Approvazioni (tavola LavApprova).
                    else if (e.kind == Event.Kind.Text && e.data?.contains(Approva.REQUEST) == true) Column {
                        ApprovalBubble(model, actions, roomId, e, mine) { menuFor = e }
                        ReactionChips(model, actions, roomId, e, mine)
                    }
                    // La stessa scheda in Chat e in Email (tavola InsStessaScheda): Affida e le altre.
                    else if (e.kind == Event.Kind.Text && e.data?.contains(eu.eurise.core.insieme.Scheda.FIELD) == true) Column {
                        CardBubble(model, actions, roomId, e, mine) { menuFor = e }
                        ReactionChips(model, actions, roomId, e, mine)
                    }
                    // Passa la palla (tavola InsPallaChat).
                    else if (e.kind == Event.Kind.Text && e.data?.contains(Palla.BALL) == true) Column {
                        BallBubble(model, actions, roomId, e, mine) { menuFor = e }
                        ReactionChips(model, actions, roomId, e, mine)
                    }
                    // Presa visione (tavola ChatPresaVisione).
                    else if (e.kind == Event.Kind.Text && e.data?.contains(Ack.ACK) == true) Column {
                        AckBubble(model, actions, roomId, e, mine) { menuFor = e }
                        ReactionChips(model, actions, roomId, e, mine)
                        if (thread == null) threads[e.id]?.let { ThreadChip(it, mine) { model.thread = e.id } }
                    }
                    else if (e.kind == Event.Kind.Location) Column {
                        LocationBubble(model, actions, roomId, e, mine) { menuFor = e }
                        ReactionChips(model, actions, roomId, e, mine)
                    }
                    else if (e.kind == Event.Kind.Poll) Column {
                        PollBubble(model, actions, roomId, e, mine) { menuFor = e }
                        ReactionChips(model, actions, roomId, e, mine)
                        threads[e.id]?.let { ThreadChip(it, mine) { model.thread = e.id } }
                    }
                    else Column {
                        Bubble(model, roomId, e, mine = mine, onLong = { if (e.kind != Event.Kind.State) menuFor = e }) {
                            model.media(e) { f -> if (f != null) actions.openFile(f) else actions.toast(fileFailed) }
                        }
                        ReactionChips(model, actions, roomId, e, mine)
                        if (!mine && e.id in recentIds && (e.kind == Event.Kind.Text || e.kind == Event.Kind.Notice) && model.bussolaRole(roomId) == null) {
                            val ins = remember(e.id) { proposals(shownText(model, e), e.time) }
                            if (ins.isNotEmpty()) ProposalCards(model, actions, e, ins)
                        }
                        // «6 risposte» sotto il messaggio di partenza di una discussione (tavola ChatThread).
                        if (thread == null) threads[e.id]?.let { ThreadChip(it, mine) { model.thread = e.id } }
                        // Per chi vede poco (tavola ChatVocali): sotto ogni messaggio ricevuto, «Leggi ad alta voce».
                        if (!mine && model.voice.autoRead && (e.kind == Event.Kind.Text || e.kind == Event.Kind.Notice)) {
                            ReadAloudChip(model, e, shownText(model, e))
                        }
                        // A scomparsa (tavola ChatProgramma).
                        if (e.kind == Event.Kind.Text) FadeLabel(model, e, mine)
                        // Difesa dalle truffe (tavola ChatTruffa): nella chat a due con chi non è verificato e a cui non hai mai scritto.
                        if (!mine && peer != null && (e.kind == Event.Kind.Text || e.kind == Event.Kind.Notice)) {
                            val known = peerVerified || all.any { it.sender == me && !it.id.startsWith("local:") }
                            eu.eurise.core.suite.ScamCheck.check(shownText(model, e), known)?.let { v -> ScamCard(model, actions, roomId, e.sender, v) }
                        }
                        // «Messaggi a me» (tavola ChatCartelle): un tocco e va in Note o in Attività.
                        if (roomId == model.selfRoom && e.kind == Event.Kind.Text && !e.id.startsWith("local:")) SelfChips(model, actions, e)
                    }
                }
                // Una volta sola, dopo l'avviso delle basi: la lettura di tutti i messaggi per chi vede poco.
                if (model.basiHintSeen && !model.readHintSeen && !model.voice.autoRead && events.any { it.kind == Event.Kind.Text && it.sender != me }) item(key = "read-hint") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.vo_hint), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(scheme.onSurface.copy(alpha = 0.06f))
                                .clickable(role = Role.Button) { model.voice.changeAutoRead(true); model.readHintDone(); actions.toast(actions.text(R.string.vo_auto_on)) }
                                .padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                // Una volta sola: come si arriva a rispondere, reagire, modificare… (tavola ChatBasi).
                if (!model.basiHintSeen && events.any { it.kind == Event.Kind.Text }) item(key = "basi-hint") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.ba_hint), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(scheme.onSurface.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                // Nella discussione: in cima il messaggio di partenza (tavola ChatThread).
                if (thread != null) item(key = "thread-root") { ThreadRootCard(model, threadRoot) }
            }
            if (thread != null) ThreadHint(threads[thread]?.count ?: events.size)
            replyTo?.let { e -> ComposerContext(stringResource(R.string.ba_replying_to, model.nameOf(e.sender)), if (e.kind == Event.Kind.Poll) e.text else shownText(model, e)) { replyTo = null } }
            editing?.let { e -> ComposerContext(stringResource(R.string.ba_editing), shownText(model, e)) { editing = null; draft = "" } }
            if (bussola == ChatModel.BussolaRole.Patient) UrgentHint()
            if (peerStart != null && peerHours != null && thread == null) WorkHoursBanner(room?.name.orEmpty(), Orari.label(peerHours), urgent) { urgent = !urgent }
            // La graffetta (tavola ChatAllega): un file qualsiasi, cifrato se la chat lo è; nelle chat di La mia Bussola
            // apre «Invia un referto», con le sue regole (tavola BusReferto).
            var reportSheet by remember(roomId) { mutableStateOf(false) }
            if (reportSheet && bussola != null) ReportSheet(model, actions, roomId, bussola) { reportSheet = false }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Tocco: un file; pressione lunga: un sondaggio (tavola ChatBasi). Non dentro una discussione.
                if (thread == null) Box(
                    Modifier.size(46.dp).clip(CircleShape).background(scheme.surface).combinedClickable(
                        role = Role.Button,
                        onLongClick = { if (bussola == null) moreSheet = true },
                        onLongClickLabel = pollLabel,
                    ) {
                        if (bussola != null) { reportSheet = true; return@combinedClickable }
                        actions.pickFile(arrayOf("*/*")) { uri ->
                            val p = uri?.let(model::describe) ?: return@pickFile
                            model.sendFile(roomId, p, null) { actions.toast(actions.text(it)) }
                        }
                    }.semantics { contentDescription = attachLabel },
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.attach(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(8.dp))
                // Vocali (tavola ChatVocali): con il campo vuoto c'è il microfono; mentre si registra, la striscia rossa.
                val micShown = draft.isBlank() && editing == null && bussola == null && thread == null
                val rec = recording
                if (rec != null) RecordingStrip(rec, cancelling, Modifier.weight(1f))
                else Box(Modifier.weight(1f).heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp)).background(scheme.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (draft.isEmpty()) Text(stringResource(when {
                        editing != null -> R.string.ba_edit_hint
                        thread != null -> R.string.th_write
                        micShown -> R.string.vo_write_or_hold
                        else -> R.string.ch_write
                    }), fontSize = 15.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicTextField(value = draft, onValueChange = { draft = it }, maxLines = 6,
                        textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                        modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(8.dp))
                val send = stringResource(R.string.ch_send)
                if (micShown) MicButton(model, actions, roomId) { r, c -> recording = r; cancelling = c }
                else Box(
                    Modifier.size(46.dp).clip(CircleShape).background(if (draft.isBlank()) scheme.primary.copy(alpha = 0.4f) else scheme.primary)
                        // Pressione lunga (tavola ChatProgramma): più tardi, quando lavora, a scomparsa.
                        .combinedClickable(enabled = draft.isNotBlank(), role = Role.Button, onLongClick = {
                            if (editing == null && bussola == null && thread == null) laterSheet = draft.trim()
                        }) {
                            val text = draft.trim()
                            draft = ""
                            val r = replyTo
                            val ed = editing
                            replyTo = null
                            editing = null
                            when {
                                ed != null -> model.edit(roomId, ed.id, text) { actions.toast(failed) }
                                thread != null -> model.sendInThread(roomId, thread, text, r?.id) { actions.toast(failed) }
                                r != null -> model.reply(roomId, r.id, text) { actions.toast(failed) }
                                peerStart != null && !urgent -> model.plan(roomId, text, peerStart.toInstant().toEpochMilli(), work = true)
                                else -> model.send(roomId, text) { actions.toast(failed) }
                            }
                        }.semantics { contentDescription = send },
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.send(2.2f), null, tint = scheme.onPrimary, modifier = Modifier.size(19.dp)) }
            }
        }
    }
    // Fogli sopra la conversazione (tavola ChatBasi).
    menuFor?.let { e ->
        MessageMenu(model, actions, roomId, e, mine = e.sender == me,
            onReply = { replyTo = e; editing = null },
            onEdit = { editing = e; replyTo = null; draft = shownText(model, e) },
            onForward = { forwarding = e; menuFor = null },
            onBall = { ballFor = e; menuFor = null },
            onApprove = { approvalFile = e.text; menuFor = null },
            onEntrust = { entrustFrom = shownText(model, e); menuFor = null },
            onClose = { menuFor = null; model.basiHintDone() })
    }
    forwarding?.let { e -> ForwardSheet(model, actions, e) { forwarding = null } }
    if (pollSheet) PollSheet(model, actions, roomId) { pollSheet = false }
    // Pressione lunga sulla graffetta: sondaggio o posizione dal vivo (tavola ChatPosizione).
    if (moreSheet) AttachMoreSheet(onPoll = { pollSheet = true }, onLive = { liveSheet = true }, onApprove = { approvalFile = "" },
        onOuting = if (model.room(roomId)?.direct == false) ({ outingSheet = true }) else null,
        more = buildList<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> {
            add(Triple(EuriseIcons.task(2f), stringResource(R.string.af_title), { entrustFrom = "" }))
            // Disponibilità in un link (tavola InsDisponibilita) e Condividi un appuntamento (tavola InsCondividi).
            add(Triple(EuriseIcons.clock(2f), stringResource(R.string.di_title), { slotsSheet = true }))
            add(Triple(EuriseIcons.share(2f), stringResource(R.string.cv_title), { shareSheet = true }))
            // L'agenda con l'assistente (tavola InsAssistente), nelle chat a due.
            if (model.peerOf(roomId) != null) add(Triple(EuriseIcons.calendar(2f), stringResource(R.string.as_menu), { agendaSheet = true }))
            // Chi c'è a cena? (tavola FamCena), nei gruppi.
            if (model.room(roomId)?.direct == false) add(Triple(EuriseIcons.spoon(2f), stringResource(R.string.fc_question), { model.askDinner(roomId, false) { actions.toast(failed) } }))
        },
    ) { moreSheet = false }
    if (slotsSheet) SlotsSheet(model, actions, roomId) { slotsSheet = false }
    if (shareSheet) ShareEventSheet(model, actions, roomId) { shareSheet = false }
    if (agendaSheet) AgendaShareSheet(model, actions, roomId) { agendaSheet = false }
    entrustFrom?.let { t -> EntrustSheet(model, actions, roomId, t) { entrustFrom = null } }
    if (outingSheet) NewOutingSheet(model, actions, roomId) { outingSheet = false }
    approvalFile?.let { f -> AskApprovalSheet(model, actions, roomId, f) { approvalFile = null } }
    if (liveSheet) LiveSheet(model, actions, roomId) { liveSheet = false }
    if (timeboxSheet) TimeboxSheet(model, actions, roomId) { timeboxSheet = false }
    ballFor?.let { e -> PassBallSheet(model, actions, roomId, e) { ballFor = null } }
    laterSheet?.let { text -> SendLaterSheet(model, actions, roomId, text, onSent = { draft = ""; replyTo = null }) { laterSheet = null } }
    plannedMenu?.let { p -> PlannedMenu(model, actions, p, onEdit = { draft = it }) { plannedMenu = null } }
}

@Composable
private fun Bubble(model: ChatModel, roomId: String, e: Event, mine: Boolean, onLong: () -> Unit, onMedia: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    if (e.kind == Event.Kind.Expense || e.kind == Event.Kind.Settlement) { ExpenseBubble(model, e, mine); return }
    if (e.kind == Event.Kind.State) {
        val (what, who) = e.text.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val label = when (what) {
            "join" -> stringResource(R.string.ch_state_join, who)
            "leave" -> stringResource(R.string.ch_state_leave, who)
            "verify" -> stringResource(R.string.ch_state_verify, who)
            "locstop" -> stringResource(R.string.lo_state_stop, who)
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
                .combinedClickable(onClick = { if (e.mediaUrl != null) onMedia() }, onLongClick = onLong)
                .padding(horizontal = 13.dp, vertical = 8.dp),
        ) {
            val fg = if (mine) scheme.onPrimary else scheme.onSurface
            if (!mine && !model.room(model.openRoom ?: "").let { it?.direct ?: false }) {
                Text(model.nameOf(e.sender), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tintFor(e.sender))
            }
            ReplyQuote(model, roomId, e, if (mine) scheme.onPrimary else scheme.onSurface)
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
                else -> Text(shownText(model, e), fontSize = 15.sp, lineHeight = 21.sp, color = fg,
                    fontStyle = if (e.kind == Event.Kind.Notice) FontStyle.Italic else FontStyle.Normal)
            }
            val edited = model.relations.edited(e.id, e.sender) != null
            Text(if (e.id.startsWith("local:")) "…" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.time)) +
                if (edited) " · " + stringResource(R.string.ba_edited) else "",
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

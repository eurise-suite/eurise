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
    fun openPassword(key: String)
    /** «Su carta»: un foglio con la chiave da stampare. */
    fun printKey(key: String, userId: String)
}

/** Le schermate della sicurezza (tavole ChatRecupero e ChatVerifica). */
private enum class SecurityPage { Recovery, Verify }

private val VERIFIED = Color(0xFF4F6B5A)
private val TINTS = listOf(0xFF3E5C8A, 0xFF4F6B5A, 0xFF6B4A7A, 0xFF85672B, 0xFF2E6E73, 0xFFA0522D, 0xFF8C2F39, 0xFF55585E)
private fun tintFor(key: String) = Color(TINTS[(key.hashCode() and 0x7fffffff) % TINTS.size])
private fun initials(name: String): String {
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
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.ch_access_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp))
        Text(stringResource(R.string.ch_access_intro), fontSize = 14.sp, lineHeight = 21.sp)
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
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
    val q = query.trim().lowercase()
    val shown = model.rooms.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.lastText.lowercase().contains(q) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ch_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                RoundIcon(EuriseIcons.person(2.2f), stringResource(R.string.ch_account)) { account = true }
            }
            SearchField(query, stringResource(R.string.ch_search)) { query = it }
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
            if (model.ready && shown.isEmpty()) Text(stringResource(R.string.ch_empty), fontSize = 14.sp, lineHeight = 20.sp,
                color = scheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(shown, key = { it.id }) { r -> RoomRow(model, r) }
                item { Spacer(Modifier.height(100.dp)) }
            }
            Row(
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
        Text(model.session?.userId.orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text(model.session?.homeserver.orEmpty(), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
        Pill(stringResource(R.string.ch_rec_title), EuriseIcons.key(2f), Modifier.fillMaxWidth(), strong = false) { account = false; onRecovery() }
        Pill(stringResource(R.string.ch_ver_menu), EuriseIcons.shield(2f), Modifier.fillMaxWidth(), strong = false) { account = false; onVerify() }
        Pill(stringResource(R.string.ch_logout), EuriseIcons.close(2f), Modifier.fillMaxWidth(), strong = false) { account = false; model.logout() }
    }
}

@Composable
private fun RoomRow(model: ChatModel, r: Room) {
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

private fun shortTime(t: Long): String {
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
            // Verifica della persona (tavola ChatVerifica): solo nelle chat a due cifrate.
            if (peer != null && room?.encrypted == true) {
                RoundIcon(EuriseIcons.shield(2.2f), stringResource(if (peerVerified) R.string.ch_ver_person_again else R.string.ch_ver_person), flat = true) {
                    model.startVerifyUser(roomId) { actions.toast(actions.text(it)) }
                    onVerify()
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, reverseLayout = true) {
            items(events.asReversed(), key = { it.id }) { e ->
                Bubble(model, e, mine = e.sender == me) { model.media(e) { f -> if (f != null) actions.openFile(f) else actions.toast(fileFailed) } }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
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

@Composable
private fun Bubble(model: ChatModel, e: Event, mine: Boolean, onMedia: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
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
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
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

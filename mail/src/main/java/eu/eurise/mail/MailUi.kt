package eu.eurise.mail

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.designsystem.component.GlassDefaults
import eu.eurise.core.designsystem.component.glass
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Cosa si scrive: destinatari, oggetto, testo, allegati e, per una risposta, il messaggio a cui si risponde. */
data class Draft(
    val to: String = "",
    val cc: String = "",
    val subject: String = "",
    val body: String = "",
    val inReplyTo: String? = null,
    val from: String? = null,
    val attachments: List<android.net.Uri> = emptyList(),
)

/** Le azioni che passano dall'attività (file, altre app della suite, permessi, avvisi). */
interface MailActions {
    fun openFile(file: java.io.File)
    fun toast(text: String)
    fun text(id: Int): String
    fun askNotifications()
    fun finish()
    /** Apre un'altra app (Calendario, Rubrica, Telefono, Mappe…); falso se nessuna lo sa fare. */
    fun start(intent: android.content.Intent?): Boolean
    /** In Attività ([task]) o in Note, nell'app Eurise. */
    fun toHub(task: Boolean, text: String)
    /** «Salva in…» di Eurise File: telefono o cloud. */
    fun saveIn(file: java.io.File)
    /** «Allega»: da Eurise File («Apri da…») o dal selettore di Android. */
    fun pickAttachments(onPicked: (List<android.net.Uri>) -> Unit)
    /** Il link per creare la password per le app (servizi chiusi). */
    fun openLink(url: String)
    /** La password della casella in Eurise Password, come voce da confermare; falso se Password non c'è o è vecchia. */
    fun toPassword(title: String, username: String, secret: String, url: String): Boolean
    /** Eurise Password è installata e riceve voci dalla suite. */
    fun hasPassword(): Boolean
}

private enum class Screen { Inbox, Accounts }

@Composable
fun MailApp(model: MailModel, actions: MailActions, startDraft: Draft?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var screen by remember { mutableStateOf(if (model.accounts.isEmpty()) Screen.Accounts else Screen.Inbox) }
    var draft by remember { mutableStateOf(startDraft) }
    val opened = model.opened

    BackHandler {
        when {
            draft != null -> draft = null
            opened != null || model.opening -> model.close()
            screen == Screen.Accounts && model.accounts.isNotEmpty() -> screen = Screen.Inbox
            else -> actions.finish()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (screen) {
            Screen.Inbox -> Inbox(model, onAccounts = { screen = Screen.Accounts }, onWrite = { draft = Draft() })
            Screen.Accounts -> AccountsScreen(model, actions, onBack = { if (model.accounts.isNotEmpty()) screen = Screen.Inbox else actions.finish() })
        }
        if (opened != null || model.opening) Reader(model, actions, onForward = { o ->
            draft = Draft(
                subject = MailText.forwardSubject(o.summary.subject),
                body = MailText.quote(o.text, forwardHeader(context, o)),
                from = o.summary.accountId,
            )
        }, onReply = { o ->
            draft = Draft(
                to = o.summary.fromAddress,
                subject = MailText.replySubject(o.summary.subject),
                body = MailText.quote(o.text, "${o.summary.fromName} (${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(o.summary.date))}):"),
                inReplyTo = o.messageId, from = o.summary.accountId,
            )
        })
        draft?.let { d -> Writer(model, actions, d, onClose = { draft = null }) }
    }
}

// --- Posta (tavola MailPosta) ---

@Composable
private fun Inbox(model: MailModel, onAccounts: () -> Unit, onWrite: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var kind by remember { mutableStateOf(Kind.People) }
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = model.messages.filter { m ->
        if (q.isNotEmpty()) m.subject.lowercase().contains(q) || m.fromName.lowercase().contains(q) || m.fromAddress.lowercase().contains(q)
        else m.kind == kind
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ml_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                RoundIcon(EuriseIcons.refresh(2.2f), stringResource(R.string.ml_refresh), onClick = { model.refresh() })
                Spacer(Modifier.width(8.dp))
                RoundIcon(EuriseIcons.gear(2.2f), stringResource(R.string.ml_accounts), onClick = onAccounts)
            }
            SearchField(query, stringResource(R.string.ml_search)) { query = it }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Kind.entries.forEach { k ->
                    val unread = model.messages.count { it.kind == k && !it.seen }
                    val label = stringResource(
                        when (k) { Kind.People -> R.string.ml_people; Kind.Alerts -> R.string.ml_alerts; Kind.Newsletters -> R.string.ml_newsletters },
                    ) + if (unread > 0 && k != Kind.People) " · $unread" else ""
                    Chip(label, selected = q.isEmpty() && kind == k) { kind = k; query = "" }
                }
            }
        }
        if (model.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        model.error?.let { e ->
            Text(e, fontSize = 12.5.sp, color = scheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).clickable { model.error = null })
        }
        Box(Modifier.weight(1f)) {
            if (shown.isEmpty()) {
                Text(
                    stringResource(if (model.refreshing) R.string.ml_loading else R.string.ml_empty),
                    fontSize = 14.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(shown, key = { "${it.accountId}/${it.uid}" }) { m ->
                    MessageRow(model, m) { model.open(m) }
                }
                item {
                    if (model.accounts.size > 1) Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        model.accounts.forEach { a -> AccountDot(a) }
                    }
                    Text(stringResource(R.string.ml_inbox_note), fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 100.dp))
                }
            }
            Row(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).height(56.dp)
                    .clip(RoundedCornerShape(28.dp)).background(scheme.primary).clickable(role = Role.Button, onClick = onWrite)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.pen(2.4f), null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ml_write), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
            }
        }
    }
    // Prima apertura: la posta si aggiorna da sola.
    androidx.compose.runtime.LaunchedEffect(model.accounts.size) { model.refresh() }
}

@Composable
private fun MessageRow(model: MailModel, m: Summary, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = model.account(m.accountId)?.let { Color(ACCOUNT_TINTS[it.tint % ACCOUNT_TINTS.size]) } ?: scheme.primary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(MailText.initials(m.fromName, m.fromAddress), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.fromName, fontSize = 15.sp, fontWeight = if (m.seen) FontWeight.SemiBold else FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(shortDate(m.date), fontSize = 12.sp, fontWeight = if (m.seen) FontWeight.Normal else FontWeight.Bold,
                    color = if (m.seen) scheme.onSurfaceVariant else scheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.answered) {
                    Icon(EuriseIcons.reply(2.4f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(m.subject.ifBlank { stringResource(R.string.ml_no_subject) }, fontSize = 13.sp,
                    color = if (m.seen) scheme.onSurfaceVariant else scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (!m.seen) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(9.dp).clip(CircleShape).background(scheme.primary))
                }
            }
        }
    }
    HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
}

private fun shortDate(time: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = time }
    val now = Calendar.getInstance()
    return if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
    } else DateFormat.getDateInstance(DateFormat.SHORT).format(Date(time))
}

// --- Lettura (tavola MailLettura) ---

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Reader(model: MailModel, actions: MailActions, onForward: (Opened) -> Unit, onReply: (Opened) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val o = model.opened
    var showImages by remember(o?.summary?.uid) { mutableStateOf(!model.blockImages) }
    var chosen by remember(o?.summary?.uid) { mutableStateOf<Int?>(null) }
    val dark = isSystemInDarkTheme()
    val archived = stringResource(R.string.ml_archived)
    val trashed = stringResource(R.string.ml_trashed)
    val moveFailed = stringResource(R.string.ml_move_failed)
    val attachFailed = stringResource(R.string.ml_attachment_failed)
    val spammed = stringResource(R.string.ml_spammed)
    val moved = stringResource(R.string.ml_moved)
    var folders by remember(o?.summary?.uid) { mutableStateOf<List<String>?>(null) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.ml_back), flat = true) { model.close() }
            Spacer(Modifier.weight(1f))
        }
        if (o == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            return@Column
        }
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(o.summary.subject.ifBlank { stringResource(R.string.ml_no_subject) }, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text(o.summary.fromName + if (o.summary.fromName != o.summary.fromAddress) " · ${o.summary.fromAddress}" else "",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.ml_to_line, o.to.ifBlank { "—" }) + " · " +
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(o.summary.date)),
                fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (o.remoteImages > 0 && !showImages) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(EuriseIcons.shield(2f), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ml_images_blocked, o.remoteImages), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    Chip(stringResource(R.string.ml_show), selected = false) { showImages = true }
                }
            }
            SuiteProposals(o, actions)
            if (o.attachments.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    o.attachments.forEach { a ->
                        Chip("${a.name} · ${size(a.size)}", selected = chosen == a.index) { chosen = if (chosen == a.index) null else a.index }
                    }
                }
                o.attachments.firstOrNull { it.index == chosen }?.let { a ->
                    AttachmentChoice(
                        a.name,
                        onOpen = { model.attachment(o.summary, a.index) { f -> if (f != null) actions.openFile(f) else actions.toast(attachFailed) } },
                        onSave = { model.attachment(o.summary, a.index) { f -> if (f != null) actions.saveIn(f) else actions.toast(attachFailed) } },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val page = MailText.page(o.html, dark)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.loadsImagesAutomatically = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)) }
                            return true
                        }
                    }
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { w ->
                // Immagini remote (e ogni altra richiesta in rete) bloccate finché non tocchi «Mostra».
                w.settings.blockNetworkLoads = !showImages
                w.settings.blockNetworkImage = !showImages
                if (w.tag != page + showImages) {
                    w.tag = page + showImages
                    w.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
        )
        // Lo spazio della pillola, perché la fine del messaggio non resti sotto.
        Spacer(Modifier.navigationBarsPadding().height(GlassDefaults.PillHeight + GlassDefaults.BottomMargin * 2))
    }
    // La pillola satinata delle azioni (come nelle altre app della suite): Rispondi, Inoltra, Archivia, Sposta in…,
    // Elimina, Spam.
    o?.let { m ->
        ActionPill(
            listOf(
                PillAction(EuriseIcons.reply(2.2f), stringResource(R.string.ml_reply), strong = true) { onReply(m) },
                PillAction(EuriseIcons.send(2.2f), stringResource(R.string.ml_forward)) { onForward(m) },
                PillAction(EuriseIcons.folder(2.2f), stringResource(R.string.ml_archive)) {
                    model.move(m.summary, MailClient.Target.Archive) { ok -> actions.toast(if (ok) archived else moveFailed) }
                },
                PillAction(EuriseIcons.drag(2.2f), stringResource(R.string.ml_move_to)) { model.folders(m.summary) { folders = it } },
                PillAction(EuriseIcons.trash(2.2f), stringResource(R.string.ml_delete)) {
                    model.move(m.summary, MailClient.Target.Trash) { ok -> actions.toast(if (ok) trashed else moveFailed) }
                },
                PillAction(EuriseIcons.block(2.2f), stringResource(R.string.ml_spam)) {
                    model.move(m.summary, MailClient.Target.Junk) { ok -> actions.toast(if (ok) spammed else moveFailed) }
                },
            ),
            Modifier.align(Alignment.BottomCenter),
        )
        folders?.let { list ->
            FolderSheet(list, onPick = { f -> folders = null; model.moveTo(m.summary, f) { ok -> actions.toast(if (ok) moved else moveFailed) } }, onClose = { folders = null })
        }
    }
    }
}

private class PillAction(val icon: ImageVector, val label: String, val strong: Boolean = false, val onClick: () -> Unit)

/** La pillola satinata in basso con le azioni del messaggio aperto; la voce principale ha il colore d'accento. */
@Composable
private fun ActionPill(items: List<PillAction>, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val haze = remember { HazeState() }
    Row(
        modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = GlassDefaults.SideMargin, vertical = GlassDefaults.BottomMargin)
            .height(GlassDefaults.PillHeight).glass(haze, RoundedCornerShape(50), solid = true).padding(GlassDefaults.Padding),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { a ->
            val color = if (a.strong) scheme.primary else scheme.onSurfaceVariant
            Column(
                Modifier.weight(1f).height(GlassDefaults.ItemHeight).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = a.onClick),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(a.icon, null, tint = color, modifier = Modifier.size(20.dp))
                Text(a.label, color = color, fontSize = 10.sp, fontWeight = if (a.strong) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** «Sposta in…»: le cartelle della casella. */
@Composable
private fun FolderSheet(folders: List<String>, onPick: (String) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f)).clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.surfaceContainer)
                .clickable(enabled = false) { }.navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.ml_move_to_title), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 6.dp))
            if (folders.isEmpty()) Text(stringResource(R.string.ml_no_folders), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                folders.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(f) }.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(EuriseIcons.folder(2.2f), null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(f.replace('/', '›').replace(".", " › ").trim(), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun forwardHeader(context: android.content.Context, o: Opened): String = context.getString(
    R.string.ml_forward_header, o.summary.fromName, o.summary.fromAddress,
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(o.summary.date)), o.summary.subject,
)

private fun size(bytes: Int): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 * 1024 -> "${(bytes / 1024).coerceAtLeast(1)} KB"
    else -> String.format(java.util.Locale.ITALY, "%.1f MB", bytes / 1048576.0)
}

// --- Scrivi (tavola MailScrivi) ---

@Composable
private fun Writer(model: MailModel, actions: MailActions, start: Draft, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var from by remember { mutableStateOf(model.account(start.from ?: "") ?: model.accounts.firstOrNull()) }
    var to by remember { mutableStateOf(start.to) }
    var cc by remember { mutableStateOf(start.cc) }
    var subject by remember { mutableStateOf(start.subject) }
    var body by remember { mutableStateOf(start.body) }
    val files = remember { androidx.compose.runtime.mutableStateListOf<android.net.Uri>().apply { addAll(start.attachments) } }
    var sending by remember { mutableStateOf(false) }
    val sent = stringResource(R.string.ml_sent)
    val failed = stringResource(R.string.ml_send_failed)
    val noAccount = stringResource(R.string.ml_no_account)
    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().windowInsetsPadding(WindowInsets.ime)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(EuriseIcons.close(2.2f), stringResource(R.string.ml_cancel), flat = true, onClick = onClose)
            Text(stringResource(if (start.inReplyTo != null) R.string.ml_reply else R.string.ml_write), fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (sending) scheme.primary.copy(alpha = 0.5f) else scheme.primary)
                    .clickable(enabled = !sending, role = Role.Button) {
                        val account = from ?: return@clickable actions.toast(noAccount)
                        if (to.isBlank()) return@clickable
                        sending = true
                        model.send(account, to, cc, subject, body, start.inReplyTo, files.toList()) { ok ->
                            sending = false
                            actions.toast(if (ok) sent else failed)
                            if (ok) onClose()
                        }
                    }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.send(2.2f), null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ml_send), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (model.accounts.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    model.accounts.forEach { a -> Chip(a.email, selected = from?.id == a.id) { from = a } }
                }
            } else if (from != null) {
                Text(stringResource(R.string.ml_from_line, from!!.email), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            }
            Field(stringResource(R.string.ml_to), to, KeyboardType.Email) { to = it }
            Field(stringResource(R.string.ml_cc), cc, KeyboardType.Email) { cc = it }
            Field(stringResource(R.string.ml_subject), subject, KeyboardType.Text) { subject = it }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("+ " + stringResource(R.string.ml_attach), selected = false) { actions.pickAttachments { picked -> files.addAll(picked.filter { it !in files }) } }
                files.forEach { u ->
                    Chip((u.lastPathSegment ?: "file").substringAfterLast('/') + "  ✕", selected = true) { files.remove(u) }
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 260.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(14.dp)) {
                if (body.isEmpty()) Text(stringResource(R.string.ml_body), fontSize = 15.sp, color = scheme.onSurfaceVariant)
                BasicTextField(
                    value = body, onValueChange = { body = it },
                    textStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// --- Caselle (tavola MailCaselle) ---

@Composable
private fun AccountsScreen(model: MailModel, actions: MailActions, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var linking by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (model.accounts.isNotEmpty()) {
                RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.ml_back), flat = true, onClick = onBack)
                Spacer(Modifier.width(6.dp))
            }
            Text(stringResource(R.string.ml_accounts), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(stringResource(R.string.ml_accounts_intro), fontSize = 14.sp, lineHeight = 21.sp, color = scheme.onSurface)
        if (model.accounts.isNotEmpty()) Card {
            model.accounts.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBox(EuriseIcons.mail(2.2f))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.label, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Text(a.email, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(ACCOUNT_TINTS[a.tint % ACCOUNT_TINTS.size])))
                    Spacer(Modifier.width(6.dp))
                    Chip(stringResource(R.string.ml_remove), selected = false) { model.remove(a) }
                }
            }
        }
        SectionTitle(stringResource(R.string.ml_add))
        Card {
            AddRow(EuriseIcons.shield(2.2f), stringResource(R.string.ml_add_eu), stringResource(R.string.ml_add_eu_sub)) { linking = "" }
            HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
            AddRow(EuriseIcons.globe(2.2f), stringResource(R.string.ml_add_domain), stringResource(R.string.ml_add_domain_sub)) { linking = "" }
            HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
            // Servizi chiusi: si possono usare, ma in fondo, con valutazione bassa e prima l'avviso.
            AddRow(EuriseIcons.mail(2.2f), stringResource(R.string.ml_add_closed), stringResource(R.string.ml_add_closed_sub)) { warning = true }
        }
        Text(stringResource(R.string.ml_oauth_later), fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
        SectionTitle(stringResource(R.string.ml_privacy))
        Card {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Switch) { model.setBlockImages(!model.blockImages) },
                verticalAlignment = Alignment.CenterVertically) {
                IconBox(EuriseIcons.eye(2.2f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ml_block_title), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ml_block_sub), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                Switch(checked = model.blockImages, onCheckedChange = { model.setBlockImages(it) })
            }
        }
        Text(stringResource(R.string.ml_privacy_note), fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
    linking?.let { LinkSheet(model, actions, onClose = { linking = null }, onLinked = { linking = null; onBack() }) }
    if (warning) ClosedWarning(onAccept = { warning = false; linking = "" }, onClose = { warning = false })
}

@Composable
private fun LinkSheet(model: MailModel, actions: MailActions, onClose: () -> Unit, onLinked: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Int?>(null) }
    val canKeep = remember { actions.hasPassword() }
    var keep by remember { mutableStateOf(canKeep) }
    val provider = Providers.known(email)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable(enabled = !working, onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.surfaceContainer)
                .pointerInput(Unit) { detectTapGestures { } }.navigationBarsPadding().windowInsetsPadding(WindowInsets.ime)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.ml_link_title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Field(stringResource(R.string.ml_link_email), email, KeyboardType.Email) { email = it; problem = null }
            Field(stringResource(R.string.ml_link_password), password, KeyboardType.Password, secret = true) { password = it; problem = null }
            Field(stringResource(R.string.ml_link_name), name, KeyboardType.Text) { name = it }
            Text(
                if (provider != null) stringResource(R.string.ml_link_known, provider.name, provider.where)
                else stringResource(R.string.ml_link_find),
                fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant,
            )
            provider?.appPasswordUrl?.let { url ->
                Text(stringResource(R.string.ml_app_password, provider.name), fontSize = 12.5.sp, lineHeight = 18.sp, color = scheme.onSurface)
                Chip(stringResource(R.string.ml_app_password_go, provider.name), selected = false) { actions.openLink(url) }
            }
            // La password anche in Eurise Password (la voce si salva là solo se confermi).
            if (canKeep) Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { keep = !keep }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ml_keep_password), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ml_keep_password_sub), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
                }
                Switch(checked = keep, onCheckedChange = { keep = it })
            }
            problem?.let { Text(stringResource(it), fontSize = 13.sp, lineHeight = 19.sp, color = scheme.error) }
            Pill(
                stringResource(if (working) R.string.ml_link_working else R.string.ml_link_go), EuriseIcons.link(2.2f),
                Modifier.fillMaxWidth(), strong = !working && email.isNotBlank() && password.isNotEmpty(),
            ) {
                if (working || email.isBlank() || password.isEmpty()) return@Pill
                working = true
                model.link(email, password, name) { r ->
                    working = false
                    when (r) {
                        is LinkResult.Done -> {
                            if (keep) actions.toPassword(r.account.label, r.account.email, password, "https://" + Providers.domainOf(r.account.email))
                            else actions.askNotifications()
                            onLinked()
                        }
                        is LinkResult.Failed -> problem = r.reason
                    }
                }
            }
        }
    }
}

// --- Pezzi comuni ---

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp), content = content)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@Composable
private fun IconBox(icon: ImageVector) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun AddRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Chip(stringResource(R.string.ml_link), selected = false, onClick = onClick)
    }
}

@Composable
private fun AccountDot(a: Account) {
    Row(
        Modifier.clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(ACCOUNT_TINTS[a.tint % ACCOUNT_TINTS.size])))
        Spacer(Modifier.width(5.dp))
        Text(a.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) scheme.primary else scheme.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) scheme.onPrimary else scheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun Pill(label: String, icon: ImageVector, modifier: Modifier, strong: Boolean, onClick: () -> Unit) {
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
private fun RoundIcon(icon: ImageVector, label: String, flat: Boolean = false, onClick: () -> Unit) {
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
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(scheme.surface).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EuriseIcons.search(2.3f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, fontSize = 14.5.sp, color = scheme.onSurfaceVariant, maxLines = 1)
            BasicTextField(value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(fontSize = 14.5.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType, secret: Boolean = false, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}


/** Prima di collegare un servizio chiuso: cosa vede, dove stanno i dati, come si torna indietro. */
@Composable
private fun ClosedWarning(onAccept: () -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.surfaceContainer)
                .pointerInput(Unit) { detectTapGestures { } }.navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ml_closed_title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.ml_closed_level), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8C2F39))
            }
            Text(stringResource(R.string.ml_closed_explain), fontSize = 13.5.sp, lineHeight = 20.sp)
            Text(stringResource(R.string.ml_closed_alternative), fontSize = 12.5.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.ml_cancel), EuriseIcons.close(2f), Modifier.weight(1f), strong = false, onClick = onClose)
                Pill(stringResource(R.string.ml_closed_go), EuriseIcons.link(2f), Modifier.weight(1f), strong = true, onClick = onAccept)
            }
        }
    }
}

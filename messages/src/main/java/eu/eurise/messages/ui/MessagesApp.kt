package eu.eurise.messages.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.contacts.Avatar
import eu.eurise.core.contacts.Contact
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.data.messages.MessageInsights
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.messages.R
import eu.eurise.messages.data.SmsStore
import eu.eurise.messages.data.Thread
import eu.eurise.messages.system.Codes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Che cosa aprire quando arriva un'intenzione da fuori (notifica, sms:, Condividi). */
sealed interface Open {
    data class Thread(val id: Long, val addresses: List<String>) : Open
    data class New(val to: List<String>, val text: String, val attachment: Uri?, val mime: String?) : Open
}

private sealed interface Screen {
    data object List : Screen
    data class Conversation(val threadId: Long, val addresses: kotlin.collections.List<String>, val draft: String = "", val attachment: Uri? = null, val mime: String? = null) : Screen
    data class New(val text: String, val attachment: Uri?, val mime: String?) : Screen
}

/** Una riga dell'elenco con quel che serve a mostrarla. */
private data class Entry(val thread: Thread, val contact: Contact?, val category: MessageInsights.Category, val proposal: MessageInsights.Insight.Event?, val code: String?)

@Composable
fun MessagesApp(open: Open?, onOpened: () -> Unit, refresh: Int, isDefault: Boolean, onBecomeDefault: () -> Unit, onAskPermissions: () -> Unit) {
    val context = LocalContext.current
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.List)) }
    var rows by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }

    // Ogni cambio nel database dei messaggi (arrivi, invii) aggiorna l'elenco.
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { tick++ }
        }
        runCatching { context.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer) }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(refresh, tick) {
        val threads = SmsStore.threads(context)
        val names = Contacts.names(context, threads.mapNotNull { it.addresses.firstOrNull() })
        val now = LocalDateTime.now()
        rows = threads.map { t ->
            val address = t.addresses.firstOrNull().orEmpty()
            val contact = names[address]
            val last = if (t.unread || t.date > System.currentTimeMillis() - 3 * 86_400_000L) SmsStore.lastIncoming(context, t.id) else null
            val text = last?.body ?: t.snippet
            val category = if (contact != null) MessageInsights.Category.People else MessageInsights.category(address, text)
            val code = MessageInsights.code(text)?.takeIf { last != null && System.currentTimeMillis() - last.date < Codes.MINUTES * 60_000 }
            val proposal = last?.let { MessageInsights.analyze(it.body, now).filterIsInstance<MessageInsights.Insight.Event>().firstOrNull() }
            Entry(t, contact, category, proposal, code)
        }
    }
    LaunchedEffect(open) {
        when (open) {
            is Open.Thread -> stack = listOf(Screen.List, Screen.Conversation(open.id, open.addresses))
            is Open.New -> stack = if (open.to.isNotEmpty()) {
                val id = runCatching { SmsStore.threadId(context, open.to.toSet()) }.getOrDefault(-1L)
                listOf(Screen.List, Screen.Conversation(id, open.to, open.text, open.attachment, open.mime))
            } else listOf(Screen.List, Screen.New(open.text, open.attachment, open.mime))
            null -> return@LaunchedEffect
        }
        onOpened()
    }
    BackHandler(stack.size > 1) { stack = stack.dropLast(1) }

    when (val s = stack.last()) {
        Screen.List -> ListScreen(rows, isDefault, onBecomeDefault, onAskPermissions,
            onOpen = { r: Entry -> stack = stack + Screen.Conversation(r.thread.id, r.thread.addresses) },
            onNew = { stack = stack + Screen.New("", null, null) })
        is Screen.Conversation -> ConversationScreen(s.threadId, s.addresses, s.draft, s.attachment, s.mime, isDefault, tick, onBecomeDefault, onBack = { stack = stack.dropLast(1) })
        is Screen.New -> NewScreen(onBack = { stack = stack.dropLast(1) }, onPick = { to ->
            val id = runCatching { SmsStore.threadId(context, to.toSet()) }.getOrDefault(-1L)
            stack = stack.dropLast(1) + Screen.Conversation(id, to, s.text, s.attachment, s.mime)
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(rows: List<Entry>, isDefault: Boolean, onBecomeDefault: () -> Unit, onAskPermissions: () -> Unit, onOpen: (Entry) -> Unit, onNew: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(MessageInsights.Category.People) }
    var query by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(false) }
    val permitted = context.checkSelfPermission(android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val codesCount = rows.count { it.category == MessageInsights.Category.Codes && it.thread.unread }
    val shown = rows.filter { r ->
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) (r.contact?.name ?: r.thread.addresses.joinToString()).lowercase().contains(q) || r.thread.snippet.lowercase().contains(q)
        else r.category == tab
    }
    val freshCode = rows.firstOrNull { it.code != null }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item(key = "head") {
                Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ms_app_name), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        IconCircle(EuriseIcons.gear(), stringResource(R.string.ms_settings)) { settings = true }
                    }
                    OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.ms_search)) }, singleLine = true,
                        leadingIcon = { Icon(EuriseIcons.search(), null, modifier = Modifier.size(18.dp)) }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth())
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(stringResource(R.string.ms_tab_people), tab == MessageInsights.Category.People) { tab = MessageInsights.Category.People }
                        Chip(stringResource(R.string.ms_tab_codes) + if (codesCount > 0) " · $codesCount" else "", tab == MessageInsights.Category.Codes) { tab = MessageInsights.Category.Codes }
                        Chip(stringResource(R.string.ms_tab_alerts), tab == MessageInsights.Category.Alerts) { tab = MessageInsights.Category.Alerts }
                    }
                }
            }
            if (!permitted) item(key = "perm") { Banner(stringResource(R.string.ms_need_permissions), stringResource(R.string.ms_allow), onAskPermissions) }
            else if (!isDefault) item(key = "role") { Banner(stringResource(R.string.ms_become_default), stringResource(R.string.ms_become_default_ok), onBecomeDefault) }
            freshCode?.let { r ->
                item(key = "code") {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                            Text("123", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ms_code_from, r.code.orEmpty(), name(r)), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(stringResource(if (Codes.enabled(context)) R.string.ms_code_cleanup else R.string.ms_code_kept), fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Pill(stringResource(R.string.ms_copy)) {
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("codice", r.code))
                        }
                    }
                }
            }
            item(key = "space") { Spacer(Modifier.height(10.dp)) }
            items(shown, key = { it.thread.id }) { r ->
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { onOpen(r) }.padding(horizontal = 12.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (r.category == MessageInsights.Category.People || r.contact != null) Avatar(name(r), r.contact?.photo, 42.dp)
                    else Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(if (r.category == MessageInsights.Category.Codes) EuriseIcons.parcel() else EuriseIcons.bell(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name(r), fontSize = 15.sp, fontWeight = if (r.thread.unread) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(DateUtils.getRelativeTimeSpanString(r.thread.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                                fontSize = 12.sp, fontWeight = if (r.thread.unread) FontWeight.Bold else FontWeight.Normal,
                                color = if (r.thread.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(r.thread.snippet.replace('\n', ' '), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (r.thread.unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        r.proposal?.let { p ->
                            Text(stringResource(R.string.ms_proposal, when2(p)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (shown.isEmpty() && permitted) item(key = "empty") {
                Text(stringResource(R.string.ms_empty), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
            item(key = "privacy") {
                Text(stringResource(R.string.ms_privacy_note), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).height(56.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onNew).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.pen(), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ms_new), color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (settings) {
        ModalBottomSheet(onDismissRequest = { settings = false }, containerColor = MaterialTheme.colorScheme.background) {
            var cleanup by remember { mutableStateOf(Codes.enabled(context)) }
            Column(Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.ms_settings), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.ms_set_cleanup), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.ms_set_cleanup_d), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(cleanup, onCheckedChange = { cleanup = it; Codes.setEnabled(context, it) })
                }
                if (!isDefault) Pill(stringResource(R.string.ms_become_default_ok), onClick = onBecomeDefault)
            }
        }
    }
}

@Composable
private fun name(r: Entry): String = r.contact?.name ?: r.thread.addresses.joinToString(", ") { Contacts.format(it) }.ifEmpty { stringResource(R.string.ms_unknown) }

private fun when2(e: MessageInsights.Insight.Event): String {
    val day = e.date.format(DateTimeFormatter.ofPattern("EEEE d", Locale.ITALIAN))
    return e.time?.let { day + " " + it.format(DateTimeFormatter.ofPattern("H:mm")) } ?: day
}

/** Nuovo messaggio: un contatto dalla rubrica o un numero scritto. */
@Composable
private fun NewScreen(onBack: () -> Unit, onPick: (List<String>) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var numbers by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(Unit) { contacts = Contacts.all(context); numbers = Contacts.firstNumbers(context) }
    val q = query.trim().lowercase()
    val shown = contacts.filter { c -> numbers[c.id] != null && (q.isEmpty() || c.name.lowercase().contains(q) || numbers[c.id]!!.filter(Char::isDigit).contains(q.filter(Char::isDigit).ifEmpty { "§" })) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.ms_new), onBack) {}
        OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.ms_to)) }, singleLine = true, shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        if (q.count(Char::isDigit) >= 3) {
            Row(Modifier.fillMaxWidth().clickable { onPick(listOf(query.trim())) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(EuriseIcons.send(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.ms_write_to, query.trim()), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().clickable { numbers[c.id]?.let { onPick(listOf(it)) } }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(c.name, c.photo, 40.dp)
                    Column {
                        Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(numbers[c.id].orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Pezzi comuni

@Composable
internal fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconCircle(EuriseIcons.chevronLeft(), stringResource(R.string.ms_back), onClick = onBack)
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(17.dp)).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp))
}

@Composable
internal fun Banner(text: String, action: String, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
        Pill(action, onClick = onClick)
    }
}

@Composable
internal fun Pill(label: String, light: Boolean = false, onClick: () -> Unit) {
    Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
        color = if (light) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(17.dp)).background(if (light) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
internal fun IconCircle(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
    }
}


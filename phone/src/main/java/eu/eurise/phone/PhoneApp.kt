package eu.eurise.phone

import android.content.ClipData
import android.content.ClipboardManager
import android.provider.CallLog
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import eu.eurise.core.contacts.ContactDraft
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.contacts.Reach
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.component.DockItem
import eu.eurise.core.designsystem.component.EuriseDock
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.phone.data.CallEntry
import eu.eurise.phone.data.PhoneData
import kotlinx.coroutines.launch

/** Dove aprire l'app quando arriva da fuori: la rubrica, o l'editor già compilato («Aggiungi in rubrica»). */
sealed interface StartAt {
    data object People : StartAt
    data class Insert(val draft: ContactDraft) : StartAt
}

private sealed interface Screen {
    data object Home : Screen
    data object People : Screen
    data class Person(val id: Long) : Screen
    data class Edit(val draft: ContactDraft) : Screen
    /** Filtro antispam e numeri bloccati. */
    data object Filter : Screen
}

@Composable
fun PhoneApp(
    dialNumber: String?,
    onDialConsumed: () -> Unit,
    start: StartAt?,
    onStartConsumed: () -> Unit,
    refresh: Int,
    isDefault: Boolean,
    onBecomeDefault: () -> Unit,
    onAskPermissions: () -> Unit,
    onCall: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    var recents by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var numbers by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var dialpad by remember { mutableStateOf<String?>(null) }
    var unknown by remember { mutableStateOf<CallEntry?>(null) }
    var reload by remember { mutableStateOf(0) }
    // Sezione della pillola in basso (tavola TelPillola): Preferiti, Recenti, Rubrica; il Tastierino si apre sopra.
    var section by remember { mutableStateOf(SEC_RECENTS) }
    val haze = remember { HazeState() }

    var filteredLog by remember { mutableStateOf<List<eu.eurise.phone.data.SpamFilter.Filtered>>(emptyList()) }
    LaunchedEffect(refresh, reload) {
        filteredLog = eu.eurise.phone.data.SpamFilter.filtered(context)
        recents = PhoneData.recents(context)
        contacts = Contacts.all(context)
        numbers = Contacts.firstNumbers(context)
    }
    LaunchedEffect(dialNumber) { if (dialNumber != null) { dialpad = dialNumber; onDialConsumed() } }
    LaunchedEffect(start) {
        when (start) {
            StartAt.People -> { stack = listOf(Screen.Home); section = SEC_PEOPLE }
            is StartAt.Insert -> { stack = listOf(Screen.Home, Screen.Edit(start.draft)); section = SEC_PEOPLE }
            null -> return@LaunchedEffect
        }
        onStartConsumed()
    }

    fun push(s: Screen) { stack = stack + s }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(stack.size > 1) { pop() }

    val permitted = PhoneData.has(context, android.Manifest.permission.READ_CALL_LOG) && Contacts.canRead(context)

    when (val s = stack.last()) {
        Screen.Home -> Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                when (section) {
                    SEC_FAVORITES -> FavoritesScreen(contacts.filter { it.starred }, numbers, onOpen = { push(Screen.Person(it.id)) }, onCall = onCall)
                    SEC_PEOPLE -> PeopleScreen(contacts, numbers, onBack = null, onOpen = { push(Screen.Person(it.id)) }, onNew = { push(Screen.Edit(ContactDraft())) }, onCall = onCall)
                    else -> HomeScreen(
                        recents = recents, favorites = contacts.filter { it.starred }, permitted = permitted, isDefault = isDefault,
                        onBecomeDefault = onBecomeDefault, onAskPermissions = onAskPermissions,
                        onOpenEntry = { e -> e.contact?.let { push(Screen.Person(it.id)) } ?: run { unknown = e } },
                        onCall = onCall, onFavorite = { push(Screen.Person(it.id)) },
                        onLongEntry = { e -> unknown = e },
                        onFilter = { push(Screen.Filter) },
                        filtered = filteredLog,
                    )
                }
            }
            val items = listOf(
                DockItem(stringResource(R.string.ph_dialpad)) { EuriseIcons.dialpad(it) },
                DockItem(stringResource(R.string.ph_favorites)) { EuriseIcons.star(it) },
                DockItem(stringResource(R.string.ph_recents_tab)) { EuriseIcons.clock(it) },
                DockItem(stringResource(R.string.ph_people_tab)) { EuriseIcons.person(it) },
            )
            EuriseDock(
                items, selected = section, onSelect = { i -> if (i == SEC_DIALPAD) dialpad = "" else section = i },
                hazeState = haze, modifier = Modifier.align(Alignment.BottomCenter),
                actionLabel = stringResource(R.string.ph_note_number), onAction = { push(Screen.Edit(ContactDraft())) },
            )
        }
        Screen.People -> PeopleScreen(contacts, numbers, onBack = { pop() }, onOpen = { push(Screen.Person(it.id)) }, onNew = { push(Screen.Edit(ContactDraft())) }, onCall = onCall)
        is Screen.Person -> PersonScreen(s.id, onBack = ::pop, onCall = onCall, onEdit = { d -> push(Screen.Edit(d)) }, onDeleted = { pop(); reload++ }, onChanged = { reload++ })
        Screen.Filter -> FilterScreen(isDefault, onBecomeDefault, onBack = ::pop, onChanged = { reload++ })
        is Screen.Edit -> EditScreen(s.draft, onBack = ::pop, onSaved = { id ->
            stack = stack.dropLast(1).let { rest -> if (s.draft.id == null && id != null) rest + Screen.Person(id) else rest }
            reload++
        })
    }

    dialpad?.let { start ->
        DialpadSheet(start, contacts, numbers, onDismiss = { dialpad = null }, onCall = { n -> dialpad = null; onCall(n) },
            onAdd = { n -> dialpad = null; push(Screen.Edit(ContactDraft(phones = listOf(n)))) })
    }
    unknown?.let { e ->
        NumberSheet(e, filteredLog.any { PhoneData.same(it.number, e.number) }, onDismiss = { unknown = null }, onCall = { unknown = null; onCall(e.number) },
            onTrust = { eu.eurise.phone.data.SpamFilter.trust(context, e.number); unknown = null; reload++ },
            onAdd = { unknown = null; push(Screen.Edit(ContactDraft(phones = listOf(e.number)))) },
            onBlock = { block -> scope.launch { PhoneData.setBlocked(context, e.number, block); unknown = null; reload++ } },
            onForget = { scope.launch { PhoneData.deleteFromLog(context, e.number); unknown = null; reload++ } })
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Recenti

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    recents: List<CallEntry>, favorites: List<Contact>, permitted: Boolean, isDefault: Boolean,
    onBecomeDefault: () -> Unit, onAskPermissions: () -> Unit, onOpenEntry: (CallEntry) -> Unit, onCall: (String) -> Unit,
    onFavorite: (Contact) -> Unit,
    onLongEntry: (CallEntry) -> Unit = {},
    onFilter: () -> Unit = {},
    filtered: List<eu.eurise.phone.data.SpamFilter.Filtered> = emptyList(),
) {
    val callBack = remember(recents) { PhoneData.toCallBack(recents) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item(key = "head") {
                Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ph_recents_tab), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconCircle(EuriseIcons.shield(), stringResource(R.string.ph_filter_title), onClick = onFilter)
                }
            }
            if (!permitted) item(key = "perm") {
                Banner(stringResource(R.string.ph_need_permissions), stringResource(R.string.ph_allow), onAskPermissions)
            } else if (!isDefault) item(key = "role") {
                Banner(stringResource(R.string.ph_become_default), stringResource(R.string.ph_become_default_ok), onBecomeDefault)
            }
            if (favorites.isNotEmpty()) item(key = "fav") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    items(favorites, key = { it.id }) { c ->
                        Column(Modifier.width(64.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { onFavorite(c) }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Avatar(c.name, c.photo, 52.dp)
                            Text(c.name.substringBefore(' '), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (callBack.isNotEmpty()) {
                item(key = "cb-label") { SectionLabel(stringResource(R.string.ph_call_back), Modifier.padding(top = 6.dp, bottom = 8.dp)) }
                item(key = "cb") {
                    Card {
                        callBack.forEachIndexed { i, e ->
                            if (i > 0) CardDivider()
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onOpenEntry(e) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(e.contact?.name ?: e.number, e.contact?.photo, 40.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(title(e) + if (e.count > 1) " · " + stringResource(R.string.ph_missed_n, e.count) else "", fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(DateUtils.getRelativeTimeSpanString(e.date).toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Pill(stringResource(R.string.ph_call_back_action)) { onCall(e.number) }
                            }
                        }
                    }
                }
            }
            item(key = "rec-label") { SectionLabel(stringResource(R.string.ph_recents), Modifier.padding(top = 16.dp, bottom = 8.dp)) }
            if (recents.isEmpty()) item(key = "empty") {
                Text(stringResource(R.string.ph_no_recents), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            items(recents.take(150), key = { "${it.date}-${it.number}" }) { e ->
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .combinedClickable(onClick = { onOpenEntry(e) }, onLongClick = { onLongEntry(e) }).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (e.blocked) Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x268C2F39)), contentAlignment = Alignment.Center) {
                        Icon(EuriseIcons.block(), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    } else Avatar(e.contact?.name ?: e.number, e.contact?.photo, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(title(e) + if (e.count > 1) " (${e.count})" else "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (e.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        val stopped = filtered.firstOrNull { f -> PhoneData.same(f.number, e.number) && kotlin.math.abs(f.at - e.date) < 120_000L }
                        Text(if (stopped != null) stringResource(R.string.ph_filter_stopped_row, reasonText(stopped.reason)) else detail(e), fontSize = 12.sp,
                            color = if (e.blocked || stopped != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(DateUtils.getRelativeTimeSpanString(e.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconCircle(EuriseIcons.phone(), stringResource(R.string.ph_call)) { onCall(e.number) }
                }
                Spacer(Modifier.height(6.dp))
            }
            item(key = "privacy") {
                Text(stringResource(R.string.ph_privacy_note), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun title(e: CallEntry): String = e.contact?.name ?: e.number.takeIf { it.isNotBlank() }?.let(Contacts::format) ?: stringResource(R.string.ph_private_number)

@Composable
private fun detail(e: CallEntry): String {
    if (e.blocked) return stringResource(R.string.ph_blocked)
    val kind = when (e.type) {
        CallLog.Calls.INCOMING_TYPE -> stringResource(R.string.ph_in)
        CallLog.Calls.OUTGOING_TYPE -> stringResource(R.string.ph_out)
        CallLog.Calls.MISSED_TYPE -> stringResource(R.string.ph_missed)
        CallLog.Calls.REJECTED_TYPE -> stringResource(R.string.ph_rejected)
        CallLog.Calls.BLOCKED_TYPE -> stringResource(R.string.ph_blocked)
        else -> stringResource(R.string.ph_call)
    }
    return if (e.duration > 0) kind + " · " + DateUtils.formatElapsedTime(e.duration) else kind
}

// ---------------------------------------------------------------------------------------------------------------------
// Preferiti (sezione della pillola)

private const val SEC_DIALPAD = 0
private const val SEC_FAVORITES = 1
private const val SEC_RECENTS = 2
private const val SEC_PEOPLE = 3

/** I contatti con la stella, tre per riga: un tocco chiama, tenendo premuto si apre la scheda. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoritesScreen(favorites: List<Contact>, numbers: Map<Long, String>, onOpen: (Contact) -> Unit, onCall: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item(key = "head") {
            Text(stringResource(R.string.ph_favorites), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp))
        }
        if (favorites.isEmpty()) item(key = "none") {
            Text(stringResource(R.string.ph_no_favorites), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        }
        items(favorites.chunked(3), key = { row -> row.first().id }) { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                row.forEach { c ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                            .combinedClickableCompat(onClick = { numbers[c.id]?.let(onCall) ?: onOpen(c) }, onLongClick = { onOpen(c) })
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Avatar(c.name, c.photo, 64.dp)
                        Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (favorites.isNotEmpty()) item(key = "hint") {
            Text(stringResource(R.string.ph_favorites_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Rubrica

@Composable
private fun PeopleScreen(contacts: List<Contact>, numbers: Map<Long, String>, onBack: (() -> Unit)?, onOpen: (Contact) -> Unit, onNew: () -> Unit, onCall: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(contacts, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) contacts else contacts.filter { c ->
            c.name.lowercase().contains(q) || (q.any(Char::isDigit) && numbers[c.id]?.filter(Char::isDigit)?.contains(q.filter(Char::isDigit)) == true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        // Nella pillola (onBack nullo) la Rubrica è una sezione: titolo grande, il «+» è quello accanto alla pillola.
        if (onBack != null) TopBar(stringResource(R.string.ph_contacts), onBack) { IconCircle(EuriseIcons.plus(), stringResource(R.string.ph_new_contact), onClick = onNew) }
        else Text(stringResource(R.string.ph_people_tab), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp))
        OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.ph_search_contacts)) }, singleLine = true,
            leadingIcon = { Icon(EuriseIcons.search(), null, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (onBack == null) 110.dp else 40.dp)) {
            var last = ""
            shown.forEach { c ->
                val letter = Contacts.letter(c.name)
                if (letter != last && query.isBlank()) {
                    last = letter
                    item(key = "l-$letter") { SectionLabel(letter, Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                }
                item(key = c.id) {
                    Row(Modifier.fillMaxWidth().clickable { onOpen(c) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(c.name, c.photo, 40.dp)
                        Text(c.name, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        numbers[c.id]?.let { n -> IconCircle(EuriseIcons.phone(), stringResource(R.string.ph_call)) { onCall(n) } }
                    }
                }
            }
            if (shown.isEmpty()) item(key = "none") {
                Text(stringResource(R.string.ph_no_contacts), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonScreen(id: Long, onBack: () -> Unit, onCall: (String) -> Unit, onEdit: (ContactDraft) -> Unit, onDeleted: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contact by remember { mutableStateOf<Contact?>(null) }
    var version by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var engagements by remember { mutableStateOf<List<eu.eurise.phone.data.Engagement>>(emptyList()) }
    LaunchedEffect(id, version) {
        contact = Contacts.detail(context, id)
        engagements = contact?.let { PhoneData.engagements(context, it.name, it.emails.map { e -> e.value }) }.orEmpty()
    }
    val c = contact ?: run { Column(Modifier.fillMaxSize()) { TopBar("", onBack) {} }; return }
    val number = c.phones.firstOrNull()?.value
    fun copy(text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(c.name, text))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item(key = "top") {
            TopBar("", onBack) {
                IconCircle(EuriseIcons.star(), stringResource(if (c.starred) R.string.ph_unstar else R.string.ph_star), tint = if (c.starred) Color(0xFFB8913F) else null) {
                    scope.launch { Contacts.setStarred(context, c.id, !c.starred); version++; onChanged() }
                }
                IconCircle(EuriseIcons.pen(), stringResource(R.string.ph_edit)) { scope.launch { Contacts.draft(context, c.id)?.let(onEdit) } }
            }
        }
        item(key = "who") {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Avatar(c.name, c.photo, 88.dp)
                Text(c.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                c.company?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item(key = "actions") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (number != null) {
                    Action(EuriseIcons.phone(), stringResource(R.string.ph_call), Modifier.weight(1f)) { onCall(number) }
                    Action(EuriseIcons.message(), stringResource(R.string.ph_sms), Modifier.weight(1f)) { Reach.open(context, Reach.sms(number)) }
                    Reach.whatsapp(context, number)?.let { i -> Action(EuriseIcons.chat(), "WhatsApp", Modifier.weight(1f)) { Reach.open(context, i) } }
                    Reach.whatsappBusiness(context, number)?.let { i -> Action(EuriseIcons.chat(), "WA Business", Modifier.weight(1f)) { Reach.open(context, i) } }
                    Reach.telegram(context, number)?.let { i -> Action(EuriseIcons.send(), "Telegram", Modifier.weight(1f)) { Reach.open(context, i) } }
                }
                c.emails.firstOrNull()?.let { e -> Action(EuriseIcons.mail(), stringResource(R.string.ph_email), Modifier.weight(1f)) { Reach.open(context, Reach.email(e.value)) } }
            }
        }
        if (engagements.isNotEmpty()) {
            item(key = "eng-label") { SectionLabel(stringResource(R.string.ph_together), Modifier.padding(top = 8.dp, bottom = 8.dp)) }
            item(key = "eng") {
                Card {
                    engagements.forEachIndexed { i, e ->
                        if (i > 0) CardDivider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(EuriseIcons.calendar(), null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Text(e.title + " · " + e.start.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", java.util.Locale.ITALIAN)), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        if (c.phones.isNotEmpty() || c.emails.isNotEmpty() || c.addresses.isNotEmpty()) {
            item(key = "info-label") { SectionLabel(stringResource(R.string.ph_details), Modifier.padding(top = 12.dp, bottom = 8.dp)) }
            item(key = "info") {
                Card {
                    val rows = c.phones.map { Triple(it, EuriseIcons.phone(), { onCall(it.value) }) } +
                        c.emails.map { Triple(it, EuriseIcons.mail(), { Reach.open(context, Reach.email(it.value)); Unit }) } +
                        c.addresses.map { Triple(it, EuriseIcons.marker(), { Reach.open(context, Reach.map(it.value)); Unit }) }
                    rows.forEachIndexed { i, (v, icon, action) ->
                        if (i > 0) CardDivider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).combinedClickable(onClick = action, onLongClick = { copy(v.value) }),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(v.value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(v.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        c.note?.let { n ->
            item(key = "note") {
                SectionLabel(stringResource(R.string.ph_note), Modifier.padding(top = 12.dp, bottom = 8.dp))
                Card { Text(n, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp, horizontal = 2.dp)) }
            }
        }
        item(key = "more") {
            Column(Modifier.padding(top = 18.dp)) {
                number?.let { n ->
                    TextButton(onClick = { scope.launch { PhoneData.setBlocked(context, n, true) } }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.ph_block_number), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.ph_delete_contact), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.ph_delete_title, c.name)) },
        text = { Text(stringResource(R.string.ph_delete_text)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { Contacts.delete(context, c.id); onDeleted() } }) { Text(stringResource(R.string.ph_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.ph_cancel)) } },
    )
}

@Composable
private fun EditScreen(start: ContactDraft, onBack: () -> Unit, onSaved: (Long?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember(start) { mutableStateOf(start) }
    var error by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "top") {
            TopBar(stringResource(if (start.id == null) R.string.ph_new_contact else R.string.ph_edit_contact), onBack) {
                Pill(stringResource(R.string.ph_save)) {
                    if (d.name.isBlank() && d.phones.all { it.isBlank() }) return@Pill
                    scope.launch {
                        val id = runCatching { Contacts.save(context, d.copy(name = d.name.ifBlank { d.phones.first { it.isNotBlank() } })) }.getOrNull()
                        if (id == null) error = true else onSaved(id)
                    }
                }
            }
        }
        item(key = "name") { Field(stringResource(R.string.ph_f_name), d.name) { d = d.copy(name = it) } }
        d.phones.forEachIndexed { i, p ->
            item(key = "p$i") { Field(stringResource(R.string.ph_f_phone), p, phone = true) { v -> d = d.copy(phones = d.phones.toMutableList().also { it[i] = v }) } }
        }
        item(key = "addp") { TextButton(onClick = { d = d.copy(phones = d.phones + "") }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.ph_add_phone)) } }
        d.emails.forEachIndexed { i, e ->
            item(key = "e$i") { Field(stringResource(R.string.ph_f_email), e) { v -> d = d.copy(emails = d.emails.toMutableList().also { it[i] = v }) } }
        }
        item(key = "adde") { TextButton(onClick = { d = d.copy(emails = d.emails + "") }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.ph_add_email)) } }
        item(key = "co") { Field(stringResource(R.string.ph_f_company), d.company) { d = d.copy(company = it) } }
        item(key = "ad") { Field(stringResource(R.string.ph_f_address), d.address) { d = d.copy(address = it) } }
        item(key = "no") { Field(stringResource(R.string.ph_f_note), d.note) { d = d.copy(note = it) } }
        if (error) item(key = "err") { Text(stringResource(R.string.ph_save_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
        item(key = "where") {
            Text(stringResource(R.string.ph_where_saved), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, phone: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(16.dp),
        keyboardOptions = if (phone) androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
        else androidx.compose.foundation.text.KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

// ---------------------------------------------------------------------------------------------------------------------
// Tastierino e numero sconosciuto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialpadSheet(start: String, contacts: List<Contact>, numbers: Map<Long, String>, onDismiss: () -> Unit, onCall: (String) -> Unit, onAdd: (String) -> Unit) {
    var typed by remember(start) { mutableStateOf(start) }
    val matches = remember(typed, contacts, numbers) {
        val digits = typed.filter(Char::isDigit)
        if (digits.length < 2) emptyList() else contacts.filter { c -> numbers[c.id]?.filter(Char::isDigit)?.contains(digits) == true || t9(c.name).contains(digits) }.take(3)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            matches.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { numbers[c.id]?.let(onCall) }.padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(c.name, c.photo, 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(numbers[c.id].orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(typed, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                if (typed.isNotEmpty()) IconCircle(EuriseIcons.backspace(), stringResource(R.string.ph_delete_digit)) { typed = typed.dropLast(1) }
            }
            if (typed.length >= 3 && matches.isEmpty()) TextButton(onClick = { onAdd(typed) }) { Text(stringResource(R.string.ph_add_to_contacts)) }
            val rows = listOf("123", "456", "789", "*0#")
            val letters = mapOf('2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+")
            Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            Column(
                                Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .combinedClickableCompat(onClick = { typed += k }, onLongClick = { if (k == '0') typed += '+' else typed += k }),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text(k.toString(), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                                letters[k]?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.size(68.dp).clip(CircleShape).background(Color(0xFF2F7D4F)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.ph_call)) { if (typed.isNotBlank()) onCall(typed) },
                contentAlignment = Alignment.Center) { Icon(EuriseIcons.phone(), stringResource(R.string.ph_call), tint = Color.White, modifier = Modifier.size(28.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit) = combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** Il nome scritto con i tasti del telefono («Anna» → «2662»), per cercare dal tastierino. */
private fun t9(name: String): String = name.lowercase().map { ch ->
    when (ch) {
        in "abcàá" -> '2'; in "defèé" -> '3'; in "ghiìí" -> '4'; in "jkl" -> '5'; in "mnoòó" -> '6'
        in "pqrs" -> '7'; in "tuvùú" -> '8'; in "wxyz" -> '9'; else -> ' '
    }
}.joinToString("").replace(" ", "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberSheet(e: CallEntry, wasFiltered: Boolean, onDismiss: () -> Unit, onCall: () -> Unit, onTrust: () -> Unit, onAdd: () -> Unit, onBlock: (Boolean) -> Unit, onForget: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(e.contact?.name ?: e.number.takeIf { it.isNotBlank() }?.let(Contacts::format) ?: stringResource(R.string.ph_private_number), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (e.number.isNotBlank()) {
                SheetRow(EuriseIcons.phone(), stringResource(R.string.ph_call), onCall)
                SheetRow(EuriseIcons.message(), stringResource(R.string.ph_sms)) { Reach.open(context, Reach.sms(e.number)) }
                Reach.whatsapp(context, e.number)?.let { i -> SheetRow(EuriseIcons.chat(), "WhatsApp") { Reach.open(context, i) } }
                Reach.whatsappBusiness(context, e.number)?.let { i -> SheetRow(EuriseIcons.chat(), "WhatsApp Business") { Reach.open(context, i) } }
                if (e.contact == null) SheetRow(EuriseIcons.person(), stringResource(R.string.ph_add_to_contacts), onAdd)
                if (wasFiltered) SheetRow(EuriseIcons.check(), stringResource(R.string.ph_filter_trust_long), onTrust)
                SheetRow(EuriseIcons.block(), stringResource(if (e.blocked) R.string.ph_unblock_number else R.string.ph_block_number)) { onBlock(!e.blocked) }
                SheetRow(EuriseIcons.trash(), stringResource(R.string.ph_forget_calls), onForget)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Pezzi comuni

@Composable
private fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconCircle(EuriseIcons.chevronLeft(), stringResource(R.string.ph_back), onClick = onBack)
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp)) { content() }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
        Pill(action, onClick)
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp))
}

@Composable
private fun IconCircle(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun BigButton(icon: ImageVector, label: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Row(modifier.height(56.dp).clip(RoundedCornerShape(22.dp)).background(bg).clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(68.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.secondaryContainer).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

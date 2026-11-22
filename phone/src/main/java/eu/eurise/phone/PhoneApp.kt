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
    data object Recents : StartAt
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
    // La ricerca in rubrica è la stessa in Preferiti, Recenti e Rubrica: resta scritta passando da una all'altra.
    var query by remember { mutableStateOf("") }
    val haze = remember { HazeState() }

    var filteredLog by remember { mutableStateOf<List<eu.eurise.phone.data.SpamFilter.Filtered>>(emptyList()) }
    LaunchedEffect(refresh, reload) {
        filteredLog = eu.eurise.phone.data.SpamFilter.filtered(context)
        recents = PhoneData.recents(context)
        numbers = Contacts.firstNumbers(context)
        contacts = withoutEmptyTwins(Contacts.all(context), numbers)
    }
    LaunchedEffect(dialNumber) { if (dialNumber != null) { dialpad = dialNumber; onDialConsumed() } }
    LaunchedEffect(start) {
        when (start) {
            StartAt.People -> { stack = listOf(Screen.Home); section = SEC_PEOPLE }
            StartAt.Recents -> { stack = listOf(Screen.Home); section = SEC_RECENTS }
            is StartAt.Insert -> { stack = listOf(Screen.Home, Screen.Edit(start.draft)); section = SEC_PEOPLE }
            null -> return@LaunchedEffect
        }
        onStartConsumed()
    }

    fun push(s: Screen) { stack = stack + s }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(stack.size > 1) { pop() }
    BackHandler(stack.size == 1 && query.isNotBlank()) { query = "" }
    val search = SearchState(query, { query = it }, contacts, numbers, onOpen = { push(Screen.Person(it.id)) }, onCall = onCall)

    val permitted = PhoneData.has(context, android.Manifest.permission.READ_CALL_LOG) && Contacts.canRead(context)

    when (val s = stack.last()) {
        Screen.Home -> Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                when (section) {
                    SEC_FAVORITES -> FavoritesScreen(contacts.filter { it.starred }, numbers, search, onOpen = { push(Screen.Person(it.id)) }, onCall = onCall)
                    SEC_PEOPLE -> PeopleScreen(contacts, numbers, onBack = null, onOpen = { push(Screen.Person(it.id)) }, onNew = { push(Screen.Edit(ContactDraft())) }, onCall = onCall, search = search)
                    else -> HomeScreen(
                        recents = recents, favorites = contacts.filter { it.starred }, permitted = permitted, isDefault = isDefault,
                        onBecomeDefault = onBecomeDefault, onAskPermissions = onAskPermissions,
                        onOpenEntry = { e -> e.contact?.let { push(Screen.Person(it.id)) } ?: run { unknown = e } },
                        onCall = onCall, onFavorite = { push(Screen.Person(it.id)) },
                        onLongEntry = { e -> unknown = e },
                        onFilter = { push(Screen.Filter) },
                        filtered = filteredLog,
                        search = search,
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
    search: SearchState,
) {
    val callBack = remember(recents) { PhoneData.toCallBack(recents) }
    val context = LocalContext.current
    // Gli avvisi per i numeri fuori dalla rubrica (elenco Eurise, call center, prefissi segnati), letti fuori dal thread principale.
    var warnings by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(recents) {
        warnings = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            recents.filter { it.contact == null && it.number.isNotBlank() }.map { it.number }.distinct().take(200)
                .mapNotNull { n -> eu.eurise.phone.data.SpamListClient.warning(context, n)?.let { n to it } }.toMap()
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item(key = "head") {
                Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ph_recents_tab), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconCircle(EuriseIcons.shield(), stringResource(R.string.ph_filter_title), onClick = onFilter)
                }
            }
            item(key = "search") { SearchBox(search) }
            if (search.active) {
                searchResults(search)
                return@LazyColumn
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
                        val suspect = if (e.contact == null) warnings[e.number] else null
                        Text(
                            when {
                                stopped != null -> stringResource(R.string.ph_filter_stopped_row, reasonText(stopped.reason))
                                suspect != null -> suspect + " · " + detail(e)
                                else -> detail(e)
                            },
                            fontSize = 12.sp, maxLines = 1,
                            color = if (e.blocked || stopped != null || suspect != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
private fun FavoritesScreen(favorites: List<Contact>, numbers: Map<Long, String>, search: SearchState, onOpen: (Contact) -> Unit, onCall: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item(key = "head") {
            Text(stringResource(R.string.ph_favorites), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp))
        }
        item(key = "search") { SearchBox(search) }
        if (search.active) {
            searchResults(search)
            return@LazyColumn
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
private fun PeopleScreen(contacts: List<Contact>, numbers: Map<Long, String>, onBack: (() -> Unit)?, onOpen: (Contact) -> Unit, onNew: () -> Unit, onCall: (String) -> Unit, search: SearchState? = null) {
    var own by remember { mutableStateOf("") }
    val query = search?.query ?: own
    val shown = remember(contacts, numbers, query) { matchContacts(contacts, numbers, query) }
    Column(Modifier.fillMaxSize()) {
        // Nella pillola (onBack nullo) la Rubrica è una sezione: titolo grande, il «+» è quello accanto alla pillola.
        if (onBack != null) TopBar(stringResource(R.string.ph_contacts), onBack) { IconCircle(EuriseIcons.plus(), stringResource(R.string.ph_new_contact), onClick = onNew) }
        else Text(stringResource(R.string.ph_people_tab), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp))
        OutlinedTextField(query, { v -> if (search != null) search.onQuery(v) else own = v }, placeholder = { Text(stringResource(R.string.ph_search_contacts)) }, singleLine = true,
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
    // «Invita sulla Chat» (tavola ChatInvita): solo se la Chat è collegata e il contatto non ci è già.
    var invite by remember { mutableStateOf(false) }
    LaunchedEffect(id, version) {
        contact = Contacts.detail(context, id)
        engagements = contact?.let { PhoneData.engagements(context, it.name, it.emails.map { e -> e.value }) }.orEmpty()
        invite = contact?.let { c ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                (c.phones.isNotEmpty() || c.emails.isNotEmpty()) && eu.eurise.core.suite.PeopleContract.chatUser(context) != null &&
                    eu.eurise.core.contacts.PeopleLookup.forContact(context, c.id)?.matrix.isNullOrEmpty() &&
                    !eu.eurise.core.suite.PeopleContract.chatKnows(context, c.lookupKey)
            }
        } ?: false
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
        // Persone e ruoli (tavola InsRuoli): chi è per te; Chat ed Email lo usano per le proposte.
        item(key = "roles") {
            val matrix by androidx.compose.runtime.produceState(emptyList<String>(), c.id) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { eu.eurise.core.contacts.PeopleLookup.forContact(context, c.id)?.matrix.orEmpty() }
            }
            RoleSection(c.lookupKey, c.name, c.phones.map { it.value } + c.emails.map { it.value } + matrix)
        }
        if (invite) item(key = "invite") {
            Banner(stringResource(R.string.ph_invite_text, c.name.substringBefore(' ')), stringResource(R.string.ph_invite)) {
                eu.eurise.core.suite.PeopleContract.inviteIntent(context, c.lookupKey, c.name, c.phones.map { it.value }, c.emails.map { it.value })
                    ?.let { i -> runCatching { context.startActivity(i) } }
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

/**
 * Il tastierino. Il numero sta in un campo vero: tenendolo premuto si incolla (anche con «Incolla» accanto), ma la
 * tastiera del telefono non si apre. Dalle tre cifre c'è sempre «Aggiungi in rubrica» (prima spariva quando le cifre
 * somigliavano a un nome). I suggerimenti hanno uno spazio fisso: il tasto verde non scende più fuori dallo schermo.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun DialpadSheet(start: String, contacts: List<Contact>, numbers: Map<Long, String>, onDismiss: () -> Unit, onCall: (String) -> Unit, onAdd: (String) -> Unit) {
    val context = LocalContext.current
    var field by remember(start) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(start, androidx.compose.ui.text.TextRange(start.length))) }
    val typed = field.text
    fun set(text: String) { field = androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length)) }
    /** Inserisce dove sta il cursore (o al posto della parte selezionata). */
    fun insert(text: String) {
        val sel = field.selection
        val t = field.text.substring(0, sel.min) + text + field.text.substring(sel.max)
        field = androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(sel.min + text.length))
    }
    fun backspace() {
        val sel = field.selection
        if (!sel.collapsed) { insert(""); return }
        if (sel.start == 0) return
        val t = field.text.removeRange(sel.start - 1, sel.start)
        field = androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(sel.start - 1))
    }
    val clip = remember { context.getSystemService(ClipboardManager::class.java) }
    var clipNumber by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        clipNumber = runCatching { clip.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
            ?.let(::phoneIn)
    }
    val matches = remember(typed, contacts, numbers) {
        val digits = typed.filter(Char::isDigit)
        if (digits.length < 2) emptyList() else contacts.filter { c -> numbers[c.id]?.filter(Char::isDigit)?.contains(digits) == true || t9(c.name).contains(digits) }.take(2)
    }
    val known = remember(typed, numbers) { typed.length >= 3 && numbers.values.any { PhoneData.same(it, typed) } }
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Spazio fisso per due suggerimenti: comparendo non spingono giù il tastierino.
            Column(Modifier.fillMaxWidth().height(92.dp), verticalArrangement = Arrangement.Bottom) {
                matches.forEach { c ->
                    Row(Modifier.fillMaxWidth().height(46.dp).clickable { numbers[c.id]?.let(onCall) }.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(c.name, c.photo, 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(numbers[c.id].orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (typed.isEmpty()) Text(stringResource(R.string.ph_dial_hint), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Niente tastiera del telefono: il campo serve per il cursore e per incollare tenendo premuto.
                    androidx.compose.ui.platform.InterceptPlatformTextInput(interceptor = { _, _ -> kotlinx.coroutines.awaitCancellation() }) {
                        androidx.compose.foundation.text.BasicTextField(
                            field, { v -> field = v.copy(text = v.text.filter { it.isDigit() || it in "+*#,; " }) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (typed.isNotEmpty()) {
                    Box(Modifier.size(44.dp).clip(CircleShape).combinedClickableCompat(onClick = ::backspace, onLongClick = { set("") }), contentAlignment = Alignment.Center) {
                        Icon(EuriseIcons.backspace(), stringResource(R.string.ph_delete_digit), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                }
            }
            // Sotto il numero: «Incolla» quando negli appunti c'è un numero, «Aggiungi in rubrica» dalle tre cifre.
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val pasted = clipNumber
                if (pasted != null && typed != pasted) Chip(EuriseIcons.copy(), stringResource(R.string.ph_paste)) { set(pasted) }
                if (typed.length >= 3 && !known) Chip(EuriseIcons.person(), stringResource(R.string.ph_add_to_contacts)) { onAdd(typed) }
            }
            val rows = listOf("123", "456", "789", "*0#")
            val letters = mapOf('2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+")
            Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            Column(
                                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .combinedClickableCompat(onClick = { insert(k.toString()) }, onLongClick = { insert(if (k == '0') "+" else k.toString()) }),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text(k.toString(), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                                letters[k]?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(68.dp).clip(CircleShape).background(Color(0xFF2F7D4F)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.ph_call)) { if (typed.isNotBlank()) onCall(typed) },
                contentAlignment = Alignment.Center) { Icon(EuriseIcons.phone(), stringResource(R.string.ph_call), tint = Color.White, modifier = Modifier.size(28.dp)) }
        }
    }
}

/** Un numero di telefono dentro un testo copiato (anche «Chiamami al 333 123 4567»), o null. */
internal fun phoneIn(text: String): String? =
    Regex("""\+?\d[\d\s./-]{5,}\d""").find(text)?.value?.filter { it.isDigit() || it == '+' }?.takeIf { it.count(Char::isDigit) in 6..15 }

@Composable
private fun Chip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.secondaryContainer).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(15.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
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

/**
 * Toglie i doppioni vuoti: una voce senza numero quando la stessa persona (stesso nome) c'è già con un numero. Succede
 * con le voci che WhatsApp, Telegram o Signal aggiungono alla rubrica e che Android non unisce alla persona: si vedevano
 * contatti senza cornetta né chat. Chi non ha un numero e non ha doppioni resta (può avere un'email).
 */
private fun withoutEmptyTwins(contacts: List<Contact>, numbers: Map<Long, String>): List<Contact> {
    val withNumber = contacts.filter { it.id in numbers }.map { it.name.trim().lowercase() }.toHashSet()
    return contacts.filter { it.id in numbers || it.name.trim().lowercase() !in withNumber }
}

// ---------------------------------------------------------------------------------------------------------------------
// Ricerca in rubrica (in Preferiti, Recenti e Rubrica)

/** La ricerca in rubrica condivisa dalle sezioni della pillola. */
private class SearchState(
    val query: String,
    val onQuery: (String) -> Unit,
    val contacts: List<Contact>,
    val numbers: Map<Long, String>,
    val onOpen: (Contact) -> Unit,
    val onCall: (String) -> Unit,
) {
    val active get() = query.isNotBlank()
}

/** Nome (anche una parte) o cifre del numero; con un numero scritto per intero vale anche «+39 333…» contro «333…». */
private fun matchContacts(contacts: List<Contact>, numbers: Map<Long, String>, query: String): List<Contact> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return contacts
    val digits = q.filter(Char::isDigit)
    return contacts.filter { c ->
        c.name.lowercase().contains(q) || (digits.length >= 2 && numbers[c.id]?.let { n -> n.filter(Char::isDigit).contains(digits) || PhoneData.same(n, q) } == true)
    }
}

@Composable
private fun SearchBox(search: SearchState) {
    OutlinedTextField(search.query, search.onQuery, placeholder = { Text(stringResource(R.string.ph_search_all)) }, singleLine = true,
        leadingIcon = { Icon(EuriseIcons.search(), null, modifier = Modifier.size(18.dp)) },
        trailingIcon = if (search.active) ({
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.ph_cancel)) { search.onQuery("") }, contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.close(), stringResource(R.string.ph_cancel), modifier = Modifier.size(16.dp))
            }
        }) else null,
        shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
}

/** I contatti trovati: tocco = scheda, cornetta = chiama. */
private fun androidx.compose.foundation.lazy.LazyListScope.searchResults(search: SearchState) {
    val found = matchContacts(search.contacts, search.numbers, search.query).take(60)
    if (found.isEmpty()) item(key = "s-none") {
        Text(stringResource(R.string.ph_search_none), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
    }
    items(found, key = { "s-${it.id}" }) { c ->
        Row(Modifier.fillMaxWidth().clickable { search.onOpen(c) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(c.name, c.photo, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(c.name, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                search.numbers[c.id]?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            search.numbers[c.id]?.let { n -> IconCircle(EuriseIcons.phone(), stringResource(R.string.ph_call)) { search.onCall(n) } }
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

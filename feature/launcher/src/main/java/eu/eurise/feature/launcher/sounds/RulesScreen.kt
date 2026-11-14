package eu.eurise.feature.launcher.sounds

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.contacts.Contact
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.notifications.NotificationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AppChoice(val pkg: String, val label: String)

/** Chi suona come (tavola SuoRegole): persone e app con i loro suoni; vince la regola più precisa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RulesScreen(newPersonSound: String?, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SoundStore.settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { SoundStore.get(context) }
    var titles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) { titles = SoundLibrary.all(context).associate { it.id to it.title } }
    fun title(id: String?) = when (id) {
        null -> null
        SoundIds.SILENT -> context.getString(R.string.rules_silent)
        else -> titles[id] ?: id.substringAfter(':')
    }
    var appSounds by remember { mutableStateOf(SoundStore.appSounds(context)) }
    val listening by NotificationStore.connected.collectAsStateWithLifecycle()

    var pickContact by remember { mutableStateOf(newPersonSound != null) }
    var editPerson by remember { mutableStateOf<PersonRule?>(null) }
    var pickApp by remember { mutableStateOf(false) }
    var editApp by remember { mutableStateOf<AppRule?>(null) }
    var contactsOk by remember { mutableStateOf(Contacts.canRead(context)) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        contactsOk = r[Manifest.permission.READ_CONTACTS] == true || Contacts.canRead(context)
        if (contactsOk) pickContact = true
    }
    val addPerson: () -> Unit = {
        if (Contacts.canRead(context) && Contacts.canWrite(context)) pickContact = true
        else contactsPermission.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
    }
    LaunchedEffect(newPersonSound) {
        if (newPersonSound != null && !(Contacts.canRead(context) && Contacts.canWrite(context))) {
            pickContact = false
            contactsPermission.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SndHeader(stringResource(R.string.snd_rules), stringResource(R.string.rules_hint), onClose)

        SndSection(stringResource(R.string.rules_people), action = { AddButton(stringResource(R.string.rules_add_person), addPerson) }) {
            if (settings.persons.isEmpty()) EmptyRow(stringResource(R.string.rules_empty_people))
            settings.persons.forEachIndexed { i, p ->
                if (i > 0) SndDivider()
                Row(Modifier.fillMaxWidth().clickable { editPerson = p }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Text(Contacts.initials(p.name), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val all = p.sameForAll
                            if (all != null) SndChip(stringResource(R.string.rules_chip, stringResource(R.string.rules_all), title(all)!!))
                            else {
                                p.calls?.let { SndChip(stringResource(R.string.rules_chip, stringResource(R.string.rules_calls), title(it)!!)) }
                                p.messages?.let { SndChip(stringResource(R.string.rules_chip, stringResource(R.string.rules_messages), title(it)!!)) }
                                p.email?.let { SndChip(stringResource(R.string.rules_chip, stringResource(R.string.rules_email), title(it)!!)) }
                            }
                            if (p.breakDnd) SndChip(stringResource(R.string.rules_break_dnd_chip), strong = false)
                        }
                    }
                }
            }
        }

        SndSection(stringResource(R.string.rules_apps), action = { AddButton(stringResource(R.string.rules_add_app)) { pickApp = true } }) {
            if (settings.apps.isEmpty()) EmptyRow(stringResource(R.string.rules_empty_apps))
            settings.apps.forEachIndexed { i, a ->
                if (i > 0) SndDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable { editApp = a }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        val parts = listOfNotNull(
                            title(a.sound),
                            a.groups?.let { stringResource(R.string.rules_groups, title(it)!!) },
                            if (a.fromHour != null && a.toHour != null) stringResource(R.string.rules_hours, a.fromHour, a.toHour) else null,
                        )
                        Text(parts.joinToString(" · "), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SndChip(stringResource(if (a.pkg in appSounds) R.string.rules_by_app else R.string.rules_by_eurise), strong = a.pkg !in appSounds)
                }
            }
        }

        // Le app che non sono di Eurise: Eurise suona al posto loro, una volta tolto il loro suono.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.rules_info), fontSize = 13.sp, lineHeight = 19.sp)
            if (!listening) {
                Text(stringResource(R.string.rules_needs_listener), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                ActionButton(stringResource(R.string.rules_grant_listener)) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            settings.apps.filter { it.pkg in appSounds }.forEach { a ->
                ActionButton(stringResource(R.string.rules_mute_app, a.label)) {
                    openChannel(context, a.pkg, appSounds[a.pkg])
                    appSounds = SoundStore.appSounds(context)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (pickContact) ContactPicker(onDismiss = { pickContact = false }) { c ->
        pickContact = false
        scope.launch {
            val d = Contacts.detail(context, c.id) ?: c
            val old = settings.persons.firstOrNull { it.key == d.lookupKey }
            editPerson = (old ?: PersonRule(d.lookupKey, d.name)).copy(
                name = d.name, phones = d.phones.map { it.value }, emails = d.emails.map { it.value },
                calls = newPersonSound ?: old?.calls,
            )
        }
    }
    editPerson?.let { p ->
        PersonEditor(p, title = ::title, onDismiss = { editPerson = null }, onRemove = {
            editPerson = null
            SoundStore.update(context) { s -> s.copy(persons = s.persons.filterNot { it.key == p.key }) }
            scope.launch { SoundLibrary.setContactRingtone(context, p.key, null) }
        }) { next ->
            editPerson = null
            SoundStore.update(context) { s -> s.copy(persons = s.persons.filterNot { it.key == next.key } + next) }
            scope.launch { SoundLibrary.setContactRingtone(context, next.key, next.calls?.takeIf { it != SoundIds.SILENT }) }
        }
    }
    if (pickApp) AppPicker(onDismiss = { pickApp = false }) { a ->
        pickApp = false
        editApp = settings.apps.firstOrNull { it.pkg == a.pkg } ?: AppRule(a.pkg, a.label, "a:gocce")
    }
    editApp?.let { a ->
        AppEditor(a, title = ::title, onDismiss = { editApp = null }, onRemove = {
            editApp = null
            SoundStore.update(context) { s -> s.copy(apps = s.apps.filterNot { it.pkg == a.pkg }) }
        }) { next ->
            editApp = null
            SoundStore.update(context) { s -> s.copy(apps = s.apps.filterNot { it.pkg == next.pkg } + next) }
        }
    }
}

/** Le impostazioni di notifica del canale (o dell'app) che suona ancora per conto suo. */
private fun openChannel(context: android.content.Context, pkg: String, channel: String?) {
    val byChannel = channel?.let {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg).putExtra(Settings.EXTRA_CHANNEL_ID, it)
    }
    val byApp = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
    if (byChannel == null || runCatching { context.startActivity(byChannel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isFailure) {
        runCatching { context.startActivity(byApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun EmptyRow(text: String) {
    Text(text, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 14.dp))
}

@Composable
private fun SoundLine(label: String, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value ?: stringResource(R.string.rules_same_as_general), fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
            color = if (value != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(EuriseIcons.chevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp).size(14.dp))
    }
}

@Composable
private fun PersonEditor(p: PersonRule, title: (String?) -> String?, onDismiss: () -> Unit, onRemove: () -> Unit, onSave: (PersonRule) -> Unit) {
    var draft by remember { mutableStateOf(p) }
    var picking by remember { mutableStateOf<SoundUse?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.name) },
        text = {
            Column {
                SoundLine(stringResource(R.string.rules_calls), title(draft.calls)) { picking = SoundUse.Calls }
                SndDivider()
                SoundLine(stringResource(R.string.rules_messages), title(draft.messages)) { picking = SoundUse.Messages }
                SndDivider()
                SoundLine(stringResource(R.string.rules_email), title(draft.email)) { picking = SoundUse.Email }
                SndDivider()
                SndSwitchRow(stringResource(R.string.rules_break_dnd), null, draft.breakDnd) { draft = draft.copy(breakDnd = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.rules_save)) } },
        dismissButton = { TextButton(onClick = onRemove) { Text(stringResource(R.string.rules_remove)) } },
    )
    picking?.let { use ->
        SoundPickerDialog(draft.soundFor(use), short = use != SoundUse.Calls, allowGeneral = true, onDismiss = { picking = null }) { id ->
            draft = when (use) {
                SoundUse.Calls -> draft.copy(calls = id)
                SoundUse.Messages -> draft.copy(messages = id)
                else -> draft.copy(email = id)
            }
            picking = null
        }
    }
}

@Composable
private fun AppEditor(a: AppRule, title: (String?) -> String?, onDismiss: () -> Unit, onRemove: () -> Unit, onSave: (AppRule) -> Unit) {
    var draft by remember { mutableStateOf(a) }
    var picking by remember { mutableIntStateOf(0) } // 1 suono, 2 gruppi
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(a.label) },
        text = {
            Column {
                SoundLine(stringResource(R.string.rules_pick_sound), title(draft.sound)) { picking = 1 }
                SndDivider()
                SoundLine(stringResource(R.string.rules_group_sound), draft.groups?.let(title) ?: stringResource(R.string.rules_same_sound)) { picking = 2 }
                SndDivider()
                val hours = draft.fromHour != null && draft.toHour != null
                SndSwitchRow(stringResource(R.string.rules_only_hours), null, hours) { on ->
                    draft = if (on) draft.copy(fromHour = 9, toHour = 18) else draft.copy(fromHour = null, toHour = null)
                }
                if (hours) {
                    HourStepper(stringResource(R.string.rules_from), draft.fromHour!!) { draft = draft.copy(fromHour = it) }
                    HourStepper(stringResource(R.string.rules_until), draft.toHour!!) { draft = draft.copy(toHour = it) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.rules_save)) } },
        dismissButton = { TextButton(onClick = onRemove) { Text(stringResource(R.string.rules_remove)) } },
    )
    when (picking) {
        1 -> SoundPickerDialog(draft.sound, short = true, allowGeneral = false, onDismiss = { picking = 0 }) { id ->
            draft = draft.copy(sound = id ?: draft.sound); picking = 0
        }
        2 -> SoundPickerDialog(draft.groups, short = true, allowGeneral = true, onDismiss = { picking = 0 }) { id ->
            draft = draft.copy(groups = id); picking = 0
        }
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Box(Modifier.size(44.dp).clip(CircleShape).clickable { onChange((hour + 23) % 24) }, contentAlignment = Alignment.Center) { Icon(EuriseIcons.minus(), null, Modifier.size(16.dp)) }
        Text("%02d:00".format(hour), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.size(44.dp).clip(CircleShape).clickable { onChange((hour + 1) % 24) }, contentAlignment = Alignment.Center) { Icon(EuriseIcons.plus(), null, Modifier.size(16.dp)) }
    }
}

@Composable
private fun ContactPicker(onDismiss: () -> Unit, onPick: (Contact) -> Unit) {
    val context = LocalContext.current
    var all by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { all = Contacts.all(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_pick_contact)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!Contacts.canRead(context)) Text(stringResource(R.string.rules_contacts_needed))
                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(stringResource(R.string.rules_search)) }, modifier = Modifier.fillMaxWidth())
                val q = SoundDecider.norm(query)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(all.filter { q.isEmpty() || SoundDecider.norm(it.name).contains(q) }, key = { it.id }) { c ->
                        Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.snd_cancel)) } },
    )
}

@Composable
private fun AppPicker(onDismiss: () -> Unit, onPick: (AppChoice) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppChoice>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                .map { AppChoice(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != context.packageName }
                .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_pick_app)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(stringResource(R.string.rules_search)) }, modifier = Modifier.fillMaxWidth())
                val q = SoundDecider.norm(query)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(apps.filter { q.isEmpty() || SoundDecider.norm(it.label).contains(q) }, key = { it.pkg }) { a ->
                        Text(a.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(a) }.padding(vertical = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.snd_cancel)) } },
    )
}

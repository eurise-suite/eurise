package eu.eurise.password.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.component.GroupCard
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.password.ImportStage
import eu.eurise.password.PasswordViewModel
import eu.eurise.password.logic.ImportKind
import eu.eurise.password.R
import eu.eurise.password.data.SyncProblem
import eu.eurise.password.data.SyncState
import eu.eurise.password.data.VaultState
import eu.eurise.password.kdbx.EntryDraft
import eu.eurise.password.kdbx.VaultEntry
import eu.eurise.password.kdbx.CardData
import eu.eurise.password.data.AliasProvider
import eu.eurise.password.logic.Cards
import eu.eurise.password.logic.ImportOutcome
import eu.eurise.password.logic.Importer
import eu.eurise.password.logic.Generator
import eu.eurise.password.logic.HealthIssue
import eu.eurise.password.logic.Strength
import eu.eurise.password.logic.Totp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

// I miei dati: profili che compilano i moduli. (diviso da PasswordApp.kt il 18 novembre).

@Composable
internal fun profileSectionName(section: eu.eurise.password.logic.Profiles.Section) = stringResource(
    when (section) {
        eu.eurise.password.logic.Profiles.Section.Person -> R.string.pwd_profile_person
        eu.eurise.password.logic.Profiles.Section.Work -> R.string.pwd_profile_work
        eu.eurise.password.logic.Profiles.Section.Address -> R.string.pwd_profile_address
    },
)

/**
 * Un profilo dei miei dati. I valori compaiono solo dopo l'impronta o il PIN e restano aperti per 2 minuti; i dati
 * riservati (codice fiscale, IBAN, documento e quelli aggiunti da te) restano coperti finché non si toccano.
 */
@Composable
internal fun ProfileDetail(vm: PasswordViewModel, entry: VaultEntry, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val profiles = eu.eurise.password.logic.Profiles
    val gate = eu.eurise.password.data.SensitiveGate
    val fields = entry.profile.orEmpty()
    var open by remember { mutableStateOf(gate.isOpen()) }
    var left by remember { mutableIntStateOf(gate.remainingSeconds()) }
    var revealed by remember(entry.uuid) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(open) {
        while (open) {
            left = gate.remainingSeconds()
            if (left == 0) { open = false; revealed = emptySet() }
            delay(1_000)
        }
    }
    LaunchedEffect(Unit) { if (!open) vm.confirmSensitive(context) { open = true } }
    val title = entry.title.ifBlank { stringResource(R.string.pwd_profile_default) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader(stringResource(R.string.pwd_tab_mydata), onBack) { if (open) PillAction(stringResource(R.string.pwd_edit), onEdit) }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(title, 52, 16, 22)
            Column {
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (open) stringResource(R.string.pwd_profile_open_until, left) else stringResource(R.string.pwd_profile_closed),
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (open) scheme.secondary else scheme.onSurfaceVariant,
                )
            }
        }
        if (!open) {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(EuriseIcons.lock(), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.pwd_profile_locked), fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Button(onClick = { vm.confirmSensitive(context) { open = true } }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text(stringResource(R.string.pwd_profile_unlock), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            if (fields.none { it.second.isNotBlank() }) {
                Text(stringResource(R.string.pwd_profile_empty), fontSize = 14.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            val toggle: (String) -> Unit = { key -> revealed = if (key in revealed) revealed - key else revealed + key }
            val copy: (String) -> Unit = { value -> vm.confirmSensitive(context) { vm.copy(value) } }
            val values = fields.toMap()
            eu.eurise.password.logic.Profiles.Section.entries.forEach { section ->
                val list = profiles.known.filter { it.section == section }.mapNotNull { k -> values[k.key]?.takeIf { it.isNotBlank() }?.let { k.key to it } }
                if (list.isNotEmpty()) {
                    SectionLabel(profileSectionName(section), Modifier.padding(top = 4.dp))
                    ProfileRows(list, revealed, toggle, copy)
                }
            }
            val custom = profiles.custom(fields).filter { it.second.isNotBlank() }
            if (custom.isNotEmpty()) {
                SectionLabel(stringResource(R.string.pwd_profile_custom), Modifier.padding(top = 4.dp))
                ProfileRows(custom, revealed, toggle, copy)
            }
        }
    }
}

@Composable
internal fun ProfileRows(list: List<Pair<String, String>>, revealed: Set<String>, onToggle: (String) -> Unit, onCopy: (String) -> Unit) {
    GroupCard {
        list.forEachIndexed { i, (key, value) ->
            if (i > 0) CardDivider()
            val sensitive = eu.eurise.password.logic.Profiles.isSensitive(key)
            val shown = !sensitive || key in revealed
            FieldRow(key, value, hidden = !shown) {
                if (sensitive) {
                    RoundIcon(if (shown) EuriseIcons.eyeOff() else EuriseIcons.eye(), stringResource(if (shown) R.string.pwd_hide else R.string.pwd_show)) { onToggle(key) }
                }
                RoundIcon(EuriseIcons.copy(), stringResource(R.string.pwd_profile_copy, key)) { onCopy(value) }
            }
        }
    }
}

/** Modifica di un profilo: i campi noti divisi per sezione e, in fondo, i dati aggiunti da te con titolo e valore. */
@Composable
internal fun ProfileEdit(entry: VaultEntry?, onBack: () -> Unit, onSave: (EntryDraft) -> Unit) {
    val profiles = eu.eurise.password.logic.Profiles
    val start = entry?.profile.orEmpty()
    val defaultName = stringResource(R.string.pwd_profile_default)
    var name by remember { mutableStateOf(entry?.title.orEmpty()) }
    val values = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(start.filter { profiles.isKnown(it.first) }) } }
    val custom = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, String>>().apply { addAll(profiles.custom(start)) } }

    fun save() {
        val known = profiles.known.mapNotNull { k -> values[k.key]?.trim()?.takeIf { it.isNotEmpty() }?.let { k.key to it } }
        val extra = custom.map { it.first.trim() to it.second.trim() }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() && !profiles.isKnown(it.first) }
            .distinctBy { it.first }
        onSave(EntryDraft(name.trim().ifEmpty { defaultName }, "", "", "", "", null, profile = known + extra))
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackHeader(stringResource(R.string.pwd_back), onBack) { PillAction(stringResource(R.string.pwd_save), ::save) }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (entry == null) stringResource(R.string.pwd_profile_new) else entry.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.pwd_profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            eu.eurise.password.logic.Profiles.Section.entries.forEach { section ->
                SectionLabel(profileSectionName(section), Modifier.padding(top = 6.dp))
                profiles.known.filter { it.section == section }.forEach { k ->
                    val type = when (k.key) {
                        eu.eurise.password.logic.Profiles.PHONE -> KeyboardType.Phone
                        eu.eurise.password.logic.Profiles.EMAIL, eu.eurise.password.logic.Profiles.PEC -> KeyboardType.Email
                        eu.eurise.password.logic.Profiles.POSTAL_CODE -> KeyboardType.Number
                        else -> KeyboardType.Text
                    }
                    OutlinedTextField(
                        values[k.key].orEmpty(), { values[k.key] = it },
                        label = { Text(k.key) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = type),
                    )
                }
            }
            SectionLabel(stringResource(R.string.pwd_profile_custom), Modifier.padding(top = 6.dp))
            custom.forEachIndexed { i, (title, value) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { custom[i] = it to custom[i].second }, label = { Text(stringResource(R.string.pwd_profile_field_title)) }, singleLine = true, modifier = Modifier.weight(0.42f))
                    OutlinedTextField(value, { custom[i] = custom[i].first to it }, label = { Text(stringResource(R.string.pwd_profile_field_value)) }, singleLine = true, modifier = Modifier.weight(0.58f))
                    IconButton(onClick = { custom.removeAt(i) }) { Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.pwd_profile_remove)) }
                }
            }
            TextButton(onClick = { custom.add("" to "") }) {
                Icon(EuriseIcons.plus(), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pwd_profile_add_field), fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.pwd_profile_custom_note), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

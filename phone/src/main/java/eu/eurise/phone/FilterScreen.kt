package eu.eurise.phone

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.phone.data.PhoneData
import eu.eurise.phone.data.SpamFilter
import kotlinx.coroutines.launch

/** Perché il filtro ha fermato una chiamata, in parole. */
@Composable
internal fun reasonText(r: SpamFilter.Reason): String = stringResource(
    when (r) {
        SpamFilter.Reason.Hidden -> R.string.ph_filter_why_hidden
        SpamFilter.Reason.ForeignUnknown -> R.string.ph_filter_why_foreign
        SpamFilter.Reason.Unknown -> R.string.ph_filter_why_unknown
        SpamFilter.Reason.Repeated -> R.string.ph_filter_why_repeated
    },
)

/**
 * Filtro e numeri bloccati (tavola TelRecenti, «disturbatori riconosciuti sul telefono»): le regole del filtro, le
 * chiamate fermate negli ultimi sette giorni e i numeri bloccati, da sbloccare o da aggiungere a mano.
 */
@Composable
internal fun FilterScreen(isDefault: Boolean, onBecomeDefault: () -> Unit, onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf(SpamFilter.rules(context)) }
    var reload by remember { mutableIntStateOf(0) }
    var blocked by remember { mutableStateOf<List<String>>(emptyList()) }
    var filtered by remember { mutableStateOf<List<SpamFilter.Filtered>>(emptyList()) }
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(reload) {
        blocked = PhoneData.blockedNumbers(context)
        filtered = SpamFilter.filtered(context)
    }
    fun set(r: SpamFilter.Rules) { rules = r; SpamFilter.save(context, r) }
    fun block(n: String, on: Boolean) = scope.launch { PhoneData.setBlocked(context, n, on); reload++; onChanged() }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 40.dp)) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.ph_back), onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.chevronLeft(), stringResource(R.string.ph_back), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.ph_filter_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!isDefault) item(key = "default") {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer).clickable(onClick = onBecomeDefault).padding(14.dp),
            ) { Text(stringResource(R.string.ph_filter_need_default), fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        item(key = "rules") {
            Label(stringResource(R.string.ph_filter_rules))
            Box(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                Column {
                    Toggle(stringResource(R.string.ph_filter_on), stringResource(R.string.ph_filter_on_sub), rules.enabled) { set(rules.copy(enabled = it)) }
                    Toggle(stringResource(R.string.ph_filter_foreign), stringResource(R.string.ph_filter_foreign_sub), rules.foreignUnknown, rules.enabled) { set(rules.copy(foreignUnknown = it)) }
                    Toggle(stringResource(R.string.ph_filter_hidden), stringResource(R.string.ph_filter_hidden_sub), rules.hidden, rules.enabled) { set(rules.copy(hidden = it)) }
                    Toggle(stringResource(R.string.ph_filter_unknown), stringResource(R.string.ph_filter_unknown_sub), rules.unknown, rules.enabled) { set(rules.copy(unknown = it)) }
                    Toggle(stringResource(R.string.ph_filter_repeated), stringResource(R.string.ph_filter_repeated_sub), rules.repeated, rules.enabled) { set(rules.copy(repeated = it)) }
                }
            }
            Text(stringResource(R.string.ph_filter_privacy), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        item(key = "stopped") { Label(stringResource(R.string.ph_filter_stopped)) }
        if (filtered.isEmpty()) item(key = "stopped-none") { Note(stringResource(R.string.ph_filter_stopped_none)) }
        items(filtered.take(40), key = { "f-${it.at}-${it.number}" }) { f ->
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.number.ifBlank { stringResource(R.string.ph_private_number) }.let { if (it == f.number) Contacts.format(it) else it },
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(reasonText(f.reason) + " · " + DateUtils.getRelativeTimeSpanString(f.at), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (f.number.isNotBlank()) {
                    Small(stringResource(R.string.ph_filter_trust)) { SpamFilter.trust(context, f.number); reload++; onChanged() }
                    Small(stringResource(R.string.ph_block_short), danger = true) { block(f.number, true) }
                }
            }
        }
        item(key = "blocked") { Label(stringResource(R.string.ph_filter_blocked)) }
        item(key = "add") {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    typed, { typed = it }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ph_filter_add_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Small(stringResource(R.string.ph_block_short), danger = true) {
                    val n = typed.trim()
                    if (n.isNotEmpty()) { block(n, true); typed = "" }
                }
            }
        }
        if (blocked.isEmpty()) item(key = "blocked-none") { Note(stringResource(R.string.ph_filter_blocked_none)) }
        items(blocked, key = { "b-$it" }) { n ->
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(EuriseIcons.block(), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Text(Contacts.format(n), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Small(stringResource(R.string.ph_unblock_short)) { block(n, false) }
            }
        }
        item(key = "end") { Spacer(Modifier.height(20.dp).navigationBarsPadding()) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun Small(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
        color = if (danger) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

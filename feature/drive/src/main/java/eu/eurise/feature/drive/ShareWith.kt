package eu.eurise.feature.drive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.cloud.DavPersonShare
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «Condividi con…» un file o una cartella del cloud (collaborazione rapida, tavole CollCondividi e InsCondividi): una
 * persona per email o per nome utente, Vede o Modifica, chi c'è già con il tocco per cambiare o togliere. Si condivide
 * dal cloud dell'utente (Murena, Nextcloud): Eurise non ha server e non vede nulla.
 */
@Composable
internal fun ShareWithSheet(data: DriveData, item: DriveItem, onClose: () -> Unit, onFail: (Exception) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var who by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf(item.folder) }
    var busy by remember { mutableStateOf(false) }
    val people by produceState<List<DavPersonShare>?>(null, reload) {
        value = try { data.peopleOf(item) } catch (e: Exception) { onFail(e); emptyList() }
    }
    var hints by remember { mutableStateOf(emptyList<eu.eurise.core.links.MailContact>()) }
    LaunchedEffect(who) {
        delay(200)
        hints = eu.eurise.core.links.searchEmails(context, who).filter { !it.email.equals(who.trim(), ignoreCase = true) }
    }
    val (score, where) = placePrivacy(data.kindOf(item.place))

    fun act(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try { block(); reload++ } catch (e: Exception) { onFail(e) } finally { busy = false }
        }
    }

    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.background)
                .clickable(enabled = false) {}.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(stringResource(R.string.drive_share_with), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text(item.name, fontSize = 13.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedTextField(
                value = who, onValueChange = { who = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.drive_share_who)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (hints.isNotEmpty()) {
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow)) {
                    hints.forEach { h ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { who = h.email; hints = emptyList() }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Initial(h.name.ifBlank { h.email })
                            Column(Modifier.weight(1f)) {
                                Text(h.name.ifBlank { h.email }, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(h.email, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(if ('@' in who) R.string.drive_share_by_email else R.string.drive_share_by_user),
                    fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                Segmented(edit) { edit = it }
            }
            Button(
                enabled = !busy && who.isNotBlank(),
                onClick = { val w = who; act { data.shareWith(item, w, edit); who = "" } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                else Text(stringResource(R.string.drive_share_do), fontWeight = FontWeight.Bold)
            }

            val list = people
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp)) }
            } else if (list.isNotEmpty()) {
                Text(stringResource(R.string.drive_share_already, list.size), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow)) {
                    list.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Initial(p.name)
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(if (p.byEmail) R.string.drive_share_kind_email else R.string.drive_share_kind_user), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                            }
                            Segmented(p.edit) { e -> act { data.setShareEdit(item, p, e) } }
                            IconButton(onClick = { act { data.unshare(item, p) } }) {
                                Icon(EuriseIcons.close(), stringResource(R.string.drive_share_remove, p.name), tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(EuriseIcons.lock(), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.drive_share_privacy, item.place.label), fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                PrivacyMeter(score, where)
            }
        }
    }
}

/** Vede / Modifica: due tasti della stessa larghezza (regola sull'ergonomia). */
@Composable
private fun Segmented(edit: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(scheme.surfaceVariant).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(false to R.string.drive_share_view, true to R.string.drive_share_edit).forEach { (value, label) ->
            val on = edit == value
            Box(
                Modifier.size(width = 72.dp, height = 34.dp).clip(RoundedCornerShape(11.dp)).background(if (on) scheme.surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { if (!on) onChange(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), fontSize = 12.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) scheme.primary else scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Initial(name: String) {
    val tints = listOf(Color(0xFF4F6B5A), Color(0xFFA8742A), Color(0xFF5B6FA8), Color(0xFF8C2F39), Color(0xFF6D4AA8))
    val letters = name.split(' ', '.', '@').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(Modifier.size(34.dp).clip(CircleShape).background(tints[(name.hashCode() and 0x7fffffff) % tints.size]), contentAlignment = Alignment.Center) {
        Text(letters, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

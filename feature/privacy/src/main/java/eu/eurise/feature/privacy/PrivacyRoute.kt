package eu.eurise.feature.privacy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.cloud.CloudSync
import eu.eurise.core.data.docs.DocReading
import eu.eurise.core.designsystem.component.DataOut
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.component.privacyScore
import eu.eurise.core.designsystem.component.where
import eu.eurise.core.designsystem.component.titleRes
import eu.eurise.core.designsystem.component.score
import eu.eurise.core.designsystem.component.detailRes
import eu.eurise.core.designsystem.component.ServiceStates
import eu.eurise.core.designsystem.component.ServiceConsentSheet
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.suite.NetworkExit
import eu.eurise.core.suite.NetworkShield
import eu.eurise.core.suite.VpnProvider
import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyEvent
import eu.eurise.core.suite.PrivacyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Traduzione di Leggi: un file uguale per tutti (8), da un archivio negli Stati Uniti (−1) gestito da Google per conto di
 * Mozilla: Google può collegare l'indirizzo di rete al resto del traffico che riceve (−1). Esce solo la coppia di lingue,
 * una volta per lingua. Criteri del 14 novembre; con la rete nascosta sale (Tor +2).
 */
private fun translateScore(hiddenIp: Int = 0) = (privacyScore(DataOut.Anonymous, Jurisdiction.US, hiddenIp = hiddenIp) - 1).coerceAtLeast(1)

/** L'etichetta delle tacchette guadagnate: «+2 Proton VPN», «+2 Tor». */
@Composable
private fun NetworkExit.label(): String? = when {
    bonus == 0 -> null
    kind == NetworkShield.Tor -> stringResource(R.string.pv_boost_tor)
    else -> stringResource(R.string.pv_boost_vpn, bonus, vpn?.name ?: "VPN")
}

/** La sigla della sede di una VPN, come per gli altri servizi. */
private fun VpnProvider.jurisdiction() = when {
    country == "CH" -> Jurisdiction.CH
    european -> Jurisdiction.EU
    country == "GB" -> Jurisdiction.UK
    country == "US" -> Jurisdiction.US
    else -> Jurisdiction.Other
}

/** Una riga della VPN: cosa le dà o le toglie fiducia, in parole. */
@Composable
private fun VpnProvider.facts(): String = listOf(
    stringResource(if (verifiedNoLogs) (if (publicReport) R.string.pv_vpn_audit_public else R.string.pv_vpn_audit_private) else R.string.pv_vpn_audit_none),
    stringResource(if (openSourceApps) R.string.pv_vpn_open else R.string.pv_vpn_closed),
    stringResource(if (anonymousAccount) R.string.pv_vpn_anon else R.string.pv_vpn_email),
    stringResource(if (european) R.string.pv_vpn_europe else R.string.pv_vpn_outside, country),
).joinToString(" · ")

private class Permission(val title: Int, val detail: Int)

private fun Context.activePermissions(): List<Permission> = buildList {
    fun has(name: String) = checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
    if (has(Manifest.permission.READ_CALENDAR)) add(Permission(R.string.pv_p_calendar, R.string.pv_p_calendar_d))
    if (has(Manifest.permission.READ_CONTACTS)) add(Permission(R.string.pv_p_contacts, R.string.pv_p_contacts_d))
    if (has(Manifest.permission.ACCESS_COARSE_LOCATION)) add(Permission(R.string.pv_p_location, R.string.pv_p_location_d))
    if (has(Manifest.permission.READ_CALL_LOG)) add(Permission(R.string.pv_p_calls, R.string.pv_p_calls_d))
    if (has(Manifest.permission.CAMERA)) add(Permission(R.string.pv_p_camera, R.string.pv_p_camera_d))
    if (has(Manifest.permission.CALL_PHONE)) add(Permission(R.string.pv_p_call_phone, R.string.pv_p_call_phone_d))
    if (android.os.Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) add(Permission(R.string.pv_p_files, R.string.pv_p_files_d))
    if (getSystemService(android.app.NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true) {
        add(Permission(R.string.pv_p_dnd, R.string.pv_p_dnd_d))
    }
}

private fun Context.openAppSettings() {
    runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp)) { content() }
}

@Composable
private fun Line() = HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))

@Composable
private fun TwoLines(
    title: String, detail: String?, modifier: Modifier = Modifier, score: Int? = null, where: Jurisdiction? = null,
    boost: Int = 0, boostLabel: String? = null,
) {
    Column(modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(title, fontSize = 15.sp)
        detail?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        score?.let { PrivacyMeter(it, where, Modifier.padding(top = 4.dp), boost, boostLabel) }
    }
}

@Composable
fun PrivacyRoute(onBack: () -> Unit) {
    var showLog by remember { mutableStateOf(false) }
    BackHandler { if (showLog) showLog = false else onBack() }
    if (showLog) LogScreen { showLog = false } else Overview(onBack) { showLog = true }
}

@Composable
private fun Overview(onBack: () -> Unit, onLog: () -> Unit) {
    val context = LocalContext.current
    val cloud = remember { CloudSync.get(context.applicationContext) }
    val settings by cloud.settings.collectAsStateWithLifecycle()
    val accounts by CloudAccounts.get(context).accounts.collectAsStateWithLifecycle()
    val states = remember { mutableStateMapOf<PrivacyService, Boolean>() }
    val loaded by produceState(false, ServiceStates.version) {
        withContext(Dispatchers.IO) { PrivacyService.entries.forEach { states[it] = PrivacyCenter.allowed(it) } }
        value = true
    }
    var asking by remember { mutableStateOf<PrivacyService?>(null) }
    var shieldTick by remember { mutableStateOf(0) }
    val shield by produceState(NetworkExit.DIRECT, shieldTick) { value = withContext(Dispatchers.IO) { NetworkExit.current(context) } }
    var picking by remember { mutableStateOf(false) }
    val choices by produceState(emptyList<NetworkExit>(), shieldTick) { value = withContext(Dispatchers.IO) { NetworkExit.choices(context) } }
    val notesAccount = accounts.firstOrNull { it.id == settings.notesAccount }?.takeIf { settings.notesEnabled }
    val notesPlace = notesAccount?.let { if (it.kind == CloudKind.Murena) "Murena" else it.name }
    val on = states.count { it.value }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "head") {
            Row(Modifier.statusBarsPadding().padding(start = 4.dp, end = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(EuriseIcons.chevronLeft(), stringResource(R.string.pv_back), modifier = Modifier.size(22.dp)) }
                Text(stringResource(R.string.pv_title), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(EuriseIcons.shield(), null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(if (on == 0 && notesPlace == null) R.string.pv_head_none else R.string.pv_head), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    val sync = notesPlace?.let { stringResource(R.string.pv_head_sync, it) }.orEmpty()
                    if (loaded) Text(sync + pluralStringResource(R.plurals.pv_head_services, on, on), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        item(key = "network") {
            SectionLabel(stringResource(R.string.pv_network))
            Card {
                val vpn = shield.vpn
                when {
                    shield.kind == NetworkShield.None -> TwoLines(stringResource(R.string.pv_net_direct), stringResource(R.string.pv_net_direct_d))
                    shield.kind == NetworkShield.Tor -> TwoLines(stringResource(R.string.pv_net_tor), stringResource(R.string.pv_net_tor_d))
                    vpn != null -> TwoLines(
                        stringResource(R.string.pv_net_vpn_named, vpn.name, shield.bonus), stringResource(R.string.pv_net_vpn_named_d, vpn.facts()),
                        score = vpn.score, where = vpn.jurisdiction(),
                    )
                    else -> TwoLines(stringResource(R.string.pv_net_vpn_unknown), stringResource(R.string.pv_net_vpn_unknown_d))
                }
                // Android non dice di chi è la VPN: se ce n'è più d'una, o la suite sbaglia, la persona la indica.
                if (shield.kind != NetworkShield.None) {
                    Line()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { picking = !picking }, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pv_net_which), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    if (picking) (choices + listOf<NetworkExit?>(null)).forEach { option ->
                        Line()
                        val title = when {
                            option == null -> stringResource(R.string.pv_net_auto)
                            option.kind == NetworkShield.Tor -> stringResource(R.string.pv_net_tor)
                            option.vpn != null -> option.vpn!!.name
                            else -> stringResource(R.string.pv_net_vpn_unknown)
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable {
                            NetworkExit.choose(context, option); picking = false; shieldTick++
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            option?.vpn?.let { PrivacyMeter(it.score, it.jurisdiction()) }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.pv_vpn_note), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
        }
        item(key = "where") {
            SectionLabel(stringResource(R.string.pv_where))
            Card {
                val phone = stringResource(R.string.pv_phone)
                listOf(
                    R.string.pv_data_notes to (notesPlace?.let { stringResource(R.string.pv_phone_and, it) } ?: phone),
                    R.string.pv_data_tasks to (notesPlace?.takeIf { settings.tasksEnabled }?.let { stringResource(R.string.pv_phone_and, it) } ?: phone),
                    R.string.pv_data_calendar to stringResource(R.string.pv_system),
                    R.string.pv_data_medicines to phone,
                    R.string.pv_data_links to phone,
                ).forEachIndexed { i, (name, place) ->
                    if (i > 0) Line()
                    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(name), fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(place, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item(key = "services") {
            SectionLabel(stringResource(R.string.pv_services))
            Card {
                PrivacyService.entries.forEachIndexed { i, service ->
                    if (i > 0) Line()
                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                        val score = service.score(shield.bonus)
                        TwoLines(
                            stringResource(service.titleRes()), stringResource(service.detailRes()), Modifier.weight(1f).padding(end = 8.dp),
                            score, service.where(), score - service.score(), shield.label(),
                        )
                        Switch(states[service] == true, enabled = loaded, onCheckedChange = { value ->
                            if (value) asking = service else { states[service] = false; ServiceStates.set(service, false) }
                        })
                    }
                }
            }
            Text(
                stringResource(R.string.pv_meter_note), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
        }
        item(key = "others") {
            SectionLabel(stringResource(R.string.pv_other_apps))
            Card {
                TwoLines(stringResource(R.string.pv_gallery), stringResource(R.string.pv_gallery_d))
                Line()
                TwoLines(stringResource(R.string.pv_password), stringResource(R.string.pv_password_d))
                Line()
                TwoLines(stringResource(R.string.pv_keyboard), stringResource(R.string.pv_keyboard_d), score = 10, where = Jurisdiction.Phone)
                Line()
                TwoLines(
                    stringResource(R.string.pv_voice), stringResource(R.string.pv_voice_d), score = privacyScore(DataOut.Anonymous, Jurisdiction.US, hiddenIp = shield.bonus),
                    where = Jurisdiction.US, boost = privacyScore(DataOut.Anonymous, Jurisdiction.US, hiddenIp = shield.bonus) - privacyScore(DataOut.Anonymous, Jurisdiction.US),
                    boostLabel = shield.label(),
                )
                Line()
                TwoLines(
                    stringResource(R.string.pv_translate), stringResource(R.string.pv_translate_d), score = translateScore(shield.bonus),
                    where = Jurisdiction.US, boost = translateScore(shield.bonus) - translateScore(),
                    boostLabel = shield.label(),
                )
                Line()
                // Lettura dei documenti: analisi dei contenuti, tutta sul telefono. Si accende dallo Scanner (con la
                // spiegazione), si spegne anche da qui senza conferme.
                var reading by remember { mutableStateOf(DocReading.enabled(context)) }
                Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                    TwoLines(stringResource(R.string.pv_read), stringResource(if (reading) R.string.pv_read_on else R.string.pv_read_off), Modifier.weight(1f).padding(end = 8.dp), 10, Jurisdiction.Phone)
                    Switch(reading, enabled = reading, onCheckedChange = { if (!it) { reading = false; DocReading.set(context, enabled = false) } })
                }
            }
        }
        item(key = "log") {
            Spacer8()
            Card {
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onLog), verticalAlignment = Alignment.CenterVertically) {
                    TwoLines(stringResource(R.string.pv_log), stringResource(R.string.pv_log_d), Modifier.weight(1f))
                    Icon(EuriseIcons.chevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    asking?.let { service ->
        ServiceConsentSheet(service, shield.bonus, onDismiss = { asking = null }, onAccept = { states[service] = true; asking = null })
    }
}

@Composable
private fun Spacer8() = Column(Modifier.height(2.dp)) {}

@Composable
private fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cloud = remember { CloudSync.get(context.applicationContext) }
    val syncEvents by cloud.events.collectAsStateWithLifecycle()
    val events by produceState(emptyList<PrivacyEvent>()) { value = withContext(Dispatchers.IO) { PrivacyCenter.events() } }
    val permissions = remember { context.activePermissions() }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "head") {
            Row(Modifier.statusBarsPadding().padding(start = 4.dp, end = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(EuriseIcons.chevronLeft(), stringResource(R.string.pv_back), modifier = Modifier.size(22.dp)) }
                Text(stringResource(R.string.pv_log), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.pv_log_intro), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        }
        item(key = "permissions") {
            SectionLabel(stringResource(R.string.pv_permissions))
            Card {
                permissions.forEachIndexed { i, p ->
                    if (i > 0) Line()
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { context.openAppSettings() }, verticalAlignment = Alignment.CenterVertically) {
                        TwoLines(stringResource(p.title), stringResource(p.detail), Modifier.weight(1f))
                        Text(stringResource(R.string.pv_manage), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item(key = "log") {
            SectionLabel(stringResource(R.string.pv_log))
            Card {
                if (events.isEmpty() && syncEvents.isEmpty()) TwoLines(stringResource(R.string.pv_log_empty), null)
                val rows: List<Pair<Long, String>> = events.map { e ->
                    e.time to (if (e.count > 1) stringResource(R.string.pv_log_count, stringResource(e.service.titleRes()), e.count) else stringResource(e.service.titleRes()))
                } + syncEvents.map { it.time to it.text }
                rows.sortedByDescending { it.first }.take(100).forEachIndexed { i, (time, text) ->
                    if (i > 0) Line()
                    TwoLines(text, format.format(Date(time)))
                }
            }
        }
    }
}

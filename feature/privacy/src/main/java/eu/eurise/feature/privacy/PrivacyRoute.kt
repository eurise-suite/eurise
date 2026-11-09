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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyEvent
import eu.eurise.core.suite.PrivacyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private fun PrivacyService.title() = when (this) {
    PrivacyService.Weather -> R.string.pv_s_weather
    PrivacyService.News -> R.string.pv_s_news
    PrivacyService.Markets -> R.string.pv_s_markets
    PrivacyService.Horoscope -> R.string.pv_s_horoscope
    PrivacyService.Videos -> R.string.pv_s_videos
    PrivacyService.Routes -> R.string.pv_s_routes
}

private fun PrivacyService.detail() = when (this) {
    PrivacyService.Weather -> R.string.pv_s_weather_d
    PrivacyService.News -> R.string.pv_s_news_d
    PrivacyService.Markets -> R.string.pv_s_markets_d
    PrivacyService.Horoscope -> R.string.pv_s_horoscope_d
    PrivacyService.Videos -> R.string.pv_s_videos_d
    PrivacyService.Routes -> R.string.pv_s_routes_d
}

private class Permission(val title: Int, val detail: Int)

private fun Context.activePermissions(): List<Permission> = buildList {
    fun has(name: String) = checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
    if (has(Manifest.permission.READ_CALENDAR)) add(Permission(R.string.pv_p_calendar, R.string.pv_p_calendar_d))
    if (has(Manifest.permission.READ_CONTACTS)) add(Permission(R.string.pv_p_contacts, R.string.pv_p_contacts_d))
    if (has(Manifest.permission.ACCESS_COARSE_LOCATION)) add(Permission(R.string.pv_p_location, R.string.pv_p_location_d))
    if (has(Manifest.permission.READ_CALL_LOG)) add(Permission(R.string.pv_p_calls, R.string.pv_p_calls_d))
    if (has(Manifest.permission.CAMERA)) add(Permission(R.string.pv_p_camera, R.string.pv_p_camera_d))
    if (android.os.Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) add(Permission(R.string.pv_p_files, R.string.pv_p_files_d))
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
private fun TwoLines(title: String, detail: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(title, fontSize = 15.sp)
        detail?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun PrivacyRoute(onBack: () -> Unit) {
    var showLog by remember { mutableStateOf(false) }
    BackHandler { if (showLog) showLog = false else onBack() }
    if (showLog) LogScreen { showLog = false } else Overview(onBack) { showLog = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Overview(onBack: () -> Unit, onLog: () -> Unit) {
    val context = LocalContext.current
    val cloud = remember { CloudSync.get(context.applicationContext) }
    val settings by cloud.settings.collectAsStateWithLifecycle()
    val accounts by CloudAccounts.get(context).accounts.collectAsStateWithLifecycle()
    val states = remember { mutableStateMapOf<PrivacyService, Boolean>() }
    val loaded by produceState(false) {
        withContext(Dispatchers.IO) { PrivacyService.entries.forEach { states[it] = PrivacyCenter.allowed(it) } }
        value = true
    }
    var asking by remember { mutableStateOf<PrivacyService?>(null) }
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
                        TwoLines(stringResource(service.title()), stringResource(service.detail()), Modifier.weight(1f).padding(end = 8.dp))
                        Switch(states[service] == true, enabled = loaded, onCheckedChange = { value ->
                            if (value) asking = service else { states[service] = false; PrivacyCenter.set(service, false) }
                        })
                    }
                }
            }
        }
        item(key = "others") {
            SectionLabel(stringResource(R.string.pv_other_apps))
            Card {
                TwoLines(stringResource(R.string.pv_gallery), stringResource(R.string.pv_gallery_d))
                Line()
                TwoLines(stringResource(R.string.pv_password), stringResource(R.string.pv_password_d))
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
        ModalBottomSheet(onDismissRequest = { asking = null }, containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.pv_on_title).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Text(stringResource(service.title()), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                    Labeled(R.string.pv_on_read, stringResource(service.detail()))
                    Line()
                    Labeled(R.string.pv_on_where, stringResource(service.title()))
                    Line()
                    Labeled(R.string.pv_on_who, stringResource(R.string.pv_on_who_d))
                    Line()
                    Labeled(R.string.pv_on_back, stringResource(R.string.pv_on_back_d))
                }
                Button(onClick = { states[service] = true; PrivacyCenter.set(service, true); asking = null }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text(stringResource(R.string.pv_on_ok), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { asking = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pv_on_no), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Spacer8() = Column(Modifier.height(2.dp)) {}

@Composable
private fun Labeled(label: Int, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(stringResource(label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.5.sp)
    }
}

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
                    e.time to (if (e.count > 1) stringResource(R.string.pv_log_count, stringResource(e.service.title()), e.count) else stringResource(e.service.title()))
                } + syncEvents.map { it.time to it.text }
                rows.sortedByDescending { it.first }.take(100).forEachIndexed { i, (time, text) ->
                    if (i > 0) Line()
                    TwoLines(text, format.format(Date(time)))
                }
            }
        }
    }
}

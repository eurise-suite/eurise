package eu.eurise.feature.launcher.sounds

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import kotlinx.coroutines.launch

/** Suoni: suonerie per uso (tavola SuoSuonerie), il pezzo di una canzone (SuoTaglia), chi suona come (SuoRegole). */
@Composable
fun SoundsScreen(onClose: () -> Unit) {
    var page by remember { mutableStateOf<SoundsPage>(SoundsPage.Main) }
    when (val p = page) {
        SoundsPage.Main -> SoundsMain(onClose = onClose, onCut = { page = SoundsPage.Cut(it) }, onRules = { page = SoundsPage.Rules(null) })
        is SoundsPage.Cut -> CutScreen(p.uri, onClose = { page = SoundsPage.Main }, onPerson = { id -> page = SoundsPage.Rules(id) })
        is SoundsPage.Rules -> RulesScreen(newPersonSound = p.newPersonCalls, onClose = { page = SoundsPage.Main })
    }
}

private sealed interface SoundsPage {
    data object Main : SoundsPage
    data class Cut(val uri: Uri) : SoundsPage
    /** [newPersonCalls]: dal taglio, «Una persona…» apre subito la scelta della persona con questo suono. */
    data class Rules(val newPersonCalls: String?) : SoundsPage
}

/**
 * Sceglie il suono generale di un uso. Per Chiamate e Altro diventa anche suoneria o avviso del sistema (copiato tra le
 * suonerie del telefono); per Messaggi ed Email lo fa sentire Eurise al posto delle app che hanno tolto il loro suono.
 * Ritorna falso se manca il permesso per le impostazioni di sistema.
 */
internal suspend fun chooseGeneral(context: android.content.Context, use: SoundUse, id: String): Boolean {
    SoundStore.update(context) { it.copy(defaults = it.defaults + (use to id)) }
    if (use == SoundUse.Messages || use == SoundUse.Email) return true
    if (Build.VERSION.SDK_INT < 29) {
        Toast.makeText(context, R.string.snd_needs_android10, Toast.LENGTH_LONG).show()
        return true
    }
    if (!Settings.System.canWrite(context)) return false
    if (!SoundLibrary.setSystemDefault(context, use, id)) Toast.makeText(context, R.string.snd_set_failed, Toast.LENGTH_LONG).show()
    return true
}

@Composable
private fun SoundsMain(onClose: () -> Unit, onCut: (Uri) -> Unit, onRules: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SoundStore.settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { SoundStore.get(context) }
    var tab by remember { mutableIntStateOf(0) }
    val use = SoundUse.entries[tab]
    var sounds by remember { mutableStateOf<List<Sound>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { sounds = SoundLibrary.all(context) }
    var playing by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { SoundPlayer.stopPreview() } }
    var askDownload by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf<Float?>(null) }
    var pendingSystem by remember { mutableStateOf<Pair<SoundUse, String>?>(null) }
    var explainWrite by remember { mutableStateOf(false) }

    val pick = { id: String ->
        scope.launch { if (!chooseGeneral(context, use, id)) { pendingSystem = use to id; explainWrite = true } }
        Unit
    }
    val latestPending by rememberUpdatedState(pendingSystem)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e ->
            val p = latestPending
            if (e == Lifecycle.Event.ON_RESUME && p != null && Settings.System.canWrite(context)) {
                pendingSystem = null
                scope.launch { chooseGeneral(context, p.first, p.second) }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val song = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onCut(uri) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SndHeader(stringResource(R.string.snd_title), null, onClose)
        // Schede: Chiamate, Messaggi, Email, Altro (tutte della stessa larghezza).
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(R.string.snd_tab_calls, R.string.snd_tab_messages, R.string.snd_tab_email, R.string.snd_tab_other).forEachIndexed { i, label ->
                val on = i == tab
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                        .selectable(on, role = Role.Tab) { tab = i },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(label), fontSize = 13.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Il suono di adesso, da ascoltare.
        val currentId = settings.defaults[use]
        val current = sounds.firstOrNull { it.id == currentId }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.music(), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    stringResource(listOf(R.string.snd_current_calls, R.string.snd_current_messages, R.string.snd_current_email, R.string.snd_current_other)[tab]),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                )
                Text(
                    current?.title ?: stringResource(if (use == SoundUse.Messages || use == SoundUse.Email) R.string.snd_app_default else R.string.snd_phone_default),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            if (current != null) {
                val on = playing == current.id
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f))
                        .clickable(role = Role.Button) {
                            if (on) { SoundPlayer.stopPreview(); playing = null } else { playing = current.id; SoundPlayer.preview(context, current.id) { playing = null } }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (on) EuriseIcons.pause() else EuriseIcons.play(), stringResource(if (on) R.string.snd_stop else R.string.snd_listen, current.title),
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (use == SoundUse.Messages || use == SoundUse.Email) {
            Text(stringResource(R.string.snd_hint_messages), fontSize = 12.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        // Chi suona come.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onRules).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.snd_rules), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.snd_rules_summary), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(EuriseIcons.chevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }

        // Per Chiamate prima le suonerie, per gli altri prima gli avvisi brevi.
        val wantShort = use != SoundUse.Calls
        val eurise = sounds.filter { it.group == SoundGroup.Eurise && it.available }.sortedBy { if (it.short == wantShort) 0 else 1 }
        SndSection(stringResource(R.string.snd_group_eurise), stringResource(R.string.snd_group_eurise_note)) {
            eurise.forEachIndexed { i, s ->
                if (i > 0) SndDivider()
                SoundRow(s, s.id == currentId, playing, { playing = it }) { pick(s.id) }
            }
            if (!SoundLibrary.packReady(context)) {
                SndDivider()
                val d = downloading
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = d == null) { askDownload = true }.padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.snd_more), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text(
                            if (d == null) stringResource(R.string.snd_more_size, SoundLibrary.PACK_MB) else stringResource(R.string.snd_downloading, (d * 100).toInt()),
                            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (d != null) LinearProgressIndicator(progress = { d }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        val classics = sounds.filter { it.group == SoundGroup.Classic }
        if (classics.isNotEmpty()) {
            SndSection(stringResource(R.string.snd_group_classic), stringResource(R.string.snd_group_classic_note)) {
                classics.forEachIndexed { i, s ->
                    if (i > 0) SndDivider()
                    SoundRow(s, s.id == currentId, playing, { playing = it }) { if (s.available) pick(s.id) else askDownload = true }
                }
            }
        }
        val mine = sounds.filter { it.group == SoundGroup.Mine }
        if (mine.isNotEmpty()) {
            SndSection(stringResource(R.string.snd_group_mine)) {
                mine.forEachIndexed { i, s ->
                    if (i > 0) SndDivider()
                    SoundRow(s, s.id == currentId, playing, { playing = it }) { pick(s.id) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .clickable { song.launch(arrayOf("audio/*")) }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.music(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.snd_song), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.snd_song_hint), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (askDownload) {
        AlertDialog(
            onDismissRequest = { askDownload = false },
            title = { Text(stringResource(R.string.snd_download_title)) },
            text = { Text(stringResource(R.string.snd_download_body, SoundLibrary.PACK_MB, SoundLibrary.PACK_HOST)) },
            confirmButton = {
                TextButton(onClick = {
                    askDownload = false
                    downloading = 0f
                    scope.launch {
                        val ok = SoundLibrary.downloadPack(context) { p -> downloading = p }
                        downloading = null
                        if (!ok) Toast.makeText(context, R.string.snd_download_failed, Toast.LENGTH_LONG).show()
                        reload++
                    }
                }) { Text(stringResource(R.string.snd_download_ok)) }
            },
            dismissButton = { TextButton(onClick = { askDownload = false }) { Text(stringResource(R.string.snd_cancel)) } },
        )
    }
    if (explainWrite) {
        AlertDialog(
            onDismissRequest = { explainWrite = false },
            text = { Text(stringResource(R.string.snd_write_settings)) },
            confirmButton = {
                TextButton(onClick = {
                    explainWrite = false
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text(stringResource(R.string.rules_grant_listener)) }
            },
            dismissButton = { TextButton(onClick = { explainWrite = false; pendingSystem = null }) { Text(stringResource(R.string.snd_cancel)) } },
        )
    }
}

@Composable
private fun SoundRow(s: Sound, selected: Boolean, playing: String?, onPlaying: (String?) -> Unit, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 52.dp).selectable(selected, role = Role.RadioButton, onClick = onPick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected, onClick = null, enabled = s.available)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(s.title, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (s.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                val sub = listOfNotNull(s.subtitle.takeIf { it.isNotEmpty() }, s.seconds.takeIf { it > 0 }?.let { stringResource(R.string.snd_seconds, it) }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (s.available) SndPlayButton(s.id, s.title, playing, onPlaying)
        else Icon(EuriseIcons.download(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(end = 4.dp))
    }
}

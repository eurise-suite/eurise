package eu.eurise.feature.launcher.sounds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R

@Composable
internal fun SndHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.chevronLeft(), stringResource(R.string.card_done), modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = if (subtitle == null) 26.sp else 22.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SndSection(title: String, note: String? = null, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (note != null) Text(note, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp)) { content() }
    }
}

@Composable
internal fun SndDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
internal fun SndSwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (hint != null) Text(hint, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun SndChip(text: String, strong: Boolean = true) {
    Text(
        text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
        color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (strong) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Tasto tondo Ascolta / Ferma per un suono. */
@Composable
internal fun SndPlayButton(id: String, title: String, playing: String?, onPlaying: (String?) -> Unit) {
    val context = LocalContext.current
    val on = playing == id
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(role = Role.Button) {
                if (on) { SoundPlayer.stopPreview(); onPlaying(null) } else { onPlaying(id); SoundPlayer.preview(context, id) { onPlaying(null) } }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) EuriseIcons.pause() else EuriseIcons.play(),
            if (on) stringResource(R.string.snd_stop) else stringResource(R.string.snd_listen, title),
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Scelta di un suono per una regola: [allowGeneral] aggiunge «Come il generale» (null), sempre c'è «Silenzioso».
 * Ascoltare non chiude il foglio; toccare il nome sceglie.
 */
@Composable
internal fun SoundPickerDialog(current: String?, short: Boolean, allowGeneral: Boolean, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var sounds by remember { mutableStateOf<List<Sound>>(emptyList()) }
    var playing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { sounds = SoundLibrary.all(context).filter { it.available }.sortedBy { if (it.short == short) 0 else 1 } }
    DisposableEffect(Unit) { onDispose { SoundPlayer.stopPreview() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_pick_sound)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (allowGeneral) item { PickRow(stringResource(R.string.rules_same_as_general), current == null) { onPick(null) } }
                item { PickRow(stringResource(R.string.rules_silent), current == SoundIds.SILENT) { onPick(SoundIds.SILENT) } }
                items(sounds, key = { it.id }) { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { PickRow(s.title, current == s.id, s.subtitle) { onPick(s.id) } }
                        SndPlayButton(s.id, s.title, playing) { playing = it }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.snd_cancel)) } },
    )
}

@Composable
private fun PickRow(title: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

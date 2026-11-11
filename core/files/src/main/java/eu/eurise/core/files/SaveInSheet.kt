package eu.eurise.core.files

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.launch
import java.io.File

/**
 * Cosa salvare con «Salva in…». [produce] prepara il file solo al momento del salvataggio (un PDF da scrivere, una
 * copia): così scegliere la cartella è immediato. [kind] separa le proposte per app ("scan", "office", "gallery"…).
 */
class SaveRequest(
    val kind: String,
    val from: String,
    val name: String,
    val extension: String,
    val mime: String,
    val produce: suspend () -> File,
    /** Una cartella proposta da chi chiama (per esempio quella letta nel documento), con il suo perché. */
    val hint: Spot? = null,
    val hintReason: String? = null,
)

/** Dove è finito il file: la cartella e il nome usato (con il numero, se il nome c'era già). */
data class Saved(val spot: Spot, val name: String, val where: String)

/**
 * «Salva in…» (tavola FileSalvaIn): nome, [extra] della app (per esempio il formato), proposte con il perché, telefono e
 * cloud, il percorso, le cartelle, «Nuova cartella», la copia sul telefono, le tacche di privacy e «Salva in Casa».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveInSheet(request: SaveRequest, onSaved: (Saved) -> Unit, onDismiss: () -> Unit, extra: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val files = remember { EuriseFiles.get(context) }
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val places = remember { files.places() }
    var name by remember { mutableStateOf(request.name) }
    val because = stringResource(R.string.files_because_already, "%s")
    val lastUsed = stringResource(R.string.files_last_used)
    // Proposte con il loro perché: prima quella di chi chiama, poi i ricordi di Eurise File.
    val suggestions = remember(name) {
        val learned = files.memory.suggest(request.kind, name, System.currentTimeMillis()).map { s ->
            s.spot to (s.because?.let { because.replace("%s", it.substringBeforeLast('.')) } ?: lastUsed)
        }
        (listOfNotNull(request.hint?.let { it to request.hintReason.orEmpty() }) + learned).distinctBy { it.first }.take(2)
    }
    var spot by remember { mutableStateOf(suggestions.firstOrNull()?.first ?: Spot(null, EuriseFiles.PHONE_HOME)) }
    var folders by remember { mutableStateOf<List<Entry>?>(null) }
    var listFailed by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var copy by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(spot, reload) {
        folders = null
        listFailed = false
        folders = runCatching { files.folders(spot) }.onFailure { listFailed = true }.getOrDefault(emptyList())
    }

    val place = places.firstOrNull { it.account == spot.account }
    val folderLabel = spot.name.ifEmpty { files.label(spot) }

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.files_save_in), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.files_from, request.from), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.files_name)) },
                        suffix = { Text("." + request.extension) }, modifier = Modifier.fillMaxWidth())
                    extra()
                }
                if (suggestions.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.files_suggestions))
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { (sSpot, reason) ->
                            val on = sSpot == spot
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                    .background(if (on) scheme.primaryContainer else scheme.surfaceContainerLow)
                                    .clickable(role = Role.RadioButton) { spot = sSpot }.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(files.label(sSpot), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, maxLines = 1)
                                Text(sSpot.path.split('/').takeLast(2).joinToString(" › ").ifEmpty { files.label(sSpot) }, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (reason.isNotEmpty()) Text(reason, fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    places.forEach { p ->
                        FilterPill(p.label, dot = null, selected = p.account == spot.account) { if (p.account != spot.account) spot = p.spot }
                    }
                }
                // Il percorso: ogni pezzo riporta lì.
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    val crumbs = spot.crumbs
                    crumbs.forEachIndexed { i, c ->
                        val label = if (i == 0) files.label(spot) else c.name
                        Text(label, fontSize = 13.sp, fontWeight = if (i == crumbs.lastIndex) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (i == crumbs.lastIndex) scheme.onSurface else scheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { spot = c }.padding(horizontal = 2.dp, vertical = 8.dp))
                        if (i < crumbs.lastIndex) Text(" › ", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                    val list = folders
                    when {
                        list == null -> Text("…", modifier = Modifier.padding(vertical = 14.dp), color = scheme.onSurfaceVariant)
                        listFailed -> Text(stringResource(R.string.files_cant_list), fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp), color = scheme.onSurfaceVariant)
                        list.isEmpty() -> Text(stringResource(R.string.files_empty), fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp), color = scheme.onSurfaceVariant)
                        else -> list.forEachIndexed { i, e ->
                            if (i > 0) CardDivider()
                            Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable { spot = e.spot }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(EuriseIcons.folder(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(e.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(EuriseIcons.chevronRight(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    CardDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.Button) { newFolder = true }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(EuriseIcons.plus(), null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.files_new_folder), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!spot.isPhone) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.files_keep_copy), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.files_keep_copy_sub), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                    Switch(checked = copy, onCheckedChange = { copy = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrivacyNotches(EuriseFiles.privacy(place?.kind))
                    val country = EuriseFiles.country(place?.kind)
                    Text(
                        when {
                            spot.isPhone -> stringResource(R.string.files_privacy_phone)
                            country != null -> stringResource(R.string.files_privacy_cloud_country, files.label(spot), country)
                            else -> stringResource(R.string.files_privacy_cloud, files.label(spot))
                        },
                        fontSize = 12.sp, color = scheme.onSurfaceVariant,
                    )
                }
                Button(
                    enabled = !saving && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = {
                        saving = true
                        scope.launch {
                            val target = spot
                            val result = runCatching {
                                val file = request.produce()
                                val used = files.save(target, Names.clean(name, request.extension), file, request.mime, phoneCopy = copy)
                                files.remember(request.kind, target, used)
                                Saved(target, used, folderLabel)
                            }
                            saving = false
                            result.onSuccess {
                                Toast.makeText(context, context.getString(R.string.files_saved, it.where), Toast.LENGTH_LONG).show()
                                onSaved(it)
                            }.onFailure { e ->
                                val text = if (e is EuriseFiles.NeedsAccess) R.string.files_needs_access else R.string.files_failed
                                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                ) { Text(if (saving) stringResource(R.string.files_saving) else stringResource(R.string.files_save_here, folderLabel), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (newFolder) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text(stringResource(R.string.files_new_folder)) },
            text = { OutlinedTextField(folderName, { folderName = it }, singleLine = true, label = { Text(stringResource(R.string.files_new_folder_name)) }) },
            confirmButton = {
                TextButton(enabled = folderName.isNotBlank(), onClick = {
                    newFolder = false
                    val made = spot.child(Names.clean(folderName))
                    scope.launch {
                        runCatching { files.mkdir(made) }
                        spot = made
                        reload++
                    }
                }) { Text(stringResource(R.string.files_create)) }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text(stringResource(R.string.files_cancel)) } },
        )
    }
}

/** Le tre tacche della privacy: verdi quelle accese. */
@Composable
fun PrivacyNotches(level: Int) {
    val on = Color(0xFF4F6B5A)
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i -> Box(Modifier.size(6.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(if (i < level) on else off)) }
    }
}

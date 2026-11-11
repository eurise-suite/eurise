package eu.eurise.browser

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/** Un file scelto per l'importazione, già letto: cosa contiene. Il contenuto non resta in memoria. */
class PickedFile(val uri: Uri, val name: String, val passwords: Boolean, val scan: ImportScan)

/** Importa (tavola BrwImportaPwd): password e segnalibri da Vivaldi, Chrome, Brave, Edge, Firefox. */
@Composable
internal fun ImportScreen(
    model: BrowserModel,
    actions: BrowserActions,
    installed: (List<String>) -> Boolean,
    onRun: (passwords: Boolean, bookmarks: Boolean, deleteFiles: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val found = remember { Imports.SOURCES.filter { installed(it.packages) } }
    var source by remember { mutableStateOf(found.firstOrNull() ?: Imports.SOURCES[1]) }
    var takePasswords by remember { mutableStateOf(true) }
    var takeBookmarks by remember { mutableStateOf(true) }
    var deleteFiles by remember { mutableStateOf(true) }
    val passwords = model.picked.filter { it.passwords }
    val bookmarks = model.picked.filter { !it.passwords }
    val pwdCount = passwords.sumOf { it.scan.passwords }
    val dupCount = passwords.sumOf { it.scan.duplicates }
    val bmCount = bookmarks.sumOf { it.scan.bookmarks }
    val folderCount = bookmarks.sumOf { it.scan.folders }

    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.brw_back)
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose).semantics { contentDescription = back }, contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.chevronLeft(2.2f), null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.brw_import_title), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(horizontal = 14.dp)) {
                found.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                    SourceRow(s.label, stringResource(R.string.brw_import_found), stringResource(R.string.brw_import_choose), strong = s == source) {
                        source = s
                        actions.openApp(s.packages)
                    }
                }
                if (found.isNotEmpty()) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                SourceRow(stringResource(R.string.brw_import_others), stringResource(R.string.brw_import_others_sub), stringResource(R.string.brw_import_from_file), strong = found.isEmpty()) {
                    actions.pickImportFiles()
                }
            }
            val steps = when (source.steps) {
                Imports.R_STEPS_VIVALDI -> R.string.brw_import_steps_vivaldi
                Imports.R_STEPS_FIREFOX -> R.string.brw_import_steps_firefox
                else -> R.string.brw_import_steps_chrome
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                stringResource(steps, source.label).split("\n").forEachIndexed { i, line ->
                    Row {
                        Text("${i + 1}.", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                        Text(line, fontSize = 13.5.sp, lineHeight = 20.sp)
                    }
                }
                Spacer(Modifier.size(4.dp))
                Pill(stringResource(R.string.brw_import_pick), EuriseIcons.folder(2f), Modifier.fillMaxWidth(), strong = model.picked.isEmpty()) { actions.pickImportFiles() }
            }
            if (model.picked.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.brw_import_found_in_files), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    if (pwdCount > 0) FoundRow(
                        stringResource(R.string.brw_import_passwords, pwdCount) +
                            (if (dupCount > 0) " · " + stringResource(R.string.brw_import_duplicates, dupCount) else ""),
                        takePasswords,
                    ) { takePasswords = it }
                    if (bmCount > 0) FoundRow(stringResource(R.string.brw_import_bookmarks, bmCount, folderCount), takeBookmarks) { takeBookmarks = it }
                    if (pwdCount == 0 && bmCount == 0) Text(stringResource(R.string.brw_import_nothing), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                    FoundRow(stringResource(R.string.brw_import_delete), deleteFiles) { deleteFiles = it }
                    Text(model.picked.joinToString(" · ") { it.name }, fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x3385672B)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(EuriseIcons.shield(2f), null, tint = Half, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.brw_import_check), fontSize = 12.5.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.size(8.dp))
        }
        val canPasswords = pwdCount > 0 && takePasswords
        val canBookmarks = bmCount > 0 && takeBookmarks
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            Pill(
                stringResource(if (canPasswords) R.string.brw_import_run_passwords else R.string.brw_import_run_bookmarks),
                EuriseIcons.download(2f), Modifier.fillMaxWidth().heightIn(min = 52.dp), strong = canPasswords || canBookmarks,
            ) { if (canPasswords || canBookmarks) onRun(canPasswords, canBookmarks, deleteFiles) }
        }
    }
}

@Composable
private fun SourceRow(title: String, sub: String, button: String, strong: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(scheme.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.globe(2.2f), null, tint = scheme.primary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        Chip(button, strong = strong, onClick = onClick)
    }
}

@Composable
private fun FoundRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

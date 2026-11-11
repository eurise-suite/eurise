package eu.eurise.core.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * «Apri da…» (tavola FileApriDa): i recenti ovunque siano (ciò che è passato da Eurise File), la ricerca per nome, poi
 * telefono e cloud da sfogliare. Si vedono solo i file con le [extensions] chieste (vuote: tutti). [onSystem], se c'è,
 * apre il selettore di Android come ultima strada.
 */
@Composable
fun OpenFromScreen(forApp: String, extensions: Set<String>, onPicked: (File, Entry?) -> Unit, onCancel: () -> Unit, onSystem: (() -> Unit)? = null) {
    val context = LocalContext.current
    val files = remember { EuriseFiles.get(context) }
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val places = remember { files.places() }
    var query by remember { mutableStateOf("") }
    var browsing by remember { mutableStateOf<Spot?>(null) }
    var entries by remember { mutableStateOf<List<Entry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }

    fun wanted(name: String) = extensions.isEmpty() || name.substringAfterLast('.', "").lowercase() in extensions
    fun open(entry: Entry) {
        opening = true
        scope.launch {
            val local = runCatching { files.fetch(entry) }.getOrNull()
            opening = false
            if (local == null) android.widget.Toast.makeText(context, R.string.files_open_failed, android.widget.Toast.LENGTH_LONG).show()
            else {
                files.remember("open", entry.spot.parent ?: entry.spot, entry.name)
                onPicked(local, entry)
            }
        }
    }

    BackHandler { val b = browsing; if (b != null) browsing = b.parent?.takeIf { it.path.isNotEmpty() || !b.isPhone } else onCancel() }
    LaunchedEffect(browsing) {
        val b = browsing ?: return@LaunchedEffect
        entries = null
        failed = false
        entries = runCatching { files.list(b) }.onFailure { failed = true }.getOrDefault(emptyList()).filter { it.folder || wanted(it.name) }
    }

    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.files_cancel)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.files_open_from), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.files_for, forApp), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7A3440))
            }
            Spacer(Modifier.width(72.dp))
        }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.files_search)) },
            leadingIcon = { Icon(EuriseIcons.search(), null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val b = browsing
            if (b == null) {
                val recents = files.memory.recentFiles(24).filter { wanted(it.name) && (query.isBlank() || it.name.contains(query, ignoreCase = true)) }
                if (recents.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.files_recent))
                    Card {
                        recents.forEachIndexed { i, r ->
                            if (i > 0) CardDivider()
                            FileRow(r.name, files.label(r.spot) + " › " + r.spot.path.split('/').takeLast(2).joinToString(" › ")) {
                                open(Entry(r.spot.child(r.name), r.name, folder = false, size = null, modified = r.at))
                            }
                        }
                    }
                }
                SectionLabel(stringResource(R.string.files_browse))
                Card {
                    places.forEachIndexed { i, p ->
                        if (i > 0) CardDivider()
                        FolderRow(p.label) { browsing = p.spot }
                    }
                }
                if (onSystem != null) Text(stringResource(R.string.files_other_app) + " ›", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(6.dp)).clickable { onSystem() })
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    places.forEach { p -> FilterPill(p.label, dot = null, selected = p.account == b.account) { browsing = p.spot } }
                }
                Text(listOf(files.label(b)).plus(b.path.split('/').filter { it.isNotEmpty() }).joinToString(" › "), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                Card {
                    b.parent?.let { up -> FolderRow("‹ " + stringResource(R.string.files_up)) { browsing = up }; CardDivider() }
                    val list = entries?.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                    when {
                        list == null -> Text("…", modifier = Modifier.padding(14.dp), color = scheme.onSurfaceVariant)
                        failed -> Text(stringResource(R.string.files_cant_list), fontSize = 13.sp, modifier = Modifier.padding(14.dp), color = scheme.onSurfaceVariant)
                        list.isEmpty() -> Text(stringResource(R.string.files_empty), fontSize = 13.sp, modifier = Modifier.padding(14.dp), color = scheme.onSurfaceVariant)
                        else -> list.forEachIndexed { i, e ->
                            if (i > 0) CardDivider()
                            if (e.folder) FolderRow(e.name) { browsing = e.spot } else FileRow(e.name, sizeLabel(e.size)) { open(e) }
                        }
                    }
                }
            }
        }
        Text(stringResource(if (opening) R.string.files_opening else R.string.files_open_note), fontSize = 12.5.sp, lineHeight = 18.sp, color = Color(0xFF2B4A37),
            modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFEAF1EC)).padding(12.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
        content()
    }
}

@Composable
private fun FolderRow(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Icon(EuriseIcons.folder(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FileRow(name: String, sub: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ext = name.substringAfterLast('.', "").uppercase().take(4)
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp, 44.dp).clip(RoundedCornerShape(6.dp)).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(ext, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun sizeLabel(size: Long?): String = when {
    size == null -> ""
    size < 1024 * 1024 -> "${(size + 1023) / 1024} KB"
    else -> String.format(java.util.Locale.ITALY, "%.1f MB", size / 1048576.0)
}

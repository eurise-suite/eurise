package eu.eurise.feature.launcher.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.apps.AppEntry
import eu.eurise.feature.launcher.apps.AppIcon

/**
 * Una cartella di app. [place] dice dove sta: "home" tra i preferiti, oppure l'id di una categoria.
 * [keys] sono le app contenute, nell'ordine scelto.
 */
data class AppFolder(val id: String, val name: String, val place: String, val keys: List<String>) {
    val key: String get() = KEY_PREFIX + id

    companion object {
        const val KEY_PREFIX = "folder:"
        const val HOME = "home"
    }
}

fun List<AppFolder>.encodeFolders(): String = joinToString("\n") { f ->
    listOf(f.id, f.name.replace('\t', ' ').replace('\n', ' '), f.place, f.keys.joinToString("|")).joinToString("\t")
}

fun decodeFolders(raw: String?): List<AppFolder> = raw.orEmpty().lines().mapNotNull { line ->
    val bits = line.split('\t')
    if (bits.size < 4) null else AppFolder(bits[0], bits[1], bits[2], bits[3].split('|').filter { it.isNotBlank() })
}

/** Icona della cartella: tondo di vetro con le prime quattro app in piccolo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderTile(
    folder: AppFolder,
    apps: List<AppEntry>,
    dark: Boolean,
    size: Dp,
    showLabel: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen).padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                .padding(size * 0.14f),
        ) {
            val mini = size * 0.33f
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                apps.take(4).chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { AppIcon(it, mini, dark) }
                        if (row.size == 1) Spacer(Modifier.size(mini))
                    }
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                folder.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.widthIn(max = size + 20.dp),
            )
        }
    }
}

/**
 * Cartella aperta: nome modificabile, app della cartella, "+ App" per aggiungerne. Tenendo premuta un'app
 * la si toglie dalla cartella.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderOverlay(
    folder: AppFolder,
    apps: List<AppEntry>,
    dark: Boolean,
    iconSize: Dp,
    badges: Map<String, Int>,
    onOpenApp: (AppEntry) -> Unit,
    onRename: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(22.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (renaming) {
                        BasicTextField(
                            value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                            textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(folder.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    stringResource(if (renaming) R.string.card_done else R.string.folder_rename),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        if (renaming && name.isNotBlank()) onRename(name.trim())
                        renaming = !renaming
                    }.padding(6.dp),
                )
                Text(
                    stringResource(R.string.folder_add), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAdd).padding(6.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(apps, key = { it.key }) { app ->
                    var menu by remember { mutableStateOf(false) }
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .combinedClickable(onClick = { onOpenApp(app) }, onLongClick = { menu = true })
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(app, iconSize, dark, badges[app.packageName] ?: 0)
                            Spacer(Modifier.height(5.dp))
                            Text(app.label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = iconSize + 18.dp))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.folder_remove_app)) },
                                leadingIcon = { Icon(EuriseIcons.minus(), null) },
                                onClick = { menu = false; onRemove(app) },
                            )
                        }
                    }
                }
            }
            Text(stringResource(R.string.folder_hint), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Scelta delle app da mettere in una cartella: elenco con spunte, in alto le già scelte. */
@Composable
fun AppPicker(
    title: String,
    apps: List<AppEntry>,
    preselected: Set<String>,
    dark: Boolean,
    onDone: (List<AppEntry>) -> Unit,
    onClose: () -> Unit,
) {
    var chosen by remember { mutableStateOf(preselected) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.card_done), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDone(apps.filter { it.key in chosen }) }.padding(6.dp),
                )
            }
            val sorted = apps.sortedWith(compareByDescending<AppEntry> { it.key in preselected }.thenBy { it.label.lowercase() })
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(sorted, key = { it.key }) { app ->
                    val on = app.key in chosen
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Checkbox) { chosen = if (on) chosen - app.key else chosen + app.key }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app, 38.dp, dark)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(EuriseIcons.task(2.6f), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
    }
}

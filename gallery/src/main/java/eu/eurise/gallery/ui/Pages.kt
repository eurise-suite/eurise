package eu.eurise.gallery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.cloud.NextcloudLogin
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.component.GlassDefaults
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.gallery.GalleryViewModel
import eu.eurise.gallery.PlaceData
import eu.eurise.gallery.R
import eu.eurise.gallery.data.Diary
import eu.eurise.gallery.data.GallerySettings
import eu.eurise.gallery.logic.DeleteChoice
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.Place
import eu.eurise.gallery.logic.albums
import eu.eurise.gallery.logic.cellLat
import eu.eurise.gallery.logic.cellLon
import eu.eurise.gallery.logic.matchesSearch
import eu.eurise.gallery.logic.searchWords
import kotlinx.coroutines.launch
import java.util.Locale

private val BottomClearance = GlassDefaults.PillHeight + 56.dp

/** Testata delle schermate che si aprono sopra le altre. */
@Composable
internal fun ScreenBar(title: String, onClose: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(EuriseIcons.chevronLeft(), stringResource(R.string.back), modifier = Modifier.size(22.dp)) }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Fondo opaco di una schermata sopra le altre, che tiene per sé i tocchi. */
@Composable
internal fun Modifier.screen(): Modifier = this.fillMaxSize().background(MaterialTheme.colorScheme.background)
    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }

@Composable
internal fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp)) { content() }
}

@Composable
internal fun ToolRow(icon: ImageVector, title: String, hint: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            hint?.let { Text(it, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(EuriseIcons.chevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun SwitchRow(title: String, hint: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            hint?.let { Text(it, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(on, onChange)
    }
}

@Composable
internal fun AlbumsPage(
    all: List<MediaItem>,
    diaries: List<Diary>,
    uploadOn: Boolean,
    cloudLabel: String,
    onOpen: (String) -> Unit,
    onCloud: () -> Unit,
    onSecure: () -> Unit,
    onCleanup: () -> Unit,
    onDiary: (Long) -> Unit,
) {
    val list = remember(all) { albums(all.filter { !it.cloudOnly }) }
    val byUri = remember(all) { all.associateBy { it.uri } }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Text(stringResource(R.string.tab_albums), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BottomClearance),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "tools", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                    ToolRow(EuriseIcons.cloud(2.3f), stringResource(R.string.tool_cloud), if (uploadOn) cloudLabel else stringResource(R.string.tool_cloud_off), onCloud)
                    CardDivider()
                    ToolRow(EuriseIcons.lock(), stringResource(R.string.tool_secure), stringResource(R.string.tool_secure_hint), onSecure)
                    CardDivider()
                    ToolRow(EuriseIcons.spark(), stringResource(R.string.tool_cleanup), stringResource(R.string.tool_cleanup_hint), onCleanup)
                }
            }
            if (diaries.isNotEmpty()) {
                item(key = "diaries-label", span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.diaries_title), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                items(diaries, key = { "diary-${it.id}" }) { diary ->
                    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { onDiary(diary.id) }) {
                        val cover = diary.uris.firstNotNullOfOrNull(byUri::get)
                        if (cover != null) Thumb(cover, Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(16.dp)))
                        else Box(Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) { Icon(EuriseIcons.book(), null) }
                        Text(diary.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                        Text(pluralStringResource(R.plurals.items_count, diary.uris.size, diary.uris.size), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
            item(key = "folders-label", span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.folders_title), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            items(list, key = { "album-${it.first}" }) { (name, photos) ->
                Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { onOpen(name) }) {
                    Thumb(photos.first(), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                    Text(name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                    Text(pluralStringResource(R.plurals.items_count, photos.size, photos.size), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
    }
}

/** Il nome del luogo o, senza nomi, le coordinate arrotondate. */
@Composable
internal fun placeTitle(place: Place): String = place.name ?: stringResource(
    R.string.places_unnamed,
    String.format(Locale.getDefault(), "%.1f°", cellLat(place.cell)),
    String.format(Locale.getDefault(), "%.1f°", cellLon(place.cell)),
)

@Composable
internal fun PlacesPage(list: List<Place>, settings: GallerySettings, vm: GalleryViewModel, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val remaining by vm.remaining.collectAsStateWithLifecycle()
    var explainNames by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.setPlaces(true) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Text(stringResource(R.string.tab_places), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp))
        if (!settings.placesOn) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.places_off_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.places_off_message), fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) vm.setPlaces(true)
                    else ask.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                }) { Text(stringResource(R.string.places_on)) }
            }
            return
        }
        LazyColumn(contentPadding = PaddingValues(bottom = BottomClearance), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "switches") {
                Card {
                    SwitchRow(stringResource(R.string.places_switch), null, true) { vm.setPlaces(false) }
                    CardDivider()
                    SwitchRow(stringResource(R.string.places_names), stringResource(R.string.places_names_hint), settings.namesOn) { on ->
                        if (on) explainNames = true else vm.setNames(false)
                    }
                }
            }
            if (remaining > 0) item(key = "reading") {
                Text(stringResource(R.string.places_reading, remaining), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            if (list.isEmpty() && remaining == 0) item(key = "empty") {
                EmptyState(icon = EuriseIcons.map(), title = stringResource(R.string.places_empty_title), message = stringResource(R.string.places_empty_message))
            }
            items(list, key = { it.key }) { place ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(place.key) }.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Thumb(place.items.first(), Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(placeTitle(place), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pluralStringResource(R.plurals.items_count, place.items.size, place.items.size), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (explainNames) {
        Explain(stringResource(R.string.places_names_explain), onCancel = { explainNames = false }) {
            explainNames = false
            vm.setNames(true)
        }
    }
}

/** Il foglio "Cosa cambia per la tua privacy", prima di accendere qualcosa che esce dal telefono. */
@Composable
internal fun Explain(text: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.cloud_explain_title)) },
        text = { Text(text, fontSize = 14.sp, lineHeight = 20.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.cloud_explain_ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun SearchPage(all: List<MediaItem>, places: PlaceData, onOpen: (List<MediaItem>, MediaItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(all, places, query) {
        if (searchWords(query).isEmpty()) emptyList()
        else all.filter { matchesSearch(it, query, places.cells[it.uri]?.let(places.names::get)) }.sortedByDescending { it.takenAt }
    }
    val suggestions = remember(all) { albums(all).take(6).map { it.first }.filter { it.isNotEmpty() } }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Text(stringResource(R.string.tab_search), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp))
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            leadingIcon = { Icon(EuriseIcons.search(), null, modifier = Modifier.size(20.dp)) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(EuriseIcons.close(), stringResource(R.string.close), modifier = Modifier.size(18.dp)) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (query.isBlank()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.take(3).forEach { s -> FilterPill(s, null, false) { query = s } }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(icon = EuriseIcons.search(), title = stringResource(R.string.search_start_title), message = stringResource(R.string.search_start_message))
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(icon = EuriseIcons.search(), title = stringResource(R.string.search_empty_title), message = stringResource(R.string.search_empty_message))
            }
        } else {
            Text(pluralStringResource(R.plurals.items_count, results.size, results.size), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = BottomClearance),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(results, key = { it.uri }) { item -> Tile(item, false, false, onClick = { onOpen(results, item) }, onLongClick = {}) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareSheet(onClose: () -> Unit, onChoose: (Boolean) -> Unit, onSaveIn: (() -> Unit)? = null) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.share_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            Card {
                ToolRow(EuriseIcons.shield(), stringResource(R.string.share_clean), stringResource(R.string.share_clean_hint)) { onChoose(true) }
                CardDivider()
                ToolRow(EuriseIcons.share(), stringResource(R.string.share_original), stringResource(R.string.share_original_hint)) { onChoose(false) }
                if (onSaveIn != null) {
                    CardDivider()
                    ToolRow(EuriseIcons.folder(), stringResource(R.string.save_in), stringResource(R.string.save_in_hint)) { onSaveIn() }
                }
            }
        }
    }
}

/** Da dove togliere le foto: telefono, cloud o tutti e due, come nell'anteprima approvata. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteSheet(items: List<MediaItem>, cloudLabel: String, onClose: () -> Unit, onDelete: (DeleteChoice) -> Unit, onProtect: () -> Unit) {
    val context = LocalContext.current
    val anyUploaded = items.any { it.remote != null && !it.cloudOnly }
    val allCloud = items.all { it.cloudOnly }
    var choice by remember { mutableStateOf(if (allCloud) DeleteChoice.Both else DeleteChoice.PhoneOnly) }
    val freed = Formatter.formatShortFileSize(context, items.filter { !it.cloudOnly && it.remote != null }.sumOf { it.size })
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val title = if (anyUploaded || allCloud) R.plurals.delete_title else R.plurals.trash_title
            Text(pluralStringResource(title, items.size, items.size), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
            if (anyUploaded) {
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                    Choice(stringResource(R.string.delete_phone), stringResource(R.string.delete_phone_hint, cloudLabel, freed), choice == DeleteChoice.PhoneOnly) { choice = DeleteChoice.PhoneOnly }
                    CardDivider()
                    Choice(stringResource(R.string.delete_cloud, cloudLabel), stringResource(R.string.delete_cloud_hint), choice == DeleteChoice.CloudOnly) { choice = DeleteChoice.CloudOnly }
                    CardDivider()
                    Choice(stringResource(R.string.delete_both), stringResource(R.string.delete_both_hint), choice == DeleteChoice.Both, danger = true) { choice = DeleteChoice.Both }
                }
            } else if (!allCloud) {
                Text(stringResource(R.string.trash_hint), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            val label = when {
                allCloud -> stringResource(R.string.delete_go_cloud, cloudLabel)
                !anyUploaded -> stringResource(R.string.delete_go_trash)
                choice == DeleteChoice.PhoneOnly -> stringResource(R.string.delete_go_phone)
                choice == DeleteChoice.CloudOnly -> stringResource(R.string.delete_go_cloud, cloudLabel)
                else -> stringResource(R.string.delete_go_both)
            }
            Button(onClick = { onDelete(choice) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            if (!allCloud) {
                TextButton(onClick = onProtect, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Icon(EuriseIcons.lock(), null, modifier = Modifier.size(17.dp))
                    Text(stringResource(R.string.move_secure), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Choice(title: String, hint: String, selected: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (hint.isNotEmpty()) Text(hint, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CloudScreen(vm: GalleryViewModel, all: List<MediaItem>, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val account = accounts.firstOrNull { it.id == settings.accountId }
    var explain by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf("") }
    val folders = remember(all) { albums(all.filter { !it.cloudOnly }).map { it.first } }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Senza il permesso il caricamento funziona lo stesso, ma Android toglie la posizione dalle copie.
        vm.updateCloud { it.copy(uploadOn = true) }
    }
    fun connect(kind: CloudKind, address: String) {
        waiting = true
        failed = false
        scope.launch {
            val ok = vm.connect(kind, address) { url ->
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())) }
            }
            waiting = false
            failed = !ok
        }
    }
    Column(Modifier.screen()) {
        ScreenBar(stringResource(R.string.cloud_title), onClose)
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.navigationBarsPadding()) {
            item(key = "where") {
                SectionLabel(stringResource(R.string.cloud_account))
                Card {
                    if (account != null) {
                        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(account.label(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(account.name, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        CardDivider()
                    }
                    ToolRow(EuriseIcons.cloud(2.3f), stringResource(R.string.cloud_link_murena), null) { connect(CloudKind.Murena, NextcloudLogin.MURENA_SERVER) }
                    CardDivider()
                    OutlinedTextField(server, { server = it }, singleLine = true, label = { Text(stringResource(R.string.cloud_server_hint)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    TextButton(enabled = server.isNotBlank(), onClick = { connect(CloudKind.Nextcloud, server) }) { Text(stringResource(R.string.cloud_link_nextcloud)) }
                }
                if (waiting) Text(stringResource(R.string.cloud_waiting), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                if (failed) Text(stringResource(R.string.cloud_link_failed), fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            if (account != null) {
                item(key = "switch") {
                    Card {
                        SwitchRow(stringResource(R.string.cloud_switch), null, settings.uploadOn) { on ->
                            if (on) explain = true else vm.updateCloud { it.copy(uploadOn = false) }
                        }
                    }
                    vm.store.lastError?.let {
                        Text(stringResource(R.string.cloud_error, it), fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    }
                }
                item(key = "when") {
                    SectionLabel(stringResource(R.string.cloud_when))
                    Card {
                        SwitchRow(stringResource(R.string.cloud_wifi), null, settings.wifiOnly) { on -> vm.updateCloud { it.copy(wifiOnly = on) } }
                        CardDivider()
                        SwitchRow(stringResource(R.string.cloud_charging), null, settings.chargingOnly) { on -> vm.updateCloud { it.copy(chargingOnly = on) } }
                    }
                }
                item(key = "from") {
                    SectionLabel(stringResource(R.string.cloud_from))
                    Card {
                        Choice(stringResource(R.string.cloud_from_now), "", settings.fromMillis != 0L) {
                            vm.updateCloud { it.copy(fromMillis = System.currentTimeMillis()) }
                        }
                        CardDivider()
                        Choice(stringResource(R.string.cloud_from_all), "", settings.fromMillis == 0L) {
                            vm.updateCloud { it.copy(fromMillis = 0) }
                        }
                    }
                }
                item(key = "folders") {
                    SectionLabel(stringResource(R.string.cloud_folders))
                    Card {
                        folders.forEachIndexed { i, name ->
                            if (i > 0) CardDivider()
                            SwitchRow(name, null, name in settings.buckets) { on ->
                                vm.updateCloud { s -> s.copy(buckets = if (on) s.buckets + name else s.buckets - name) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (explain) {
        Explain(stringResource(R.string.cloud_explain, account?.label().orEmpty()), onCancel = { explain = false }) {
            explain = false
            ask.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }
}


package eu.eurise.gallery.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.text.format.Formatter
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.gallery.GalleryViewModel
import eu.eurise.gallery.PlaceData
import eu.eurise.gallery.R
import eu.eurise.gallery.data.Diary
import eu.eurise.gallery.data.SecureItem
import eu.eurise.gallery.data.contentHash
import eu.eurise.gallery.data.editorPreview
import eu.eurise.gallery.data.fileUri
import eu.eurise.gallery.data.saveEdited
import eu.eurise.gallery.logic.Adjust
import eu.eurise.gallery.logic.Crop
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.adjustMatrix
import eu.eurise.gallery.logic.alreadyUploaded
import eu.eurise.gallery.logic.bigVideos
import eu.eurise.gallery.logic.bursts
import eu.eurise.gallery.logic.dayPlace
import eu.eurise.gallery.logic.dragCrop
import eu.eurise.gallery.logic.duplicateCandidates
import eu.eurise.gallery.logic.extras
import eu.eurise.gallery.logic.groupByDay
import eu.eurise.gallery.logic.oldScreenshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

// Diario di viaggio

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryPicker(diaries: List<Diary>, onClose: () -> Unit, onNew: () -> Unit, onChoose: (Long) -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.diary_choose), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            Card {
                ToolRow(EuriseIcons.plus(), stringResource(R.string.diary_new), null, onNew)
                diaries.forEach { d ->
                    CardDivider()
                    ToolRow(EuriseIcons.book(), d.title, null) { onChoose(d.id) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DiaryScreen(
    diary: Diary,
    byUri: Map<String, MediaItem>,
    places: PlaceData,
    vm: GalleryViewModel,
    actions: MediaActions,
    onOpen: (List<MediaItem>, MediaItem) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var title by remember(diary.id) { mutableStateOf(diary.title) }
    val notes = remember(diary.id) { mutableStateMapOf<String, String>().apply { putAll(diary.notes) } }
    val deleted = remember(diary.id) { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    // Si salva all'uscita: riscrivere il database a ogni lettera farebbe rileggere tutta la Galleria.
    DisposableEffect(diary.id) {
        onDispose {
            if (!deleted.value) {
                val finalTitle = title.ifBlank { diary.title }
                val finalNotes = notes.toMap()
                vm.io {
                    vm.store.renameDiary(diary.id, finalTitle)
                    (diary.notes.keys + finalNotes.keys).forEach { day ->
                        val text = finalNotes[day].orEmpty()
                        if (text != diary.notes[day].orEmpty()) vm.store.setDiaryNote(diary.id, day, text)
                    }
                }
            }
        }
    }
    val items = remember(diary.uris, byUri) { diary.uris.mapNotNull(byUri::get) }
    val days = remember(items) { groupByDay(items).asReversed() }
    val ordered = remember(days) { days.flatMap { it.items } }
    Column(Modifier.screen()) {
        ScreenBar(title, onClose) {
            IconButton(onClick = { actions.shareClean(ordered) }, enabled = ordered.isNotEmpty()) { Icon(EuriseIcons.share(), stringResource(R.string.share_clean_button), modifier = Modifier.size(21.dp)) }
            IconButton(onClick = { confirm = true }) { Icon(EuriseIcons.trash(), stringResource(R.string.diary_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(21.dp)) }
        }
        LazyColumn(Modifier.imePadding().navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "title") {
                OutlinedTextField(title, { title = it }, singleLine = true, label = { Text(stringResource(R.string.diary_title_label)) }, modifier = Modifier.fillMaxWidth())
            }
            days.forEach { group ->
                val day = group.date.toString()
                item(key = "head-$day") {
                    val place = remember(group, places) { dayPlace(group.items, places.cells, places.names) }
                    Text(if (place != null) "${longDate(group.date)} · $place" else longDate(group.date), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
                item(key = "note-$day") {
                    OutlinedTextField(notes[day].orEmpty(), { notes[day] = it }, placeholder = { Text(stringResource(R.string.diary_note_hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
                items(group.items.reversed().chunked(3), key = { row -> "row-${row.first().uri}" }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { item ->
                            Box(Modifier.weight(1f).aspectRatio(1f).combinedClickable(onClick = { onOpen(ordered, item) }, onLongClick = { vm.io { vm.store.removeFromDiary(diary.id, item.uri) } })) {
                                Thumb(item, Modifier.fillMaxSize())
                            }
                        }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.diary_delete)) },
            text = { Text(stringResource(R.string.diary_delete_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    deleted.value = true
                    vm.io { vm.store.deleteDiary(diary.id) }
                    onClose()
                }) { Text(stringResource(R.string.diary_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// Pulizia

private class CleanupGroup(val title: Int, val hint: Int, val items: List<MediaItem>, val preselected: Set<Long>)

@Composable
internal fun CleanupScreen(all: List<MediaItem>, ops: SystemOps, onClose: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableIntStateOf(-1) }
    BackHandler { if (open >= 0) open = -1 else onClose() }
    val duplicates by produceState<List<MediaItem>?>(null, all) {
        value = withContext(Dispatchers.IO) {
            extras(duplicateCandidates(all).flatMap { group -> group.groupBy { contentHash(context, it.uri) }.filterKeys { it != null }.values.filter { it.size > 1 } })
        }
    }
    val groups = remember(all, duplicates) {
        val now = System.currentTimeMillis()
        val shots = bursts(all)
        val screenshots = oldScreenshots(all, now)
        val uploaded = alreadyUploaded(all)
        listOf(
            CleanupGroup(R.string.cleanup_duplicates, R.string.cleanup_duplicates_hint, duplicates.orEmpty(), duplicates.orEmpty().map { it.id }.toSet()),
            CleanupGroup(R.string.cleanup_bursts, R.string.cleanup_bursts_hint, shots.flatten(), extras(shots).map { it.id }.toSet()),
            CleanupGroup(R.string.cleanup_screenshots, R.string.cleanup_screenshots_hint, screenshots, screenshots.map { it.id }.toSet()),
            CleanupGroup(R.string.cleanup_videos, R.string.cleanup_videos_hint, bigVideos(all), emptySet()),
            CleanupGroup(R.string.cleanup_uploaded, R.string.cleanup_uploaded_hint, uploaded, uploaded.map { it.id }.toSet()),
        )
    }
    val group = groups.getOrNull(open)
    if (group == null) {
        Column(Modifier.screen()) {
            ScreenBar(stringResource(R.string.cleanup_title), onClose)
            if (duplicates == null) Text(stringResource(R.string.cleanup_checking), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
            Card {
                groups.forEachIndexed { i, g ->
                    if (i > 0) CardDivider()
                    val weight = Formatter.formatShortFileSize(context, g.items.filter { it.id in g.preselected || g.preselected.isEmpty() }.sumOf { it.size })
                    val count = pluralStringResource(R.plurals.items_count, g.items.size, g.items.size)
                    ToolRow(
                        EuriseIcons.spark(), stringResource(g.title),
                        if (g.items.isEmpty()) stringResource(R.string.cleanup_none) else "${stringResource(g.hint)} · ${stringResource(R.string.cleanup_summary, count, weight)}",
                    ) { if (g.items.isNotEmpty()) open = i }
                }
            }
        }
    } else {
        var chosen by remember(open) { mutableStateOf(group.preselected) }
        val selectedItems = group.items.filter { it.id in chosen }
        Box(Modifier.screen()) {
            Column(Modifier.fillMaxSize()) {
                ScreenBar(stringResource(group.title), { open = -1 })
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(group.items, key = { it.uri }) { item ->
                        val toggle = { chosen = if (item.id in chosen) chosen - item.id else chosen + item.id }
                        Tile(item, item.id in chosen, false, onClick = toggle, onLongClick = toggle)
                    }
                }
            }
            Button(
                enabled = selectedItems.isNotEmpty(),
                onClick = { ops.trash(selectedItems) { ok -> if (ok) open = -1 } },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp).height(50.dp),
            ) { Text(stringResource(R.string.cleanup_go, Formatter.formatShortFileSize(context, selectedItems.sumOf { it.size })), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// Cartella protetta

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SecureScreen(vm: GalleryViewModel, ops: SystemOps, moving: List<MediaItem>?, cloudLabel: String, onMoved: () -> Unit, onClose: () -> Unit) {
    val folder = vm.secure
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by folder.items.collectAsStateWithLifecycle()
    val working = remember { mutableStateOf(false) }
    var isSetUp by remember { mutableStateOf(folder.isSetUp) }
    var code by remember { mutableStateOf<String?>(null) }
    var keepOnCloud by remember { mutableStateOf<Boolean?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val failedText = stringResource(R.string.action_failed)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    BackHandler(enabled = viewing == null, onBack = onClose)

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !working.value) folder.lock()
            if (event == Lifecycle.Event.ON_RESUME) folder.clearPlayback()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            folder.lock()
        }
    }

    val needsCloudChoice = moving?.any { it.remote != null } == true && keepOnCloud == null
    LaunchedEffect(items != null, moving, needsCloudChoice, code) {
        val list = moving ?: return@LaunchedEffect
        if (items == null || needsCloudChoice || code != null || working.value) return@LaunchedEffect
        working.value = true
        val added = ArrayList<String>()
        val copied = withContext(Dispatchers.IO) { runCatching { list.forEach { added += folder.add(it) } }.isSuccess }
        if (!copied) {
            withContext(Dispatchers.IO) { added.forEach(folder::drop) }
            Toast.makeText(context, failedText, Toast.LENGTH_LONG).show()
            working.value = false
            onMoved()
            return@LaunchedEffect
        }
        // Eliminazione vera, non cestino: nel cestino la foto resterebbe in chiaro per 30 giorni.
        ops.erase(list) { ok ->
            scope.launch(Dispatchers.IO) {
                if (ok) folder.refresh() else added.forEach(folder::drop)
                if (ok && keepOnCloud == false) withContext(Dispatchers.Main) { vm.removeFromCloud(list.filter { it.remote != null }) }
                withContext(Dispatchers.Main) { working.value = false; onMoved() }
            }
        }
    }

    Column(Modifier.screen()) {
        ScreenBar(stringResource(R.string.secure_title), onClose)
        when {
            code != null -> RecoveryCode(code.orEmpty()) { code = null }
            !isSetUp -> SecureSetup { pass ->
                scope.launch {
                    code = withContext(Dispatchers.Default) { folder.setup(pass) }
                    isSetUp = true
                }
            }
            items == null -> SecureUnlock { secret, wrong ->
                scope.launch { if (!withContext(Dispatchers.Default) { folder.unlock(secret) }) wrong() }
            }
            working.value -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.secure_working))
                }
            }
            items.orEmpty().isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(icon = EuriseIcons.lock(), title = stringResource(R.string.secure_empty_title), message = stringResource(R.string.secure_empty_message))
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(items.orEmpty(), key = { it.id }) { item -> SecureTile(vm, item) { viewing = item.id } }
            }
        }
    }
    viewing?.let { id ->
        val list = items.orEmpty()
        if (list.none { it.id == id }) viewing = null else SecureViewer(vm, list, list.indexOfFirst { it.id == id }, onClose = { viewing = null })
    }
    if (needsCloudChoice && items != null && code == null) {
        AlertDialog(
            onDismissRequest = { keepOnCloud = true },
            title = { Text(stringResource(R.string.secure_cloud_title, cloudLabel)) },
            text = { Text(stringResource(R.string.secure_cloud_message)) },
            confirmButton = { TextButton(onClick = { keepOnCloud = false }) { Text(stringResource(R.string.secure_cloud_remove)) } },
            dismissButton = { TextButton(onClick = { keepOnCloud = true }) { Text(stringResource(R.string.secure_cloud_keep)) } },
        )
    }
}

@Composable
private fun SecureSetup(onCreate: (String) -> Unit) {
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.secure_setup_message), fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(pass, { pass = it }, singleLine = true, label = { Text(stringResource(R.string.secure_pass)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(again, { again = it }, singleLine = true, label = { Text(stringResource(R.string.secure_pass_again)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        val valid = pass.length >= 8 && pass == again
        if (pass.isNotEmpty() && !valid) Text(stringResource(R.string.secure_pass_short), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(enabled = valid && !busy, onClick = { busy = true; onCreate(pass) }) { Text(stringResource(R.string.secure_create)) }
    }
}

@Composable
private fun RecoveryCode(code: String, onDone: () -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.secure_code_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.secure_code_message), fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(code, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.secure_code_done)) }
    }
}

@Composable
private fun SecureUnlock(onUnlock: (String, () -> Unit) -> Unit) {
    var secret by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            secret, { secret = it; wrong = false }, singleLine = true, isError = wrong,
            label = { Text(stringResource(R.string.secure_unlock_message)) },
            supportingText = { if (wrong) Text(stringResource(R.string.secure_wrong)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(enabled = secret.isNotEmpty() && !busy, onClick = {
            busy = true
            onUnlock(secret) { wrong = true; busy = false }
        }) { Text(stringResource(R.string.secure_unlock)) }
    }
}

@Composable
private fun SecureTile(vm: GalleryViewModel, item: SecureItem, onClick: () -> Unit) {
    val image by produceState<ImageBitmap?>(null, item.id) { value = withContext(Dispatchers.IO) { vm.secure.thumbnail(item)?.asImageBitmap() } }
    Box(Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick)) {
        image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun SecureViewer(vm: GalleryViewModel, list: List<SecureItem>, start: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = start.coerceAtLeast(0)) { list.size }
    var confirmDelete by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<SecureItem?>(null) }
    val failedText = stringResource(R.string.action_failed)
    Box(Modifier.fillMaxSize().background(Color(0xFF14171C))) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { list[it].id }) { page ->
            val item = list[page]
            val image by produceState<ImageBitmap?>(null, item.id) {
                value = withContext(Dispatchers.IO) { if (item.isVideo) vm.secure.thumbnail(item)?.asImageBitmap() else vm.secure.image(item)?.asImageBitmap() }
            }
            ZoomableBitmap(image, item.isVideo, item.id, onPlay = { playing = item })
        }
        val current = list.getOrNull(pager.currentPage) ?: return@Box
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) { RoundAction(EuriseIcons.close(), stringResource(R.string.close), onClose) }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(EuriseIcons.download(), stringResource(R.string.secure_restore)) {
                scope.launch {
                    if (!withContext(Dispatchers.IO) { vm.secure.restore(current) }) Toast.makeText(context, failedText, Toast.LENGTH_LONG).show()
                }
            }
            RoundAction(EuriseIcons.trash(), stringResource(R.string.secure_delete)) { confirmDelete = true }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.secure_delete)) },
                text = { Text(stringResource(R.string.secure_delete_message)) },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; vm.io { vm.secure.drop(current.id) } }) { Text(stringResource(R.string.secure_delete)) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        // Video della cartella protetta: si guardano qui, senza passare da altre app.
        playing?.let { video ->
            VideoScreen(item = null, title = video.name, load = { withContext(Dispatchers.IO) { vm.secure.videoFile(video) }?.let(android.net.Uri::fromFile) }, actions = null, onClose = { playing = null })
        }
    }
}

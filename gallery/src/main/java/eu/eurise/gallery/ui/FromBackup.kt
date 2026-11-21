package eu.eurise.gallery.ui

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.gallery.GalleryViewModel
import eu.eurise.gallery.R
import eu.eurise.gallery.data.SecureFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Cartella protetta arrivata dal backup (tavole GalDalBackup, GalDalBackupApri, GalDalBackupUnisci, approvate il
// 21 novembre 2026): quella del telefono di prima, chiusa con la sua frase, che il ripristino ha messo accanto alla tua.

/** In cima alla tua cartella aperta: «Arrivata dal backup · N foto e video · Apri». */
@Composable
internal fun FromBackupCard(count: Int, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.lock(), null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.fb_card_title), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.fb_card_text, count), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onOpen, modifier = Modifier.height(44.dp)) { Text(stringResource(R.string.fb_open), fontWeight = FontWeight.Bold) }
        }
    }
}

/** La cartella dal backup: si apre con la sua frase o il suo codice, poi si sposta nella tua o si tiene a parte. */
@Composable
internal fun FromBackupScreen(vm: GalleryViewModel, other: SecureFolder, count: Int, mine: SecureFolder, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by other.items.collectAsStateWithLifecycle()
    var moving by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val movedText = stringResource(R.string.fb_moved)
    val failedText = stringResource(R.string.action_failed)
    BackHandler(enabled = viewing == null && moving == null, onBack = onClose)

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Fuori dall'app si richiude; lo spostamento in corso finisce comunque (usa copie delle chiavi).
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && moving == null) other.lock() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            other.lock()
        }
    }

    Column(Modifier.screen()) {
        val list = items
        val progress = moving
        when {
            progress != null -> {
                ScreenBar(stringResource(R.string.fb_title), onClose = {})
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.fb_moving, progress.first, progress.second))
                    }
                }
            }
            list == null -> FromBackupUnlock(onClose = onClose, onForgot = { forgot = true }) { secret, wrong ->
                scope.launch { if (!withContext(Dispatchers.Default) { other.unlock(secret) }) wrong() }
            }
            else -> {
                ScreenBar(stringResource(R.string.fb_title), onClose)
                Text(
                    stringResource(R.string.fb_open_sub, list.size), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(list, key = { it.id }) { item -> SecureTile(other, item) { viewing = item.id } }
                }
                // Le scelte in basso, sotto il pollice (tavola GalDalBackupUnisci).
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.fb_merge_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.fb_merge_text), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.fb_keep_apart), fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            moving = 0 to list.size
                            scope.launch {
                                val failed = withContext(Dispatchers.IO) {
                                    runCatching { other.moveAllInto(mine) { done, total -> scope.launch(Dispatchers.Main) { moving = done to total } } }.getOrDefault(-1)
                                }
                                moving = null
                                when (failed) {
                                    0 -> { Toast.makeText(context, movedText, Toast.LENGTH_LONG).show(); onClose() }
                                    -1 -> Toast.makeText(context, failedText, Toast.LENGTH_LONG).show()
                                    else -> Toast.makeText(context, context.getString(R.string.fb_moved_some, failed), Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(stringResource(R.string.fb_merge), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    viewing?.let { id ->
        val list = items.orEmpty()
        if (list.none { it.id == id }) viewing = null else SecureViewer(vm, other, list, list.indexOfFirst { it.id == id }, onClose = { viewing = null })
    }
    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text(stringResource(R.string.fb_forgot_title)) },
            text = { Text(stringResource(R.string.fb_forgot_text)) },
            confirmButton = { TextButton(onClick = { forgot = false; onClose() }) { Text(stringResource(R.string.fb_forgot_keep)) } },
            dismissButton = { TextButton(onClick = { forgot = false; discard = true }) { Text(stringResource(R.string.fb_discard)) } },
        )
    }
    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.fb_discard_title, items?.size ?: count)) },
            text = { Text(stringResource(R.string.fb_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    discard = false
                    scope.launch { withContext(Dispatchers.IO) { other.discard() }; onClose() }
                }) { Text(stringResource(R.string.fb_discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Tavola GalDalBackupApri: frase o codice di recupero della cartella del telefono di prima. */
@Composable
private fun FromBackupUnlock(onClose: () -> Unit, onForgot: () -> Unit, onUnlock: (String, () -> Unit) -> Unit) {
    var byCode by remember { mutableStateOf(false) }
    var secret by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenBar(stringResource(R.string.secure_title), onClose)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.fb_unlock_title), fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
            Text(stringResource(R.string.fb_unlock_text), fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            // Due linguette della stessa larghezza: cambiano solo l'etichetta e la tastiera, la cartella accetta tutte e due.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(false to R.string.fb_tab_phrase, true to R.string.fb_tab_code).forEach { (code, label) ->
                    val on = byCode == code
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { byCode = code; secret = ""; wrong = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(label), fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedTextField(
                secret, { secret = it; wrong = false }, singleLine = true, isError = wrong,
                label = { Text(stringResource(if (byCode) R.string.fb_code_label else R.string.fb_phrase_label)) },
                supportingText = { if (wrong) Text(stringResource(R.string.secure_wrong)) },
                visualTransformation = if (byCode) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = if (byCode) KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false)
                else KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(EuriseIcons.lock(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.fb_unlock_note), fontSize = 12.5.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onForgot, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.fb_forgot), fontWeight = FontWeight.Bold) }
            Button(
                enabled = secret.isNotBlank() && !busy,
                onClick = { busy = true; onUnlock(secret) { wrong = true; busy = false } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.fb_open), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

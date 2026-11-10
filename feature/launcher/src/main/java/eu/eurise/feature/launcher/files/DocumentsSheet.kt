package eu.eurise.feature.launcher.files

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Un documento trovato: sul telefono ([local]) oppure su un account cloud ([accountId] e [path]). */
private data class FoundDoc(
    val name: String,
    val where: String,
    val place: String,
    val local: RecentFile? = null,
    val accountId: String? = null,
    val path: String? = null,
)

/**
 * Documenti con il nome cercato, sul telefono e sugli account Murena e Nextcloud collegati, in un foglio solo.
 * I file del cloud si scaricano solo quando li si apre. Le cartelle aggiunte da altre app (kDrive, Proton) si
 * cercano da File.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsSheet(query: String, files: RecentFilesRepository, onOpenDrive: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val phone = stringResource(R.string.docs_phone)
    var results by remember { mutableStateOf<List<FoundDoc>?>(null) }
    var place by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf<FoundDoc?>(null) }
    val failed = stringResource(R.string.docs_open_failed)

    LaunchedEffect(query) {
        results = coroutineScope {
            val local = async { files.search(query, 20).map { FoundDoc(it.name, it.folder, phone, local = it) } }
            val accounts = CloudAccounts.get(context)
            val cloud = accounts.accounts.value.filter { it.kind == CloudKind.Murena || it.kind == CloudKind.Nextcloud }.map { account ->
                async {
                    val label = if (account.kind == CloudKind.Murena) "Murena" else "Nextcloud"
                    runCatching { accounts.dav(account.id)?.search(query, 20).orEmpty() }.getOrDefault(emptyList()).map { (path, entry) ->
                        FoundDoc(entry.name, path.trim('/').substringBeforeLast('/', "").replace("/", " › ").ifBlank { label }, label, accountId = account.id, path = path)
                    }
                }
            }
            (listOf(local) + cloud).awaitAll().flatten()
        }
    }

    fun open(doc: FoundDoc) {
        doc.local?.let { files.open(it); onDismiss(); return }
        val account = doc.accountId ?: return
        val path = doc.path ?: return
        opening = doc
        scope.launch {
            val ok = openFromCloud(context, account, path, doc.name)
            opening = null
            if (ok) onDismiss() else Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = scheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(EuriseIcons.folder(), null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text("«$query»", fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val count = results?.size
                    Text(
                        if (count == null) stringResource(R.string.docs_searching) else stringResource(R.string.docs_count, count),
                        fontSize = 12.5.sp, color = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp).clip(CircleShape).background(scheme.surfaceContainerLow)) {
                    Icon(EuriseIcons.close(), stringResource(R.string.close), tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            val found = results.orEmpty()
            val places = found.groupingBy { it.place }.eachCount()
            if (places.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    item { FilterPill(stringResource(R.string.docs_everywhere), null, place == null) { place = null } }
                    items(places.entries.toList(), key = { it.key }) { (p, n) -> FilterPill("$p $n", null, place == p) { place = if (place == p) null else p } }
                }
            }
            if (!files.hasAccess()) {
                FilterPill(stringResource(R.string.docs_allow_phone), null, false) {
                    files.accessIntent()?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                }
            }
            val shown = found.filter { place == null || it.place == place }
            if (results == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
            } else if (shown.isEmpty()) {
                Text(stringResource(R.string.docs_none), fontSize = 14.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp))
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surface),
                ) {
                    items(shown, key = { (it.accountId ?: "phone") + (it.path ?: it.local?.uri.toString()) }) { doc ->
                        Row(
                            Modifier.fillMaxWidth().clickable { open(doc) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerLow),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(doc.name.substringAfterLast('.', "").take(4).uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.primary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(doc.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${doc.place} › ${doc.where}", fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (opening == doc) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.docs_open_drive),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onDismiss(); onOpenDrive() }.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

/** Scarica il file nella cache di File (stessa cartella condivisa con le altre app) e lo apre con l'app adatta. */
private suspend fun openFromCloud(context: Context, accountId: String, path: String, name: String): Boolean = withContext(Dispatchers.IO) {
    val dav = CloudAccounts.get(context).dav(accountId) ?: return@withContext false
    val dir = File(context.cacheDir, "drive/search").apply { mkdirs() }
    val target = File(dir, name.replace('/', '_'))
    val ok = runCatching { dav.download(path, target) }.getOrDefault(false)
    if (!ok) return@withContext false
    val uri = runCatching { FileProvider.getUriForFile(context, context.packageName + ".drivefiles", target) }.getOrNull() ?: return@withContext false
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    withContext(Dispatchers.Main) { runCatching { context.startActivity(intent) }.isSuccess }
}

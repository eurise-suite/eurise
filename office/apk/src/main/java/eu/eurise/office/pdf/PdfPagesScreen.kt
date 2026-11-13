package eu.eurise.office.pdf

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.office.R
import eu.eurise.office.data.Suite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * «Pagine» (tavola OffPdfPagine): tutte le pagine in griglia, si scelgono toccandole; sotto, Ruota, Estrai, Dividi,
 * Duplica, Elimina; «Inserisci da» Scanner, un altro PDF, una foto o una pagina vuota.
 */
@Composable
fun PdfPagesScreen(model: PdfModel, onClose: () -> Unit) {
    val state by model.session.current.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chosen = remember { mutableStateListOf<Int>() }
    BackHandler(onBack = onClose)

    fun after(): Int = chosen.maxOrNull() ?: (state.pages.size - 1)

    fun insertFrom(uri: Uri) {
        scope.launch {
            val other = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { PDDocument.load(it, MemoryUsageSetting.setupMainMemoryOnly()) } }.getOrNull()
            }
            if (other == null) {
                Toast.makeText(context, R.string.pdf_cannot_open, Toast.LENGTH_LONG).show()
            } else {
                model.session.keepOpen(other)
                val at = after()
                model.session.edit { d -> PdfTools.insertPdf(d, other, at) }
                chosen.clear()
            }
        }
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) insertFrom(uri) }
    // «Scansiona e restituisci»: le pagine dello Scanner dell'app Eurise tornano qui e si inseriscono dopo la selezione.
    val scan = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.takeIf { result.resultCode == android.app.Activity.RESULT_OK }?.let { insertFrom(it) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeScaled(context, uri, 2400) }
            if (bitmap != null) {
                val at = after()
                model.session.edit { d -> PdfTools.insertImage(d, bitmap, at) }
                chosen.clear()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.statusBars).padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(EuriseIcons.chevronLeft(), contentDescription = stringResource(R.string.pdf_back)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pdf_pages_count, state.pages.size, chosen.size), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(state.name, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                TextButton(onClick = {
                    if (chosen.size == state.pages.size) chosen.clear() else { chosen.clear(); chosen.addAll(state.pages.indices) }
                }) { Text(stringResource(if (chosen.size == state.pages.size) R.string.pdf_pages_none else R.string.pdf_pages_all), fontWeight = FontWeight.Bold) }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 240.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.pages.size, key = { "${state.version}-$it" }) { i ->
                    Thumb(model, i, state.pages[i], state.version, i in chosen) { if (i in chosen) chosen.remove(i) else chosen.add(i) }
                }
                item(key = "inserisci") {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(4.dp))
                            .border(2.dp, Color(0xFFB6B6AE), RoundedCornerShape(4.dp)).clickable { pickPdf.launch(arrayOf("application/pdf")) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(EuriseIcons.plus(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.pdf_insert), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 12.dp)) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.pdf_insert_from).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.pdf_insert_scanner), Modifier.weight(1f)) {
                        val intent = eu.eurise.core.suite.ScannerContract.scanIntent(context, Suite.hub)
                        if (intent != null) scan.launch(intent) else Toast.makeText(context, R.string.office_needs_eurise, Toast.LENGTH_LONG).show()
                    }
                    Pill(stringResource(R.string.pdf_insert_pdf), Modifier.weight(1f)) { pickPdf.launch(arrayOf("application/pdf")) }
                    Pill(stringResource(R.string.pdf_insert_photo), Modifier.weight(1f)) { pickImage.launch("image/*") }
                    Pill(stringResource(R.string.pdf_insert_blank), Modifier.weight(0.8f)) {
                        val at = after()
                        model.session.edit { d -> PdfTools.insertBlank(d, at) }
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(64.dp)
                    .shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val none = chosen.isEmpty()
                PageAction(R.string.pdf_rotate, { EuriseIcons.rotate(it) }, !none, Modifier.weight(1f)) {
                    val pages = chosen.toList()
                    model.session.edit { d -> PdfTools.rotate(d, pages) }
                }
                PageAction(R.string.pdf_extract, { EuriseIcons.copy(it) }, !none, Modifier.weight(1f)) {
                    val pages = chosen.toList()
                    scope.launch {
                        val target = File(context.cacheDir, "copie/estratte.pdf").apply { parentFile?.mkdirs() }
                        model.session.read { d -> PdfTools.extract(d, pages, target) }
                        PdfOut.saveIn(context, target, state.name.removeSuffix(".pdf") + " (pagine).pdf")
                    }
                }
                PageAction(R.string.pdf_split, { EuriseIcons.scissors(it) }, chosen.size == 1 && chosen[0] > 0, Modifier.weight(1f)) {
                    val at = chosen[0]
                    scope.launch {
                        val first = File(context.cacheDir, "copie/parte-1.pdf").apply { parentFile?.mkdirs() }
                        val second = File(context.cacheDir, "copie/parte-2.pdf")
                        model.session.read { d ->
                            PdfTools.extract(d, (0 until at).toList(), first)
                            PdfTools.extract(d, (at until d.numberOfPages).toList(), second)
                        }
                        val base = state.name.removeSuffix(".pdf")
                        PdfOut.saveIn(context, second, "$base (2).pdf")
                        PdfOut.saveIn(context, first, "$base (1).pdf")
                    }
                }
                PageAction(R.string.pdf_duplicate, { EuriseIcons.copy(it) }, !none, Modifier.weight(1f)) {
                    val pages = chosen.toList()
                    model.session.edit { d -> PdfTools.duplicate(d, pages) }
                    chosen.clear()
                }
                PageAction(R.string.pdf_move_up, { EuriseIcons.chevronLeft(it) }, chosen.size == 1 && chosen[0] > 0, Modifier.weight(1f)) {
                    val from = chosen[0]
                    model.session.edit { d -> PdfTools.move(d, from, from - 1) }
                    chosen.clear(); chosen.add(from - 1)
                }
                PageAction(R.string.pdf_move_down, { EuriseIcons.chevronRight(it) }, chosen.size == 1 && chosen[0] < state.pages.size - 1, Modifier.weight(1f)) {
                    val from = chosen[0]
                    model.session.edit { d -> PdfTools.move(d, from, from + 1) }
                    chosen.clear(); chosen.add(from + 1)
                }
                PageAction(R.string.pdf_delete, { EuriseIcons.trash(it) }, !none && chosen.size < state.pages.size, Modifier.weight(1f), danger = true) {
                    val pages = chosen.toList()
                    model.session.edit { d -> PdfTools.delete(d, pages) }
                    chosen.clear()
                }
            }
        }
    }
}

@Composable
private fun Thumb(model: PdfModel, index: Int, geometry: PageGeometry, version: Int, chosen: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, version) { value = model.session.render(index, 300) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(geometry.displayAspect).shadow(if (chosen) 0.dp else 2.dp).background(Color.White)
                .then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier)
                .clickable(onClick = onClick)
                .semantics { contentDescription = context.getString(R.string.pdf_page_n, index + 1); selected = chosen },
        ) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            if (chosen) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.check(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp)) }
            }
        }
        Text(
            (index + 1).toString(), modifier = Modifier.padding(top = 6.dp), fontSize = 12.sp,
            fontWeight = if (chosen) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
private fun PageAction(
    label: Int,
    icon: (Float) -> androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val ink = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        danger -> Color(0xFF8C2F39)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier.height(54.dp).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon(EuriseIcons.Stroke), contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        Text(stringResource(label), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
    }
}

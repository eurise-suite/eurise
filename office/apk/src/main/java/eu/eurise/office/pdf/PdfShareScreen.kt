package eu.eurise.office.pdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import eu.eurise.core.suite.PasswordContract
import eu.eurise.office.BuildConfig
import eu.eurise.office.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Un dato da oscurare trovato nel PDF, con la pagina e i riquadri. */
private data class Found(val page: Int, val hit: SensitiveHit, val boxes: List<PdfRect>)

/** Le scelte di «Prima di condividere». */
private data class ShareOptions(
    val reduce: Boolean = true,
    val weight: Weight = Weight.Screen,
    val hidden: Boolean = true,
    val flatten: Boolean = true,
    val password: String? = null,
    val redact: Set<Int> = emptySet(),
)

/**
 * «Prima di condividere» (tavola OffPdfCondividi): si condivide sempre una copia, l'originale resta com'è. La copia si
 * prepara davvero a ogni cambio di scelta (peso vero, non stimato); oscurare toglie il testo dal file.
 */
@Composable
fun PdfShareScreen(model: PdfModel, onClose: () -> Unit) {
    val state by model.session.current.collectAsState()
    val context = LocalContext.current
    var options by remember { mutableStateOf(ShareOptions()) }
    var found by remember { mutableStateOf<List<Found>?>(null) }
    var choose by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(true) }
    val original = remember { model.session.workingFile.length() }
    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) {
        val list = mutableListOf<Found>()
        for (i in state.pages.indices) {
            val text = model.session.text(i) ?: continue
            Sensitive.find(text.text).forEach { hit -> list += Found(i, hit, text.boxesForRange(hit.start, hit.end)) }
        }
        found = list
    }
    LaunchedEffect(options) {
        busy = true
        delay(250)
        prepared = prepare(context, model, options, found.orEmpty())
        busy = false
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.pdf_cancel), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Text(stringResource(R.string.pdf_share_title), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.padding(horizontal = 36.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card {
                val after = prepared?.length()
                val detail = when {
                    !options.reduce -> stringResource(R.string.pdf_share_size, Formatter.formatShortFileSize(context, original))
                    busy || after == null -> stringResource(R.string.pdf_share_computing)
                    else -> stringResource(R.string.pdf_share_from_to, Formatter.formatShortFileSize(context, original), Formatter.formatShortFileSize(context, after))
                }
                Toggle(stringResource(R.string.pdf_share_reduce), detail, options.reduce) { options = options.copy(reduce = it) }
                if (options.reduce) {
                    Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Weight.Screen to R.string.pdf_weight_screen, Weight.Print to R.string.pdf_weight_print, Weight.Pec to R.string.pdf_weight_pec)
                            .forEach { (w, label) -> Choice(stringResource(label), options.weight == w) { options = options.copy(weight = w) } }
                    }
                }
            }
            Card { Toggle(stringResource(R.string.pdf_share_hidden), stringResource(R.string.pdf_share_hidden_detail), options.hidden) { options = options.copy(hidden = it) } }
            val list = found
            if (list != null && list.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFF2E6CC)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val fiscal = list.count { it.hit.kind == SensitiveHit.Kind.FiscalCode }
                    val iban = list.count { it.hit.kind == SensitiveHit.Kind.Iban }
                    Text(stringResource(R.string.pdf_redact_title), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A3A18))
                    Text(stringResource(R.string.pdf_redact_found, fiscal, iban), fontSize = 12.5.sp, color = Color(0xFF4A3A18))
                    if (choose) {
                        list.forEachIndexed { i, f ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                                options = options.copy(redact = if (i in options.redact) options.redact - i else options.redact + i)
                            }) {
                                Checkbox(checked = i in options.redact, onCheckedChange = null)
                                Text(stringResource(R.string.pdf_redact_item, f.hit.text, f.page + 1), fontSize = 13.sp, color = Color(0xFF4A3A18))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val all = options.redact.size == list.size
                        Box(
                            Modifier.height(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                .clickable { options = options.copy(redact = if (all) emptySet() else list.indices.toSet()) }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (all) stringResource(R.string.pdf_redact_none) else stringResource(R.string.pdf_redact_all, list.size),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        TextButton(onClick = { choose = !choose }) { Text(stringResource(R.string.pdf_redact_choose), color = Color(0xFF4A3A18), fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Card {
                Toggle(stringResource(R.string.pdf_share_flatten), null, options.flatten) { options = options.copy(flatten = it) }
                Toggle(stringResource(R.string.pdf_share_password), stringResource(R.string.pdf_share_password_detail), options.password != null) {
                    options = options.copy(password = if (it) PdfTools.randomPassword() else null)
                }
                options.password?.let { pwd ->
                    Text(stringResource(R.string.pdf_share_password_is, pwd), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
            Text(stringResource(R.string.pdf_share_original, state.where), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val name = shareName(state.name)
            fun ready(action: (File) -> Unit) {
                val file = prepared
                if (busy || file == null) Toast.makeText(context, R.string.pdf_share_computing, Toast.LENGTH_SHORT).show()
                else {
                    options.password?.let { keepPassword(context, name, it) }
                    action(file)
                }
            }
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .clickable { ready { PdfOut.share(context, PdfOut.copy(context, it, name), name) } },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.pdf_share_copy), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                    .clickable { ready { PdfOut.saveIn(context, it, name) } },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.pdf_more_save_in), fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun shareName(name: String): String = name.ifBlank { "documento.pdf" }

/** La password del file: negli appunti e, se c'è, proposta a Eurise Password. */
private fun keepPassword(context: Context, name: String, password: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(name, password))
    val intent = PasswordContract.addIntent(context, BuildConfig.DEBUG, name, password, notes = context.getString(R.string.pdf_share_password_note))
    if (intent != null) runCatching { context.startActivity(intent) }
    Toast.makeText(context, R.string.pdf_share_password_copied, Toast.LENGTH_LONG).show()
}

/** Prepara la copia con le scelte fatte, lavorando su un documento a parte (lo stato dell'editor non cambia). */
private suspend fun prepare(context: Context, model: PdfModel, o: ShareOptions, found: List<Found>): File? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.cacheDir, "copie/condividi").apply { mkdirs() }
        val source = File(dir, "stato.pdf")
        model.session.workingFile.copyTo(source, overwrite = true)
        val out = File(dir, "copia.pdf")
        PDDocument.load(source, MemoryUsageSetting.setupMixed(64L * 1024 * 1024)).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            val boxes = o.redact.mapNotNull { found.getOrNull(it) }.groupBy { it.page }.mapValues { (_, l) -> l.flatMap { it.boxes } }
            if (boxes.isNotEmpty()) PdfTools.redact(doc, source, boxes)
            if (o.flatten) PdfTools.flatten(doc)
            if (o.hidden) PdfTools.removeHidden(doc, notes = true)
            if (o.reduce) PdfTools.reduceWeight(doc, o.weight)
            o.password?.let { PdfTools.protect(doc, it) }
            out.outputStream().use { doc.save(it) }
        }
        out
    }.getOrNull()
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 4.dp)) {
        content()
    }
}

@Composable
private fun Toggle(title: String, detail: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (detail != null) Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun Choice(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp).clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold) }
}

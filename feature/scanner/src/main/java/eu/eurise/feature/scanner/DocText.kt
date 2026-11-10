package eu.eurise.feature.scanner

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.docs.DocFacts
import eu.eurise.core.data.docs.DocReading
import eu.eurise.core.data.docs.ReadClient
import eu.eurise.core.data.docs.ReadPage
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Stato della lettura delle pagine nella revisione dello Scanner. */
internal sealed interface DocRead {
    data object Off : DocRead
    data object Idle : DocRead
    data object Reading : DocRead
    class Done(val pages: List<ReadPage>, val facts: DocFacts) : DocRead {
        val text: String get() = pages.joinToString("\n\n") { it.text.trim() }.trim()
    }
    class Problem(val result: ReadClient.Result) : DocRead
}

/**
 * La scheda «Testo» della revisione (tavola DocLeggi): tipo e nome proposto, cartella, ciò che si è trovato (scadenza →
 * Da fare, importo, IBAN e codice avviso da copiare), il testo con gli importi e le date in evidenza, Copia e Leggi ad
 * alta voce. Spenta, propone di accendere la lettura (tavola DocMotore).
 */
@Composable
internal fun DocTextPart(state: DocRead, onRead: () -> Unit, onTurnOn: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            DocRead.Off -> Hint(stringResource(R.string.doc_off_title), stringResource(R.string.doc_off_text), stringResource(R.string.doc_turn_on), onTurnOn)
            DocRead.Idle -> Hint(stringResource(R.string.doc_idle_title), stringResource(R.string.doc_idle_text), stringResource(R.string.doc_read), onRead)
            DocRead.Reading -> Text(stringResource(R.string.doc_reading), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            is DocRead.Problem -> when (state.result) {
                ReadClient.Result.NotInstalled -> Hint(stringResource(R.string.doc_missing_title), stringResource(R.string.doc_missing_text), stringResource(R.string.doc_get_read)) { openStore(context) }
                ReadClient.Result.NoModel -> Hint(stringResource(R.string.doc_no_model_title), stringResource(R.string.doc_no_model_text), stringResource(R.string.doc_open_read)) {
                    ReadClient.setupIntent(context)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                else -> Hint(stringResource(R.string.doc_failed_title), stringResource(R.string.doc_failed_text), stringResource(R.string.doc_retry), onRead)
            }
            is DocRead.Done -> Done(state)
        }
    }
}

@Composable
private fun Done(state: DocRead.Done) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val facts = state.facts
    val text = state.text
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.close() } }

    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.doc_read_on_phone), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.tertiary)
        if (facts.title != null) {
            Text(facts.title!!, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.doc_folder, folderLabel(facts.folder!!)), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
        } else if (text.isEmpty()) {
            Text(stringResource(R.string.doc_no_text), fontSize = 14.sp, color = scheme.onSurfaceVariant)
        }
    }

    val deadline = facts.deadline
    val amount = facts.amount
    if (deadline != null || amount != null || facts.iban != null || facts.notice != null) {
        Section(stringResource(R.string.doc_found))
        Group {
            var first = true
            fun divider() = if (first) { first = false; false } else true
            if (deadline != null) {
                if (divider()) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                FactRow(EuriseIcons.calendar(), stringResource(R.string.doc_deadline, deadline.value.format(DAY)), "«${deadline.quote}»") {
                    Button(onClick = {
                        scope.launch {
                            val what = facts.title ?: context.getString(R.string.doc_task_default)
                            val notes = listOfNotNull(
                                amount?.let { context.getString(R.string.doc_task_amount, DocFacts.formatAmount(it.value)) },
                                facts.iban?.let { "IBAN $it" },
                                facts.notice?.let { context.getString(R.string.doc_task_notice, it) },
                            ).joinToString("\n")
                            EuriseData.create(context).tasks.add(context.getString(R.string.doc_task_title, what), due = deadline.value, notes = notes)
                            Toast.makeText(context, R.string.doc_task_added, Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(R.string.doc_todo), fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }
                }
            }
            if (amount != null) {
                if (divider()) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                val value = DocFacts.formatAmount(amount.value)
                FactRow(EuriseIcons.euro(), stringResource(R.string.doc_amount, value), "«${amount.quote}»") { CopyButton(value.removeSuffix(" €")) }
            }
            if (facts.iban != null || facts.notice != null) {
                if (divider()) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                val both = listOfNotNull(facts.iban, facts.notice).joinToString("\n")
                FactRow(EuriseIcons.bank(), stringResource(if (facts.iban != null && facts.notice != null) R.string.doc_iban_notice else if (facts.iban != null) R.string.doc_iban else R.string.doc_notice), listOfNotNull(facts.iban, facts.notice).joinToString(" · ")) { CopyButton(both) }
            }
        }
    }

    if (text.isNotEmpty()) {
        Section(stringResource(R.string.doc_text))
        SelectionContainer {
            Text(
                highlighted(text, listOfNotNull(deadline?.quote, amount?.quote)),
                fontSize = 13.5.sp, lineHeight = 21.sp, color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Secondary(stringResource(R.string.doc_copy_text)) { context.copy(text) } }
            Box(Modifier.weight(1f)) { Secondary(stringResource(if (speaker.speaking) R.string.doc_stop_reading else R.string.doc_read_aloud)) { speaker.toggle(text) } }
        }
        Text(stringResource(R.string.doc_searchable_note), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** Lettura ad alta voce con la sintesi vocale del telefono (nessun servizio esterno da parte di Eurise). */
private class Speaker(context: Context) {
    var speaking by mutableStateOf(false)
        private set
    private var ready = false
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts.language = Locale.ITALIAN
        }
    }

    fun toggle(text: String) {
        if (speaking) { tts.stop(); speaking = false; return }
        if (!ready) return
        // La sintesi accetta pezzi di lunghezza limitata: si va per paragrafi.
        text.split(Regex("\n{2,}")).filter { it.isNotBlank() }.forEachIndexed { i, part ->
            tts.speak(part.take(TextToSpeech.getMaxSpeechInputLength() - 1), if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "doc$i")
        }
        speaking = true
    }

    fun close() {
        tts.stop()
        tts.shutdown()
    }
}

/**
 * «Cosa cambia per la tua privacy» prima di accendere la lettura (tavola DocMotore). Le lingue si scelgono nell'app
 * Eurise Leggi, che le scarica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocReadingSheet(onClose: () -> Unit, onTurnedOn: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var auto by remember { mutableStateOf(DocReading.auto(context)) }
    val installed = remember { ReadClient.installed(context) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = scheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.doc_privacy_kicker).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = scheme.onSurfaceVariant)
            Text(stringResource(R.string.doc_privacy_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Group {
                FactRow(EuriseIcons.pdf(), stringResource(R.string.doc_privacy_what), stringResource(R.string.doc_privacy_what_text)) {}
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                FactRow(EuriseIcons.phone(), stringResource(R.string.doc_privacy_where), stringResource(R.string.doc_privacy_where_text)) {}
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                FactRow(EuriseIcons.download(), stringResource(R.string.doc_privacy_download), stringResource(R.string.doc_privacy_download_text)) {}
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                FactRow(EuriseIcons.undo(), stringResource(R.string.doc_privacy_back), stringResource(R.string.doc_privacy_back_text)) {}
            }
            Text(stringResource(R.string.doc_privacy_languages), fontSize = 12.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).clickable { auto = !auto }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.doc_auto), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.doc_auto_text), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                }
                Switch(checked = auto, onCheckedChange = { auto = it })
            }
            Primary(stringResource(if (installed) R.string.doc_turn_on else R.string.doc_get_and_turn_on)) {
                DocReading.set(context, enabled = true, auto = auto)
                if (!installed) openStore(context)
                onTurnedOn()
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.doc_not_now), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Hint(title: String, text: String, action: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(EuriseIcons.text(), null, tint = scheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(text, fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text(action, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Section(label: String) {
    Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun FactRow(icon: ImageVector, title: String, detail: String, action: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 2)
        }
        action()
    }
}

@Composable
private fun CopyButton(value: String) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.qr_copy_all)
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(scheme.primaryContainer).clickable(onClickLabel = label) { context.copy(value) }, contentAlignment = Alignment.Center) {
        Icon(EuriseIcons.copy(), contentDescription = label, tint = scheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
    }
}

/** Il testo con i pezzi trovati (importo, scadenza) in evidenza. */
@Composable
private fun highlighted(text: String, quotes: List<String>): AnnotatedString {
    val mark = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, color = MaterialTheme.colorScheme.onTertiaryContainer)
    val flat = text.replace(Regex("[ \\t]+"), " ")
    return buildAnnotatedString {
        append(flat)
        quotes.forEach { q ->
            // Si evidenziano i numeri (importo o data) dentro la citazione, dove compaiono nel testo.
            Regex("\\d[\\d.,/ ]*\\d").findAll(q).map { it.value }.forEach { piece ->
                var i = flat.indexOf(piece)
                while (i >= 0) {
                    addStyle(mark, i, i + piece.length)
                    i = flat.indexOf(piece, i + piece.length)
                }
            }
        }
    }
}

/** «Casa/Bollette» → «File › Casa › Bollette». */
internal fun folderLabel(folder: String) = (listOf("File") + folder.split('/')).joinToString(" › ")

private fun openStore(context: Context) {
    val pkg = ReadClient.readPackage(context)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.doc_install_by_hand, Toast.LENGTH_LONG).show()
    }
}

private val DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)

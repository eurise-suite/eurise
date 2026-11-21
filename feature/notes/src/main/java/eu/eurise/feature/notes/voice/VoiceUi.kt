package eu.eurise.feature.notes.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import eu.eurise.feature.notes.VoiceWork
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.suite.VoiceContract
import eu.eurise.feature.notes.R
import eu.eurise.feature.notes.doc.AudioBlock
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

/** Cartella del telefono con le note vocali. */
internal fun voiceDir(context: Context) = File(context.filesDir, "note-vocali")

/** Una frase della trascrizione che sembra una cosa da fare («chiamare l'idraulico giovedì»); nulla se non c'è. */
internal fun foundTask(transcript: String): String? = transcript
    .split(Regex("(?<=[.!?;])\\s+"))
    .map { it.trim().trimEnd('.', ';') }
    .firstOrNull { TASK_WORDS.containsMatchIn(it) }
    ?.let { s -> TASK_WORDS.find(s)?.let { s.substring(it.range.first) } ?: s }
    ?.replaceFirstChar { it.uppercase() }
    ?.take(90)

private val TASK_WORDS = Regex(
    "\\b(chiamare|telefonare|comprare|prenotare|ricordar(?:si|mi)|mandare|inviare|scrivere a|pagare|portare|fissare|prendere|rinnovare|disdire|chiedere)\\b",
    RegexOption.IGNORE_CASE,
)

/**
 * La registrazione (tavole NoteVocale e NoteVocaleRegistra): «Nota vocale · taccuino», durata, onde del volume, il
 * testo grezzo che compare mentre si parla (un paragrafo per pausa), dove andrà l'audio con «Cambia», Annulla, Pausa,
 * Fine. Chiede il permesso del microfono la prima volta. [onDone] riceve il blocco con la trascrizione grezza (un pezzo
 * per riga); [onCancel] butta via tutto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoiceRecordPanel(
    notebookName: String?,
    placeLabel: String,
    onChangePlace: () -> Unit,
    onDone: (AudioBlock) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val done = rememberUpdatedState(onDone)
    var allowed by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) allowed = true else onCancel() }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.RECORD_AUDIO) }

    val levels = remember { mutableStateListOf<Float>() }
    val text = remember { mutableStateListOf<String>() }
    var elapsed by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var noModel by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    val name = remember { UUID.randomUUID().toString() + ".m4a" }
    val file = remember { File(voiceDir(context), name) }
    val transcriber = remember {
        VoiceTranscriber(context, onText = { text.add(it) }, onNoModel = { noModel = true })
    }
    val recorder = remember {
        VoiceRecorder(
            file,
            onLevel = { l -> android.os.Handler(android.os.Looper.getMainLooper()).post { levels.add(l); if (levels.size > 40) levels.removeAt(0) } },
            onChunk = transcriber::add,
        )
    }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(allowed) {
        if (allowed && !started) {
            started = true
            if (!recorder.start()) failed = true
        }
    }
    LaunchedEffect(started) {
        while (started) {
            elapsed = recorder.durationMs
            delay(250)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            recorder.stop()
            transcriber.close()
        }
    }
    LaunchedEffect(finishing) {
        if (!finishing) return@LaunchedEffect
        recorder.stop()
        // Aspetta (al massimo 20 secondi) che Voce finisca gli ultimi pezzi.
        var waited = 0
        while (transcriber.pending() && waited < 80) { delay(250); waited++ }
        done.value(AudioBlock(0, name, recorder.durationMs, text.joinToString("\n")))
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surface.copy(alpha = 0.96f))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(26.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (paused) scheme.onSurfaceVariant else scheme.error))
            Column(Modifier.weight(1f)) {
                Text(
                    notebookName?.let { stringResource(R.string.notes_voice_title_in, it) } ?: stringResource(R.string.notes_insert_voice),
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    stringResource(if (paused) R.string.notes_voice_paused else R.string.notes_voice_recording, clock(elapsed)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(stringResource(R.string.notes_voice_on_phone), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            PrivacyMeter(10)
        }
        Wave(levels, scheme.primary)
        when {
            failed -> Text(stringResource(R.string.notes_voice_failed), fontSize = 14.sp, color = scheme.error)
            text.isNotEmpty() -> Column(
                Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState(), reverseScrolling = true),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.notes_voice_while_talking), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                text.forEach { Text(it, fontSize = 14.sp, lineHeight = 21.sp) }
            }
            finishing -> Text(stringResource(R.string.notes_voice_finishing), fontSize = 14.sp, color = scheme.onSurfaceVariant)
        }
        val hint = when {
            !transcriber.available -> R.string.notes_voice_no_voice
            noModel -> R.string.notes_voice_no_model
            text.isNotEmpty() -> R.string.notes_voice_raw_hint
            else -> R.string.notes_voice_hint
        }
        Text(stringResource(hint), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        if (!transcriber.available || noModel) {
            Text(
                stringResource(R.string.notes_voice_get_voice),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
                modifier = Modifier.clickable {
                    val pkg = VoiceContract.PACKAGES.firstOrNull { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }
                    runCatching {
                        if (pkg != null) context.startActivity(Intent(VoiceContract.ACTION_MODELS).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
            )
        }
        // Dove va l'audio, sempre in vista (tavola NoteVocaleRegistra).
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surfaceContainer)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.folder(), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.notes_voice_goes_to, placeLabel),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            TextButton(onClick = onChangePlace, enabled = !finishing) {
                Text(stringResource(R.string.notes_voice_change), fontWeight = FontWeight.SemiBold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = { recorder.stop(); file.delete(); onCancel() }, modifier = Modifier.weight(1f).height(50.dp)) {
                Text(stringResource(R.string.notes_voice_cancel), fontWeight = FontWeight.SemiBold)
            }
            FilledTonalButton(
                onClick = { paused = !paused; recorder.paused = paused },
                enabled = started && !failed && !finishing,
                modifier = Modifier.weight(1f).height(50.dp),
            ) { Text(stringResource(if (paused) R.string.notes_voice_resume else R.string.notes_voice_pause), fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = { finishing = true },
                enabled = started && !failed && !finishing,
                modifier = Modifier.weight(1f).height(50.dp),
            ) { Text(stringResource(R.string.notes_voice_done), fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * «Dove salvo le registrazioni?» (tavola NoteVocaleDove): sul telefono nella cartella pubblica, solo nelle Note, nel cloud
 * collegato; ognuno con le sue tacchette. [current] è il posto segnato all'apertura; «Va bene» passa la scelta a
 * [onChoose], che la tiene anche per le prossime note vocali.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoicePlaceSheet(current: VoicePlace, onChoose: (VoicePlace) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var chosen by rememberSaveable { mutableStateOf(current) }
    val cloud = remember { VoicePlaces.cloudName(context) }
    val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        onChoose(if (ok) VoicePlace.Phone else VoicePlace.App)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = scheme.background) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.notes_voice_where_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.notes_voice_where_text), fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurfaceVariant)
            listOf(VoicePlace.Phone, VoicePlace.App, VoicePlace.Cloud).forEach { place ->
                val enabled = place != VoicePlace.Cloud || cloud != null
                val (score, where) = VoicePlaces.privacy(context, place)
                val title = when (place) {
                    VoicePlace.Phone -> stringResource(R.string.notes_voice_where_phone)
                    VoicePlace.App -> stringResource(R.string.notes_voice_where_app)
                    VoicePlace.Cloud -> stringResource(R.string.notes_voice_where_cloud)
                }
                val text = when (place) {
                    VoicePlace.Phone -> stringResource(R.string.notes_voice_where_phone_text, VoicePlaces.label(context, VoicePlace.Phone))
                    VoicePlace.App -> stringResource(R.string.notes_voice_where_app_text)
                    VoicePlace.Cloud -> if (cloud != null) stringResource(R.string.notes_voice_where_cloud_text, cloud)
                    else stringResource(R.string.notes_voice_where_cloud_none)
                }
                val selected = chosen == place
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainerLow)
                        .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, RoundedCornerShape(18.dp))
                        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { chosen = place }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            when (place) { VoicePlace.Phone -> EuriseIcons.phone(); VoicePlace.App -> EuriseIcons.lock(); VoicePlace.Cloud -> EuriseIcons.cloud() },
                            contentDescription = null,
                            tint = if (enabled) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant)
                        PrivacyMeter(score, where)
                    }
                    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.notes_voice_where_remember), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp)) {
                    Text(stringResource(R.string.notes_voice_cancel), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        val storage = Manifest.permission.WRITE_EXTERNAL_STORAGE
                        if (chosen == VoicePlace.Phone && VoicePlaces.needsStoragePermission() &&
                            context.checkSelfPermission(storage) != PackageManager.PERMISSION_GRANTED
                        ) askStorage.launch(storage) else onChoose(chosen)
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text(stringResource(R.string.notes_voice_ok), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Wave(levels: List<Float>, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val shown = List(40 - levels.size) { 0f } + levels
        shown.forEach { l ->
            Box(Modifier.width(3.dp).height((4 + l * 26).dp).clip(RoundedCornerShape(2.dp)).background(if (l > 0f) color else color.copy(alpha = 0.25f)))
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Azioni del blocco vocale nella nota. */
internal class AudioActions(
    val onToText: () -> Unit,
    val onTask: (String) -> Unit,
    val onReorder: () -> Unit,
    val onSummary: () -> Unit,
    val onClearSummary: () -> Unit,
)

/**
 * Una nota vocale nella nota (tavola NoteVocaleOrdinata): ascolto con avanzamento e durata, dove sta l'audio e quanto
 * pesa, «riordinata da Alfred sul telefono», il riassunto se chiesto, e le azioni: «Testo com'era» (la trascrizione
 * grezza), «Riordina di nuovo», «Riassunto» (solo con il cervello di Alfred), la cosa da fare trovata. Il testo
 * riordinato sta nella nota, sotto il blocco, e si modifica come il resto. Sposta, duplica ed elimina stanno nel menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AudioBlockView(block: AudioBlock, work: VoiceWork?, brainReady: Boolean, actions: AudioActions) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var unreachable by remember(block.file) { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var showRaw by rememberSaveable(block.id) { mutableStateOf(false) }
    // Il file privato c'è o no: vale per le note vocali di prima, che stavano solo lì.
    val local = remember(block.file, block.place) { File(voiceDir(context), block.file).exists() }
    val missing = unreachable || !local && VoicePlace.of(block.place) == VoicePlace.App
    DisposableEffect(block.file) { onDispose { player?.release(); player = null } }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { p -> position = p.currentPosition; progress = if (p.duration > 0) p.currentPosition / p.duration.toFloat() else 0f }
            delay(200)
        }
    }
    val playLabel = stringResource(if (playing) R.string.notes_voice_stop else R.string.notes_voice_play)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(start = 14.dp, end = 44.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scheme.secondaryContainer)
                    .clickable(role = Role.Button, enabled = !missing && !loading) {
                        player?.let { p ->
                            if (playing) { p.pause(); playing = false } else { p.start(); playing = true }
                            return@clickable
                        }
                        loading = true
                        scope.launch {
                            val source = VoicePlaces.source(context, block)
                            val p = source?.let { uri ->
                                runCatching {
                                    MediaPlayer().apply {
                                        setDataSource(context, uri)
                                        prepare()
                                        setOnCompletionListener { playing = false; progress = 0f; position = 0 }
                                    }
                                }.getOrNull()
                            }
                            loading = false
                            if (p == null) { unreachable = true; return@launch }
                            player = p
                            p.start()
                            playing = true
                        }
                    }
                    .semantics { contentDescription = playLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing) EuriseIcons.pause() else EuriseIcons.play(), contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(scheme.outlineVariant)) {
                Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(CircleShape).background(scheme.primary))
            }
            Text(
                (if (playing || position > 0) clock(position.toLong()) + " / " else "") + clock(block.durationMs),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
            )
        }
        // Dove sta l'audio e quanto pesa.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                when (VoicePlace.of(block.place)) { VoicePlace.Phone -> EuriseIcons.folder(); VoicePlace.App -> EuriseIcons.lock(); VoicePlace.Cloud -> EuriseIcons.cloud() },
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            val size = if (block.size > 0) " · " + android.text.format.Formatter.formatShortFileSize(context, block.size) else ""
            Text(VoicePlaces.label(context, block) + size, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        if (missing) Text(stringResource(if (VoicePlace.of(block.place) == VoicePlace.Cloud) R.string.notes_voice_missing_cloud else R.string.notes_voice_missing), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        if (block.transcript.isNotBlank()) {
            Text(
                when {
                    work == VoiceWork.Ordering -> stringResource(R.string.notes_voice_ordering)
                    work == VoiceWork.Summarizing -> stringResource(R.string.notes_voice_summarizing)
                    block.ordered.contains(OrderedText.HEADING.trim()) || brainReady && block.ordered.isNotBlank() -> stringResource(R.string.notes_voice_ordered_by_alfred, clock(block.durationMs))
                    block.ordered.isNotBlank() -> stringResource(R.string.notes_voice_ordered_at_pauses, clock(block.durationMs))
                    else -> stringResource(R.string.notes_voice_by_voice)
                },
                fontSize = 12.sp,
                color = if (work != null) scheme.primary else scheme.onSurfaceVariant,
                fontWeight = if (work != null) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (block.summary.isNotBlank()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.notes_voice_summary_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = actions.onClearSummary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.notes_voice_summary_remove), tint = scheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
                        }
                    }
                    block.summary.lines().filter { it.isNotBlank() }.forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", fontSize = 14.sp, color = scheme.onPrimaryContainer)
                            Text(line, fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onPrimaryContainer)
                        }
                    }
                }
            }
            // Senza testo riordinato (note vocali di prima) la trascrizione si vede qui, come prima.
            if (showRaw || block.ordered.isBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (block.ordered.isNotBlank()) Text(stringResource(R.string.notes_voice_raw_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                    VoiceOrder.pieces(block.transcript).forEach { Text(it, fontSize = 14.sp, lineHeight = 21.sp, color = if (block.ordered.isNotBlank()) scheme.onSurfaceVariant else scheme.onSurface) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                foundTask(VoiceOrder.pieces(block.transcript).joinToString(" "))?.let { task ->
                    Chip(stringResource(R.string.notes_voice_task, task), primary = true) { actions.onTask(task) }
                }
                if (block.ordered.isNotBlank()) {
                    Chip(stringResource(if (showRaw) R.string.notes_voice_raw_hide else R.string.notes_voice_raw_show)) { showRaw = !showRaw }
                }
                Chip(stringResource(if (block.ordered.isBlank()) R.string.notes_voice_order else R.string.notes_voice_reorder), enabled = work == null) { actions.onReorder() }
                if (brainReady && block.summary.isBlank()) {
                    Chip(stringResource(R.string.notes_voice_summary), enabled = work == null) { actions.onSummary() }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(if (primary) scheme.primaryContainer else scheme.surfaceContainer)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (primary) scheme.onPrimaryContainer else scheme.onSurface, maxLines = 1)
    }
}

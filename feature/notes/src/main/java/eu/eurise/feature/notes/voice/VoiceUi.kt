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
 * Il microfono nella nota (tavola NoteVocale): durata, onde del volume, testo che compare mentre si parla, Annulla, Pausa,
 * Fine. Chiede il permesso del microfono la prima volta. [onDone] riceve il blocco pronto; [onCancel] butta via tutto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoiceRecordPanel(onDone: (AudioBlock) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
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
        done.value(AudioBlock(0, name, recorder.durationMs, text.joinToString(" ")))
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surface.copy(alpha = 0.94f))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(26.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (paused) scheme.onSurfaceVariant else scheme.error))
            Text(
                stringResource(if (paused) R.string.notes_voice_paused else R.string.notes_voice_recording, clock(elapsed)),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.notes_voice_on_phone), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            PrivacyMeter(10)
        }
        Wave(levels, scheme.primary)
        when {
            failed -> Text(stringResource(R.string.notes_voice_failed), fontSize = 14.sp, color = scheme.error)
            text.isNotEmpty() -> Text(text.joinToString(" "), fontSize = 14.sp, lineHeight = 21.sp)
            finishing -> Text(stringResource(R.string.notes_voice_finishing), fontSize = 14.sp, color = scheme.onSurfaceVariant)
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
        val hint = when {
            !transcriber.available -> R.string.notes_voice_no_voice
            noModel -> R.string.notes_voice_no_model
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
internal class AudioActions(val onToText: () -> Unit, val onTask: (String) -> Unit)

/**
 * Una nota vocale nella nota: ascolto con avanzamento, durata, trascrizione e le azioni («Metti il testo nella nota», la
 * cosa da fare trovata). Il resto (sposta, duplica, elimina) sta nel menu del blocco.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AudioBlockView(block: AudioBlock, actions: AudioActions) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val file = remember(block.file) { File(voiceDir(context), block.file) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
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
                    .clickable(role = Role.Button, enabled = file.exists()) {
                        val p = player ?: runCatching {
                            MediaPlayer().apply {
                                setDataSource(file.path)
                                prepare()
                                setOnCompletionListener { playing = false; progress = 0f; position = 0 }
                            }
                        }.getOrNull().also { player = it }
                        if (p == null) return@clickable
                        if (playing) { p.pause(); playing = false } else { p.start(); playing = true }
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
        if (!file.exists()) Text(stringResource(R.string.notes_voice_missing), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        if (block.transcript.isNotBlank()) {
            Text(block.transcript, fontSize = 14.sp, lineHeight = 21.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                foundTask(block.transcript)?.let { task ->
                    Chip(stringResource(R.string.notes_voice_task, task), primary = true) { actions.onTask(task) }
                }
                Chip(stringResource(R.string.notes_voice_to_text)) { actions.onToText() }
            }
            Text(stringResource(R.string.notes_voice_by_voice), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Chip(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(if (primary) scheme.primaryContainer else scheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (primary) scheme.onPrimaryContainer else scheme.onSurface, maxLines = 1)
    }
}

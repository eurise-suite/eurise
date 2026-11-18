@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package eu.eurise.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Il rosso del vocale che si registra. */
private val REC = Color(0xFFC0392B)

/**
 * Il fumetto di un vocale (tavola ChatVocali): tasto di ascolto, forma d'onda (tocco = salta lì), durata; sotto la
 * trascrizione fatta sul telefono, con la riga «Trascritto sul telefono · l'audio non esce».
 */
@Composable
internal fun AudioBubble(model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean, onLong: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val hub = model.voice
    val info = remember(e.data) { Voice.info(e.data?.let { runCatching { JSONObject(it) }.getOrNull() }) }
    val bg = if (mine) scheme.primary else scheme.surface
    val fg = if (mine) scheme.onPrimary else scheme.onSurface
    val failed = stringResource(R.string.ch_file_failed)
    val fetch: (Event, (java.io.File?) -> Unit) -> Unit = { ev, done -> model.media(ev, done) }
    val local = e.id.startsWith("local:")
    // La trascrizione parte da sola appena il vocale si vede (solo sul telefono, uno alla volta).
    LaunchedEffect(hub.key(e)) { if (!local) hub.want(e, fetch) }
    val transcript = hub.transcripts[hub.key(e)]
    val playing = hub.playing == e.id
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 310.dp).fillMaxWidth(0.82f).clip(RoundedCornerShape(18.dp)).background(bg)
                .combinedClickable(onClick = {}, onLongClick = onLong).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!mine && model.room(roomId)?.direct != true) {
                Text(model.nameOf(e.sender), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tintFor(e.sender))
            }
            ReplyQuote(model, roomId, e, fg)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val label = stringResource(if (playing && !hub.paused) R.string.vo_pause else R.string.vo_play)
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(if (mine) scheme.onPrimary else scheme.primary)
                        .clickable(enabled = !local, role = Role.Button) { hub.toggle(e, fetch) { actions.toast(failed) } }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    val tint = if (mine) scheme.primary else scheme.onPrimary
                    when {
                        hub.loading == e.id -> Text("…", color = tint, fontWeight = FontWeight.Bold)
                        playing && !hub.paused -> Icon(EuriseIcons.pause(2.4f), null, tint = tint, modifier = Modifier.size(16.dp))
                        else -> Icon(EuriseIcons.play(2.4f), null, tint = tint, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Waveform(info.wave, if (playing) hub.progress else 0f, fg, Modifier.weight(1f)) { f -> hub.seek(e, f) }
                Spacer(Modifier.width(8.dp))
                Text(Voice.duration(if (playing) (info.durationMs * hub.progress).toLong() else info.durationMs),
                    fontSize = 12.sp, color = fg.copy(alpha = 0.72f))
            }
            when (transcript) {
                is VoiceHub.Transcript.Text -> Text("«" + transcript.text + "»", fontSize = 13.5.sp, lineHeight = 19.sp, color = fg,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fg.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = 8.dp))
                VoiceHub.Transcript.Working -> Text(stringResource(R.string.vo_transcribing), fontSize = 12.5.sp, color = fg.copy(alpha = 0.72f))
                VoiceHub.Transcript.NoModel -> Text(stringResource(R.string.vo_no_model), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = fg,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) {
                        hub.modelsIntent()?.let(actions::launch)
                        hub.retry(e, fetch)
                    }.padding(vertical = 2.dp))
                VoiceHub.Transcript.NoVoice -> Text(stringResource(R.string.vo_no_voice), fontSize = 12.sp, color = fg.copy(alpha = 0.72f))
                else -> Unit
            }
            val time = if (local) "…" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.time))
            Text(if (transcript is VoiceHub.Transcript.Text) stringResource(R.string.vo_on_phone, time) else time,
                fontSize = 10.5.sp, color = fg.copy(alpha = 0.65f), modifier = Modifier.align(Alignment.End))
        }
    }
}

/** Le sbarrette del vocale: quelle già ascoltate piene, le altre chiare. Un tocco salta in quel punto. */
@Composable
private fun Waveform(wave: List<Int>, progress: Float, color: Color, modifier: Modifier, onSeek: (Float) -> Unit) {
    val bars = remember(wave) { Voice.bars(wave, 28) }
    Row(
        modifier.height(26.dp).pointerInput(Unit) { detectTapGestures { onSeek(it.x / size.width) } },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bars.forEachIndexed { i, v ->
            val done = (i + 0.5f) / bars.size <= progress
            Box(Modifier.weight(1f).height((22 * v).dp.coerceAtLeast(4.dp)).clip(RoundedCornerShape(2.dp))
                .background(if (done) color else color.copy(alpha = 0.45f)))
        }
    }
}

/**
 * Il microfono accanto al campo (tavola ChatVocali), quando non c'è testo: si tiene premuto e si parla, si lascia per
 * mandare; scorrendo verso sinistra si annulla. [onState] dice a chi lo contiene se si sta registrando (per mostrare
 * la striscia della registrazione al posto del campo).
 */
@Composable
internal fun MicButton(model: ChatModel, actions: ChatActions, roomId: String, onState: (VoiceRecorder?, Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val cancelPx = with(LocalDensity.current) { 90.dp.toPx() }
    val hold = stringResource(R.string.vo_hold)
    val tooShort = stringResource(R.string.vo_too_short)
    val noMic = stringResource(R.string.vo_no_mic)
    var active by remember { mutableStateOf(false) }
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(if (active) REC else scheme.primary)
            .semantics { contentDescription = hold }
            .pointerInput(roomId) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        actions.askMic { ok -> if (ok) actions.toast(hold) }
                        return@awaitEachGesture
                    }
                    model.voice.stopPlaying(); model.voice.stopSpeaking()
                    val rec = VoiceRecorder()
                    if (!rec.start()) { actions.toast(noMic); return@awaitEachGesture }
                    active = true
                    onState(rec, false)
                    var cancelled = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        val dx = ch.position.x - down.position.x
                        if (dx < -cancelPx != cancelled) { cancelled = dx < -cancelPx; onState(rec, cancelled) }
                        ch.consume()
                        if (!ch.pressed || rec.elapsedMs >= Voice.MAX_MS) break
                    }
                    val pcm = rec.stop()
                    active = false
                    onState(null, false)
                    when {
                        cancelled -> Unit
                        pcm.size * 1000L / Voice.RATE < Voice.MIN_MS -> actions.toast(tooShort)
                        else -> model.sendVoice(roomId, pcm) { actions.toast(actions.text(it)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Icon(EuriseIcons.mic(2.2f), null, tint = scheme.onPrimary, modifier = Modifier.size(19.dp)) }
}

/** Al posto del campo mentre si registra: pallino rosso, tempo, livello della voce e «scorri per annullare». */
@Composable
internal fun RecordingStrip(rec: VoiceRecorder, cancelling: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    var elapsed by remember(rec) { mutableLongStateOf(0L) }
    var level by remember(rec) { mutableFloatStateOf(0f) }
    LaunchedEffect(rec) { while (true) { elapsed = rec.elapsedMs; level = rec.level; delay(100) } }
    Row(
        modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp)).background(scheme.surface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if ((elapsed / 500) % 2 == 0L) REC else REC.copy(alpha = 0.35f)))
        Spacer(Modifier.width(8.dp))
        Text(Voice.duration(elapsed), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(36.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(scheme.onSurface.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(level.coerceIn(0.05f, 1f)).height(6.dp).background(REC))
        }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(if (cancelling) R.string.vo_release_cancel else R.string.vo_slide_cancel), fontSize = 13.sp,
            color = if (cancelling) REC else scheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
    }
}

/** «Leggi ad alta voce» sotto un messaggio ricevuto (con la lettura per chi vede poco accesa). */
@Composable
internal fun ReadAloudChip(model: ChatModel, e: Event, text: String) {
    val scheme = MaterialTheme.colorScheme
    val speaking = model.voice.speakingId == e.id
    Row(
        Modifier.padding(start = 4.dp, bottom = 4.dp).height(34.dp).clip(RoundedCornerShape(17.dp)).background(scheme.surface)
            .clickable(role = Role.Button) {
                if (speaking) model.voice.stopSpeaking()
                else model.voice.speak(e.id, Voice.spoken(model.nameOf(e.sender), text))
            }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (speaking) EuriseIcons.volumeOff(2.2f) else EuriseIcons.volume(2.2f), null, tint = scheme.onSurface, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(if (speaking) R.string.vo_stop_reading else R.string.vo_read_aloud), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** Il testo da leggere ad alta voce per un messaggio: il testo, o la trascrizione di un vocale. */
internal fun readableText(model: ChatModel, e: Event): String? = when (e.kind) {
    Event.Kind.Text, Event.Kind.Notice -> shownText(model, e)
    Event.Kind.Audio -> (model.voice.transcripts[model.voice.key(e)] as? VoiceHub.Transcript.Text)?.text
    else -> null
}

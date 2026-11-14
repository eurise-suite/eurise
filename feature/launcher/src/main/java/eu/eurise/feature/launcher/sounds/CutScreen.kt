package eu.eurise.feature.launcher.sounds

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Prendi il pezzo (tavola SuoTaglia): una canzone dell'utente, il tratto scelto, le dissolvenze, a chi va. */
@Composable
internal fun CutScreen(uri: Uri, onClose: () -> Unit, onPerson: (String) -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<AudioCut.Info?>(null) }
    var reading by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(uri) {
        val i = AudioCut.analyze(context, uri) { reading = it }
        if (i == null) failed = true else info = i
    }
    var seconds by remember { mutableIntStateOf(16) }
    var free by remember { mutableStateOf(false) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(16_000L) }
    var fadeIn by remember { mutableStateOf(true) }
    var seamless by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var head by remember { mutableLongStateOf(-1L) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    val stopPlay: () -> Unit = {
        player[0]?.let { runCatching { it.stop() }; it.release() }
        player[0] = null
        playing = false
        head = -1L
    }
    DisposableEffect(Unit) { onDispose { stopPlay() } }

    val duration = info?.durationMs ?: 0L
    fun place(start: Long, len: Long = endMs - startMs) {
        val l = len.coerceIn(3_000L, minOf(60_000L, maxOf(3_000L, duration)))
        val s = start.coerceIn(0L, maxOf(0L, duration - l))
        startMs = s
        endMs = s + l
    }
    LaunchedEffect(info) { info?.let { place(0, seconds * 1000L) } }
    // Cursore che scorre mentre si ascolta.
    LaunchedEffect(playing) {
        while (playing) {
            val p = player[0]
            if (p != null) runCatching { head = p.currentPosition.toLong(); if (head >= endMs) stopPlay() }
            delay(50)
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SndHeader(stringResource(R.string.cut_title), info?.let { stringResource(R.string.cut_on_phone, "${it.name} · ${clock(it.durationMs)}") }, onClose)
        val i = info
        if (i == null) {
            if (failed) Text(stringResource(R.string.cut_unreadable), fontSize = 15.sp)
            else {
                Text(stringResource(R.string.cut_reading, (reading * 100).toInt()), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { reading }, modifier = Modifier.fillMaxWidth())
            }
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Waveform(i, startMs, endMs, head, free, onMove = { s, e -> place(s, e - s) })
                Row {
                    Text(stringResource(R.string.cut_from, clock(startMs)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.cut_length, ((endMs - startMs) / 1000).toInt()), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(stringResource(R.string.cut_to, clock(endMs)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary)
                            .clickable(role = Role.Button) {
                                stopPlay()
                                val len = (endMs - startMs).toInt() / 1000
                                val f = Chorus.find(i.loud, i.rough, AudioCut.FPS, maxOf(3, len))
                                place(f * 1000L / AudioCut.FPS, endMs - startMs)
                            },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(EuriseIcons.spark(), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.cut_chorus), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(start = 8.dp))
                    }
                    Box(
                        Modifier.width(52.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                            .clickable(role = Role.Button) {
                                if (playing) stopPlay() else runCatching {
                                    val mp = MediaPlayer()
                                    mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                                    mp.setDataSource(context, uri)
                                    mp.setOnPreparedListener { it.seekTo(startMs.toInt()); it.start(); playing = true }
                                    mp.setOnCompletionListener { stopPlay() }
                                    mp.prepareAsync()
                                    player[0] = mp
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (playing) EuriseIcons.pause() else EuriseIcons.play(), stringResource(R.string.cut_play), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            SndSection(stringResource(R.string.cut_duration)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 16, 30, 0).forEach { s ->
                        val on = if (s == 0) free else !free && seconds == s
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                                .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                                .selectable(on, role = Role.RadioButton) {
                                    if (s == 0) free = true else { free = false; seconds = s; place(startMs, s * 1000L) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (s == 0) stringResource(R.string.cut_free) else stringResource(R.string.snd_seconds, s), fontSize = 14.sp,
                                fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                SndSwitchRow(stringResource(R.string.cut_fade_in), stringResource(R.string.cut_fade_in_hint), fadeIn) { fadeIn = it }
                SndDivider()
                SndSwitchRow(stringResource(R.string.cut_seamless), stringResource(R.string.cut_seamless_hint), seamless) { seamless = it }
            }
            Text(stringResource(R.string.cut_note), fontSize = 12.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        Text(stringResource(if (saving) R.string.cut_saving else R.string.cut_use_for), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        val save: (Boolean) -> Unit = { forPerson ->
            if (!saving) {
                saving = true
                stopPlay()
                scope.launch {
                    val f = AudioCut.export(context, uri, startMs, endMs, fadeIn, seamless, i.name)
                    saving = false
                    if (f == null) { Toast.makeText(context, R.string.cut_failed, Toast.LENGTH_LONG).show(); return@launch }
                    val id = "m:${f.nameWithoutExtension}"
                    if (forPerson) onPerson(id)
                    else {
                        chooseGeneral(context, SoundUse.Calls, id)
                        onClose()
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable(enabled = !saving) { save(false) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.cut_all_calls), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !saving) { save(true) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.cut_person), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Forma d'onda con il tratto scelto: si sposta trascinando; con la durata libera si tirano anche i bordi. */
@Composable
private fun Waveform(info: AudioCut.Info, startMs: Long, endMs: Long, head: Long, free: Boolean, onMove: (Long, Long) -> Unit) {
    val bars = remember(info) { AudioCut.bars(info.loud, 56) }
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val band = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val total = info.durationMs.coerceAtLeast(1L)
    var grab by remember { mutableIntStateOf(0) } // 0 sposta, -1 bordo sinistro, 1 bordo destro
    val curStart by androidx.compose.runtime.rememberUpdatedState(startMs)
    val curEnd by androidx.compose.runtime.rememberUpdatedState(endMs)
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).pointerInput(free, total) {
            detectHorizontalDragGestures(
                onDragStart = { o ->
                    val sx = curStart.toFloat() / total * size.width
                    val ex = curEnd.toFloat() / total * size.width
                    val touch = 28.dp.toPx()
                    grab = when {
                        free && kotlin.math.abs(o.x - sx) < touch -> -1
                        free && kotlin.math.abs(o.x - ex) < touch -> 1
                        else -> 0
                    }
                },
            ) { change, dx ->
                change.consume()
                val dMs = (dx / size.width * total).toLong()
                when (grab) {
                    -1 -> onMove((curStart + dMs).coerceIn(0L, curEnd - 3_000L), curEnd)
                    1 -> onMove(curStart, (curEnd + dMs).coerceIn(curStart + 3_000L, minOf(total, curStart + 60_000L)))
                    else -> onMove(curStart + dMs, curEnd + dMs)
                }
            }
        },
    ) {
        val sx = startMs.toFloat() / total * size.width
        val ex = endMs.toFloat() / total * size.width
        drawRoundRect(band, Offset(sx, 0f), Size(ex - sx, size.height), CornerRadius(10.dp.toPx()))
        val slot = size.width / bars.size
        val barW = slot * 0.6f
        bars.forEachIndexed { k, v ->
            val x = k * slot + (slot - barW) / 2
            val h = v * size.height * 0.85f
            val inside = x + barW / 2 in sx..ex
            drawRoundRect(if (inside) on else off, Offset(x, (size.height - h) / 2), Size(barW, h), CornerRadius(barW / 2))
        }
        drawRoundRect(on, Offset(sx - 3.dp.toPx(), 0f), Size(6.dp.toPx(), size.height), CornerRadius(3.dp.toPx()))
        drawRoundRect(on, Offset(ex - 3.dp.toPx(), 0f), Size(6.dp.toPx(), size.height), CornerRadius(3.dp.toPx()))
        if (head >= 0) {
            val hx = head.toFloat() / total * size.width
            drawRect(Color(0xFFE07A3F), Offset(hx - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

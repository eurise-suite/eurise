package eu.eurise.voice

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import eu.eurise.voice.speech.VoiceTuning
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.voice.speech.Voice
import eu.eurise.voice.speech.Voices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * I modelli di Eurise Voce in una pagina: Whisper per ascoltare (Alfred e dettatura) e la voce di Alfred per parlare.
 * Si apre da Alfred, dalle impostazioni della sintesi vocale del telefono e da Voce. Ogni download chiede prima conferma
 * con cosa esce e dove va.
 */
class ModelsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EuriseTheme { ModelsPage(onClose = ::finish) } }
    }
}

private sealed interface Item {
    data class Hear(val model: Model) : Item
    data class Speak(val voice: Voice) : Item
}

@Composable
private fun ModelsPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    val progress = remember { mutableStateMapOf<Item, Int>() }
    var ask by remember { mutableStateOf<Item?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Prova della voce: la sintesi del telefono con il motore di Voce stesso.
    val tester = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) { onDispose { tester.value?.shutdown() } }

    fun hear(m: Model) = m.installed(context)
    fun speak(v: Voice) = v.installed(context)

    fun download(item: Item) {
        progress[item] = 0
        failed = false
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                when (item) {
                    is Item.Hear -> Downloader.download(context, item.model) { p -> progress[item] = p }
                    is Item.Speak -> Voices.download(context, item.voice) { p -> progress[item] = p }.also { if (it) Voices.choose(context, item.voice) }
                }
            }
            progress.remove(item)
            failed = !ok
            tick++
        }
    }

    fun tryVoice() {
        // Riprovare rimette in uso una voce «ferma»: se si ferma di nuovo, la spia la segna ancora.
        Voices.clearStopped(context)
        tick++
        tester.value?.shutdown()
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ITALIAN
                tts?.speak(context.getString(R.string.voice_tts_sample), TextToSpeech.QUEUE_FLUSH, null, "prova")
            }
        }, context.packageName)
        tester.value = tts
    }

    Column(
        Modifier.fillMaxSize().background(scheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Letto qui perché la pagina si ridisegni dopo un download o una cancellazione.
        if (tick < 0) return@Column
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.voice_models_title), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.voice_close)) }
        }
        Text(stringResource(R.string.voice_models_lead), fontSize = 13.sp, color = scheme.onSurfaceVariant)

        Section(stringResource(R.string.voice_models_hear))
        Model.entries.forEach { m ->
            val item = Item.Hear(m)
            ModelRow(stringResource(m.label), stringResource(m.detail, m.megabytes), hear(m), progress[item],
                onDownload = { ask = item }, onDelete = { Downloader.delete(context, m); tick++ })
        }

        Section(stringResource(R.string.voice_models_speak))
        // Le tre opzioni di Alfred (14 novembre): Riccardo com'è, Riccardo ritoccato, la voce di un attore.
        val current = Voices.installed(context)
        val riccardo = speak(Voice.Riccardo)
        var tuning by remember { mutableStateOf(Voices.tuning(context)) }
        fun pick(voice: Voice, t: VoiceTuning?) {
            // Il ritocco si salva subito: vale anche se la voce si deve ancora scaricare.
            t?.let { tuning = it; Voices.setTuning(context, it) }
            if (!voice.installed(context)) { ask = Item.Speak(voice); return }
            Voices.choose(context, voice)
            tick++
        }
        Choice(
            stringResource(R.string.voice_opt_plain), stringResource(R.string.voice_opt_plain_d, Voice.Riccardo.megabytes),
            selected = current == Voice.Riccardo && !tuning.retouched, enabled = true, percent = progress[Item.Speak(Voice.Riccardo)],
        ) { pick(Voice.Riccardo, tuning.copy(retouched = false, speed = 1f, pitch = 1f)) }
        Choice(
            stringResource(R.string.voice_opt_butler), stringResource(R.string.voice_opt_butler_d),
            selected = current == Voice.Riccardo && tuning.retouched, enabled = true, percent = null,
        ) { pick(Voice.Riccardo, if (tuning.retouched) tuning else VoiceTuning.BUTLER) }
        if (current == Voice.Riccardo && tuning.retouched) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.voice_opt_speed, (tuning.speed * 100).toInt()), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Slider(tuning.speed, { tuning = tuning.copy(speed = it) }, valueRange = VoiceTuning.SPEED, onValueChangeFinished = { Voices.setTuning(context, tuning) })
                Text(stringResource(R.string.voice_opt_pitch, semitones(tuning.pitch)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Slider(tuning.pitch, { tuning = tuning.copy(pitch = it) }, valueRange = VoiceTuning.PITCH, onValueChangeFinished = { Voices.setTuning(context, tuning) })
                TextButton(onClick = { tuning = VoiceTuning.BUTLER; Voices.setTuning(context, tuning) }) {
                    Text(stringResource(R.string.voice_opt_reset), fontWeight = FontWeight.Bold)
                }
            }
        }
        Choice(
            stringResource(Voice.Attore.label),
            if (Voice.Attore.ready) stringResource(Voice.Attore.detail, Voice.Attore.megabytes) else stringResource(R.string.voice_opt_actor_soon),
            selected = current == Voice.Attore, enabled = Voice.Attore.ready, percent = progress[Item.Speak(Voice.Attore)],
        ) { pick(Voice.Attore, null) }
        if (riccardo) TextButton(onClick = { Voices.delete(context, Voice.Riccardo); tick++ }) {
            Text(stringResource(R.string.voice_opt_delete, Voice.Riccardo.megabytes), fontSize = 12.5.sp)
        }
        Section(stringResource(R.string.voice_models_other))
        listOf(Voice.Paola).forEach { v ->
            val item = Item.Speak(v)
            val inUse = current == v
            ModelRow(stringResource(v.label) + if (inUse) " · " + stringResource(R.string.voice_tts_in_use) else "",
                stringResource(v.detail, v.megabytes), speak(v), progress[item],
                onDownload = { ask = item },
                onDelete = { Voices.delete(context, v); tick++ },
                onChoose = if (speak(v) && !inUse) ({ Voices.choose(context, v); tick++ }) else null)
        }
        if (Voices.installed(context) != null) {
            if (Voices.stopped(context)) Text(stringResource(R.string.voice_tts_stopped), fontSize = 13.sp, color = scheme.error)
            TextButton(onClick = ::tryVoice) { Text(stringResource(R.string.voice_tts_try), fontWeight = FontWeight.Bold) }
            Text(stringResource(R.string.voice_tts_system), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        if (failed) Text(stringResource(R.string.voice_download_failed), color = scheme.error)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(EuriseIcons.lock(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            Text(stringResource(R.string.voice_models_private), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
        }
    }

    ask?.let { item ->
        val (name, mb, host) = when (item) {
            is Item.Hear -> Triple(stringResource(item.model.label), item.model.megabytes, Model.HOST)
            is Item.Speak -> Triple(stringResource(item.voice.label), item.voice.megabytes, Voices.HOST)
        }
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(stringResource(R.string.voice_download_title, name)) },
            text = { Text(stringResource(R.string.voice_download_body, mb, host)) },
            confirmButton = { TextButton(onClick = { ask = null; download(item) }) { Text(stringResource(R.string.voice_download)) } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(stringResource(R.string.voice_cancel)) } },
        )
    }
}

/** Semitoni dal rapporto di frequenza: 0,92 → −1,4. */
private fun semitones(pitch: Float): String = String.format(Locale.ITALIAN, "%+.1f", 12 * kotlin.math.ln(pitch) / kotlin.math.ln(2f))

/** Una delle tre voci di Alfred: un tocco la sceglie (e la scarica, se serve). */
@Composable
private fun Choice(title: String, detail: String, selected: Boolean, enabled: Boolean, percent: Int?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow)
            .selectable(selected = selected, enabled = enabled && percent == null, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant)
            Text(detail, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        if (percent != null) Text("$percent%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
    }
}

@Composable
private fun Section(title: String) =
    Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))

@Composable
private fun ModelRow(
    title: String, detail: String, installed: Boolean, percent: Int?,
    onDownload: () -> Unit, onDelete: () -> Unit, onChoose: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerLow)
            .clickable(enabled = !installed && percent == null, onClick = onDownload).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
            when {
                percent != null -> Text("$percent%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                installed -> {
                    onChoose?.let { TextButton(onClick = it) { Text(stringResource(R.string.voice_tts_use)) } }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.voice_delete)) }
                }
                else -> Icon(EuriseIcons.download(), null, tint = scheme.primary)
            }
        }
        if (percent != null) LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
    }
}

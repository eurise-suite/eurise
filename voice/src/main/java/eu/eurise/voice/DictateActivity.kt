package eu.eurise.voice

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext

/**
 * Dettatura: si parla, Whisper trascrive sul telefono, il testo torna nella tastiera (o negli appunti se l'app è aperta
 * dal cassetto). Si apre come foglio sopra l'app in cui si sta scrivendo.
 */
class DictateActivity : ComponentActivity() {

    private val fromKeyboard get() = intent.getStringExtra(EXTRA_REPLY_TO) != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = intent.getStringExtra(EXTRA_LANGUAGE)?.takeIf { it.length in 2..3 } ?: "auto"
        Speed.load(this)
        setContent { EuriseTheme { DictateSheet(language, fromKeyboard, ::deliver, ::finish) } }
    }

    /** Il testo va alla tastiera che ha chiesto la dettatura (solo app Eurise con la stessa firma), altrimenti negli appunti. */
    private fun deliver(text: String) {
        val reply = intent.getStringExtra(EXTRA_REPLY_TO)
        if (reply != null) {
            sendBroadcast(Intent(ACTION_RESULT).setPackage(reply).putExtra(Intent.EXTRA_TEXT, text), suitePermission())
        } else {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Eurise Voce", text))
        }
        finish()
    }

    private fun suitePermission() =
        if (packageName.endsWith(".debug")) "eu.eurise.permission.SUITE_DEBUG" else "eu.eurise.permission.SUITE"

    companion object {
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_REPLY_TO = "reply_to"
        const val ACTION_RESULT = "eu.eurise.action.DICTATION_RESULT"
    }
}

private sealed interface Phase {
    data object NoModel : Phase
    data class Downloading(val percent: Int) : Phase
    data object Ready : Phase
    data object Listening : Phase
    data object Working : Phase
    data class Done(val text: String) : Phase
    data class Failed(val message: Int) : Phase
}

@Composable
private fun DictateSheet(language: String, fromKeyboard: Boolean, onDeliver: (String) -> Unit, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { Recorder() }
    var phase by remember { mutableStateOf<Phase>(if (Model.installed(context) != null) Phase.Ready else Phase.NoModel) }
    var explain by remember { mutableStateOf<Model?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    var seconds by remember { mutableIntStateOf(0) }
    val scheme = MaterialTheme.colorScheme

    var live by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf<String?>(null) }

    /**
     * Si parla: il modello si carica mentre si parla (non dopo), e ogni pausa lunga fa trascrivere il pezzo appena
     * detto. Fermata la registrazione resta solo l'ultimo pezzo: l'attesa è molto più breve.
     */
    fun listen() {
        val model = Model.installed(context) ?: run { phase = Phase.NoModel; return }
        phase = Phase.Listening
        seconds = 0
        live = ""
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val engine = scope.async(worker) { runCatching { Whisper.load(model.file(context)) }.getOrNull() }
        val pieces = mutableListOf<String>()
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        var done = 0
        fun piece(from: Int, to: Int) {
            jobs += scope.launch(worker) {
                val whisper = engine.await() ?: return@launch
                val text = runCatching { whisper.transcribe(recorder.slice(from, to), language, prompt = pieces.joinToString(" ").takeLast(220)) }.getOrNull()
                if (!text.isNullOrBlank()) { pieces += text; live = pieces.joinToString(" ") }
            }
        }
        scope.launch {
            val audio = withContext(Dispatchers.IO) { recorder.record { cut -> val from = done; done = cut; piece(from, cut) } }
            if (audio.size < Recorder.SAMPLE_RATE / 2 && pieces.isEmpty()) {
                phase = Phase.Ready; engine.await()?.close(); worker.close(); return@launch
            }
            phase = Phase.Working
            if (audio.size - done > Recorder.SAMPLE_RATE / 3) piece(done, audio.size)
            jobs.joinAll()
            val whisper = engine.await()
            withContext(worker) { whisper?.close() }
            worker.close()
            val text = pieces.joinToString(" ").trim()
            phase = when {
                whisper == null -> Phase.Failed(R.string.voice_failed)
                text.isBlank() -> Phase.Failed(R.string.voice_nothing)
                else -> Phase.Done(text)
            }
        }
    }

    /** Misura una volta quanti thread vanno più veloci su questo telefono. */
    fun measure() {
        val model = Model.installed(context) ?: return
        speed = context.getString(R.string.voice_speed_working)
        scope.launch {
            val times = withContext(Dispatchers.Default) {
                runCatching { Whisper.load(model.file(context))?.use { Speed.measure(context, it) } }.getOrNull()
            }
            speed = times?.takeIf { it.isNotEmpty() }?.let { list ->
                val best = list.minBy { it.second }.first
                context.getString(R.string.voice_speed_result, list.joinToString(" · ") { (n, ms) -> "$n: %.1f s".format(ms / 1000.0) }, best)
            } ?: context.getString(R.string.voice_failed)
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) listen() }
    fun start() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(phase) {
        while (phase == Phase.Listening) { level = recorder.level; delay(100); if (++seconds % 10 == 0) Unit }
    }
    LaunchedEffect(Unit) { if (phase == Phase.Ready && fromKeyboard) start() }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f)).clickable(onClick = { recorder.stop(); onClose() }), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.background)
                .clickable(enabled = false) {}.navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.voice_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            when (val p = phase) {
                Phase.NoModel -> {
                    Text(stringResource(R.string.voice_need_model), fontSize = 15.sp)
                    Model.entries.forEach { m ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerLow).clickable { explain = m }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(m.label), fontWeight = FontWeight.Bold)
                                Text(stringResource(m.detail, m.megabytes), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                            }
                            Icon(EuriseIcons.download(), null, tint = scheme.primary)
                        }
                    }
                }
                is Phase.Downloading -> {
                    Text(stringResource(R.string.voice_downloading, p.percent))
                    LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.fillMaxWidth())
                }
                Phase.Ready, Phase.Listening -> {
                    val listening = p == Phase.Listening
                    Text(stringResource(if (listening) R.string.voice_listening else R.string.voice_tap), fontSize = 15.sp)
                    if (listening && live.isNotEmpty()) Text(live, fontSize = 14.sp, color = scheme.onSurfaceVariant, maxLines = 4)
                    val label = stringResource(if (listening) R.string.voice_stop else R.string.voice_start)
                    Box(Modifier.align(Alignment.CenterHorizontally).size(84.dp).scale(1f + level * 0.6f).clip(CircleShape)
                        .background(if (listening) scheme.primary else scheme.surfaceContainerLow)
                        .clickable { if (listening) recorder.stop() else start() }.semantics { contentDescription = label },
                        contentAlignment = Alignment.Center) {
                        Icon(EuriseIcons.mic(), null, tint = if (listening) scheme.onPrimary else scheme.primary, modifier = Modifier.size(34.dp))
                    }
                }
                Phase.Working -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (live.isNotEmpty()) Text(live, fontSize = 14.sp, color = scheme.onSurfaceVariant, maxLines = 4)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text(stringResource(R.string.voice_working))
                    }
                }
                is Phase.Done -> {
                    var text by remember(p) { mutableStateOf(p.text) }
                    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { phase = Phase.Ready; start() }) { Text(stringResource(R.string.voice_again)) }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { onDeliver(text) }) { Text(stringResource(if (fromKeyboard) R.string.voice_insert else R.string.voice_copy), fontWeight = FontWeight.Bold) }
                    }
                }
                is Phase.Failed -> {
                    Text(stringResource(p.message))
                    Button(onClick = { phase = Phase.Ready; start() }) { Text(stringResource(R.string.voice_again)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(EuriseIcons.lock(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                Text(stringResource(R.string.voice_private), fontSize = 11.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (phase == Phase.Ready && !fromKeyboard) TextButton(onClick = ::measure, enabled = speed != context.getString(R.string.voice_speed_working)) {
                    Text(stringResource(R.string.voice_speed), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            speed?.let { Text(it, fontSize = 12.sp, color = scheme.onSurfaceVariant) }
        }
    }

    explain?.let { m ->
        AlertDialog(
            onDismissRequest = { explain = null },
            title = { Text(stringResource(R.string.voice_download_title, stringResource(m.label))) },
            text = { Text(stringResource(R.string.voice_download_body, m.megabytes, Model.HOST)) },
            confirmButton = {
                TextButton(onClick = {
                    explain = null
                    phase = Phase.Downloading(0)
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Downloader.download(context, m) { pct -> phase = Phase.Downloading(pct) } }
                        phase = if (ok) Phase.Ready else Phase.Failed(R.string.voice_download_failed)
                    }
                }) { Text(stringResource(R.string.voice_download)) }
            },
            dismissButton = { TextButton(onClick = { explain = null }) { Text(stringResource(R.string.voice_cancel)) } },
        )
    }
}

package eu.eurise.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.log10

/**
 * Eurise Voce come servizio di riconoscimento, solo per le app della suite (permesso di firma): Alfred chiede di
 * ascoltare e la registrazione parte subito, senza schermate né tasti; quando si smette di parlare ([Endpoint]) Whisper
 * trascrive sul telefono e il testo torna a chi ha chiesto. Il modello resta caricato finché il servizio è vivo, così
 * la seconda domanda è più rapida. L'audio resta in memoria e si butta dopo la trascrizione.
 */
class VoiceRecognitionService : RecognitionService() {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private var engine: Future<Whisper?>? = null
    private var loadedModel: Model? = null
    @Volatile private var recorder: Recorder? = null
    @Volatile private var cancelled = false

    override fun onStartListening(intent: Intent, listener: Callback) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.safe { error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }
            return
        }
        val model = Model.installed(this) ?: run { listener.safe { error(SpeechRecognizer.ERROR_SERVER) }; return }
        val language = intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)?.substringBefore('-')?.takeIf { it.length in 2..3 } ?: "auto"
        Speed.load(this)
        // Il modello si carica mentre si parla: quando la frase finisce è già pronto.
        if (engine == null || loadedModel != model) {
            engine?.let { old -> worker.execute { runCatching { old.get()?.close() } } }
            loadedModel = model
            engine = worker.submit<Whisper?> { runCatching { Whisper.load(model.file(this)) }.getOrNull() }
        }
        cancelled = false
        val rec = Recorder(maxSeconds = 25)
        recorder = rec
        listener.safe { readyForSpeech(Bundle()) }
        Thread({
            val endpoint = Endpoint()
            var heard = false
            var noSpeech = false
            val audio = rec.record(onBlock = { rms, end ->
                listener.safe { rmsChanged(if (rms <= 0f) -2f else (20 * log10(rms) + 50).coerceIn(-2f, 10f)) }
                when (endpoint.feed(rms, end)) {
                    Endpoint.Event.Started -> { heard = true; listener.safe { beginningOfSpeech() } }
                    Endpoint.Event.Finished -> rec.stop()
                    Endpoint.Event.NoSpeech -> { noSpeech = true; rec.stop() }
                    Endpoint.Event.None -> Unit
                }
            })
            recorder = null
            if (cancelled) return@Thread
            listener.safe { endOfSpeech() }
            if (noSpeech || !heard || audio.size < Recorder.SAMPLE_RATE / 3) {
                listener.safe { error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
                return@Thread
            }
            val text = runCatching {
                val whisper = engine?.get() ?: return@runCatching null
                worker.submit<String> { whisper.transcribe(audio, language, fullContext = false) }.get()
            }.getOrNull()?.trim()
            if (cancelled) return@Thread
            when {
                text == null -> listener.safe { error(SpeechRecognizer.ERROR_SERVER) }
                text.isBlank() -> listener.safe { error(SpeechRecognizer.ERROR_NO_MATCH) }
                else -> listener.safe {
                    results(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) })
                }
            }
        }, "eurise-voice-listen").start()
    }

    /** «Ho finito»: si ferma la registrazione e si trascrive quel che c'è. */
    override fun onStopListening(listener: Callback) {
        recorder?.stop()
    }

    override fun onCancel(listener: Callback) {
        cancelled = true
        recorder?.stop()
    }

    override fun onDestroy() {
        cancelled = true
        recorder?.stop()
        engine?.let { e -> worker.execute { runCatching { e.get()?.close() } } }
        worker.shutdown()
        super.onDestroy()
    }

    /** Chi ha chiesto può essersene andato: i suoi avvisi non devono far cadere il servizio. */
    private inline fun Callback.safe(block: Callback.() -> Unit) {
        runCatching { block() }
    }
}

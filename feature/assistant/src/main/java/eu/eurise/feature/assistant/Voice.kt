package eu.eurise.feature.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import eu.eurise.core.contacts.Reach
import java.util.Locale

/**
 * Come si parla ad Alfred: con Eurise Voce (Whisper sul telefono, il testo torna con il permesso della suite) oppure,
 * se non c'è, con il riconoscimento vocale del telefono. Va creato in `onCreate` dell'attività.
 */
class Dictation(private val activity: ComponentActivity) {
    private var onText: ((String) -> Unit)? = null

    private val system = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { onText?.invoke(it) }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { onText?.invoke(it) }
        }
    }
    private var registered = false

    private val voicePackage: String? get() = listOf("eu.eurise.voice", "eu.eurise.voice.debug").firstOrNull { Reach.installed(activity, it) }

    private val systemIntent get() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

    enum class Source { EuriseVoice, System, None }

    val source: Source get() = when {
        voicePackage != null -> Source.EuriseVoice
        activity.packageManager.resolveActivity(systemIntent, 0) != null -> Source.System
        else -> Source.None
    }

    /** Ascolta e restituisce il testo detto; falso se non c'è modo di ascoltare. */
    fun start(onText: (String) -> Unit): Boolean {
        this.onText = onText
        val voice = voicePackage
        if (voice != null) {
            if (!registered) {
                val filter = IntentFilter(ACTION_RESULT)
                if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, suitePermission(), null, Context.RECEIVER_EXPORTED)
                else activity.registerReceiver(receiver, filter, suitePermission(), null)
                registered = true
            }
            val intent = Intent().setClassName(voice, "eu.eurise.voice.DictateActivity")
                .putExtra("reply_to", activity.packageName)
                .putExtra("language", Locale.getDefault().language)
            return runCatching { activity.startActivity(intent) }.isSuccess
        }
        return runCatching { system.launch(systemIntent) }.isSuccess
    }

    fun release() {
        if (registered) runCatching { activity.unregisterReceiver(receiver) }
        registered = false
    }

    private fun suitePermission() =
        if (activity.packageName.endsWith(".debug")) "eu.eurise.permission.SUITE_DEBUG" else "eu.eurise.permission.SUITE"

    private companion object {
        const val ACTION_RESULT = "eu.eurise.action.DICTATION_RESULT"
    }
}

/**
 * Le risposte di Alfred lette ad alta voce, con la sintesi del telefono (sceglie una voce italiana maschile se il
 * motore ne ha una). La voce propria di Alfred, scaricata a richiesta, arriverà dopo.
 */
class Speaker(context: Context) {
    private var ready = false
    private var pending: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            pickVoice()
            pending?.let { say(it) }
            pending = null
        }
    }

    private fun pickVoice() = tts.alfredVoice()

    fun say(text: String) {
        if (!ready) { pending = text; return }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alfred")
    }

    /** Zitto: quando si comincia a parlare, Alfred smette di leggere. */
    fun stop() = runCatching { tts.stop() }

    fun release() = runCatching { tts.stop(); tts.shutdown() }
}

/** La voce di Alfred con la sintesi del telefono: italiana, locale, maschile se il motore ne ha una, pacata. */
internal fun TextToSpeech.alfredVoice() = runCatching {
    language = Locale.ITALIAN
    val italian = voices.orEmpty().filter { it.locale.language == "it" && !it.isNetworkConnectionRequired }
    (italian.firstOrNull { "male" in it.name.lowercase() && "female" !in it.name.lowercase() } ?: italian.firstOrNull())?.let { voice = it }
    setSpeechRate(0.95f)
    setPitch(0.9f)
}

/**
 * Alfred che ascolta senza altri tasti: un tocco sul microfono e parte; quando si smette di parlare capisce da solo che
 * si è finito e passa il testo. Prima Eurise Voce (Whisper sul telefono, servizio riservato alla suite), poi il
 * riconoscimento del telefono se c'è. Se nessuno dei due può ascoltare qui, resta la dettatura classica ([Dictation]).
 */
class Ear(private val context: Context) {
    private var recognizer: android.speech.SpeechRecognizer? = null

    /** Da dove ascolta: Eurise Voce, il telefono, oppure nessuno (serve la dettatura a schermo). */
    val available: Boolean get() = component() != null || android.speech.SpeechRecognizer.isRecognitionAvailable(context)

    private fun component(): android.content.ComponentName? =
        listOf("eu.eurise.voice", "eu.eurise.voice.debug").firstOrNull { Reach.installed(context, it) }
            ?.let { android.content.ComponentName(it, "eu.eurise.voice.VoiceRecognitionService") }
            ?.takeIf { c ->
                runCatching { context.packageManager.getServiceInfo(c, 0) }.isSuccess
            }

    /**
     * Ascolta. [onLevel] riceve il volume (0..1) per l'animazione, [onText] la frase, [onFail] il codice d'errore di
     * [android.speech.SpeechRecognizer] (per esempio niente voce o modello mancante).
     */
    fun listen(onLevel: (Float) -> Unit, onSpeech: () -> Unit, onText: (String) -> Unit, onFail: (Int) -> Unit): Boolean {
        cancel()
        val c = component()
        val r = runCatching {
            if (c != null) android.speech.SpeechRecognizer.createSpeechRecognizer(context, c)
            else android.speech.SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull() ?: return false
        recognizer = r
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = onSpeech()
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) { onFail(error) }
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (text.isNullOrBlank()) onFail(android.speech.SpeechRecognizer.ERROR_NO_MATCH) else onText(text)
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        return runCatching { r.startListening(intent) }.isSuccess
    }

    /** «Ho finito» toccando di nuovo: si trascrive subito quel che c'è. */
    fun finish() = runCatching { recognizer?.stopListening() }

    fun cancel() {
        recognizer?.let { runCatching { it.cancel(); it.destroy() } }
        recognizer = null
    }
}

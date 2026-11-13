package eu.eurise.feature.assistant

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import eu.eurise.core.contacts.Reach
import eu.eurise.core.suite.SpeechText
import eu.eurise.core.suite.VoiceContract
import java.util.Locale

/**
 * La sintesi per Alfred: prima la sua voce neurale in Eurise Voce (scaricata a richiesta), poi il motore del telefono con
 * una voce italiana locale. [onReady] riceve il motore pronto e se è la voce di Alfred ([natural]).
 */
internal object AlfredTts {
    fun voicePackage(context: Context): String? = VoiceContract.PACKAGES.firstOrNull { Reach.installed(context, it) }

    fun open(context: Context, onReady: (TextToSpeech, natural: Boolean) -> Unit): Holder {
        val app = context.applicationContext
        val holder = Holder()
        val voice = voicePackage(app)
        fun system() {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(app) { status ->
                val t = tts ?: return@TextToSpeech
                if (status == TextToSpeech.SUCCESS) { t.phoneVoice(); onReady(t, false) }
            }
            holder.tts = tts
        }
        if (voice == null) { system(); return holder }
        var ours: TextToSpeech? = null
        ours = TextToSpeech(app, { status ->
            val t = ours ?: return@TextToSpeech
            val ok = status == TextToSpeech.SUCCESS && t.isLanguageAvailable(Locale.ITALIAN) >= TextToSpeech.LANG_AVAILABLE
            if (ok) { t.language = Locale.ITALIAN; onReady(t, true) }
            else { runCatching { t.shutdown() }; if (!holder.closed) system() }
        }, voice)
        holder.tts = ours
        return holder
    }

    /** Apre in Eurise Voce la pagina dove si scaricano la voce di Alfred e il modello per ascoltare. */
    fun openModels(context: Context): Boolean {
        val pkg = voicePackage(context) ?: return false
        return runCatching {
            context.startActivity(Intent(VoiceContract.ACTION_MODELS).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    class Holder {
        @Volatile var tts: TextToSpeech? = null
        @Volatile var closed = false
        fun shutdown() { closed = true; runCatching { tts?.stop(); tts?.shutdown() } }
    }
}

/** Il motore del telefono: una voce italiana locale, maschile se il motore ne ha una, pacata. */
internal fun TextToSpeech.phoneVoice() = runCatching {
    language = Locale.ITALIAN
    val italian = voices.orEmpty().filter { it.locale.language == "it" && !it.isNetworkConnectionRequired }
    (italian.firstOrNull { "male" in it.name.lowercase() && "female" !in it.name.lowercase() } ?: italian.firstOrNull())?.let { voice = it }
    setSpeechRate(0.95f)
    setPitch(0.9f)
}

/**
 * Le risposte di Alfred lette ad alta voce, con il testo ripulito dai segni ([SpeechText]). [onDone] arriva sul thread
 * principale quando ha finito di leggere una frase (serve per ascoltare subito la risposta). [natural] dice se sta
 * usando la sua voce o quella del telefono.
 */
class Speaker(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var pending: Pair<String, (() -> Unit)?>? = null
    private val done = mutableMapOf<String, () -> Unit>()
    private var counter = 0

    @Volatile var natural = false
        private set

    private val holder = AlfredTts.open(context) { tts, isNatural ->
        natural = isNatural
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) = Unit
            override fun onDone(id: String) { main.post { done.remove(id)?.invoke() } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) { main.post { done.remove(id)?.invoke() } }
            override fun onStop(id: String, interrupted: Boolean) { main.post { done.remove(id) } }
        })
        main.post {
            engine = tts
            pending?.let { (t, cb) -> say(t, cb) }
            pending = null
        }
    }

    fun say(text: String, onDone: (() -> Unit)? = null) {
        val line = SpeechText.clean(text)
        val tts = engine ?: run { pending = line to onDone; return }
        val id = "alfred-${counter++}"
        if (onDone != null) done[id] = onDone
        tts.speak(line, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    /** Zitto: quando si comincia a parlare, Alfred smette di leggere. */
    fun stop() { done.clear(); runCatching { engine?.stop() } }

    fun release() { done.clear(); holder.shutdown() }
}

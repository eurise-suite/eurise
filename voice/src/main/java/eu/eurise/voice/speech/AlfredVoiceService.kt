package eu.eurise.voice.speech

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import eu.eurise.core.suite.SpeechText

/**
 * Eurise Voce come motore di sintesi di Android: la voce di Alfred, neurale e tutta sul telefono (Piper con sherpa-onnx).
 * Alfred la chiede per nome; Mappe e le altre app la usano se la persona la sceglie come motore del telefono. Il testo
 * arriva dall'app che legge, diventa audio qui e non si salva. Senza voce scaricata, o con la voce «ferma» (vedi
 * [Voices.checkCrash]), risponde «dati mancanti» e l'app torna al motore del telefono.
 *
 * Gira nel processo ":voce", separato dalla pagina dei modelli. Si genera una frase alla volta con `generate` (senza la
 * funzione di ritorno chiamata dal codice nativo, che dopo la prova del 13 novembre era il sospettato della chiusura).
 */
class AlfredVoiceService : TextToSpeechService() {
    private var engine: OfflineTts? = null
    private var loaded: VoicePack? = null
    @Volatile private var stopped = false

    override fun onCreate() {
        Voices.checkCrash(this)
        super.onCreate()
    }

    override fun onDestroy() {
        engine?.release()
        engine = null
        super.onDestroy()
    }

    private fun italian(lang: String?) = lang.equals("ita", true) || lang.equals("it", true)

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int = when {
        !italian(lang) -> TextToSpeech.LANG_NOT_SUPPORTED
        Voices.current(this) == null || Voices.stopped(this) -> TextToSpeech.LANG_MISSING_DATA
        country.isNullOrEmpty() || country.equals("ITA", true) || country.equals("IT", true) -> TextToSpeech.LANG_COUNTRY_AVAILABLE
        else -> TextToSpeech.LANG_AVAILABLE
    }

    override fun onGetLanguage(): Array<String> = arrayOf("ita", "ITA", "")

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val r = onIsLanguageAvailable(lang, country, variant)
        if (r >= TextToSpeech.LANG_AVAILABLE && ensureEngine() == null) return TextToSpeech.LANG_MISSING_DATA
        return r
    }

    override fun onStop() { stopped = true }

    @Synchronized
    private fun ensureEngine(): OfflineTts? {
        val voice = Voices.current(this) ?: return null
        if (engine != null && loaded == voice) return engine
        engine?.release()
        engine = null
        Voices.speakingStart(this)
        engine = runCatching {
            OfflineTts(
                config = OfflineTtsConfig(
                    model = if (voice.supertonicSid != null) OfflineTtsModelConfig(
                        // Supertonic 3: quattro modelli; l'italiano lo legge da sé (lingua passata a ogni frase).
                        supertonic = OfflineTtsSupertonicModelConfig(
                            durationPredictor = voice.supertonic("duration_predictor.int8.onnx").absolutePath,
                            textEncoder = voice.supertonic("text_encoder.int8.onnx").absolutePath,
                            vectorEstimator = voice.supertonic("vector_estimator.int8.onnx").absolutePath,
                            vocoder = voice.supertonic("vocoder.int8.onnx").absolutePath,
                            ttsJson = voice.supertonic("tts.json").absolutePath,
                            unicodeIndexer = voice.supertonic("unicode_indexer.bin").absolutePath,
                            voiceStyle = voice.supertonic("voice.bin").absolutePath,
                        ),
                        numThreads = 4,
                        debug = false,
                        provider = "cpu",
                    ) else if (voice.kokoroSid != null) OfflineTtsModelConfig(
                        // Kokoro: modello unico per più lingue; l'italiano lo pronuncia espeak-ng («it»).
                        kokoro = OfflineTtsKokoroModelConfig(
                            model = voice.model.absolutePath,
                            voices = voice.styles.absolutePath,
                            tokens = voice.tokens.absolutePath,
                            dataDir = voice.data.absolutePath,
                            lang = "it",
                        ),
                        numThreads = 4,
                        debug = false,
                        provider = "cpu",
                    ) else OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = voice.model.absolutePath,
                            tokens = voice.tokens.absolutePath,
                            dataDir = voice.data.absolutePath,
                        ),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu",
                    ),
                ),
            )
        }.getOrNull()
        // La prima frase di un modello ONNX appena caricato è molto più lenta delle altre: la si fa adesso (Alfred apre
        // la voce mentre si parla), così la risposta vera parte subito.
        engine?.let { e -> runCatching { speak(e, voice, "Sì.", 1f) } }
        Voices.speakingEnd(this)
        loaded = voice
        return engine
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopped = false
        val text = SpeechText.clean(request.charSequenceText?.toString() ?: request.text.orEmpty())
        val tts = if (italian(request.language) && !Voices.stopped(this)) ensureEngine() else null
        if (tts == null) { callback.error(); return }
        // Riccardo ritoccato: frequenza dichiarata più bassa (tono più grave) e Piper un po' più svelto per compensare,
        // così il passo finale è quello scelto. Vale solo per Riccardo: le altre voci restano come sono.
        val tuning = if (loaded?.id == Voice.Riccardo.id) Voices.tuning(this) else VoiceTuning.PLAIN
        val rate = (tts.sampleRate() * tuning.pitch).toInt()
        if (callback.start(rate, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
        if (text.isBlank()) { callback.done(); return }
        // La velocità del telefono (100 = normale). Kokoro in italiano è lento di suo: Nicola e Sara un po' più svelti
        // (prova del 17 novembre: «estremamente lento nel parlare»); Piper com'era.
        val pace = when {
            loaded?.supertonicSid != null -> SUPERTONIC_PACE
            loaded?.kokoroSid != null -> KOKORO_PACE
            else -> 0.95f
        }
        val speed = (request.speechRate / 100f).coerceIn(0.5f, 2.5f) * pace * tuning.speed / tuning.pitch
        val pause = ByteArray(rate * tuning.pauseMs / 1000 * 2)
        val max = callback.maxBufferSize.coerceAtLeast(1024)
        Voices.speakingStart(this)
        val ok = runCatching {
            for ((i, sentence) in SpeechChunks.split(text).withIndex()) {
                if (stopped) break
                val audio = speak(tts, loaded, sentence, speed)
                val bytes = if (i > 0 && pause.isNotEmpty()) pause + pcm(audio.samples) else pcm(audio.samples)
                var off = 0
                while (off < bytes.size && !stopped) {
                    val n = minOf(max, bytes.size - off)
                    if (callback.audioAvailable(bytes, off, n) != TextToSpeech.SUCCESS) { stopped = true; break }
                    off += n
                }
            }
        }.isSuccess
        Voices.speakingEnd(this)
        if (ok) callback.done() else callback.error()
    }

    /** Una frase con la voce [voice]: Supertonic vuole la lingua e il numero della voce nella configurazione. */
    private fun speak(tts: OfflineTts, voice: VoicePack?, text: String, speed: Float): GeneratedAudio {
        val st = voice?.supertonicSid ?: return tts.generate(text, voice?.kokoroSid ?: 0, speed)
        return tts.generateWithConfig(text, GenerationConfig(speed = speed, sid = st, extra = mapOf("lang" to "it")))
    }

    private fun pcm(samples: FloatArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
            bytes[2 * i] = v.toByte()
            bytes[2 * i + 1] = (v shr 8).toByte()
        }
        return bytes
    }

    private companion object {
        /** Passo di Kokoro rispetto al suo normale: deciso a orecchio, da ritoccare dopo la prova. */
        const val KOKORO_PACE = 1.15f

        /** Supertonic parla già a buon passo: appena un filo più svelto, per non far aspettare. */
        const val SUPERTONIC_PACE = 1.05f
    }
}

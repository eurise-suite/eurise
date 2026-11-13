package eu.eurise.voice.speech

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import eu.eurise.core.suite.SpeechText

/**
 * Eurise Voce come motore di sintesi di Android: la voce di Alfred, neurale e tutta sul telefono (Piper con sherpa-onnx).
 * Alfred la chiede per nome; Mappe e le altre app la usano se la persona la sceglie come motore del telefono. Il testo
 * arriva dall'app che legge, diventa audio qui e non si salva. Senza voce scaricata risponde «dati mancanti» e l'app
 * torna al motore del telefono.
 */
class AlfredVoiceService : TextToSpeechService() {
    private var engine: OfflineTts? = null
    private var loaded: Voice? = null
    @Volatile private var stopped = false

    override fun onDestroy() {
        engine?.release()
        engine = null
        super.onDestroy()
    }

    private fun italian(lang: String?) = lang.equals("ita", true) || lang.equals("it", true)

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int = when {
        !italian(lang) -> TextToSpeech.LANG_NOT_SUPPORTED
        Voices.installed(this) == null -> TextToSpeech.LANG_MISSING_DATA
        country.isNullOrEmpty() || country.equals("ITA", true) || country.equals("IT", true) -> TextToSpeech.LANG_COUNTRY_AVAILABLE
        else -> TextToSpeech.LANG_AVAILABLE
    }

    override fun onGetLanguage(): Array<String> = arrayOf("ita", "ITA", "")

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val r = onIsLanguageAvailable(lang, country, variant)
        if (r >= TextToSpeech.LANG_AVAILABLE) ensureEngine()
        return r
    }

    override fun onStop() { stopped = true }

    @Synchronized
    private fun ensureEngine(): OfflineTts? {
        val voice = Voices.installed(this) ?: return null
        if (engine != null && loaded == voice) return engine
        engine?.release()
        engine = runCatching {
            OfflineTts(
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = voice.model(this).absolutePath,
                            tokens = voice.tokens(this).absolutePath,
                            dataDir = voice.data(this).absolutePath,
                        ),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu",
                    ),
                ),
            )
        }.getOrNull()
        loaded = voice
        return engine
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopped = false
        val text = SpeechText.clean(request.charSequenceText?.toString() ?: request.text.orEmpty())
        val tts = if (italian(request.language)) ensureEngine() else null
        if (tts == null) { callback.error(); return }
        if (text.isBlank()) { callback.start(tts.sampleRate(), AudioFormat.ENCODING_PCM_16BIT, 1); callback.done(); return }
        // La velocità del telefono (100 = normale); Alfred parla un filo più piano.
        val speed = (request.speechRate / 100f).coerceIn(0.5f, 2.5f) * 0.95f
        if (callback.start(tts.sampleRate(), AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
        val max = callback.maxBufferSize.coerceAtLeast(1024)
        val ok = runCatching {
            tts.generateWithCallback(text, 0, speed) { samples ->
                if (stopped) return@generateWithCallback 0
                val bytes = ByteArray(samples.size * 2)
                for (i in samples.indices) {
                    val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
                    bytes[2 * i] = v.toByte()
                    bytes[2 * i + 1] = (v shr 8).toByte()
                }
                var off = 0
                while (off < bytes.size && !stopped) {
                    val n = minOf(max, bytes.size - off)
                    if (callback.audioAvailable(bytes, off, n) != TextToSpeech.SUCCESS) { stopped = true; break }
                    off += n
                }
                if (stopped) 0 else 1
            }
        }.isSuccess
        if (ok) callback.done() else callback.error()
    }
}

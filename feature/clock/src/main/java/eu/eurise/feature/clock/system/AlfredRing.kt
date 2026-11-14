package eu.eurise.feature.clock.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import eu.eurise.core.data.alfred.AlfredMusic
import eu.eurise.core.data.alfred.AlfredPrefs
import eu.eurise.core.data.alfred.AlfredWake
import eu.eurise.core.data.alfred.AlfredWakeUp
import eu.eurise.core.data.alfred.addressSuffix
import eu.eurise.core.data.weather.MeteoRepository
import eu.eurise.core.suite.SpeechText
import eu.eurise.core.suite.VoiceContract
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * La sveglia di Alfred (tavola AlfSveglia), mentre suona: Alfred saluta sul canale della sveglia (sua voce in Eurise
 * Voce, altrimenti quella del telefono), poi il brano parte dove la persona ha scelto. Rete di sicurezza: se la voce non
 * parte o la musica non suona entro pochi secondi, [fallback] fa suonare la suoneria, che cresce piano. Tutto sul
 * telefono: all'app di musica arrivano solo titolo e autore.
 */
class AlfredRing(private val context: Context, private val fallback: () -> Unit, private val playLocal: (android.net.Uri) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var done = false

    /** Il testo della sveglia, con il meteo già sul telefono (nessuna richiesta di rete a quest'ora). */
    fun compose(): AlfredWakeUp {
        val prefs = AlfredPrefs(context)
        val now = runCatching { kotlinx.coroutines.runBlocking { MeteoRepository(context).cached() } }.getOrNull()?.forecast?.now
        return AlfredWake.compose(LocalDate.now(), LocalTime.now(), addressSuffix(prefs), now)
    }

    /** Dove suonerà il brano, per la scheda sullo schermo: nome dell'app, «telefono» o null (suoneria). */
    fun musicLabel(): String? {
        val pkg = AlfredPrefs(context).musicAlarm
        return when (pkg) {
            "", RINGTONE -> null
            PHONE -> PHONE
            else -> AlfredMusic.apps(context).firstOrNull { it.first == pkg }?.second
        }
    }

    fun start(wake: AlfredWakeUp) {
        // Se la voce non comincia entro 8 secondi (motore assente o fermo), si passa alla musica.
        main.postDelayed({ if (!done) music(wake) }, 8_000)
        val pkg = VoiceContract.PACKAGES.firstOrNull { p -> runCatching { context.packageManager.getPackageInfo(p, 0) }.isSuccess }
        var engine: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            val t = engine ?: return@OnInitListener
            if (status != TextToSpeech.SUCCESS) { main.post { music(wake) }; return@OnInitListener }
            t.language = Locale.ITALIAN
            t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { main.removeCallbacksAndMessages(null) }
                override fun onDone(id: String?) { main.post { music(wake) } }
                @Deprecated("Richiesto dall'interfaccia")
                override fun onError(id: String?) { main.post { music(wake) } }
            })
            t.speak(SpeechText.clean(wake.spoken), TextToSpeech.QUEUE_FLUSH, null, "alfred-sveglia")
        }
        engine = if (pkg != null) TextToSpeech(context, listener, pkg) else TextToSpeech(context, listener)
        tts = engine
    }

    /** Il brano: nell'app scelta, dai brani del telefono, o la suoneria. Poi si controlla che suoni davvero. */
    private fun music(wake: AlfredWakeUp) {
        if (done) return
        done = true
        main.removeCallbacksAndMessages(null)
        runCatching { tts?.shutdown() }; tts = null
        val choice = AlfredPrefs(context).musicAlarm
        when (choice) {
            "", RINGTONE -> { fallback(); return }
            PHONE -> {
                val track = AlfredWake.localTrack(context, wake.pearl)
                if (track != null) playLocal(track) else fallback()
                return
            }
            else -> if (!AlfredMusic.play(context, wake.pearl, choice)) { fallback(); return }
        }
        // L'app di musica ha 10 secondi per suonare; altrimenti la sveglia suona comunque.
        val audio = context.getSystemService(AudioManager::class.java)
        main.postDelayed({ if (audio?.isMusicActive != true) fallback() }, 10_000)
    }

    /** Rimanda: la musica dell'altra app si mette in pausa (si riprenderà al prossimo squillo). */
    fun pauseMusic() {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }

    fun stop() {
        done = true
        main.removeCallbacksAndMessages(null)
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
    }

    companion object {
        /** Scelte speciali nelle disposizioni di Alfred (al posto del pacchetto di un'app). */
        const val PHONE = "telefono"
        const val RINGTONE = "suoneria"
    }
}

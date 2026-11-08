package eu.eurise.clock.system

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import eu.eurise.clock.R
import eu.eurise.clock.data.ClockStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Routine del mattino: dopo aver spento la sveglia legge a voce l'ora e il primo impegno del giorno (se il
 * calendario è acceso), poi fa ripartire la musica dell'ultima app usata. Usa la sintesi vocale del telefono.
 */
object Routine {
    private var tts: TextToSpeech? = null

    fun run(context: Context) {
        val text = buildText(context)
        tts?.shutdown()
        tts = TextToSpeech(context) { status ->
            val engine = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) { playMusic(context); return@TextToSpeech }
            engine.language = Locale.ITALIAN
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { playMusic(context); engine.shutdown(); tts = null }
                @Deprecated("Richiesto dall'interfaccia")
                override fun onError(id: String?) { playMusic(context); engine.shutdown(); tts = null }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "routine")
        }
    }

    private fun buildText(context: Context): String {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm"))
        val parts = mutableListOf(context.getString(R.string.routine_hello, time))
        if (ClockStore.get(context).settings.value.useCalendar) {
            val first = CalendarReader.firstEvent(context, LocalDate.now())
            parts += if (first != null) {
                context.getString(R.string.routine_first, first.second.format(DateTimeFormatter.ofPattern("H:mm")), first.first)
            } else {
                context.getString(R.string.routine_free)
            }
        }
        return parts.joinToString(" ")
    }

    /** "Play" come dal tasto delle cuffie: riparte la musica dell'ultima app che ha suonato. */
    private fun playMusic(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
    }
}

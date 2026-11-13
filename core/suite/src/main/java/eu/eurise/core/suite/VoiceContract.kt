package eu.eurise.core.suite

/**
 * Accordo tra le app della suite ed Eurise Voce.
 *
 * **Trascrizione** ([ACTION_TRANSCRIBE], servizio con il permesso della suite): l'app che ascolta (Alfred) registra da sé
 * il microfono, perché è lei in primo piano e ha il permesso; quando la frase è finita ([Endpoint]) manda a Voce
 * [MSG_TRANSCRIBE] con l'audio in [KEY_AUDIO] (il lato da leggere di un tubo: PCM a 16 bit, little-endian, mono, 16 kHz) e
 * la lingua. Voce trascrive con Whisper sul telefono e risponde [MSG_RESULT] con [KEY_TEXT] o [KEY_ERROR]. L'audio resta
 * in memoria e non si salva. [MSG_STATUS] chiede cosa è pronto: [KEY_HEARING] (modello di Whisper) e [KEY_SPEAKING]
 * (voce di Alfred).
 *
 * **Voce di Alfred**: Voce è anche un motore di sintesi di Android ([TextToSpeech] con il pacchetto di Voce), con le voci
 * neurali scaricate a richiesta. Lo usano Alfred e, se la persona lo sceglie come motore del telefono, anche Mappe.
 *
 * [ACTION_MODELS] apre in Voce la pagina dove si scaricano modello e voce.
 */
object VoiceContract {
    const val ACTION_TRANSCRIBE = "eu.eurise.action.TRANSCRIBE"
    const val ACTION_MODELS = "eu.eurise.action.VOICE_MODELS"

    const val MSG_TRANSCRIBE = 1
    const val MSG_STATUS = 2
    const val MSG_RESULT = 3

    /** ParcelFileDescriptor: il lato da leggere del tubo con l'audio. */
    const val KEY_AUDIO = "audio"
    /** String: codice di due lettere della lingua («it»), oppure «auto». */
    const val KEY_LANGUAGE = "language"
    /** Frasi d'esempio che orientano Whisper sulle parole attese (comandi brevi di Alfred); facoltativo. */
    const val KEY_PROMPT = "prompt"
    const val KEY_TEXT = "text"
    const val KEY_ERROR = "error"
    const val KEY_HEARING = "hearing"
    const val KEY_SPEAKING = "speaking"

    /** Manca il modello di Whisper: si scarica in Voce ([ACTION_MODELS]). */
    const val ERROR_NO_MODEL = "no_model"
    /** Nessuna parola nell'audio. */
    const val ERROR_NOTHING = "nothing"
    const val ERROR_FAILED = "failed"

    /** Pacchetti di Voce: ufficiale e di prova. */
    val PACKAGES = listOf("eu.eurise.voice", "eu.eurise.voice.debug")

    /** Campioni al secondo dell'audio che si manda. */
    const val SAMPLE_RATE = 16_000
}

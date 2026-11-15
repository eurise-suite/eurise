package eu.eurise.core.suite

/**
 * Il cervello di Alfred: il servizio di Eurise AI (APK senza Internet) a cui Alfred chiede di capire una frase o di
 * rispondere a una domanda. Si collega solo chi ha il permesso di firma della suite.
 *
 * Messaggi (Messenger, [android.os.Message.replyTo] per le risposte):
 * - [MSG_STATUS] → [MSG_STATUS] con [KEY_READY], [KEY_MODEL] (il modello in uso) e [KEY_MODELS] (quelli installati).
 * - [MSG_INSTALL] con [KEY_FILE] (il GGUF scaricato da Eurise, letto e copiato) e [KEY_NAME] → [MSG_STATUS].
 * - [MSG_USE] con [KEY_NAME] → [MSG_STATUS]; [MSG_REMOVE] con [KEY_NAME] → [MSG_STATUS].
 * - [MSG_COMMAND] con [KEY_TEXT], [KEY_HINTS], [KEY_DATE] (AAAA-MM-GG) → [MSG_COMMAND] con [KEY_TEXT]: da una a tre
 *   righe «tipo|chi|testo|data|ora|canale».
 * - [MSG_ASK] con [KEY_TEXT] → tanti [MSG_PIECE] con [KEY_TEXT], poi [MSG_DONE]. [MSG_STOP] ferma la risposta.
 * Errori: [KEY_ERROR] ([ERROR_NO_MODEL], [ERROR_FAILED]).
 */
object BrainContract {
    const val ACTION_BRAIN = "eu.eurise.action.BRAIN"

    const val MSG_STATUS = 1
    const val MSG_INSTALL = 2
    const val MSG_USE = 3
    const val MSG_REMOVE = 4
    const val MSG_COMMAND = 5
    const val MSG_ASK = 6
    const val MSG_PIECE = 7
    const val MSG_DONE = 8
    const val MSG_STOP = 9

    const val KEY_READY = "ready"
    const val KEY_MODEL = "model"
    const val KEY_MODELS = "models"
    const val KEY_FILE = "file"
    const val KEY_NAME = "name"
    const val KEY_TEXT = "text"
    const val KEY_HINTS = "hints"
    const val KEY_DATE = "date"
    const val KEY_MS = "ms"
    const val KEY_ERROR = "error"

    const val ERROR_NO_MODEL = "no_model"
    const val ERROR_FAILED = "failed"

    /** Pacchetti di Eurise AI: ufficiale e di prova. */
    val PACKAGES = listOf("eu.eurise.ai", "eu.eurise.ai.debug")
}

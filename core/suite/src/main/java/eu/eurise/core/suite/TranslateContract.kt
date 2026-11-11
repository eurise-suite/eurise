package eu.eurise.core.suite

/**
 * Accordo tra le app della suite (Tastiera, Messaggi, Eurise) ed Eurise Leggi per tradurre un testo sul telefono. L'app si
 * collega al servizio di traduzione di Leggi (solo app con la stessa firma: permesso della suite) e manda [MSG_TRANSLATE]
 * con il testo e le lingue; Leggi risponde con [MSG_RESULT]. Il testo non esce dal telefono e Leggi non lo salva.
 * Le lingue sono codici di due lettere («it», «en», «fr»…); tra due lingue diverse dall'inglese si passa dall'inglese.
 * [MSG_LANGUAGES] chiede le lingue scaricate: la risposta ha [KEY_LANGUAGES].
 */
object TranslateContract {
    const val ACTION_TRANSLATE = "eu.eurise.action.TRANSLATE"
    const val MSG_TRANSLATE = 1
    const val MSG_LANGUAGES = 2
    const val MSG_RESULT = 3

    /** String: il testo da tradurre (righe separate da a capo; al massimo [MAX_CHARS] caratteri). */
    const val KEY_TEXT = "text"
    /** String: lingua del testo. */
    const val KEY_FROM = "from"
    /** String: lingua in cui tradurre. */
    const val KEY_TO = "to"
    /** String: la traduzione. */
    const val KEY_RESULT = "result"
    /** ArrayList<String>: lingue pronte sul telefono, inglese compreso quando c'è almeno una lingua. */
    const val KEY_LANGUAGES = "languages"
    /** In caso di problema: [ERROR_NO_MODEL] (manca una lingua: va scaricata in Leggi) o [ERROR_FAILED]. */
    const val KEY_ERROR = "error"
    const val ERROR_NO_MODEL = "no_model"
    const val ERROR_FAILED = "failed"

    /** Testo per richiesta: la risposta passa dal Binder, che ha un limite di dimensione. */
    const val MAX_CHARS = 20_000

    /** Attività di Leggi dove si scaricano le lingue (da aprire quando arriva [ERROR_NO_MODEL]). */
    const val ACTION_LANGUAGES = "eu.eurise.action.TRANSLATE_LANGUAGES"
}

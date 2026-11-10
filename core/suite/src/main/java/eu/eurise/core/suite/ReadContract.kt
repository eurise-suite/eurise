package eu.eurise.core.suite

/**
 * Accordo tra l'app Eurise (Scanner, File, Viaggi) ed Eurise Leggi per leggere il testo delle pagine. L'app si collega al
 * servizio di Leggi (solo app con la stessa firma: permesso della suite) e manda [MSG_READ] con gli indirizzi delle
 * immagini ([KEY_PAGES], letti con un permesso temporaneo); Leggi le legge sul telefono e risponde con [MSG_RESULT]:
 * testo, parole con i riquadri (TSV di Tesseract) e misure dell'immagine letta, pagina per pagina. Leggi non salva nulla.
 */
object ReadContract {
    const val ACTION_READ = "eu.eurise.action.READ_TEXT"
    const val MSG_READ = 1
    const val MSG_RESULT = 2

    /** ArrayList<Uri> delle pagine (JPEG o PNG). */
    const val KEY_PAGES = "pages"
    /** ArrayList<String>: testo di ogni pagina. */
    const val KEY_TEXTS = "texts"
    /** ArrayList<String>: parole di ogni pagina, TSV di Tesseract. */
    const val KEY_WORDS = "words"
    /** IntArray: larghezza e altezza dell'immagine letta, due numeri per pagina (i riquadri sono in questi pixel). */
    const val KEY_SIZES = "sizes"
    /** In caso di problema: [ERROR_NO_MODEL] o [ERROR_FAILED]. */
    const val KEY_ERROR = "error"
    const val ERROR_NO_MODEL = "no_model"
    const val ERROR_FAILED = "failed"

    /** Pagine per richiesta: le risposte passano dal Binder, che ha un limite di dimensione. */
    const val MAX_PAGES = 8
}

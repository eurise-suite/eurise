package eu.eurise.core.suite

/**
 * «La tua giornata» per Eurise Postazione (la nuova schermata iniziale, tela «Eurise · nuova interfaccia»): i prossimi
 * impegni e le cose da fare di oggi, letti dall'app Eurise e passati solo alle app della suite (permesso della suite,
 * controllato dal fornitore). Niente rete: i dati restano sul telefono.
 */
object PostazioneContract {
    fun authority(hubPackage: String) = "$hubPackage.postazione"

    /** Impegni da poco prima di adesso fino a [HOURS] ore dopo, e attività aperte con scadenza fino a oggi. */
    const val METHOD_TODAY = "today"

    /** Array di String: titolo dell'impegno. */
    const val KEY_EV_TITLE = "evTitle"
    /** Array di String: luogo (vuoto se non c'è). */
    const val KEY_EV_PLACE = "evPlace"
    /** LongArray: inizio, millisecondi. */
    const val KEY_EV_BEGIN = "evBegin"
    /** LongArray: fine, millisecondi. */
    const val KEY_EV_END = "evEnd"
    /** BooleanArray: tutto il giorno. */
    const val KEY_EV_ALLDAY = "evAllDay"

    /** LongArray: id dell'attività. */
    const val KEY_TASK_ID = "taskId"
    /** Array di String: titolo dell'attività. */
    const val KEY_TASK_TITLE = "taskTitle"
    /** LongArray: scadenza (con l'ora se c'è), millisecondi; 0 se non c'è. */
    const val KEY_TASK_DUE = "taskDue"
    /** BooleanArray: scaduta (prima di oggi). */
    const val KEY_TASK_LATE = "taskLate"
    /** Int: attività aperte fino a oggi (anche oltre quelle mandate). */
    const val KEY_TASK_OPEN = "taskOpen"
    /** Int: attività chiuse oggi. */
    const val KEY_TASK_DONE = "taskDone"

    /** Boolean: c'è un meteo recente già sul telefono (nessuna richiesta in rete per questa chiamata). */
    const val KEY_W_OK = "wOk"
    /** Int: gradi adesso. */
    const val KEY_W_TEMP = "wTemp"
    /** String: il cielo adesso (nome di `Sky`: Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Showers, Snow, Storm). */
    const val KEY_W_SKY = "wSky"
    /** Long: la prima ora delle prossime 24 con pioggia probabile, millisecondi; 0 se non piove. */
    const val KEY_W_RAIN = "wRain"

    /**
     * «Chiudi la giornata»: le attività aperte con scadenza fino a oggi passano a domani. Risposta [KEY_MOVED].
     * Scrive nei dati dell'app Eurise: solo app della suite, come tutto il resto.
     */
    const val METHOD_POSTPONE = "postpone"
    /** Int: quante attività sono passate a domani. */
    const val KEY_MOVED = "moved"

    /**
     * «Mettile lì»: le attività [EXTRA_IDS] (al più [MAX_TASKS]) prendono un orario, una ogni 25 minuti da [EXTRA_START]
     * (entro un giorno e mezzo da adesso). Risposta [KEY_MOVED].
     */
    const val METHOD_SCHEDULE = "schedule"
    /** LongArray: id delle attività, nell'ordine. */
    const val EXTRA_IDS = "ids"
    /** Long: inizio dello spazio libero, millisecondi. */
    const val EXTRA_START = "start"

    /**
     * Le ultime notizie dalle fonti scelte nell'app Eurise. Le legge l'app Eurise con le sue regole (servizio «Notizie»
     * del centro privacy: spento, non torna nulla); la Postazione non va in rete. Array paralleli, al più [MAX_NEWS].
     */
    const val METHOD_NEWS = "news"
    const val KEY_N_TITLE = "nTitle"
    const val KEY_N_SOURCE = "nSource"
    const val KEY_N_LINK = "nLink"
    /** LongArray: ora di pubblicazione, millisecondi. */
    const val KEY_N_WHEN = "nWhen"
    const val MAX_NEWS = 4

    const val MAX_EVENTS = 5
    const val MAX_TASKS = 4
    const val HOURS = 36
}

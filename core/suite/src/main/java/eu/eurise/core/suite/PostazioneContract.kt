package eu.eurise.core.suite

/**
 * «La tua giornata» per Eurise Postazione (la nuova schermata iniziale, tela «Eurise · nuova interfaccia»): i prossimi
 * impegni e le cose da fare di oggi, letti dall'app Eurise e passati solo alle app della suite (permesso della suite,
 * controllato dal fornitore). Niente rete: i dati restano sul telefono.
 */
object PostazioneContract {
    fun authority(hubPackage: String) = "$hubPackage.postazione"

    /** Impegni da poco prima di adesso fino a [HOURS] ore dopo (o, se non ce ne sono, quelli del primo giorno che ne ha), e attività aperte con scadenza fino a oggi. */
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
    /** LongArray: id dell'evento (per aprirlo nel Calendario o eliminarlo). */
    const val KEY_EV_ID = "evId"
    /** Boolean: l'app Eurise ha il permesso del calendario (senza, gli impegni non arrivano: la Postazione lo dice). */
    const val KEY_EV_PERM = "evPerm"
    /** BooleanArray: si può eliminare dalla Postazione (calendario scrivibile, evento non ricorrente). */
    const val KEY_EV_EDIT = "evEdit"

    /** LongArray: id dell'attività. */
    const val KEY_TASK_ID = "taskId"
    /** Array di String: titolo dell'attività. */
    const val KEY_TASK_TITLE = "taskTitle"
    /** LongArray: scadenza (con l'ora se c'è), millisecondi; 0 se non c'è. */
    const val KEY_TASK_DUE = "taskDue"
    /** BooleanArray: scaduta (prima di oggi). */
    const val KEY_TASK_LATE = "taskLate"
    /** BooleanArray: è la dose di un farmaco (elenco «Farmaci» dei promemoria). */
    const val KEY_TASK_MED = "taskMed"
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

    /**
     * Un'azione su una cosa da fare ([EXTRA_ID]): arg [ACT_DONE] (fatta, o dose presa), [ACT_TOMORROW] (a domani, con
     * l'orario che aveva) o [ACT_DELETE]. Risposta [KEY_OK]. Scrive nei dati dell'app Eurise: solo app della suite.
     */
    const val METHOD_TASK = "task"
    /**
     * Una cosa da fare nuova per oggi, dal titolo in arg (da 1 a [MAX_TITLE] caratteri): «Richiamare Giulia», «Rispondere
     * a Studio Rossi» dallo swipe di «Ti hanno cercato» e «Da rispondere». Risposta [KEY_OK]. Solo app della suite.
     */
    const val METHOD_ADD_TASK = "addTask"
    const val MAX_TITLE = 120
    /** Un'azione su un impegno ([EXTRA_ID]): arg [ACT_DELETE], solo se [KEY_EV_EDIT] era vero. Risposta [KEY_OK]. */
    const val METHOD_EVENT = "event"
    const val ACT_DONE = "done"
    const val ACT_TOMORROW = "tomorrow"
    const val ACT_DELETE = "delete"
    /** Long: id della cosa da fare o dell'impegno. */
    const val EXTRA_ID = "id"
    /** Boolean: l'azione è riuscita. */
    const val KEY_OK = "ok"

    /**
     * La ricerca della lente: arg il testo (da 2 a [MAX_QUERY] caratteri). Impegni, cose da fare, note e documenti
     * dell'app Eurise, al più [MAX_FOUND] per tipo, in array paralleli. I documenti ci sono solo se l'app Eurise ha già
     * l'accesso ai file; dei documenti passa il nome, mai il contenuto né l'indirizzo.
     */
    const val METHOD_SEARCH = "search"
    /** Array di String: [KIND_EVENT], [KIND_TASK], [KIND_NOTE] o [KIND_FILE]. */
    const val KEY_S_KIND = "sKind"
    const val KEY_S_ID = "sId"
    const val KEY_S_TITLE = "sTitle"
    /** Array di String: una riga sotto il titolo (cartella del documento, nome del calendario…). */
    const val KEY_S_DETAIL = "sDetail"
    /** LongArray: inizio dell'impegno o scadenza, millisecondi; 0 se non c'è. */
    const val KEY_S_WHEN = "sWhen"
    const val MAX_QUERY = 80
    const val MAX_FOUND = 4

    /**
     * L'oroscopo di oggi, letto dall'app Eurise con il servizio «Oroscopo» del centro privacy (spento: [KEY_H_ON] falso e
     * nient'altro). [EXTRA_SIGN] (facoltativo) cambia il segno, lo stesso della Scrivania.
     */
    const val METHOD_HOROSCOPE = "horoscope"
    const val EXTRA_SIGN = "sign"
    const val KEY_H_ON = "hOn"
    const val KEY_H_SIGN = "hSign"
    /** Array di String: i nomi dei segni, nell'ordine dei numeri di [EXTRA_SIGN]. */
    const val KEY_H_SIGNS = "hSigns"
    const val KEY_H_TEXT = "hText"
    const val KEY_H_SOURCE = "hSource"
    const val KEY_H_LINK = "hLink"

    /**
     * Aprire nell'app Eurise una cosa trovata o mostrata dalla Postazione: attività `OpenFoundActivity`, protetta dal
     * permesso della suite. [EXTRA_KIND] dice cosa, [EXTRA_ID] quale ([EXTRA_BEGIN] per l'occorrenza di un impegno).
     * Un documento si riapre dall'id che l'app Eurise ha dato nella ricerca, ricontrollato con le stesse regole.
     */
    const val ACTION_OPEN = "eu.eurise.action.OPEN_FOUND"
    const val OPEN_ACTIVITY = "eu.eurise.hub.OpenFoundActivity"
    const val EXTRA_KIND = "kind"
    const val EXTRA_BEGIN = "begin"
    const val KIND_EVENT = "event"
    const val KIND_TASK = "task"
    const val KIND_NOTE = "note"
    const val KIND_FILE = "file"
    const val KIND_MEDICINES = "medicines"

    const val MAX_EVENTS = 5
    const val MAX_TASKS = 8
    const val HOURS = 36
    /** Se nelle prossime [HOURS] ore non c'è niente, si cerca il primo giorno con impegni fino a tanti giorni avanti. */
    const val AVANTI_GIORNI = 30
}

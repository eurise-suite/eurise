package eu.eurise.core.suite

/**
 * «Per oggi, dalla suite» per la nuova scheda del Browser (tavola BrwNuovaSchedaPro): riunioni e impegni delle prossime
 * ore e cose da fare dei prossimi giorni che contengono un link, letti dall'app Eurise e passati solo alle app della suite
 * (permesso della suite, controllato dal fornitore). Niente rete.
 */
object TodayLinksContract {
    fun authority(hubPackage: String) = "$hubPackage.todaylinks"

    const val METHOD_LIST = "list"
    /** Array di String: titolo. */
    const val KEY_TITLE = "title"
    /** Array di String: il link trovato. */
    const val KEY_URL = "url"
    /** Array di String: [KIND_MEETING], [KIND_EVENT] o [KIND_TASK]. */
    const val KEY_KIND = "kind"
    /** LongArray: inizio dell'impegno o scadenza dell'attività (mezzanotte), millisecondi; 0 se non c'è. */
    const val KEY_WHEN = "when"

    const val KIND_MEETING = "meeting"
    const val KIND_EVENT = "event"
    const val KIND_TASK = "task"

    const val MAX = 4
    /** Impegni da adesso fino a quante ore dopo. */
    const val HOURS = 18
    /** Attività con scadenza entro quanti giorni (anche quelle scadute). */
    const val TASK_DAYS = 3

    private val URL = Regex("""https?://[^\s<>"')\]]+""")
    private val MEETING = listOf("meet.jit.si", "jitsi", "zoom.us", "teams.microsoft", "meet.google", "whereby", "webex", "bigbluebutton", "talk.", "/call/")

    /** Il primo link nel testo (senza la punteggiatura finale). */
    fun firstUrl(vararg texts: String): String? =
        texts.firstNotNullOfOrNull { t -> URL.find(t)?.value?.trimEnd('.', ',', ';', ':') }

    fun isMeeting(url: String) = MEETING.any { url.contains(it, ignoreCase = true) }
}

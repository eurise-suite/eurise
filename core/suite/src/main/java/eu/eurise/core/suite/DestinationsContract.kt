package eu.eurise.core.suite

/**
 * «Dove devi andare» per Eurise Mappe (tavola MapCerca): gli impegni dei prossimi giorni che hanno un luogo, letti
 * dall'app Eurise (che ha il permesso del Calendario) e passati solo alle app della suite (permesso della suite,
 * controllato dal fornitore). Niente rete: Mappe cerca il luogo sulle mappe scaricate. Mappe è in Java: solo nomi.
 */
object DestinationsContract {
    fun authority(hubPackage: String) = "$hubPackage.destinations"

    const val METHOD_LIST = "list"
    /** Array di String: titolo dell'impegno. */
    const val KEY_TITLE = "title"
    /** Array di String: il luogo come è scritto nell'evento. */
    const val KEY_PLACE = "place"
    /** LongArray: inizio, millisecondi. */
    const val KEY_START = "start"
    /** Falso se l'app Eurise non può leggere il Calendario. */
    const val KEY_CALENDAR = "calendar"

    const val MAX = 8
    const val DAYS = 7
}

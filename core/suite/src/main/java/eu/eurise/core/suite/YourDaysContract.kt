package eu.eurise.core.suite

/**
 * «I tuoi giorni» per il paesaggio di Eurise Mappe (tavola MapCieli): se oggi è il compleanno o l'anniversario
 * dell'utente (Date importanti, quelle segnate come «mie»). Si passa solo il tipo di giorno: nessun nome, nessuna data,
 * niente rete. Solo app con la stessa firma (permesso della suite, controllato dal fornitore). Mappe è in Java: solo nomi.
 */
object YourDaysContract {
    fun authority(hubPackage: String) = "$hubPackage.yourdays"

    /** Il giorno di oggi (data del telefono): risposta [KEY_KIND]. */
    const val METHOD_TODAY = "today"

    /** Int: [NONE], [BIRTHDAY] o [ANNIVERSARY]. Il compleanno ha la precedenza, come sullo sfondo della Scrivania. */
    const val KEY_KIND = "kind"

    const val NONE = 0
    const val BIRTHDAY = 1
    const val ANNIVERSARY = 2
}

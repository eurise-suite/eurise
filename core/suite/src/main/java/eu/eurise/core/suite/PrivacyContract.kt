package eu.eurise.core.suite

/**
 * I servizi esterni che un'altra app della suite contatta da sé (per ora Eurise Mappe: ricerca, note su OpenStreetMap, prezzi dei carburanti)
 * passano comunque dal centro privacy dell'app Eurise: lì si vedono, si accendono e si spengono, e ogni contatto finisce
 * nel registro. Solo app con la stessa firma (permesso della suite, controllato dal fornitore). Mappe è in Java: qui
 * solo nomi.
 */
object PrivacyContract {
    fun authority(hubPackage: String) = "$hubPackage.privacy"

    /** Lo stato del servizio [arg] (nome di [PrivacyService]): risposta [KEY_ON]. */
    const val METHOD_STATUS = "status"
    /** Acceso dalla persona dopo la spiegazione nell'app che lo usa. */
    const val METHOD_ENABLE = "enable"
    /** Spento, senza conferme. */
    const val METHOD_DISABLE = "disable"
    /** Un contatto appena fatto: va nel registro del centro privacy. */
    const val METHOD_LOG = "log"

    const val KEY_ON = "on"

    /** I servizi che le altre app possono chiedere qui: nessun altro interruttore si tocca da fuori. */
    val SHARED = setOf(PrivacyService.MapSearch, PrivacyService.MapReports, PrivacyService.FuelPrices)
}

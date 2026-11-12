package eu.eurise.core.suite

/**
 * Meteo sul percorso per Eurise Mappe (tavola MapGuida). Mappe non contatta servizi da sola: chiede all'app Eurise, che
 * passa dal centro privacy (servizio [PrivacyService.RouteWeather], spento finché la persona non lo accende) e da
 * Open-Meteo con i punti arrotondati a circa 1 km. Solo app con la stessa firma (permesso della suite, controllato dal
 * fornitore). Mappe è in Java: qui solo nomi e numeri.
 */
object RouteWeatherContract {
    fun authority(hubPackage: String) = "$hubPackage.routeweather"

    /** Lo stato del servizio: [KEY_ON]. */
    const val METHOD_STATUS = "status"
    /** Acceso dalla persona dopo la spiegazione in Mappe. */
    const val METHOD_ENABLE = "enable"
    /** Spento, senza conferme. */
    const val METHOD_DISABLE = "disable"
    /** Il tempo nei punti [KEY_LAT], [KEY_LON] alle ore [KEY_TIME]; risposta con le chiavi qui sotto, o [KEY_ON] falso. */
    const val METHOD_ROUTE = "route"

    const val KEY_ON = "on"
    /** DoubleArray. */
    const val KEY_LAT = "lat"
    const val KEY_LON = "lon"
    /** LongArray: secondi Unix in cui si passerà dal punto. */
    const val KEY_TIME = "time"
    /** IntArray: codice WMO del tempo (-1 se non c'è), un valore per punto. */
    const val KEY_CODE = "code"
    /** FloatArray: gradi. */
    const val KEY_TEMP = "temp"
    /** IntArray: probabilità di pioggia in %. */
    const val KEY_RAIN = "rain"
    /** FloatArray: raffiche in km/h. */
    const val KEY_GUSTS = "gusts"

    /** Punti per richiesta: abbastanza per un viaggio lungo, pochi per non dire troppo del percorso. */
    const val MAX_POINTS = 8
}

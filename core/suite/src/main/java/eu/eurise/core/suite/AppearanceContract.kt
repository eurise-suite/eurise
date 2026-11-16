package eu.eurise.core.suite

/**
 * L'aspetto della suite per le altre app: il colore d'accento scelto sulla Scrivania e le tinte tra cui scegliere
 * (le otto della suite e quelle in più della Scrivania). Lo serve l'app Eurise con un ContentProvider sull'autorità
 * [authority] (metodo `call`); solo app con la stessa firma (permesso della suite, controllato dal fornitore).
 * Office lo usa per l'accento delle sue icone (14 novembre). Finché l'app Eurise non lo offre, chi lo chiede usa le
 * sue scelte di riserva.
 */
object AppearanceContract {
    fun authority(hubPackage: String) = "$hubPackage.appearance"

    /** L'accento della Scrivania: risposta [KEY_LIGHT] e [KEY_DARK] (ARGB) e [KEY_NAME] (come lo salva la Scrivania). */
    const val METHOD_ACCENT = "accent"
    /** Le tinte da offrire: [KEY_NAMES] (nomi salvati), [KEY_LABELS], [KEY_LIGHTS] e [KEY_DARKS] (ARGB), stesso ordine. */
    const val METHOD_PALETTE = "palette"

    const val KEY_LIGHT = "light"
    const val KEY_DARK = "dark"
    const val KEY_NAME = "name"
    const val KEY_NAMES = "names"
    const val KEY_LABELS = "labels"
    const val KEY_LIGHTS = "lights"
    const val KEY_DARKS = "darks"
}

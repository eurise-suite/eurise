package eu.eurise.office.home

import android.content.Context

/**
 * Preferenze di Office, sul telefono. La mano dominante oggi si sceglie qui; quando l'app Eurise la offrirà a tutta la
 * suite (regola sull'ergonomia, 13 novembre) Office la leggerà da lì e questa resterà solo come riserva.
 */
object OfficePrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("office", Context.MODE_PRIVATE)

    fun leftHanded(context: Context): Boolean = prefs(context).getBoolean("mancino", false)
    fun setLeftHanded(context: Context, value: Boolean) = prefs(context).edit().putBoolean("mancino", value).apply()

    /** «Apri sempre i documenti nella modifica completa» (tavola OffLetturaCompleta): salta la vista rapida. */
    fun alwaysFull(context: Context): Boolean = prefs(context).getBoolean("sempre_completa", false)
    fun setAlwaysFull(context: Context, value: Boolean) = prefs(context).edit().putBoolean("sempre_completa", value).apply()

    /** Il consiglio «Tocca un paragrafo…» si vede finché non si tocca il primo. */
    fun quickTipSeen(context: Context): Boolean = prefs(context).getBoolean("consiglio_visto", false)
    fun setQuickTipSeen(context: Context) = prefs(context).edit().putBoolean("consiglio_visto", true).apply()

    /** Il nome con cui si firmano i commenti. */
    fun author(context: Context): String = prefs(context).getString("autore", "").orEmpty()
    fun setAuthor(context: Context, value: String) = prefs(context).edit().putString("autore", value).apply()
}

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
}

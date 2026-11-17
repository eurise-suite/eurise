package eu.eurise.chat

import android.content.Context

/**
 * Le stanze di La mia Bussola su questo telefono, per le notifiche: lì la notifica dice solo la frase fissa, mai il
 * contenuto (specifiche, «Le notifiche non contengono mai dati sanitari»). Solo identificativi di stanza, nelle
 * preferenze private dell'app.
 */
object BussolaRooms {
    private const val PREFS = "bussola"
    private const val KEY = "rooms"

    fun all(context: Context): Set<String> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()

    fun add(context: Context, roomId: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = p.getStringSet(KEY, emptySet()).orEmpty()
        if (roomId !in now) p.edit().putStringSet(KEY, now + roomId).apply()
    }
}

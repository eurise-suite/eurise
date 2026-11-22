package eu.eurise.phone.data

import android.content.Context
import eu.eurise.core.insieme.Ruoli

/**
 * Persone e ruoli (tavola InsRuoli) nella Rubrica: sul telefono, nelle preferenze dell'app. Per ogni persona la chiave del
 * contatto, il ruolo, cosa vede di base se l'hai cambiato e i suoi indirizzi (numeri, email, utente della Chat) per
 * riconoscerla nelle altre app.
 */
object Roles {
    private fun prefs(context: Context) = context.getSharedPreferences("ruoli", Context.MODE_PRIVATE)

    @Synchronized fun load(context: Context): List<Ruoli.Person> = Ruoli.decode(prefs(context).getString("list", null))

    @Synchronized fun set(context: Context, key: String, name: String, role: Ruoli.Role?, level: Ruoli.Level?, addresses: List<String>) {
        // Se la persona c'era con un'altra chiave (prima aggiunta dalla Chat o dall'Email), la si unisce.
        val list = load(context)
        val same = addresses.mapNotNull { a -> Ruoli.find(list, a)?.key }.toSet() - key
        val old = list.filter { it.key in same }.flatMap { it.addresses }
        val next = Ruoli.set(list.filterNot { it.key in same }, key, name, role, level, addresses + old)
        prefs(context).edit().putString("list", Ruoli.encode(next)).apply()
    }
}

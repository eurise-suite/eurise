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

    /** Accento delle icone: vuoto = come la Scrivania, altrimenti il nome della tinta («suite:Ottone», «extra:Cuoio»…). */
    fun accent(context: Context): String = prefs(context).getString("accento", "").orEmpty()
    fun setAccent(context: Context, value: String) = prefs(context).edit().putString("accento", value).apply()

    /** Il cloud preferito della dock (id dell'account in Eurise File): tocco breve su «Cloud» apre lì. */
    fun favoriteCloud(context: Context): String = prefs(context).getString("cloud_preferito", "").orEmpty()
    fun setFavoriteCloud(context: Context, id: String) = prefs(context).edit().putString("cloud_preferito", id).apply()

    /** La cartella di riferimento di un cloud: quella dell'ultimo documento aperto da lì (chiave di Eurise File). */
    fun cloudFolder(context: Context, id: String): String? = prefs(context).getString("cartella_$id", null)
    fun setCloudFolder(context: Context, id: String, key: String) = prefs(context).edit().putString("cartella_$id", key).apply()

    /** L'ultimo documento passato al motore: nome, tipo e ora (per il rapporto del problema, OffRapporto). */
    fun setLastEngineOpen(context: Context, name: String, kind: String) = prefs(context).edit()
        .putString("motore_nome", name).putString("motore_tipo", kind).putLong("motore_ora", System.currentTimeMillis()).apply()
    fun lastEngineOpen(context: Context): Triple<String, String, Long> = prefs(context).let {
        Triple(it.getString("motore_nome", "").orEmpty(), it.getString("motore_tipo", "").orEmpty(), it.getLong("motore_ora", 0L))
    }

    /** Il nome con cui si firmano i commenti. */
    fun author(context: Context): String = prefs(context).getString("autore", "").orEmpty()
    fun setAuthor(context: Context, value: String) = prefs(context).edit().putString("autore", value).apply()
}

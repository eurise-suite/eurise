package eu.eurise.postazione.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.AppHubContract as H
import eu.eurise.core.suite.SuiteAccess
import eu.eurise.postazione.R

/**
 * La Postazione nel centro privacy dell'app Eurise ([H]): i tre interruttori ([Uso]) con le 10 tacchette (tutto sul
 * telefono, la Postazione non ha la rete). Risponde **solo all'app Eurise** ([SuiteAccess.callerIsHub]); spegnere da lì
 * vale subito, accendere apre la Postazione sulle sue impostazioni, dove c'è la spiegazione.
 */
class CentroPrivacy : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (!SuiteAccess.callerIsHub(context)) throw SecurityException("Eurise Postazione: solo l'app Eurise")
        return when (method) {
            H.METHOD_PRIVACY -> H.servicesBundle(servizi(context), "")
            H.METHOD_DISABLE -> {
                Uso.entries.firstOrNull { it.name == arg }?.spegni(context)
                Bundle()
            }
            else -> null
        }
    }

    private fun servizi(context: Context): List<H.Service> = listOf(
        H.Service(Uso.Notifiche.name, context.getString(R.string.cp_notifiche), context.getString(R.string.cp_notifiche_d), 10, "Phone", Ascolto.acceso(context)),
        H.Service(Uso.Contatti.name, context.getString(R.string.cp_contatti), context.getString(R.string.cp_contatti_d), 10, "Phone", Preferiti.permesso(context)),
        H.Service(Uso.Posizione.name, context.getString(R.string.cp_posizione), context.getString(R.string.cp_posizione_d), 10, "Phone", Posto.permesso(context)),
    )

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

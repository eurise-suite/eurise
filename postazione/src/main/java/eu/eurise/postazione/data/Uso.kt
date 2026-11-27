package eu.eurise.postazione.data

import android.Manifest
import android.content.Context
import android.os.Build

/**
 * I tre interruttori della Postazione, visibili anche nel centro privacy dell'app Eurise: accesso alle notifiche, contatti,
 * posizione. Ognuno vale solo se Android ha dato il permesso **e** l'interruttore è acceso. Spegnere (da qui o dal centro
 * privacy) ferma subito l'uso, senza conferme; il permesso si restituisce ad Android alla chiusura dell'app (Android 13 e
 * oltre) e l'accesso alle notifiche si stacca. Riaccendere passa sempre dalla spiegazione nella Postazione.
 */
enum class Uso(val chiave: String) {
    Notifiche("uso.notifiche"), Contatti("uso.contatti"), Posizione("uso.posizione"),

    /** Chiamate da richiamare (Eurise Telefono) ed email da leggere (Eurise Email): restano nella suite, sul telefono. */
    Suite("uso.suite");

    fun acceso(context: Context): Boolean = prefs(context).getBoolean(chiave, true)

    fun accendi(context: Context) { prefs(context).edit().putBoolean(chiave, true).apply() }

    fun spegni(context: Context) {
        prefs(context).edit().putBoolean(chiave, false).apply()
        when (this) {
            Notifiche -> {
                AscoltoService.avvisi.value = emptyList()
                runCatching { AscoltoService.istanza?.requestUnbind() }
            }
            Contatti -> restituisci(context, Manifest.permission.READ_CONTACTS)
            Posizione -> restituisci(context, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            Suite -> Unit
        }
    }

    private fun restituisci(context: Context, vararg permessi: String) {
        if (Build.VERSION.SDK_INT >= 33) runCatching { context.revokeSelfPermissionsOnKill(permessi.toList()) }
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("postazione", Context.MODE_PRIVATE)
    }
}

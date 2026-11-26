package eu.eurise.postazione.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal

/** Dove hai lasciato l'auto: latitudine, longitudine e quando l'hai segnato. Resta sul telefono. */
data class Auto(val lat: Double, val lon: Double, val quando: Long)

/**
 * «Dove hai lasciato l'auto», a mano: un tocco segna il punto in cui sei. Serve il permesso di posizione, chiesto solo
 * accendendo la funzione dopo la spiegazione; la posizione si legge una volta sola, in quel momento, e resta nelle
 * preferenze private della Postazione (che non ha la rete).
 */
object Posto {
    fun permesso(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** La posizione di adesso (o l'ultima nota se il satellite non risponde); [fatto] arriva sul thread principale. */
    @SuppressLint("MissingPermission")
    fun adesso(context: Context, fatto: (Location?) -> Unit) {
        val gestore = context.getSystemService(LocationManager::class.java)
        if (gestore == null || !permesso(context)) { fatto(null); return }
        val fornitori = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val fornitore = fornitori.firstOrNull { f -> runCatching { gestore.isProviderEnabled(f) }.getOrDefault(false) }
        if (fornitore == null) { fatto(null); return }
        val ultima = { fornitori.firstNotNullOfOrNull { f -> runCatching { gestore.getLastKnownLocation(f) }.getOrNull() } }
        runCatching {
            gestore.getCurrentLocation(fornitore, CancellationSignal(), context.mainExecutor) { posizione: Location? -> fatto(posizione ?: ultima()) }
        }.onFailure { fatto(ultima()) }
    }
}

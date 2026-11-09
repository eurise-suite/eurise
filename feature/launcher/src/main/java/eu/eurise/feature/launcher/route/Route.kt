package eu.eurise.feature.launcher.route

import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyService
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/** Tempo per arrivare a un indirizzo in auto, in minuti. */
data class Travel(val destination: String, val minutes: Int)

/**
 * Tempi di viaggio con i servizi aperti di OpenStreetMap: Nominatim trova l'indirizzo, OSRM calcola il
 * percorso. Si inviano solo la posizione approssimata (due decimali) e l'indirizzo di arrivo; il risultato
 * resta in memoria mezz'ora per non interrogare i servizi più del necessario.
 */
class RouteRepository(private val context: Context) {
    private val cache = HashMap<String, Pair<Long, Travel?>>()

    suspend fun travelTo(address: String): Travel? = withContext(Dispatchers.IO) {
        if (!PrivacyCenter.allowed(PrivacyService.Routes)) return@withContext null
        val key = address.trim().lowercase()
        cache[key]?.let { (at, value) -> if (System.currentTimeMillis() - at < 30 * 60_000) return@withContext value }
        val result = runCatching {
            val from = location() ?: return@runCatching null
            PrivacyCenter.log(PrivacyService.Routes)
            val to = geocode(address) ?: return@runCatching null
            val url = String.format(
                Locale.ROOT,
                "https://router.project-osrm.org/route/v1/driving/%.4f,%.4f;%.5f,%.5f?overview=false",
                from.second, from.first, to.second, to.first,
            )
            val seconds = JSONObject(get(url)).getJSONArray("routes").getJSONObject(0).getDouble("duration")
            Travel(address, (seconds / 60).roundToInt().coerceAtLeast(1))
        }.getOrNull()
        cache[key] = System.currentTimeMillis() to result
        result
    }

    private fun geocode(address: String): Pair<Double, Double>? {
        val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(address, "UTF-8")
        val list = JSONArray(get(url))
        if (list.length() == 0) return null
        val first = list.getJSONObject(0)
        return first.getString("lat").toDouble() to first.getString("lon").toDouble()
    }

    private fun location(): Pair<Double, Double>? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        return loc.latitude to loc.longitude
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            // Nominatim chiede un nome dell'applicazione; nessun dato personale.
            setRequestProperty("User-Agent", "Eurise/1.0 (launcher open source)")
        }
        return try { connection.inputStream.bufferedReader().readText() } finally { connection.disconnect() }
    }
}

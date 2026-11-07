package eu.eurise.feature.launcher.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

enum class Sky { Clear, Cloudy, Fog, Rain, Snow, Storm }

data class DayForecast(val date: LocalDate, val sky: Sky, val max: Int, val min: Int)

/**
 * Meteo da Open-Meteo: servizio aperto, senza account e senza chiave. Si invia solo la posizione
 * approssimata (due decimali, circa un chilometro); il risultato resta in memoria per un'ora.
 */
class WeatherRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("eurise_weather", Context.MODE_PRIVATE)

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Oggi e i sei giorni seguenti; null se manca la posizione o la rete. */
    suspend fun forecast(): List<DayForecast>? = withContext(Dispatchers.IO) {
        cached()?.let { return@withContext it }
        val (lat, lon) = location() ?: return@withContext null
        val url = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=7",
            lat, lon,
        )
        val body = runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 8_000
                readTimeout = 8_000
                try { inputStream.bufferedReader().readText() } finally { disconnect() }
            }
        }.getOrNull() ?: return@withContext null
        prefs.edit().putString("json", body).putLong("at", System.currentTimeMillis()).apply()
        parse(body)
    }

    private fun cached(): List<DayForecast>? {
        val at = prefs.getLong("at", 0)
        if (System.currentTimeMillis() - at > 60 * 60 * 1000) return null
        return prefs.getString("json", null)?.let { parse(it) }
    }

    /** Ultima posizione approssimata nota (per alba e tramonto), senza chiederne una nuova. */
    fun lastLocation(): Pair<Double, Double>? = location()

    private fun location(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        return loc.latitude to loc.longitude
    }

    private fun parse(json: String): List<DayForecast>? = runCatching {
        val daily = JSONObject(json).getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val max = daily.getJSONArray("temperature_2m_max")
        val min = daily.getJSONArray("temperature_2m_min")
        (0 until dates.length()).map { i ->
            DayForecast(LocalDate.parse(dates.getString(i)), skyOf(codes.getInt(i)), max.getDouble(i).roundToInt(), min.getDouble(i).roundToInt())
        }
    }.getOrNull()

    /** Codici WMO usati da Open-Meteo. */
    private fun skyOf(code: Int) = when (code) {
        0, 1 -> Sky.Clear
        2, 3 -> Sky.Cloudy
        45, 48 -> Sky.Fog
        in 51..67, in 80..82 -> Sky.Rain
        in 71..77, 85, 86 -> Sky.Snow
        in 95..99 -> Sky.Storm
        else -> Sky.Cloudy
    }
}

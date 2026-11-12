package eu.eurise.core.data.weather

import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyService
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/** Il tempo in un punto del percorso all'ora in cui ci si passa. [code] -1 se Open-Meteo non l'ha. */
data class RoutePointWeather(val code: Int, val temp: Float, val rain: Int, val gusts: Float)

/**
 * Meteo sul percorso per Eurise Mappe: una sola richiesta a Open-Meteo con tutti i punti (arrotondati a due decimali,
 * circa 1 km), passando dal centro privacy ([PrivacyService.RouteWeather]). Per ogni punto l'ora più vicina a quella di
 * passaggio. Da chiamare fuori dal thread principale.
 */
object RouteWeather {
    fun fetch(lat: DoubleArray, lon: DoubleArray, time: LongArray): List<RoutePointWeather>? {
        if (lat.isEmpty() || lat.size != lon.size || lat.size != time.size) return null
        if (!PrivacyCenter.allowed(PrivacyService.RouteWeather)) return null
        PrivacyCenter.log(PrivacyService.RouteWeather)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat.joinToString(",") { "%.2f".format(Locale.ROOT, it) } +
            "&longitude=" + lon.joinToString(",") { "%.2f".format(Locale.ROOT, it) } +
            "&hourly=weather_code,temperature_2m,precipitation_probability,wind_gusts_10m&forecast_days=3&timeformat=unixtime&timezone=GMT"
        val body = runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Eurise")
                try { if (responseCode == 200) inputStream.bufferedReader().readText() else null } finally { disconnect() }
            }
        }.getOrNull() ?: return null
        return runCatching { parse(body, time) }.getOrNull()
    }

    /** Dalla risposta di Open-Meteo (un oggetto per un punto, un elenco per più punti) al tempo di ogni punto. */
    fun parse(body: String, time: LongArray): List<RoutePointWeather> {
        val t = body.trim()
        val items = if (t.startsWith("[")) JSONArray(t).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } else listOf(JSONObject(t))
        return items.mapIndexed { i, o ->
            val h = o.getJSONObject("hourly")
            val times = h.getJSONArray("time")
            val at = time.getOrElse(i) { time.lastOrNull() ?: 0L }
            var best = 0
            for (k in 0 until times.length()) if (abs(times.getLong(k) - at) < abs(times.getLong(best) - at)) best = k
            fun num(name: String) = h.optJSONArray(name)?.let { if (it.isNull(best)) null else it.optDouble(best) }
            RoutePointWeather(
                code = num("weather_code")?.toInt() ?: -1,
                temp = num("temperature_2m")?.toFloat() ?: Float.NaN,
                rain = num("precipitation_probability")?.toInt() ?: 0,
                gusts = num("wind_gusts_10m")?.toFloat() ?: 0f,
            )
        }
    }
}

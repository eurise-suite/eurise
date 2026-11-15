package app.organicmaps.eurise.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.round

/**
 * Mezzi pubblici e viaggi lontani (tavole MapMezzi, MapLontano, MapLontanoScelta): itinerari da Transitous (MOTIS 2,
 * dati aperti GTFS delle aziende di trasporto), servizio comunitario per progetti a codice aperto e non commerciali.
 *
 * Regole della suite: il servizio è nel centro privacy dell'app Eurise («Mezzi pubblici», spento di base) e si contatta
 * solo quando la persona tocca «Mezzi» o apre un viaggio lontano; escono partenza e arrivo arrotondati a circa 200 m e
 * l'ora, con un User-Agent che dice solo nome e versione dell'app (come chiede Transitous), mai identificativi.
 * Regole di Transitous (transitous.org/api, lette il 15 novembre 2026): User-Agent con un contatto, il collegamento alle
 * fonti in vista, al massimo una richiesta per gesto della persona (qui anche una cache di due minuti). Oltre 720 richieste
 * al giorno in tutto (molti utenti) vanno concordate con loro: passo una tantum del proprietario prima della pubblicazione.
 */
object EuriseTransit {
    private const val API = "https://api.transitous.org/api/v5/plan"
    const val SOURCES = "https://transitous.org/sources/"
    private const val SERVICE = "PublicTransport"
    private const val CACHE_MS = 2 * 60_000L

    /** Una tratta: a piedi o su un mezzo. */
    class Leg(
        val mode: String, val line: String, val color: Int?, val headsign: String, val from: String, val to: String,
        val start: Long, val end: Long, val stops: Int, val agency: String, val agencyUrl: String,
        val fromLat: Double, val fromLon: Double,
    ) {
        val walk get() = mode == "WALK" || mode == "BIKE" || mode == "CAR"
    }

    class Itinerary(val start: Long, val end: Long, val transfers: Int, val legs: List<Leg>) {
        val seconds get() = ((end - start) / 1000).toInt()
        val rides get() = legs.filter { !it.walk }
    }

    private var cacheKey = ""
    private var cacheAt = 0L
    private var cache: List<Itinerary> = emptyList()

    /** Lo stato del servizio nel centro privacy dell'app Eurise: true acceso, false spento, null app assente. */
    fun privacy(c: Context, method: String = "status"): Boolean? {
        for (h in HUBS) {
            try {
                val b: Bundle? = c.contentResolver.call(Uri.parse("content://$h.privacy"), method, SERVICE, null)
                if (b != null) return b.getBoolean("on")
            } catch (_: Exception) {
                // App Eurise assente o di un'altra firma.
            }
        }
        return null
    }

    /** Arrotondato a 0,002° (circa 200 m): basta per trovare la fermata, non dice la porta di casa. */
    private fun rounded(v: Double) = String.format(Locale.ROOT, "%.3f", round(v / 0.002) * 0.002)

    /**
     * Gli itinerari da [fromLat],[fromLon] a [toLat],[toLon] in partenza da [time] (o in arrivo entro, con [arriveBy]).
     * Fuori dal thread principale. Null se il servizio è spento o la rete non risponde.
     */
    fun plan(c: Context, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, time: Long, arriveBy: Boolean = false): List<Itinerary>? {
        if (privacy(c) == false) return null
        val from = rounded(fromLat) + "," + rounded(fromLon)
        val to = rounded(toLat) + "," + rounded(toLon)
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(time / 60_000L * 60_000L))
        val key = "$from|$to|$iso|$arriveBy"
        synchronized(this) {
            if (key == cacheKey && System.currentTimeMillis() - cacheAt < CACHE_MS) return cache
        }
        val url = API + "?fromPlace=" + enc(from) + "&toPlace=" + enc(to) + "&time=" + enc(iso) +
            "&arriveBy=" + arriveBy + "&numItineraries=5&detailedTransfers=false"
        val body = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "EuriseMappe/1 (+https://github.com/eurise-suite/eurise)")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return null
        }
        privacy(c, "log")
        val out = runCatching { parse(body) }.getOrNull() ?: return null
        synchronized(this) {
            cacheKey = key
            cacheAt = System.currentTimeMillis()
            cache = out
        }
        return out
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun time(s: String?): Long = s?.takeIf { it.length >= 19 }?.let {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        runCatching { f.parse(it.substring(0, 19))!!.time }.getOrNull()
    } ?: 0L

    private fun color(s: String?): Int? = s?.trim()?.removePrefix("#")?.takeIf { it.length == 6 }?.let {
        runCatching { (0xFF000000L or it.toLong(16)).toInt() }.getOrNull()
    }

    internal fun parse(json: String): List<Itinerary> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("itineraries") ?: return emptyList()
        val out = ArrayList<Itinerary>()
        for (i in 0 until arr.length()) {
            val it = arr.getJSONObject(i)
            val legsJson = it.optJSONArray("legs") ?: continue
            val legs = ArrayList<Leg>()
            for (j in 0 until legsJson.length()) {
                val l = legsJson.getJSONObject(j)
                val from = l.optJSONObject("from")
                val to = l.optJSONObject("to")
                val short = l.optString("routeShortName").ifBlank { l.optString("displayName") }
                legs += Leg(
                    mode = l.optString("mode"),
                    line = short.ifBlank { l.optString("tripShortName") },
                    color = color(l.optString("routeColor")),
                    headsign = l.optString("headsign"),
                    from = from?.optString("name").orEmpty(),
                    to = to?.optString("name").orEmpty(),
                    start = time(l.optString("startTime")),
                    end = time(l.optString("endTime")),
                    stops = l.optJSONArray("intermediateStops")?.length()?.plus(1) ?: 0,
                    agency = l.optString("agencyName"),
                    agencyUrl = l.optString("agencyUrl"),
                    fromLat = from?.optDouble("lat") ?: Double.NaN,
                    fromLon = from?.optDouble("lon") ?: Double.NaN,
                )
            }
            if (legs.isEmpty()) continue
            out += Itinerary(time(it.optString("startTime")), time(it.optString("endTime")), it.optInt("transfers"), legs)
        }
        return out
    }
}

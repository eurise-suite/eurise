package eu.eurise.core.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Tutto quello che mostra Meteo, già filtrato per la zona. [updated] è l'ora dei dati delle previsioni. */
data class MeteoState(
    val place: Place?,
    val forecast: Forecast?,
    val pollen: Pollen?,
    val alerts: List<Alert>,
    val updated: Long,
    val needsLocation: Boolean = false,
)

/**
 * Meteo della suite: previsioni e pollini da Open-Meteo (posizione arrotondata a due decimali, circa 1 km),
 * allerte dal feed di MeteoAlarm del paese (uguale per tutti: la zona si confronta sul telefono). Ogni servizio
 * passa dal centro privacy e si spegne lì. I dati restano in file dell'app: previsioni e allerte valgono 30 minuti,
 * i pollini tre ore. Nessuna chiave, nessun account.
 */
class MeteoRepository(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "meteo").apply { mkdirs() }

    fun hasLocationPermission(): Boolean =
        app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Solo quello che c'è già sul telefono, senza rete: per mostrare subito qualcosa. */
    suspend fun cached(): MeteoState? = withContext(Dispatchers.IO) { build(network = false) }

    /** Dati aggiornati; [force] ignora la durata della copia sul telefono. */
    suspend fun load(force: Boolean = false, withPollen: Boolean = true): MeteoState = withContext(Dispatchers.IO) {
        build(network = true, force = force, withPollen = withPollen) ?: MeteoState(null, null, null, emptyList(), 0, needsLocation = !hasLocationPermission())
    }

    private fun build(network: Boolean, force: Boolean = false, withPollen: Boolean = true): MeteoState? {
        val loc = location()
        if (loc == null && !network) return null
        if (loc == null) return MeteoState(null, null, null, emptyList(), 0, needsLocation = true)
        val (lat, lon) = loc
        val place = place(lat, lon, network)
        val forecastFile = File(dir, "forecast.json")
        val forecastJson = fetch(
            forecastFile, 30, network, force, PrivacyService.Weather,
            String.format(
                Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                    "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day" +
                    "&hourly=temperature_2m,precipitation_probability,weather_code" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,wind_gusts_10m_max" +
                    "&timezone=auto&forecast_days=7",
                lat, lon,
            ),
        )
        val forecast = forecastJson?.let { runCatching { MeteoParse.forecast(it) }.getOrNull() }
        val now = LocalDateTime.now(forecast?.zone ?: ZoneId.systemDefault())
        val pollen = fetch(
            File(dir, "pollen.json"), 180, network && withPollen, force, PrivacyService.Pollen,
            String.format(
                Locale.ROOT,
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.2f&longitude=%.2f" +
                    "&hourly=alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen&timezone=auto&forecast_days=2",
                lat, lon,
            ),
        )?.let { runCatching { MeteoParse.pollen(it, now.toLocalDate()) }.getOrNull() }
        val feed = place?.countryCode?.let { AlarmFeeds.forCountry(it) }
        val alerts = feed?.let { name ->
            fetch(File(dir, "alerts-$name.xml"), 30, network, force, PrivacyService.Alerts, "https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-$name")
        }?.let { xml ->
            val all = runCatching { MeteoParse.alerts(xml) }.getOrDefault(emptyList())
            MeteoParse.forPlace(all, listOfNotNull(place?.region, place?.provinceName, place?.name), now)
        }.orEmpty()
        if (forecast == null && !network) return null
        return MeteoState(place?.place, forecast, pollen, alerts, forecastFile.lastModified())
    }

    /** Legge il file se è ancora fresco; altrimenti, se si può, lo riscarica (il vecchio resta se la rete manca). */
    private fun fetch(file: File, minutes: Int, network: Boolean, force: Boolean, service: PrivacyService, url: String): String? {
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < minutes * 60_000L
        if ((fresh && !force) || !network || !PrivacyCenter.allowed(service)) return file.takeIf { it.exists() && PrivacyCenter.allowed(service) }?.readText()
        PrivacyCenter.log(service)
        val body = runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Eurise")
                try { if (responseCode == 200) inputStream.bufferedReader().readText() else null } finally { disconnect() }
            }
        }.getOrNull()
        if (body != null) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(body)
            tmp.renameTo(file)
            return body
        }
        return file.takeIf { it.exists() }?.readText()
    }

    private class Resolved(val place: Place, val provinceName: String?, val region: String?, val name: String, val countryCode: String?)

    /** Il nome del luogo dal telefono (Geocoder), ricordato per coordinate arrotondate. Senza servizio: nessun nome. */
    @Suppress("DEPRECATION")
    private fun place(lat: Double, lon: Double, network: Boolean): Resolved? {
        val key = String.format(Locale.ROOT, "%.2f,%.2f", lat, lon)
        val file = File(dir, "place.txt")
        val saved = file.takeIf { it.exists() }?.readLines()
        // Il servizio di posizione del telefono (Google o OpenStreetMap con microG) riceve la stessa posizione arrotondata
        // del meteo, e solo se il meteo è acceso nel centro privacy.
        val lines = if (saved != null && saved.firstOrNull() == key) saved else if (network && Geocoder.isPresent() && PrivacyCenter.allowed(PrivacyService.Weather)) {
            val a = runCatching {
                Geocoder(app, Locale.ITALIAN).getFromLocation(Math.round(lat * 100) / 100.0, Math.round(lon * 100) / 100.0, 1)?.firstOrNull()
            }.getOrNull()
            if (a == null) null else listOf(key, a.locality ?: a.subLocality ?: a.subAdminArea ?: "", a.subAdminArea ?: "", a.adminArea ?: "", a.countryCode ?: "")
                .also { file.writeText(it.joinToString("\n")) }
        } else null
        lines ?: return null
        val name = lines.getOrNull(1).orEmpty().ifEmpty { return null }
        val province = lines.getOrNull(2)?.ifEmpty { null }
        val region = lines.getOrNull(3)?.ifEmpty { null }
        val country = lines.getOrNull(4)?.ifEmpty { null }
        val code = if (country == "IT") Provinces.code(province) else null
        return Resolved(Place(name, code, region, country), province, region, name, country)
    }

    /** Ultima posizione approssimata nota, senza chiederne una nuova (niente GPS acceso apposta). */
    private fun location(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val lm = app.getSystemService(LocationManager::class.java) ?: return null
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        return loc.latitude to loc.longitude
    }
}

/** I feed nazionali di MeteoAlarm, per sigla del paese. */
object AlarmFeeds {
    private val names = mapOf(
        "IT" to "italy", "SM" to "italy", "VA" to "italy", "FR" to "france", "DE" to "germany", "AT" to "austria", "CH" to "switzerland",
        "ES" to "spain", "PT" to "portugal", "SI" to "slovenia", "HR" to "croatia", "GR" to "greece", "BE" to "belgium",
        "NL" to "netherlands", "LU" to "luxembourg", "IE" to "ireland", "GB" to "united-kingdom", "NO" to "norway", "SE" to "sweden",
        "FI" to "finland", "DK" to "denmark", "PL" to "poland", "CZ" to "czechia", "SK" to "slovakia", "HU" to "hungary",
        "RO" to "romania", "BG" to "bulgaria", "MT" to "malta", "CY" to "cyprus", "EE" to "estonia", "LV" to "latvia", "LT" to "lithuania",
    )

    fun forCountry(code: String): String? = names[code.uppercase()]
}

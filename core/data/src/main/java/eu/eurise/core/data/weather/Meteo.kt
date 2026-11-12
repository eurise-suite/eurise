package eu.eurise.core.data.weather

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/** Il cielo, dai codici WMO di Open-Meteo. */
enum class Sky { Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Showers, Snow, Storm }

data class Now(val temp: Int, val feels: Int, val sky: Sky, val windKmh: Int, val gustKmh: Int, val windFrom: Int, val day: Boolean)
data class Hour(val time: LocalDateTime, val temp: Int, val rainChance: Int, val sky: Sky)
data class Day(
    val date: LocalDate, val sky: Sky, val max: Int, val min: Int, val rainChance: Int, val gustKmh: Int,
    val sunrise: LocalDateTime?, val sunset: LocalDateTime?,
)

/** Previsioni per un punto: adesso, le prossime ore e i giorni. */
data class Forecast(
    val now: Now, val hours: List<Hour>, val days: List<Day>, val zone: ZoneId,
    /** Tutte le ore dei sette giorni (per il dettaglio di un giorno); [hours] sono le prossime 24. */
    val allHours: List<Hour> = hours,
)

/** Pollini di oggi: livello complessivo (0 assenti, 1 bassi, 2 moderati, 3 alti) e le specie più presenti. */
data class Pollen(val level: Int, val top: List<String>)

/** Un'allerta ufficiale. [level]: 2 gialla, 3 arancione, 4 rossa (scala MeteoAlarm). */
data class Alert(
    val level: Int, val event: String, val area: String, val onset: LocalDateTime?, val expires: LocalDateTime?,
    val headline: String, val description: String, val source: String,
)

/** Lettori dei formati di Open-Meteo e MeteoAlarm: logica pura, provata dai test. */
object MeteoParse {

    fun sky(code: Int): Sky = when (code) {
        0, 1 -> Sky.Clear
        2 -> Sky.PartlyCloudy
        3 -> Sky.Cloudy
        45, 48 -> Sky.Fog
        in 51..57 -> Sky.Drizzle
        in 61..67 -> Sky.Rain
        in 80..82 -> Sky.Showers
        in 71..77, 85, 86 -> Sky.Snow
        in 95..99 -> Sky.Storm
        else -> Sky.Cloudy
    }

    /** Risposta di `/v1/forecast` con current, hourly e daily (vedi [MeteoRepository]). */
    fun forecast(json: String, from: LocalDateTime? = null): Forecast {
        val root = JSONObject(json)
        val zone = runCatching { ZoneId.of(root.optString("timezone")) }.getOrDefault(ZoneId.systemDefault())
        val c = root.getJSONObject("current")
        val now = Now(
            temp = c.getDouble("temperature_2m").roundToInt(),
            feels = c.optDouble("apparent_temperature", c.getDouble("temperature_2m")).roundToInt(),
            sky = sky(c.optInt("weather_code")),
            windKmh = c.optDouble("wind_speed_10m", 0.0).roundToInt(),
            gustKmh = c.optDouble("wind_gusts_10m", 0.0).roundToInt(),
            windFrom = c.optDouble("wind_direction_10m", 0.0).roundToInt(),
            day = c.optInt("is_day", 1) == 1,
        )
        val start = from ?: LocalDateTime.parse(c.getString("time")).withMinute(0)
        val h = root.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val all = (0 until times.length()).map { i ->
            Hour(
                LocalDateTime.parse(times.getString(i)),
                h.getJSONArray("temperature_2m").num(i).roundToInt(),
                h.optJSONArray("precipitation_probability")?.num(i)?.roundToInt() ?: 0,
                sky(h.getJSONArray("weather_code").num(i).toInt()),
            )
        }
        val hours = all.filter { !it.time.isBefore(start) }.take(24)
        val d = root.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val days = (0 until dates.length()).map { i ->
            Day(
                date = LocalDate.parse(dates.getString(i)),
                sky = sky(d.getJSONArray("weather_code").num(i).toInt()),
                max = d.getJSONArray("temperature_2m_max").num(i).roundToInt(),
                min = d.getJSONArray("temperature_2m_min").num(i).roundToInt(),
                rainChance = d.optJSONArray("precipitation_probability_max")?.num(i)?.roundToInt() ?: 0,
                gustKmh = d.optJSONArray("wind_gusts_10m_max")?.num(i)?.roundToInt() ?: 0,
                sunrise = d.optJSONArray("sunrise")?.optString(i)?.takeIf { it.isNotEmpty() }?.let(LocalDateTime::parse),
                sunset = d.optJSONArray("sunset")?.optString(i)?.takeIf { it.isNotEmpty() }?.let(LocalDateTime::parse),
            )
        }
        return Forecast(now, hours, days, zone, all)
    }

    private fun JSONArray.num(i: Int): Double = if (isNull(i)) 0.0 else getDouble(i)

    /** Nomi italiani delle specie della Air Quality API (CAMS). */
    private val species = linkedMapOf(
        "grass_pollen" to "graminacee", "birch_pollen" to "betulla", "alder_pollen" to "ontano",
        "olive_pollen" to "olivo", "mugwort_pollen" to "artemisia", "ragweed_pollen" to "ambrosia",
    )

    /**
     * Pollini dalla Air Quality API: il massimo di oggi per ogni specie (granuli/m³). Soglie semplici, comuni a tutte
     * le specie: sotto 10 bassi, sotto 50 moderati, da 50 alti; sotto 1 assenti.
     */
    fun pollen(json: String, day: LocalDate): Pollen {
        val h = JSONObject(json).getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val today = (0 until times.length()).filter { LocalDateTime.parse(times.getString(it)).toLocalDate() == day }
        val peaks = species.mapNotNull { (key, name) ->
            val arr = h.optJSONArray(key) ?: return@mapNotNull null
            val peak = today.maxOfOrNull { arr.num(it) } ?: 0.0
            name to peak
        }
        val max = peaks.maxOfOrNull { it.second } ?: 0.0
        val level = when { max < 1 -> 0; max < 10 -> 1; max < 50 -> 2; else -> 3 }
        return Pollen(level, peaks.filter { it.second >= 10 }.sortedByDescending { it.second }.map { it.first }.take(3))
    }

    /**
     * Allerte dal feed Atom di MeteoAlarm (`meteoalarm-legacy-atom-<paese>`). Legge i campi CAP di ogni voce senza
     * badare al prefisso dei nomi; il livello viene da `awareness_level` («3; orange; Severe»), altrimenti da severity.
     */
    fun alerts(xml: String, zone: ZoneId = ZoneId.systemDefault()): List<Alert> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val entries = doc.getElementsByTagName("entry")
        return (0 until entries.length).mapNotNull { i ->
            val e = entries.item(i) as Element
            val f = fields(e)
            val level = f["awareness_level"]?.substringBefore(';')?.trim()?.toIntOrNull() ?: when (f["severity"]?.lowercase()) {
                "moderate" -> 2; "severe" -> 3; "extreme" -> 4; else -> 1
            }
            if (level < 2) return@mapNotNull null
            Alert(
                level = level,
                event = f["event"].orEmpty().ifEmpty { f["awareness_type"]?.substringAfter(';')?.trim().orEmpty() },
                area = f["areaDesc"].orEmpty(),
                onset = (f["onset"] ?: f["effective"])?.let { time(it, zone) },
                expires = f["expires"]?.let { time(it, zone) },
                headline = f["headline"] ?: f["title"].orEmpty(),
                description = f["description"] ?: f["summary"].orEmpty(),
                source = f["senderName"] ?: f["name"].orEmpty(),
            )
        }
    }

    /** Testo dei discendenti di [e] per nome locale (senza prefisso); il primo vince. */
    private fun fields(e: Element): Map<String, String> {
        val out = HashMap<String, String>()
        val all = e.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val n = all.item(i) as Element
            val name = n.nodeName.substringAfter(':')
            if (name == "parameter" || name == "valueName" || name == "value") continue
            val text = n.textContent?.trim().orEmpty()
            if (text.isNotEmpty() && n.getElementsByTagName("*").length == 0) out.putIfAbsent(name, text)
        }
        // Parametri CAP: <parameter><valueName>awareness_level</valueName><value>3; orange; Severe</value></parameter>
        val params = e.getElementsByTagName("*")
        for (i in 0 until params.length) {
            val p = params.item(i) as Element
            if (p.nodeName.substringAfter(':') != "parameter") continue
            var key: String? = null
            var value: String? = null
            val kids = p.getElementsByTagName("*")
            for (k in 0 until kids.length) {
                val kid = kids.item(k)
                when (kid.nodeName.substringAfter(':')) {
                    "valueName" -> key = kid.textContent.trim()
                    "value" -> value = kid.textContent.trim()
                }
            }
            if (key != null && value != null) out.putIfAbsent(key, value)
        }
        return out
    }

    private fun time(raw: String, zone: ZoneId): LocalDateTime? =
        runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw) }.getOrNull()

    /**
     * Le allerte che riguardano la zona: l'area dell'allerta deve contenere una delle parole significative dei
     * [places] (regione, provincia, comune, come li dà il telefono). Confronto senza maiuscole né accenti.
     */
    fun forPlace(alerts: List<Alert>, places: List<String>, at: LocalDateTime): List<Alert> {
        val words = places.flatMap { place -> words(place) }.filter { it.length >= 4 }.toSet()
        if (words.isEmpty()) return emptyList()
        return alerts.filter { a ->
            (a.expires == null || a.expires.isAfter(at)) && words(a.area).any { it in words }
        }.sortedWith(compareByDescending<Alert> { it.level }.thenBy { it.onset })
    }

    private val stop = setOf("provincia", "della", "dello", "delle", "citta", "metropolitana", "zona", "area", "regione", "libero", "consorzio")

    private fun words(s: String): List<String> = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .split(Regex("[^a-z]+"))
        .filter { it.isNotEmpty() && it !in stop }
}

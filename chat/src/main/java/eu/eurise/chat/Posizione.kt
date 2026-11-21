package eu.eurise.chat

import org.json.JSONObject
import java.time.LocalDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Posizione dal vivo a tempo (tavola ChatPosizione), senza Android: si prova con i test.
 *
 * Gli eventi sono quelli di Element (MSC3672, MSC3488): uno stato `beacon_info` per persona (chiave = il suo id) dice che
 * la condivisione è accesa e fino a quando; le posizioni sono messaggi `beacon` che vi si riferiscono, cifrati come gli
 * altri messaggi. Lo stato non è cifrato (Matrix non cifra gli stati) ma dentro non c'è nessuna posizione: solo «dal vivo»
 * e la durata. Le mappe non si scaricano da nessun server: niente richieste che dicano dove sei.
 */
object Posizione {
    const val INFO = "org.matrix.msc3672.beacon_info"
    const val BEACON = "org.matrix.msc3672.beacon"
    const val LOCATION = "org.matrix.msc3488.location"
    const val TS = "org.matrix.msc3488.ts"
    const val ASSET = "org.matrix.msc3488.asset"

    /** «Finché arrivo» al più dura tre ore. */
    const val ARRIVAL_MAX_MS = 3 * 3_600_000L

    data class Info(val live: Boolean, val start: Long, val timeout: Long, val description: String) {
        val endsAt: Long get() = start + timeout
        fun isLive(now: Long) = live && now < endsAt
    }

    data class Pos(val lat: Double, val lon: Double, val accuracy: Double?, val ts: Long)

    fun infoContent(description: String, start: Long, timeoutMs: Long, live: Boolean): JSONObject = JSONObject()
        .put("description", description).put("live", live).put("timeout", timeoutMs).put(TS, start)
        .put(ASSET, JSONObject().put("type", "m.self"))

    fun info(content: JSONObject?): Info? {
        content ?: return null
        if (!content.has("live")) return null
        return Info(content.optBoolean("live"), content.optLong(TS), content.optLong("timeout"), content.optString("description"))
    }

    /** Una posizione della condivisione [infoId]. */
    fun beaconContent(infoId: String, p: Pos): JSONObject = JSONObject()
        .put("m.relates_to", JSONObject().put("rel_type", "m.reference").put("event_id", infoId))
        .put(LOCATION, JSONObject().put("uri", geoUri(p)))
        .put(TS, p.ts)

    /** La condivisione a cui si riferisce una posizione. */
    fun beaconOf(content: JSONObject?): String? =
        content?.optJSONObject("m.relates_to")?.takeIf { it.optString("rel_type") == "m.reference" }?.optString("event_id")?.ifEmpty { null }

    fun pos(content: JSONObject?): Pos? {
        content ?: return null
        val uri = content.optJSONObject(LOCATION)?.optString("uri")?.ifEmpty { null } ?: content.optString("geo_uri").ifEmpty { return null }
        val g = parseGeo(uri) ?: return null
        return g.copy(ts = content.optLong(TS).takeIf { it > 0 } ?: 0L)
    }

    /** «geo:45.46421,9.19003;u=12» → posizione (ts = 0). */
    fun parseGeo(uri: String): Pos? {
        if (!uri.startsWith("geo:")) return null
        val parts = uri.removePrefix("geo:").split(';')
        val c = parts[0].split(',')
        val lat = c.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon = c.getOrNull(1)?.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val u = parts.drop(1).firstOrNull { it.startsWith("u=") }?.removePrefix("u=")?.toDoubleOrNull()
        return Pos(lat, lon, u, 0)
    }

    fun geoUri(p: Pos): String =
        "geo:" + "%.5f".format(java.util.Locale.ROOT, p.lat) + "," + "%.5f".format(java.util.Locale.ROOT, p.lon) +
            (p.accuracy?.let { ";u=" + it.toInt() } ?: "")

    /** Le scelte di «Per quanto?»: 15 minuti, un'ora, fino alle 23 (solo prima delle 22), finché arrivo. */
    enum class Until { Quarter, Hour, Evening, Arrival }

    fun choices(now: LocalDateTime): List<Until> = buildList {
        add(Until.Quarter); add(Until.Hour)
        if (now.hour < 22) add(Until.Evening)
        add(Until.Arrival)
    }

    /** Quanto dura una scelta, a partire da [now]. */
    fun duration(u: Until, now: LocalDateTime): Long = when (u) {
        Until.Quarter -> 15 * 60_000L
        Until.Hour -> 3_600_000L
        Until.Evening -> java.time.Duration.between(now, now.toLocalDate().atTime(23, 0)).toMillis().coerceAtLeast(60_000L)
        Until.Arrival -> ARRIVAL_MAX_MS
    }

    /** Metri tra due punti (formula dell'emisenoverso). */
    fun meters(a: Pos, b: Pos): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /**
     * «Finché arrivo» senza destinazione: si è arrivati quando, dopo essersi spostati di almeno [moved] metri dalla
     * partenza, si resta entro [still] metri dallo stesso punto per [minutes] minuti. Con Mappe si userà la meta del
     * percorso.
     */
    class Arrival(private val moved: Double = 500.0, private val still: Double = 60.0, private val minutes: Int = 5) {
        private var start: Pos? = null
        private var anchor: Pos? = null
        private var left = false

        /** Vero quando si è arrivati. */
        fun feed(p: Pos): Boolean {
            val s = start ?: run { start = p; anchor = p; return false }
            if (!left && meters(s, p) >= moved) { left = true; anchor = p; return false }
            if (!left) return false
            val a = anchor!!
            if (meters(a, p) > still) { anchor = p; return false }
            return p.ts - a.ts >= minutes * 60_000L
        }
    }
}

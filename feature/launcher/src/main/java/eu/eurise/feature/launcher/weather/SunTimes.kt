package eu.eurise.feature.launcher.weather

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Ore di alba e tramonto (algoritmo dell'Almanac for Computers), calcolate sul telefono dalla posizione approssimata. */
object SunTimes {
    private const val ZENITH = 90.833

    fun of(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId = ZoneId.systemDefault()): Pair<LocalTime, LocalTime>? {
        val rise = calc(date, latitude, longitude, zone, true) ?: return null
        val set = calc(date, latitude, longitude, zone, false) ?: return null
        return rise to set
    }

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    private fun calc(date: LocalDate, lat: Double, lng: Double, zone: ZoneId, rising: Boolean): LocalTime? {
        val n = date.dayOfYear
        val lngHour = lng / 15
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634
        l = (l + 360) % 360
        var ra = deg(atan(0.91764 * tan(rad(l))))
        ra = (ra + 360) % 360
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(ZENITH)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1 || cosH < -1) return null // sole sempre sotto o sopra l'orizzonte
        val h = (if (rising) 360 - deg(acos(cosH)) else deg(acos(cosH))) / 15
        val localMean = h + ra - 0.06571 * t - 6.622
        val utc = ((localMean - lngHour) % 24 + 24) % 24
        val seconds = (utc * 3600).toLong()
        val utcTime = ZonedDateTime.of(date, LocalTime.MIDNIGHT, ZoneId.of("UTC")).plusSeconds(seconds)
        return utcTime.withZoneSameInstant(zone).toLocalTime()
    }
}

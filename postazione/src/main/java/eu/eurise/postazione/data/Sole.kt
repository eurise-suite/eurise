package eu.eurise.postazione.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Il tempo che fa, per il cielo dietro la Postazione (tavola «Il cielo secondo il tempo»). */
enum class Tempo { Sereno, Nuvoloso, Coperto, Pioggia, Neve, Temporale }

/** Il momento della giornata, dalle ore di alba e tramonto del posto (tavola «Il sole durante la giornata»). */
enum class Fase { Notte, Alba, Presto, Giorno, Pomeriggio, Tramonto, Sera }

/**
 * Alba, tramonto e posizione del sole, calcolati sul telefono senza rete (formule della NOAA, precisione di qualche
 * minuto: basta per i colori del cielo). Il posto è quello dell'auto se l'hai segnata, altrimenti si stima dal fuso orario.
 */
object Sole {
    /** Alba e tramonto locali; null se il sole non sorge o non tramonta (vicino ai poli). */
    fun orari(data: LocalDate, lat: Double, lon: Double, zona: ZoneId): Pair<LocalTime, LocalTime>? {
        val g = 2 * PI / 365.0 * (data.dayOfYear - 1)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val phi = Math.toRadians(lat)
        val c = cos(Math.toRadians(90.833)) / (cos(phi) * cos(decl)) - tan(phi) * tan(decl)
        if (c < -1 || c > 1) return null
        val ha = Math.toDegrees(acos(c))
        val offset = zona.rules.getOffset(data.atTime(12, 0)).totalSeconds / 60.0
        val alba = 720 - 4 * (lon + ha) - eq + offset
        val tramonto = 720 - 4 * (lon - ha) - eq + offset
        return minuti(alba) to minuti(tramonto)
    }

    private fun minuti(m: Double): LocalTime {
        val t = ((m.toInt() % 1440) + 1440) % 1440
        return LocalTime.of(t / 60, t % 60)
    }

    /** Una stima del posto dal fuso orario (longitudine) e una latitudine media europea, se non sappiamo dov'è il telefono. */
    fun lonDaZona(zona: ZoneId, data: LocalDate): Double =
        zona.rules.getStandardOffset(data.atStartOfDay(zona).toInstant()).totalSeconds / 3600.0 * 15.0

    fun fase(ora: LocalTime, alba: LocalTime, tramonto: LocalTime): Fase {
        val m = ora.toSecondOfDay() / 60
        val a = alba.toSecondOfDay() / 60
        val t = tramonto.toSecondOfDay() / 60
        val mezzo = (a + t) / 2
        return when {
            m < a - 45 -> Fase.Notte
            m < a + 30 -> Fase.Alba
            m < a + 150 -> Fase.Presto
            m < mezzo + 120 -> Fase.Giorno
            m < t - 60 -> Fase.Pomeriggio
            m < t + 20 -> Fase.Tramonto
            m < t + 150 -> Fase.Sera
            else -> Fase.Notte
        }
    }

    /**
     * Dove sta il sole nel cielo, in frazioni della larghezza e dell'altezza: sempre nella parte libera, a destra (mai
     * sopra saluto, orologio e meteo). Sale dal basso all'alba, è in alto a mezzogiorno, scende a destra al tramonto.
     * Null di notte.
     */
    fun posizione(ora: LocalTime, alba: LocalTime, tramonto: LocalTime): Pair<Float, Float>? {
        val m = ora.toSecondOfDay() / 60f
        val a = alba.toSecondOfDay() / 60f
        val t = tramonto.toSecondOfDay() / 60f
        if (m < a - 45 || m > t + 150) return null
        val f = ((m - a) / (t - a)).coerceIn(-0.1f, 1.2f)
        val k = f.coerceIn(0f, 1f)
        val x = (0.56f + 0.40f * f).coerceIn(0.5f, 1.0f)
        val y = (0.64f - 0.58f * sin(PI * k).toFloat()).coerceIn(0.05f, 0.92f) + if (f > 1f) (f - 1f) * 1.4f else 0f
        return x to y.coerceAtMost(1.0f)
    }

    /** Dal cielo letto dall'app Eurise (Open-Meteo) al tempo del fondo. */
    fun tempo(cielo: String?): Tempo = when (cielo) {
        null, "Clear" -> Tempo.Sereno
        "PartlyCloudy" -> Tempo.Nuvoloso
        "Drizzle", "Rain", "Showers" -> Tempo.Pioggia
        "Snow" -> Tempo.Neve
        "Storm" -> Tempo.Temporale
        else -> Tempo.Coperto
    }

    /** Il cielo di adesso: fase, posizione del sole (null di notte) e tempo. */
    fun adesso(now: LocalDateTime, zona: ZoneId, lat: Double?, lon: Double?, cielo: String?): Cielo {
        val data = now.toLocalDate()
        val o = orari(data, lat ?: 43.0, lon ?: lonDaZona(zona, data), zona) ?: (LocalTime.of(7, 0) to LocalTime.of(18, 0))
        return Cielo(fase(now.toLocalTime(), o.first, o.second), posizione(now.toLocalTime(), o.first, o.second), tempo(cielo))
    }
}

data class Cielo(val fase: Fase, val sole: Pair<Float, Float>?, val tempo: Tempo) {
    /** Cielo scuro: le scritte diventano chiare. */
    val scuro: Boolean get() = fase == Fase.Notte || fase == Fase.Sera || fase == Fase.Tramonto || fase == Fase.Alba || tempo == Tempo.Temporale
}

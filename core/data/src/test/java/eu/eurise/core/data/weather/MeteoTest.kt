package eu.eurise.core.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MeteoTest {
    private val zone = ZoneId.of("Europe/Rome")

    private fun forecastJson(rainAt15: Boolean = false): String {
        val hours = (0 until 48).map { LocalDateTime.of(2026, 11, 12, 0, 0).plusHours(it.toLong()) }
        val times = hours.joinToString(",") { "\"${it}\"".replace(":00\"", ":00\"") }
        val temps = hours.joinToString(",") { "${8 + it.hour % 6}" }
        val probs = hours.joinToString(",") { if (rainAt15 && it.dayOfMonth == 12 && it.hour in 14..17) "80" else "5" }
        val codes = hours.joinToString(",") { if (rainAt15 && it.dayOfMonth == 12 && it.hour in 14..17) "95" else "3" }
        return """
        {"timezone":"Europe/Rome",
         "current":{"time":"2026-11-12T08:45","temperature_2m":11.2,"apparent_temperature":9.4,"weather_code":61,
           "wind_speed_10m":14.0,"wind_direction_10m":225,"wind_gusts_10m":32.0,"is_day":1},
         "hourly":{"time":[$times],"temperature_2m":[$temps],"precipitation_probability":[$probs],"weather_code":[$codes]},
         "daily":{"time":["2026-11-12","2026-11-13","2026-11-14"],"weather_code":[95,3,0],
           "temperature_2m_max":[14.4,13.0,15.2],"temperature_2m_min":[8.1,7.0,5.0],
           "sunrise":["2026-11-12T07:21","2026-11-13T07:22","2026-11-14T07:24"],
           "sunset":["2026-11-12T16:52","2026-11-13T16:51","2026-11-14T16:50"],
           "precipitation_probability_max":[80,10,0],"wind_gusts_10m_max":[40,20,15]}}
        """.trimIndent()
    }

    @Test fun forecastIsRead() {
        val f = MeteoParse.forecast(forecastJson())
        assertEquals(11, f.now.temp)
        assertEquals(9, f.now.feels)
        assertEquals(Sky.Rain, f.now.sky)
        assertEquals(32, f.now.gustKmh)
        assertEquals("SO", MeteoText.windFrom(f.now.windFrom))
        assertEquals(LocalDateTime.of(2026, 11, 12, 8, 0), f.hours.first().time)
        assertEquals(24, f.hours.size)
        assertEquals(3, f.days.size)
        assertEquals(15, f.days[2].max)
        assertEquals(LocalDateTime.of(2026, 11, 12, 16, 52), f.days[0].sunset)
        assertEquals(zone, f.zone)
    }

    @Test fun storyTellsTheStorm() {
        val f = MeteoParse.forecast(forecastJson(rainAt15 = true))
        val s = MeteoText.story(f, emptyList(), LocalDateTime.of(2026, 11, 12, 8, 45))
        assertEquals("Oggi pomeriggio: temporali", s.title)
        assertTrue(s.body, s.body.startsWith("Temporali tra le 14 e le 18"))
    }

    @Test fun eventLineCalmOrRain() {
        val now = LocalDateTime.of(2026, 11, 12, 8, 45)
        val calm = MeteoText.forEvent(LocalDateTime.of(2026, 11, 12, 9, 30), 18, MeteoParse.forecast(forecastJson()), emptyList(), now)
        assertEquals(MeteoLine.Tone.Calm, calm?.tone)
        assertEquals("Oggi il tempo regge: dovresti arrivare in tempo.", calm?.text)
        val wet = MeteoText.forEvent(LocalDateTime.of(2026, 11, 12, 15, 0), 22, MeteoParse.forecast(forecastJson(rainAt15 = true)), emptyList(), now)
        assertEquals(MeteoLine.Tone.Watch, wet?.tone)
    }

    private val atom = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
          <entry>
            <title>Orange Thunderstorm Warning issued for Italy - Lombardia</title>
            <cap:areaDesc>Lombardia - Nodo idraulico di Milano e Brianza</cap:areaDesc>
            <cap:event>Temporali</cap:event>
            <cap:severity>Severe</cap:severity>
            <cap:onset>2026-11-13T14:00:00+01:00</cap:onset>
            <cap:expires>2026-11-13T22:00:00+01:00</cap:expires>
            <cap:awareness_level>3; orange; Severe</cap:awareness_level>
          </entry>
          <entry>
            <title>Yellow wind</title>
            <cap:areaDesc>Sicilia - Nord orientale</cap:areaDesc>
            <cap:event>Vento</cap:event>
            <cap:severity>Moderate</cap:severity>
            <cap:onset>2026-11-13T06:00:00+01:00</cap:onset>
            <cap:expires>2026-11-13T23:00:00+01:00</cap:expires>
          </entry>
          <entry>
            <title>Green</title>
            <cap:areaDesc>Lombardia - Valtellina</cap:areaDesc>
            <cap:event>Nessuna</cap:event>
            <cap:awareness_level>1; green; Minor</cap:awareness_level>
          </entry>
        </feed>
    """.trimIndent()

    @Test fun alertsAreReadAndFiltered() {
        val all = MeteoParse.alerts(atom, zone)
        assertEquals(2, all.size)
        assertEquals(3, all[0].level)
        assertEquals(2, all[1].level)
        assertEquals(LocalDateTime.of(2026, 11, 13, 14, 0), all[0].onset)
        val mine = MeteoParse.forPlace(all, listOf("Lombardia", "Provincia di Monza e della Brianza", "Monza"), LocalDateTime.of(2026, 11, 12, 9, 0))
        assertEquals(listOf("Temporali"), mine.map { it.event })
        val over = MeteoParse.forPlace(all, listOf("Lombardia"), LocalDateTime.of(2026, 11, 14, 9, 0))
        assertTrue(over.isEmpty())
    }

    @Test fun alertLineAdvisesEarlierDeparture() {
        val alerts = MeteoParse.alerts(atom, zone).take(1)
        val line = MeteoText.forEvent(LocalDateTime.of(2026, 11, 13, 15, 0), 22, null, alerts, LocalDateTime.of(2026, 11, 13, 8, 0))
        assertEquals(MeteoLine.Tone.Alert, line?.tone)
        assertEquals("Allerta meteo arancione dalle 14: presta attenzione e anticipa la partenza alle 14:22.", line?.text)
        assertNull(MeteoText.forEvent(LocalDateTime.of(2026, 11, 13, 15, 0), null, null, emptyList(), LocalDateTime.of(2026, 11, 13, 8, 0)))
    }

    @Test fun pollenLevels() {
        val json = """
            {"hourly":{"time":["2026-11-12T09:00","2026-11-12T12:00","2026-11-13T12:00"],
              "grass_pollen":[3.0,12.5,80.0],"birch_pollen":[0.0,0.2,null],"ragweed_pollen":[null,null,null]}}
        """.trimIndent()
        val p = MeteoParse.pollen(json, LocalDate.of(2026, 11, 12))
        assertEquals(2, p.level)
        assertEquals(listOf("graminacee"), p.top)
        assertEquals("Moderati", MeteoText.pollen(p.level))
    }

    @Test fun provincesAndFlag() {
        assertEquals("MB", Provinces.code("Provincia di Monza e della Brianza"))
        assertEquals("MI", Provinces.code("Città Metropolitana di Milano"))
        assertEquals("RE", Provinces.code("Provincia di Reggio nell'Emilia"))
        assertEquals("BZ", Provinces.code("Provincia autonoma di Bolzano - Alto Adige"))
        assertEquals("AQ", Provinces.code("Provincia dell'Aquila").also { assertNotNull(it) })
        assertEquals("TO", Provinces.code("TO"))
        val p = Place("Monza", "MB", "Lombardia", "IT")
        assertEquals("Monza (MB)", p.label)
        assertEquals("🇮🇹", p.flag)
    }
}

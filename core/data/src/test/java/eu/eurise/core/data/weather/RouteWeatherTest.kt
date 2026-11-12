package eu.eurise.core.data.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteWeatherTest {
    private fun place(codes: String) =
        """{"hourly":{"time":[3600,7200,10800],"weather_code":[$codes],"temperature_2m":[10.5,12,null],"precipitation_probability":[0,70,20],"wind_gusts_10m":[5,30,70]}}"""

    @Test fun piuPuntiOraPiuVicina() {
        val body = "[" + place("0,61,95") + "," + place("1,2,3") + "]"
        val r = RouteWeather.parse(body, longArrayOf(7000, 11000))
        assertEquals(61, r[0].code)
        assertEquals(70, r[0].rain)
        assertEquals(12f, r[0].temp)
        assertEquals(3, r[1].code)
        assertEquals(true, r[1].temp.isNaN())
        assertEquals(70f, r[1].gusts)
    }

    @Test fun unPuntoSolo() {
        val r = RouteWeather.parse(place("0,61,95"), longArrayOf(0))
        assertEquals(listOf(0), r.map { it.code })
    }
}

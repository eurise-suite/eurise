package eu.eurise.core.data.alfred

import eu.eurise.core.data.weather.Now
import eu.eurise.core.data.weather.Sky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AlfredWakeTest {
    @Test
    fun hourInWords() {
        assertEquals("Sono le sette", AlfredWake.hour(LocalTime.of(7, 0)))
        assertEquals("Sono le sei e mezza", AlfredWake.hour(LocalTime.of(6, 30)))
        assertEquals("È l'una e un quarto", AlfredWake.hour(LocalTime.of(13, 15)))
        assertEquals("Sono le 6:40", AlfredWake.hour(LocalTime.of(6, 40)))
        assertEquals("È mezzogiorno", AlfredWake.hour(LocalTime.of(12, 0)))
    }

    @Test
    fun greetingWithWeather() {
        val w = AlfredWake.compose(LocalDate.of(2026, 11, 14), LocalTime.of(7, 0), ", Giulia", Now(8, 7, Sky.Clear, 5, 10, 180, true))
        assertTrue(w.greeting, w.greeting.startsWith("Buongiorno, Giulia. Sono le sette, "))
        assertTrue(w.greeting, w.greeting.endsWith("Fuori 8°, sereno: giornata da cappotto leggero."))
        assertTrue(w.spoken.contains(w.pearl.title))
    }

    @Test
    fun noWeatherNoAddress() {
        val w = AlfredWake.compose(LocalDate.of(2026, 11, 14), LocalTime.of(7, 30), "", null)
        assertTrue(w.greeting, w.greeting.startsWith("Buongiorno. Sono le sette e mezza, "))
        assertTrue(!w.greeting.contains("Fuori"))
    }

    @Test
    fun sameDaySamePearl() {
        val d = LocalDate.of(2026, 3, 2)
        assertEquals(AlfredWake.compose(d, LocalTime.of(7, 0), "", null).pearl, AlfredWake.compose(d, LocalTime.of(8, 0), "", null).pearl)
    }
}

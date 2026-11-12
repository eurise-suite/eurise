package eu.eurise.browser

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingTest {
    private val now = LocalDateTime.of(2026, 11, 12, 10, 0)

    @Test fun minutesOfReading() {
        assertEquals(0, Reading.minutes(""))
        assertEquals(1, Reading.minutes("poche parole"))
        assertEquals(6, Reading.minutes(List(1300) { "parola" }.joinToString(" ")))
    }

    @Test fun findsTheDeadlineInTheArticle() {
        val text = "# TARI 2026: scadenze e come pagare\n\nLa prima rata della tassa sui rifiuti va pagata entro il 16 dicembre con l'avviso pagoPA."
        assertEquals(LocalDate.of(2026, 12, 16), Reading.firstDate(text, now)?.date)
    }

    @Test fun noDateNoCard() {
        assertNull(Reading.firstDate("Un articolo senza scadenze né appuntamenti.", now))
    }

    @Test fun matchesIgnoreCase() {
        assertEquals(listOf(0, 11), Reading.matches("Rata unica rata", "rata"))
        assertEquals(emptyList<Int>(), Reading.matches("testo", " "))
    }

    @Test fun laterKeepsNewestFirstAndReplacesTheSamePage() {
        val a = LaterItem("https://a.it", "A", "testo", 1)
        val list = LaterStore.add(LaterStore.add(emptyList(), a), LaterItem("https://b.it", "B", "testo", 2))
        assertEquals(listOf("https://b.it", "https://a.it"), list.map { it.url })
        assertEquals(2, LaterStore.add(list, a.copy(time = 3)).size)
        assertEquals(list, LaterStore.decode(LaterStore.encode(list)))
    }
}

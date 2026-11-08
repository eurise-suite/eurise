package eu.eurise.feature.tasks

import eu.eurise.core.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class QuickAddTest {

    /** Domenica 8 novembre 2026, ore 21. */
    private val now = LocalDateTime.of(2026, 11, 8, 21, 0)
    private val lists = listOf("Attività", "Casa", "Lavoro", "Date importanti")

    @Test
    fun theExampleOfThePreview() {
        val r = QuickAdd.parse("Pagare il bollo dell'auto il 30 novembre alle 18 ogni anno !importante #casa", now, lists)
        assertEquals("Pagare il bollo dell'auto", r.title)
        assertEquals(LocalDate.of(2026, 11, 30), r.due)
        assertEquals(LocalTime.of(18, 0), r.time)
        assertEquals("FREQ=YEARLY", r.repeat)
        assertEquals(Task.PRIORITY_HIGH, r.priority)
        assertEquals("Casa", r.listName)
        assertEquals(5, r.pieces.size)
    }

    @Test
    fun relativeDaysAndTimes() {
        val r = QuickAdd.parse("Chiamare l'idraulico domani alle 10", now)
        assertEquals("Chiamare l'idraulico", r.title)
        assertEquals(LocalDate.of(2026, 11, 9), r.due)
        assertEquals(LocalTime.of(10, 0), r.time)

        assertEquals(LocalDate.of(2026, 11, 10), QuickAdd.parse("Spesa dopodomani", now).due)
        assertEquals(LocalDate.of(2026, 11, 22), QuickAdd.parse("Revisione tra 2 settimane", now).due)
        assertEquals(LocalTime.of(20, 0), QuickAdd.parse("Telefonare a Anna stasera", now).time)
        assertEquals(LocalTime.of(9, 30), QuickAdd.parse("Riunione alle 9:30 lunedì", now).time)
    }

    @Test
    fun weekdaysAndDatesWithoutYear() {
        assertEquals(LocalDate.of(2026, 11, 13), QuickAdd.parse("Cena venerdì", now).due)
        assertEquals(LocalDate.of(2027, 2, 3), QuickAdd.parse("Tagliando entro il 3/2", now).due)
        assertEquals(LocalDate.of(2027, 1, 5), QuickAdd.parse("Bollo il 5 gennaio", now).due)
        assertEquals(LocalDate.of(2026, 11, 8), QuickAdd.parse("Bucato il 8 novembre", now).due)
    }

    @Test
    fun aTimeAlreadyPastMeansTomorrow() {
        assertEquals(LocalDate.of(2026, 11, 9), QuickAdd.parse("Pillola alle 8", now).due)
        assertEquals(LocalDate.of(2026, 11, 8), QuickAdd.parse("Chiudere il gas alle 22", now).due)
    }

    @Test
    fun repetitions() {
        val monday = QuickAdd.parse("Portare fuori la carta ogni lunedì", now)
        assertEquals("Portare fuori la carta", monday.title)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", monday.repeat)
        assertEquals(LocalDate.of(2026, 11, 9), monday.due)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", QuickAdd.parse("Timbrare nei giorni feriali", now).repeat)
        assertEquals("FREQ=MONTHLY;INTERVAL=3", QuickAdd.parse("Filtro ogni 3 mesi", now).repeat)
        assertEquals("FREQ=DAILY", QuickAdd.parse("Annaffiare tutti i giorni", now).repeat)
    }

    @Test
    fun unknownListsStayInTheTitle() {
        val r = QuickAdd.parse("Comprare il latte #spesa !media", now, lists)
        assertEquals("Comprare il latte #spesa", r.title)
        assertNull(r.listName)
        assertEquals(Task.PRIORITY_MEDIUM, r.priority)
        assertEquals("Date importanti", QuickAdd.parse("Fiori #dateimportanti", now, lists).listName)
    }

    @Test
    fun plainTextStaysAsItIs() {
        val r = QuickAdd.parse("Leggere il libro di Marco", now)
        assertEquals("Leggere il libro di Marco", r.title)
        assertNull(r.due)
        assertNull(r.time)
        assertEquals(0, r.pieces.size)
    }
}

package eu.eurise.core.data

import eu.eurise.core.data.model.Vtodo
import eu.eurise.core.data.model.VtodoFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class VtodoTest {
    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun readsAnotherAppsTask() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VTODO\r\nUID:x1\r\nSUMMARY:Latte\\, pane\r\nDESCRIPTION:riga uno\\nriga\r\n  due\r\nDUE:20261109T083000Z\r\nPRIORITY:1\r\nRELATED-TO;RELTYPE=PARENT:p9\r\nBEGIN:VALARM\r\nTRIGGER:-PT15M\r\nSUMMARY:non questa\r\nEND:VALARM\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"
        val t = Vtodo.parse(ics, rome)!!
        assertEquals("x1", t.uid)
        assertEquals("Latte, pane", t.fields.summary)
        assertEquals("riga uno\nriga due", t.fields.description)
        assertEquals(LocalDate.parse("2026-11-09"), t.fields.due)
        assertEquals(LocalTime.of(9, 30), t.fields.time)
        assertEquals(1, t.fields.priority)
        assertEquals("p9", t.fields.parentUid)
    }

    @Test
    fun allDayAndCompleted() {
        val t = Vtodo.parse("BEGIN:VTODO\nUID:a\nDUE;VALUE=DATE:20260102\nSTATUS:COMPLETED\nCOMPLETED:20260103T100000Z\nEND:VTODO\n", ZoneOffset.UTC)!!
        assertEquals(LocalDate.parse("2026-01-02"), t.fields.due)
        assertNull(t.fields.time)
        assertTrue(t.fields.done)
        assertEquals(LocalDate.parse("2026-01-03").atTime(10, 0).toEpochSecond(ZoneOffset.UTC) * 1000, t.fields.completedAt)
    }

    @Test
    fun whatWeWriteReadsBackTheSame() {
        val f = VtodoFields("Tè; \"caldo\", ora", "due\nrighe", LocalDate.parse("2026-11-09"), LocalTime.of(7, 5), true, 1_762_000_000_000L, 5, "FREQ=WEEKLY;BYDAY=MO", "padre")
        val parsed = Vtodo.parse(Vtodo.render(null, "u", f, 1_762_000_000_000L), rome)!!
        assertEquals("u", parsed.uid)
        assertEquals(f, parsed.fields)
    }

    @Test
    fun longLinesAreFoldedAndUnfolded() {
        val f = VtodoFields("è".repeat(120))
        val text = Vtodo.render(null, "u", f, 0)
        assertTrue(text.split("\r\n").all { it.toByteArray().size <= 75 })
        assertEquals(f.summary, Vtodo.parse(text, rome)!!.fields.summary)
    }

    @Test
    fun rewritingKeepsWhatWeDoNotUnderstand() {
        val base = "BEGIN:VCALENDAR\r\nPRODID:altro\r\nBEGIN:VTODO\r\nUID:x\r\nSUMMARY:vecchio\r\nCATEGORIES:casa\r\nBEGIN:VALARM\r\nTRIGGER:-PT15M\r\nEND:VALARM\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"
        val text = Vtodo.render(base, "x", VtodoFields("nuovo"), 0)
        assertTrue(text.contains("CATEGORIES:casa"))
        assertTrue(text.contains("TRIGGER:-PT15M"))
        assertTrue(text.contains("PRODID:altro"))
        assertEquals("nuovo", Vtodo.parse(text, rome)!!.fields.summary)
        assertEquals(1, Regex("SUMMARY:").findAll(text).count())
    }

    @Test
    fun mergeKeepsChangesFromBothSides() {
        val base = VtodoFields("a", "n")
        val merged = Vtodo.merge(base, base.copy(summary = "telefono"), base.copy(description = "cloud", done = true, completedAt = 1000))
        assertEquals("telefono", merged.summary)
        assertEquals("cloud", merged.description)
        assertTrue(merged.done)
        assertEquals("telefono", Vtodo.merge(base, base.copy(summary = "telefono"), base.copy(summary = "cloud")).summary)
    }
}

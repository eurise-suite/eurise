package eu.eurise.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TaskCollabTest {
    private val t0 = 1_763_370_000_000L // 17 novembre 2025, al secondo

    private val assigned = TaskCollab(
        assignee = "Laura Neri", assigneeEmail = "laura@example.org", handoff = Handoff.Assigned, assignedAt = t0,
        message = "Mi segni cosa non torna?", owner = "Mario, Rossi", ownerEmail = "mario@example.org", notify = true,
    )

    @Test
    fun encodesAndDecodes() {
        val c = assigned.copy(takenBy = "Marta", takenAt = t0 + 1000, message = "due\nrighe \\ e =")
        assertEquals(c, TaskCollab.decode(c.encode()))
        assertNull(TaskCollab().encode())
        assertEquals(TaskCollab(), TaskCollab.decode(null))
    }

    @Test
    fun assignmentGoesThroughTheServerUnchanged() {
        val fields = VtodoFields("Preventivo cucina", due = LocalDate.parse("2026-11-20"), collab = assigned)
        val ics = Vtodo.render(null, "u1", fields, t0)
        val flat = ics.replace("\r\n ", "")
        assertTrue(flat.contains("ATTENDEE;CN=\"Laura Neri\";PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:laura@example.org"))
        assertTrue(flat.contains("ORGANIZER;CN=\"Mario, Rossi\":mailto:mario@example.org"))
        val back = Vtodo.parse(ics, ZoneOffset.UTC)!!.fields
        assertEquals(fields.collab.normalized(false), back.collab)
        assertEquals(fields, back.copy(collab = assigned))
    }

    @Test
    fun takenAndDoneInSharedLists() {
        val taken = VtodoFields("Pane", collab = TaskCollab(takenBy = "Marta", takenAt = t0))
        val ics = Vtodo.render(null, "p", taken, t0)
        assertTrue(ics.contains("STATUS:IN-PROCESS"))
        val back = Vtodo.parse(ics)!!.fields
        assertFalse(back.done)
        assertEquals("Marta", back.collab.takenBy)
        assertEquals(t0, back.collab.takenAt)

        val done = taken.copy(done = true, completedAt = t0 + 60_000, collab = taken.collab.copy(doneBy = "Luca"))
        val doneBack = Vtodo.parse(Vtodo.render(ics, "p", done, t0 + 60_000))!!.fields
        assertTrue(doneBack.done)
        assertEquals("Luca", doneBack.collab.doneBy)
    }

    @Test
    fun anotherAppsAttendeesStayWhenNotAssigned() {
        val other = "BEGIN:VCALENDAR\r\nBEGIN:VTODO\r\nUID:z\r\nSUMMARY:Riunione\r\nATTENDEE;CN=Anna:mailto:anna@example.org\r\nATTENDEE:mailto:bob@example.org\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"
        val fields = Vtodo.parse(other)!!.fields
        assertFalse(fields.collab.assigned)
        val again = Vtodo.render(other, "z", fields.copy(summary = "Riunione lunga"), t0)
        assertTrue(again.contains("mailto:anna@example.org"))
        assertTrue(again.contains("mailto:bob@example.org"))
    }

    @Test
    fun takingBackRemovesTheAttendee() {
        val ics = Vtodo.render(null, "u1", VtodoFields("X", collab = assigned), t0)
        val back = Vtodo.render(ics, "u1", VtodoFields("X"), t0)
        assertFalse(back.contains("ATTENDEE"))
        assertFalse(back.contains("X-EURISE-HANDOFF"))
    }

    @Test
    fun theStepOnlyMovesForward() {
        val base = assigned
        val phone = assigned.copy(handoff = Handoff.Seen, seenAt = t0 + 1000)
        val server = assigned.copy(handoff = Handoff.Started, seenAt = t0 + 500, startedAt = t0 + 2000)
        val merged = TaskCollab.merge(base, phone, server)
        assertEquals(Handoff.Started, merged.handoff)
        assertEquals(t0 + 2000, merged.startedAt)
        assertEquals("laura@example.org", merged.assigneeEmail)
    }

    @Test
    fun whoTookItMergesLikeTheOtherFields() {
        val base = TaskCollab()
        val phone = TaskCollab(takenBy = "Marta", takenAt = t0)
        val merged = TaskCollab.merge(base, phone, base)
        assertEquals("Marta", merged.takenBy)
        val fromServer = TaskCollab.merge(base, base, TaskCollab(takenBy = "Luca", takenAt = t0))
        assertEquals("Luca", fromServer.takenBy)
    }
}

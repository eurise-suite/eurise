package eu.eurise.chat

import eu.eurise.chat.Shifts.Avail
import eu.eurise.chat.Shifts.Change
import eu.eurise.chat.Shifts.Entry
import eu.eurise.chat.Shifts.Kind
import eu.eurise.chat.Shifts.Off
import eu.eurise.chat.Shifts.Person
import eu.eurise.chat.Shifts.Plan
import eu.eurise.chat.Shifts.Problem
import eu.eurise.chat.Shifts.Reply
import eu.eurise.chat.Shifts.Role
import eu.eurise.chat.Shifts.State
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftsTest {
    private val io = "@io:x.it"; private val sara = "@sara:x.it"; private val luca = "@luca:x.it"; private val elena = "@elena:x.it"
    private val four = listOf(io, sara, luca, elena)
    private val mon = LocalDate.of(2026, 11, 16)

    private fun ward(people: List<Person> = four.map { Person(it, target = 150 * 60) }, entries: List<Entry> = emptyList()) =
        Plan("p", Shifts.ward(), people, entries)

    @Test fun notteFinisceIlGiornoDopo() {
        val n = Shifts.ward().first { it.code == "N" }
        assertEquals(600, n.minutes)
        assertEquals(mon.plusDays(1).atTime(7, 0), n.finish(mon))
    }

    @Test fun pasquaEFestivi() {
        assertEquals(LocalDate.of(2026, 4, 5), Shifts.easter(2026))
        assertEquals(LocalDate.of(2027, 3, 28), Shifts.easter(2027))
        assertTrue(LocalDate.of(2026, 4, 6) in Shifts.italianHolidays(2026))
        val ca = Plan("c", Shifts.continuity(), emptyList(), emptyList(), holidays = true)
        // 8 dicembre 2026 è martedì: festivo come domenica; lunedì 7 è prefestivo come sabato.
        assertEquals(7, Shifts.dayKind(ca, LocalDate.of(2026, 12, 8)))
        assertEquals(6, Shifts.dayKind(ca, LocalDate.of(2026, 12, 7)))
        assertEquals(3, Shifts.dayKind(ca, LocalDate.of(2026, 12, 9)))
        assertEquals(listOf("F", "N"), Shifts.typesOn(ca, LocalDate.of(2026, 12, 8)).map { it.code })
        assertEquals(listOf("PF", "N"), Shifts.typesOn(ca, LocalDate.of(2026, 12, 7)).map { it.code })
        assertEquals(listOf("N"), Shifts.typesOn(ca, LocalDate.of(2026, 12, 9)).map { it.code })
    }

    @Test fun genereCopreTuttoERispettaIRiposi() {
        val plan = ward()
        val g = Shifts.generate(plan, emptyMap(), mon, 28)
        assertEquals(emptyList<Any>(), g.uncovered)
        assertEquals(28 * 3, g.entries.size)
        val slots = Shifts.allSlots(plan, g.entries)
        for (p in four) {
            val mine = slots.filter { it.person == p }.sortedBy { it.start }
            mine.zipWithNext().forEach { (a, b) ->
                assertTrue("riposo di $p tra ${a.start} e ${b.start}", java.time.Duration.between(a.end, b.start).toMinutes() >= Shifts.REST_MINUTES)
            }
        }
        // Equo: ore simili per tutti (al massimo un turno lungo di differenza).
        val hours = four.map { p -> slots.filter { it.person == p }.sumOf { it.minutes } }
        assertTrue("ore $hours", hours.max() - hours.min() <= 600)
        // Stesso risultato su ogni telefono.
        assertEquals(g, Shifts.generate(plan, emptyMap(), mon, 28))
    }

    @Test fun indisponibilitaEPreferenze() {
        val plan = ward()
        val avail = mapOf(
            sara to Avail(sara, off = listOf(Off(mon, mon.plusDays(6)))),
            luca to Avail(luca, avoid = setOf("N")),
            elena to Avail(elena, prefer = setOf("N")),
        )
        val g = Shifts.generate(plan, avail, mon, 14)
        assertTrue(g.entries.none { sara in it.people && it.date.isBefore(mon.plusDays(7)) })
        val nights = g.entries.filter { it.type == "N" }
        assertTrue(nights.count { elena in it.people } > nights.count { luca in it.people })
    }

    @Test fun fissatiAManoRestano() {
        val locked = Entry(mon, "M", listOf(luca), locked = true)
        val g = Shifts.generate(ward(entries = listOf(locked, Entry(mon, "P", listOf(io)))), emptyMap(), mon, 7)
        assertEquals(listOf(luca), g.entries.first { it.key == locked.key }.people)
        // Luca la mattina non può fare la notte prima del giorno dopo: 11 ore.
        assertFalse(luca in g.entries.first { it.date == mon && it.type == "N" }.people)
    }

    @Test fun sostitutiEReperibili() {
        val people = listOf(
            Person(io), Person(sara), Person(luca),
            Person(elena, Role.Substitute, replaces = luca, from = mon, to = mon.plusDays(6)),
            Person("@ext:x.it", Role.External),
        )
        val g = Shifts.generate(ward(people), emptyMap(), mon, 14)
        assertTrue(g.entries.none { "@ext:x.it" in it.people })
        assertTrue(g.entries.none { luca in it.people && it.date.isBefore(mon.plusDays(7)) })
        assertTrue(g.entries.none { elena in it.people && !it.date.isBefore(mon.plusDays(7)) })
        assertTrue(g.entries.any { elena in it.people })
    }

    @Test fun doppiaConTelefonoATurno() {
        val types = listOf(Shifts.Type("N", "Notte", LocalTime.of(20, 0), LocalTime.of(8, 0), people = 2))
        val plan = Plan("p", types, four.map { Person(it) }, emptyList())
        val g = Shifts.generate(plan, emptyMap(), mon, 8)
        assertTrue(g.entries.all { it.people.size == 2 && it.phone in it.people })
        val phones = g.entries.groupingBy { it.phone }.eachCount()
        assertTrue("telefono $phones", phones.values.max() - phones.values.min() <= 1)
    }

    @Test fun diurnoDiviso() {
        val plan = Plan("c", Shifts.continuity(), listOf(Person(io), Person(sara)), emptyList())
        val sun = LocalDate.of(2026, 11, 22)
        val e = Entry(sun, "F", listOf(io, sara), split = LocalTime.of(13, 0))
        val s = Shifts.slots(plan, e)
        assertEquals(listOf(5L * 60, 7L * 60), s.map { it.minutes })
        assertEquals(listOf(io, sara), s.map { it.person })
    }

    @Test fun controlliDelloScambio() {
        val plan = ward()
        val busy = Shifts.allSlots(plan, listOf(Entry(mon, "P", listOf(io))))
        val n = Shifts.ward().first { it.code == "N" }
        val m = Shifts.ward().first { it.code == "M" }
        // Pomeriggio fino alle 21, notte dalle 21: si sovrappone (stesso istante = 0 ore di riposo).
        assertTrue(Problem.Rest in Shifts.problems(plan, io, mon, "N", n.begin(mon), n.finish(mon), busy, null))
        // Mattina del giorno dopo alle 7: solo 10 ore.
        val tue = mon.plusDays(1)
        assertEquals(setOf(Problem.Rest), Shifts.problems(plan, io, tue, "M", m.begin(tue), m.finish(tue), busy, null))
        val wed = mon.plusDays(2)
        assertEquals(emptySet<Problem>(), Shifts.problems(plan, io, wed, "M", m.begin(wed), m.finish(wed), busy, null))
        val off = Avail(io, off = listOf(Off(wed, wed, setOf("M"))))
        assertEquals(setOf(Problem.Unavailable), Shifts.problems(plan, io, wed, "M", m.begin(wed), m.finish(wed), busy, off))
    }

    @Test fun controlloPrimaDiChiedere() {
        val tue = mon.plusDays(1); val sat = mon.plusDays(5)
        val mine = Entry(tue, "M", listOf(elena)); val theirs = Entry(sat, "P", listOf(io))
        val plan = ward(entries = listOf(mine, theirs, Entry(mon, "P", listOf(io))))
        // Io lunedì pomeriggio fino alle 21: martedì alle 7 sono 10 ore, non va.
        assertEquals(setOf(Problem.Rest), Shifts.check(plan, plan.entries, null, io, mine, elena, theirs))
        // Elena sabato pomeriggio: nessun problema.
        assertEquals(emptySet<Problem>(), Shifts.check(plan, plan.entries, null, elena, theirs, io, mine))
        // Chi è già nel turno non può prenderlo.
        assertEquals(setOf(Problem.Overlap), Shifts.check(plan, plan.entries, null, elena, mine, elena))
    }

    @Test fun scambiCessioniStraordinariEAnnulla() {
        val tue = mon.plusDays(1); val sat = mon.plusDays(5)
        val plan = ward(entries = listOf(Entry(tue, "M", listOf(elena)), Entry(sat, "P", listOf(io)), Entry(mon, "N", listOf(luca))))
        val swap = Change("1", Kind.Swap, elena, tue, "M", to = io, withDate = sat, withType = "P", time = 1)
        val give = Change("2", Kind.Give, luca, mon, "N", time = 2)
        val extra = Change("3", Kind.Extra, io, sat, "P", minutes = 45, reason = "Consegne", time = 3)
        var eff = Shifts.effective(plan, listOf(swap, give, extra), emptyList())
        assertEquals(listOf(State.Waiting, State.Waiting, State.Accepted), eff.changes.map { it.state })
        eff = Shifts.effective(plan, listOf(swap, give), listOf(Reply("1", io, true, time = 4), Reply("2", sara, true, time = 5)))
        assertEquals(listOf(io), eff.entries.first { it.date == tue }.people)
        assertEquals(listOf(elena), eff.entries.first { it.date == sat }.people)
        assertEquals(listOf(sara), eff.entries.first { it.date == mon && it.type == "N" }.people)
        // Una risposta di chi non è il destinatario non vale.
        eff = Shifts.effective(plan, listOf(swap), listOf(Reply("1", sara, true)))
        assertEquals(State.Waiting, eff.changes.single().state)
        // Annullato da chi l'ha chiesto: il turno torna com'era.
        eff = Shifts.effective(plan, listOf(swap), listOf(Reply("1", io, true, time = 4), Reply("1", elena, false, undo = true, time = 6)))
        assertEquals(State.Undone, eff.changes.single().state)
        assertEquals(listOf(elena), eff.entries.first { it.date == tue }.people)
        // Straordinario sul proprio turno: vale subito.
        eff = Shifts.effective(plan, listOf(extra), emptyList())
        assertEquals(listOf(extra), eff.extras)
        val h = Shifts.hours(plan, eff, YearMonth.of(2026, 11)).first { it.person == io }
        assertEquals(45L, h.extra)
        assertEquals(7L * 60, h.worked)
    }

    @Test fun ripetiLaSettimana() {
        val plan = ward(entries = (0..6).map { Entry(mon.plusDays(it.toLong()), "M", listOf(four[it % 4])) })
        val out = Shifts.repeatLastWeek(plan, mon.plusDays(7), 2)
        assertEquals(21, out.size)
        assertEquals(listOf(io), out.first { it.date == mon.plusDays(14) }.people)
    }

    @Test fun messaggiAndataERitorno() {
        val plan = Plan("p", Shifts.continuity(), listOf(Person(io, target = 9000), Person(elena, Role.Substitute, replaces = io, from = mon, to = mon.plusDays(3))),
            listOf(Entry(mon, "N", listOf(io, elena), phone = elena, locked = true), Entry(mon.plusDays(6), "F", listOf(io, elena), split = LocalTime.of(13, 0))),
            holidays = true, localHolidays = listOf(java.time.MonthDay.of(8, 24)), time = 9)
        val back = Shifts.plan(JSONObject(Shifts.content(plan, "Turni").toString()), 9)
        assertEquals(plan, back)
        val a = Avail(sara, listOf(Off(mon, mon.plusDays(2), setOf("N"))), setOf("M"), setOf("N"), setOf(7), 3)
        assertEquals(a, Shifts.avail(JSONObject(Shifts.content(a.copy(person = "falso"), "x").toString()), sara, 3))
        val c = Change("1", Kind.Swap, elena, mon, "M", to = io, withDate = mon.plusDays(5), withType = "P", time = 4)
        assertEquals(c, Shifts.change(Shifts.content(c, "x"), elena, 4))
        // Uno scambio con sé stesso o uno straordinario non a passi di 15 minuti non valgono.
        assertNull(Shifts.change(Shifts.content(c.copy(to = elena), "x"), elena, 4))
        assertNull(Shifts.change(Shifts.content(Change("2", Kind.Extra, io, mon, "N", minutes = 20), "x"), io, 4))
        val r = Reply("1", io, true, time = 5)
        assertEquals(r, Shifts.reply(Shifts.content(r, "x"), io, 5))
    }

    @Test fun durate() {
        assertEquals("7 h", Shifts.duration(420))
        assertEquals("1 h 30", Shifts.duration(90))
        assertEquals("45 min", Shifts.duration(45))
        assertEquals(mon, Shifts.mondayOf(mon.plusDays(6)))
    }
}

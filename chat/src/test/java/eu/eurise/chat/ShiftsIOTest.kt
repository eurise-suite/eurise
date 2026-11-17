package eu.eurise.chat

import eu.eurise.chat.Shifts.Entry
import eu.eurise.chat.Shifts.Person
import eu.eurise.chat.Shifts.Plan
import eu.eurise.chat.ShiftsIO.Layout
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftsIOTest {
    private val rossi = "@rossi:x.it"; private val bianchi = "@anna:x.it"; private val verdi = "${ShiftsIO.EXTERNAL}Luca Verdi"
    private val names = mapOf(rossi to "Mario Rossi", bianchi to "Anna Bianchi")
    private val nov = YearMonth.of(2026, 11)
    private val ward = Plan("p", Shifts.ward(), listOf(Person(rossi), Person(bianchi)), emptyList())
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private fun who(i: ShiftsIO.Import, day: Int, type: String) = i.entries.firstOrNull { it.date == d(day) && it.type == type }?.people

    @Test fun date() {
        assertEquals(d(17), ShiftsIO.date("17/11/2026", nov, false))
        assertEquals(d(17), ShiftsIO.date("17/11", nov, false))
        assertEquals(d(17), ShiftsIO.date("2026-11-17", nov, false))
        assertEquals(d(17), ShiftsIO.date("lunedì 17 novembre", nov, false))
        assertEquals(d(17), ShiftsIO.date("lun 17", nov, true))
        assertNull(ShiftsIO.date("lun 17", nov, false))
        assertEquals(LocalDate.of(2027, 3, 17), ShiftsIO.date("mer 17 mar 2027", nov, false))
        assertEquals(d(17), ShiftsIO.date("46343", nov, false)) // numero di serie di Excel
        assertNull(ShiftsIO.date("Rossi", nov, true))
        assertNull(ShiftsIO.date("17 Rossi", nov, true))
    }

    @Test fun csvRighePerPersona() {
        val csv = "Turni novembre;;;;;;;\nNome;1;2;3;4;5;6;7\nMario Rossi;M;M;P;;N;N;-\nBianchi;P;-;M;M;;;\nVerdi;N;N;;X1;;;\nM = Mattina 7-14;;;;;;;\n"
        val table = ShiftsIO.table("turni.csv", csv.toByteArray()) { "" }
        var i = ShiftsIO.read(table, ShiftsIO.Matcher(ward, names), nov)
        assertEquals(Layout.PersonRows, i.layout)
        assertEquals(listOf(rossi), who(i, 1, "M"))
        assertEquals(listOf(rossi), who(i, 5, "N"))
        assertEquals(listOf(bianchi), who(i, 3, "M"))
        assertEquals(listOf("Verdi"), i.unknownNames)
        // Abbinato come persona senza Chat, con la sigla sconosciuta letta come notte.
        i = ShiftsIO.read(table, ShiftsIO.Matcher(ward, names, mapOf("verdi" to verdi), mapOf("x1" to "N")), nov)
        assertEquals(emptyList<String>(), i.unknownNames)
        assertEquals(listOf(verdi), who(i, 4, "N"))
        assertEquals(listOf(verdi), who(i, 1, "N"))
        assertTrue(i.entries.all { it.locked })
    }

    @Test fun pdfARighePerPersona() {
        // Testo come lo estrae un PDF: parole separate da spazi.
        val text = "Reparto B · novembre 2026\nNome lun 2 mar 3 mer 4 gio 5 ven 6 sab 7 dom 8\nRossi M M P P · N N\nBianchi Anna P P · M M P ·\nTotale ore 140\n"
        val i = ShiftsIO.read(ShiftsIO.text(text), ShiftsIO.Matcher(ward, names), nov)
        assertEquals(Layout.PersonRows, i.layout)
        assertEquals(listOf(rossi), who(i, 2, "M"))
        assertEquals(listOf(rossi), who(i, 8, "N"))
        assertEquals(listOf(bianchi), who(i, 5, "M"))
        assertEquals(listOf(bianchi), who(i, 7, "P"))
    }

    @Test fun colonnePerTurno() {
        val csv = "Data;Mattina;Pomeriggio;Notte\n17/11/2026;Rossi;Bianchi;Verdi\n18/11/2026;Bianchi, Rossi;;Rossi\n"
        val i = ShiftsIO.read(ShiftsIO.table("t.csv", csv.toByteArray()) { "" }, ShiftsIO.Matcher(ward, names), nov)
        assertEquals(Layout.TypeColumns, i.layout)
        assertEquals(listOf(rossi), who(i, 17, "M"))
        assertEquals(listOf(bianchi, rossi), who(i, 18, "M"))
        assertEquals(listOf("Verdi"), i.unknownNames)
    }

    @Test fun calendarioDiPresidioARighePerGiorno() {
        // Continuità assistenziale: 1 novembre festivo (diurno e notte), 2 novembre solo notte.
        val ca = Plan("c", Shifts.continuity(), listOf(Person(rossi), Person(bianchi)), emptyList(), holidays = true)
        val i = ShiftsIO.read(ShiftsIO.text("Turni di novembre\n1 dom Rossi Bianchi\n2 lun Bianchi\n"), ShiftsIO.Matcher(ca, names), nov)
        assertEquals(Layout.DayRows, i.layout)
        assertEquals(listOf(rossi), who(i, 1, "F"))
        assertEquals(listOf(bianchi), who(i, 1, "N"))
        assertEquals(listOf(bianchi), who(i, 2, "N"))
    }

    @Test fun righePerGiornoConSiglaEDElenchi() {
        val m = ShiftsIO.Matcher(ward, names)
        var i = ShiftsIO.read(ShiftsIO.text("17/11 M Rossi N Bianchi\n18/11 Rossi P, Bianchi N\n"), m, nov)
        assertEquals(listOf(rossi), who(i, 17, "M")); assertEquals(listOf(bianchi), who(i, 17, "N"))
        assertEquals(listOf(rossi), who(i, 18, "P")); assertEquals(listOf(bianchi), who(i, 18, "N"))
        // Elenco: persona; data; turno (in qualunque ordine).
        i = ShiftsIO.read(ShiftsIO.table("e.csv", "Persona;Data;Turno\nMario Rossi;19/11/2026;P\nAnna Bianchi;19/11/2026;Notte\n".toByteArray()) { "" }, m, nov)
        assertEquals(listOf(rossi), who(i, 19, "P")); assertEquals(listOf(bianchi), who(i, 19, "N"))
    }

    private fun zip(files: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test fun xlsxEOds() {
        val shared = "<sst><si><t>Nome</t></si><si><t>Mario Rossi</t></si><si><t>M</t></si><si><r><t>N</t></r></si></sst>"
        val sheet = "<worksheet><sheetData>" +
            "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\"><v>46343</v></c><c r=\"C1\"><v>46344</v></c><c r=\"D1\"><v>46345</v></c><c r=\"E1\"><v>46346</v></c><c r=\"F1\"><v>46347</v></c></row>" +
            "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>1</v></c><c r=\"B2\" t=\"s\"><v>2</v></c><c r=\"D2\" t=\"inlineStr\"><is><t>P</t></is></c><c r=\"F2\" t=\"s\"><v>3</v></c></row>" +
            "</sheetData></worksheet>"
        val x = ShiftsIO.table("t.xlsx", zip(mapOf("xl/sharedStrings.xml" to shared, "xl/worksheets/sheet1.xml" to sheet))) { "" }
        val i = ShiftsIO.read(x, ShiftsIO.Matcher(ward, names), nov)
        assertEquals(listOf(rossi), who(i, 17, "M"))
        assertEquals(null, who(i, 18, "M"))
        assertEquals(listOf(rossi), who(i, 19, "P"))
        assertEquals(listOf(rossi), who(i, 21, "N"))
        val content = "<office:document-content><office:body><office:spreadsheet><table:table table:name=\"Turni\">" +
            "<table:table-row><table:table-cell><text:p>Nome</text:p></table:table-cell>" +
            (17..21).joinToString("") { "<table:table-cell office:value-type=\"date\" office:date-value=\"2026-11-$it\"><text:p>$it/11</text:p></table:table-cell>" } +
            "</table:table-row><table:table-row><table:table-cell><text:p>Bianchi</text:p></table:table-cell>" +
            "<table:table-cell table:number-columns-repeated=\"2\"><text:p>N</text:p></table:table-cell><table:table-cell/><table:table-cell><text:p>M</text:p></table:table-cell>" +
            "</table:table-row></table:table></office:spreadsheet></office:body></office:document-content>"
        val o = ShiftsIO.read(ShiftsIO.table("t.ods", zip(mapOf("content.xml" to content))) { "" }, ShiftsIO.Matcher(ward, names), nov)
        assertEquals(listOf(bianchi), who(o, 17, "N"))
        assertEquals(listOf(bianchi), who(o, 18, "N"))
        assertEquals(listOf(bianchi), who(o, 20, "M"))
    }

    @Test fun pdfPassaDalLettore() {
        val t = ShiftsIO.table("x.pdf", "%PDF-1.4 ...".toByteArray()) { "Nome 1 2 3 4 5\nRossi M P N · ·" }
        assertEquals(listOf(rossi), who(ShiftsIO.read(t, ShiftsIO.Matcher(ward, names), nov), 3, "N"))
    }

    @Test fun unireOSostituire() {
        val plan = ward.copy(entries = listOf(Entry(d(1), "M", listOf(bianchi)), Entry(d(20), "M", listOf(bianchi)), Entry(d(25), "N", listOf(rossi))))
        val imported = listOf(Entry(d(20), "M", listOf(rossi), locked = true), Entry(d(22), "P", listOf(rossi), locked = true))
        val replaced = ShiftsIO.merge(plan, imported, replace = true)
        assertEquals(listOf(rossi), replaced.first { it.date == d(20) }.people)
        assertTrue(replaced.any { it.date == d(1) } && replaced.any { it.date == d(25) })
        val added = ShiftsIO.merge(plan, imported, replace = false)
        assertEquals(listOf(bianchi, rossi), added.first { it.date == d(20) }.people)
    }

    @Test fun esportare() {
        val plan = ward.copy(title = "Reparto B", people = ward.people + Person(verdi),
            entries = listOf(Entry(d(17), "M", listOf(rossi)), Entry(d(17), "N", listOf(bianchi, verdi), phone = verdi), Entry(d(18), "P", listOf(rossi))))
        val name = { id: String -> names[id] ?: ShiftsIO.display(id) }
        val grid = ShiftsIO.csvGrid(plan, plan.entries, nov, name)
        assertTrue(grid.startsWith("﻿Reparto B\r\nNome;"))
        val row = grid.lines().first { it.startsWith("Mario Rossi;") }.split(';')
        assertEquals("M", row[17]); assertEquals("P", row[18]); assertEquals("14 h", row.last())
        assertTrue(grid.contains("Luca Verdi;"))
        val extra = Shifts.Change("e", Shifts.Kind.Extra, rossi, d(17), "M", minutes = 30, reason = "Consegne")
        val list = ShiftsIO.csvList(plan, plan.entries, listOf(extra), nov, name)
        assertTrue(list.lines().any { it.startsWith("2026-11-17;") && "Mario Rossi" in it && "30 min" in it && "Consegne" in it })
        val ics = ShiftsIO.ics(plan, plan.entries, d(1), "Reparto B", name, null, ZoneId.of("Europe/Rome"), Instant.parse("2026-11-17T10:00:00Z"))
        assertEquals(4, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue("DTSTART:20261117T060000Z" in ics) // 7:00 a Roma
        assertTrue(ics.split("\r\n").all { it.toByteArray().size <= 75 })
        val mine = ShiftsIO.ics(plan, plan.entries, d(1), "", name, rossi, ZoneId.of("Europe/Rome"), Instant.EPOCH)
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(mine).count())
        val p = ShiftsIO.presence(plan, plan.entries, listOf(extra), nov, rossi)
        assertEquals(2, p.size)
        assertEquals(30L, p[0].extra)
        assertEquals(LocalTime.of(14, 30), p[0].end.toLocalTime())
    }
}

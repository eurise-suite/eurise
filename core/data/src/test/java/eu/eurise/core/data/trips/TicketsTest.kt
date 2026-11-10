package eu.eurise.core.data.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.Deflater

class TicketsTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test fun `carta d'imbarco IATA`() {
        // Un tratto, volo AZ 2041 da Fiumicino a Linate il giorno 320 (16 novembre), posto 12A.
        val raw = "M1ROSSI/MARIO         EABC123 FCOLINAZ 2041 320Y012A0045 100"
        val r = Bcbp.parse(raw, today)!!
        assertEquals("ABC123", r.booking)
        assertEquals("ROSSI MARIO", r.passenger)
        val leg = r.legs.single()
        assertEquals("AZ 2041", leg.number)
        assertEquals("Roma (FCO)", leg.from)
        assertEquals("Milano (LIN)", leg.to)
        assertEquals(LocalDate.of(2026, 11, 16), leg.depart?.toLocalDate())
        assertEquals("12A", leg.seat)
    }

    @Test fun `un volo dal testo di una conferma`() {
        val text = """
            Conferma di prenotazione
            Codice di prenotazione: XK7P2Q
            Volo FR 1234  Bergamo (BGY) - Barcellona (BCN)
            Venerdì 20/11/2026  partenza 06:35  arrivo 08:20
        """.trimIndent()
        val r = Tickets.read(emptyList(), text, today)
        assertEquals("XK7P2Q", r.booking)
        val leg = r.legs.single()
        assertEquals("FR 1234", leg.number)
        assertEquals(LocalDateTime.of(2026, 11, 20, 6, 35), leg.depart)
        assertEquals(LocalDateTime.of(2026, 11, 20, 8, 20), leg.arrive)
        assertEquals("Europe/Madrid", leg.arriveZone)
    }

    @Test fun `un treno dal testo del biglietto`() {
        val text = """
            Biglietto Frecciarossa 9520
            Da Roma Termini a Milano Centrale, 12 dic 2026
            Partenza 07:30 Arrivo 10:40
        """.trimIndent()
        val leg = Tickets.read(emptyList(), text, today).legs.single()
        assertEquals(Leg.Kind.Train, leg.kind)
        assertEquals("Frecciarossa 9520", leg.number)
        assertEquals("Roma Termini", leg.from)
        assertEquals("Milano Centrale", leg.to)
        assertEquals(LocalDateTime.of(2026, 12, 12, 7, 30), leg.depart)
    }

    @Test fun `il testo di un pdf compresso con mappa dei caratteri`() {
        // Pagina con un font Type0 (codici a due byte) e una ToUnicode, contenuto compresso.
        val cmap = """
            /CIDInit /ProcSet findresource begin
            begincmap
            1 begincodespacerange <0000> <FFFF> endcodespacerange
            2 beginbfchar <0001> <0056> <0002> <006F> endbfchar
            1 beginbfrange <0003> <0005> <006C> endbfrange
            endcmap
        """.trimIndent()
        // 0001=V 0002=o 0003=l 0004=m 0005=n → "Vol" ; poi testo latino semplice.
        val content = "BT /F1 12 Tf 10 700 Td <000100020003> Tj 0 -14 Td /F2 10 Tf (AZ 2041 FCO-LIN) Tj ET"
        val pdf = buildPdf(content, cmap)
        val text = PdfText.extract(pdf)
        assertTrue(text, text.startsWith("Vol"))
        assertTrue(text, "AZ 2041 FCO-LIN" in text)
        assertEquals(2, text.lines().size)
    }

    private fun deflate(s: String): ByteArray {
        val d = Deflater()
        d.setInput(s.toByteArray(Charsets.ISO_8859_1)); d.finish()
        val out = ByteArrayOutputStream(); val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        return out.toByteArray()
    }

    private fun buildPdf(content: String, cmap: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        w("%PDF-1.5\n")
        w("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
        w("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 /Resources << /Font << /F1 5 0 R /F2 7 0 R >> >> >> endobj\n")
        w("3 0 obj << /Type /Page /Parent 2 0 R /Contents 4 0 R >> endobj\n")
        val data = deflate(content)
        w("4 0 obj << /Length ${data.size} /Filter /FlateDecode >>\nstream\n"); out.write(data); w("\nendstream\nendobj\n")
        w("5 0 obj << /Type /Font /Subtype /Type0 /BaseFont /X /ToUnicode 6 0 R >> endobj\n")
        w("6 0 obj << /Length ${cmap.length} >>\nstream\n$cmap\nendstream\nendobj\n")
        w("7 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n")
        w("trailer << /Root 1 0 R >>\n%%EOF\n")
        return out.toByteArray()
    }
}

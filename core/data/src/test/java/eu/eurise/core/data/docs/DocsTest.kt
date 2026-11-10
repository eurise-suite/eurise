package eu.eurise.core.data.docs

import eu.eurise.core.data.trips.PdfText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class DocsTest {

    private val bolletta = """
        Enel Energia S.p.A.
        Bolletta n. 0040123456 emessa il 03/12/2026
        Fornitura di energia elettrica - Codice POD IT001E12345678
        Periodo di fatturazione: novembre 2026
        Consumi 182 kWh
        Totale da pagare 128,40 €
        da pagare entro il 31/12/2026
        Paga con avviso pagoPA - Codice avviso 3020 0000 1234 5678 90
        oppure bonifico su IBAN IT60 X054 2811 1010 0000 0123 456
    """.trimIndent()

    @Test fun bollettaDellaTela() {
        val f = DocReader.analyze(bolletta)
        assertEquals(DocKind.Electricity, f.kind)
        assertEquals("Enel", f.issuer)
        assertEquals(YearMonth.of(2026, 11), f.month)
        assertEquals(LocalDate.of(2026, 12, 31), f.deadline?.value)
        assertTrue(f.deadline!!.quote.contains("entro il 31/12/2026"))
        assertEquals(BigDecimal("128.40"), f.amount?.value)
        assertTrue(f.amount!!.quote.startsWith("Totale da pagare"))
        assertEquals("IT60 X054 2811 1010 0000 0123 456", f.iban)
        assertEquals("3020 0000 1234 5678 90", f.notice)
        assertEquals("Bolletta luce Enel · novembre 2026", f.title)
        assertEquals("2026-11 Bolletta luce Enel", f.fileName)
        assertEquals("Casa/Bollette", f.folder)
        assertEquals("128,40 €", DocFacts.formatAmount(f.amount!!.value))
    }

    @Test fun fatturaGenerica() {
        val text = """
            Studio Rossi - Partita IVA 01234567890
            Fattura n. 12 del 5 marzo 2026
            Imponibile 1.250,00 €  Aliquota IVA 22%  IVA 275,00 €
            Totale fattura € 1.525,00
            Scadenza: 04.04.2026
        """.trimIndent()
        val f = DocReader.analyze(text)
        assertEquals(DocKind.Invoice, f.kind)
        assertEquals(BigDecimal("1525.00"), f.amount?.value)
        assertEquals(LocalDate.of(2026, 4, 4), f.deadline?.value)
        assertEquals(YearMonth.of(2026, 3), f.month)
        assertNull(f.iban)
    }

    @Test fun senzaSegniNienteProposte() {
        val f = DocReader.analyze("Caro Marco, ci vediamo il 12/05/2026 al solito posto.")
        assertNull(f.kind)
        assertNull(f.title)
        assertNull(f.deadline)
        assertNull(f.amount)
    }

    @Test fun ibanSbagliatoScartato() {
        assertNull(DocReader.iban("IBAN IT61 X054 2811 1010 0000 0123 456"))
        assertTrue(DocReader.ibanValid("IT60X0542811101000000123456"))
    }

    @Test fun paroleDaTsv() {
        val tsv = listOf(
            "1\t1\t0\t0\t0\t0\t0\t0\t1000\t800\t-1\t",
            "4\t1\t1\t1\t1\t0\t10\t20\t300\t40\t-1\t",
            "5\t1\t1\t1\t1\t1\t10\t20\t120\t40\t96.5\tTotale",
            "5\t1\t1\t1\t1\t2\t140\t22\t60\t38\t91\t128,40",
            "5\t1\t1\t1\t2\t1\t10\t80\t80\t30\t90\tentro",
        ).joinToString("\n")
        val words = OcrWords.fromTsv(tsv)
        assertEquals(listOf("Totale", "128,40", "entro"), words.map { it.text })
        assertEquals(words[0].line, words[1].line)
        assertTrue(words[0].line != words[2].line)
        assertEquals(130, words[0].right)
        assertEquals(96.5f, words[0].confidence)
    }

    @Test fun pdfCercabileSiRilegge() {
        val words = listOf(
            OcrWord("Totale", 100, 100, 260, 140, 1, 95f),
            OcrWord("da", 270, 100, 310, 140, 1, 95f),
            OcrWord("pagare", 320, 100, 470, 140, 1, 95f),
            OcrWord("128,40", 480, 100, 620, 140, 1, 95f),
            OcrWord("€", 630, 100, 660, 140, 1, 95f),
            OcrWord("Perché", 100, 200, 260, 240, 2, 95f),
            OcrWord("(città)", 270, 200, 430, 240, 2, 95f),
        )
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val out = ByteArrayOutputStream()
        SearchablePdf.write(listOf(SearchablePdf.Page(jpeg, 1240, 1754, words), SearchablePdf.Page(jpeg, 1240, 1754, emptyList())), out, "Prova")
        val pdf = out.toByteArray()
        val text = PdfText.extract(pdf)
        assertEquals("Totale da pagare 128,40 €\nPerché (città)", text)
        val s = String(pdf, Charsets.ISO_8859_1)
        assertTrue(s.startsWith("%PDF-1.4"))
        assertTrue(s.contains("/Count 2"))
        // La tabella xref punta davvero agli oggetti.
        val startxref = s.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertTrue(s.startsWith("xref", startxref))
        val first = s.substring(startxref).lines()[3].substring(0, 10).toInt()
        assertTrue(s.startsWith("1 0 obj", first))
    }

    @Test fun misureDalJpeg() {
        // Intestazione minima: SOI, APP0 corto, SOF0 con altezza 1754, larghezza 1240, 3 componenti.
        val jpeg = intArrayOf(
            0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x04, 0x00, 0x00,
            0xFF, 0xC0, 0x00, 0x11, 0x08, 0x06, 0xDA, 0x04, 0xD8, 0x03, 0x01, 0x22, 0x00,
        ).map { it.toByte() }.toByteArray()
        assertEquals(Triple(1240, 1754, 3), SearchablePdf.jpegInfo(jpeg))
        val page = SearchablePdf.pageFromJpeg(jpeg, listOf(OcrWord("a", 10, 20, 30, 40, 1, 90f)), 620)!!
        assertEquals(20, page.words[0].left)
        assertEquals(80, page.words[0].bottom)
        assertNull(SearchablePdf.jpegInfo(byteArrayOf(1, 2, 3, 4)))
    }

    @Test fun larghezzaParolaRispettata() {
        assertEquals(556 * 2, SearchablePdf.textWidth("ab"))
        assertEquals("(a\\(b\\)\\\\)", SearchablePdf.literal("a(b)\\"))
        assertEquals("(\\200)", SearchablePdf.literal("€"))
    }
}

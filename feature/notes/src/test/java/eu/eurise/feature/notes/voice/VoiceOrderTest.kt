package eu.eurise.feature.notes.voice

import eu.eurise.feature.notes.doc.AudioBlock
import eu.eurise.feature.notes.doc.Block
import eu.eurise.feature.notes.doc.NoteHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceOrderTest {

    private val raw = listOf(
        "allora per il viaggio a Lisbona prima cosa i voli conviene partire venerdì mattina da Fiumicino costano meno",
        "poi l'albergo",
        "Marco dice zona Alfama però è in salita quindi forse meglio Baixa",
        "poi le cose da portare adattatore no non serve stessa presa crema solare e le scarpe comode",
        "ok",
    ).joinToString("\n")

    @Test
    fun localParagraphsJoinShortPiecesAndEndWithAFullStop() {
        val p = VoiceOrder.paragraphs(raw)
        assertEquals(3, p.size)
        assertTrue(p[0].startsWith("Allora per il viaggio"))
        assertTrue(p[1].startsWith("Poi l'albergo Marco dice"))
        // L'ultimo pezzo cortissimo («ok») va con il paragrafo prima.
        assertTrue(p[2].endsWith("scarpe comode ok."))
        p.forEach { assertTrue(it.last() == '.') }
    }

    @Test
    fun tidyFixesSpacesAndCapital() {
        assertEquals("Ciao, come va?", VoiceOrder.tidy("  ciao ,  come va?"))
        assertEquals("Domani alle dieci.", VoiceOrder.tidy("domani alle dieci"))
    }

    @Test
    fun cleanAnswerIsRefusedWhenItSummarises() {
        val original = listOf("allora per il viaggio a Lisbona prima cosa i voli conviene partire venerdì mattina da Fiumicino costano meno")
        assertNull(VoiceOrder.acceptClean(original, "Voli da Fiumicino."))
        val ok = VoiceOrder.acceptClean(original, "Allora, per il viaggio a Lisbona, prima cosa i voli: conviene partire venerdì mattina da Fiumicino, costano meno.")
        assertEquals(1, ok?.size)
    }

    @Test
    fun cleanAnswerKeepsParagraphCountOrIsRefused() {
        val original = listOf("uno due tre quattro cinque sei", "sette otto nove dieci undici dodici")
        assertNull(VoiceOrder.acceptClean(original, "Uno, due, tre, quattro, cinque, sei, sette, otto, nove, dieci, undici, dodici."))
        assertEquals(2, VoiceOrder.acceptClean(original, "Ecco il testo:\nUno, due, tre, quattro, cinque, sei.\nSette, otto, nove, dieci, undici, dodici.")?.size)
    }

    @Test
    fun structureReadsTitleAndHeadings() {
        val paragraphs = listOf("Voli il venerdì.", "Albergo in Baixa.", "Crema solare.")
        val o = VoiceOrder.structure(paragraphs, "TITOLO: «Viaggio a Lisbona»\n1: Voli\n2: Albergo\n3: Cose da portare\n9: fuori")
        assertEquals("Viaggio a Lisbona", o.title)
        assertEquals(listOf("Voli", "Albergo", "Cose da portare"), o.sections.map { it.heading })
        assertEquals(paragraphs, o.sections.flatMap { it.paragraphs })
    }

    @Test
    fun aSingleHeadingOnTwoParagraphsIsDropped() {
        val o = VoiceOrder.structure(listOf("Uno.", "Due."), "TITOLO: Prova\n1: Tutto")
        assertEquals(listOf<String?>(null), o.sections.map { it.heading })
    }

    @Test
    fun orderedTextRoundTrips() {
        val o = OrderedText("T", listOf(OrderedText.Section(null, listOf("a.")), OrderedText.Section("Voli", listOf("b.", "c."))))
        assertEquals(o.copy(title = ""), OrderedText.parse(o.encode()))
    }

    @Test
    fun summaryLinesWithOrWithoutDashes() {
        assertEquals(listOf("Volo venerdì.", "Albergo in Baixa."), VoiceOrder.summaryLines("- volo venerdì\n- Albergo in Baixa."))
        assertEquals(2, VoiceOrder.summaryLines("Volo venerdì. Albergo in Baixa.").size)
    }

    @Test
    fun audioBlockKeepsPlaceOrderAndSummaryInHtml() {
        var next = 0L
        val block = AudioBlock(
            1, "a.m4a", 227_000, transcript = "primo pezzo\nsecondo pezzo", place = "cloud", uri = "Note vocali/a.m4a",
            size = 3_100_000, ordered = "## Voli\nConviene partire venerdì.", summary = "Volo venerdì.\nAlbergo in Baixa.",
        )
        val read = NoteHtml.read(NoteHtml.write(listOf<Block>(block))) { ++next }
        assertEquals(listOf(block.copy(id = 0)), read.map { (it as AudioBlock).copy(id = 0) })
    }
}

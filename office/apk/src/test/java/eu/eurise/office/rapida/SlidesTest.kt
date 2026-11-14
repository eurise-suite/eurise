package eu.eurise.office.rapida

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidesTest {
    private fun zip(vararg entries: Pair<String, String>): Bundle {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (name, text) -> z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() } }
        return Bundle.read(out.toByteArray())
    }

    private val ns = """xmlns:p="${Pptx.P}" xmlns:a="${Pptx.A}" xmlns:r="${Pptx.R}""""
    private fun rels(vararg r: Triple<String, String, String>) =
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            r.joinToString("") { (id, type, target) -> """<Relationship Id="$id" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/$type" Target="$target"/>""" } +
            "</Relationships>"

    private fun pptx(): Bundle = zip(
        "ppt/presentation.xml" to """<p:presentation $ns><p:sldIdLst><p:sldId id="257" r:id="rId3"/><p:sldId id="256" r:id="rId2"/></p:sldIdLst><p:sldSz cx="12192000" cy="6858000"/></p:presentation>""",
        "ppt/_rels/presentation.xml.rels" to rels(Triple("rId2", "slide", "slides/slide1.xml"), Triple("rId3", "slide", "slides/slide2.xml")),
        // Nell'ordine di sldIdLst slide2 viene prima.
        "ppt/slides/slide2.xml" to """<p:sld $ns><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val="1F2A44"/></a:solidFill></p:bgPr></p:bg><p:spTree>
            <p:sp><p:nvSpPr><p:cNvPr id="2" name="T"/><p:cNvSpPr/><p:nvPr><p:ph type="ctrTitle"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>Ristrutturazione</a:t></a:r><a:r><a:t> bagno</a:t></a:r></a:p></p:txBody></p:sp>
            <p:sp><p:nvSpPr><p:cNvPr id="3" name="S"/><p:cNvSpPr/><p:nvPr><p:ph type="subTitle" idx="1"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>Avanzamento lavori</a:t></a:r></a:p></p:txBody></p:sp>
            </p:spTree></p:cSld></p:sld>""",
        "ppt/slides/slide1.xml" to """<p:sld $ns show="0"><p:cSld><p:spTree>
            <p:pic><p:nvPicPr><p:cNvPr id="5" name="Immagine 4" descr="Foto del bagno"/></p:nvPicPr><p:blipFill><a:blip r:embed="rId4"/></p:blipFill><p:spPr><a:xfrm><a:off x="8000000" y="1500000"/></a:xfrm></p:spPr></p:pic>
            <p:sp><p:nvSpPr><p:cNvPr id="3" name="B"/><p:cNvSpPr/><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x="0" y="1400000"/></a:xfrm></p:spPr><p:txBody><a:p><a:r><a:rPr b="1"/><a:t>Demolizioni</a:t></a:r><a:r><a:t> finite</a:t></a:r></a:p><a:p><a:pPr lvl="1"/><a:r><a:t>il 6 novembre</a:t></a:r></a:p><a:p><a:pPr><a:buNone/></a:pPr><a:r><a:t>Senza punto</a:t></a:r></a:p><a:p/></p:txBody></p:sp>
            <p:sp><p:nvSpPr><p:cNvPr id="2" name="T"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>A che punto siamo</a:t></a:r></a:p></p:txBody></p:sp>
            <p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="6" name="Tab"/></p:nvGraphicFramePr><p:xfrm><a:off x="0" y="4000000"/></p:xfrm><a:graphic><a:graphicData><a:tbl><a:tr><a:tc><a:txBody><a:p><a:r><a:t>Voce</a:t></a:r></a:p></a:txBody></a:tc><a:tc><a:txBody><a:p><a:r><a:t>Costo</a:t></a:r></a:p></a:txBody></a:tc></a:tr><a:tr><a:tc><a:txBody><a:p><a:r><a:t>Idraulico</a:t></a:r></a:p></a:txBody></a:tc><a:tc><a:txBody><a:p><a:r><a:t>2.850,00 €</a:t></a:r></a:p></a:txBody></a:tc></a:tr></a:tbl></a:graphicData></a:graphic></p:graphicFrame>
            <p:sp><p:nvSpPr><p:cNvPr id="9" name="N"/><p:cNvSpPr/><p:nvPr><p:ph type="sldNum"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>2</a:t></a:r></a:p></p:txBody></p:sp>
            </p:spTree></p:cSld></p:sld>""",
        "ppt/slides/_rels/slide1.xml.rels" to rels(Triple("rId4", "image", "../media/image1.png"), Triple("rId5", "notesSlide", "../notesSlides/notesSlide1.xml")),
        "ppt/notesSlides/notesSlide1.xml" to """<p:notes $ns><p:cSld><p:spTree><p:sp><p:nvSpPr><p:cNvPr id="1" name="I"/><p:cNvSpPr/><p:nvPr><p:ph type="sldImg"/></p:nvPr></p:nvSpPr></p:sp><p:sp><p:nvSpPr><p:cNvPr id="2" name="N"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>Ricordare il colore delle fughe.</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:notes>""",
        "ppt/media/image1.png" to "png",
    )

    @Test fun pptxOrderTitleAndRatio() {
        val deck = Pptx.read(pptx())
        assertEquals(2, deck.slides.size)
        assertEquals(12192000f / 6858000f, deck.ratio, 0.001f)
        val cover = deck.slides[0]
        assertEquals("Ristrutturazione bagno", cover.title)
        assertEquals("Avanzamento lavori", cover.subtitle)
        assertEquals(0x1F2A44, cover.background)
        assertTrue(cover.shapes.isEmpty())
    }

    @Test fun pptxBodyBulletsImageTableNotes() {
        val slide = Pptx.read(pptx()).slides[1]
        assertEquals("A che punto siamo", slide.title)
        assertTrue(slide.hidden)
        // Ordinate dall'alto: testo (1.4M), immagine (1.5M), tabella (4M); il numero di pagina non c'è.
        val text = slide.shapes[0] as SlideText
        assertEquals(3, text.paras.size)
        assertEquals("Demolizioni finite", text.paras[0].text)
        assertTrue(text.paras[0].spans[0].bold)
        assertFalse(text.paras[0].spans[1].bold)
        assertTrue(text.paras[0].bullet)
        assertEquals(1, text.paras[1].level)
        assertFalse(text.paras[2].bullet)
        val image = slide.shapes[1] as SlideImage
        assertEquals("ppt/media/image1.png", image.path)
        assertEquals("Foto del bagno", image.name)
        val table = slide.shapes[2] as SlideTable
        assertEquals(listOf("Idraulico", "2.850,00 €"), table.rows[1])
        assertEquals(3, slide.shapes.size)
        assertEquals("Ricordare il colore delle fughe.", slide.notes)
        assertTrue(slide.text.contains("fughe"))
    }

    private val odpNs = """xmlns:office="${Odt.OFFICE}" xmlns:text="${Odt.TEXT}" xmlns:table="${Odt.TABLE}" xmlns:style="${Odt.STYLE}" xmlns:fo="${Odt.FO}" xmlns:draw="${Odp.DRAW}" xmlns:presentation="${Odp.PRESENTATION}" xmlns:svg="${Odp.SVG}" xmlns:xlink="${Odp.XLINK}""""

    private fun odp(): Bundle = zip(
        "mimetype" to "application/vnd.oasis.opendocument.presentation",
        "content.xml" to """<office:document-content $odpNs><office:automatic-styles>
            <style:style style:name="dp1" style:family="drawing-page"><style:drawing-page-properties draw:fill="solid" draw:fill-color="#1f2a44"/></style:style>
            <style:style style:name="T1" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style>
            </office:automatic-styles><office:body><office:presentation>
            <draw:page draw:name="p1" draw:style-name="dp1"><draw:frame presentation:class="title" svg:y="2cm"><draw:text-box><text:p>Benvenuti</text:p></draw:text-box></draw:frame></draw:page>
            <draw:page draw:name="p2">
              <draw:frame presentation:class="graphic" svg:y="3cm"><draw:image xlink:href="Pictures/foto.jpg"/><svg:desc>Il cantiere</svg:desc></draw:frame>
              <draw:frame presentation:class="outline" svg:y="2.5cm"><draw:text-box><text:list><text:list-item><text:p>Primo <text:span text:style-name="T1">punto</text:span></text:p><text:list><text:list-item><text:p>sotto</text:p></text:list-item></text:list></text:list-item></text:list></draw:text-box></draw:frame>
              <draw:frame presentation:class="title" svg:y="1cm"><draw:text-box><text:p>Programma</text:p></draw:text-box></draw:frame>
              <draw:frame presentation:class="page-number" svg:y="18cm"><draw:text-box><text:p>2</text:p></draw:text-box></draw:frame>
              <presentation:notes><draw:page-thumbnail/><draw:frame presentation:class="notes"><draw:text-box><text:p>Parlare piano.</text:p></draw:text-box></draw:frame></presentation:notes>
            </draw:page>
            </office:presentation></office:body></office:document-content>""",
        "styles.xml" to """<office:document-styles $odpNs><office:automatic-styles><style:page-layout style:name="PM1"><style:page-layout-properties fo:page-width="28cm" fo:page-height="15.75cm"/></style:page-layout></office:automatic-styles></office:document-styles>""",
    )

    @Test fun odpPagesTextImageNotes() {
        val deck = Odp.read(odp())
        assertEquals(2, deck.slides.size)
        assertEquals(28f / 15.75f, deck.ratio, 0.001f)
        assertEquals("Benvenuti", deck.slides[0].title)
        assertEquals(0x1F2A44, deck.slides[0].background)
        val s = deck.slides[1]
        assertEquals("Programma", s.title)
        val text = s.shapes[0] as SlideText
        assertEquals("Primo punto", text.paras[0].text)
        assertTrue(text.paras[0].bullet)
        assertTrue(text.paras[0].spans[1].bold)
        assertEquals(1, text.paras[1].level)
        assertEquals("Pictures/foto.jpg", (s.shapes[1] as SlideImage).path)
        assertEquals("Il cantiere", (s.shapes[1] as SlideImage).name)
        assertEquals(2, s.shapes.size)
        assertEquals("Parlare piano.", s.notes)
    }

    @Test fun relativePaths() {
        assertEquals("ppt/media/a.png", Pptx.resolve("ppt/slides", "../media/a.png"))
        assertEquals("ppt/slides/slide1.xml", Pptx.resolve("ppt", "slides/slide1.xml"))
        assertEquals("ppt/x.xml", Pptx.resolve("ppt/slides", "/ppt/x.xml"))
        assertEquals(2.54f, Odp.length("1in")!!, 0.001f)
    }
}

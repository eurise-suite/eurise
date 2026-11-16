package eu.eurise.office.rapida

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OdtTest {
    internal fun odt(body: String): Bundle {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            val mime = "application/vnd.oasis.opendocument.text".toByteArray()
            z.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size; crc = CRC32().apply { update(mime) }.value
            })
            z.write(mime); z.closeEntry()
            val content = """<?xml version="1.0" encoding="UTF-8"?><office:document-content xmlns:office="${Odt.OFFICE}" xmlns:text="${Odt.TEXT}" xmlns:table="${Odt.TABLE}" xmlns:style="${Odt.STYLE}" xmlns:fo="${Odt.FO}" xmlns:dc="${Odt.DC}" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"><office:automatic-styles><style:style style:name="T1" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style><style:style style:name="P1" style:family="paragraph"><style:paragraph-properties fo:text-align="center"/></style:style></office:automatic-styles><office:body><office:text>$body</office:text></office:body></office:document-content>"""
            z.putNextEntry(ZipEntry("content.xml")); z.write(content.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("styles.xml")); z.write("<office:document-styles xmlns:office=\"${Odt.OFFICE}\"/>".toByteArray()); z.closeEntry()
        }
        return Bundle.read(out.toByteArray())
    }

    private val sample = """<text:h text:outline-level="1">Verbale</text:h><text:p text:style-name="P1">Presenti <text:span text:style-name="T1">otto</text:span> condomini.</text:p><text:p>Due<text:s text:c="2"/>spazi<text:tab/>e tab</text:p><table:table><table:table-row><table:table-cell><text:p>Cella</text:p></table:table-cell></table:table-row></table:table><text:p>Con <draw:frame/> figura</text:p><text:list><text:list-item><text:p>Punto</text:p></text:list-item></text:list>"""

    @Test fun reads() {
        val d = Odt.read(odt(sample))
        val ps = d.paragraphs()
        assertEquals(1, ps[0].level)
        assertEquals("Presenti otto condomini.", ps[1].text)
        assertTrue(ps[1].spans[1].bold)
        assertEquals(Align.Center, ps[1].align)
        assertEquals("Due  spazi\te tab", ps[2].text)
        assertEquals("Cella", (d.blocks[3] as Table).rows[0][0][0].text)
        assertFalse(ps[4].editable)
        assertTrue(ps[5].bullet)
    }

    @Test fun editsInPlaceAndKeepsMimetypeStored() {
        val before = odt(sample)
        val after = Odt.edit(before, 1, "Presenti nove condomini.")
        val p = Odt.read(after).paragraphs()[1]
        assertEquals("Presenti nove condomini.", p.text)
        assertTrue(p.spans.first { it.text == "nove" }.bold)
        var b = Odt.edit(after, 2, "Uno  spazio\ne a capo")
        assertEquals("Uno  spazio\ne a capo", Odt.read(b).paragraphs()[2].text)
        b = Odt.edit(b, 3, "Cella cambiata")
        assertEquals("Cella cambiata", Odt.read(b).paragraphs()[3].text)
        ZipInputStream(b.toBytes().inputStream()).use { z ->
            val first = z.nextEntry!!
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
        }
    }

    @Test fun comment() {
        val b = Odt.comment(odt(sample), 1, "Verificare", "Anna")
        assertEquals(listOf("Anna: Verificare"), Odt.read(b).paragraphs()[1].comments)
        assertEquals("Presenti otto condomini.", Odt.read(b).paragraphs()[1].text)
    }
}

class InsertAfterTest {
    @Test fun newParagraphsInBothFormats() {
        val odtDoc = OdtTest().odt("<text:p/>")
        var b = Odt.edit(odtDoc, 0, "Primo")
        b = Odt.insertAfter(b, 0, "Secondo")
        b = Odt.insertAfter(b, 1, "")
        assertEquals(listOf("Primo", "Secondo", ""), Odt.read(b).paragraphs().map { it.text })
    }
}

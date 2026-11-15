package eu.eurise.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class VCardTest {
    @Test
    fun readsSeveralVersions() {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            N:Rossi;Anna;;;
            FN:Anna Rossi
            ORG:Studio Rossi;Amministrazione
            TEL;TYPE=CELL:+39 333 1234567
            TEL;TYPE=WORK:02 1234567
            EMAIL:anna@example.it
            ADR;TYPE=WORK:;;Via Roma 1;Milano;;20100;Italia
            NOTE:Commercialista\, chiamare al mattino
            END:VCARD
            BEGIN:VCARD
            VERSION:2.1
            N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Nicol=C3=B2;Luca;;;
            TEL;CELL:3471112223
            END:VCARD
            BEGIN:VCARD
            VERSION:4.0
            item1.EMAIL:solo@example.org
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            END:VCARD
        """.trimIndent()
        val c = VCard.parse(vcf)
        assertEquals(3, c.size)
        assertEquals("Anna Rossi", c[0].name)
        assertEquals(listOf("+39 333 1234567", "02 1234567"), c[0].phones)
        assertEquals("Studio Rossi", c[0].company)
        assertEquals("Via Roma 1, Milano, 20100, Italia", c[0].address)
        assertEquals("Commercialista, chiamare al mattino", c[0].note)
        assertEquals("Luca Nicolò", c[1].name)
        assertEquals("solo@example.org", c[2].name)
        assertEquals(listOf("solo@example.org"), c[2].emails)
    }
}

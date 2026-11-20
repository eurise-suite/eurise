package eu.eurise.core.suite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpamListTest {
    private val text = """
        # eurise-spam 1
        # commento
        +216	wangiri	Tunisia
        +390844	callcenter
        +39021234567	truffa	finto operatore
        +39	SBAGLIATA ma il numero va
        333 1234567	riga senza più: si salta
    """.trimIndent()

    @Test
    fun leggeLeVoci() {
        val list = SpamList.parse(text)!!
        assertEquals(listOf("216", "390844", "39021234567", "39"), list.map { it.prefix })
        assertEquals("molesto", list[3].category)
        assertNull(SpamList.parse("+216\twangiri"))
    }

    @Test
    fun confrontaIlPrefissoPiuLungo() {
        val list = SpamList.parse(text)!!
        assertEquals("wangiri", SpamList.match("+216 71 000 000", list, "39")?.category)
        assertEquals("wangiri", SpamList.match("0021671000000", list, "39")?.category)
        assertEquals("callcenter", SpamList.match("0844 123 4567", list, "39")?.category)
        assertEquals("truffa", SpamList.match("02 1234567", list, "39")?.category)
        assertEquals("molesto", SpamList.match("333 1234567", list, "39")?.category)
        assertNull(SpamList.match("+49 30 1234", list, "39"))
        assertNull(SpamList.match(null, list, "39"))
    }

    @Test
    fun formaInternazionale() {
        assertEquals("39021234", SpamList.international("02 1234", "39"))
        assertEquals("49301234", SpamList.international("030 1234", "49"))
        assertEquals("216710000", SpamList.international("+216 710 000", "39"))
    }
}

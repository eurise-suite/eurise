package eu.eurise.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class MailTextTest {
    @Test fun tipo() {
        assertEquals(MailKind.Person, MailText.kind("m.rossi@posta.it", false, null, null))
        assertEquals(MailKind.Newsletter, MailText.kind("news@negozio.it", true, null, null))
        assertEquals(MailKind.Notice, MailText.kind("noreply@aruba.it", false, null, null))
        assertEquals(MailKind.Notice, MailText.kind("fatture@aruba.it", false, "auto-generated", null))
        assertEquals(MailKind.Newsletter, MailText.kind("club@sito.it", false, null, "bulk"))
    }

    @Test fun testoDaHtml() {
        val html = "<style>p{}</style><p>Ciao&nbsp;Marco,</p><p>ecco il <b>preventivo</b>.</p><br><br><br><div>Saluti</div>"
        assertEquals("Ciao Marco,\necco il preventivo.\n\nSaluti", MailText.plain(html))
    }
}

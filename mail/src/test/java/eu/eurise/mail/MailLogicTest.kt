package eu.eurise.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailLogicTest {
    @Test fun fornitoriNoti() {
        assertEquals("mail.ecloud.global", Providers.known("giorgio@murena.io")?.settings?.imap?.host)
        assertEquals(465, Providers.known("a@libero.it")?.settings?.smtp?.port)
        assertNull(Providers.known("a@studio.it"))
        assertEquals("imap.studio.it", Providers.guesses("giorgio@studio.it").first().imap.host)
        assertTrue(Providers.isValidEmail("giorgio@studio.it"))
        assertFalse(Providers.isValidEmail("giorgio@studio"))
    }

    @Test fun autoconfigDiThunderbird() {
        val xml = """
            <clientConfig version="1.1"><emailProvider id="esempio.it">
              <incomingServer type="pop3"><hostname>pop.esempio.it</hostname><port>995</port><socketType>SSL</socketType></incomingServer>
              <incomingServer type="imap"><hostname>imap.esempio.it</hostname><port>993</port><socketType>SSL</socketType>
                <username>%EMAILLOCALPART%</username></incomingServer>
              <outgoingServer type="smtp"><hostname>smtp.esempio.it</hostname><port>25</port><socketType>plain</socketType></outgoingServer>
              <outgoingServer type="smtp"><hostname>smtp.esempio.it</hostname><port>587</port><socketType>STARTTLS</socketType></outgoingServer>
            </emailProvider></clientConfig>
        """.trimIndent()
        val s = Providers.parseAutoconfig(xml, "anna@esempio.it")!!
        assertEquals(Server("imap.esempio.it", 993, Security.Ssl), s.imap)
        // Il server in chiaro si salta: si prende quello con STARTTLS.
        assertEquals(Server("smtp.esempio.it", 587, Security.StartTls), s.smtp)
        assertEquals("anna", s.user)
    }

    @Test fun personeAvvisiNewsletter() {
        assertEquals(Kind.Newsletters, MailText.classify("news@negozio.it", listUnsubscribe = true, listId = false, precedence = null))
        assertEquals(Kind.Alerts, MailText.classify("no-reply@banca.it", false, false, null))
        assertEquals(Kind.Alerts, MailText.classify("notifiche@comune.it", false, false, null))
        assertEquals(Kind.People, MailText.classify("anna.rossi@studio.it", false, false, null))
        assertEquals(Kind.Newsletters, MailText.classify("anna@studio.it", false, false, "bulk"))
    }

    @Test fun inizialiEOggetti() {
        assertEquals("AR", MailText.initials("Anna Rossi", "anna@x.it"))
        assertEquals("ST", MailText.initials("", "studio.tecnico@x.it"))
        assertEquals("Re: Documenti", MailText.replySubject("R: RE: Documenti"))
        assertEquals("Re: Preventivo", MailText.replySubject("Preventivo"))
    }

    @Test fun htmlETestoEPixel() {
        val html = "<html><head><style>p{}</style></head><body><p>Ciao&nbsp;Anna</p><img src=\"https://t.example/p.gif\" width=1><br>Fine</body></html>"
        assertEquals("Ciao Anna\n\nFine", MailText.htmlToText(html))
        assertEquals(1, MailText.remoteImages(html))
        assertTrue(MailText.plainToHtml("vedi https://eurise.eu ora").contains("<a href=\"https://eurise.eu\">"))
        assertEquals("\n\nAnna ha scritto:\n> riga 1\n> riga 2", MailText.quote("riga 1\nriga 2\n", "Anna ha scritto:"))
    }
}

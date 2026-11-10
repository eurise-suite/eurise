package eu.eurise.feature.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrContentTest {
    @Test fun pagoPa() {
        val c = QrContent.parse("PAGOPA|002|302000001234567890|01234567890|12840") as QrContent.PagoPa
        assertEquals("302000001234567890", c.notice)
        assertEquals("01234567890", c.creditorTaxCode)
        assertEquals(12840L, c.amountCents)
    }

    @Test fun sepaTransfer() {
        val c = QrContent.parse("BCD\n002\n1\nSCT\n\nStudio Rossi\nIT60 X054 2811 1010 0000 0123 456\nEUR128.40\n\n\nFattura 12") as QrContent.Sepa
        assertEquals("Studio Rossi", c.name)
        assertEquals("IT60X0542811101000000123456", c.iban)
        assertEquals("128.40", c.amount)
        assertEquals("Fattura 12", c.reference)
    }

    @Test fun wifiWithEscapes() {
        val c = QrContent.parse("WIFI:T:WPA;S:Studio\\;Ospiti;P:pa\\:ss;H:false;;") as QrContent.Wifi
        assertEquals("Studio;Ospiti", c.ssid)
        assertEquals("pa:ss", c.password)
        assertEquals("WPA", c.security)
        assertFalse(c.hidden)
    }

    @Test fun linkShowsHost() {
        val c = QrContent.parse("https://login.esempio.it/pagina?x=1") as QrContent.Link
        assertEquals("login.esempio.it", c.host)
        assertTrue(c.secure)
        assertFalse((QrContent.parse("http://utente@truffa.com:8080/") as QrContent.Link).secure)
        assertEquals("truffa.com", (QrContent.parse("http://utente@truffa.com:8080/") as QrContent.Link).host)
    }

    @Test fun contactsAndEvents() {
        val v = QrContent.parse("BEGIN:VCARD\nVERSION:3.0\nN:Rossi;Anna\nTEL;TYPE=CELL:+39 333 1234567\nEMAIL:anna@esempio.it\nEND:VCARD") as QrContent.Contact
        assertEquals("Anna Rossi", v.name)
        assertEquals(listOf("+39 333 1234567"), v.phones)
        val m = QrContent.parse("MECARD:N:Rossi,Anna;TEL:3331234567;EMAIL:anna@esempio.it;;") as QrContent.Contact
        assertEquals("Anna Rossi", m.name)
        val e = QrContent.parse("BEGIN:VEVENT\nSUMMARY:Convegno\nDTSTART:20261212T090000\nLOCATION:Roma\nEND:VEVENT") as QrContent.Event
        assertEquals("Convegno", e.title)
        assertEquals("20261212T090000", e.start)
    }

    @Test fun otpIsNeverText() {
        assertTrue(QrContent.parse("otpauth://totp/Murena?secret=ABC") is QrContent.Otp)
        assertTrue(QrContent.parse("ciao a tutti") is QrContent.Text)
    }
}

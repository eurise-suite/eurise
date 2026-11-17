package eu.eurise.chat

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsTest {
    @Test fun fuori_orario() {
        // Martedì 17 novembre 2026.
        assertFalse(Quiet.isQuiet(LocalDateTime.of(2026, 11, 17, 8, 0)))
        assertFalse(Quiet.isQuiet(LocalDateTime.of(2026, 11, 17, 18, 59)))
        assertTrue(Quiet.isQuiet(LocalDateTime.of(2026, 11, 17, 19, 0)))
        assertTrue(Quiet.isQuiet(LocalDateTime.of(2026, 11, 17, 7, 59)))
        // Sabato e domenica tutto il giorno.
        assertTrue(Quiet.isQuiet(LocalDateTime.of(2026, 11, 21, 11, 0)))
        assertTrue(Quiet.isQuiet(LocalDateTime.of(2026, 11, 22, 11, 0)))
    }

    @Test fun dominio_dall_email() {
        assertEquals("studiorossi.it", Quiet.domainOf(" Dott.Rossi@StudioRossi.it "))
        assertNull(Quiet.domainOf("studiorossi.it"))
        assertNull(Quiet.domainOf("mario@localhost"))
        assertNull(Quiet.domainOf("mario@studio rossi.it"))
    }

    @Test fun tacchette_del_server() {
        assertEquals(5, Quiet.privacyOf("https://matrix-client.matrix.org"))
        assertEquals(5, Quiet.privacyOf("matrix.org"))
        assertEquals(8, Quiet.privacyOf("https://matrix.studiorossi.it"))
    }

    @Test fun l_altro_account() {
        assertEquals(Account.Work, Account.Personal.other)
        assertEquals(Account.Personal, Account.Work.other)
    }
}

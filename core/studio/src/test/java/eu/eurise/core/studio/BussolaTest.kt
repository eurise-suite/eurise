package eu.eurise.core.studio

import eu.eurise.core.studio.Bussola.Item
import eu.eurise.core.studio.Bussola.Prescription
import eu.eurise.core.studio.Bussola.RxState
import eu.eurise.core.studio.Bussola.RxStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BussolaTest {
    private val studio = "@dottore:studio.it"
    private val patient = "@giuseppe:studio.it"
    private val day = 86_400_000L

    @Test fun nre() {
        assertEquals("1500A4ABCDE1234", Bussola.nre("1500a 4abcd-e1234"))
        assertEquals("1500A 4ABCD E1234", Bussola.nreGroups("1500A4ABCDE1234"))
        assertNull(Bussola.nre("1500A4"))
    }

    @Test fun andataERitorno() {
        val r = Bussola.RxRequest("r1", listOf("Ramipril 5 mg", "Cardioaspirina 100"))
        val c = Bussola.request(r)
        assertEquals(r, Bussola.readRequest(c))
        assertTrue(c.getString("body").contains("Ramipril 5 mg"))
        val s = RxStatus("\$ev1", RxState.Ready, nre = "1500A4ABCDE1234")
        val sc = Bussola.status(s)
        assertEquals(s, Bussola.readStatus(sc))
        assertTrue(sc.getString("body").contains("1500A 4ABCD E1234"))
        assertEquals("\$ev1", sc.getJSONObject("m.relates_to").getString("event_id"))
        val info = Bussola.StudioInfo("Studio Rossi", "Lun 9-12", absence = "dal 4 all'11 dicembre", substitute = "Dott.ssa Prova")
        assertEquals(info, Bussola.readStudio(Bussola.studio(info)))
        assertEquals(listOf("Ramipril 5 mg"), Bussola.readTherapy(Bussola.therapy(listOf("Ramipril 5 mg"))))
        val inv = Bussola.Invite("i1", "È ora del controllo")
        assertEquals(inv, Bussola.readInvite(Bussola.invite(inv)))
        assertNull(Bussola.readRequest(sc))
    }

    @Test fun statiSoloDalloStudio() {
        val items = listOf(
            Item("\$s0", studio, 1, Bussola.studio(Bussola.StudioInfo("Studio"))),
            Item("\$s1", patient, 2, Bussola.studio(Bussola.StudioInfo("Finto"))),
            Item("\$req", patient, 10, Bussola.request(Bussola.RxRequest("r1", listOf("Ramipril")))),
            Item("\$a", studio, 20, Bussola.status(RxStatus("\$req", RxState.Preparing))),
            // Il paziente non può dire «pronta».
            Item("\$b", patient, 30, Bussola.status(RxStatus("\$req", RxState.Ready, "1500A4ABCDE1234"))),
        )
        assertEquals(studio, Bussola.studioSender(items))
        assertEquals("Studio", Bussola.latestStudio(items, studio)?.name)
        assertEquals(RxState.Preparing, Bussola.statusOf("\$req", items, studio).first.state)
        val ready = items + Item("\$c", studio, 40, Bussola.status(RxStatus("\$req", RxState.Ready, "1500A4ABCDE1234")))
        val (st, at) = Bussola.statusOf("\$req", ready, studio)
        assertEquals(Prescription.Valid, Bussola.prescription(st, at, 40 + 29 * day))
        assertEquals(Prescription.Expired, Bussola.prescription(st, at, 40 + 31 * day))
        val taken = ready + Item("\$d", patient, 50, Bussola.status(RxStatus("\$req", RxState.Collected)))
        assertEquals(Prescription.Collected, Bussola.prescription(Bussola.statusOf("\$req", taken, studio).first, null, 0))
        assertEquals(RxState.Received, Bussola.statusOf("\$altro", items, studio).first.state)
    }

    @Test fun risposteAgliInviti() {
        val items = listOf(
            Item("\$i", studio, 1, Bussola.invite(Bussola.Invite("i1", "Controllo"))),
            Item("\$r", patient, 2, Bussola.reply(Bussola.Reply("i1", Bussola.CHECKUP_CHOICES[0]))),
        )
        assertEquals("Ho prenotato", Bussola.replyTo("i1", items, patient))
        assertNull(Bussola.replyTo("i2", items, patient))
    }
}

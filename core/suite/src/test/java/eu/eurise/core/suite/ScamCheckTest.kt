package eu.eurise.core.suite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScamCheckTest {
    @Test fun familyWithNewNumber() {
        val v = ScamCheck.check("Ciao mamma, ho rotto il telefono, questo è il mio numero nuovo. Mi serve un bonifico urgente di 980 euro, ti spiego dopo")!!
        assertEquals(ScamCheck.Kind.Family, v.kind)
        val named = ScamCheck.check("Ciao papà sono Luca, ho cambiato numero. Salvalo. Mi fai una ricarica di 50 euro?")!!
        assertEquals("Luca", named.name)
        // Dalla rubrica non si avvisa.
        assertNull(ScamCheck.check("Ciao mamma, ho rotto il telefono, questo è il mio numero nuovo. Mi serve un bonifico urgente", known = true))
    }

    @Test fun bankParcelCode() {
        assertEquals(ScamCheck.Kind.Bank, ScamCheck.check("Poste Italiane: il tuo conto è stato sospeso per accesso anomalo. Verifica i tuoi dati su https://poste-verifica.top/x")!!.kind)
        assertEquals(ScamCheck.Kind.Parcel, ScamCheck.check("Il tuo pacco è in giacenza. Paga 1,99 di spese di spedizione: https://bit.ly/abc")!!.kind)
        assertEquals(ScamCheck.Kind.Code, ScamCheck.check("Scusa ti è arrivato un codice per errore, mandami il codice per favore")!!.kind)
        assertEquals(ScamCheck.Kind.Code, ScamCheck.check("Ehi dimmi il codice che ti è arrivato, l'ho messo sul tuo numero", known = true)!!.kind)
    }

    @Test fun normalMessagesPass() {
        assertNull(ScamCheck.check("Ciao mamma, stasera arrivo alle otto, prendo io il pane"))
        assertNull(ScamCheck.check("Il pacco è arrivato, grazie mille!"))
        assertNull(ScamCheck.check("Ti faccio il bonifico domani per la cena"))
        assertNull(ScamCheck.check("Il tuo codice di verifica è 482913. Non condividerlo con nessuno."))
        assertNull(ScamCheck.check("ok"))
    }
}

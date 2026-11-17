package eu.eurise.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CharModelTest {
    private val words = listOf(
        "ciao" to 200, "casa" to 220, "cosa" to 230, "come" to 240, "che" to 255, "non" to 250, "sono" to 240,
        "bene" to 200, "buono" to 180, "anche" to 230, "questo" to 230, "nella" to 210, "nel" to 220, "con" to 245,
        "domani" to 190, "vediamo" to 170, "sera" to 190, "lavoro" to 200, "grazie" to 210,
    )
    // Un dizionario vero ha migliaia di parole: qui ogni parola conta come se ce ne fossero molte simili.
    private val model = CharModel.Builder().apply { words.forEach { (w, p) -> add(w, 50f * CharModel.weightOf(p)) } }.build()

    @Test fun leLettereCheHannoSensoSonoPiuProbabili() {
        // Dopo «co» viene più facilmente «s», «m» o «n» che «x».
        assertTrue(model.logProb('c', 'o', 's') > model.logProb('c', 'o', 'x'))
        // Dopo «ch» in italiano viene «e».
        assertTrue(model.logProb('c', 'h', 'e') > model.logProb('c', 'h', 'r'))
        // Alla fine di «che» lo spazio è molto probabile, dopo «qu» quasi impossibile.
        val b = CharModel.BOUNDARY
        assertTrue(model.logProb('h', 'e', b) > model.logProb('q', 'u', b) + 2f)
    }

    @Test fun nessunaLetteraEImpossibile() {
        val v = model.logProb('z', 'z', 'q')
        assertTrue(v.isFinite() && v >= CharModel.FLOOR)
    }

    @Test fun siSalvaESiRilegge() {
        val out = ByteArrayOutputStream()
        model.write(out)
        val back = CharModel.read(ByteArrayInputStream(out.toByteArray()))!!
        assertEquals(model.logProb('c', 'o', 's'), back.logProb('c', 'o', 's'), 1e-6f)
        assertEquals(null, CharModel.read(ByteArrayInputStream(byteArrayOf(1, 2, 3))))
    }

    // Fila «v b n» sopra lo spazio: tasti larghi 100, alti 150; spazio sotto, largo 500.
    private val v = KeyBox('v'.code, 300, 300, 100, 150)
    private val b = KeyBox('b'.code, 400, 300, 100, 150)
    private val n = KeyBox('n'.code, 500, 300, 100, 150)
    private val space = KeyBox(' '.code, 200, 470, 500, 150)

    private fun lmAfter(p2: Char, p1: Char): (Int) -> Float? = { code ->
        val c = code.toChar()
        if (c == ' ' || c.isLetter()) model.logProb(p2, p1, c) else null
    }

    @Test fun alCentroDelTastoVinceSempreIlTocco() {
        val m = KeyModel()
        // Al centro di «b», anche se dopo «ch» la «b» non ha senso, resta «b».
        assertEquals(b.code, m.chooseWith(b, listOf(v, n, space), b.cx, b.cy, lmAfter('c', 'h')))
    }

    @Test fun sulBordoDecidonoLeLetterePrecedenti() {
        val m = KeyModel()
        // Dopo «co», tocco sul bordo tra «b» e «n»: «n» (con, come no) ha più senso di «b».
        assertEquals(n.code, m.chooseWith(b, listOf(v, n, space), b.x + 97f, b.cy, lmAfter('c', 'o')))
        // Dopo «ch», tocco sul bordo basso di «n», verso lo spazio: la parola non è finita, resta la lettera?
        // «che» vuole la «e», non «n» né spazio: il tasto sotto il dito non cambia se nessuno lo batte nettamente.
        val chosen = m.chooseWith(n, listOf(b, space), n.cx, n.y + 148f, lmAfter('c', 'h'))
        assertTrue(chosen == n.code || chosen == ' '.code)
        // Dopo «che» (parola finita), tocco al bordo basso di «b» sopra lo spazio: è lo spazio.
        assertEquals(' '.code, m.chooseWith(b, listOf(v, n, space), b.cx, b.y + 146f, lmAfter('h', 'e')))
        // Dopo «co», tocco tra la fila di «n» e lo spazio (finito sullo spazio): era la «n».
        assertEquals(n.code, m.chooseWith(space, listOf(b, n, v), n.cx, space.y - 8f, lmAfter('c', 'o')))
    }
}

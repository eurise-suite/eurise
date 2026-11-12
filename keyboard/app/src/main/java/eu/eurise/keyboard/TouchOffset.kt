package eu.eurise.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode

/**
 * Precisione su misura (richiesta del proprietario, 12 novembre: «la tastiera è imprecisa»). Ognuno tocca i tasti un
 * po' spostato rispetto al centro (di solito più in basso e verso il pollice): la tastiera impara lo scarto medio dei
 * tocchi andati a buon fine e sposta di altrettanto il punto che usa per scegliere il tasto.
 *
 * - Conta solo i tocchi sulle lettere che restano scritti: se il tasto dopo è Cancella, il tocco non si impara.
 * - Lo scarto si misura in quote del tasto (larghezza e altezza), così vale anche cambiando dimensione o rotazione.
 * - Impara piano (media mobile) e non va mai oltre un quarto di tasto; tutto resta sul telefono.
 */
object TouchOffset {
    private const val ALPHA = 0.03f
    private const val MAX = 0.25f
    /** Prima di questi campioni la correzione cresce gradualmente (niente salti con i primi tocchi). */
    private const val WARMUP = 60
    private const val PREF_X = "offset_x"
    private const val PREF_Y = "offset_y"
    private const val PREF_N = "offset_n"

    private var prefs: SharedPreferences? = null
    @Volatile private var meanX = 0f
    @Volatile private var meanY = 0f
    @Volatile private var samples = 0

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("eurise_touch", Context.MODE_PRIVATE)
        prefs = p
        meanX = p.getFloat(PREF_X, 0f).coerceIn(-MAX, MAX)
        meanY = p.getFloat(PREF_Y, 0f).coerceIn(-MAX, MAX)
        samples = p.getInt(PREF_N, 0)
    }

    private fun weight() = (samples.toFloat() / WARMUP).coerceAtMost(1f)

    /** Correzione in pixel da togliere al tocco (x). */
    fun dx(keyboard: Keyboard): Int = (meanX * weight() * keyboard.mMostCommonKeyWidth).toInt()
    fun dy(keyboard: Keyboard): Int = (meanY * weight() * keyboard.mMostCommonKeyHeight).toInt()

    // Il tocco in attesa: diventa un campione se il tasto successivo non è Cancella.
    private class Pending(val code: Int, val fx: Float, val fy: Float, val at: Long)
    @Volatile private var pending: Pending? = null

    /** Il tocco (coordinate originali, senza correzione) è finito su [key]. */
    fun touched(key: Key?, rawX: Int, rawY: Int) {
        if (key == null || !Character.isLetter(key.code) || key.width <= 0 || key.height <= 0) { pending = null; return }
        val fx = (rawX - (key.x + key.width / 2f)) / key.width
        val fy = (rawY - (key.y + key.height / 2f)) / key.height
        // Un tocco lontanissimo dal centro non dice nulla dell'abitudine (scivolata, bordo).
        if (kotlin.math.abs(fx) > 0.6f || kotlin.math.abs(fy) > 0.6f) { pending = null; return }
        pending = Pending(key.code, fx, fy, SystemClock.uptimeMillis())
    }

    /** Ogni tasto davvero scritto (LatinIME.onEvent): conferma o scarta il tocco precedente. */
    fun committed(code: Int) {
        val p = pending
        val now = SystemClock.uptimeMillis()
        if (code == KeyCode.DELETE) { pending = null; return }
        // Il tasto appena scritto è quello del tocco: resta in attesa del prossimo, che lo conferma.
        if (p != null && p.code == code && now - p.at < 1_000) { confirmed = p; pending = null; return }
        confirmed?.let { c -> if (now - c.at < 10_000) learn(c) }
        confirmed = null
    }

    @Volatile private var confirmed: Pending? = null

    private fun learn(p: Pending) {
        meanX = (meanX + ALPHA * (p.fx - meanX)).coerceIn(-MAX, MAX)
        meanY = (meanY + ALPHA * (p.fy - meanY)).coerceIn(-MAX, MAX)
        samples++
        if (samples % 20 == 0) prefs?.edit()?.putFloat(PREF_X, meanX)?.putFloat(PREF_Y, meanY)?.putInt(PREF_N, samples)?.apply()
    }

    /** «Ricomincia»: dimentica lo scarto imparato. */
    fun reset() {
        meanX = 0f; meanY = 0f; samples = 0
        prefs?.edit()?.remove(PREF_X)?.remove(PREF_Y)?.remove(PREF_N)?.apply()
    }
}

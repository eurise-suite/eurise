package eu.eurise.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants

/**
 * Lo spazio che diventava b o n (prova del proprietario, 11 novembre). Scendendo verso la barra spaziatrice il pollice
 * si ferma un po' prima: la parte bassa dei tasti che stanno sopra lo spazio (di solito c v b n m) conta come spazio.
 *
 * Quanto conta si regola da solo con la scrittura di chi usa la tastiera:
 * - lettera di quella zona, cancellata e sostituita da uno spazio → la zona dello spazio cresce un poco;
 * - spazio dato dalla zona, cancellato e sostituito da una lettera → la zona cala.
 * Tutto resta sul telefono (una sola cifra nelle impostazioni della tastiera).
 */
object TouchFix {
    /**
     * Quota del tasto, dal basso, che va allo spazio: di base un ottavo, mai meno di [MIN] né più di [MAX].
     * Prova del proprietario (13 novembre, sera): spazi dove non dovevano esserci. Con il tetto di prima (38 %) la zona
     * cresceva fino a prendersi quasi metà di c v b n m; ora resta piccola e si riparte da capo (chiave nuova).
     */
    const val DEFAULT_REACH = 0.12f
    private const val MIN = 0.05f
    private const val MAX = 0.20f
    private const val STEP = 0.02f
    private const val PREF = "eurise_space_reach_2"

    private var prefs: SharedPreferences? = null
    @Volatile var reach = DEFAULT_REACH
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("eurise_touch", Context.MODE_PRIVATE)
        prefs = p
        reach = p.getFloat(PREF, DEFAULT_REACH).coerceIn(MIN, MAX)
    }

    // --- Quale tasto ---

    private var spaceOf: Keyboard? = null
    private var space: Key? = null

    /**
     * Il tasto da usare al posto di [hit] se il tocco cade nella parte bassa di un tasto subito sopra lo spazio.
     * Annota la decisione per l'apprendimento.
     */
    fun adjust(keyboard: Keyboard, hit: Key?, x: Int, y: Int): Key? {
        if (hit == null || !Character.isLetter(hit.code)) return hit.also { note(hit, false, false) }
        val sp = spaceKey(keyboard) ?: return hit
        val above = isAboveSpace(hit, sp, x)
        val inZone = above && y >= hit.y + hit.height - (reach * hit.height).toInt()
        // Ancora più in basso, nella fascia di [MAX], è «quasi spazio»: serve a imparare se la zona è troppo piccola.
        val nearZone = above && y >= hit.y + hit.height - (MAX * hit.height).toInt()
        return if (inZone) sp.also { note(it, redirected = true, nearSpace = true) } else hit.also { note(it, false, nearZone) }
    }

    fun isAboveSpace(key: Key, sp: Key, x: Int): Boolean {
        val bottom = key.y + key.height
        val gap = sp.y - bottom
        return gap >= -key.height / 4 && gap < key.height / 2 && x >= sp.x && x < sp.x + sp.width
    }

    private fun spaceKey(keyboard: Keyboard): Key? {
        if (spaceOf !== keyboard) {
            spaceOf = keyboard
            space = keyboard.sortedKeys.firstOrNull { it.code == Constants.CODE_SPACE }
        }
        return space
    }

    // --- Apprendimento ---

    private class Tap(val code: Int, val redirected: Boolean, val nearSpace: Boolean, val at: Long)

    @Volatile private var lastHit: Tap? = null
    /** Le ultime cose scritte: (tasto, veniva dalla zona?) con l'ora. */
    private val recent = ArrayDeque<Tap>()

    private fun note(key: Key?, redirected: Boolean, nearSpace: Boolean) {
        lastHit = key?.let { Tap(it.code, redirected, nearSpace, SystemClock.uptimeMillis()) }
    }

    /** Ogni tasto davvero scritto (LatinIME.onEvent). */
    fun committed(code: Int) {
        val now = SystemClock.uptimeMillis()
        val hit = lastHit?.takeIf { it.code == code && now - it.at < 1_500 }
        val tap = Tap(code, hit?.redirected == true, hit?.nearSpace == true, now)
        if (code == KeyCode.DELETE) { recent.addLast(tap); trim(); return }
        learn(tap)
        recent.addLast(tap)
        trim()
    }

    /** Sequenze «sbagliato, cancella, giusto» negli ultimi tre secondi. */
    private fun learn(now: Tap) {
        val del = recent.lastOrNull()?.takeIf { it.code == KeyCode.DELETE && now.at - it.at < 3_000 } ?: return
        val wrong = recent.getOrNull(recent.size - 2)?.takeIf { del.at - it.at < 3_000 } ?: return
        when {
            // Voleva lo spazio, è uscita la lettera dalla parte bassa del tasto.
            now.code == Constants.CODE_SPACE && Character.isLetter(wrong.code) && wrong.nearSpace && !wrong.redirected -> move(+STEP)
            // Voleva la lettera, la zona gli ha dato lo spazio.
            Character.isLetter(now.code) && wrong.code == Constants.CODE_SPACE && wrong.redirected -> move(-STEP)
        }
    }

    private fun move(by: Float) {
        reach = (reach + by).coerceIn(MIN, MAX)
        prefs?.edit()?.putFloat(PREF, reach)?.apply()
    }

    private fun trim() { while (recent.size > 6) recent.removeFirst() }
}

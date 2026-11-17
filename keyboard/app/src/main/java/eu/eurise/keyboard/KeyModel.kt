package eu.eurise.keyboard

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sqrt

/** Un tasto: codice (minuscolo) e rettangolo in pixel della tastiera. */
data class KeyBox(val code: Int, val x: Int, val y: Int, val w: Int, val h: Int) {
    val cx get() = x + w / 2f
    val cy get() = y + h / 2f
}

/**
 * Modello del tocco per tasto (lavoro principale della Tastiera, approvato il 12 novembre; idea presa da FUTO Keyboard,
 * codice nostro). Per ogni lettera la tastiera impara dove la persona la tocca davvero: il punto medio rispetto al
 * centro del tasto e quanto i tocchi sono sparsi, in quote del tasto (valgono anche cambiando altezza o rotazione).
 *
 * Serve a due cose:
 * - al motore delle correzioni di HeliBoard, come «punto preferito» e raggio di ogni tasto ([sweetSpot]): le parole
 *   proposte e corrette tengono conto di come scrive quella persona, insieme al dizionario italiano;
 * - alla scelta del tasto sul bordo tra due lettere ([choose]), solo con molti esempi e un vantaggio netto (la prova
 *   del 13 novembre ha insegnato che spostare troppo la scelta scambia le lettere).
 *
 * Con pochi esempi ogni tasto resta vicino alla media di tutti i tasti e questa a valori prudenti. Tutto resta sul
 * telefono. Nessuna dipendenza da Android: si prova in `KeyModelTest`.
 */
class KeyModel {
    private class Stats(var n: Float = 0f, var mx: Float = 0f, var my: Float = 0f, var vx: Float = 0f, var vy: Float = 0f) {
        /** Media mobile che dimentica piano: dopo [MEMORY] esempi conta il nuovo come 1/[MEMORY]. */
        fun add(fx: Float, fy: Float, weight: Float) {
            n = (n + weight).coerceAtMost(MEMORY)
            val a = weight / n
            val dx = fx - mx
            val dy = fy - my
            mx += a * dx
            my += a * dy
            vx = (1 - a) * (vx + a * dx * dx)
            vy = (1 - a) * (vy + a * dy * dy)
        }
    }

    private val keys = HashMap<Int, Stats>()
    private val all = Stats()

    /** Esempi imparati in tutto (anche dimenticati: serve a sapere quando il modello è pronto). */
    var learned = 0
        private set

    val ready get() = learned >= READY

    /** Un tocco in ([px], [py]) voleva il tasto [box]. Tocchi lontanissimi (scivolate, bordi) non insegnano nulla. */
    @Synchronized
    fun record(box: KeyBox, px: Float, py: Float, weight: Float = 1f): Boolean {
        if (box.w <= 0 || box.h <= 0) return false
        val fx = (px - box.cx) / box.w
        val fy = (py - box.cy) / box.h
        if (abs(fx) > FAR || abs(fy) > FAR) return false
        keys.getOrPut(box.code) { Stats() }.add(fx, fy, weight)
        all.add(fx, fy, weight)
        learned++
        return true
    }

    /** Punto medio e dispersione di un tasto, avvicinati alla media generale (e questa ai valori prudenti) se ci sono pochi esempi. */
    @Synchronized
    fun shape(code: Int): FloatArray {
        val gn = all.n
        val gmx = all.mx * gn / (gn + PRIOR)
        val gmy = all.my * gn / (gn + PRIOR)
        val gvx = (all.vx * gn + PRIOR_VX * PRIOR) / (gn + PRIOR)
        val gvy = (all.vy * gn + PRIOR_VY * PRIOR) / (gn + PRIOR)
        val s = keys[code] ?: return limited(gmx, gmy, gvx, gvy, 0f)
        val k = PRIOR
        val mx = (s.mx * s.n + gmx * k) / (s.n + k)
        val my = (s.my * s.n + gmy * k) / (s.n + k)
        val vx = (s.vx * s.n + gvx * k) / (s.n + k)
        val vy = (s.vy * s.n + gvy * k) / (s.n + k)
        return limited(mx, my, vx, vy, s.n)
    }

    /**
     * Il punto medio resta entro il limite; se lo si avvicina al centro, la dispersione cresce di quanto lo si è spostato
     * (resta la distanza media dei tocchi dal punto usato).
     */
    private fun limited(mx: Float, my: Float, vx: Float, vy: Float, n: Float): FloatArray {
        val cx = mx.clampMean(MAX_MX)
        val cy = my.clampMean(MAX_MY)
        return floatArrayOf(
            cx, cy,
            (vx + (mx - cx) * (mx - cx)).coerceIn(MIN_V, MAX_V),
            (vy + (my - cy) * (my - cy)).coerceIn(MIN_V, MAX_V),
            n,
        )
    }

    /**
     * Punto preferito (x, y) e raggio del tasto per il motore delle correzioni. Il raggio con i valori prudenti è quello
     * di AOSP (0,15 della diagonale); poi segue la dispersione vera, tra 0,12 e 0,20 della diagonale.
     */
    fun sweetSpot(box: KeyBox): FloatArray {
        val (mx, my, vx, vy) = shape(box.code)
        val diag = hypot(box.w.toFloat(), box.h.toFloat())
        val sigma = sqrt(vx * box.w * box.w + vy * box.h * box.h)
        val r = (RADIUS * sigma).coerceIn(0.12f * diag, 0.20f * diag)
        return floatArrayOf(box.cx + mx * box.w, box.cy + my * box.h, r)
    }

    /** Verosimiglianza (logaritmo) che il tocco volesse [box]. */
    private fun score(box: KeyBox, px: Float, py: Float): Float {
        val (mx, my, vx, vy) = shape(box.code)
        val fx = (px - box.cx) / box.w - mx
        val fy = (py - box.cy) / box.h - my
        return -0.5f * (fx * fx / vx + fy * fy / vy) - 0.5f * ln(vx * vy) - ln(box.w.toFloat() * box.h)
    }

    /**
     * Il tasto da scrivere: quello sotto il dito ([hit]) salvo che un tasto accanto, ben conosciuto anche lui, spieghi il
     * tocco molto meglio. Restituisce il codice scelto.
     */
    fun choose(hit: KeyBox, near: List<KeyBox>, px: Float, py: Float): Int {
        if (!ready || shape(hit.code)[4] < MIN_N) return hit.code
        val base = score(hit, px, py)
        var best = hit.code
        var bestScore = base + MARGIN
        for (k in near) {
            if (k.code == hit.code || shape(k.code)[4] < MIN_N) continue
            // Solo tasti davvero vicini al tocco (al massimo poco oltre il loro bordo).
            if (abs(px - k.cx) > 0.85f * k.w || abs(py - k.cy) > 0.85f * k.h) continue
            val s = score(k, px, py)
            if (s > bestScore) { best = k.code; bestScore = s }
        }
        return best
    }

    /**
     * Scelta del tasto con le lettere precedenti (17 novembre): per ogni tasto vicino, quanto il tocco gli somiglia
     * ([score], con i valori prudenti finché non ha imparato) più quanto quella lettera ha senso dopo le precedenti
     * ([lm], logaritmo; null = tasto che non è una lettera né lo spazio). Il tasto sotto il dito vince salvo che un
     * altro sia nettamente più probabile: al centro di un tasto la distanza pesa troppo perché una lettera la ribalti,
     * sul bordo decide la lingua. Vale anche tra lettere e spazio.
     */
    fun chooseWith(hit: KeyBox, near: List<KeyBox>, px: Float, py: Float, lm: (Int) -> Float?): Int {
        val hitLm = lm(hit.code) ?: return hit.code
        // La larghezza di un tasto normale: lo spazio, molto più largo, si tocca in un punto qualsiasi della sua
        // larghezza, quindi per lui conta solo quanto il tocco è vicino in altezza.
        val unit = (near.map { it.w } + hit.w).min()
        fun geo(k: KeyBox): Float =
            if (k.w > 2.5f * unit) score(KeyBox(k.code, (px - unit / 2f).toInt(), k.y, unit, k.h), px, py) else score(k, px, py)
        val hitScore = geo(hit) + LM_WEIGHT * hitLm
        var best = hit.code
        var bestScore = hitScore + LM_MARGIN
        for (k in near) {
            if (k.code == hit.code) continue
            val l = lm(k.code) ?: continue
            // Solo tasti davvero accanto al tocco.
            if (abs(py - k.cy) > 0.95f * k.h) continue
            if (k.w > 2.5f * unit) { if (px < k.x - 0.3f * unit || px > k.x + k.w + 0.3f * unit) continue }
            else if (abs(px - k.cx) > 0.95f * k.w) continue
            val s = geo(k) + LM_WEIGHT * l
            if (s > bestScore) { best = k.code; bestScore = s }
        }
        return best
    }

    @Synchronized
    fun clear() { keys.clear(); all.n = 0f; all.mx = 0f; all.my = 0f; all.vx = 0f; all.vy = 0f; learned = 0 }

    /** Forma compatta per le preferenze e il backup: `1|learned|code,n,mx,my,vx,vy;…` (il primo è il totale). */
    @Synchronized
    fun save(): String = buildString {
        append("1|").append(learned).append('|')
        fun put(code: Int, s: Stats) {
            append(code).append(',').append(s.n).append(',').append(s.mx).append(',').append(s.my)
                .append(',').append(s.vx).append(',').append(s.vy).append(';')
        }
        put(0, all)
        keys.forEach { (c, s) -> put(c, s) }
    }

    @Synchronized
    fun load(text: String?) {
        clear()
        val parts = text?.split('|') ?: return
        if (parts.size != 3 || parts[0] != "1") return
        learned = parts[1].toIntOrNull() ?: 0
        for (item in parts[2].split(';')) {
            val v = item.split(',')
            if (v.size != 6) continue
            val code = v[0].toIntOrNull() ?: continue
            val f = v.drop(1).map { it.toFloatOrNull() ?: Float.NaN }
            if (f.any { it.isNaN() }) continue
            val s = Stats(f[0].coerceIn(0f, MEMORY), f[1], f[2], f[3].coerceAtLeast(0f), f[4].coerceAtLeast(0f))
            if (code == 0) { all.n = s.n; all.mx = s.mx; all.my = s.my; all.vx = s.vx; all.vy = s.vy } else keys[code] = s
        }
    }

    private fun Float.clampMean(max: Float) = coerceIn(-max, max)

    companion object {
        /** Memoria di ogni tasto (esempi); oltre, i tocchi vecchi pesano sempre meno. */
        const val MEMORY = 400f
        /** Esempi «immaginari» con i valori prudenti: con pochi tocchi veri il tasto resta com'è. */
        const val PRIOR = 30f
        /** Dispersione prudente: un quarto di tasto in larghezza, poco meno in altezza. */
        const val PRIOR_VX = 0.0625f
        const val PRIOR_VY = 0.0484f
        const val MIN_V = 0.01f
        const val MAX_V = 0.16f
        /** Il punto preferito non si allontana dal centro più di questo (quote del tasto). */
        const val MAX_MX = 0.15f
        const val MAX_MY = 0.20f
        const val FAR = 1.2f
        /** Raggio = dispersione × questo (con i valori prudenti dà lo 0,15 della diagonale di AOSP). */
        const val RADIUS = 0.65f
        /** Esempi in tutto prima di usare il modello. */
        const val READY = 150
        /** Esempi di un tasto prima che possa cambiare la scelta. */
        const val MIN_N = 40f
        /** Vantaggio richiesto (logaritmo): il tasto accanto deve essere almeno ~3 volte più probabile. */
        const val MARGIN = 1.1f
        /** Peso delle lettere precedenti rispetto al tocco. */
        const val LM_WEIGHT = 0.8f
        /** Vantaggio richiesto per cambiare il tasto sotto il dito con le lettere precedenti. */
        const val LM_MARGIN = 0.35f
    }
}

package eu.eurise.gallery.logic

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pennello per passo (tavola GalModifiche): dove vale un passo della modifica. Ogni passata porta la zona verso
 * [MaskStroke.amount] (100, 75, 50, 25 %, o 0 = gomma); [inverted] scambia dentro e fuori. Senza passate il passo vale
 * ovunque. Posizioni in quote della foto prima di raddrizzarla, come i ritocchi. Si prova in `StepMaskTest`.
 */
data class MaskStroke(val size: Float, val amount: Float, val points: List<Float>)

data class StepMask(val strokes: List<MaskStroke> = emptyList(), val inverted: Boolean = false) {
    val isEmpty: Boolean get() = strokes.isEmpty() && !inverted
}

/** La maschera disegnata, da 0 a 1, in una griglia piccola. */
class MaskGrid(val w: Int, val h: Int, val v: FloatArray) {
    fun sample(fx: Float, fy: Float): Float {
        val x = (fx * w - 0.5f).coerceIn(0f, w - 1f)
        val y = (fy * h - 0.5f).coerceIn(0f, h - 1f)
        val x0 = x.toInt(); val y0 = y.toInt()
        val x1 = min(w - 1, x0 + 1); val y1 = min(h - 1, y0 + 1)
        val tx = x - x0; val ty = y - y0
        val a = v[y0 * w + x0] * (1 - tx) + v[y0 * w + x1] * tx
        val b = v[y1 * w + x0] * (1 - tx) + v[y1 * w + x1] * tx
        return a * (1 - ty) + b * ty
    }
}

fun maskGrid(m: StepMask, aspect: Float, maxSide: Int = 512): MaskGrid {
    val mw = if (aspect >= 1f) maxSide else max(1, (maxSide * aspect).roundToInt())
    val mh = if (aspect >= 1f) max(1, (maxSide / aspect).roundToInt()) else maxSide
    val v = FloatArray(mw * mh)
    val short = min(mw, mh).toFloat()
    for (s in m.strokes) {
        val cover = FloatArray(mw * mh)
        val r = max(1f, s.size * short)
        fun dab(cx: Float, cy: Float) {
            val x0 = max(0, (cx - r).toInt()); val x1 = min(mw - 1, (cx + r).toInt() + 1)
            val y0 = max(0, (cy - r).toInt()); val y1 = min(mh - 1, (cy + r).toInt() + 1)
            for (y in y0..y1) for (x in x0..x1) {
                val d2 = ((x + 0.5f - cx) * (x + 0.5f - cx) + (y + 0.5f - cy) * (y + 0.5f - cy)) / (r * r)
                if (d2 >= 1f) continue
                val a = min(1f, (1f - d2) * 2f)
                val i = y * mw + x
                if (a > cover[i]) cover[i] = a
            }
        }
        val pts = s.points
        var px = pts[0] * mw; var py = pts[1] * mh
        dab(px, py)
        for (i in 2 until pts.size step 2) {
            val nx = pts[i] * mw; val ny = pts[i + 1] * mh
            val steps = max(1, (hypot(nx - px, ny - py) / (r / 4f)).toInt())
            for (k in 1..steps) dab(px + (nx - px) * k / steps, py + (ny - py) * k / steps)
            px = nx; py = ny
        }
        for (j in v.indices) if (cover[j] > 0f) v[j] = v[j] * (1f - cover[j]) + s.amount * cover[j]
    }
    if (m.strokes.isEmpty()) v.fill(0f)
    if (m.inverted) for (j in v.indices) v[j] = 1f - v[j]
    return MaskGrid(mw, mh, v)
}

/**
 * Mescola una fascia sviluppata [dev] con quella di prima [orig] secondo la maschera. La fascia sta nella foto già
 * raddrizzata ([angle] gradi, ingrandita di [scale]); la maschera nella foto prima di raddrizzarla, quindi ogni pixel si
 * riporta lì. Il risultato va in [dev].
 */
fun blendMasked(dev: IntArray, orig: IntArray, w: Int, rows: Int, y0: Int, fullW: Int, fullH: Int, grid: MaskGrid, angle: Float = 0f, scale: Float = 1f) {
    val rad = Math.toRadians(angle.toDouble())
    val ca = cos(rad).toFloat(); val sa = sin(rad).toFloat()
    val hw = fullW / 2f; val hh = fullH / 2f
    for (y in 0 until rows) for (x in 0 until w) {
        val dx = x + 0.5f - hw; val dy = y0 + y + 0.5f - hh
        val sx = (dx * ca + dy * sa) / scale + hw
        val sy = (-dx * sa + dy * ca) / scale + hh
        val m = grid.sample(sx / fullW, sy / fullH)
        val i = y * w + x
        if (m >= 0.999f) continue
        val a = dev[i]; val b = orig[i]
        if (m <= 0.001f) { dev[i] = b; continue }
        fun ch(sh: Int) = (((b shr sh) and 0xFF) + (((a shr sh) and 0xFF) - ((b shr sh) and 0xFF)) * m).roundToInt().coerceIn(0, 255)
        dev[i] = (a and -0x1000000) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

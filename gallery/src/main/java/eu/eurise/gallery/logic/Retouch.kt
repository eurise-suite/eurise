package eu.eurise.gallery.logic

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ritocca (tavola GalRitocca, approvata il 17 novembre): Selettivo a punti, Pennello, Correttivo per le macchie.
 * Le posizioni sono in quote della foto ruotata e raddrizzata in prospettiva, prima del ritaglio; le grandezze in quote
 * del lato corto. I ritocchi si fanno sulla foto prima delle regolazioni, nell'anteprima come nella copia salvata.
 * Logica pura: si prova in `RetouchTest`.
 */

/** Un punto del Selettivo: cambia i colori simili a [ref] attorno a sé, sempre meno allontanandosi. */
data class SelectivePoint(
    val id: Long,
    val x: Float,
    val y: Float,
    val radius: Float = 0.18f,
    /** Il colore sotto il punto quando è stato messo (RGB): i colori lontani da questo cambiano poco. */
    val ref: Int,
    val light: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
)

enum class BrushKind { Lighten, Darken, Warm, Cool, Saturate, Desaturate, Erase }

/** Una passata del Pennello: punti x, y alternati; [size] = raggio; [strength] da 0 a 1. */
data class BrushStroke(val kind: BrushKind, val size: Float, val strength: Float, val points: List<Float>)

/** Una macchia tolta con il Correttivo. */
data class HealSpot(val x: Float, val y: Float, val r: Float)

data class Retouch(
    val points: List<SelectivePoint> = emptyList(),
    val strokes: List<BrushStroke> = emptyList(),
    val spots: List<HealSpot> = emptyList(),
    val portrait: Portrait = Portrait(),
) {
    val isEmpty: Boolean get() = spots.isEmpty() && strokes.isEmpty() && portrait.isEmpty && points.none { it.light != 0f || it.contrast != 0f || it.saturation != 0f || it.warmth != 0f }
    val needsBands: Boolean get() = strokes.isNotEmpty() || (!portrait.isEmpty && portrait.spotlight != 0f) || points.any { it.light != 0f || it.contrast != 0f || it.saturation != 0f || it.warmth != 0f }
}

/** Le tre maschere del Pennello (esposizione, calore, saturazione), da -1 a 1, a bassa risoluzione. */
class BrushMasks(val w: Int, val h: Int) {
    val exposure = FloatArray(w * h)
    val warmth = FloatArray(w * h)
    val saturation = FloatArray(w * h)

    /** Il valore in un punto (quote della foto), interpolato. */
    fun sample(m: FloatArray, fx: Float, fy: Float): Float {
        val x = (fx * w - 0.5f).coerceIn(0f, w - 1f)
        val y = (fy * h - 0.5f).coerceIn(0f, h - 1f)
        val x0 = x.toInt(); val y0 = y.toInt()
        val x1 = min(w - 1, x0 + 1); val y1 = min(h - 1, y0 + 1)
        val tx = x - x0; val ty = y - y0
        val a = m[y0 * w + x0] * (1 - tx) + m[y0 * w + x1] * tx
        val b = m[y1 * w + x0] * (1 - tx) + m[y1 * w + x1] * tx
        return a * (1 - ty) + b * ty
    }
}

/**
 * Disegna le passate nelle maschere. Ogni passata copre la sua scia con un pennello morbido (al centro pieno, ai bordi
 * sfumato); passate dello stesso tipo si sommano fino al massimo; la Gomma toglie ogni effetto dove passa.
 * [aspect] = larghezza / altezza della foto, perché il pennello resti tondo.
 */
fun brushMasks(strokes: List<BrushStroke>, aspect: Float, maxSide: Int = 512): BrushMasks {
    val mw = if (aspect >= 1f) maxSide else max(1, (maxSide * aspect).roundToInt())
    val mh = if (aspect >= 1f) max(1, (maxSide / aspect).roundToInt()) else maxSide
    val masks = BrushMasks(mw, mh)
    val short = min(mw, mh).toFloat()
    val cover = FloatArray(mw * mh)
    for (s in strokes) {
        cover.fill(0f)
        val r = max(1f, s.size * short)
        val pts = s.points
        if (pts.size < 2) continue
        fun stamp(cx: Float, cy: Float) {
            val x0 = max(0, (cx - r).toInt()); val x1 = min(mw - 1, (cx + r).toInt() + 1)
            val y0 = max(0, (cy - r).toInt()); val y1 = min(mh - 1, (cy + r).toInt() + 1)
            for (y in y0..y1) for (x in x0..x1) {
                val d2 = ((x + 0.5f - cx) * (x + 0.5f - cx) + (y + 0.5f - cy) * (y + 0.5f - cy)) / (r * r)
                if (d2 >= 1f) continue
                val a = (1f - d2).let { it * it }
                val i = y * mw + x
                if (a > cover[i]) cover[i] = a
            }
        }
        var px = pts[0] * mw; var py = pts[1] * mh
        stamp(px, py)
        var i = 2
        while (i + 1 < pts.size) {
            val nx = pts[i] * mw; val ny = pts[i + 1] * mh
            val steps = max(1, (hypot(nx - px, ny - py) / (r / 4f)).toInt())
            for (k in 1..steps) stamp(px + (nx - px) * k / steps, py + (ny - py) * k / steps)
            px = nx; py = ny; i += 2
        }
        val (target, sign) = when (s.kind) {
            BrushKind.Lighten -> masks.exposure to 1f
            BrushKind.Darken -> masks.exposure to -1f
            BrushKind.Warm -> masks.warmth to 1f
            BrushKind.Cool -> masks.warmth to -1f
            BrushKind.Saturate -> masks.saturation to 1f
            BrushKind.Desaturate -> masks.saturation to -1f
            BrushKind.Erase -> null to 0f
        }
        for (j in cover.indices) {
            val c = cover[j]
            if (c == 0f) continue
            if (target == null) {
                masks.exposure[j] *= 1f - c; masks.warmth[j] *= 1f - c; masks.saturation[j] *= 1f - c
            } else target[j] = (target[j] + sign * c * s.strength).coerceIn(-1f, 1f)
        }
    }
    return masks
}

/**
 * Selettivo e Pennello su una fascia di pixel ARGB ([px], larga [w], [rows] righe) che comincia alla riga [y0] di una foto
 * grande [fullW]×[fullH]. Modifica [px] sul posto.
 */
fun applyLocal(px: IntArray, w: Int, rows: Int, y0: Int, fullW: Int, fullH: Int, r: Retouch, masks: BrushMasks?) {
    val active = r.points.filter { it.light != 0f || it.contrast != 0f || it.saturation != 0f || it.warmth != 0f }
    if (active.isEmpty() && masks == null) return
    val short = min(fullW, fullH).toFloat()
    for (y in 0 until rows) {
        val fy = (y0 + y + 0.5f) / fullH
        for (x in 0 until w) {
            val i = y * w + x
            val c = px[i]
            var rr = ((c shr 16) and 0xFF).toFloat()
            var gg = ((c shr 8) and 0xFF).toFloat()
            var bb = (c and 0xFF).toFloat()
            val fx = (x + 0.5f) / fullW
            var changed = false
            for (p in active) {
                val dx = (fx - p.x) * fullW / short
                val dy = (fy - p.y) * fullH / short
                val d2 = (dx * dx + dy * dy) / (p.radius * p.radius)
                if (d2 > 4f) continue
                val near = exp(-d2 * 2f)
                // Solo i colori simili a quello del punto: il cielo sì, la torre davanti no.
                val pr = (p.ref shr 16) and 0xFF; val pg = (p.ref shr 8) and 0xFF; val pb = p.ref and 0xFF
                val cd2 = ((rr - pr) * (rr - pr) + (gg - pg) * (gg - pg) + (bb - pb) * (bb - pb)) / (3f * 55f * 55f)
                val wgt = near * exp(-cd2)
                if (wgt < 0.003f) continue
                changed = true
                // Luminosità e contrasto attorno al grigio medio, saturazione attorno alla luminosità, calore rosso-blu.
                val k = 1f + p.contrast * 0.7f * wgt
                val add = p.light * 70f * wgt
                rr = (rr - 128f) * k + 128f + add; gg = (gg - 128f) * k + 128f + add; bb = (bb - 128f) * k + 128f + add
                val l = 0.2126f * rr + 0.7152f * gg + 0.0722f * bb
                val s = 1f + p.saturation * wgt
                rr = l + (rr - l) * s; gg = l + (gg - l) * s; bb = l + (bb - l) * s
                rr += p.warmth * 30f * wgt; bb -= p.warmth * 30f * wgt
            }
            if (masks != null) {
                val e = masks.sample(masks.exposure, fx, fy)
                val t = masks.sample(masks.warmth, fx, fy)
                val s = masks.sample(masks.saturation, fx, fy)
                if (e != 0f) { val k = Math.pow(2.0, e * 1.1).toFloat(); rr *= k; gg *= k; bb *= k; changed = true }
                if (t != 0f) { rr += t * 35f; bb -= t * 35f; changed = true }
                if (s != 0f) {
                    val l = 0.2126f * rr + 0.7152f * gg + 0.0722f * bb
                    val k = 1f + s
                    rr = l + (rr - l) * k; gg = l + (gg - l) * k; bb = l + (bb - l) * k; changed = true
                }
            }
            if (changed) px[i] = (c and -0x1000000) or (rr.roundToInt().coerceIn(0, 255) shl 16) or (gg.roundToInt().coerceIn(0, 255) shl 8) or bb.roundToInt().coerceIn(0, 255)
        }
    }
}

/**
 * Correttivo: la macchia di raggio [r] (pixel) in ([cx], [cy]) si copre con la zona vicina che le somiglia di più attorno
 * al bordo, con i colori riallineati e i bordi sfumati, così non si vedono cuciture. Modifica [px] (ARGB, [w]×[h]).
 */
fun heal(px: IntArray, w: Int, h: Int, cx: Int, cy: Int, r: Int) {
    if (r < 1) return
    val ring = (r * 1.4f).roundToInt().coerceAtLeast(r + 2)
    fun inside(x: Int, y: Int, rad: Int) = x - rad >= 0 && y - rad >= 0 && x + rad < w && y + rad < h
    // Un anello di confronto: i pixel tra r e 1,4 r attorno al centro.
    val offsets = buildList {
        for (dy in -ring..ring) for (dx in -ring..ring) {
            val d = sqrt((dx * dx + dy * dy).toFloat())
            if (d > r && d <= ring) add(dx to dy)
        }
    }
    if (offsets.isEmpty()) return
    fun at(x: Int, y: Int) = px[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
    var best: Pair<Int, Int>? = null
    var bestCost = Float.MAX_VALUE
    for (dist in listOf(2.2f, 3f, 4f)) for (k in 0 until 16) {
        val a = k * Math.PI / 8
        val sx = cx + (cos(a) * dist * r).roundToInt()
        val sy = cy + (sin(a) * dist * r).roundToInt()
        if (!inside(sx, sy, ring)) continue
        var cost = 0f
        for ((dx, dy) in offsets) {
            val t = at(cx + dx, cy + dy); val s = at(sx + dx, sy + dy)
            val er = ((t shr 16) and 0xFF) - ((s shr 16) and 0xFF)
            val eg = ((t shr 8) and 0xFF) - ((s shr 8) and 0xFF)
            val eb = (t and 0xFF) - (s and 0xFF)
            cost += (er * er + eg * eg + eb * eb).toFloat()
        }
        // A parità di somiglianza si preferisce la zona più vicina.
        cost *= 1f + (dist - 2.2f) * 0.08f
        if (cost < bestCost) { bestCost = cost; best = sx to sy }
    }
    val (sx, sy) = best ?: return
    // Differenza media dei colori sull'anello: la toppa prende la luce del posto in cui va.
    var mr = 0f; var mg = 0f; var mb = 0f
    for ((dx, dy) in offsets) {
        val t = at(cx + dx, cy + dy); val s = at(sx + dx, sy + dy)
        mr += ((t shr 16) and 0xFF) - ((s shr 16) and 0xFF)
        mg += ((t shr 8) and 0xFF) - ((s shr 8) and 0xFF)
        mb += (t and 0xFF) - (s and 0xFF)
    }
    mr /= offsets.size; mg /= offsets.size; mb /= offsets.size
    val out = px.copyOf()
    for (dy in -r..r) for (dx in -r..r) {
        val x = cx + dx; val y = cy + dy
        if (x !in 0 until w || y !in 0 until h) continue
        val d = sqrt((dx * dx + dy * dy).toFloat()) / r
        if (d > 1f) continue
        val alpha = if (d < 0.7f) 1f else (1f - (d - 0.7f) / 0.3f).let { it * it * (3 - 2 * it) }
        val s = at(sx + dx, sy + dy); val t = px[y * w + x]
        fun mix(tc: Int, sc: Int, m: Float) = (tc * (1 - alpha) + (sc + m) * alpha).roundToInt().coerceIn(0, 255)
        out[y * w + x] = (t and -0x1000000) or
            (mix((t shr 16) and 0xFF, (s shr 16) and 0xFF, mr) shl 16) or
            (mix((t shr 8) and 0xFF, (s shr 8) and 0xFF, mg) shl 8) or
            mix(t and 0xFF, s and 0xFF, mb)
    }
    System.arraycopy(out, 0, px, 0, px.size)
}

/** Distanza di un punto dal centro in quote del lato corto: serve a scegliere il punto più vicino a un tocco. */
fun shortDistance(ax: Float, ay: Float, bx: Float, by: Float, w: Int, h: Int): Float {
    val s = min(w, h).toFloat()
    return hypot((ax - bx) * w / s, (ay - by) * h / s)
}

/** Per la pila delle modifiche. */
fun Retouch.summary(): String = listOfNotNull(
    points.size.takeIf { it > 0 }?.let { "◎ $it" },
    strokes.size.takeIf { it > 0 }?.let { "✎ $it" },
    spots.size.takeIf { it > 0 }?.let { "✚ $it" },
    portrait.faces.size.takeIf { !portrait.isEmpty }?.let { "☺ $it" },
).joinToString(" · ")

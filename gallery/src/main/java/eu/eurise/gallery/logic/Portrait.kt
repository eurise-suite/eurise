package eu.eurise.gallery.logic

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Ritratto (tavola GalRitocca, quarta parte): pelle più liscia, occhi più chiari e luce sul viso. I volti si trovano con il
 * riconoscimento di Android sul telefono, oppure si toccano a mano; qui si lavora soltanto sui pixel. Posizioni in quote
 * della foto (come gli altri ritocchi), [eyes] = distanza tra gli occhi in quote del lato corto. Si prova in `PortraitTest`.
 */
data class FaceSpot(val x: Float, val y: Float, val eyes: Float)

data class Portrait(
    val faces: List<FaceSpot> = emptyList(),
    /** Luce sul viso: il volto un po' più chiaro, attorno un po' più scuro. */
    val spotlight: Float = 0f,
    val smoothing: Float = 0f,
    val eyes: Float = 0f,
) {
    val isEmpty: Boolean get() = faces.isEmpty() || (spotlight == 0f && smoothing == 0f && eyes == 0f)
}

/** Quanto un punto (pixel) sta dentro l'ovale del viso: 1 al centro, 0 fuori; [grow] allarga l'ovale. */
fun faceWeight(px: Float, py: Float, cx: Float, cy: Float, e: Float, grow: Float = 1f): Float {
    val rx = 1.25f * e * grow
    val ry = 1.7f * e * grow
    val dx = (px - cx) / rx
    val dy = (py - (cy + 0.35f * e)) / ry
    val d = dx * dx + dy * dy
    if (d <= 0.55f) return 1f
    if (d >= 1.3f) return 0f
    val t = (d - 0.55f) / 0.75f
    return 1f - t * t * (3f - 2f * t)
}

/** Quanto un colore somiglia alla pelle (Cb e Cr nella fascia delle pelli umane, chiare e scure), da 0 a 1. */
fun skinLikelihood(r: Int, g: Int, b: Int): Float {
    val cb = 128f - 0.1687f * r - 0.3313f * g + 0.5f * b
    val cr = 128f + 0.5f * r - 0.4187f * g - 0.0813f * b
    fun band(v: Float, lo: Float, hi: Float, soft: Float) = when {
        v < lo - soft || v > hi + soft -> 0f
        v < lo -> (v - lo + soft) / soft
        v > hi -> (hi + soft - v) / soft
        else -> 1f
    }
    return band(cr, 135f, 175f, 10f) * band(cb, 80f, 130f, 10f)
}

/** Sfocatura a scatola di un'immagine ARGB, canale per canale (raggio [r] pixel). */
fun boxBlurArgb(px: IntArray, w: Int, h: Int, r: Int): IntArray {
    if (r < 1) return px.copyOf()
    val tmp = IntArray(px.size)
    val out = IntArray(px.size)
    fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (l in 0 until lines) {
            var sr = 0; var sg = 0; var sb = 0
            fun at(i: Int): Int { val k = i.coerceIn(0, len - 1); return if (horizontal) src[l * w + k] else src[k * w + l] }
            for (i in -r..r) { val c = at(i); sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
            val n = 2 * r + 1
            for (i in 0 until len) {
                val idx = if (horizontal) l * w + i else i * w + l
                dst[idx] = (src[idx] and -0x1000000) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                val add = at(i + r + 1); val rem = at(i - r)
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
    pass(px, tmp, true)
    pass(tmp, out, false)
    return out
}

/**
 * Pelle e occhi di un volto, su un ritaglio di pixel [px] ([w]×[h]) attorno al viso; ([cx], [cy]) e [e] in pixel del ritaglio.
 * La pelle si leviga solo dove il colore è di pelle e non ci sono bordi (occhi, bocca, capelli restano nitidi).
 */
fun portraitFace(px: IntArray, w: Int, h: Int, cx: Float, cy: Float, e: Float, smoothing: Float, eyes: Float) {
    if (smoothing != 0f) {
        val soft = boxBlurArgb(px, w, h, max(1, (e * 0.09f).roundToInt()))
        for (y in 0 until h) for (x in 0 until w) {
            val fw = faceWeight(x + 0.5f, y + 0.5f, cx, cy, e)
            if (fw == 0f) continue
            val i = y * w + x
            val c = px[i]; val s = soft[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val sr = (s shr 16) and 0xFF; val sg = (s shr 8) and 0xFF; val sb = s and 0xFF
            val diff = ((r - sr) * 0.3f + (g - sg) * 0.59f + (b - sb) * 0.11f) / 16f
            val a = (smoothing * 0.9f * fw * skinLikelihood(r, g, b) * exp(-diff * diff)).coerceIn(0f, 1f)
            if (a < 0.01f) continue
            px[i] = (c and -0x1000000) or ((r + (sr - r) * a).roundToInt() shl 16) or ((g + (sg - g) * a).roundToInt() shl 8) or (b + (sb - b) * a).roundToInt()
        }
    }
    if (eyes != 0f) {
        val fine = boxBlurArgb(px, w, h, max(1, (e * 0.05f).roundToInt()))
        val rad = 0.32f * e
        for (side in listOf(-0.5f, 0.5f)) {
            val ex = cx + side * e
            val x0 = max(0, (ex - 2 * rad).toInt()); val x1 = min(w - 1, (ex + 2 * rad).toInt())
            val y0 = max(0, (cy - 2 * rad).toInt()); val y1 = min(h - 1, (cy + 2 * rad).toInt())
            for (y in y0..y1) for (x in x0..x1) {
                val dx = (x + 0.5f - ex) / rad; val dy = (y + 0.5f - cy) / (rad * 0.8f)
                val wgt = exp(-(dx * dx + dy * dy)) * eyes
                if (wgt < 0.01f) continue
                val i = y * w + x
                val c = px[i]; val f = fine[i]
                fun ch(sh: Int): Int {
                    val v = (c shr sh) and 0xFF; val bl = (f shr sh) and 0xFF
                    return (v + (v - bl) * 1.6f * wgt + 14f * wgt).roundToInt().coerceIn(0, 255)
                }
                px[i] = (c and -0x1000000) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
    }
}

/** Luce sul viso su una fascia ([rows] righe da [y0], foto [fullW]×[fullH]): volti più chiari, attorno più scuro. */
fun spotlight(px: IntArray, w: Int, rows: Int, y0: Int, fullW: Int, fullH: Int, p: Portrait) {
    if (p.spotlight == 0f || p.faces.isEmpty()) return
    val short = min(fullW, fullH).toFloat()
    val faces = p.faces.map { Triple(it.x * fullW, it.y * fullH, it.eyes * short) }
    for (y in 0 until rows) for (x in 0 until w) {
        var fw = 0f
        for ((cx, cy, e) in faces) fw = max(fw, faceWeight(x + 0.5f, y0 + y + 0.5f, cx, cy, e, grow = 1.7f))
        val k = 1f + p.spotlight * (0.22f * fw - 0.3f * (1f - fw))
        if (k == 1f) continue
        val i = y * w + x
        val c = px[i]
        px[i] = (c and -0x1000000) or
            ((((c shr 16) and 0xFF) * k).roundToInt().coerceIn(0, 255) shl 16) or
            ((((c shr 8) and 0xFF) * k).roundToInt().coerceIn(0, 255) shl 8) or
            ((c and 0xFF) * k).roundToInt().coerceIn(0, 255)
    }
}

package eu.eurise.gallery.logic

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Effetti (tavola GalEffetti, approvata il 17 novembre). Ogni effetto è un passo della pila, con la sua forza, i suoi
 * stili e il suo pennello ([Effect.mask]). Si applicano dopo le regolazioni, uno dopo l'altro.
 *
 * Gli effetti che guardano attorno a un pixel (HDR, Drammatico, Bagliore, Sfocatura…) usano una copia piccola della
 * foto ([Plane], circa 512 px) sfocata e ingrandita al volo: così costano poco e la copia salvata somiglia all'anteprima
 * qualunque sia la grandezza. Le posizioni (centro della vignettatura, cornici, luce di Retrò) sono in quote del riquadro
 * finale, dopo il ritaglio ([EffectSpace]). Logica pura: si prova in `EffectsTest`.
 */
enum class EffectKind(val styles: Int, val centered: Boolean = false) {
    Hdr(4), Drama(4), Glow(4), Tonal(0), Vintage(4), Grain(4), Retro(4), Grunge(5),
    BlackWhite(6), Noir(4), Blur(2, centered = true), Double(6), Vignette(0, centered = true), Frame(5),
}

/** I tre cursori di ogni effetto: da -1 o da 0 fino a 1, e il valore di partenza. */
data class EffectParam(val from: Float, val start: Float)

val EffectKind.params: List<EffectParam>
    get() = when (this) {
        EffectKind.Hdr -> listOf(EffectParam(0f, 0.5f), EffectParam(-1f, 0f), EffectParam(-1f, 0f))
        EffectKind.Drama -> listOf(EffectParam(0f, 0.6f), EffectParam(-1f, 0f), EffectParam(-1f, 0f))
        EffectKind.Glow -> listOf(EffectParam(0f, 0.5f), EffectParam(-1f, 0f), EffectParam(-1f, 0f))
        EffectKind.Tonal -> listOf(EffectParam(-1f, 0.4f), EffectParam(-1f, 0.4f), EffectParam(-1f, 0.4f))
        EffectKind.Vintage -> listOf(EffectParam(0f, 0.6f), EffectParam(-1f, 0f), EffectParam(0f, 0.4f))
        EffectKind.Grain -> listOf(EffectParam(0f, 0.4f), EffectParam(0f, 0.5f))
        EffectKind.Retro -> listOf(EffectParam(0f, 0.6f), EffectParam(-1f, 0f), EffectParam(-1f, 0f))
        EffectKind.Grunge -> listOf(EffectParam(0f, 0.6f), EffectParam(-1f, 0.2f), EffectParam(-1f, -0.3f))
        EffectKind.BlackWhite -> listOf(EffectParam(-1f, 0f), EffectParam(-1f, 0.2f), EffectParam(0f, 0f))
        EffectKind.Noir -> listOf(EffectParam(-1f, 0f), EffectParam(0f, 0.3f), EffectParam(0f, 0.4f))
        EffectKind.Blur -> listOf(EffectParam(0f, 0.6f), EffectParam(0f, 0.4f), EffectParam(0f, 0.35f))
        EffectKind.Double -> listOf(EffectParam(0f, 0.5f))
        EffectKind.Vignette -> listOf(EffectParam(-1f, 0.5f), EffectParam(-1f, 0f), EffectParam(0f, 0.5f))
        EffectKind.Frame -> listOf(EffectParam(0f, 0.4f))
    }

data class Effect(
    val id: Long,
    val kind: EffectKind,
    val style: Int = 0,
    val a: Float = 0f,
    val b: Float = 0f,
    val c: Float = 0f,
    /** Centro (Sfocatura, Vignettatura), in quote del riquadro finale. */
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    /** La seconda foto della Doppia esposizione. */
    val second: String? = null,
    val mask: StepMask = StepMask(),
) {
    fun param(i: Int) = when (i) { 0 -> a; 1 -> b; else -> c }
    fun withParam(i: Int, v: Float) = when (i) { 0 -> copy(a = v); 1 -> copy(b = v); else -> copy(c = v) }
}

fun newEffect(kind: EffectKind, id: Long, second: String? = null): Effect {
    val p = kind.params
    return Effect(id, kind, a = p.getOrNull(0)?.start ?: 0f, b = p.getOrNull(1)?.start ?: 0f, c = p.getOrNull(2)?.start ?: 0f, second = second)
}

/** Per la pila: la forza in percento. */
fun Effect.summary(): String = "${(a * 100).roundToInt()} %"

/** Un'immagine piccola a canali separati (0..255), per guardare attorno ai pixel senza leggere tutta la foto. */
class Plane(val w: Int, val h: Int, val r: FloatArray, val g: FloatArray, val b: FloatArray) {
    val short: Int get() = min(w, h)

    fun blurred(radius: Int): Plane {
        // Due passate a scatola: quasi una gaussiana, senza bordi a gradino.
        fun twice(ch: FloatArray) = boxBlur(boxBlur(ch, w, h, radius), w, h, radius)
        return Plane(w, h, twice(r), twice(g), twice(b))
    }

    /** Il colore in un punto (quote 0..1), interpolato, in [out] (r, g, b). */
    fun sample(fx: Float, fy: Float, out: FloatArray) {
        val x = (fx * w - 0.5f).coerceIn(0f, w - 1f)
        val y = (fy * h - 0.5f).coerceIn(0f, h - 1f)
        val x0 = x.toInt(); val y0 = y.toInt()
        val x1 = min(w - 1, x0 + 1); val y1 = min(h - 1, y0 + 1)
        val tx = x - x0; val ty = y - y0
        val i00 = y0 * w + x0; val i10 = y0 * w + x1; val i01 = y1 * w + x0; val i11 = y1 * w + x1
        fun mix(c: FloatArray) = (c[i00] * (1 - tx) + c[i10] * tx) * (1 - ty) + (c[i01] * (1 - tx) + c[i11] * tx) * ty
        out[0] = mix(r); out[1] = mix(g); out[2] = mix(b)
    }

    companion object {
        /** Riduce pixel ARGB a non più di [maxSide] per lato, con la media dei pixel di ogni casella. */
        fun of(px: IntArray, w: Int, h: Int, maxSide: Int = 512): Plane {
            val k = max(1, (max(w, h) + maxSide - 1) / maxSide)
            val sw = max(1, w / k); val sh = max(1, h / k)
            val r = FloatArray(sw * sh); val g = FloatArray(sw * sh); val b = FloatArray(sw * sh)
            for (y in 0 until sh) for (x in 0 until sw) {
                var sr = 0; var sg = 0; var sb = 0; var n = 0
                for (yy in y * k until min(h, y * k + k)) for (xx in x * k until min(w, x * k + k)) {
                    val c = px[yy * w + xx]; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF; n++
                }
                val i = y * sw + x
                r[i] = sr.toFloat() / n; g[i] = sg.toFloat() / n; b[i] = sb.toFloat() / n
            }
            return Plane(sw, sh, r, g, b)
        }
    }
}

/**
 * Da un pixel (x, y) dell'immagine su cui si lavora alle quote (u, v) del riquadro finale. Nella copia salvata gli effetti
 * lavorano sulla foto già raddrizzata ([angle] = 0); nell'anteprima sulla foto prima di raddrizzarla, ruotata poi a schermo.
 */
class EffectSpace(private val ux: Float, private val uy: Float, private val u0: Float, private val vx: Float, private val vy: Float, private val v0: Float, val aspect: Float) {
    fun u(x: Float, y: Float) = ux * x + uy * y + u0
    fun v(x: Float, y: Float) = vx * x + vy * y + v0

    companion object {
        fun of(w: Int, h: Int, crop: Crop, angle: Float = 0f, scale: Float = 1f): EffectSpace {
            val rad = Math.toRadians(angle.toDouble())
            val ca = cos(rad).toFloat() * scale; val sa = sin(rad).toFloat() * scale
            val cw = max(1e-4f, crop.right - crop.left); val ch = max(1e-4f, crop.bottom - crop.top)
            // X = W/2 + ca·(x − W/2) − sa·(y − H/2), poi u = (X/W − left)/cw; lo stesso per Y e v.
            val hw = w / 2f; val hh = h / 2f
            val ux = ca / w / cw; val uy = -sa / w / cw
            val u0 = ((hw - ca * hw + sa * hh) / w - crop.left) / cw
            val vx = sa / h / ch; val vy = ca / h / ch
            val v0 = ((hh - sa * hw - ca * hh) / h - crop.top) / ch
            return EffectSpace(ux, uy, u0, vx, vy, v0, (cw * w) / (ch * h))
        }
    }
}

/** Ciò che un effetto prepara prima di passare sui pixel: copie sfocate e la seconda foto. */
class EffectInput(val low: Plane? = null, val second: Plane? = null)

/** Raggio della sfocatura di guida, in quote del lato corto della copia piccola. */
private fun Effect.guideRadius(): Float? = when (kind) {
    EffectKind.Hdr -> floatArrayOf(0.06f, 0.035f, 0.018f, 0.06f)[style]
    EffectKind.Drama -> 0.045f
    EffectKind.Glow -> floatArrayOf(0.015f, 0.03f, 0.05f, 0.08f)[style]
    EffectKind.Tonal -> 0.04f
    EffectKind.Grunge -> 0.03f
    EffectKind.Blur -> 0.012f + a * 0.03f
    else -> null
}

val Effect.needsGuide: Boolean get() = guideRadius() != null

/** Prepara l'effetto dalla copia piccola dell'immagine com'è arrivata a questo passo. */
fun prepareEffect(e: Effect, small: Plane?, second: Plane? = null): EffectInput {
    val rad = e.guideRadius()
    val low = if (rad != null && small != null) small.blurred(max(1, (rad * small.short).roundToInt())) else null
    return EffectInput(low, second)
}

private fun hash(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 32767.5f - 1f
}

/** Rumore morbido da -1 a 1. */
fun valueNoise(x: Float, y: Float, seed: Int = 0): Float {
    val ix = floor(x).toInt(); val iy = floor(y).toInt()
    var fx = x - ix; var fy = y - iy
    fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
    val a = hash(ix, iy, seed); val b = hash(ix + 1, iy, seed)
    val c = hash(ix, iy + 1, seed); val d = hash(ix + 1, iy + 1, seed)
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy
}

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b

private val VintageTint = arrayOf(floatArrayOf(255f, 214f, 150f), floatArrayOf(255f, 188f, 186f), floatArrayOf(160f, 205f, 200f), floatArrayOf(226f, 186f, 136f))
private val RetroLeak = arrayOf(floatArrayOf(255f, 120f, 60f), floatArrayOf(255f, 80f, 140f), floatArrayOf(255f, 200f, 80f), floatArrayOf(120f, 160f, 255f))
private val RetroCorner = arrayOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)
private val GrungeTint = arrayOf(floatArrayOf(14f, 4f, -12f), floatArrayOf(-6f, 4f, 10f), floatArrayOf(10f, 10f, -14f), floatArrayOf(4f, -4f, 6f), floatArrayOf(0f, 0f, 0f))
/** Filtri colorati del bianco e nero: Neutro, Rosso, Arancio, Giallo, Verde, Blu. */
private val BwMix = arrayOf(
    floatArrayOf(0.2126f, 0.7152f, 0.0722f), floatArrayOf(0.8f, 0.3f, -0.1f), floatArrayOf(0.6f, 0.45f, -0.05f),
    floatArrayOf(0.45f, 0.55f, 0f), floatArrayOf(0.1f, 0.75f, 0.15f), floatArrayOf(0f, 0.3f, 0.7f),
)

/**
 * Applica [e] a una fascia di pixel ARGB ([px], larga [w], [rows] righe dalla riga [y0]) di un'immagine
 * [fullW]×[fullH]. [space] dà le quote del riquadro finale. Il pennello del passo si applica fuori, con [blendMasked].
 */
fun applyEffect(px: IntArray, w: Int, rows: Int, y0: Int, fullW: Int, fullH: Int, e: Effect, input: EffectInput, space: EffectSpace) {
    val short = min(fullW, fullH).toFloat()
    val low = FloatArray(3)
    val sec = FloatArray(3)
    val asp = space.aspect
    // Il riquadro finale in quote del suo lato corto.
    val fw = if (asp >= 1f) asp else 1f
    val fh = if (asp >= 1f) 1f else 1f / asp
    for (yy in 0 until rows) {
        val y = y0 + yy
        val fy = (y + 0.5f) / fullH
        for (x in 0 until w) {
            val i = yy * w + x
            val c = px[i]
            var r = ((c shr 16) and 0xFF).toFloat()
            var g = ((c shr 8) and 0xFF).toFloat()
            var b = (c and 0xFF).toFloat()
            val fx = (x + 0.5f) / fullW
            val u = space.u(x + 0.5f, y + 0.5f)
            val v = space.v(x + 0.5f, y + 0.5f)
            input.low?.sample(fx, fy, low)
            // Rumore legato alla grandezza dell'immagine: la grana è la stessa in anteprima e nella copia salvata.
            val nx = x / short * 700f; val ny = y / short * 700f
            fun saturate(k: Float) { val l = luma(r, g, b); r = l + (r - l) * k; g = l + (g - l) * k; b = l + (b - l) * k }
            fun localContrast(k: Float) {
                // Le differenze grandi (un orizzonte chiaro contro una collina scura) si smorzano: niente aloni.
                val raw = luma(r, g, b) - luma(low[0], low[1], low[2])
                val d = raw / (1f + abs(raw) / 28f)
                r += k * d; g += k * d; b += k * d
            }
            fun darkEdges(amount: Float) {
                val d = hypot((u - 0.5f) * fw, (v - 0.5f) * fh) / hypot(0.5f * fw, 0.5f * fh)
                val k = 1f - amount * smooth(0.45f, 1.05f, d)
                r *= k; g *= k; b *= k
            }
            when (e.kind) {
                EffectKind.Hdr -> {
                    val amt = floatArrayOf(1.4f, 0.9f, 1.6f, 2.6f)[e.style]
                    localContrast(e.a * amt)
                    val l = luma(r, g, b) / 255f
                    val lift = e.b * 45f * (1f - l * 0.6f)
                    r += lift; g += lift; b += lift
                    saturate(1f + e.a * 0.35f + e.c * 0.8f)
                }
                EffectKind.Drama -> {
                    val (k, light, sat) = when (e.style) {
                        0 -> Triple(2.2f, 6f, -0.25f); 1 -> Triple(2.8f, 14f, -0.4f)
                        2 -> Triple(2.2f, -22f, -0.3f); else -> Triple(2.8f, -38f, -0.45f)
                    }
                    localContrast(e.a * k)
                    val add = light * e.a + e.c * 40f
                    r += add; g += add; b += add
                    saturate(1f + sat * e.a + e.b * 0.8f)
                }
                EffectKind.Glow -> {
                    val k = e.a * 0.85f
                    r = 255f - (255f - r) * (255f - low[0] * k) / 255f
                    g = 255f - (255f - g) * (255f - low[1] * k) / 255f
                    b = 255f - (255f - b) * (255f - low[2] * k) / 255f
                    saturate(1f + e.b * 0.8f)
                    r += e.c * 25f; b -= e.c * 25f
                }
                EffectKind.Tonal -> {
                    val l = luma(r, g, b) / 255f
                    val hi = smooth(0.45f, 0.95f, l); val sh = 1f - smooth(0.05f, 0.55f, l)
                    val mid = max(0f, 1f - abs(l - 0.5f) * 2.2f)
                    localContrast(1.6f * (e.a * hi + e.b * mid + e.c * sh))
                }
                EffectKind.Vintage -> {
                    val t = VintageTint[e.style]
                    val l = luma(r, g, b) / 255f
                    val k = 0.4f * e.a
                    r = r * (1 - k) + t[0] * l * k; g = g * (1 - k) + t[1] * l * k; b = b * (1 - k) + t[2] * l * k
                    // Neri sbiaditi, come una stampa vecchia.
                    val f = 0.22f * e.a
                    r = r * (1 - f) + 205f * f; g = g * (1 - f) + 192f * f; b = b * (1 - f) + 170f * f
                    saturate(1f + e.b * 0.8f - e.a * 0.15f)
                    darkEdges(e.c * 0.6f)
                }
                EffectKind.Grain -> {
                    val n = valueNoise(nx, ny, 7) * 0.7f + valueNoise(nx * 2.3f, ny * 2.3f, 11) * 0.3f
                    val amp = e.a * 50f
                    r += n * amp; g += n * amp; b += n * amp
                    val s = e.b
                    when (e.style) {
                        1 -> { r += 18f * s; b -= 18f * s }
                        2 -> { r -= 14f * s; b += 18f * s }
                        3 -> { val l = luma(r, g, b); r = r + (l * 1.07f + 12f - r) * s; g = g + (l * 0.95f + 4f - g) * s; b = b + (l * 0.78f - b) * s }
                    }
                }
                EffectKind.Retro -> {
                    val (cx, cy) = RetroCorner[e.style]
                    val d2 = ((u - cx) * fw) * ((u - cx) * fw) + ((v - cy) * fh) * ((v - cy) * fh)
                    val leak = exp(-d2 / 0.35f) * e.a * 0.85f
                    val lc = RetroLeak[e.style]
                    r = 255f - (255f - r) * (255f - lc[0] * leak) / 255f
                    g = 255f - (255f - g) * (255f - lc[1] * leak) / 255f
                    b = 255f - (255f - b) * (255f - lc[2] * leak) / 255f
                    val f = 0.15f * e.a
                    r = r * (1 - f) + 200f * f; g = g * (1 - f) + 186f * f; b = b * (1 - f) + 214f * f
                    val add = e.c * 40f
                    r += add; g += add; b += add
                    saturate(1f + e.b * 0.8f - 0.1f * e.a)
                    darkEdges(0.3f * e.a)
                }
                EffectKind.Grunge -> {
                    val sx = x / short; val sy = y / short
                    val seed = 31 + e.style * 17
                    val t = valueNoise(sx * 6f, sy * 6f, seed) * 0.5f + valueNoise(sx * 22f, sy * 22f, seed + 1) * 0.3f + valueNoise(sx * 90f, sy * 90f, seed + 2) * 0.2f
                    val k = 1f + t * e.a * 0.32f
                    r *= k; g *= k; b *= k
                    localContrast(e.b * 2f)
                    val tint = GrungeTint[e.style]
                    r += tint[0] * e.a; g += tint[1] * e.a; b += tint[2] * e.a
                    saturate(1f + e.c * 0.8f)
                    darkEdges(0.35f * e.a)
                }
                EffectKind.BlackWhite -> {
                    val m = BwMix[e.style]
                    var l = m[0] * r + m[1] * g + m[2] * b
                    l += e.a * 60f
                    l = (l - 128f) * (1f + e.b) + 128f
                    l += valueNoise(nx, ny, 3) * e.c * 45f
                    r = l; g = l; b = l
                }
                EffectKind.Noir -> {
                    var l = luma(r, g, b)
                    val k = floatArrayOf(1.3f, 1.5f, 1.75f, 2.1f)[e.style]
                    l = (l - 118f) * k + 118f + e.a * 50f
                    // Lavaggio: neri e grigi verso un argento chiaro.
                    l = l * (1 - 0.35f * e.b) + 205f * 0.35f * e.b
                    l += valueNoise(nx, ny, 5) * e.c * 50f
                    r = l; g = l; b = l
                    darkEdges(0.45f)
                }
                EffectKind.Blur -> {
                    val dx = (u - e.x) * fw; val dy = (v - e.y) * fh
                    val d = if (e.style == 0) hypot(dx, dy) else abs(dy)
                    val r0 = 0.05f + e.c * 0.6f
                    val k = smooth(r0, r0 + 0.04f + e.b * 0.6f, d) * min(1f, 0.4f + e.a)
                    r += (low[0] - r) * k; g += (low[1] - g) * k; b += (low[2] - b) * k
                }
                EffectKind.Double -> {
                    val s = input.second
                    if (s != null && u in 0f..1f && v in 0f..1f) {
                        val sa = s.w.toFloat() / s.h
                        val su = if (sa > asp) 0.5f + (u - 0.5f) * asp / sa else u
                        val sv = if (sa > asp) v else 0.5f + (v - 0.5f) * sa / asp
                        s.sample(su, sv, sec)
                        fun blend(p: Float, q: Float) = when (e.style) {
                            1 -> max(p, q)
                            2 -> min(p, q)
                            3 -> 255f - (255f - p) * (255f - q) / 255f
                            4 -> p * q / 255f
                            5 -> if (p < 128f) 2f * p * q / 255f else 255f - 2f * (255f - p) * (255f - q) / 255f
                            else -> q
                        }
                        val k = e.a
                        r += (blend(r, sec[0]) - r) * k; g += (blend(g, sec[1]) - g) * k; b += (blend(b, sec[2]) - b) * k
                    }
                }
                EffectKind.Vignette -> {
                    val d = hypot((u - e.x) * fw, (v - e.y) * fh)
                    val r0 = 0.12f + e.c * 0.75f
                    val outer = smooth(r0, r0 + 0.55f, d)
                    val inner = 1f - smooth(0f, r0, d)
                    val k = 1f - e.a * 0.75f * outer + e.b * 0.35f * inner
                    r *= k; g *= k; b *= k
                }
                EffectKind.Frame -> if (u in 0f..1f && v in 0f..1f) {
                    val X = u * fw; val Y = v * fh
                    val bw = 0.008f + e.a * 0.09f
                    val edge = min(min(X, fw - X), min(Y, fh - Y))
                    val rr = when (e.style) { 2 -> bw * 1.6f; 4 -> bw * 0.7f; else -> 0f }
                    val qx = max(max(bw + rr - X, X - (fw - bw - rr)), 0f)
                    val qy = max(max(bw + rr - Y, Y - (fh - bw - rr)), 0f)
                    val outside = edge < bw || (rr > 0f && hypot(qx, qy) > rr)
                    val ink = if (e.style == 1 || e.style == 4) 12f else 248f
                    if (e.style == 3) {
                        val k = 1f - smooth(0f, bw * 2.5f, edge)
                        r += (ink - r) * k; g += (ink - g) * k; b += (ink - b) * k
                    } else if (outside) { r = ink; g = ink; b = ink }
                    else if (e.style == 4 && edge < bw + 0.004f) { r = 92f; g = 92f; b = 92f }
                }
            }
            px[i] = (c and -0x1000000) or (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
        }
    }
}

/**
 * Tutti gli effetti su un'immagine intera ([px], [w]×[h]), come nell'anteprima: per ogni effetto la copia piccola si rifà
 * dall'immagine com'è a quel passo. [masks] = maschere dei pennelli già pronte; [seconds] = seconde foto per uri.
 */
fun applyEffects(px: IntArray, w: Int, h: Int, effects: List<Effect>, space: EffectSpace, seconds: Map<String, Plane> = emptyMap()) {
    for (e in effects) {
        val small = if (e.needsGuide) Plane.of(px, w, h) else null
        val input = prepareEffect(e, small, e.second?.let { seconds[it] })
        val before = if (e.mask.isEmpty) null else px.copyOf()
        applyEffect(px, w, h, 0, w, h, e, input, space)
        if (before != null) blendMasked(px, before, w, h, 0, w, h, maskGrid(e.mask, w / h.toFloat(), 384))
    }
}

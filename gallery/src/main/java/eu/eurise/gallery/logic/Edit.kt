package eu.eurise.gallery.logic

import kotlin.math.roundToInt

/**
 * Luce, contrasto, saturazione, calore, ombre, alte luci e vignetta, da -1 a 1 (0 lascia com'è).
 * Le prime quattro sono una matrice di colore; ombre e alte luci una curva ([toneLut]); la vignetta un velo ai bordi.
 */
data class Adjust(
    val light: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val vignette: Float = 0f,
) {
    val isNeutral: Boolean get() = matrixNeutral && hasCurve.not() && vignette == 0f
    val matrixNeutral: Boolean get() = light == 0f && contrast == 0f && saturation == 0f && warmth == 0f
    val hasCurve: Boolean get() = shadows != 0f || highlights != 0f

    operator fun plus(o: Adjust) = Adjust(
        (light + o.light).coerceIn(-1f, 1f), (contrast + o.contrast).coerceIn(-1f, 1f), (saturation + o.saturation).coerceIn(-1f, 1f),
        (warmth + o.warmth).coerceIn(-1f, 1f), (shadows + o.shadows).coerceIn(-1f, 1f), (highlights + o.highlights).coerceIn(-1f, 1f),
        (vignette + o.vignette).coerceIn(-1f, 1f),
    )

    operator fun times(k: Float) = Adjust(light * k, contrast * k, saturation * k, warmth * k, shadows * k, highlights * k, vignette * k)
}

/** I filtri: ognuno è un insieme di regolazioni, dosato con l'intensità. */
enum class Look(val adjust: Adjust) {
    None(Adjust()),
    Lisbona(Adjust(light = 0.05f, saturation = 0.3f, warmth = 0.45f, contrast = 0.1f, vignette = 0.25f)),
    Perla(Adjust(light = 0.12f, contrast = -0.15f, saturation = -0.3f, shadows = 0.3f)),
    Notte(Adjust(light = -0.15f, contrast = 0.2f, warmth = -0.5f, saturation = -0.1f, vignette = 0.35f)),
    Vivace(Adjust(saturation = 0.45f, contrast = 0.2f)),
    Caldo(Adjust(warmth = 0.6f, saturation = 0.1f)),
    Freddo(Adjust(warmth = -0.6f)),
    Pellicola(Adjust(contrast = -0.1f, saturation = -0.15f, warmth = 0.2f, shadows = 0.35f, highlights = -0.3f, vignette = 0.2f)),
    BiancoNero(Adjust(saturation = -1f, contrast = 0.15f)),
}

/**
 * La curva di ombre e alte luci: per ogni valore 0..255 il nuovo valore. Le ombre agiscono sui toni scuri, le alte
 * luci su quelli chiari; il peso resta basso agli estremi, così il nero e il bianco non si spostano e la curva sale sempre.
 */
fun toneLut(shadows: Float, highlights: Float): IntArray = IntArray(256) { v ->
    val x = v / 255f
    val ws = 4f * x * (1 - x) * (1 - x)
    val wh = 4f * x * x * (1 - x)
    ((x + 0.22f * shadows * ws + 0.22f * highlights * wh).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}

/**
 * "Automatico": luce e contrasto proposti dall'istogramma della luminosità (256 valori). Una foto scura si schiarisce,
 * una slavata prende contrasto; una foto già equilibrata resta quasi com'è.
 */
fun autoAdjust(histogram: IntArray): Adjust {
    val total = histogram.sum().takeIf { it > 0 } ?: return Adjust()
    var mean = 0.0
    histogram.forEachIndexed { i, n -> mean += i.toDouble() * n }
    mean /= total * 255.0
    fun percentile(p: Double): Int {
        var acc = 0L
        histogram.forEachIndexed { i, n -> acc += n; if (acc >= total * p) return i }
        return 255
    }
    val spread = (percentile(0.98) - percentile(0.02)) / 255f
    val light = ((0.48 - mean) * 1.3).toFloat().coerceIn(-0.45f, 0.45f)
    val contrast = ((0.82f - spread) * 1.1f).coerceIn(-0.2f, 0.5f)
    val shadows = if (mean < 0.35) 0.3f else 0f
    return Adjust(light = light.round2(), contrast = contrast.round2(), shadows = shadows)
}

private fun Float.round2() = (this * 100).roundToInt() / 100f

/** Matrice di colore 4×5 di Android e Compose, con gli scostamenti in 0..255. */
fun adjustMatrix(a: Adjust): FloatArray {
    val s = 1f + a.saturation
    val c = 1f + a.contrast * 0.6f
    val shift = 127.5f * (1 - c) + a.light * 70f
    val r = 0.2126f * (1 - s)
    val g = 0.7152f * (1 - s)
    val b = 0.0722f * (1 - s)
    return floatArrayOf(
        (r + s) * c, g * c, b * c, 0f, shift + a.warmth * 28f,
        r * c, (g + s) * c, b * c, 0f, shift,
        r * c, g * c, (b + s) * c, 0f, shift - a.warmth * 28f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** Ritaglio in frazioni dell'immagine (0..1). */
data class Crop(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val isFull: Boolean get() = left == 0f && top == 0f && right == 1f && bottom == 1f
}

/** Il ritaglio in pixel: x, y, larghezza e altezza, sempre dentro l'immagine e mai vuoto. */
fun cropPixels(crop: Crop, width: Int, height: Int): IntArray {
    val x = (crop.left * width).roundToInt().coerceIn(0, width - 1)
    val y = (crop.top * height).roundToInt().coerceIn(0, height - 1)
    val w = ((crop.right - crop.left) * width).roundToInt().coerceIn(1, width - x)
    val h = ((crop.bottom - crop.top) * height).roundToInt().coerceIn(1, height - y)
    return intArrayOf(x, y, w, h)
}

/** Sposta un angolo o tutto il riquadro senza uscire dall'immagine e senza farlo più piccolo di [min]. */
fun dragCrop(crop: Crop, corner: Int, dx: Float, dy: Float, min: Float = 0.08f): Crop = when (corner) {
    0 -> crop.copy(left = (crop.left + dx).coerceIn(0f, crop.right - min), top = (crop.top + dy).coerceIn(0f, crop.bottom - min))
    1 -> crop.copy(right = (crop.right + dx).coerceIn(crop.left + min, 1f), top = (crop.top + dy).coerceIn(0f, crop.bottom - min))
    2 -> crop.copy(left = (crop.left + dx).coerceIn(0f, crop.right - min), bottom = (crop.bottom + dy).coerceIn(crop.top + min, 1f))
    3 -> crop.copy(right = (crop.right + dx).coerceIn(crop.left + min, 1f), bottom = (crop.bottom + dy).coerceIn(crop.top + min, 1f))
    else -> {
        val w = crop.right - crop.left
        val h = crop.bottom - crop.top
        val l = (crop.left + dx).coerceIn(0f, 1f - w)
        val t = (crop.top + dy).coerceIn(0f, 1f - h)
        Crop(l, t, l + w, t + h)
    }
}

/** Come appare una scritta: normale, marcata, su un fondo arrotondato o con il contorno. */
enum class MarkStyle { Plain, Bold, Box, Outline }

/** Una scritta: centro in frazioni dell'immagine, grandezza in frazioni del lato corto, così regge le rotazioni. */
data class TextMark(val id: Long, val text: String, val x: Float, val y: Float, val size: Float, val color: Int, val style: MarkStyle = MarkStyle.Bold)

/** La punta del tratto: penna, pennarello (più morbido e largo), evidenziatore (trasparente, a punta piatta). */
enum class Nib { Pen, Marker, Highlighter }

/** Un tratto a mano libera, con le stesse misure di [TextMark]. */
data class InkStroke(val points: List<Pair<Float, Float>>, val width: Float, val color: Int, val nib: Nib = Nib.Pen)

/** La gomma: toglie i tratti che passano entro [radius] (frazioni dell'immagine) dal punto toccato. */
fun erase(strokes: List<InkStroke>, x: Float, y: Float, radius: Float): List<InkStroke> =
    strokes.filterNot { s -> s.points.any { (px, py) -> (px - x) * (px - x) + (py - y) * (py - y) < radius * radius } }

/** Tutto ciò che si è fatto a una foto; l'originale non si tocca mai. */
data class EditState(
    val turns: Int = 0,
    val flip: Boolean = false,
    val straighten: Float = 0f,
    val crop: Crop = Crop(),
    val adjust: Adjust = Adjust(),
    val texts: List<TextMark> = emptyList(),
    val strokes: List<InkStroke> = emptyList(),
    val look: Look = Look.None,
    val lookStrength: Float = 1f,
) {
    /** Le regolazioni a mano più il filtro dosato: quello che si applica davvero. */
    val effective: Adjust get() = if (look == Look.None) adjust else adjust + look.adjust * lookStrength
}

private fun EditState.mapPoints(map: (Float, Float) -> Pair<Float, Float>) = copy(
    crop = Crop(),
    texts = texts.map { t -> map(t.x, t.y).let { (x, y) -> t.copy(x = x, y = y) } },
    strokes = strokes.map { s -> s.copy(points = s.points.map { (x, y) -> map(x, y) }) },
)

/** Un quarto di giro in senso orario: scritte e tratti girano con la foto, il ritaglio riparte. */
fun EditState.rotated(): EditState = mapPoints { x, y -> (1 - y) to x }.copy(turns = (turns + 1) % 4)

/** Specchio orizzontale di ciò che si vede ora; si applica prima della rotazione, quindi i giri si invertono. */
fun EditState.flipped(): EditState = mapPoints { x, y -> (1 - x) to y }.copy(flip = !flip, turns = (4 - turns) % 4, straighten = -straighten)

/** Ingrandimento che evita angoli vuoti raddrizzando di [degrees] un'immagine di [w] per [h]. */
fun straightenScale(degrees: Float, w: Float, h: Float): Float {
    val a = kotlin.math.abs(Math.toRadians(degrees.toDouble()))
    val c = kotlin.math.cos(a).toFloat()
    val s = kotlin.math.sin(a).toFloat()
    return maxOf((w * c + h * s) / w, (w * s + h * c) / h)
}

/** Il ritaglio più grande del formato chiesto, al centro. [ratio] è il rapporto larghezza/altezza in frazioni dell'immagine. */
fun ratioCrop(ratio: Float): Crop {
    var w = 1f
    var h = 1f / ratio
    if (h > 1f) { h = 1f; w = ratio }
    return Crop((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2)
}

/** Come [dragCrop], tenendo il formato: l'angolo opposto resta fermo; un gesto che uscirebbe dall'immagine non cambia nulla. */
fun dragCropRatio(crop: Crop, corner: Int, dx: Float, dy: Float, ratio: Float): Crop {
    val moved = dragCrop(crop, corner, dx, dy)
    if (corner !in 0..3) return moved
    val h = (moved.right - moved.left) / ratio
    val out = if (corner < 2) moved.copy(top = crop.bottom - h, bottom = crop.bottom) else moved.copy(top = crop.top, bottom = crop.top + h)
    return if (out.top < 0f || out.bottom > 1f) crop else out
}

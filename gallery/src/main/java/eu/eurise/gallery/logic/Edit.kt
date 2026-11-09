package eu.eurise.gallery.logic

import kotlin.math.roundToInt

/** Luce, contrasto, saturazione e calore, da -1 a 1 (0 lascia com'è). */
data class Adjust(val light: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f, val warmth: Float = 0f) {
    val isNeutral: Boolean get() = light == 0f && contrast == 0f && saturation == 0f && warmth == 0f
}

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

/** Una scritta: centro in frazioni dell'immagine, grandezza in frazioni del lato corto, così regge le rotazioni. */
data class TextMark(val id: Long, val text: String, val x: Float, val y: Float, val size: Float, val color: Int)

/** Un tratto a mano libera, con le stesse misure di [TextMark]. */
data class InkStroke(val points: List<Pair<Float, Float>>, val width: Float, val color: Int)

/** Tutto ciò che si è fatto a una foto; l'originale non si tocca mai. */
data class EditState(
    val turns: Int = 0,
    val flip: Boolean = false,
    val straighten: Float = 0f,
    val crop: Crop = Crop(),
    val adjust: Adjust = Adjust(),
    val texts: List<TextMark> = emptyList(),
    val strokes: List<InkStroke> = emptyList(),
)

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

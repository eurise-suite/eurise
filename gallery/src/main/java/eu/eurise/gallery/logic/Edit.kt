package eu.eurise.gallery.logic

import kotlin.math.roundToInt

/** Luce, contrasto e saturazione, da -1 a 1 (0 lascia com'è). */
data class Adjust(val light: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f) {
    val isNeutral: Boolean get() = light == 0f && contrast == 0f && saturation == 0f
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
        (r + s) * c, g * c, b * c, 0f, shift,
        r * c, (g + s) * c, b * c, 0f, shift,
        r * c, g * c, (b + s) * c, 0f, shift,
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

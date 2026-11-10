package eu.eurise.core.designsystem.color

import kotlin.math.abs
import kotlin.math.pow

/** Un colore come tinta (0–360), intensità e luminosità (0–1): così lo sceglie la ruota. */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/** Calcoli del selettore "Il tuo colore", senza Android: si provano con i test. Colori come 0xAARRGGBB. */
object ColorMath {

    fun toHsv(argb: Int): Hsv {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0) it + 360f else it }
        return Hsv(h, if (max == 0f) 0f else d / max, max)
    }

    fun toArgb(hsv: Hsv): Int {
        val h = ((hsv.hue % 360f) + 360f) % 360f
        val s = hsv.saturation.coerceIn(0f, 1f)
        val v = hsv.value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2f - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** "#3FA39A", "3fa39a" o "#3a9" → colore; null se non è un HEX valido. */
    fun parseHex(text: String): Int? {
        val t = text.trim().removePrefix("#")
        val full = when (t.length) {
            3 -> t.map { "$it$it" }.joinToString("")
            6 -> t
            else -> return null
        }
        if (!full.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        return (0xFF shl 24) or full.toInt(16)
    }

    fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** Luminanza relativa (WCAG). */
    fun luminance(argb: Int): Double {
        fun lin(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin((argb shr 16) and 0xFF) + 0.7152 * lin((argb shr 8) and 0xFF) + 0.0722 * lin(argb and 0xFF)
    }

    /** Contrasto tra due colori, da 1 a 21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}

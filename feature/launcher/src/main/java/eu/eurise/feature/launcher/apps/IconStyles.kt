package eu.eurise.feature.launcher.apps

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb

/**
 * Gli stili delle icone oltre ai due della suite (tavola IconeStili, approvata il 13 novembre 2026) e la forma a scelta.
 * Ogni stile ha la sua palette: il colore dell'app (tinta della categoria o dell'icona originale) si porta al colore più
 * vicino della palette, così Spotify verde resta verde, ma nel verde dello stile.
 */
object IconStyles {
    const val IOS = "ios"
    const val MATERIAL = "material"
    const val EOS = "eos"

    /** Un colore della palette: fondo (in alto e in basso per iOS, uguali per gli altri) e simbolo. */
    class Swatch(val top: Color, val bottom: Color, val symbol: Color)

    /** iOS: colori di sistema, fondo sfumato dall'alto in basso, simbolo bianco. */
    private val ios = listOf(
        0xFF7972 to 0xFF3B30, 0xFFB651 to 0xFF9500, 0xFFDC51 to 0xFFCC00, 0x74D88E to 0x34C759, 0x72C9D8 to 0x30B0C7,
        0x73C7EE to 0x32ADE6, 0x51A4FF to 0x007AFF, 0x8D8CE3 to 0x5856D6, 0xC889E8 to 0xAF52DE, 0xFF708B to 0xFF2D55,
        0xB2B2B5 to 0x8E8E93,
    ).map { (t, b) -> Swatch(rgb(t), rgb(b), Color.White) }

    /** Android: palette Material, tondo nel tono 100 e simbolo nel tono 800, niente sfumature. */
    private val material = listOf(
        0xC5CAE9 to 0x283593, 0xFFCDD2 to 0xC62828, 0xFFE0B2 to 0xE65100, 0xFFECB3 to 0xFF6F00, 0xCFD8DC to 0x37474F,
        0xBBDEFB to 0x1565C0, 0xB2DFDB to 0x00695C, 0xB2EBF2 to 0x00838F, 0xC8E6C9 to 0x2E7D32, 0xF8BBD0 to 0xAD1457,
        0xE1BEE7 to 0x6A1B9A,
    ).map { (bg, fg) -> Swatch(rgb(bg), rgb(bg), rgb(fg)) }

    /** /e/OS: tinte piene vivaci, simbolo bianco, ombra leggera. */
    private val eos = listOf(0x3949AB, 0xE53935, 0xFB8C00, 0xF9A825, 0x546E7A, 0x1E88E5, 0x00897B, 0x00ACC1, 0x43A047, 0xD81B60, 0x8E24AA)
        .map { Swatch(rgb(it), rgb(it), Color.White) }

    fun isStyle(theme: String) = theme == IOS || theme == MATERIAL || theme == EOS

    /** Il colore della palette dello stile più vicino a [tint]: per tinta, e i grigi (poco saturi) ai grigi. */
    fun swatch(theme: String, tint: Color): Swatch {
        val list = when (theme) { IOS -> ios; MATERIAL -> material; else -> eos }
        val hsl = hsl(tint)
        return list.minBy { s ->
            val c = hsl(if (theme == MATERIAL) s.symbol else s.bottom)
            val dh = Math.abs(hsl[0] - c[0]).let { minOf(it, 360f - it) } / 180f
            val grey = hsl[1] < 0.18f
            val cGrey = c[1] < 0.25f
            if (grey != cGrey) 10f + dh else dh + Math.abs(hsl[1] - c[1]) * 0.3f
        }
    }

    // ---- Forma a scelta («Griglia e icone»): vale per ogni stile e per tutte le app ----------------------------------

    const val SHAPE_AUTO = "auto"
    val shapes = listOf("circle", "rounded", "squircle", "drop", "square")

    /** La forma scelta; «automatica» è quella dello stile (iOS e /e/OS quadrato arrotondato, gli altri il cerchio). */
    fun shape(choice: String, theme: String): Shape = when (if (choice == SHAPE_AUTO) defaultShape(theme) else choice) {
        "rounded" -> RoundedCornerShape(25)
        "squircle" -> Squircle
        "drop" -> Drop
        "square" -> RoundedCornerShape(10)
        else -> CircleShape
    }

    private fun defaultShape(theme: String) = if (theme == IOS || theme == EOS) "rounded" else "circle"

    /** Lo squircle della tavola: lati dritti che curvano piano verso gli angoli. */
    private val Squircle = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        val a = 8f / 72f
        moveTo(w / 2, 0f)
        cubicTo(w * (1 - a), 0f, w, h * a, w, h / 2)
        cubicTo(w, h * (1 - a), w * (1 - a), h, w / 2, h)
        cubicTo(w * a, h, 0f, h * (1 - a), 0f, h / 2)
        cubicTo(0f, h * a, w * a, 0f, w / 2, 0f)
        close()
    }

    /** La goccia: tonda, con l'angolo in basso a destra appena smussato (8 su 72). */
    private val Drop = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        val r = w * 8f / 72f
        moveTo(0f, h / 2)
        arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, w, h), 180f, 180f, false)
        lineTo(w, h - r)
        arcTo(androidx.compose.ui.geometry.Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
        lineTo(w / 2, h)
        arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, w, h), 90f, 90f, false)
        close()
    }

    private fun rgb(v: Int) = Color(0xFF000000.toInt() or v)

    private fun hsl(c: Color): FloatArray = FloatArray(3).also { androidx.core.graphics.ColorUtils.colorToHSL(c.toArgb(), it) }
}

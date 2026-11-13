package eu.eurise.office.pdf

/** Un rettangolo nello spazio PDF (punti, origine in basso a sinistra). */
data class PdfRect(val left: Float, val bottom: Float, val right: Float, val top: Float) {
    val width get() = right - left
    val height get() = top - bottom
    fun union(o: PdfRect) = PdfRect(minOf(left, o.left), minOf(bottom, o.bottom), maxOf(right, o.right), maxOf(top, o.top))
    fun contains(x: Float, y: Float) = x in left..right && y in bottom..top
}

/** Un punto della pagina come la si vede, in frazioni (0..1) da in alto a sinistra. */
data class ViewPoint(val u: Float, val v: Float)

/**
 * Passaggio tra la pagina disegnata sullo schermo (frazioni, origine in alto a sinistra, già ruotata come la mostra
 * PdfRenderer) e lo spazio PDF della pagina (punti, origine in basso a sinistra, senza rotazione).
 * [cropLeft], [cropBottom], [cropWidth], [cropHeight]: il riquadro visibile della pagina (CropBox); [rotation]: /Rotate.
 */
class PageGeometry(
    val cropLeft: Float,
    val cropBottom: Float,
    val cropWidth: Float,
    val cropHeight: Float,
    rotation: Int,
) {
    val rotation: Int = ((rotation % 360) + 360) % 360

    /** Proporzioni della pagina come appare (larghezza / altezza). */
    val displayAspect: Float
        get() = if (rotation == 90 || rotation == 270) cropHeight / cropWidth else cropWidth / cropHeight

    /** Da schermo a PDF. */
    fun toPdf(p: ViewPoint): Pair<Float, Float> {
        val (s, t) = when (rotation) {
            90 -> p.v to 1f - p.u
            180 -> 1f - p.u to 1f - p.v
            270 -> 1f - p.v to p.u
            else -> p.u to p.v
        }
        return (cropLeft + s * cropWidth) to (cropBottom + (1f - t) * cropHeight)
    }

    /** Da PDF a schermo. */
    fun toView(x: Float, y: Float): ViewPoint {
        val s = (x - cropLeft) / cropWidth
        val t = 1f - (y - cropBottom) / cropHeight
        return when (rotation) {
            90 -> ViewPoint(1f - t, s)
            180 -> ViewPoint(1f - s, 1f - t)
            270 -> ViewPoint(t, 1f - s)
            else -> ViewPoint(s, t)
        }
    }

    /** Un rettangolo PDF come appare: (sinistra, alto, destra, basso) in frazioni. */
    fun toViewRect(r: PdfRect): FloatArray {
        val a = toView(r.left, r.bottom)
        val b = toView(r.right, r.top)
        return floatArrayOf(minOf(a.u, b.u), minOf(a.v, b.v), maxOf(a.u, b.u), maxOf(a.v, b.v))
    }

    /** Dimensione in punti sullo schermo corrispondente a [points] punti PDF, data la larghezza in pixel della pagina. */
    fun pdfPerPixel(viewWidthPx: Float): Float =
        (if (rotation == 90 || rotation == 270) cropHeight else cropWidth) / viewWidthPx
}

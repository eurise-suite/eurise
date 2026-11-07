package eu.eurise.feature.notes.doc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Il foglio di un disegno. */
enum class Paper { Blank, Lines, Grid }

/** Gli strumenti che lasciano un segno (gomma e righello sono modi dell'editor, non tratti). */
enum class Tool { Pen, Pencil, Marker, Highlighter }

/** Un punto del tratto in unità del disegno (largo [DrawingBlock.WIDTH]), con la pressione da 0 a 1. */
data class InkPoint(val x: Float, val y: Float, val p: Float = 0.5f)

/**
 * Un tratto.
 *
 * @property tint nome di una tinta della suite; nullo per l'inchiostro (nero, o chiaro nel tema scuro).
 * @property size spessore di base in unità del disegno.
 * @property shape vero se il tratto è una forma riconosciuta (linea, ellisse, rettangolo).
 */
data class Stroke(
    val tool: Tool,
    val tint: String?,
    val size: Float,
    val points: List<InkPoint>,
    val shape: Boolean = false,
)

/** Un disegno a mano dentro la nota, salvato come SVG. */
data class DrawingBlock(
    override val id: Long,
    val height: Int = DEFAULT_HEIGHT,
    val paper: Paper = Paper.Grid,
    val strokes: List<Stroke> = emptyList(),
) : Block {
    companion object {
        /** Larghezza fissa del disegno in unità: lo si scala alla larghezza dello schermo. */
        const val WIDTH = 1000
        const val DEFAULT_HEIGHT = 1000
        const val MAX_HEIGHT = 1600
        /** Passo di righe e quadretti. */
        const val PAPER_STEP = 50f
    }
}

/** Opacità di ogni strumento: l'evidenziatore lascia vedere sotto, la matita è appena più leggera. */
fun Tool.alpha(): Float = when (this) {
    Tool.Pen -> 1f
    Tool.Pencil -> 0.82f
    Tool.Marker -> 0.95f
    Tool.Highlighter -> 0.35f
}

/** Raggio del tratto in un punto: penna e matita seguono la pressione del pennino, gli altri no. */
fun Stroke.radiusAt(p: Float): Float = when (tool) {
    Tool.Pen -> size * (0.55f + 0.9f * p) / 2f
    Tool.Pencil -> size * 0.7f * (0.6f + 0.8f * p) / 2f
    Tool.Marker -> size * 2.2f / 2f
    Tool.Highlighter -> size * 4.5f / 2f
}

/**
 * Il contorno del tratto come poligono chiuso (x, y alternati): si allarga a destra e a sinistra di ogni punto
 * secondo il suo raggio e si chiudono le estremità con mezzi cerchi. Lo stesso contorno si disegna nell'app e
 * si scrive nell'SVG, così il disegno appare identico ovunque.
 */
fun Stroke.outline(): FloatArray {
    val pts = smooth(points)
    if (pts.isEmpty()) return FloatArray(0)
    if (pts.size == 1 || pts.all { hypot(it.x - pts[0].x, it.y - pts[0].y) < 0.5f }) {
        return circle(pts[0].x, pts[0].y, radiusAt(pts[0].p), 16)
    }
    val left = ArrayList<Float>(pts.size * 2)
    val right = ArrayList<Float>(pts.size * 2)
    for (i in pts.indices) {
        val a = pts[max(0, i - 1)]
        val b = pts[min(pts.lastIndex, i + 1)]
        var dx = b.x - a.x
        var dy = b.y - a.y
        val len = hypot(dx, dy).takeIf { it > 0f } ?: 1f
        dx /= len
        dy /= len
        val r = radiusAt(pts[i].p)
        left += pts[i].x - dy * r; left += pts[i].y + dx * r
        right += pts[i].x + dy * r; right += pts[i].y - dx * r
    }
    val out = ArrayList<Float>(left.size * 2 + 40)
    out.addAll(left)
    // Estremità finale: mezzo cerchio da sinistra a destra attorno all'ultimo punto.
    cap(out, pts.last(), pts[pts.lastIndex - 1], radiusAt(pts.last().p))
    for (i in right.size - 2 downTo 0 step 2) { out += right[i]; out += right[i + 1] }
    cap(out, pts.first(), pts[1], radiusAt(pts.first().p))
    return out.toFloatArray()
}

private fun cap(out: MutableList<Float>, end: InkPoint, prev: InkPoint, r: Float) {
    val angle = atan2(end.y - prev.y, end.x - prev.x)
    val steps = 8
    for (k in 1 until steps) {
        val a = angle + PI.toFloat() / 2f - PI.toFloat() * k / steps
        out += end.x + cos(a) * r
        out += end.y + sin(a) * r
    }
}

private fun circle(cx: Float, cy: Float, r: Float, n: Int): FloatArray {
    val out = FloatArray(n * 2)
    for (k in 0 until n) {
        val a = 2 * PI.toFloat() * k / n
        out[2 * k] = cx + cos(a) * r
        out[2 * k + 1] = cy + sin(a) * r
    }
    return out
}

/** Media mobile leggera: toglie il tremolio del dito senza arrotondare gli angoli voluti. */
private fun smooth(points: List<InkPoint>): List<InkPoint> {
    if (points.size < 3) return points
    return points.mapIndexed { i, p ->
        if (i == 0 || i == points.lastIndex) p
        else {
            val a = points[i - 1]
            val b = points[i + 1]
            InkPoint((a.x + 2 * p.x + b.x) / 4f, (a.y + 2 * p.y + b.y) / 4f, (a.p + 2 * p.p + b.p) / 4f)
        }
    }
}

/** Il punto [x], [y] tocca il tratto (con un margine [slack] in unità)? Serve alla gomma. */
fun Stroke.hits(x: Float, y: Float, slack: Float): Boolean {
    val pts = points
    if (pts.isEmpty()) return false
    for (i in pts.indices) {
        val a = pts[i]
        val b = pts[min(pts.lastIndex, i + 1)]
        if (distanceToSegment(x, y, a.x, a.y, b.x, b.y) <= radiusAt(a.p) + slack) return true
    }
    return false
}

private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

/**
 * Riconosce una linea, un'ellisse o un rettangolo tracciati a mano e restituisce la forma pulita;
 * nullo se il tratto non somiglia abbastanza a nessuna delle tre.
 */
fun Stroke.recognizeShape(): Stroke? {
    val pts = points
    if (pts.size < 6) return null
    val minX = pts.minOf { it.x }
    val maxX = pts.maxOf { it.x }
    val minY = pts.minOf { it.y }
    val maxY = pts.maxOf { it.y }
    val w = maxX - minX
    val h = maxY - minY
    val diag = hypot(w, h)
    if (diag < 60f) return null
    val p = pts.map { it.p }.average().toFloat()
    val first = pts.first()
    val last = pts.last()

    // Linea: tutti i punti vicini alla corda.
    val chord = hypot(last.x - first.x, last.y - first.y)
    if (chord > 0.7f * diag) {
        val maxDev = pts.maxOf { distanceToSegment(it.x, it.y, first.x, first.y, last.x, last.y) }
        if (maxDev / chord < 0.05f) return copy(points = listOf(first.copy(p = p), last.copy(p = p)), shape = true)
    }

    // Forme chiuse: inizio e fine vicini, e abbastanza grandi in entrambe le direzioni.
    if (chord > 0.25f * diag || min(w, h) < 0.15f * diag) return null
    val cx = (minX + maxX) / 2f
    val cy = (minY + maxY) / 2f
    val rx = w / 2f
    val ry = h / 2f
    val ellipseError = pts.map { abs(sqrt(((it.x - cx) / rx).let { v -> v * v } + ((it.y - cy) / ry).let { v -> v * v }) - 1f) }.average()
    val rectError = pts.map {
        min(min(abs(it.x - minX), abs(it.x - maxX)), min(abs(it.y - minY), abs(it.y - maxY))) / diag
    }.average()
    // Un rettangolo passa vicino ai quattro angoli; un'ellisse no.
    val corners = listOf(minX to minY, maxX to minY, maxX to maxY, minX to maxY)
    val cornersHit = corners.count { (x, y) -> pts.any { hypot(it.x - x, it.y - y) < 0.12f * diag } }
    return when {
        cornersHit >= 3 && rectError < 0.035 -> copy(
            points = listOf(
                InkPoint(minX, minY, p), InkPoint(maxX, minY, p), InkPoint(maxX, maxY, p),
                InkPoint(minX, maxY, p), InkPoint(minX, minY, p),
            ).densify(),
            shape = true,
        )
        ellipseError < 0.11 -> copy(
            points = (0..64).map { k ->
                val a = 2 * PI * k / 64
                InkPoint(cx + (rx * cos(a)).toFloat(), cy + (ry * sin(a)).toFloat(), p)
            },
            shape = true,
        )
        else -> null
    }
}

/**
 * Aggiunge punti lungo i lati di una spezzata: il contorno di un rettangolo resta dritto e con gli angoli vivi,
 * perché la media mobile tocca solo i punti vicini all'angolo.
 */
private fun List<InkPoint>.densify(): List<InkPoint> {
    val out = mutableListOf<InkPoint>()
    for (i in 0 until size - 1) {
        val a = this[i]
        val b = this[i + 1]
        val n = max(1, (hypot(b.x - a.x, b.y - a.y) / 40f).toInt())
        for (k in 0 until n) {
            val t = k.toFloat() / n
            out += InkPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.p)
        }
        // L'angolo ripetuto tiene il vertice fermo anche dopo la media mobile.
        out += b
    }
    return out
}

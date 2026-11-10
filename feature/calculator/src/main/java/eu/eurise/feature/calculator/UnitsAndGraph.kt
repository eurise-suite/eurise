package eu.eurise.feature.calculator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.calc.Calc
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.FilterPill

// --- Unità ---

/** Un'unità: [toBase] e [fromBase] convertono verso l'unità di riferimento della categoria. */
private class Measure(val name: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double) {
    constructor(name: String, factor: Double) : this(name, { it * factor }, { it / factor })
}

private val Categories: List<Pair<Int, List<Measure>>> = listOf(
    R.string.calc_u_length to listOf(Measure("mm", 0.001), Measure("cm", 0.01), Measure("m", 1.0), Measure("km", 1000.0), Measure("pollici", 0.0254), Measure("piedi", 0.3048), Measure("miglia", 1609.344)),
    R.string.calc_u_mass to listOf(Measure("g", 0.001), Measure("kg", 1.0), Measure("q", 100.0), Measure("t", 1000.0), Measure("once", 0.028349523125), Measure("libbre", 0.45359237)),
    R.string.calc_u_area to listOf(Measure("cm²", 0.0001), Measure("m²", 1.0), Measure("ettari", 10000.0), Measure("km²", 1e6), Measure("piedi²", 0.09290304)),
    R.string.calc_u_volume to listOf(Measure("ml", 0.001), Measure("cl", 0.01), Measure("litri", 1.0), Measure("m³", 1000.0), Measure("galloni USA", 3.785411784)),
    R.string.calc_u_temperature to listOf(
        Measure("°C", { it }, { it }),
        Measure("°F", { (it - 32) * 5 / 9 }, { it * 9 / 5 + 32 }),
        Measure("K", { it - 273.15 }, { it + 273.15 }),
    ),
    R.string.calc_u_speed to listOf(Measure("km/h", 1.0), Measure("m/s", 3.6), Measure("mph", 1.609344), Measure("nodi", 1.852)),
    R.string.calc_u_data to listOf(Measure("kB", 1.0), Measure("MB", 1000.0), Measure("GB", 1e6), Measure("TB", 1e9), Measure("MiB", 1048.576), Measure("GiB", 1073741.824)),
    R.string.calc_u_time to listOf(Measure("secondi", 1.0), Measure("minuti", 60.0), Measure("ore", 3600.0), Measure("giorni", 86400.0), Measure("settimane", 604800.0)),
)

@Composable
internal fun UnitsScreen() {
    var category by rememberSaveable { mutableStateOf(0) }
    var from by rememberSaveable { mutableStateOf(2) }
    var value by rememberSaveable { mutableStateOf("1") }
    val units = Categories[category].second
    val fromUnit = units[from.coerceIn(0, units.lastIndex)]
    ScrollColumn {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Categories.indices.toList()) { i -> FilterPill(stringResource(Categories[i].first), null, i == category) { category = i; from = 0 } }
        }
        NumberField(stringResource(R.string.calc_value_in, fromUnit.name), value, { value = it })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(units.indices.toList()) { i -> FilterPill(units[i].name, null, units[i] === fromUnit) { from = i } }
        }
        val v = Calc.preview(value)
        if (v != null) {
            val base = fromUnit.toBase(v)
            Card {
                units.filter { it !== fromUnit }.forEachIndexed { i, u ->
                    if (i > 0) CardDivider()
                    ResultRow(u.name, Calc.format(u.fromBase(base), 6))
                }
            }
        }
    }
}

// --- Grafico ---

private val GraphColors = listOf(Color(0xFF3E5C8A), Color(0xFFB5703F), Color(0xFF4F6B5A))

/** Punti notevoli di una funzione nell'intervallo mostrato, trovati campionando: zeri, minimi e massimi. */
private fun notable(f: (Double) -> Double?, from: Double, to: Double): Pair<List<Double>, List<Pair<Double, Double>>> {
    val n = 400
    val xs = (0..n).map { from + (to - from) * it / n }
    val ys = xs.map { f(it) }
    val zeros = mutableListOf<Double>()
    val extremes = mutableListOf<Pair<Double, Double>>()
    for (i in 1 until n) {
        val a = ys[i - 1]; val b = ys[i]; val c = ys[i + 1]
        if (a != null && b != null && a * b <= 0 && !(a == 0.0 && b == 0.0)) {
            val x = if (b == a) xs[i] else xs[i - 1] + (xs[i] - xs[i - 1]) * (-a / (b - a))
            if (zeros.none { kotlin.math.abs(it - x) < (to - from) / n * 2 }) zeros += x
        }
        if (a != null && b != null && c != null && ((b < a && b <= c) || (b > a && b >= c))) extremes += xs[i] to b
    }
    return zeros.take(6) to extremes.take(4)
}

@Composable
internal fun GraphScreen(tape: Tape) {
    val scheme = MaterialTheme.colorScheme
    val functions = remember { mutableStateListOf("x² − 3x", "x + 1") }
    var center by remember { mutableStateOf(Offset(1.5f, 2f)) }
    var span by remember { mutableFloatStateOf(10f) }
    val degrees = tape.degrees
    val fs: List<(Double) -> Double?> = functions.map { text ->
        val clean = text.replace('×', '*').replace('÷', '/').replace('−', '-');
        { x: Double -> runCatching { Calc.evaluate(clean, degrees, x) }.getOrNull() }
    }
    ScrollColumn {
        Box(
            Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerLow)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        span = (span / zoom).coerceIn(0.5f, 2000f)
                        val perPx = span / size.width
                        center = Offset(center.x - pan.x * perPx, center.y + pan.y * perPx)
                    }
                },
        ) {
            val grid = scheme.outlineVariant
            val axis = scheme.onSurfaceVariant
            Canvas(Modifier.fillMaxWidth().height(300.dp)) {
                val w = size.width
                val h = size.height
                val xFrom = center.x - span / 2
                val yHalf = span * h / w / 2
                fun px(x: Double) = ((x - xFrom) / span * w).toFloat()
                fun py(y: Double) = ((center.y + yHalf - y) / (2 * yHalf) * h).toFloat()
                val step = Math.pow(10.0, Math.floor(Math.log10(span / 2.0)))
                var gx = Math.floor(xFrom / step) * step
                while (gx < xFrom + span) { drawLine(grid, Offset(px(gx), 0f), Offset(px(gx), h), 1f); gx += step }
                var gy = Math.floor((center.y - yHalf) / step) * step
                while (gy < center.y + yHalf) { drawLine(grid, Offset(0f, py(gy)), Offset(w, py(gy)), 1f); gy += step }
                drawLine(axis, Offset(px(0.0), 0f), Offset(px(0.0), h), 2f)
                drawLine(axis, Offset(0f, py(0.0)), Offset(w, py(0.0)), 2f)
                fs.forEachIndexed { i, f ->
                    val path = Path()
                    var drawing = false
                    val n = w.toInt().coerceAtLeast(2)
                    for (k in 0..n) {
                        val x = xFrom + span * k / n.toDouble()
                        val y = f(x)
                        if (y == null || y.isNaN() || kotlin.math.abs(y - center.y) > yHalf * 20) { drawing = false; continue }
                        val p = Offset(px(x), py(y))
                        if (drawing) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
                        drawing = true
                    }
                    drawPath(path, GraphColors[i % GraphColors.size], style = Stroke(width = 3.dp.toPx()))
                }
            }
        }
        Card {
            functions.forEachIndexed { i, text ->
                if (i > 0) CardDivider()
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(GraphColors[i % GraphColors.size]))
                    Text("  y =", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    OutlinedTextField(
                        text, { functions[i] = it }, singleLine = true,
                        modifier = Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        isError = text.isNotBlank() && fs[i](center.x.toDouble()) == null && fs[i](center.x + 0.37) == null,
                    )
                    if (functions.size > 1) Text(
                        "✕", fontSize = 16.sp, color = scheme.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape).clickable { functions.removeAt(i) }.padding(10.dp),
                    )
                }
            }
            if (functions.size < 3) {
                CardDivider()
                Text(
                    stringResource(R.string.calc_add_function), fontWeight = FontWeight.Bold, color = scheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { functions += "" }.padding(14.dp),
                )
            }
        }
        val xFrom = (center.x - span / 2).toDouble()
        val xTo = (center.x + span / 2).toDouble()
        val notes = fs.mapIndexedNotNull { i, f ->
            if (functions[i].isBlank()) return@mapIndexedNotNull null
            val (zeros, extremes) = notable(f, xFrom, xTo)
            Triple(i, zeros, extremes)
        }
        if (notes.isNotEmpty()) {
            Text(stringResource(R.string.calc_notable).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = scheme.onSurfaceVariant)
            Card {
                notes.forEachIndexed { k, (i, zeros, extremes) ->
                    if (k > 0) CardDivider()
                    ResultRow(stringResource(R.string.calc_zeros, functions[i]), zeros.joinToString(" · ") { "x = " + Calc.format(it, 3) }.ifEmpty { "—" })
                    extremes.forEach { (x, y) -> ResultRow(stringResource(R.string.calc_extreme), "(" + Calc.format(x, 3) + "; " + Calc.format(y, 3) + ")") }
                }
            }
        }
        Text(stringResource(R.string.calc_graph_hint), fontSize = 12.sp, color = scheme.onSurfaceVariant)
    }
}

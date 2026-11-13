package eu.eurise.office.pdf

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.eurise.office.R
import org.json.JSONArray

/**
 * La firma disegnata a mano, tenuta solo sul telefono (preferenze private di Office). I tratti sono in frazioni di un
 * riquadro largo il doppio che alto: [place] li riporta sulla pagina alla larghezza voluta.
 */
class Signature(val strokes: List<List<Pair<Float, Float>>>) {
    /** I tratti in punti PDF, con l'angolo in alto a sinistra in ([left], [top]) e larghi [width]. */
    fun place(left: Float, top: Float, width: Float): List<List<Pair<Float, Float>>> {
        val height = width / ASPECT
        return strokes.map { s -> s.map { (u, v) -> (left + u * width) to (top - v * height) } }
    }

    companion object {
        const val ASPECT = 2.2f
        private const val KEY = "firma"

        fun load(context: Context): Signature? {
            val json = context.getSharedPreferences("office", Context.MODE_PRIVATE).getString(KEY, null) ?: return null
            return runCatching {
                val array = JSONArray(json)
                Signature((0 until array.length()).map { i ->
                    val s = array.getJSONArray(i)
                    (0 until s.length() / 2).map { j -> s.getDouble(j * 2).toFloat() to s.getDouble(j * 2 + 1).toFloat() }
                })
            }.getOrNull()?.takeIf { it.strokes.isNotEmpty() }
        }

        fun save(context: Context, strokes: List<List<Pair<Float, Float>>>) {
            val array = JSONArray()
            strokes.forEach { s -> array.put(JSONArray().also { a -> s.forEach { (u, v) -> a.put(u.toDouble()); a.put(v.toDouble()) } }) }
            context.getSharedPreferences("office", Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
        }
    }
}

/** Il foglio per disegnare la firma con il dito («Firma» nella scheda Annota). */
@Composable
fun SignaturePadDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_sign_title)) },
        text = {
            Column {
                Text(stringResource(R.string.pdf_sign_hint), style = MaterialTheme.typography.bodySmall)
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(Signature.ASPECT).clip(RoundedCornerShape(12.dp))
                        .background(Color.White).onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current.clear(); current.add(it) },
                                onDrag = { change, _ -> change.consume(); current.add(change.position) },
                                onDragEnd = { if (current.size > 1) strokes.add(current.toList()); current.clear() },
                            )
                        },
                ) {
                    drawLine(Color(0xFFC9C9C2), Offset(16f, size.height * 0.78f), Offset(size.width - 16f, size.height * 0.78f), 2f)
                    (strokes + listOf(current.toList())).filter { it.size > 1 }.forEach { s ->
                        val path = Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(path, Color(0xFF1F2A44), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (strokes.isNotEmpty() && size.width > 0) {
                    Signature.save(context, strokes.map { s -> s.map { (it.x / size.width) to (it.y / size.height) } })
                    onSaved()
                }
            }) { Text(stringResource(R.string.pdf_sign_keep)) }
        },
        dismissButton = {
            TextButton(onClick = { if (strokes.isEmpty()) onDismiss() else strokes.clear() }) {
                Text(stringResource(if (strokes.isEmpty()) R.string.pdf_cancel else R.string.pdf_sign_clear))
            }
        },
    )
}

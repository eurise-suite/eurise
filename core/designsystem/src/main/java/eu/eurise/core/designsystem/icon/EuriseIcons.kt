package eu.eurise.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icone a tratto della suite (griglia 24 × 24), disegnate come nei render. Lo spessore del tratto
 * cambia per la voce selezionata: 2,3 di base, 2,7 se attiva.
 */
object EuriseIcons {
    const val Stroke = 2.3f
    const val StrokeSelected = 2.7f

    fun today(stroke: Float = Stroke) = icon("today", stroke,
        circle(12f, 12f, 4f),
        "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")

    fun calendar(stroke: Float = Stroke) = icon("calendar", stroke,
        "M6.5 5h11a3 3 0 0 1 3 3v9.5a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z",
        "M3.5 10h17M8 3v4M16 3v4")

    fun note(stroke: Float = Stroke) = icon("note", stroke,
        "M15 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9z", "M15 3v6h6", "M7 13h7M7 17h4")

    fun task(stroke: Float = Stroke) = icon("task", stroke, circle(12f, 12f, 9f), "M8 12l3 3 5-6")

    fun plus(stroke: Float = 2.4f) = icon("plus", stroke, "M12 5v14M5 12h14")

    fun chevronLeft(stroke: Float = 2.4f) = icon("left", stroke, "M15 6l-6 6 6 6")

    fun chevronRight(stroke: Float = 2.4f) = icon("right", stroke, "M9 6l6 6-6 6")

    fun pen(stroke: Float = Stroke) = icon("pen", stroke, "M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z")

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private val cache = HashMap<String, ImageVector>()

    private fun icon(name: String, stroke: Float, vararg paths: String): ImageVector =
        cache.getOrPut("$name@$stroke") {
            ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
                .apply {
                    paths.forEach { d ->
                        addPath(
                            pathData = addPathNodes(d),
                            stroke = SolidColor(Color.Black),
                            strokeLineWidth = stroke,
                            strokeLineCap = StrokeCap.Round,
                            strokeLineJoin = StrokeJoin.Round,
                        )
                    }
                }
                .build()
        }
}

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

    fun star(stroke: Float = Stroke) = icon("star", stroke, "M12 3.8l2.5 5.2 5.7.8-4.1 4 1 5.7L12 16.8l-5.1 2.7 1-5.7-4.1-4 5.7-.8z")

    fun chat(stroke: Float = Stroke) = icon("chat", stroke,
        "M5.5 5h13A2.5 2.5 0 0 1 21 7.5v7a2.5 2.5 0 0 1-2.5 2.5H10.5L6 20.5V17h-.5A2.5 2.5 0 0 1 3 14.5v-7A2.5 2.5 0 0 1 5.5 5z")

    fun folder(stroke: Float = Stroke) = icon("folder", stroke,
        "M3.5 7.5a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")

    fun tools(stroke: Float = Stroke) = icon("tools", stroke,
        "M14.7 6.3a4 4 0 0 0-5.4 5.4L4 17v3h3l5.3-5.3a4 4 0 0 0 5.4-5.4l-2.4 2.4-2.6-.6-.6-2.6z")

    fun grid(stroke: Float = Stroke) = icon("grid", stroke,
        "M5.8 4h2.9A1.8 1.8 0 0 1 10.5 5.8v2.9a1.8 1.8 0 0 1-1.8 1.8H5.8A1.8 1.8 0 0 1 4 8.7V5.8A1.8 1.8 0 0 1 5.8 4z",
        "M15.3 4h2.9A1.8 1.8 0 0 1 20 5.8v2.9a1.8 1.8 0 0 1-1.8 1.8h-2.9a1.8 1.8 0 0 1-1.8-1.8V5.8A1.8 1.8 0 0 1 15.3 4z",
        "M5.8 13.5h2.9a1.8 1.8 0 0 1 1.8 1.8v2.9A1.8 1.8 0 0 1 8.7 20H5.8A1.8 1.8 0 0 1 4 18.2v-2.9a1.8 1.8 0 0 1 1.8-1.8z",
        "M15.3 13.5h2.9a1.8 1.8 0 0 1 1.8 1.8v2.9a1.8 1.8 0 0 1-1.8 1.8h-2.9a1.8 1.8 0 0 1-1.8-1.8v-2.9a1.8 1.8 0 0 1 1.8-1.8z")

    fun search(stroke: Float = Stroke) = icon("search", stroke, circle(11f, 11f, 6.5f), "M20 20l-4.2-4.2")

    fun cloud(stroke: Float = 2f) = icon("cloud", stroke, "M7 18h10a4 4 0 0 0 .6-7.95A5.8 5.8 0 0 0 6.4 9.6 4.3 4.3 0 0 0 7 18z")

    fun sun(stroke: Float = 2f) = icon("sun", stroke, circle(12f, 12f, 4f),
        "M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4L6 18M18 6l1.4-1.4")

    fun rain(stroke: Float = 2f) = icon("rain", stroke,
        "M7 15h10a4 4 0 0 0 .6-7.95A5.8 5.8 0 0 0 6.4 6.6 4.3 4.3 0 0 0 7 15z", "M8.5 18.5l-1 2M12.5 18.5l-1 2M16.5 18.5l-1 2")

    fun snow(stroke: Float = 2f) = icon("snow", stroke, "M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9")

    fun fog(stroke: Float = 2f) = icon("fog", stroke, "M4 9h16M6 13h12M4 17h16")

    fun storm(stroke: Float = 2f) = icon("storm", stroke,
        "M7 14h10a4 4 0 0 0 .6-7.95A5.8 5.8 0 0 0 6.4 5.6 4.3 4.3 0 0 0 7 14z", "M12.5 15l-2 3.5h3l-2 3.5")

    fun pause(stroke: Float = 2.6f) = icon("pause", stroke, "M9 6v12M15 6v12")

    fun play(stroke: Float = 2.4f) = icon("play", stroke, "M8 5.5l10 6.5-10 6.5z")

    fun next(stroke: Float = 2.2f) = icon("next", stroke, "M6 6l8 6-8 6zM18 6v12")

    fun previous(stroke: Float = 2.2f) = icon("previous", stroke, "M18 6l-8 6 8 6zM6 6v12")

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

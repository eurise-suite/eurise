package eu.eurise.feature.launcher.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import java.time.LocalDateTime
import java.time.Month

/** Momento della giornata che decide i colori dello sfondo. */
enum class Moment { Morning, Day, Evening, Night }

enum class Season { Winter, Spring, Summer, Autumn }

/**
 * Una scena dello sfondo: cielo in tre tonalità e tre colline della stessa famiglia di colore, più il
 * sole o la luna. Le scene sono disegnate dall'app: nessuna immagine scaricata.
 */
data class Scene(val sky: List<Color>, val hills: List<Color>, val astro: Color, val moon: Boolean, val astroY: Float) {
    /** Sopra le scene di sera e di notte il testo è chiaro. */
    val dark: Boolean get() = moon || sky.first().luminance() < 0.25f
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun c(hex: Long) = Color(hex or 0xFF000000)

private val AutumnMorning = Scene(listOf(c(0xC7BFD3), c(0xE2CFD3), c(0xF0D8CC)), listOf(c(0xB8A9BC), c(0x9C8BA3), c(0x7F6E89)), c(0xF8E2CF), false, 470f)
private val AutumnDay = Scene(listOf(c(0xB9C7DD), c(0xD7DCE6), c(0xEBDCCB)), listOf(c(0xAEB9C9), c(0x93A1B4), c(0x7A889C)), c(0xF6E7D3), false, 420f)
private val Evening = Scene(listOf(c(0x3E3A5C), c(0x9A6A78), c(0xE0A27A)), listOf(c(0x6E5468), c(0x56404F), c(0x3E2E3A)), c(0xF2B98E), false, 520f)
private val Night = Scene(listOf(c(0x0E1424), c(0x1B2540), c(0x2B3554)), listOf(c(0x1F2638), c(0x181E2E), c(0x111624)), c(0xE9E4D6), true, 458f)
private val WinterDay = Scene(listOf(c(0xAEBBCD), c(0xCCD5E0), c(0xE6E3E2)), listOf(c(0xA7B3C3), c(0x8E9BAE), c(0x76849A)), c(0xF1E4D6), false, 400f)
private val SpringDay = Scene(listOf(c(0xB4C6CF), c(0xD3DCD8), c(0xE8E2D3)), listOf(c(0xA7B8B3), c(0x8DA29C), c(0x748A84)), c(0xF6EBDA), false, 410f)
private val SummerDay = Scene(listOf(c(0xC9C2B8), c(0xE3D6C4), c(0xF0DDC2)), listOf(c(0xBCAB97), c(0xA3907C), c(0x887562)), c(0xFBEBD2), false, 380f)

fun seasonOf(month: Month): Season = when (month) {
    Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Season.Winter
    Month.MARCH, Month.APRIL, Month.MAY -> Season.Spring
    Month.JUNE, Month.JULY, Month.AUGUST -> Season.Summer
    else -> Season.Autumn
}

fun momentOf(hour: Int): Moment = when (hour) {
    in 6..9 -> Moment.Morning
    in 10..16 -> Moment.Day
    in 17..20 -> Moment.Evening
    else -> Moment.Night
}

/** La scena per un istante: segue la stagione e il momento della giornata. */
fun sceneFor(now: LocalDateTime): Scene = when (momentOf(now.hour)) {
    Moment.Night -> Night
    Moment.Evening -> Evening
    Moment.Morning -> AutumnMorning
    Moment.Day -> when (seasonOf(now.month)) {
        Season.Winter -> WinterDay
        Season.Spring -> SpringDay
        Season.Summer -> SummerDay
        Season.Autumn -> AutumnDay
    }
}

// Colline disegnate su una griglia di 390 × 844 e adattate allo schermo.
private val HillPaths = listOf(
    "M0 560 C80 520 160 540 240 500 C300 470 350 480 390 470 L390 844 L0 844Z",
    "M0 640 C90 600 170 630 250 590 C310 560 360 575 390 565 L390 844 L0 844Z",
    "M0 720 C110 690 200 715 290 680 C340 662 370 668 390 664 L390 844 L0 844Z",
).map { PathParser().parsePathString(it).toPath() }

private val Stars = listOf(60f to 90f, 120f to 160f, 200f to 70f, 250f to 230f, 340f to 110f, 90f to 300f, 180f to 260f)

/** Disegna lo sfondo della Scrivania. */
@Composable
fun SceneBackground(scene: Scene, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(scene.sky))
        val sx = size.width / 390f
        val sy = size.height / 844f
        val s = minOf(sx, sy)
        if (scene.moon) {
            Stars.forEach { (x, y) -> drawCircle(Color(0xFFE9E4D6).copy(alpha = 0.7f), 1.4f * s, Offset(x * sx, y * sy)) }
        }
        drawCircle(scene.astro.copy(alpha = 0.9f), (if (scene.moon) 22f else 34f) * s, Offset((if (scene.moon) 338f else 300f) * sx, scene.astroY * sy))
        scale(sx, sy, pivot = Offset.Zero) {
            HillPaths.forEachIndexed { i, path -> drawPath(path, scene.hills[i]) }
        }
    }
}

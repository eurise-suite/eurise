package eu.eurise.feature.weather

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import eu.eurise.core.data.weather.Sky

/**
 * Il tempo disegnato in cima all'app Meteo (richiesta del proprietario, 12 novembre), nello stile degli sfondi della
 * Scrivania: cielo, sole o luna, nuvole, pioggia, neve, lampi o nebbia sopra tre colline. Disegno su una griglia di
 * 390 × 320, adattato alla larghezza; niente immagini scaricate.
 */
@Composable
fun SkyScene(sky: Sky?, day: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val (top, bottom) = sceneSky(sky, day)
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        scale(size.width / W, size.height / H, pivot = Offset.Zero) {
            val clearish = sky == null || sky == Sky.Clear || sky == Sky.PartlyCloudy
            if (!day && (clearish || sky == Sky.Cloudy)) Stars.forEach { (x, y) -> drawCircle(Color(0xFFE9E4D6).copy(alpha = 0.7f), 1.3f, Offset(x, y)) }
            if (clearish) {
                if (day) {
                    drawCircle(Color(0xFFFFE7A8).copy(alpha = 0.35f), 58f, Offset(318f, 92f))
                    drawCircle(Color(0xFFFFE19A), 36f, Offset(318f, 92f))
                } else {
                    drawCircle(Color(0xFFE9E4D6), 26f, Offset(320f, 86f))
                    drawCircle(sceneSky(sky, false).first, 24f, Offset(331f, 79f))
                }
            }
            when (sky) {
                Sky.PartlyCloudy -> { cloud(250f, 118f, 0.9f, cloudColor(day, 0.92f)); cloud(110f, 70f, 0.6f, cloudColor(day, 0.75f)) }
                Sky.Cloudy -> cloudsLayer(day, 0.9f)
                Sky.Fog -> cloudsLayer(day, 0.55f)
                Sky.Drizzle, Sky.Rain, Sky.Showers -> {
                    if (sky == Sky.Showers && day) drawCircle(Color(0xFFFFE19A).copy(alpha = 0.8f), 30f, Offset(330f, 70f))
                    cloudsLayer(day, 1f, dark = true)
                }
                Sky.Storm -> cloudsLayer(day, 1f, dark = true, storm = true)
                Sky.Snow -> cloudsLayer(day, 0.85f)
                else -> Unit
            }
            val hills = sceneHills(sky, day)
            Hills.forEachIndexed { i, p -> drawPath(p, hills[i]) }
            if (sky == Sky.Snow) drawPath(SnowCap, Color.White.copy(alpha = if (day) 0.85f else 0.45f))
            when (sky) {
                Sky.Drizzle -> rain(day, 28, 10f)
                Sky.Rain -> rain(day, 60, 16f)
                Sky.Showers -> rain(day, 45, 16f)
                Sky.Storm -> { rain(day, 80, 18f); bolt() }
                Sky.Snow -> snow()
                Sky.Fog -> fog(day)
                else -> Unit
            }
        }
    }
}

private const val W = 390f
private const val H = 320f

/** I colori del cielo: azzurro col sereno, grigio-blu con la pioggia, ardesia col temporale, blu notte di sera. */
fun sceneSky(s: Sky?, day: Boolean): Pair<Color, Color> = when {
    !day && (s == Sky.Rain || s == Sky.Storm || s == Sky.Showers || s == Sky.Drizzle) -> Color(0xFF151B28) to Color(0xFF2E3747)
    !day -> Color(0xFF1B2540) to Color(0xFF3A4A6B)
    s == Sky.Clear -> Color(0xFF3F7CC2) to Color(0xFFA8CDEB)
    s == Sky.PartlyCloudy -> Color(0xFF5A8BC4) to Color(0xFFB4CFE6)
    s == Sky.Storm -> Color(0xFF3C4452) to Color(0xFF6E7888)
    s == Sky.Snow -> Color(0xFF8C9AAC) to Color(0xFFD7DEE7)
    s == Sky.Fog -> Color(0xFF9AA3AE) to Color(0xFFD5D9DE)
    else -> Color(0xFF5F6F86) to Color(0xFF9FAAB9)
}

private fun sceneHills(s: Sky?, day: Boolean): List<Color> {
    val base = listOf(Color(0xFF8FA7C0), Color(0xFF6F88A3), Color(0xFF55708C))
    val k = when {
        !day -> 0.62f
        s == Sky.Storm -> 0.45f
        s == Sky.Rain || s == Sky.Showers || s == Sky.Drizzle || s == Sky.Cloudy -> 0.25f
        s == Sky.Fog || s == Sky.Snow -> 0.0f
        else -> 0f
    }
    val toward = if (s == Sky.Fog || s == Sky.Snow) Color(0xFFC9D1DB) else Color(0xFF1E2533)
    val f = if (s == Sky.Fog || s == Sky.Snow) 0.35f else k
    return base.map { androidx.compose.ui.graphics.lerp(it, toward, f) }
}

private fun cloudColor(day: Boolean, alpha: Float, dark: Boolean = false) =
    (if (dark) (if (day) Color(0xFF7D8796) else Color(0xFF3A4352)) else if (day) Color.White else Color(0xFF55617A)).copy(alpha = alpha)

private fun DrawScope.cloud(x: Float, y: Float, k: Float, color: Color) {
    drawOval(color, Offset(x - 70 * k, y - 14 * k), Size(140 * k, 38 * k))
    drawOval(color, Offset(x - 42 * k, y - 38 * k), Size(70 * k, 56 * k))
    drawOval(color, Offset(x + 2 * k, y - 30 * k), Size(56 * k, 44 * k))
}

private fun DrawScope.cloudsLayer(day: Boolean, alpha: Float, dark: Boolean = false, storm: Boolean = false) {
    val c = cloudColor(day, alpha, dark)
    cloud(80f, 70f, 1.0f, c)
    cloud(250f, 52f, 1.2f, c)
    cloud(350f, 100f, 0.9f, c)
    cloud(170f, 118f, 1.1f, cloudColor(day, alpha * 0.9f, dark || storm))
}

private fun DrawScope.rain(day: Boolean, drops: Int, len: Float) {
    val col = (if (day) Color(0xFFE6EEF7) else Color(0xFFB8C4D6)).copy(alpha = 0.7f)
    var seed = 17
    repeat(drops) {
        seed = (seed * 1103515245 + 12345) and 0x7fffffff
        val x = (seed % 400).toFloat()
        val y = 110f + ((seed / 400) % 200)
        drawLine(col, Offset(x, y), Offset(x - 4f, y + len), strokeWidth = 1.6f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.snow() {
    var seed = 29
    repeat(55) {
        seed = (seed * 1103515245 + 12345) and 0x7fffffff
        drawCircle(Color.White.copy(alpha = 0.9f), 1.8f + (seed % 3) * 0.8f, Offset((seed % 390).toFloat(), 90f + ((seed / 390) % 225)))
    }
}

private fun DrawScope.bolt() {
    val p = Path().apply {
        moveTo(200f, 120f); lineTo(184f, 170f); lineTo(200f, 170f); lineTo(186f, 222f); lineTo(220f, 158f); lineTo(204f, 158f); lineTo(216f, 120f); close()
    }
    drawPath(p, Color(0xFFFFE19A))
}

private fun DrawScope.fog(day: Boolean) {
    val c = (if (day) Color.White else Color(0xFFB8C0CC)).copy(alpha = if (day) 0.45f else 0.18f)
    listOf(150f, 190f, 232f, 270f).forEachIndexed { i, y ->
        drawRoundRect(c, Offset(if (i % 2 == 0) -20f else 40f, y), Size(360f, 14f), androidx.compose.ui.geometry.CornerRadius(7f))
    }
}

private val Hills = listOf(
    "M0 236 C80 206 160 222 240 196 C300 178 350 184 390 178 L390 320 L0 320Z",
    "M0 268 C90 240 170 262 250 236 C310 216 360 228 390 222 L390 320 L0 320Z",
    "M0 300 C110 280 200 298 290 274 C340 262 370 266 390 262 L390 320 L0 320Z",
).map { PathParser().parsePathString(it).toPath() }

private val SnowCap = PathParser().parsePathString(
    "M0 236 C80 206 160 222 240 196 C300 178 350 184 390 178 L390 196 C340 202 300 198 240 216 C160 240 80 228 0 256Z",
).toPath()

private val Stars = listOf(40f to 40f, 110f to 90f, 180f to 30f, 230f to 120f, 270f to 40f, 70f to 150f, 150f to 140f, 20f to 110f, 360f to 150f)

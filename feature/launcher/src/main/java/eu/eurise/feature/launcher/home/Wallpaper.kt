package eu.eurise.feature.launcher.home

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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import eu.eurise.feature.launcher.weather.Sky
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.MonthDay
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Momento della giornata: decide come si colorano cielo e colline. */
enum class Moment { Dawn, Morning, Afternoon, Evening, Night }

enum class Season { Winter, Spring, Summer, Autumn }

/** Ricorrenze con uno sfondo proprio. Compleanno e anniversario sono quelli dell'utente (Date importanti). */
enum class Festivity { ChristmasEve, Christmas, NewYearsEve, NewYear, Epiphany, Easter, SpringStart, SummerStart, AutumnStart, WinterStart, Birthday, Anniversary }

/** Cosa fa cambiare lo sfondo (Griglia e icone → "Lo sfondo cambia con"). */
data class WallpaperOptions(
    val season: Boolean = true,
    val weather: Boolean = true,
    val holidays: Boolean = true,
    val moment: Boolean = true,
    /** Sfondo scelto dall'utente: "season:Autumn" o "fest:Christmas"; null = automatico. */
    val fixed: String? = null,
) {
    val fixedSeason: Season? get() = fixed?.removePrefix("season:")?.takeIf { fixed.startsWith("season:") }?.let { n -> Season.entries.firstOrNull { it.name == n } }
    val fixedFestivity: Festivity? get() = fixed?.removePrefix("fest:")?.takeIf { fixed.startsWith("fest:") }?.let { n -> Festivity.entries.firstOrNull { it.name == n } }
}

/** Le date personali che hanno uno sfondo: il proprio compleanno e il proprio anniversario. */
data class PersonalDays(val birthday: MonthDay? = null, val anniversary: MonthDay? = null)

/**
 * Una scena dello sfondo: cielo in tre tonalità e tre colline della stessa famiglia di colore, il sole o la
 * luna, i segni discreti della ricorrenza e l'effetto del meteo. Tutto è disegnato dall'app.
 */
data class Scene(
    val sky: List<Color>,
    val hills: List<Color>,
    val astro: Color,
    val moon: Boolean,
    val astroY: Float,
    val festivity: Festivity? = null,
    val weather: Sky? = null,
    val moment: Moment = Moment.Morning,
    /** Tema scelto dall'utente: vero scuro, falso chiaro, null segue lo sfondo. */
    val forceDark: Boolean? = null,
) {
    /** Sopra le scene scure il testo è chiaro, a meno che l'utente non abbia scelto il tema. */
    val dark: Boolean get() = forceDark ?: (moon || sky.first().luma() < 0.3f)

    /** Il tema scuro arriva solo con lo sfondo di notte: sopra quello di sera resta il tema chiaro. */
    val themeDark: Boolean get() = forceDark ?: (moment == Moment.Night)

    /**
     * Il cielo in alto (dove stanno saluto, orario e meteo) è ancora abbastanza chiaro per un testo scuro,
     * anche quando il resto della Scrivania è già in tema scuro (per esempio al tramonto).
     */
    val skyLight: Boolean get() = moment != Moment.Night && sky.take(2).map { it.luma() }.average() >= 0.22
}

private fun Color.luma(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun c(hex: Long) = Color(hex or 0xFF000000)

/** Coppia giorno / notte di una famiglia di colori. */
private class Palette(val sky: List<Color>, val hills: List<Color>, val nightSky: List<Color>, val nightHills: List<Color>, val sun: Color = c(0xF6EBDA))

private val NightSky = listOf(c(0x0E1424), c(0x1B2540), c(0x2B3554))
private val NightHills = listOf(c(0x1F2638), c(0x181E2E), c(0x111624))
private val EveningSky = listOf(c(0x3E3A5C), c(0x9A6A78), c(0xE0A27A))
private val EveningHills = listOf(c(0x6E5468), c(0x56404F), c(0x3E2E3A))

private val Seasons = mapOf(
    Season.Autumn to Palette(listOf(c(0xB9C7DD), c(0xD7DCE6), c(0xEBDCCB)), listOf(c(0xAEB9C9), c(0x93A1B4), c(0x7A889C)), NightSky, NightHills, c(0xF6E7D3)),
    Season.Winter to Palette(listOf(c(0xAEBBCD), c(0xCCD5E0), c(0xE6E3E2)), listOf(c(0xA7B3C3), c(0x8E9BAE), c(0x76849A)), NightSky, NightHills, c(0xF1E4D6)),
    Season.Spring to Palette(listOf(c(0xB4C6CF), c(0xD3DCD8), c(0xE8E2D3)), listOf(c(0xA7B8B3), c(0x8DA29C), c(0x748A84)), NightSky, NightHills, c(0xF6EBDA)),
    // Estate: cielo azzurro e colline color mare, sole caldo.
    Season.Summer to Palette(listOf(c(0xA9CDE0), c(0xCFE4EC), c(0xF1EBDA)), listOf(c(0x9CC6D0), c(0x7EAEBB), c(0x5F94A3)), NightSky, NightHills, c(0xFBE3A8)),
)

private val Festive = mapOf(
    Festivity.ChristmasEve to Palette(listOf(c(0xBFC9D8), c(0xD6DCE5), c(0xEAE7E3)), listOf(c(0xA9B5C5), c(0x8E9CB0), c(0x75849A)),
        listOf(c(0x141A2E), c(0x24304F), c(0x3A4566)), listOf(c(0x2A3350), c(0x222A43), c(0x1A2136))),
    Festivity.Christmas to Palette(listOf(c(0xC9D3DF), c(0xDFE4EA), c(0xF1EEE8)), listOf(c(0xB9C4D1), c(0xA3B0C0), c(0x8C9AAD)),
        listOf(c(0x161D30), c(0x283553), c(0x3D4A6B)), listOf(c(0x2D3754), c(0x242D46), c(0x1B2238))),
    Festivity.NewYearsEve to Palette(listOf(c(0xCFC8DD), c(0xE2DCE8), c(0xF1ECEA)), listOf(c(0xBDB3CF), c(0xA69BBC), c(0x8F83A6)),
        listOf(c(0x1A1530), c(0x2E2550), c(0x4A3D6E)), listOf(c(0x33294F), c(0x271F3F), c(0x1C1630))),
    Festivity.NewYear to Palette(listOf(c(0xD8D0E6), c(0xE8E2EE), c(0xF4EFEA)), listOf(c(0xC6BCD6), c(0xAEA3C2), c(0x968AAE)),
        listOf(c(0x1D1832), c(0x312852), c(0x4C406F)), listOf(c(0x352B51), c(0x292141), c(0x1E1832))),
    Festivity.Epiphany to Palette(listOf(c(0xD3CBD3), c(0xE4DDE0), c(0xF1E9E4)), listOf(c(0xC2B4BE), c(0xA898A6), c(0x8E7E8D)),
        listOf(c(0x1E1B2B), c(0x3A3042), c(0x5D4A52)), listOf(c(0x3E3240), c(0x302733), c(0x221C26)), c(0xEFE3C8)),
    Festivity.Easter to Palette(listOf(c(0xDCE6D6), c(0xECEFE2), c(0xF6F1E6)), listOf(c(0xC3D2B9), c(0xAABD9F), c(0x91A786)),
        listOf(c(0x16201C), c(0x26362F), c(0x3B4C43)), listOf(c(0x2B3A31), c(0x222F28), c(0x1A241F))),
    Festivity.SpringStart to Palette(listOf(c(0xD3E2DC), c(0xE7EEE6), c(0xF3EFE2)), listOf(c(0xB7CDBF), c(0x9DB7A7), c(0x82A08E)),
        listOf(c(0x13201D), c(0x22362F), c(0x354C43)), listOf(c(0x283A33), c(0x1F2F29), c(0x18241F))),
    Festivity.SummerStart to Palette(listOf(c(0x9FCAE0), c(0xC9E3EE), c(0xF4ECD6)), listOf(c(0x94C3CE), c(0x74A9B7), c(0x56909F)),
        listOf(c(0x0F1D2A), c(0x1C3346), c(0x2E4A60)), listOf(c(0x203546), c(0x182A38), c(0x11202B)), c(0xF7D98E)),
    Festivity.AutumnStart to Palette(listOf(c(0xE3D3C3), c(0xEEDFCF), c(0xF4E8DA)), listOf(c(0xCFB49C), c(0xB8977C), c(0x9E7C62)),
        listOf(c(0x1E1A22), c(0x372D35), c(0x55444A)), listOf(c(0x3C3036), c(0x2F262B), c(0x231C20)), c(0xF4DDBE)),
    Festivity.WinterStart to Palette(listOf(c(0xC9D2DC), c(0xDDE2E8), c(0xEEEEEC)), listOf(c(0xB3BECB), c(0x9DA9B9), c(0x8693A5)),
        NightSky, NightHills, c(0xF1E4D6)),
    Festivity.Birthday to Palette(listOf(c(0xE9D6DE), c(0xF1E3E6), c(0xF6EEEA)), listOf(c(0xD6BAC6), c(0xC29FAE), c(0xAA8496)),
        listOf(c(0x241A26), c(0x412C3E), c(0x634556)), listOf(c(0x4A3446), c(0x3A2837), c(0x2B1E29))),
    Festivity.Anniversary to Palette(listOf(c(0xE6D2D8), c(0xEFE0E2), c(0xF5ECE8)), listOf(c(0xD2B3BE), c(0xBC98A6), c(0xA27D8D)),
        listOf(c(0x2A1B2A), c(0x4A2E42), c(0x6E4658)), listOf(c(0x56384A), c(0x45293B), c(0x33202D))),
)

fun seasonOf(month: Month): Season = when (month) {
    Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Season.Winter
    Month.MARCH, Month.APRIL, Month.MAY -> Season.Spring
    Month.JUNE, Month.JULY, Month.AUGUST -> Season.Summer
    else -> Season.Autumn
}

/** Alba 5–7, mattina 7–12, pomeriggio 12–17, sera 17–21, notte. */
fun momentOf(hour: Int): Moment = when (hour) {
    in 5..6 -> Moment.Dawn
    in 7..11 -> Moment.Morning
    in 12..16 -> Moment.Afternoon
    in 17..20 -> Moment.Evening
    else -> Moment.Night
}

/** La ricorrenza di un giorno, se c'è; le personali hanno la precedenza. */
fun festivityOf(date: LocalDate, personal: PersonalDays): Festivity? {
    val md = MonthDay.from(date)
    if (personal.birthday == md) return Festivity.Birthday
    if (personal.anniversary == md) return Festivity.Anniversary
    return when {
        md == MonthDay.of(12, 24) -> Festivity.ChristmasEve
        md == MonthDay.of(12, 25) -> Festivity.Christmas
        md == MonthDay.of(12, 31) -> Festivity.NewYearsEve
        md == MonthDay.of(1, 1) -> Festivity.NewYear
        md == MonthDay.of(1, 6) -> Festivity.Epiphany
        date == easter(date.year) -> Festivity.Easter
        date == seasonStart(date.year, 0) -> Festivity.SpringStart
        date == seasonStart(date.year, 1) -> Festivity.SummerStart
        date == seasonStart(date.year, 2) -> Festivity.AutumnStart
        date == seasonStart(date.year, 3) -> Festivity.WinterStart
        else -> null
    }
}

/** Domenica di Pasqua (calendario gregoriano, algoritmo anonimo). */
fun easter(year: Int): LocalDate {
    val a = year % 19; val b = year / 100; val cc = year % 100
    val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30; val i = cc / 4; val k = cc % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(year, month, day)
}

/** Equinozi e solstizi (0 primavera, 1 estate, 2 autunno, 3 inverno), formule di Meeus, ora italiana approssimata. */
fun seasonStart(year: Int, which: Int): LocalDate {
    val y = (year - 2000) / 1000.0
    val jde = when (which) {
        0 -> 2451623.80984 + 365242.37404 * y + 0.05169 * y * y - 0.00411 * y * y * y
        1 -> 2451716.56767 + 365241.62603 * y + 0.00325 * y * y + 0.00888 * y * y * y
        2 -> 2451810.21715 + 365242.01767 * y - 0.11575 * y * y + 0.00337 * y * y * y
        else -> 2451900.05952 + 365242.74049 * y - 0.06223 * y * y - 0.00823 * y * y * y
    } + 1.0 / 24 // circa un'ora avanti rispetto al tempo universale
    val days = floor(jde + 0.5).toLong() - 2440588 // giorno giuliano → giorni dal 1970
    return LocalDate.ofEpochDay(days)
}

/** Applica il momento della giornata a una famiglia di colori. */
private fun Palette.at(moment: Moment, seasonal: Boolean): Pair<List<Color>, List<Color>> = when (moment) {
    Moment.Dawn -> sky.map { lerp(it, c(0xF0C8B0), 0.35f) }.let { s -> listOf(lerp(s[0], nightSky[2], 0.35f), s[1], s[2]) } to hills.map { lerp(it, nightHills[0], 0.25f) }
    Moment.Morning -> sky to hills
    Moment.Afternoon -> sky.map { lerp(it, c(0xF2D9B5), 0.15f) } to hills
    Moment.Evening -> if (seasonal) EveningSky to EveningHills
    else listOf(lerp(sky[0], nightSky[1], 0.6f), lerp(sky[1], c(0xD9A08A), 0.45f), lerp(sky[2], c(0xE8B48C), 0.4f)) to hills.zip(nightHills) { d, n -> lerp(d, n, 0.55f) }
    Moment.Night -> nightSky to nightHills
}

/** Effetto del meteo sui colori: pioggia e temporale velano di grigio, la nebbia di bianco, le nuvole poco. */
private fun weatherTint(colors: List<Color>, sky: Sky?, dark: Boolean): List<Color> = when (sky) {
    Sky.Rain -> colors.map { lerp(it, if (dark) c(0x2A3038) else c(0x8A93A0), 0.35f) }
    Sky.Storm -> colors.map { lerp(it, if (dark) c(0x1E222A) else c(0x6E7684), 0.5f) }
    Sky.Fog -> colors.map { lerp(it, if (dark) c(0x4A505A) else c(0xE6E8EA), 0.4f) }
    Sky.Cloudy -> colors.map { lerp(it, if (dark) c(0x30343C) else c(0xB8BEC6), 0.18f) }
    Sky.Snow -> colors.map { lerp(it, if (dark) c(0x3A4250) else c(0xE4E9EF), 0.3f) }
    else -> colors
}

/** La scena per un istante, secondo le opzioni scelte. */
fun sceneFor(now: LocalDateTime, options: WallpaperOptions = WallpaperOptions(), weather: Sky? = null, personal: PersonalDays = PersonalDays()): Scene {
    val moment = if (options.moment) momentOf(now.hour) else Moment.Morning
    // Uno sfondo scelto a mano resta fermo: non cambia con stagione e ricorrenze.
    val festivity = options.fixedFestivity
        ?: if (options.fixed == null && options.holidays) festivityOf(now.toLocalDate(), personal) else null
    val season = options.fixedSeason ?: if (options.season) seasonOf(now.month) else Season.Autumn
    val palette = festivity?.let { Festive.getValue(it) } ?: Seasons.getValue(season)
    val (sky0, hills0) = palette.at(moment, seasonal = festivity == null)
    val night = moment == Moment.Night
    val w = if (options.weather) weather else null
    val darkish = night || sky0.first().luma() < 0.3f
    val sky = weatherTint(sky0, w, darkish)
    val hills = weatherTint(hills0, w, darkish)
    val astroY = when (moment) { Moment.Dawn -> 520f; Moment.Morning -> 420f; Moment.Afternoon -> 380f; Moment.Evening -> 500f; Moment.Night -> 458f }
    return Scene(sky, hills, if (night) c(0xE9E4D6) else palette.sun, night, astroY, festivity, w, moment)
}

// Colline disegnate su una griglia di 390 × 844 e adattate allo schermo.
private val HillPaths = listOf(
    "M0 560 C80 520 160 540 240 500 C300 470 350 480 390 470 L390 844 L0 844Z",
    "M0 640 C90 600 170 630 250 590 C310 560 360 575 390 565 L390 844 L0 844Z",
    "M0 720 C110 690 200 715 290 680 C340 662 370 668 390 664 L390 844 L0 844Z",
).map { PathParser().parsePathString(it).toPath() }

private val SnowCap = PathParser().parsePathString(
    "M0 560 C80 520 160 540 240 500 C300 470 350 480 390 470 L390 494 C340 500 300 496 240 522 C160 556 80 542 0 584Z",
).toPath()

private val StarPoints = listOf(
    60f to 90f, 120f to 160f, 200f to 70f, 250f to 230f, 340f to 110f, 90f to 300f, 180f to 260f, 30f to 200f, 300f to 300f,
    150f to 40f, 280f to 60f, 220f to 140f, 40f to 360f, 360f to 220f,
)

/** Disegna lo sfondo della Scrivania. */
@Composable
fun SceneBackground(scene: Scene, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(scene.sky))
        val sx = size.width / 390f
        val sy = size.height / 844f
        scale(sx, sy, pivot = Offset.Zero) {
            val clear = scene.weather == null || scene.weather == Sky.Clear || scene.weather == Sky.Cloudy
            if (scene.moon && clear) StarPoints.forEach { (x, y) -> drawCircle(Color(0xFFE9E4D6).copy(alpha = 0.7f), 1.4f, Offset(x, y)) }
            if (clear || scene.weather == Sky.Cloudy) {
                drawCircle(scene.astro.copy(alpha = 0.9f), if (scene.moon) 22f else 34f, Offset(if (scene.moon) 338f else 300f, scene.astroY))
            }
            if (scene.weather == Sky.Cloudy) clouds(scene.dark)
            festivitySky(scene)
            HillPaths.forEachIndexed { i, path -> drawPath(path, scene.hills[i]) }
            if (scene.weather == Sky.Snow) drawPath(SnowCap, Color.White.copy(alpha = if (scene.dark) 0.35f else 0.75f))
            festivityGround(scene)
            when (scene.weather) {
                Sky.Rain, Sky.Storm -> rain(scene.dark, heavy = scene.weather == Sky.Storm)
                Sky.Snow -> snow()
                Sky.Fog -> drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = if (scene.dark) 0.12f else 0.35f))), size = Size(390f, 844f))
                else -> Unit
            }
        }
    }
}

private fun DrawScope.clouds(dark: Boolean) {
    val col = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.45f)
    listOf(Triple(90f, 180f, 1f), Triple(260f, 120f, 0.8f), Triple(200f, 300f, 0.7f)).forEach { (x, y, k) ->
        drawOval(col, Offset(x - 60 * k, y - 16 * k), Size(120 * k, 32 * k))
        drawOval(col, Offset(x - 30 * k, y - 30 * k), Size(70 * k, 36 * k))
    }
}

private fun DrawScope.rain(dark: Boolean, heavy: Boolean) {
    val col = (if (dark) Color(0xFFB8C4D6) else Color(0xFF5F6B7A)).copy(alpha = if (heavy) 0.45f else 0.3f)
    var seed = 17
    repeat(if (heavy) 70 else 45) {
        seed = (seed * 1103515245 + 12345) and 0x7fffffff
        val x = (seed % 390).toFloat()
        val y = ((seed / 390) % 800).toFloat()
        drawLine(col, Offset(x, y), Offset(x - 4f, y + 16f), strokeWidth = 1.3f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.snow() {
    var seed = 29
    repeat(50) {
        seed = (seed * 1103515245 + 12345) and 0x7fffffff
        drawCircle(Color.White.copy(alpha = 0.8f), 1.6f + (seed % 3) * 0.6f, Offset((seed % 390).toFloat(), ((seed / 390) % 820).toFloat()))
    }
}

private fun DrawScope.tree(x: Float, y: Float, k: Float, color: Color, lights: Boolean) {
    val p = Path().apply { moveTo(x, y - 60 * k); lineTo(x - 22 * k, y); lineTo(x + 22 * k, y); close() }
    drawPath(p, color)
    drawRect(color, Offset(x - 3 * k, y), Size(6 * k, 8 * k))
    if (lights) listOf(-8f to -38f, 6f to -26f, -12f to -12f, 10f to -8f, 0f to -48f).forEach { (dx, dy) ->
        drawCircle(Color(0xFFF6E7B8), 2f, Offset(x + dx * k, y + dy * k))
    }
}

private fun DrawScope.firework(cx: Float, cy: Float, r: Float, color: Color) {
    for (i in 0 until 12) {
        val a = i / 12f * 6.2832f
        drawLine(color.copy(alpha = 0.85f), Offset(cx, cy), Offset(cx + r * cos(a), cy + r * sin(a)), strokeWidth = 2f, cap = StrokeCap.Round)
    }
    drawCircle(color, 3f, Offset(cx, cy))
}

private fun DrawScope.flower(x: Float, y: Float, color: Color) {
    for (i in 0 until 5) rotate(i * 72f, Offset(x, y)) { drawOval(color.copy(alpha = 0.9f), Offset(x - 3f, y - 10f), Size(6f, 10f)) }
    drawCircle(Color(0xFFF2DFA0), 2.4f, Offset(x, y))
}

private fun DrawScope.heart(x: Float, y: Float, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(x, y + s * 0.9f)
        cubicTo(x - s * 1.6f, y - s * 0.2f, x - s * 0.6f, y - s * 1.2f, x, y - s * 0.3f)
        cubicTo(x + s * 0.6f, y - s * 1.2f, x + s * 1.6f, y - s * 0.2f, x, y + s * 0.9f)
        close()
    }
    drawPath(p, color.copy(alpha = 0.85f))
}

private fun DrawScope.confetti(colors: List<Color>) {
    listOf(40f to 120f, 100f to 200f, 170f to 90f, 240f to 160f, 300f to 240f, 350f to 110f, 70f to 300f, 210f to 320f).forEachIndexed { i, (x, y) ->
        rotate(i * 37f, Offset(x, y)) { drawRect(colors[i % colors.size].copy(alpha = 0.8f), Offset(x, y), Size(5f, 9f)) }
    }
}

/** Segni nel cielo, dietro le colline. */
private fun DrawScope.festivitySky(scene: Scene) {
    val night = scene.dark
    when (scene.festivity) {
        Festivity.NewYearsEve -> if (night || scene.moment == Moment.Evening) {
            firework(110f, 160f, 40f, Color(0xFFD2B06A)); firework(270f, 230f, 30f, Color(0xFFBA9BCB)); firework(200f, 110f, 22f, Color(0xFFE8939B))
        } else confetti(listOf(Color(0xFFD2B06A), Color(0xFFBA9BCB), Color(0xFFE8939B)))
        Festivity.NewYear -> confetti(listOf(Color(0xFFD2B06A), Color(0xFFBA9BCB), Color(0xFFE8939B), Color(0xFF8FA9D9)))
        Festivity.Christmas, Festivity.ChristmasEve -> if (!night) snow()
        Festivity.Epiphany -> {
            // La Befana sulla scopa davanti alla luna (o al sole).
            val col = if (night) Color(0xFF3A2E2A) else Color(0xFF6B5A55)
            drawLine(col, Offset(240f, 150f), Offset(300f, 136f), strokeWidth = 2.5f, cap = StrokeCap.Round)
            drawCircle(col, 6f, Offset(262f, 140f))
            drawPath(Path().apply { moveTo(256f, 142f); lineTo(250f, 152f); lineTo(268f, 148f); close() }, col)
        }
        Festivity.Birthday -> {
            listOf(Triple(90f, 170f, Color(0xFFE8939B)), Triple(140f, 140f, Color(0xFFD2B06A)), Triple(300f, 190f, Color(0xFFBA9BCB)), Triple(250f, 150f, Color(0xFF8FA9D9)))
                .forEach { (x, y, col) ->
                    drawOval(col.copy(alpha = 0.92f), Offset(x - 16f, y - 20f), Size(32f, 40f))
                    drawLine(col.copy(alpha = 0.6f), Offset(x, y + 20f), Offset(x + 3f, y + 80f), strokeWidth = 1.4f)
                }
        }
        Festivity.Anniversary -> { heart(200f, 200f, 22f, Color(0xFFE8939B)); heart(150f, 260f, 10f, Color(0xFFDE919A)); heart(260f, 250f, 12f, Color(0xFFDE919A)) }
        else -> Unit
    }
}

/** Segni a terra, davanti alle colline. */
private fun DrawScope.festivityGround(scene: Scene) {
    val night = scene.dark
    when (scene.festivity) {
        Festivity.ChristmasEve, Festivity.Christmas -> {
            val col = if (night) Color(0xFF1C2A2A) else Color(0xFF6E7F91)
            tree(80f, 560f, 1.1f, col, lights = night); tree(130f, 590f, 0.8f, col, lights = night); tree(310f, 600f, 1f, col, lights = night)
        }
        Festivity.Easter -> {
            listOf(Triple(90f, 690f, Color(0xFFE8C9D2)), Triple(120f, 702f, Color(0xFFF2E1A8)), Triple(150f, 694f, Color(0xFFC9D8EA)))
                .forEach { (x, y, col) -> drawOval(col, Offset(x - 12f, y - 16f), Size(24f, 32f)) }
            flower(250f, 720f, Color(0xFFF4F0FA)); flower(300f, 740f, Color(0xFFE8C9D2))
        }
        Festivity.SpringStart -> listOf(Triple(50f, 700f, 0xFFF2E1EA), Triple(110f, 735f, 0xFFF4F0FA), Triple(180f, 705f, 0xFFF2E1A8),
            Triple(250f, 745f, 0xFFF2E1EA), Triple(330f, 715f, 0xFFF4F0FA), Triple(300f, 790f, 0xFFF2E1A8)).forEach { (x, y, col) -> flower(x, y, Color(col)) }
        Festivity.SummerStart -> drawPath(
            PathParser().parsePathString("M0 760 C80 750 160 770 240 755 C300 745 350 752 390 748 L390 844 L0 844Z").toPath(),
            Color(0xFF8FB7C4).copy(alpha = if (night) 0.3f else 0.65f),
        )
        Festivity.AutumnStart -> listOf(Triple(60f, 640f, 0xFFC27A4A), Triple(140f, 690f, 0xFFB8603A), Triple(230f, 660f, 0xFFD2A04A),
            Triple(310f, 720f, 0xFFC27A4A), Triple(90f, 770f, 0xFFD2A04A), Triple(280f, 790f, 0xFFB8603A)).forEachIndexed { i, (x, y, col) ->
            rotate(i * 40f, Offset(x, y)) { drawOval(Color(col).copy(alpha = 0.9f), Offset(x - 8f, y - 4f), Size(16f, 8f)) }
        }
        Festivity.WinterStart -> drawPath(SnowCap, Color.White.copy(alpha = if (night) 0.3f else 0.8f))
        else -> Unit
    }
}

package eu.eurise.core.designsystem.icon

import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le tre serie delle icone (tela «Icone della suite», pagina Icone): bolle chiare e bolle scure con il disegno nel colore
 * dell'app, e colorate. Stanno qui perché le usano sia la Scrivania sia la Postazione.
 */
enum class SuiteSeries { Light, Dark, Color }

/** Quale disegno della suite spetta a un'app, dal pacchetto e dall'attività con cui compare nel cassetto. */
object SuiteKeys {
    fun forComponent(pkg: String, cls: String): String? = when {
        pkg.startsWith("eu.eurise.phone") -> if (cls.endsWith("ContactsAlias")) "Contatti" else "Telefono"
        pkg.startsWith("eu.eurise.browser") -> "Browser"
        pkg.startsWith("eu.eurise.mail") -> "Email"
        pkg.startsWith("eu.eurise.chat") -> "Chat"
        pkg.startsWith("eu.eurise.messages") -> "Messaggi"
        pkg.startsWith("eu.eurise.gallery") -> "Galleria"
        pkg.startsWith("eu.eurise.camera") -> "Fotocamera"
        pkg.startsWith("eu.eurise.password") -> "Password"
        pkg.startsWith("eu.eurise.keyboard") -> "Tastiera"
        pkg.startsWith("eu.eurise.maps") -> "Mappe"
        pkg.startsWith("eu.eurise.office") -> "Office"
        pkg.startsWith("eu.eurise.voice") -> "Alfred"
        pkg.startsWith("eu.eurise.read") -> "Leggi"
        pkg.startsWith("eu.eurise.translate") -> "Traduttore"
        cls.endsWith(".FileApp") -> "Drive"
        cls.endsWith(".ScannerApp") -> "Scanner"
        cls.endsWith(".AgendaApp") -> "Agenda"
        cls.endsWith(".CalendarApp") -> "Calendario"
        cls.endsWith(".TasksApp") -> "Attività"
        cls.endsWith(".NotesApp") -> "Note"
        cls.endsWith(".BackupApp") -> "Backup"
        else -> null
    }

    /** Il disegno di quell'app, o null se non è una delle nostre. */
    fun drawing(pkg: String, cls: String): SuiteDrawings.Drawing? = forComponent(pkg, cls)?.let { SuiteDrawings.all[it] }
}

/** I colori di [drawing] nella serie scelta. */
fun suitePalette(drawing: SuiteDrawings.Drawing, series: SuiteSeries): SuiteDrawings.Palette = when (series) {
    SuiteSeries.Light -> drawing.light
    SuiteSeries.Dark -> drawing.dark
    SuiteSeries.Color -> drawing.color
}

private val paths = LruCache<String, Path>(400)

/** Il tracciato di una parte, letto una volta sola. */
private fun pathOf(d: String): Path = paths.get(d) ?: PathParser().parsePathString(d).toPath().also { paths.put(d, it) }

/**
 * Il disegno di un'app della suite nella serie scelta, alla misura del riquadro. [hole] è il colore degli incavi quando
 * la bolla non ha il colore di serie (per esempio la carta della Postazione).
 */
@Composable
fun SuiteDrawingCanvas(drawing: SuiteDrawings.Drawing, series: SuiteSeries, modifier: Modifier = Modifier, hole: Color? = null) {
    val pal = suitePalette(drawing, series)
    val incavo = hole ?: pal.hole
    Canvas(modifier) {
        // Sulla tela il disegno da 48 sta in un riquadro da -7,5 a 55,5 grande quanto la bolla.
        val k = size.width / 63f
        translate(7.5f * k, 7.5f * k) {
            scale(k, k, pivot = Offset.Zero) {
                val strokeBrush = Brush.linearGradient(listOf(pal.s0, pal.s1), start = Offset(8f, 6f), end = Offset(40f, 42f))
                val fillBrush = Brush.linearGradient(listOf(pal.p0, pal.p1), start = Offset(10f, 6f), end = Offset(38f, 42f))
                drawing.parts.forEach { part ->
                    val p = pathOf(part.path)
                    when (part.kind) {
                        "t" -> drawPath(p, pal.tint)
                        "s" -> drawPath(p, strokeBrush, style = Stroke(pal.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        "S" -> drawPath(p, fillBrush, style = Stroke(pal.stroke + 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        "p" -> drawPath(p, fillBrush)
                        "h" -> drawPath(p, incavo, style = Stroke(pal.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        "hf" -> drawPath(p, incavo)
                        // Bestiario del 26 novembre 2026: secondo piano a velo, tagli e filo di contorno.
                        "q" -> drawPath(p, fillBrush, alpha = 0.5f)
                        "v" -> drawPath(p, fillBrush, alpha = 0.62f, style = Stroke(2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        "k" -> drawPath(p, incavo, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        "H" -> drawPath(p, incavo, style = Stroke(pal.stroke + 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        "o" -> drawPath(p, incavo, alpha = 0.55f, style = Stroke(1.3f, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

/**
 * La bolla di una serie: vetro satinato chiaro o scuro (bianco o grafite opaco, luce in alto a sinistra, bordo di luce in
 * alto), oppure il tondo colorato con la sua luce. Dentro, [content]. Con [flat] la bolla è quella della tavola «Le icone
 * a bolle» (26 novembre 2026): tinta unita [fill], un filo di contorno, ombra appena accennata, nessun riflesso.
 */
@Composable
fun SuiteBubble(
    series: SuiteSeries,
    size: Dp,
    shape: Shape,
    round: List<Color>,
    glow: Color,
    fill: Color? = null,
    /** Fondo che sale dritto dal basso e dove cade ogni colore (vedi [SuiteDrawings.Drawing]). */
    vertical: Boolean = false,
    stops: List<Float> = listOf(0f, 0.45f, 1f),
    flat: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (flat) {
        val tinta = fill ?: if (series == SuiteSeries.Dark) BubbleDark else BubbleLight
        val filo = if (series == SuiteSeries.Dark) Color.White.copy(alpha = 0.08f) else Color(0xFF1C1D20).copy(alpha = 0.08f)
        Box(
            Modifier.size(size).shadow(1.5.dp, shape, clip = false).clip(shape).background(tinta).border(1.dp, SolidColor(filo), shape),
            contentAlignment = Alignment.Center,
        ) { content() }
        return
    }
    val rim = when (series) {
        SuiteSeries.Light -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.10f)))
        SuiteSeries.Dark -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.25f)))
        SuiteSeries.Color -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.12f)))
    }
    val light = when (series) {
        SuiteSeries.Light -> Color.White.copy(alpha = 0.62f)
        SuiteSeries.Dark -> Color.White.copy(alpha = 0.12f)
        SuiteSeries.Color -> glow
    }
    Box(
        Modifier
            .size(size)
            .shadow(if (series == SuiteSeries.Dark) 7.dp else 5.dp, shape, clip = false)
            .clip(shape)
            .then(
                when {
                    // Il vetro delle bolle della Scrivania (tema «Sfondo delle bolle»), un filo più chiaro in alto.
                    fill != null -> Modifier.background(Brush.verticalGradient(listOf(lerp(fill, Color.White, 0.10f), fill)))
                    else -> when (series) {
                        SuiteSeries.Light -> Modifier.background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.66f), Color.White.copy(alpha = 0.46f))))
                        SuiteSeries.Dark -> Modifier.background(Brush.linearGradient(listOf(Color(0xB0262B33), Color(0xC4181B21))))
                        SuiteSeries.Color -> Modifier.background(roundBrush(round, vertical, stops))
                    }
                },
            )
            .background(lightSpot(light))
            .border(1.dp, rim, shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Il fuori delle icone a bolle della tavola: carta chiara di giorno, grafite di notte (gli incavi dei disegni lo riprendono). */
val BubbleLight = Color(0xFFF6F5F1)
val BubbleDark = Color(0xFF262A33)

/** Il tondo colorato: sfumatura a 165 gradi (dall'alto, appena a sinistra, al basso), con la tinta piena al 45 %. */
private fun roundBrush(colors: List<Color>, vertical: Boolean = false, stops: List<Float> = listOf(0f, 0.45f, 1f)): Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size) = androidx.compose.ui.graphics.LinearGradientShader(
        from = Offset(size.width * (if (vertical) 0.5f else 0.37f), 0f), to = Offset(size.width * (if (vertical) 0.5f else 0.63f), size.height),
        colors = colors, colorStops = stops.takeIf { it.size == colors.size } ?: listOf(0f, 0.45f, 1f),
    )
}

/** La luce in alto a sinistra (centro al 32 % e 24 %, raggio a metà della bolla), come sulla tela. */
private fun lightSpot(color: Color): Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size) = androidx.compose.ui.graphics.RadialGradientShader(
        center = Offset(size.width * 0.32f, size.height * 0.24f), radius = size.width * 0.58f,
        colors = listOf(color, color.copy(alpha = 0f)),
    )
}

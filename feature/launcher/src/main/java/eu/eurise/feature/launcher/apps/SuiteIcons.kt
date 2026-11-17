package eu.eurise.feature.launcher.apps

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le tre serie nuove delle icone (tela «Icone della suite», pagina Icone, approvate il 16 novembre 2026), come temi in
 * più accanto ai vecchi: bolle chiare e bolle scure di vetro satinato, e colorate. Le app della suite hanno il loro
 * disegno ([SuiteDrawings]); le altre il loro simbolo nella stessa bolla.
 */
internal object SuiteIcons {
    /** La serie di un tema nuovo, o null per i temi di prima. */
    enum class Series { Light, Dark, Color }

    fun series(theme: String): Series? = when (theme) {
        IconThemes.GLASS_LIGHT -> Series.Light
        IconThemes.GLASS_DARK -> Series.Dark
        IconThemes.VIVID -> Series.Color
        else -> null
    }

    /** Il disegno della suite per [entry]: le sezioni di Eurise per nome, le app separate per pacchetto. */
    fun drawingFor(entry: AppEntry): SuiteDrawings.Drawing? = keyFor(entry)?.let { SuiteDrawings.all[it] }

    internal fun keyFor(entry: AppEntry): String? {
        entry.euriseSection?.let { section ->
            return when (section) {
                "today" -> "Agenda"
                "calendar" -> "Calendario"
                "notes" -> "Note"
                "tasks" -> "Attività"
                "clock" -> "Orologio"
                "drive" -> "Drive"
                "scanner" -> "Scanner"
                "calculator" -> "Calcolatrice"
                "weather" -> "Meteo"
                "alfred" -> "Alfred"
                else -> null
            }
        }
        val pkg = entry.packageName
        val cls = entry.component?.className.orEmpty()
        return when {
            pkg.startsWith("eu.eurise.phone") -> if (cls.endsWith("ContactsAlias")) "Contatti" else "Telefono"
            pkg.startsWith("eu.eurise.browser") -> "Browser"
            pkg.startsWith("eu.eurise.mail") -> "Email"
            pkg.startsWith("eu.eurise.chat") -> "Chat"
            pkg.startsWith("eu.eurise.messages") -> "Messaggi"
            pkg.startsWith("eu.eurise.gallery") -> "Galleria"
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
            else -> null
        }
    }

    fun palette(drawing: SuiteDrawings.Drawing, series: Series) = when (series) {
        Series.Light -> drawing.light
        Series.Dark -> drawing.dark
        Series.Color -> drawing.color
    }

    private val paths = LruCache<String, Path>(400)

    /** Il tracciato di una parte, letto una volta sola. */
    fun path(d: String): Path = paths.get(d) ?: PathParser().parsePathString(d).toPath().also { paths.put(d, it) }

    /** I colori del fondo colorato per un'app senza disegno della suite: la sua tinta, più chiara in alto, più cupa in basso. */
    fun roundFor(tint: Color): List<Color> = listOf(lerp(tint, Color.White, 0.18f), tint, lerp(tint, Color.Black, 0.25f))
}

/** Il disegno di un'app della suite nella serie scelta, alla misura del riquadro. */
@Composable
internal fun SuiteDrawingCanvas(drawing: SuiteDrawings.Drawing, series: SuiteIcons.Series, modifier: Modifier = Modifier) {
    val pal = SuiteIcons.palette(drawing, series)
    Canvas(modifier) {
        // Sulla tela il disegno da 48 sta in un riquadro da -7,5 a 55,5 grande quanto la bolla.
        val k = size.width / 63f
        translate(7.5f * k, 7.5f * k) {
            scale(k, k, pivot = Offset.Zero) {
                val strokeBrush = Brush.linearGradient(listOf(pal.s0, pal.s1), start = Offset(8f, 6f), end = Offset(40f, 42f))
                val fillBrush = Brush.linearGradient(listOf(pal.p0, pal.p1), start = Offset(10f, 6f), end = Offset(38f, 42f))
                drawing.parts.forEach { part ->
                    val p = SuiteIcons.path(part.path)
                    when (part.kind) {
                        "t" -> drawPath(p, pal.tint)
                        "s" -> drawPath(p, strokeBrush, style = Stroke(pal.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        "S" -> drawPath(p, fillBrush, style = Stroke(pal.stroke + 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        "p" -> drawPath(p, fillBrush)
                        "h" -> drawPath(p, pal.hole, style = Stroke(pal.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        "hf" -> drawPath(p, pal.hole)
                    }
                }
            }
        }
    }
}

/**
 * La bolla di una serie nuova: vetro satinato chiaro o scuro (bianco o grafite opaco, luce in alto a sinistra, bordo di
 * luce in alto), oppure il tondo colorato con la sua luce. Dentro, [content].
 */
@Composable
internal fun SuiteBubble(
    series: SuiteIcons.Series,
    size: Dp,
    shape: Shape,
    round: List<Color>,
    glow: Color,
    fill: Color? = null,
    content: @Composable () -> Unit,
) {
    val rim = when (series) {
        SuiteIcons.Series.Light -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.10f)))
        SuiteIcons.Series.Dark -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.25f)))
        SuiteIcons.Series.Color -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.12f)))
    }
    val light = when (series) {
        SuiteIcons.Series.Light -> Color.White.copy(alpha = 0.62f)
        SuiteIcons.Series.Dark -> Color.White.copy(alpha = 0.12f)
        SuiteIcons.Series.Color -> glow
    }
    Box(
        Modifier
            .size(size)
            .shadow(if (series == SuiteIcons.Series.Dark) 7.dp else 5.dp, shape, clip = false)
            .clip(shape)
            .then(
                when {
                    // Il vetro delle bolle della Scrivania (tema «Sfondo delle bolle»), un filo più chiaro in alto.
                    fill != null -> Modifier.background(Brush.verticalGradient(listOf(lerp(fill, Color.White, 0.10f), fill)))
                    else -> when (series) {
                    SuiteIcons.Series.Light -> Modifier.background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.66f), Color.White.copy(alpha = 0.46f))))
                    SuiteIcons.Series.Dark -> Modifier.background(Brush.linearGradient(listOf(Color(0xB0262B33), Color(0xC4181B21))))
                    SuiteIcons.Series.Color -> Modifier.background(roundBrush(round))
                    }
                },
            )
            .background(lightSpot(light))
            .border(1.dp, rim, shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Il tondo colorato: sfumatura a 165 gradi (dall'alto, appena a sinistra, al basso), con la tinta piena al 45 %. */
private fun roundBrush(colors: List<Color>): Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size) = androidx.compose.ui.graphics.LinearGradientShader(
        from = Offset(size.width * 0.37f, 0f), to = Offset(size.width * 0.63f, size.height),
        colors = colors, colorStops = listOf(0f, 0.45f, 1f),
    )
}

/** La luce in alto a sinistra (centro al 32 % e 24 %, raggio a metà della bolla), come sulla tela. */
private fun lightSpot(color: Color): Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size) = androidx.compose.ui.graphics.RadialGradientShader(
        center = Offset(size.width * 0.32f, size.height * 0.24f), radius = size.width * 0.58f,
        colors = listOf(color, color.copy(alpha = 0f)),
    )
}

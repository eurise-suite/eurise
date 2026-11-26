package eu.eurise.postazione.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.postazione.R
import eu.eurise.postazione.data.Cielo
import eu.eurise.postazione.data.Gruppo

/** I tre accenti della suite (tela «Tavolozza»): Ceruleo di partenza, Petrolio più sobrio, Inchiostro e ottone più caldo. */
enum class Accento { Ceruleo, Petrolio, Ottone }

private fun sfuma(a: Long, b: Long, c: Long) = Brush.linearGradient(listOf(Color(a), Color(b), Color(c)))

/** Il cangiante della lente (accento Ceruleo), lo stesso della riga del «Prossimo» e del pulsante principale. */
val Lente = sfuma(0xFF2AA2E2, 0xFF1298DF, 0xFF003AC7)

class Colori(
    val notte: Boolean,
    val fondo: Color,
    val foglio: Color,
    val riquadro: Color,
    val prossimo: Color,
    val inchiostro: Color,
    val sotto: Color,
    val riga: Color,
    val accento: Color,
    val barra: Color,
    val lente: Brush,
)

/** I colori della Postazione: carta e inchiostro uguali per tutti, l'accento a scelta; di sera l'accento si spegne. */
fun colori(notte: Boolean, accento: Accento = Accento.Ceruleo): Colori {
    val (lente, tinta, prossimo) = when (accento) {
        Accento.Ceruleo -> if (notte) Triple(sfuma(0xFF6E9AD3, 0xFF4F7CC0, 0xFF34579A), Color(0xFF9DB6DE), Color(0xFF212A38))
            else Triple(Lente, Color(0xFF0A5BD0), Color(0xFFEAF2FB))
        Accento.Petrolio -> if (notte) Triple(sfuma(0xFF5E9E9C, 0xFF3F7F84, 0xFF2B5A6A), Color(0xFF8CC3C0), Color(0xFF1F2B2E))
            else Triple(sfuma(0xFF2BA3A0, 0xFF13858A, 0xFF0B4F6C), Color(0xFF0E6E74), Color(0xFFE6F1F0))
        Accento.Ottone -> if (notte) Triple(sfuma(0xFFC9A35A, 0xFFA8843F, 0xFF7C6130), Color(0xFFD6B572), Color(0xFF262420))
            else Triple(sfuma(0xFFD9A441, 0xFFB98429, 0xFF8A6420), Color(0xFF8A6420), Color(0xFFF5EEDF))
    }
    return if (notte) Colori(
        true, Color(0xFF12161D), Color(0xFF181D25), Color(0xFF232B37), prossimo,
        Color(0xFFE9EBEE), Color(0xFF9AA3B0), Color(0x12FFFFFF), tinta, Color(0xEB181D25), lente,
    ) else Colori(
        false, Color(0xFFF2F0EB), Color(0xFFFBFAF7), Color(0xFFF2F1EC), prossimo,
        Color(0xFF1C1D20), Color(0xFF62615C), Color(0x141C1D20), tinta, Color(0xF0FBFAF7), lente,
    )
}

/** Il colore del testo sopra il cielo: chiaro di sera e di notte, scuro di giorno. */
fun testoSulCielo(c: Cielo) = if (c == Cielo.Notte || c == Cielo.Tramonto) Color(0xFFF2F3F5) else Color(0xFF1B2536)

/** Il cielo: solo luce, niente disegni (giro 3 della tela: «meno giocattolo»). */
@Composable
fun CieloDiFondo(c: Cielo, modifier: Modifier = Modifier) {
    val (base, luce) = when (c) {
        Cielo.Mattino -> listOf(Color(0xFFAFC6E0), Color(0xFFD3DEEA), Color(0xFFEEEAE3)) to Color(0xF2FFF4D6)
        Cielo.Giorno -> listOf(Color(0xFF9DBEE3), Color(0xFFCFDDEC), Color(0xFFEFEDE7)) to Color(0xCCFFFBEF)
        Cielo.Tramonto -> listOf(Color(0xFF3F5275), Color(0xFF7D7C93), Color(0xFFC9A48F), Color(0xFFE7CDB2)) to Color(0x8CFFD6AA)
        Cielo.Notte -> listOf(Color(0xFF0C1119), Color(0xFF141C2A), Color(0xFF1C2638)) to Color(0x29D6DEF0)
    }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(base))) {
        Canvas(Modifier.fillMaxSize()) {
            val centro = if (c == Cielo.Tramonto) Offset(size.width * 0.18f, size.height * 0.30f) else Offset(size.width * 0.82f, size.height * 0.07f)
            drawCircle(Brush.radialGradient(listOf(luce, luce.copy(alpha = 0f)), centro, size.width * 0.55f), size.width * 0.55f, centro)
        }
    }
}

/** La lente della barra con la stellina della suite al centro: il rimando al segno di Eurise. */
@Composable
fun LenteEurise(colore: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val cx = w * 0.44f
        val cy = w * 0.44f
        val r = w * 0.27f
        val tratto = w * 0.085f
        drawCircle(colore, r, Offset(cx, cy), style = Stroke(tratto))
        val d = r * 0.7071f + tratto * 0.4f
        drawLine(colore, Offset(cx + d, cy + d), Offset(w * 0.86f, w * 0.86f), tratto * 1.1f, StrokeCap.Round)
        val s = r * 0.62f
        val q = s * 0.12f
        val stella = Path().apply {
            moveTo(cx, cy - s)
            quadraticTo(cx + q, cy - q, cx + s, cy)
            quadraticTo(cx + q, cy + q, cx, cy + s)
            quadraticTo(cx - q, cy + q, cx - s, cy)
            quadraticTo(cx - q, cy - q, cx, cy - s)
            close()
        }
        drawPath(stella, colore)
    }
}

class Aspetto(@StringRes val nome: Int, val icona: ImageVector, val tinta: Color)

fun aspetto(g: Gruppo): Aspetto = when (g) {
    Gruppo.Lavoro -> Aspetto(R.string.g_lavoro, EuriseIcons.work(), Color(0xFF7A2EE0))
    Gruppo.Foto -> Aspetto(R.string.g_foto, EuriseIcons.camera(), Color(0xFF4A4440))
    Gruppo.Mappe -> Aspetto(R.string.g_mappe, EuriseIcons.map(), Color(0xFF1D55E6))
    Gruppo.WebAI -> Aspetto(R.string.g_webai, EuriseIcons.globe(), Color(0xFFB0307A))
    Gruppo.Comunica -> Aspetto(R.string.g_comunica, EuriseIcons.chat(), Color(0xFF1F9A47))
    Gruppo.Musica -> Aspetto(R.string.g_musica, EuriseIcons.music(), Color(0xFFD2453B))
    Gruppo.Casa -> Aspetto(R.string.g_casa, EuriseIcons.bag(), Color(0xFFB07A10))
    Gruppo.Appunti -> Aspetto(R.string.g_appunti, EuriseIcons.note(), Color(0xFFA8742A))
}

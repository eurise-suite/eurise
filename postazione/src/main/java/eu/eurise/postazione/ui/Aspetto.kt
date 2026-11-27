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
import eu.eurise.postazione.data.Categoria
import eu.eurise.postazione.data.Gruppo
import androidx.compose.ui.res.stringResource

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
    /** Il fondo delle bolle sopra il foglio satinato: un filo più pieno, perché si legga sempre. */
    val bolla: Color,
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
    // Il foglio è satinato e quasi trasparente sopra il cielo (27 novembre 2026); le bolle e la dock un filo più piene.
    return if (notte) Colori(
        true, Color(0xFF12161D), Color(0x57161B24), Color(0x1FFFFFFF), prossimo.copy(alpha = 0.78f),
        Color(0xFFE9EBEE), Color(0xFFAAB2BE), Color(0x14FFFFFF), tinta, Color(0xA6161B24), lente, Color(0xA81E2430),
    ) else Colori(
        false, Color(0xFFF2F0EB), Color(0x57FBFAF7), Color(0x14000000), prossimo.copy(alpha = 0.80f),
        Color(0xFF1C1D20), Color(0xFF5C5B56), Color(0x121C1D20), tinta, Color(0xB8FBFAF7), lente, Color(0xBDFFFFFF),
    )
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

class Aspetto(val nome: String, val icona: ImageVector, val tinta: Color)

/** Le icone che si possono dare a una categoria della dock, ognuna con il suo colore (come nella Scrivania). */
val IconeDock: List<String> = listOf(
    "work", "camera", "map", "globe", "music", "chat", "home", "note", "calendar", "bank", "cart", "plane",
    "medical", "game", "book", "fitness", "star", "heart", "folder", "tools", "phone", "mail", "car", "paw",
    "gift", "school", "leaf", "coffee", "spark", "news", "image", "shield", "euro", "baby", "palette", "code",
)

fun iconaDock(chiave: String): ImageVector = when (chiave) {
    "work" -> EuriseIcons.work(); "camera" -> EuriseIcons.camera(); "map" -> EuriseIcons.map(); "globe" -> EuriseIcons.globe()
    "music" -> EuriseIcons.music(); "chat" -> EuriseIcons.chat(); "home" -> EuriseIcons.home(); "note" -> EuriseIcons.note()
    "calendar" -> EuriseIcons.calendar(); "bank" -> EuriseIcons.bank(); "cart" -> EuriseIcons.cart(); "plane" -> EuriseIcons.plane()
    "medical" -> EuriseIcons.medical(); "game" -> EuriseIcons.game(); "book" -> EuriseIcons.book(); "fitness" -> EuriseIcons.fitness()
    "star" -> EuriseIcons.star(); "heart" -> EuriseIcons.heart(); "folder" -> EuriseIcons.folder(); "tools" -> EuriseIcons.tools()
    "phone" -> EuriseIcons.phone(); "mail" -> EuriseIcons.mail(); "car" -> EuriseIcons.car(); "paw" -> EuriseIcons.paw()
    "gift" -> EuriseIcons.gift(); "school" -> EuriseIcons.school(); "leaf" -> EuriseIcons.leaf(); "coffee" -> EuriseIcons.coffee()
    "spark" -> EuriseIcons.spark(); "news" -> EuriseIcons.news(); "image" -> EuriseIcons.image(); "shield" -> EuriseIcons.shield()
    "euro" -> EuriseIcons.euro(); "baby" -> EuriseIcons.baby(); "palette" -> EuriseIcons.palette(); "code" -> EuriseIcons.code()
    else -> EuriseIcons.grid()
}

fun tintaDock(chiave: String): Color = Color(
    when (chiave) {
        "work" -> 0xFF7A2EE0; "camera" -> 0xFF4A4440; "map" -> 0xFF1D55E6; "globe" -> 0xFFB0307A; "music" -> 0xFFD2453B
        "chat" -> 0xFF1F9A47; "home" -> 0xFFB07A10; "note" -> 0xFFA8742A; "calendar" -> 0xFFC2410C; "bank" -> 0xFF0F766E
        "cart" -> 0xFFB45309; "plane" -> 0xFF0369A1; "medical" -> 0xFFDC2626; "game" -> 0xFF7C3AED; "book" -> 0xFF92400E
        "fitness" -> 0xFF16A34A; "star" -> 0xFFCA8A04; "heart" -> 0xFFDB2777; "phone", "mail" -> 0xFF1F9A47
        "car", "paw", "leaf" -> 0xFF4D7C0F; "coffee", "euro" -> 0xFF78501E; "shield", "code" -> 0xFF334155
        else -> 0xFF475569
    },
)

@StringRes
fun nomeDi(g: Gruppo): Int = when (g) {
    Gruppo.Lavoro -> R.string.g_lavoro; Gruppo.Foto -> R.string.g_foto; Gruppo.Mappe -> R.string.g_mappe
    Gruppo.WebAI -> R.string.g_webai; Gruppo.Comunica -> R.string.g_comunica; Gruppo.Musica -> R.string.g_musica
    Gruppo.Casa -> R.string.g_casa; Gruppo.Appunti -> R.string.g_appunti; Gruppo.Agenda -> R.string.g_agenda
    Gruppo.Banca -> R.string.g_banca; Gruppo.Acquisti -> R.string.g_acquisti; Gruppo.Viaggi -> R.string.g_viaggi
    Gruppo.Salute -> R.string.g_salute; Gruppo.Giochi -> R.string.g_giochi; Gruppo.Lettura -> R.string.g_lettura
    Gruppo.Sport -> R.string.g_sport
}

@Composable
fun aspetto(c: Categoria): Aspetto {
    val nome = c.nome.ifBlank { c.pronta?.let { stringResource(nomeDi(it)) }.orEmpty() }
    return Aspetto(nome, iconaDock(c.icona), tintaDock(c.icona))
}

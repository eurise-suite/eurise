package eu.eurise.postazione.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import eu.eurise.postazione.data.Cielo
import eu.eurise.postazione.data.Fase
import eu.eurise.postazione.data.Tempo
import kotlin.math.sqrt
import kotlin.random.Random

/** Il colore del testo sopra il cielo: chiaro quando il cielo è scuro (alba, tramonto, sera, notte, temporale). */
fun testoSulCielo(c: Cielo) = if (c.scuro || ((c.fase == Fase.Notte || c.fase == Fase.Sera))) Color(0xFFF2F3F5) else Color(0xFF1B2536)

private class Tinte(val alto: Color, val mezzo: Color, val basso: Color)

private fun t(a: Long, b: Long, c: Long) = Tinte(Color(a), Color(b), Color(c))

/** I colori del cielo nelle sette fasi (tavola «Il sole durante la giornata») e la luce del sole. */
private fun fase(f: Fase): Pair<Tinte, Color> = when (f) {
    Fase.Alba -> t(0xFF3E4E78, 0xFFB98FA8, 0xFFF2BE98) to Color(0xFFFFC58A)
    Fase.Presto -> t(0xFF8FAAD0, 0xFFD8D3D4, 0xFFF4E2CB) to Color(0xFFFFE3B0)
    Fase.Giorno -> t(0xFF8DB3DE, 0xFFC9DAEC, 0xFFEEEAE3) to Color(0xFFFFF6DC)
    Fase.Pomeriggio -> t(0xFF9DB9DA, 0xFFD6DAE0, 0xFFF2E3CB) to Color(0xFFFFE9BC)
    Fase.Tramonto -> t(0xFF4E5C8C, 0xFFD98E7A, 0xFFF6BE7C) to Color(0xFFFFB46B)
    Fase.Sera -> t(0xFF1C2850, 0xFF4F4774, 0xFFA46F82) to Color(0xFFE7A07F)
    Fase.Notte -> t(0xFF0B1530, 0xFF16244A, 0xFF24304C) to Color(0xFFE6ECFF)
}

/** Verso quali colori il tempo porta il cielo, e quanto (di giorno e di sera/notte). */
private fun tempo(tp: Tempo, buio: Boolean): Pair<Tinte, Float>? = when (tp) {
    Tempo.Sereno -> null
    Tempo.Nuvoloso -> if (buio) t(0xFF1A2438, 0xFF26304A, 0xFF323C52) to 0.4f else t(0xFFB9C8D8, 0xFFD6DEE6, 0xFFECE9E4) to 0.5f
    Tempo.Coperto -> if (buio) t(0xFF161B26, 0xFF20273A, 0xFF2C3446) to 0.7f else t(0xFF8F9AA8, 0xFFB4BCC6, 0xFFD9DBDD) to 0.8f
    Tempo.Pioggia -> if (buio) t(0xFF141B27, 0xFF1E2838, 0xFF2A3342) to 0.75f else t(0xFF7F8D9F, 0xFFA6B1BE, 0xFFD6D9DC) to 0.85f
    Tempo.Neve -> if (buio) t(0xFF2A3344, 0xFF3A4458, 0xFF4A5266) to 0.6f else t(0xFFBFC9D6, 0xFFDCE2EA, 0xFFF4F3F1) to 0.8f
    Tempo.Temporale -> if (buio) t(0xFF1A1C2A, 0xFF2A2C40, 0xFF3A3C50) to 0.85f else t(0xFF33374D, 0xFF555A72, 0xFFA4A4B0) to 0.9f
}

/** Quanta luce del sole passa con quel tempo. */
private fun luce(tp: Tempo) = when (tp) {
    Tempo.Sereno -> 1f; Tempo.Nuvoloso -> 0.6f; Tempo.Coperto -> 0.2f; Tempo.Pioggia -> 0.15f; Tempo.Neve -> 0.3f; Tempo.Temporale -> 0f
}

/** Pioggia e neve, in frazioni dello schermo: fisse (niente animazione, per la batteria), uguali a ogni avvio. */
private class Particella(val x: Float, val y: Float, val misura: Float, val forza: Float)

private fun particelle(seme: Int, quante: Int, misure: List<Float>, forze: List<Float>): List<Particella> {
    val r = Random(seme)
    return List(quante) { Particella(r.nextFloat(), r.nextFloat() * 0.52f, misure[r.nextInt(misure.size)], forze[r.nextInt(forze.size)]) }
}

/**
 * Quanto si vede una goccia o un fiocco: si dissolvono avvicinandosi a saluto, orologio e meteo, in alto a sinistra
 * (tavola «Il cielo secondo il tempo»: arrivano e sfumano verso l'orologio, mai sopra).
 */
private fun visibile(x: Float, y: Float, w: Float, h: Float): Float {
    val dx = x / (w * 0.85f)
    val dy = (y - h * 0.06f) / (h * 0.30f)
    val d = sqrt(dx * dx + dy * dy)
    val k = ((d - 0.55f) / 0.45f).coerceIn(0f, 1f)
    return k * k * (3 - 2 * k)
}

/**
 * Il cielo dietro la Postazione: una sfumatura che segue l'ora (alba, mattino, giorno, pomeriggio, tramonto, sera, notte)
 * e un solo segno per il tempo (nuvole sfocate, gocce corte, fiocchi, il bagliore del lampo). Il sole sta nella parte
 * libera, a destra; di notte la luna e poche stelle.
 */
@Composable
fun CieloDiFondo(c: Cielo, modifier: Modifier = Modifier) {
    val buio = c.fase == Fase.Notte || c.fase == Fase.Sera
    val (base, sole) = fase(c.fase)
    val tinte = tempo(c.tempo, buio)?.let { (verso, peso) ->
        Tinte(lerp(base.alto, verso.alto, peso), lerp(base.mezzo, verso.mezzo, peso), lerp(base.basso, verso.basso, peso))
    } ?: base
    val gocce = remember { particelle(11, 70, listOf(7f, 9f, 10f, 12f, 14f), listOf(0.45f, 0.6f, 0.75f)) }
    val fiocchi = remember { particelle(7, 55, listOf(2f, 3f, 3f, 4f, 4f, 5f, 6f), listOf(0.7f, 0.85f, 0.95f)) }
    val stelle = remember { particelle(3, 16, listOf(1f), listOf(0.4f, 0.6f, 0.8f)) }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(0f to tinte.alto, 0.45f to tinte.mezzo, 0.85f to tinte.basso))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // Il sole (o la luna), nella parte libera.
            val p = c.sole
            val forza = luce(c.tempo)
            if (c.fase == Fase.Notte) {
                if (c.tempo == Tempo.Sereno || c.tempo == Tempo.Nuvoloso) {
                    stelle.forEach { s -> drawCircle(Color.White.copy(alpha = s.forza), 1.dp.toPx(), Offset(w * (0.42f + s.x * 0.55f), h * (0.01f + s.y * 0.3f))) }
                    val luna = Offset(w * 0.82f, h * 0.07f)
                    drawCircle(Brush.radialGradient(listOf(sole.copy(alpha = 0.35f), sole.copy(alpha = 0f)), luna, w * 0.14f), w * 0.14f, luna)
                    drawCircle(Color(0xFFE9EEFF), 9.dp.toPx(), luna)
                }
            } else if (p != null && forza > 0f) {
                val centro = Offset(w * p.first, h * 0.5f * p.second)
                val r = w * when (c.fase) { Fase.Giorno -> 0.55f; Fase.Pomeriggio, Fase.Presto -> 0.48f; Fase.Sera -> 0.32f; else -> 0.42f }
                drawCircle(Brush.radialGradient(listOf(sole.copy(alpha = 0.95f * forza), sole.copy(alpha = 0.35f * forza), sole.copy(alpha = 0f)), centro, r), r, centro)
            }
            when (c.tempo) {
                Tempo.Nuvoloso -> {
                    nuvola(Offset(w * 0.75f, h * 0.06f), Size(w * 0.8f, h * 0.15f), Color.White.copy(alpha = if (buio) 0.10f else 0.5f))
                    nuvola(Offset(w * 0.1f, h * 0.24f), Size(w * 0.7f, h * 0.11f), Color.White.copy(alpha = if (buio) 0.08f else 0.4f))
                }
                Tempo.Coperto -> {
                    nuvola(Offset(w * 0.5f, h * 0.02f), Size(w * 1.3f, h * 0.24f), (if (buio) Color(0xFF0E121A) else Color(0xFF788291)).copy(alpha = 0.55f))
                    nuvola(Offset(w * 0.6f, h * 0.22f), Size(w * 0.95f, h * 0.14f), Color.White.copy(alpha = if (buio) 0.05f else 0.35f))
                }
                Tempo.Pioggia -> {
                    nuvola(Offset(w * 0.5f, h * 0.0f), Size(w * 1.4f, h * 0.22f), (if (buio) Color(0xFF0C1018) else Color(0xFF687486)).copy(alpha = 0.55f))
                    gocce.forEach { g ->
                        val x = g.x * w; val y = g.y * h
                        val a = g.forza * visibile(x, y, w, h) * (if (buio) 0.7f else 1f)
                        if (a > 0.02f) {
                            val l = g.misura.dp.toPx()
                            // Una goccia corta, appena inclinata, più chiara in basso.
                            drawLine(
                                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = a)), y, y + l),
                                Offset(x, y), Offset(x - l * 0.2f, y + l), 1.4.dp.toPx(), StrokeCap.Round,
                            )
                        }
                    }
                }
                Tempo.Neve -> fiocchi.forEach { f ->
                    val x = f.x * w; val y = f.y * h
                    val a = f.forza * visibile(x, y, w, h)
                    if (a > 0.02f) drawCircle(Color.White.copy(alpha = a), (f.misura / 2f).dp.toPx(), Offset(x, y))
                }
                Tempo.Temporale -> {
                    // Il lampo tra le nuvole: bianco-lilla al centro, viola e fucsia intorno; le nuvole scure davanti.
                    val lampo = Offset(w * 0.70f, h * 0.10f)
                    drawCircle(Brush.radialGradient(listOf(Color(0xF2F6F0FF), Color(0xBFCEAAFF), Color(0x73C470E6), Color(0x00AA5AD2)), lampo, w * 0.33f), w * 0.33f, lampo)
                    val cuore = Offset(w * 0.74f, h * 0.13f)
                    drawCircle(Brush.radialGradient(listOf(Color(0xE6FFFAFF), Color(0x66EC96F0), Color(0x00EC96F0)), cuore, w * 0.16f), w * 0.16f, cuore)
                    nuvola(Offset(w * 0.18f, h * 0.02f), Size(w * 0.95f, h * 0.2f), Color(0xD1282C40))
                    nuvola(Offset(w * 1.0f, h * 0.02f), Size(w * 0.6f, h * 0.15f), Color(0xB32C3046))
                    nuvola(Offset(w * 0.75f, h * 0.22f), Size(w * 0.9f, h * 0.11f), Color(0xA63E425A))
                }
                Tempo.Sereno -> Unit
            }
        }
    }
}

/** Una nuvola: un'ellisse molto sfumata (niente bordi). */
private fun DrawScope.nuvola(centro: Offset, s: Size, colore: Color) {
    val r = maxOf(s.width, s.height) / 2f
    drawOval(
        Brush.radialGradient(listOf(colore, colore.copy(alpha = colore.alpha * 0.5f), colore.copy(alpha = 0f)), centro, r),
        topLeft = Offset(centro.x - s.width / 2f, centro.y - s.height / 2f), size = s,
    )
}

package eu.eurise.postazione.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.postazione.PostazioneModel
import eu.eurise.postazione.R
import eu.eurise.postazione.data.Gruppi
import eu.eurise.postazione.data.Gruppo
import eu.eurise.postazione.data.Scheda
import eu.eurise.postazione.data.Telefono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La barra in basso: cinque gruppi con la loro icona (un tocco apre la preferita, tenere premuto apre il ventaglio) e la
 * lente con la stellina di Eurise dal lato della mano (un tocco cerca, tenere premuto chiama Alfred).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Barra(
    model: PostazioneModel, scheda: Scheda, col: Colori,
    onGruppo: (Gruppo) -> Unit, onVentaglio: (Gruppo) -> Unit, onLente: () -> Unit, onAlfred: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val lente: @Composable () -> Unit = {
        Column(
            Modifier.combinedClickable(onClick = onLente, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAlfred() }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(46.dp).background(col.lente, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                LenteEurise(Color.White, Modifier.size(26.dp))
            }
            Text(stringResource(R.string.cerca), color = col.sotto, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
    }
    Row(
        modifier.navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 12.dp).fillMaxWidth().height(84.dp)
            .background(col.barra, RoundedCornerShape(26.dp)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (model.mancino) { lente(); Divisore(col) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            Gruppi.barra(scheda).forEach { g ->
                val a = aspetto(g)
                Column(
                    Modifier.combinedClickable(
                        onClick = { onGruppo(g) },
                        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onVentaglio(g) },
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(46.dp).background(a.tinta.copy(alpha = if (col.notte) 0.28f else 0.12f), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(a.icona, null, Modifier.size(23.dp), tint = if (col.notte) Color(0xFFE9EBEE) else a.tinta) }
                    Text(stringResource(a.nome), color = col.sotto, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (!model.mancino) { Divisore(col); lente() }
    }
}

@Composable
private fun Divisore(col: Colori) {
    Box(Modifier.padding(horizontal = 4.dp).padding(bottom = 14.dp).width(1.dp).height(34.dp).background(col.riga))
}

/** Il ventaglio di un gruppo: le sue app (la prima è la preferita), «Aggiungi un'app», togli. Sta sopra la barra. */
@Composable
fun Ventaglio(model: PostazioneModel, g: Gruppo, col: Colori, onChiudi: () -> Unit, onAggiungi: () -> Unit) {
    val lista = model.gruppi[g].orEmpty()
    val a = aspetto(g)
    Box(
        Modifier.fillMaxSize().background(col.fondo.copy(alpha = 0.72f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChiudi() },
    ) {
        Column(
            Modifier.align(if (model.mancino) Alignment.BottomStart else Alignment.BottomEnd).navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 108.dp).width(270.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(a.nome), color = col.inchiostro, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            Text(stringResource(R.string.ventaglio_aiuto), color = col.sotto, fontSize = 12.5.sp, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
            Voce(col, onClick = onAggiungi) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(EuriseIcons.plus(), null, Modifier.size(22.dp), tint = col.accento) }
                Text(stringResource(R.string.aggiungi_app), color = col.accento, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp))
            }
            if (lista.isEmpty()) Text(stringResource(R.string.gruppo_vuoto), color = col.sotto, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
            lista.forEachIndexed { i, chiave ->
                Voce(col, onClick = { if (model.telefono.apri(chiave)) onChiudi() }) {
                    IconaApp(model.telefono, chiave, 40.dp)
                    Text(model.nomeDi(chiave), color = col.inchiostro, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Icon(
                        EuriseIcons.star(), stringResource(if (i == 0) R.string.preferita else R.string.rendi_preferita),
                        Modifier.size(34.dp).clickable(enabled = i != 0) { model.preferita(g, chiave) }.padding(7.dp),
                        tint = if (i == 0) col.accento else col.sotto.copy(alpha = 0.5f),
                    )
                    Icon(
                        EuriseIcons.close(), stringResource(R.string.togli),
                        Modifier.size(34.dp).clickable { model.togli(g, chiave) }.padding(8.dp), tint = col.sotto,
                    )
                }
            }
        }
    }
}

@Composable
private fun Voce(col: Colori, onClick: () -> Unit, contenuto: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).background(col.foglio, RoundedCornerShape(20.dp)).clickable { onClick() }
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = contenuto,
    )
}

/** L'icona vera dell'app, letta da Android fuori dal thread principale e tenuta in memoria. */
@Composable
fun IconaApp(telefono: Telefono, chiave: String, misura: Dp) {
    val px = with(LocalDensity.current) { misura.roundToPx() }
    val bmp by produceState<ImageBitmap?>(null, chiave, px) { value = withContext(Dispatchers.IO) { telefono.icona(chiave, px) } }
    val b = bmp
    if (b != null) Image(b, null, Modifier.size(misura))
    else Box(Modifier.size(misura).background(Color(0x22888888), CircleShape))
}

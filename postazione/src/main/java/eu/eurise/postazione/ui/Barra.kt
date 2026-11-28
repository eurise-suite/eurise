package eu.eurise.postazione.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.icon.SuiteBubble
import eu.eurise.core.designsystem.icon.SuiteDrawingCanvas
import eu.eurise.core.designsystem.icon.SuiteSeries
import eu.eurise.core.designsystem.icon.shimmer
import eu.eurise.postazione.PostazioneModel
import eu.eurise.postazione.R
import eu.eurise.postazione.data.Categoria
import eu.eurise.postazione.data.Gruppi
import eu.eurise.postazione.data.Gruppo
import eu.eurise.postazione.data.Icona
import eu.eurise.postazione.data.Siti
import eu.eurise.postazione.data.Telefono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La dock in basso: otto categorie con la loro icona, senza scritte, le stesse in ogni scheda (un tocco apre il ventaglio con le sue app, tenere
 * premuto apre la preferita) e la lente con la stellina di Eurise dal lato della mano (un tocco cerca ovunque, tenere premuto
 * chiama Alfred).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Barra(
    model: PostazioneModel, col: Colori,
    onGruppo: (String) -> Unit, onVentaglio: (String) -> Unit, onLente: () -> Unit, onAlfred: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val cercaNome = stringResource(R.string.cerca)
    val lente: @Composable () -> Unit = {
        Column(
            Modifier.combinedClickable(onClick = onLente, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAlfred() }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(46.dp).background(col.lente, RoundedCornerShape(15.dp))
                    .semantics { contentDescription = cercaNome },
                contentAlignment = Alignment.Center,
            ) { LenteEurise(Color.White, Modifier.size(25.dp)) }
        }
    }
    Row(
        modifier.navigationBarsPadding().padding(start = 8.dp, end = 8.dp, bottom = 12.dp).fillMaxWidth().height(AltezzaDock)
            .background(col.barra, RoundedCornerShape(26.dp)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (model.mancino) { lente(); Divisore(col) }
        val dock = model.dock.take(Gruppi.IN_DOCK)
        val voci = if (model.mancino) dock.reversed() else dock
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            voci.forEach { c ->
                val a = aspetto(c)
                Column(
                    Modifier.weight(1f).combinedClickable(
                        // Scelta del proprietario (27 novembre, notte): il tocco apre le app della categoria, tenere premuto la preferita.
                        onClick = { onVentaglio(c.id) },
                        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onGruppo(c.id) },
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Solo il disegno nel suo colore, senza scritte (richiesta del proprietario, 28 novembre): il nome lo legge
                    // TalkBack. Il tocco è largo quanto la colonna.
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(a.icona, a.nome, Modifier.size(25.dp), tint = if (col.notte) Color(0xFFE9EBEE) else a.tinta)
                    }
                }
            }
        }
        if (!model.mancino) { Divisore(col); lente() }
    }
}

/** La dock degli schermi larghi (tablet, computer): in colonna dal lato della mano, con la lente in cima. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Binario(
    model: PostazioneModel, col: Colori,
    onGruppo: (String) -> Unit, onVentaglio: (String) -> Unit, onLente: () -> Unit, onAlfred: () -> Unit, onTutte: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 14.dp).width(84.dp).fillMaxHeight().background(col.barra, RoundedCornerShape(26.dp)).padding(vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(52.dp).background(col.lente, RoundedCornerShape(17.dp))
                .combinedClickable(onClick = onLente, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAlfred() }),
            contentAlignment = Alignment.Center,
        ) { LenteEurise(Color.White, Modifier.size(28.dp)) }
        Box(Modifier.width(30.dp).height(1.dp).background(col.riga))
        model.dock.take(Gruppi.IN_DOCK).reversed().forEach { c ->
            val a = aspetto(c)
            Column(
                Modifier.combinedClickable(onClick = { onVentaglio(c.id) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onGruppo(c.id) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(a.icona, a.nome, Modifier.size(25.dp), tint = if (col.notte) Color(0xFFE9EBEE) else a.tinta)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Icon(
            EuriseIcons.grid(), stringResource(R.string.tutte_le_app),
            Modifier.size(46.dp).clickable { onTutte() }.padding(12.dp), tint = col.sotto,
        )
    }
}

@Composable
private fun Divisore(col: Colori) {
    Box(Modifier.padding(horizontal = 4.dp).padding(bottom = 14.dp).width(1.dp).height(34.dp).background(col.riga))
}

/**
 * Il ventaglio di una categoria: un pannello pieno appoggiato sopra la dock, dal lato della mano. Le app partono dal
 * basso, con la preferita (stellina) in fondo, vicino al dito; il tocco apre, la pressione lunga offre «Preferita» e
 * «Togli». In cima, piccoli: il nome della categoria, + per aggiungere un'app e la matita per modificarla.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Ventaglio(model: PostazioneModel, id: String, col: Colori, largo: Boolean, onChiudi: () -> Unit, onAggiungi: () -> Unit, onModifica: () -> Unit) {
    val c = model.categoria(id) ?: return
    val lista = model.gruppi[id].orEmpty()
    val a = aspetto(c)
    val haptic = LocalHapticFeedback.current
    var scelte by remember { mutableStateOf<String?>(null) }
    Box(
        Modifier.fillMaxSize().background(col.fondo.copy(alpha = 0.9f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChiudi() },
    ) {
        Column(
            // Sul telefono sopra la dock; sugli schermi larghi accanto al binario, dal lato della mano.
            Modifier.align(if (largo) (if (model.mancino) Alignment.CenterStart else Alignment.CenterEnd) else if (model.mancino) Alignment.BottomStart else Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(start = if (largo) 116.dp else 12.dp, end = if (largo) 116.dp else 12.dp, bottom = if (largo) 16.dp else AltezzaDock + 18.dp)
                .width(264.dp).heightIn(max = 520.dp)
                .background(col.pieno, RoundedCornerShape(24.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(6.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(a.icona, null, Modifier.size(17.dp), tint = if (col.notte) Color(0xFFE9EBEE) else a.tinta)
                Text(a.nome, color = col.inchiostro, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp))
                Icon(EuriseIcons.plus(), stringResource(R.string.aggiungi_app), Modifier.size(40.dp).clickable { onAggiungi() }.padding(10.dp), tint = col.accento)
                Icon(EuriseIcons.pencil(), stringResource(R.string.modifica_categoria), Modifier.size(40.dp).clickable { onModifica() }.padding(11.dp), tint = col.accento)
            }
            if (lista.isEmpty()) Text(stringResource(R.string.gruppo_vuoto), color = col.sotto, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState(), reverseScrolling = true)) {
                // Dal basso: la preferita (la prima) è l'ultima riga, la più vicina al pollice.
                lista.withIndex().reversed().forEach { (i, chiave) ->
                    Box {
                        Row(
                            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = { if (model.apri(chiave)) onChiudi() },
                                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); scelte = chiave },
                                ).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (Siti.eSito(chiave)) IconaSito(model.nomeDi(chiave), a.tinta, 38.dp, col.notte) else IconaApp(model.telefono, chiave, 38.dp, col.notte)
                            Text(model.nomeDi(chiave), color = col.inchiostro, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            if (i == 0) Icon(EuriseIcons.star(), stringResource(R.string.preferita), Modifier.size(16.dp), tint = col.accento)
                        }
                        DropdownMenu(scelte == chiave, onDismissRequest = { scelte = null }) {
                            if (i != 0) DropdownMenuItem(
                                text = { Text(stringResource(R.string.rendi_preferita)) }, leadingIcon = { Icon(EuriseIcons.star(), null) },
                                onClick = { scelte = null; model.preferita(id, chiave) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.togli)) }, leadingIcon = { Icon(EuriseIcons.close(), null) },
                                onClick = { scelte = null; model.togli(id, chiave) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modifica una categoria della dock: una categoria già pronta al suo posto (porta le sue app), oppure nome e icona a
 * piacere; le frecce la spostano nella dock. Le app si scelgono dal ventaglio.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorCategoria(model: PostazioneModel, id: String, onChiudi: () -> Unit) {
    val c = model.categoria(id) ?: return
    val nomePronto = c.pronta?.let { stringResource(nomeDi(it)) }.orEmpty()
    var nome by remember { mutableStateOf(c.nome.ifBlank { nomePronto }) }
    var icona by remember { mutableStateOf(c.icona) }
    var pronta by remember { mutableStateOf(c.pronta) }
    val nomiPronti = Gruppo.entries.associateWith { stringResource(nomeDi(it)) }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.modifica_categoria)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.cat_pronte), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Gruppo.entries.forEach { g ->
                        val scelta = pronta == g
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (scelta) tintaDock(g.icona).copy(alpha = 0.18f) else Color(0x0F000000))
                                .clickable { pronta = g; nome = nomiPronti.getValue(g); icona = g.icona }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(iconaDock(g.icona), null, Modifier.size(16.dp), tint = tintaDock(g.icona))
                            Text(nomiPronti.getValue(g), fontSize = 13.sp, fontWeight = if (scelta) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                OutlinedTextField(nome, { nome = it.take(24) }, label = { Text(stringResource(R.string.cat_nome)) }, singleLine = true)
                Text(stringResource(R.string.cat_icona), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconeDock.forEach { k ->
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (k == icona) tintaDock(k).copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { icona = k },
                            contentAlignment = Alignment.Center,
                        ) { Icon(iconaDock(k), null, Modifier.size(21.dp), tint = tintaDock(k)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.cat_posto), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton({ model.spostaCategoria(id, -1) }) { Text("←") }
                    TextButton({ model.spostaCategoria(id, 1) }) { Text("→") }
                }
                Text(stringResource(R.string.cat_posto_aiuto, Gruppi.IN_DOCK), fontSize = 11.5.sp, color = Color(0xFF6B6A66))
                if (model.dock.size > 1) TextButton({ model.togliCategoria(id); onChiudi() }) {
                    Text(stringResource(R.string.cat_togli), color = Color(0xFFB3261E))
                }
            }
        },
        confirmButton = {
            TextButton({
                val p = pronta
                // Una categoria pronta tiene il suo id (e le sue app); con un nome diverso dal suo resta pronta ma rinominata.
                val nuova = if (p != null) Categoria(p.name, nome.trim().takeIf { it != nomiPronti.getValue(p) }.orEmpty(), icona)
                else Categoria(if (c.pronta == null) c.id else "c" + System.currentTimeMillis(), nome.trim().ifBlank { nomePronto }, icona)
                model.modificaCategoria(id, nuova)
                onChiudi()
            }) { Text(stringResource(R.string.imp_fatto)) }
        },
        dismissButton = { TextButton(onChiudi) { Text(stringResource(R.string.annulla)) } },
    )
}

/**
 * L'icona di un'app nella fisionomia della suite: bolla chiara di giorno e scura di notte, dentro il disegno nel colore
 * dell'app (il nostro per le app di Eurise, il simbolo dell'app per le altre). Letta fuori dal thread principale.
 */
@Composable
fun IconaApp(telefono: Telefono, chiave: String, misura: Dp, notte: Boolean) {
    val px = with(LocalDensity.current) { misura.roundToPx() }
    val icona by produceState<Icona?>(null, chiave, px) { value = withContext(Dispatchers.IO) { telefono.icona(chiave, px) } }
    val serie = if (notte) SuiteSeries.Dark else SuiteSeries.Light
    when (val i = icona) {
        // Le app non nostre: l'originale a tutto tondo, nella bolla piatta della suite (filo e ombra appena accennata).
        is Icona.Originale -> if (i.pieno) {
            SuiteBubble(serie, misura, CircleShape, emptyList(), Color.White, fill = i.fondo ?: Color.White, flat = true) {
                Image(i.immagine, null, Modifier.size(misura), contentScale = ContentScale.FillBounds)
            }
        } else {
            SuiteBubble(serie, misura, CircleShape, emptyList(), Color.White, flat = true) {
                Image(i.immagine, null, Modifier.size(misura * 0.78f))
            }
        }
        else -> SuiteBubble(serie, misura, CircleShape, emptyList(), Color.White, flat = true) {
            if (i is Icona.Nostra) SuiteDrawingCanvas(i.disegno, serie, Modifier.size(misura))
        }
    }
}

/** Un sito messo in un gruppo: l'iniziale nel colore del gruppo, nella stessa bolla delle app. */
@Composable
fun IconaSito(nome: String, tinta: Color, misura: Dp, notte: Boolean) {
    SuiteBubble(if (notte) SuiteSeries.Dark else SuiteSeries.Light, misura, CircleShape, emptyList(), Color.White, flat = true) {
        Text(
            (nome.firstOrNull() ?: '?').uppercase(), color = shimmer(tinta, notte)[1],
            fontSize = (misura.value * 0.42f).sp, fontWeight = FontWeight.Bold,
        )
    }
}

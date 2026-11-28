package eu.eurise.postazione.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.postazione.PostazioneModel
import eu.eurise.postazione.R
import eu.eurise.postazione.data.Gruppi
import eu.eurise.postazione.data.Pagina
import eu.eurise.postazione.data.Siti

/** Un'app (o un sito) in una griglia: icona e nome; tocco apre, pressione lunga per le scelte. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TesseraApp(
    model: PostazioneModel, chiave: String, col: Colori, testo: Color, scelta: Boolean = false,
    onTocco: () -> Unit, onLungo: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.combinedClickable(onClick = onTocco, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLungo() })
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (Siti.eSito(chiave)) IconaSito(model.nomeDi(chiave), col.accento, 52.dp, col.notte)
            else IconaApp(model.telefono, chiave, 52.dp, col.notte)
            if (scelta) Box(
                Modifier.size(20.dp).background(col.accento, CircleShape).border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(EuriseIcons.check(), null, Modifier.size(12.dp), tint = Color.White) }
        }
        Text(
            model.nomeDi(chiave), color = testo, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/**
 * Una pagina a sinistra della Home: le app che l'utente ci mette, in griglia sul cielo; il riquadro + ne aggiunge,
 * la pressione lunga su un'app la toglie. In alto il nome e la matita per le pagine.
 */
@Composable
fun PaginaUtente(
    model: PostazioneModel, pagina: Pagina, col: Colori, suCielo: Color, largo: Boolean,
    onAggiungi: () -> Unit, onModifica: () -> Unit,
) {
    val lista = model.appPagine[pagina.id].orEmpty()
    var menu by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = if (largo) 14.dp else 124.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 10.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pagina.nome, color = suCielo, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(EuriseIcons.pencil(), stringResource(R.string.pg_titolo), Modifier.size(44.dp).clickable { onModifica() }.padding(12.dp), tint = suCielo.copy(alpha = 0.8f))
        }
        LazyVerticalGrid(
            GridCells.Fixed(if (largo) 6 else 4), Modifier.weight(1f).padding(horizontal = 10.dp)
                .background(col.foglio, RoundedCornerShape(28.dp)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (lista.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.pg_vuota), color = col.sotto, fontSize = 13.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            }
            items(lista, key = { it }) { chiave ->
                Box {
                    TesseraApp(model, chiave, col, col.inchiostro, onTocco = { model.apri(chiave) }, onLungo = { menu = chiave })
                    DropdownMenu(menu == chiave, onDismissRequest = { menu = null }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.togli)) }, onClick = { menu = null; model.togliDaPagina(pagina.id, chiave) })
                    }
                }
            }
            item(key = "+") {
                Column(Modifier.clickable { onAggiungi() }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp).border(1.5.dp, col.accento.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(EuriseIcons.plus(), null, Modifier.size(22.dp), tint = col.accento)
                    }
                    Text(stringResource(R.string.aggiungi_app), color = col.accento, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

private const val TUTTE = "*"

/**
 * La pagina a destra della Home: il cassetto per categorie. In basso la dock delle categorie (fino a dieci, più A–Z e +);
 * tenere premuta un'app la sceglie e se ne scelgono altre con un tocco, poi «Sposta in…», «Copia in…» o «Togli».
 * Tenere premuta una categoria la modifica (nome, icona, posto).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CassettoCategorie(model: PostazioneModel, col: Colori, giri: Int, onModifica: (String) -> Unit) {
    var attiva by rememberSaveable { mutableStateOf(model.dock.firstOrNull()?.id ?: TUTTE) }
    var scelte by remember { mutableStateOf<List<String>>(emptyList()) }
    var destinazione by remember { mutableStateOf<Boolean?>(null) } // true = copia, false = sposta
    var nuova by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(giri) { scelte = emptyList() }
    if (attiva != TUTTE && model.categoria(attiva) == null) attiva = model.dock.firstOrNull()?.id ?: TUTTE
    val categoria = model.categoria(attiva)
    val lista = if (categoria == null) model.app.map { it.chiave } else model.gruppi[attiva].orEmpty()
    val titolo = categoria?.let { aspetto(it).nome } ?: stringResource(R.string.cat_tutte)

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (scelte.isEmpty()) titolo else stringResource(R.string.cat_scelte, scelte.size),
                    color = testoSu(model), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(if (scelte.isEmpty()) R.string.cat_aiuto else R.string.cat_aiuto_scelte),
                    color = testoSu(model).copy(alpha = 0.75f), fontSize = 12.sp,
                )
            }
            if (categoria != null && scelte.isEmpty()) Icon(
                EuriseIcons.pencil(), stringResource(R.string.modifica_categoria),
                Modifier.size(44.dp).clickable { onModifica(attiva) }.padding(12.dp), tint = testoSu(model).copy(alpha = 0.8f),
            )
            if (scelte.isNotEmpty()) Icon(
                EuriseIcons.close(), stringResource(R.string.annulla),
                Modifier.size(44.dp).clickable { scelte = emptyList() }.padding(12.dp), tint = testoSu(model),
            )
        }
        // Le app partono dal basso, vicino al pollice e alla dock (regola «Ergonomia»): la prima è in basso, le altre salgono.
        // In «Tutte», l'indice delle lettere sta dal lato della mano, con la A in basso come le app.
        val griglia = rememberLazyGridState()
        val scope = rememberCoroutineScope()
        LaunchedEffect(attiva, giri) { runCatching { griglia.scrollToItem(0) } }
        Row(Modifier.weight(1f).padding(horizontal = 10.dp).background(col.foglio, RoundedCornerShape(28.dp))) {
        val indice: @Composable () -> Unit = {
            if (categoria == null && scelte.isEmpty()) {
                val app = model.app
                IndiceLettere(app.map { it.iniziale }.distinct(), col, dalBasso = true, Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) { l ->
                    val i = app.indexOfFirst { it.iniziale == l }
                    if (i >= 0) scope.launch { griglia.scrollToItem(i) }
                }
            }
        }
        if (model.mancino) indice()
        LazyVerticalGrid(
            GridCells.Fixed(4), Modifier.weight(1f).fillMaxHeight(), state = griglia,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom),
            reverseLayout = true,
        ) {
            if (lista.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.gruppo_vuoto), color = col.sotto, fontSize = 13.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            }
            items(lista, key = { it }) { chiave ->
                val scelta = chiave in scelte
                TesseraApp(
                    model, chiave, col, col.inchiostro, scelta,
                    onTocco = {
                        if (scelte.isNotEmpty()) scelte = if (scelta) scelte - chiave else scelte + chiave else model.apri(chiave)
                    },
                    onLungo = { scelte = if (scelta) scelte - chiave else scelte + chiave },
                )
            }
        }
        if (!model.mancino) indice()
        }
        if (scelte.isNotEmpty()) {
            // Le azioni sulle app scelte, sotto il pollice.
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth().height(52.dp).background(col.barra, RoundedCornerShape(20.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val azione: @Composable (Int, () -> Unit) -> Unit = { testo, fai ->
                    Box(Modifier.fillMaxWidth().height(52.dp).clickable { fai() }, contentAlignment = Alignment.Center) {
                        Text(stringResource(testo), color = col.accento, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.weight(1f)) {
                    Box { azione(R.string.cat_sposta) { destinazione = false } }
                    DropdownMenu(destinazione == false, onDismissRequest = { destinazione = null }) {
                        model.dock.filter { it.id != attiva }.forEach { c ->
                            DropdownMenuItem(text = { Text(aspetto(c).nome) }, leadingIcon = { Icon(iconaDock(c.icona), null, tint = tintaDock(c.icona)) }, onClick = {
                                model.spostaApp(scelte, categoria?.id, c.id, copia = false); scelte = emptyList(); destinazione = null
                            })
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    Box { azione(R.string.cat_copia) { destinazione = true } }
                    DropdownMenu(destinazione == true, onDismissRequest = { destinazione = null }) {
                        model.dock.filter { it.id != attiva }.forEach { c ->
                            DropdownMenuItem(text = { Text(aspetto(c).nome) }, leadingIcon = { Icon(iconaDock(c.icona), null, tint = tintaDock(c.icona)) }, onClick = {
                                model.spostaApp(scelte, categoria?.id, c.id, copia = true); scelte = emptyList(); destinazione = null
                            })
                        }
                    }
                }
                if (categoria != null) Box(Modifier.weight(1f)) {
                    Box { azione(R.string.togli) { model.togliApp(scelte, attiva); scelte = emptyList() } }
                }
            }
        }
        // La dock delle categorie.
        val voci = model.dock.map { it.id } + TUTTE
        val tutteNome = stringResource(R.string.cat_tutte)
        LazyRow(
            Modifier.restaNellaFila().padding(start = 8.dp, end = 8.dp, bottom = 12.dp, top = 4.dp).fillMaxWidth().height(AltezzaDock).background(col.barra, RoundedCornerShape(26.dp)),
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            reverseLayout = model.mancino,
        ) {
            items(voci, key = { it }) { id ->
                val c = model.categoria(id)
                val sel = id == attiva
                Column(
                    Modifier.width(52.dp).combinedClickable(
                        onClick = { attiva = id; scelte = emptyList() },
                        onLongClick = { if (c != null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onModifica(id) } },
                    ).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Solo icone, senza scritte (28 novembre): il nome lo legge TalkBack; «Tutte» è «A–Z».
                    Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                        if (c != null) {
                            val a = aspetto(c)
                            Icon(a.icona, a.nome, Modifier.size(if (sel) 26.dp else 24.dp), tint = if (col.notte) Color(0xFFE9EBEE) else a.tinta)
                        } else {
                            Text("A–Z", color = if (sel) col.inchiostro else col.sotto, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { contentDescription = tutteNome })
                        }
                    }
                    Box(Modifier.padding(top = 3.dp).width(16.dp).height(2.5.dp).background(if (sel) col.accento else Color.Transparent, RoundedCornerShape(2.dp)))
                }
            }
            if (model.dock.size < Gruppi.MAX) item(key = "+") {
                Box(Modifier.size(52.dp).clickable { nuova = true }, contentAlignment = Alignment.Center) {
                    Icon(EuriseIcons.plus(), stringResource(R.string.cat_nuova), Modifier.size(22.dp), tint = col.accento)
                }
            }
        }
    }
    if (nuova) TestoDialog(stringResource(R.string.cat_nuova), "", onOk = { n ->
        if (n.isNotBlank()) model.nuovaCategoria(n, "star")?.let { id -> attiva = id; onModifica(id) }
    }, onChiudi = { nuova = false })
}

/** Il testo sopra il cielo, dove non c'è il foglio. */
@Composable
private fun testoSu(model: PostazioneModel): Color = testoSulCielo(model.cielo)

/**
 * L'indice delle lettere accanto a un elenco alfabetico, dal lato della mano: si tocca una lettera o si scorre con il
 * dito lungo la fila, e l'elenco salta alla prima app con quell'iniziale. Con [dalBasso] la A sta in basso, come le app
 * che partono dal basso.
 */
@Composable
internal fun IndiceLettere(lettere: List<Char>, col: Colori, dalBasso: Boolean, modifier: Modifier = Modifier, onLettera: (Char) -> Unit) {
    if (lettere.size < 2) return
    val ordine = if (dalBasso) lettere.reversed() else lettere
    var altezza by remember { mutableIntStateOf(1) }
    var attuale by remember { mutableStateOf<Char?>(null) }
    val haptic = LocalHapticFeedback.current
    val salta by androidx.compose.runtime.rememberUpdatedState(onLettera)
    val scegli: (Float) -> Unit = { y ->
        val l = ordine[(y / altezza * ordine.size).toInt().coerceIn(0, ordine.lastIndex)]
        if (l != attuale) { attuale = l; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); salta(l) }
    }
    Column(
        modifier.fillMaxHeight().width(30.dp).background(col.riquadro, RoundedCornerShape(15.dp))
            .onSizeChanged { altezza = it.height.coerceAtLeast(1) }
            .pointerInput(ordine) {
                awaitEachGesture {
                    val giu = awaitFirstDown()
                    giu.consume(); scegli(giu.position.y)
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.id == giu.id } ?: break
                        if (!c.pressed) break
                        c.consume(); scegli(c.position.y)
                    }
                    attuale = null
                }
            }
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ordine.forEach { l ->
            Text(
                l.toString(), color = if (l == attuale) col.inchiostro else col.accento,
                fontSize = if (l == attuale) 14.sp else 11.5.sp, fontWeight = if (l == attuale) FontWeight.Bold else FontWeight.SemiBold,
            )
        }
    }
}


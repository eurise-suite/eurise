package eu.eurise.postazione.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.postazione.PostazioneModel
import eu.eurise.postazione.R
import eu.eurise.postazione.data.Avviso
import eu.eurise.postazione.data.Bolla
import eu.eurise.postazione.data.Liberi
import eu.eurise.postazione.data.Linguetta
import eu.eurise.postazione.data.Misura
import eu.eurise.postazione.data.Pagine
import eu.eurise.postazione.data.Scheda
import eu.eurise.postazione.data.Tipo
import eu.eurise.postazione.data.TipoBolla
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.math.roundToInt

/** Quante righe mostra una bolla: le misure alte e larghe di più. */
private fun quante(m: Misura) = when (m) { Misura.Piccola -> 1; Misura.Media -> 3; Misura.Larga -> 3; Misura.Grande -> 6 }

@androidx.annotation.StringRes
internal fun nomeBolla(t: TipoBolla): Int = when (t) {
    TipoBolla.Prossimo -> R.string.b_prossimo; TipoBolla.Cercato -> R.string.b_cercato; TipoBolla.Rispondere -> R.string.b_rispondere
    TipoBolla.DaFare -> R.string.b_dafare; TipoBolla.Farmaci -> R.string.b_farmaci; TipoBolla.Meteo -> R.string.b_meteo
    TipoBolla.Ascolto -> R.string.b_ascolto; TipoBolla.Notizie -> R.string.b_notizie; TipoBolla.Oroscopo -> R.string.b_oroscopo
    TipoBolla.Preferiti -> R.string.b_preferiti; TipoBolla.Auto -> R.string.b_auto
}

@androidx.annotation.StringRes
private fun nomeMisura(m: Misura): Int = when (m) {
    Misura.Piccola -> R.string.m_piccola; Misura.Media -> R.string.m_media; Misura.Larga -> R.string.m_larga; Misura.Grande -> R.string.m_grande
}

/**
 * Il foglio satinato della linguetta: le sue bolle, due per riga quelle da mezza riga. Tenendo premuto il titolo di una
 * bolla si entra in modifica: × la toglie, la misura gira tra Piccola, Media, Larga e Grande, le frecce la spostano, in
 * fondo «Aggiungi una bolla».
 */
@Composable
fun FoglioBolle(
    model: PostazioneModel, l: Linguetta, col: Colori, giri: Int, modifica: Boolean, onModifica: (Boolean) -> Unit, modifier: Modifier,
) {
    val stato = remember(l.id) { LazyListState() }
    LaunchedEffect(giri, l.id) { stato.scrollToItem(0) }
    val prima = model.linguette.firstOrNull()?.id == l.id
    val forma = RoundedCornerShape(topStart = if (prima) 0.dp else 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    val bolle = model.bolleDi(l)
    var aggiungi by remember { mutableStateOf(false) }
    LazyColumn(
        modifier.fillMaxWidth().padding(horizontal = 10.dp).background(col.foglio, forma), state = stato,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!model.giornata.collegata) item { Box(Modifier.background(col.bolla, RoundedCornerShape(18.dp)).padding(12.dp)) { Nota(stringResource(R.string.senza_eurise), col) } }
        if (model.pausaFino > 0L) item(key = "pausa") { Box(Modifier.background(col.bolla, RoundedCornerShape(18.dp)).padding(6.dp)) { RigaPausa(model, col) } }
        if (l.tipo == Tipo.Lavoro && model.giornata.collegata && model.fineGiornata && !modifica) {
            item(key = "fine") { Box(Modifier.background(col.bolla, RoundedCornerShape(18.dp)).padding(6.dp)) { FineGiornata(model, col) } }
        }
        Pagine.righe(bolle).forEach { riga ->
            item(key = riga.joinToString { it.tipo.name }) {
                if (riga.size == 2) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        riga.forEach { b -> BollaVista(model, l, b, col, modifica, onModifica, Modifier.weight(1f).fillMaxHeight()) }
                    }
                } else if (riga.first().misura.colonne == 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BollaVista(model, l, riga.first(), col, modifica, onModifica, Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
                    }
                } else BollaVista(model, l, riga.first(), col, modifica, onModifica, Modifier.fillMaxWidth())
            }
        }
        if (bolle.isEmpty() && !modifica) item { Riga(stringResource(R.string.b_vuoto), stringResource(R.string.b_aggiungi), col) { onModifica(true); aggiungi = true } }
        if (modifica) item(key = "aggiungi") {
            Box(
                Modifier.fillMaxWidth().height(48.dp).background(col.bolla, RoundedCornerShape(16.dp)).clickable { aggiungi = true },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.plus(), null, Modifier.size(18.dp), tint = col.accento)
                    Text(" " + stringResource(R.string.b_aggiungi), color = col.accento, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (aggiungi) AggiungiBolla(model, l, onChiudi = { aggiungi = false })
}

/** In alto, al posto della testata, mentre modifichi le bolle. */
@Composable
fun BarraModifica(model: PostazioneModel, col: Colori, onFatto: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp).background(col.barra, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.b_modifica_di, nomeLinguetta(model.attiva)), color = col.inchiostro, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Box(
            Modifier.height(36.dp).background(col.inchiostro, RoundedCornerShape(12.dp)).clickable { onFatto() }.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.imp_fatto), color = col.fondo, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BollaVista(
    model: PostazioneModel, l: Linguetta, b: Bolla, col: Colori, modifica: Boolean, onModifica: (Boolean) -> Unit, modifier: Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().then(if (modifica) Modifier.fillMaxHeight() else Modifier).background(col.bolla, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val titolo = Modifier.combinedClickable(onClick = {}, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onModifica(true) })
            ContenutoBolla(model, l, b, col, titolo)
        }
        if (modifica) {
            Box(Modifier.matchParentSize().background(col.accento.copy(alpha = 0.06f), RoundedCornerShape(18.dp)))
            Box(
                Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp).size(26.dp).background(Color(0xFF1C1D20), CircleShape).clickable { model.togliBolla(l, b.tipo) },
                contentAlignment = Alignment.Center,
            ) { Icon(EuriseIcons.close(), stringResource(R.string.b_togli, stringResource(nomeBolla(b.tipo))), Modifier.size(14.dp), tint = Color.White) }
            Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.chevronUp(), stringResource(R.string.b_prima), Modifier.size(30.dp).background(Color.White, CircleShape).clickable { model.spostaBolla(l, b.tipo, -1) }.padding(6.dp), tint = col.accento)
                Spacer(Modifier.width(6.dp))
                Icon(EuriseIcons.chevronDown(), stringResource(R.string.b_dopo), Modifier.size(30.dp).background(Color.White, CircleShape).clickable { model.spostaBolla(l, b.tipo, 1) }.padding(6.dp), tint = col.accento)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(nomeMisura(b.misura)), color = col.accento, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color.White, RoundedCornerShape(14.dp)).clickable { model.misuraBolla(l, b.tipo) }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TitoloBolla(testo: String, col: Colori, tocco: Modifier, link: String? = null, onLink: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(testo, color = col.inchiostro, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = tocco.weight(1f))
        if (link != null && onLink != null) {
            Text(link, color = col.accento, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onLink() }.padding(start = 8.dp, top = 2.dp, bottom = 2.dp))
        }
    }
}

@Composable
private fun ContenutoBolla(model: PostazioneModel, l: Linguetta, b: Bolla, col: Colori, tocco: Modifier) {
    val g = model.giornata
    val n = quante(b.misura)
    val largo = b.misura.colonne == 2
    val tel = model.telefono
    when (b.tipo) {
        TipoBolla.Prossimo -> {
            TitoloBolla(stringResource(R.string.b_prossimo), col, tocco, stringResource(R.string.calendario)) { model.apriSezione("calendar") }
            when {
                !g.collegata -> Text(stringResource(R.string.senza_eurise), color = col.sotto, fontSize = 13.sp)
                !g.calendario -> Riga(stringResource(R.string.calendario_senza_permesso), stringResource(R.string.apri), col) { model.apriSezione("calendar") }
                g.impegni.isEmpty() -> Text(stringResource(R.string.nessun_impegno), color = col.sotto, fontSize = 13.5.sp)
                largo -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(g.impegni.size, key = { g.impegni[it].id.toString() + ":" + g.impegni[it].inizio }) { i ->
                        val e = g.impegni[i]
                        SchedaImpegno(e, i == 0, l.tipo == Tipo.Viaggio, model.adesso, col,
                            onNaviga = { tel.avvia(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(e.luogo)))) },
                            onRitardo = { testo, titolo -> tel.avvia(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, testo), titolo)) },
                            onApri = { model.apriImpegno(e) },
                            onElimina = if (e.eliminabile) ({ model.eliminaImpegno(e) }) else null)
                    }
                }
                else -> g.impegni.take(n).forEach { e ->
                    Column(Modifier.fillMaxWidth().clickable { model.apriImpegno(e) }) {
                        Text(e.titolo, color = col.inchiostro, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (e.tuttoIlGiorno) stringResource(R.string.tutto_il_giorno) else orario(e.inizio) + "–" + orario(e.fine), color = col.sotto, fontSize = 12.5.sp, maxLines = 1)
                    }
                }
            }
        }
        TipoBolla.Cercato, TipoBolla.Rispondere -> {
            val chiamate = b.tipo == TipoBolla.Cercato
            val lista = model.avvisi.filter { it.chiamata == chiamate }
            TitoloBolla(
                stringResource(if (chiamate) R.string.b_cercato_n else R.string.b_rispondere_n, lista.size), col, tocco,
                stringResource(if (chiamate) R.string.b_telefono else R.string.b_comunica),
            ) { if (chiamate) model.funzione(eu.eurise.postazione.data.Azioni.TELEFONO) else model.apriComunica() }
            when {
                !model.ascolto -> AccendiAscolto(col) { model.aggiorna() }
                lista.isEmpty() -> Text(stringResource(if (chiamate) R.string.b_nessuna_chiamata else R.string.niente_da_rispondere), color = col.sotto, fontSize = 13.sp)
                else -> lista.take(n).forEach { a -> RigaSwipe(model, a, col) }
            }
        }
        TipoBolla.DaFare, TipoBolla.Farmaci -> {
            val farmaci = b.tipo == TipoBolla.Farmaci
            val visibili = g.cose.filter { it.id !in model.toccate && it.farmaco == farmaci }
            val totale = g.aperte + g.fatte
            TitoloBolla(
                if (farmaci) stringResource(R.string.b_farmaci_n, visibili.size)
                else if (g.fatte > 0) stringResource(R.string.da_fare_fatte, g.fatte, totale) else stringResource(R.string.da_fare_oggi, g.aperte),
                col, tocco, stringResource(if (farmaci) R.string.b_farmaci else R.string.attivita),
            ) { model.apriSezione(if (farmaci) "medicines" else "tasks") }
            if (!g.collegata) { Text(stringResource(R.string.senza_eurise), color = col.sotto, fontSize = 13.sp); return }
            if (!farmaci && l.tipo == Tipo.Lavoro && largo) {
                val ora = model.adesso.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val sera = model.adesso.toLocalDate().atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val spazio = if (g.aperte > 0) Liberi.primo(ora, sera, g.impegni.filter { !it.tuttoIlGiorno }.map { it.inizio to it.fine }) else null
                if (spazio != null) {
                    val q = Liberi.quante(spazio, g.cose.count { !it.farmaco })
                    SpazioLibero(spazio, spazio.inizio == ora, q, col, onMetti = if (q > 0) ({ model.mettiNelloSpazio(spazio) }) else null)
                }
            }
            if (visibili.isEmpty()) Text(stringResource(if (farmaci) R.string.b_nessun_farmaco else R.string.niente_da_fare), color = col.sotto, fontSize = 13.sp)
            visibili.take(n).forEach { c -> RigaCosa(model, c, col) }
        }
        TipoBolla.Meteo -> {
            val m = g.meteo
            TitoloBolla(stringResource(R.string.b_meteo), col, tocco)
            Column(Modifier.fillMaxWidth().clickable { model.apriSezione("weather") }) {
                if (m == null) Text(stringResource(R.string.apri_meteo), color = col.sotto, fontSize = 13.sp)
                else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconaCielo(m.cielo), null, Modifier.size(22.dp), tint = col.inchiostro)
                        Text(" ${m.gradi}°", color = col.inchiostro, fontSize = 22.sp, fontWeight = FontWeight.Light)
                    }
                    Text(rigaMeteo(m, model.adesso), color = col.sotto, fontSize = 12.5.sp, maxLines = if (b.misura.alta) 3 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        TipoBolla.Ascolto -> {
            TitoloBolla(stringResource(R.string.b_ascolto), col, tocco)
            when {
                !model.ascolto -> AccendiAscolto(col) { model.aggiorna() }
                model.brano == null -> Text(stringResource(R.string.b_niente_musica), color = col.sotto, fontSize = 13.sp)
                largo -> SchedaMusica(model, col)
                else -> {
                    val br = model.brano!!
                    Text(br.titolo, color = col.inchiostro, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(br.artista, color = col.sotto, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(
                        Modifier.size(36.dp).background(col.lente, CircleShape).clickable { model.musica(eu.eurise.postazione.data.Musica.Comando.SuonaPausa) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (br.suona) EuriseIcons.pause() else EuriseIcons.play(), stringResource(R.string.suona), Modifier.size(18.dp), tint = Color.White) }
                }
            }
        }
        TipoBolla.Notizie -> {
            TitoloBolla(stringResource(R.string.notizie), col, tocco)
            val notizie = model.notizie
            when {
                !g.collegata -> Text(stringResource(R.string.senza_eurise), color = col.sotto, fontSize = 13.sp)
                notizie == null -> Text(stringResource(R.string.b_in_arrivo), color = col.sotto, fontSize = 13.sp)
                notizie.isEmpty() -> Riga(stringResource(R.string.notizie_vuote), stringResource(R.string.centro_privacy), col) { model.apriSezione("privacy") }
                else -> notizie.take(n).forEach { RigaNotizia(model, it, col) }
            }
        }
        TipoBolla.Oroscopo -> {
            TitoloBolla(stringResource(R.string.b_oroscopo), col, tocco)
            if (model.oroscopo == null) Text(stringResource(if (g.collegata) R.string.b_in_arrivo else R.string.senza_eurise), color = col.sotto, fontSize = 13.sp)
            else BollaOroscopo(model, col)
        }
        TipoBolla.Preferiti -> { TitoloBolla(stringResource(R.string.b_preferiti), col, tocco); BollaPreferiti(model, col) }
        TipoBolla.Auto -> { TitoloBolla(stringResource(R.string.b_auto), col, tocco); BollaAuto(model, col) }
    }
}

/**
 * Una chiamata persa o un messaggio, con lo swipe come nella Scrivania: verso destra Richiama (o Apri), verso sinistra
 * Vista, Più tardi, Da fare.
 */
@Composable
private fun RigaSwipe(model: PostazioneModel, a: Avviso, col: Colori) {
    val destra = if (a.chiamata) Azione(stringResource(R.string.sw_richiama), EuriseIcons.phone(), col.accento) { model.richiama(a) }
    else Azione(stringResource(R.string.sw_apri), EuriseIcons.chat(), col.accento) { model.apriAvviso(a) }
    val sinistra = listOf(
        Azione(stringResource(R.string.sw_vista), EuriseIcons.check(), Color(0xFF62615C)) { model.vista(a) },
        Azione(stringResource(R.string.sw_dopo), EuriseIcons.clock(), Color(0xFFB45309)) { model.piuTardi(a) },
        Azione(stringResource(R.string.sw_da_fare), EuriseIcons.task(), col.accento) { model.daFare(a) },
    )
    SwipeRiga(destra, sinistra, col) { RigaAvviso(model, a, col) }
}

class Azione(val testo: String, val icona: ImageVector, val colore: Color, val esegui: () -> Unit)

/**
 * Una riga da tirare: verso destra oltre metà del tasto fa l'azione principale; verso sinistra si apre e mostra le altre,
 * che si toccano. Un tocco sulla riga aperta la richiude.
 */
@Composable
fun SwipeRiga(destra: Azione?, sinistra: List<Azione>, col: Colori, contenuto: @Composable () -> Unit) {
    val densita = LocalDensity.current
    val tasto = with(densita) { 62.dp.toPx() }
    val soglia = with(densita) { 96.dp.toPx() }
    val aperta = -tasto * sinistra.size
    val x = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxWidth().background(Color.Transparent, RoundedCornerShape(12.dp))) {
        if (x.value > 0f && destra != null) {
            Row(Modifier.matchParentSize().background(destra.colore, RoundedCornerShape(12.dp)).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(destra.icona, null, Modifier.size(18.dp), tint = Color.White)
                Text(" " + destra.testo, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (x.value < 0f) {
            Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                sinistra.forEach { az ->
                    Column(
                        Modifier.width(62.dp).fillMaxHeight().background(az.colore).clickable { scope.launch { x.animateTo(0f) }; az.esegui() },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(az.icona, null, Modifier.size(17.dp), tint = Color.White)
                        Text(az.testo, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        Box(
            Modifier.offset { IntOffset(x.value.roundToInt(), 0) }.fillMaxWidth()
                .background(if (x.value != 0f) col.fondo else Color.Transparent, RoundedCornerShape(12.dp))
                .pointerInput(destra, sinistra.size) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    x.value > soglia && destra != null -> { x.animateTo(0f); destra.esegui() }
                                    x.value < aperta / 2 -> x.animateTo(aperta)
                                    else -> x.animateTo(0f)
                                }
                            }
                        },
                    ) { change, d ->
                        change.consume()
                        scope.launch { x.snapTo((x.value + d).coerceIn(aperta, if (destra != null) soglia * 1.4f else 0f)) }
                    }
                },
        ) { contenuto() }
    }
}

/** Le bolle che si possono aggiungere alla linguetta (quelle che non ha). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AggiungiBolla(model: PostazioneModel, l: Linguetta, onChiudi: () -> Unit) {
    val presenti = model.bolleDi(l).map { it.tipo }.toSet()
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.b_aggiungi)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.b_aggiungi_aiuto), fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TipoBolla.entries.forEach { t ->
                        val c = t in presenti
                        Text(
                            (if (c) "✓ " else "+ ") + stringResource(nomeBolla(t)), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                            color = if (c) Color(0xFF0A5BD0) else Color(0xFF1C1D20),
                            modifier = Modifier.background(if (c) Color(0x1F0A5BD0) else Color(0xFFEFEDE8), RoundedCornerShape(17.dp))
                                .clickable(enabled = !c) { model.aggiungiBolla(l, t) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onChiudi) { Text(stringResource(R.string.imp_fatto)) } },
    )
}

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
    chiediAggiungi: Int = 0,
) {
    val stato = remember(l.id) { LazyListState() }
    LaunchedEffect(giri, l.id) { stato.scrollToItem(0) }
    val prima = model.linguette.firstOrNull()?.id == l.id
    val forma = RoundedCornerShape(topStart = if (prima) 0.dp else 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    val bolle = model.bolleDi(l)
    var aggiungi by remember { mutableStateOf(false) }
    // Il «+» accanto alle linguette chiede «Aggiungi una bolla»: si apre la scelta, già in modifica.
    LaunchedEffect(chiediAggiungi) { if (chiediAggiungi > 0) aggiungi = true }
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
        // Le bolle che non hanno niente da dire diventano una riga sola di icone (tavola del 27 novembre, sera).
        val vuote = if (modifica) emptyList() else bolle.filter { vuota(model, it) }
        val piene = bolle - vuote.toSet()
        Pagine.righe(piene).forEach { riga ->
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
        if (vuote.isNotEmpty()) item(key = "vuote") { StrisciaVuote(model, vuote, col) }
        if (bolle.isEmpty() && !modifica) item { Riga(stringResource(R.string.b_vuoto), stringResource(R.string.b_aggiungi), col) { onModifica(true); aggiungi = true } }
        // Sempre in fondo alla scheda, piccolo e dal lato della mano: «+ Bolla» porta subito alla scelta.
        if (!modifica && bolle.isNotEmpty()) item(key = "piu") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (model.mancino) Arrangement.Start else Arrangement.End) {
                Row(
                    Modifier.background(col.bolla, RoundedCornerShape(16.dp)).clickable { onModifica(true); aggiungi = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(EuriseIcons.plus(), null, Modifier.size(15.dp), tint = col.accento)
                    Text(" " + stringResource(R.string.b_aggiungi), color = col.accento, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
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
                largo -> LazyRow(Modifier.restaNellaFila(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                // L'app si riconosce dal suo simbolo, senza scriverne il nome; il tocco apre la bolla (come nella Scrivania).
                else -> lista.take(maxOf(n, 3)).forEach { a -> RigaContatto(model, a, col) }
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

/** Vero se la bolla non ha niente da dire adesso: allora è solo un'icona nella riga in fondo. */
private fun vuota(model: PostazioneModel, b: Bolla): Boolean {
    val g = model.giornata
    return when (b.tipo) {
        TipoBolla.Prossimo -> g.collegata && g.calendario && g.impegni.isEmpty()
        TipoBolla.Cercato -> model.avvisi.none { it.chiamata } && (model.ascolto || model.chiamateDallaSuite)
        TipoBolla.Rispondere -> model.avvisi.none { !it.chiamata } && model.ascolto
        TipoBolla.DaFare -> g.collegata && g.aperte == 0 && g.cose.none { !it.farmaco && it.id !in model.toccate }
        TipoBolla.Farmaci -> g.collegata && g.cose.none { it.farmaco && it.id !in model.toccate }
        TipoBolla.Meteo -> g.meteo == null
        TipoBolla.Ascolto -> model.ascolto && model.brano == null
        TipoBolla.Notizie -> g.collegata && model.notizie.isNullOrEmpty()
        TipoBolla.Oroscopo -> model.oroscopo == null
        TipoBolla.Preferiti, TipoBolla.Auto -> false
    }
}

private fun iconaBolla(t: TipoBolla): ImageVector = when (t) {
    TipoBolla.Prossimo -> EuriseIcons.calendar(); TipoBolla.Cercato -> EuriseIcons.phone(); TipoBolla.Rispondere -> EuriseIcons.chat()
    TipoBolla.DaFare -> EuriseIcons.task(); TipoBolla.Farmaci -> EuriseIcons.clock(); TipoBolla.Meteo -> EuriseIcons.sun()
    TipoBolla.Ascolto -> EuriseIcons.music(); TipoBolla.Notizie -> EuriseIcons.news(); TipoBolla.Oroscopo -> EuriseIcons.star()
    TipoBolla.Preferiti -> EuriseIcons.star(); TipoBolla.Auto -> EuriseIcons.car()
}

/** Dove porta l'icona di una bolla vuota: l'app o la sezione che le fa da casa. */
private fun apriCasa(model: PostazioneModel, t: TipoBolla) {
    when (t) {
        TipoBolla.Prossimo -> model.apriSezione("calendar")
        TipoBolla.Cercato -> model.funzione(eu.eurise.postazione.data.Azioni.TELEFONO)
        TipoBolla.Rispondere -> model.apriComunica()
        TipoBolla.DaFare -> model.apriSezione("tasks")
        TipoBolla.Farmaci -> model.apriSezione("medicines")
        TipoBolla.Meteo -> model.apriSezione("weather")
        TipoBolla.Ascolto -> model.apriGruppo(eu.eurise.postazione.data.Gruppo.Musica.name)
        TipoBolla.Notizie -> model.apriSezione("today")
        TipoBolla.Oroscopo -> model.apriSezione("today")
        else -> Unit
    }
}

/** Le bolle vuote in una riga: icona monocromatica e nome corto; il tocco apre la loro app. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StrisciaVuote(model: PostazioneModel, vuote: List<Bolla>, col: Colori) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().background(col.bolla, RoundedCornerShape(18.dp)).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        vuote.forEach { b ->
            Column(
                Modifier.widthIn(min = 64.dp).clickable { apriCasa(model, b.tipo) }.padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(iconaBolla(b.tipo), null, Modifier.size(20.dp), tint = col.sotto)
                Text(
                    stringResource(nomeCorto(b.tipo)), color = col.sotto, fontSize = 10.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@androidx.annotation.StringRes
private fun nomeCorto(t: TipoBolla): Int = when (t) {
    TipoBolla.Prossimo -> R.string.bc_impegni; TipoBolla.Cercato -> R.string.bc_chiamate; TipoBolla.Rispondere -> R.string.bc_messaggi
    else -> nomeBolla(t)
}

/** L'icona piccola e monocromatica di una riga: chiamata, email o messaggio (niente icone d'app colorate). */
private fun iconaAvviso(a: Avviso): ImageVector = when {
    a.chiamata -> EuriseIcons.phone()
    a.rifMail.isNotEmpty() || a.pacchetto.contains("mail") || a.pacchetto.contains(".gm") || a.pacchetto.contains("email") -> EuriseIcons.mail()
    else -> EuriseIcons.chat()
}

/**
 * Il simbolo dell'app che ha scritto: quello piccolo della sua notifica, già monocromatico, nel colore dato. Per le righe
 * che vengono dalla suite (Telefono, Email) e se il simbolo non si legge, il disegno della suite (telefono, posta, chat).
 */
@Composable
private fun SimboloAvviso(a: Avviso, misura: androidx.compose.ui.unit.Dp, tinta: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val px = with(LocalDensity.current) { misura.roundToPx() }.coerceAtLeast(1)
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, a.chiave, px) {
        val ic = a.simbolo ?: return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val d = ic.loadDrawable(context) ?: return@runCatching null
                val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, px, px); d.draw(android.graphics.Canvas(bmp))
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    val i = img
    if (i != null) androidx.compose.foundation.Image(i, null, Modifier.size(misura), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tinta))
    else Icon(iconaAvviso(a), null, Modifier.size(misura), tint = tinta)
}

/**
 * Una chiamata da richiamare o un messaggio in una riga, come nella Scrivania: il simbolo dell'app, chi, quando, una riga
 * di testo; per le chiamate il telefono per richiamare subito. Il tocco apre la bolla con la risposta e i comandi.
 */
@Composable
private fun RigaContatto(model: PostazioneModel, a: Avviso, col: Colori) {
    var bolla by remember(a.chiave) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { bolla = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SimboloAvviso(a, 17.dp, col.sotto)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.titolo + if (a.quante > 1) " · ${a.quante}" else "", color = col.inchiostro, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Text("  " + quandoBreve(a.quando, model), color = col.sotto, fontSize = 11.5.sp, maxLines = 1)
            }
            val sotto = a.testo.ifBlank { if (a.chiamata) stringResource(R.string.av_persa) else "" }
            if (sotto.isNotBlank()) Text(sotto, color = col.sotto, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (a.chiamata) Icon(
            EuriseIcons.phone(), stringResource(R.string.sw_richiama),
            Modifier.size(40.dp).clickable { model.richiama(a) }.padding(11.dp), tint = col.accento,
        )
    }
    if (bolla) FoglioAvviso(model, a, col) { bolla = false }
}

private fun quandoBreve(ms: Long, model: PostazioneModel): String {
    val d = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return if (d.toLocalDate() == model.adesso.toLocalDate()) orario(ms)
    else d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d", java.util.Locale.ITALIAN))
}

/**
 * La bolla di un messaggio, di un'email o di una chiamata (come quella della Scrivania): i messaggi arrivati come in una
 * chat (tenendone premuto uno: reazione, cita, copia, da fare), la risposta scritta qui (con «Rispondi» della notifica, un
 * SMS da Messaggi o l'email da finire in Email) e le azioni: Richiama, SMS, Apri, Da fare, Più tardi, Archivia, Segna come
 * letto, Togli. Nulla parte da solo.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun FoglioAvviso(model: PostazioneModel, a: Avviso, col: Colori, onChiudi: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var testo by remember { mutableStateOf("") }
    var cita by remember { mutableStateOf<String?>(null) }
    var scelto by remember { mutableStateOf<Int?>(null) }
    var esito by remember { mutableStateOf<Boolean?>(null) }
    val fuoco = remember { androidx.compose.ui.focus.FocusRequester() }
    val manda: (String) -> Unit = { corpo ->
        val pieno = cita?.let { "«" + it.take(80) + "»\n" + corpo } ?: corpo
        model.rispondi(a, pieno.trim()) { ok -> esito = ok; if (ok) { model.vista(a); onChiudi() } }
    }
    val storia = a.storia.ifEmpty { listOfNotNull(a.testo.takeIf { it.isNotBlank() }?.let { eu.eurise.postazione.data.Messaggio(null, it) }) }
    val scorri = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(scorri.maxValue) { scorri.scrollTo(scorri.maxValue) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onChiudi, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onChiudi() }) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding().padding(10.dp)
                    .heightIn(max = 640.dp)
                    .background(col.pieno, RoundedCornerShape(26.dp))
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SimboloAvviso(a, 20.dp, col.accento)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(a.titolo, color = col.inchiostro, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                if (a.chiamata) stringResource(R.string.av_persa) else null,
                                quandoBreve(a.quando, model),
                                if (a.quante > 1) stringResource(R.string.av_quante, a.quante) else null,
                            ).joinToString(" · "),
                            color = col.sotto, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Tasto(EuriseIcons.external(), stringResource(R.string.sw_apri), col) { model.apriAvviso(a); onChiudi() }
                }
                // I messaggi, come in una chat; tenendone premuto uno, le sue azioni.
                if (storia.isNotEmpty()) Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(scorri),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    a.foto?.let { b -> FotoBolla(b.asImageBitmap()) }
                    storia.forEachIndexed { i, m ->
                        val chi = m.chi
                        val corpo = m.testo
                        val forma = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
                        Column(
                            Modifier.padding(end = 36.dp).background(if (scelto == i) col.prossimo else col.riquadro, forma)
                                .combinedClickable(onClick = { scelto = null }, onLongClick = { scelto = i })
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            if (!chi.isNullOrBlank()) Text(chi, color = col.accento, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (m.foto) FotoDaUri(m.uri!!, col) { model.apriAvviso(a); onChiudi() }
                            if (m.audio) Audio(m.uri!!, col) { model.apriAvviso(a); onChiudi() }
                            if (corpo.isNotBlank()) Text(corpo, color = col.inchiostro, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        if (scelto == i) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (a.rispondi != null) listOf("👍", "❤️", "😂", "🙏").forEach { e ->
                                Text(e, fontSize = 18.sp, modifier = Modifier.background(col.riquadro, CircleShape).clickable { manda(e) }.padding(horizontal = 9.dp, vertical = 5.dp))
                            }
                            if (model.puoRispondere(a)) Tasto(EuriseIcons.reply(), stringResource(R.string.av_cita), col) {
                                cita = corpo; scelto = null; runCatching { fuoco.requestFocus() }
                            }
                            Tasto(EuriseIcons.copy(), stringResource(R.string.av_copia), col) {
                                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("", corpo)); scelto = null
                            }
                            Tasto(EuriseIcons.task(), stringResource(R.string.sw_da_fare), col) { model.daFare(a, corpo); scelto = null }
                        }
                    }
                }
                if (model.puoRispondere(a)) {
                    cita?.let { q ->
                        Row(Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(12.dp)).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("«$q»", color = col.sotto, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(EuriseIcons.close(), null, Modifier.size(34.dp).clickable { cita = null }.padding(10.dp), tint = col.sotto)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.text.BasicTextField(
                            testo, { testo = it.take(1000) },
                            Modifier.weight(1f).focusRequester(fuoco).background(col.riquadro, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = col.inchiostro, fontSize = 15.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(col.accento), maxLines = 5,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { if (testo.isNotBlank()) manda(testo) }),
                            decorationBox = { campo ->
                                Box {
                                    if (testo.isEmpty()) Text(
                                        stringResource(if (a.chiamata) R.string.av_sms else if (a.rifMail.isNotEmpty()) R.string.av_mail else R.string.av_rispondi),
                                        color = col.sotto, fontSize = 15.sp,
                                    )
                                    campo()
                                }
                            },
                        )
                        Box(
                            Modifier.padding(start = 8.dp).size(46.dp).background(col.lente, CircleShape)
                                .clickable(enabled = testo.isNotBlank()) { manda(testo) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(EuriseIcons.send(), stringResource(R.string.ai_invia), Modifier.size(19.dp), tint = Color.White) }
                    }
                    if (testo.isEmpty() && !a.chiamata) {
                        val presto = stringResource(R.string.domattina_testo)
                        Gettone(EuriseIcons.clock(), presto, col) { testo = presto }
                    }
                    if (esito == false) Text(stringResource(R.string.av_non_partita), color = Color(0xFFB3261E), fontSize = 12.5.sp)
                }
                // I comandi: icone monocromatiche in una fila, dal lato della mano; tenendone premuto uno se ne legge il nome.
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, if (model.mancino) Alignment.Start else Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val comandi = buildList<@Composable () -> Unit> {
                        if (!a.chiamata && (a.letto != null || a.rifMail.isNotEmpty())) {
                            add { Tasto(EuriseIcons.check(), stringResource(R.string.av_letto), col) { model.vista(a); onChiudi() } }
                        } else {
                            add { Tasto(EuriseIcons.close(), stringResource(if (a.chiamata) R.string.av_non_richiamare else R.string.av_togli), col) { model.vista(a); onChiudi() } }
                        }
                        if (model.eliminabile(a)) add { TastoElimina(stringResource(R.string.av_elimina), col) { model.elimina(a); onChiudi() } }
                        if (a.archivia != null) add { Tasto(EuriseIcons.folder(), stringResource(R.string.av_archivia), col) { model.archivia(a); onChiudi() } }
                        add { Tasto(EuriseIcons.clock(), stringResource(R.string.sw_dopo), col) { model.piuTardi(a); onChiudi() } }
                        add { Tasto(EuriseIcons.task(), stringResource(R.string.sw_da_fare), col) { model.daFare(a); onChiudi() } }
                        if (a.chiamata) {
                            if (a.numero.isNotEmpty()) add { Tasto(EuriseIcons.message(), stringResource(R.string.av_sms_breve), col) { runCatching { fuoco.requestFocus() } } }
                            add { Tasto(EuriseIcons.phone(), stringResource(R.string.sw_richiama), col, forte = true) { model.richiama(a); onChiudi() } }
                        } else if (a.rifMail.isNotEmpty()) {
                            add { Tasto(EuriseIcons.mail(), stringResource(R.string.av_leggi), col, forte = true) { model.apriAvviso(a); onChiudi() } }
                        }
                    }
                    // Il più usato (Richiama, Leggi) sta sotto il pollice: per ultimo a destra, per primo a sinistra.
                    (if (model.mancino) comandi.reversed() else comandi).forEach { it() }
                }
            }
        }
    }
}

@Composable
private fun FotoBolla(img: androidx.compose.ui.graphics.ImageBitmap) {
    androidx.compose.foundation.Image(
        img, null, Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp)),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
    )
}

/**
 * La foto allegata a un messaggio: si legge (ridotta) solo se Android ce la lascia leggere; se no, «Apri nella chat».
 */
@Composable
private fun FotoDaUri(uri: android.net.Uri, col: Colori, onApri: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var img by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var fallita by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        img = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(src) { d, info, _ ->
                    val lato = maxOf(info.size.width, info.size.height)
                    if (lato > 1080) d.setTargetSampleSize((lato + 1079) / 1080)
                }.asImageBitmap()
            }.getOrNull()
        }
        fallita = img == null
    }
    img?.let { FotoBolla(it) }
    if (fallita) Text(
        stringResource(R.string.av_foto_in_app), color = col.accento, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable { onApri() }.padding(vertical = 6.dp),
    )
}

/** Un audio (vocale) allegato: si ascolta qui se Android lo lascia leggere, se no si apre la chat. */
@Composable
private fun Audio(uri: android.net.Uri, col: Colori, onApri: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var suona by remember(uri) { mutableStateOf(false) }
    val lettore = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    androidx.compose.runtime.DisposableEffect(uri) { onDispose { lettore.value?.release(); lettore.value = null } }
    Row(
        Modifier.background(col.prossimo, RoundedCornerShape(16.dp)).clickable {
            if (suona) { lettore.value?.release(); lettore.value = null; suona = false; return@clickable }
            val p = runCatching {
                android.media.MediaPlayer().apply {
                    setDataSource(context, uri); prepare(); start()
                    setOnCompletionListener { suona = false }
                }
            }.getOrNull()
            if (p == null) onApri() else { lettore.value = p; suona = true }
        }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (suona) EuriseIcons.pause() else EuriseIcons.play(), null, Modifier.size(16.dp), tint = col.accento)
        Text(" " + stringResource(if (suona) R.string.av_ferma else R.string.av_ascolta), color = col.accento, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Un'azione della bolla: icona piccola monocromatica e nome. */
@Composable
private fun Gettone(icona: ImageVector, testo: String, col: Colori, forte: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.background(if (forte) col.prossimo else col.riquadro, RoundedCornerShape(16.dp)).clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icona, null, Modifier.size(15.dp), tint = if (forte) col.accento else col.sotto)
        Text(" " + testo, color = if (forte) col.accento else col.inchiostro, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * Un comando della bolla: solo l'icona, in un tondo; il nome lo leggono TalkBack e, tenendolo premuto, un avviso breve.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tasto(icona: ImageVector, nome: String, col: Colori, forte: Boolean = false, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        Modifier.size(44.dp).background(if (forte) col.prossimo else col.riquadro, CircleShape)
            .combinedClickable(
                onClickLabel = nome, role = androidx.compose.ui.semantics.Role.Button,
                onLongClick = { android.widget.Toast.makeText(context, nome, android.widget.Toast.LENGTH_SHORT).show() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { Icon(icona, nome, Modifier.size(19.dp), tint = if (forte) col.accento else col.sotto) }
}

/**
 * «Elimina»: il cestino. Il primo tocco lo fa diventare rosso e dice «Tocca di nuovo per eliminare»; il secondo elimina.
 * Così un tocco per sbaglio non cancella niente (un SMS eliminato non torna).
 */
@Composable
private fun TastoElimina(nome: String, col: Colori, onElimina: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pronto by remember { mutableStateOf(false) }
    val rosso = Color(0xFFB3261E)
    val ancora = stringResource(R.string.av_elimina_ancora)
    LaunchedEffect(pronto) { if (pronto) { kotlinx.coroutines.delay(3_000); pronto = false } }
    Box(
        Modifier.size(44.dp).background(if (pronto) rosso else col.riquadro, CircleShape)
            .clickable(onClickLabel = nome, role = androidx.compose.ui.semantics.Role.Button) {
                if (pronto) onElimina() else { pronto = true; android.widget.Toast.makeText(context, ancora, android.widget.Toast.LENGTH_SHORT).show() }
            },
        contentAlignment = Alignment.Center,
    ) { Icon(EuriseIcons.trash(), nome, Modifier.size(19.dp), tint = if (pronto) Color.White else col.sotto) }
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

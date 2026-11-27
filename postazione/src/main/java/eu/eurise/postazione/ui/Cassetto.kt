package eu.eurise.postazione.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.postazione.PostazioneModel
import eu.eurise.postazione.R
import eu.eurise.postazione.data.App
import eu.eurise.postazione.data.Ascolto
import eu.eurise.postazione.data.Posto
import eu.eurise.postazione.data.Preferiti
import eu.eurise.postazione.data.Uso
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import eu.eurise.postazione.data.Ai
import eu.eurise.postazione.data.Scheda
import eu.eurise.postazione.data.Trovata
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.PrivacyConsentSheet
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.suite.PostazioneContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import java.text.Normalizer

/**
 * Le app. Si apre a «mezzo tiro»: quelle che servono adesso, le più usate e le recenti, con il campo di ricerca in basso
 * sotto il pollice; un altro tocco (o due lettere) e ci sono tutte dalla A alla Z, con l'indice dal lato della mano.
 * Con [scegliPer] serve a scegliere l'app, o il sito, da aggiungere a un gruppo.
 */
@Composable
fun Cassetto(
    model: PostazioneModel, col: Colori, cercaSubito: Boolean, scegliPer: String?,
    onScelta: (String) -> Unit, onSito: () -> Unit, onImpostazioni: () -> Unit, onChiudi: () -> Unit,
) {
    var tutteAperte by remember { mutableStateOf(scegliPer != null) }
    BackHandler { if (tutteAperte && scegliPer == null) tutteAperte = false else onChiudi() }
    var testo by remember { mutableStateOf("") }
    val fuoco = remember { FocusRequester() }
    val griglia = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val chiave = semplice(testo)
    val tutte = model.app
    val trovateApp = if (chiave.isEmpty()) tutte else tutte.filter { semplice(it.nome).contains(chiave) }
        .sortedBy { if (semplice(it.nome).startsWith(chiave)) 0 else 1 }
    // La lente cerca ovunque: con il testo le app sono le prime otto, poi contatti, impegni, cose, note, documenti, web e AI.
    val ovunque = scegliPer == null && chiave.isNotEmpty()
    val mostrate = if (ovunque) trovateApp.take(8) else trovateApp
    LaunchedEffect(cercaSubito) { if (cercaSubito) runCatching { fuoco.requestFocus() } }
    LaunchedEffect(testo, scegliPer) {
        if (scegliPer == null) { delay(220); model.cerca(testo) }
    }
    val titoloScelta = scegliPer?.let { id ->
        if (id.startsWith("scheda:")) stringResource(R.string.aggiungi_a, nomeScheda(id.removePrefix("scheda:")))
        else stringResource(R.string.aggiungi_a, model.categoria(id)?.let { aspetto(it).nome }.orEmpty())
    }
    val rapido = chiave.isEmpty() && !tutteAperte
    val perChiave = remember(tutte) { tutte.associateBy { it.chiave } }

    Box(Modifier.fillMaxSize().background(col.foglio)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    titoloScelta
                        ?: if (rapido) stringResource(R.string.rapido_titolo) else stringResource(R.string.cassetto_titolo, tutte.size),
                    color = col.inchiostro, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                Icon(EuriseIcons.gear(), stringResource(R.string.impostazioni), Modifier.size(44.dp).clickable { onImpostazioni() }.padding(11.dp), tint = col.sotto)
                Icon(EuriseIcons.close(), stringResource(R.string.chiudi), Modifier.size(44.dp).clickable { onChiudi() }.padding(11.dp), tint = col.sotto)
            }
            Row(Modifier.weight(1f)) {
                val indice: @Composable () -> Unit = {
                    if (chiave.isEmpty() && !rapido) {
                        val lettere = tutte.map { it.iniziale }.distinct()
                        Column(
                            Modifier.padding(horizontal = 4.dp, vertical = 8.dp).width(26.dp).background(col.riquadro, RoundedCornerShape(13.dp)).padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            lettere.forEach { l ->
                                Text(
                                    l.toString(), color = col.accento, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        val i = tutte.indexOfFirst { it.iniziale == l }
                                        if (i >= 0) scope.launch { griglia.scrollToItem(i) }
                                    }.padding(vertical = 1.5.dp, horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
                if (model.mancino) indice()
                LazyVerticalGrid(
                    GridCells.Fixed(4), Modifier.weight(1f), state = griglia,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (scegliPer != null && chiave.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(52.dp).background(col.riquadro, RoundedCornerShape(16.dp)).clickable { onSito() }.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(EuriseIcons.globe(), null, Modifier.size(20.dp), tint = col.accento)
                                Text(stringResource(R.string.sito_aggiungi), color = col.accento, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                    if (rapido) {
                        sezione("adesso", R.string.rapido_adesso, model.perAdesso(4).mapNotNull { perChiave[it] }, model, col, onScelta)
                        sezione("usate", R.string.rapido_usate, model.piuUsate(8).mapNotNull { perChiave[it] }, model, col, onScelta)
                        sezione("recenti", R.string.rapido_recenti, model.recenti.mapNotNull { perChiave[it] }.take(4), model, col, onScelta)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().height(48.dp).background(col.riquadro, RoundedCornerShape(16.dp)).clickable { tutteAperte = true },
                                contentAlignment = Alignment.Center,
                            ) { Text(stringResource(R.string.rapido_tutte), color = col.accento, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        }
                    } else {
                        items(mostrate, key = { it.chiave }) { a -> Tessera(a, model, col, onScelta) }
                        if (ovunque) ovunque(model, testo, col, onImpostazioni)
                    }
                }
                if (!model.mancino) indice()
            }
            if (chiave.isNotEmpty() && mostrate.isEmpty() && !ovunque) {
                Text(stringResource(R.string.cassetto_nessuna, testo), color = col.sotto, fontSize = 13.5.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            // Il campo di ricerca in basso, sotto il pollice; con la tastiera aperta resta appena sopra.
            Row(
                Modifier.padding(10.dp).fillMaxWidth().height(58.dp).background(col.riquadro, RoundedCornerShape(20.dp)).padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.search(), null, Modifier.size(20.dp), tint = col.sotto)
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (testo.isEmpty()) Text(stringResource(R.string.cassetto_cerca), color = col.sotto, fontSize = 15.sp)
                    BasicTextField(
                        testo, { testo = it }, Modifier.fillMaxWidth().focusRequester(fuoco), singleLine = true,
                        textStyle = TextStyle(color = col.inchiostro, fontSize = 15.sp), cursorBrush = SolidColor(col.accento),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val prima = mostrate.firstOrNull()
                            if (prima != null) onScelta(prima.chiave) else if (ovunque) { model.cercaSulWeb(testo); onChiudi() }
                        }),
                    )
                }
                Box(
                    Modifier.size(42.dp).background(col.lente, RoundedCornerShape(14.dp))
                        .clickable(enabled = ovunque) { model.cercaSulWeb(testo); onChiudi() },
                    contentAlignment = Alignment.Center,
                ) {
                    LenteEurise(Color.White, Modifier.size(24.dp))
                }
            }
        }
    }
}

private fun LazyGridScope.sezione(id: String, titolo: Int, lista: List<App>, model: PostazioneModel, col: Colori, onScelta: (String) -> Unit) {
    if (lista.isEmpty()) return
    item(key = "titolo:$id", span = { GridItemSpan(maxLineSpan) }) {
        Text(stringResource(titolo), color = col.sotto, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
    }
    items(lista, key = { "$id:" + it.chiave }) { a -> Tessera(a, model, col, onScelta) }
}

@Composable
private fun Tessera(a: App, model: PostazioneModel, col: Colori, onScelta: (String) -> Unit) {
    Column(Modifier.clickable { onScelta(a.chiave) }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconaApp(model.telefono, a.chiave, 50.dp, col.notte)
        Text(
            a.nome, color = col.inchiostro, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun nomeScheda(nome: String): String = stringResource(
    when (runCatching { Scheda.valueOf(nome) }.getOrDefault(Scheda.Lavoro)) {
        Scheda.Lavoro -> R.string.scheda_lavoro; Scheda.Personale -> R.string.scheda_personale; Scheda.Viaggio -> R.string.scheda_viaggio
    },
)

private val GiornoOra = DateTimeFormatter.ofPattern("EEE d MMM · H:mm", java.util.Locale.ITALIAN)
private val SoloGiorno = DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ITALIAN)

/** I risultati della lente oltre alle app: tutto letto sul telefono; web e AI partono solo al tocco. */
private fun LazyGridScope.ovunque(model: PostazioneModel, testo: String, col: Colori, onImpostazioni: () -> Unit) {
    val q = testo.trim()
    val pieno: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    if (model.persone.isNotEmpty()) {
        item(key = "t:persone", span = pieno) { Titolo(R.string.cerca_contatti, col) }
        model.persone.forEach { p ->
            item(key = "p:" + p.id, span = pieno) {
                RigaRisultato(EuriseIcons.person(), p.nome, p.numero, col, onClick = { model.apriPersona(p) }) {
                    if (p.numero.isNotBlank()) {
                        Icon(
                            EuriseIcons.phone(), stringResource(R.string.chiama), Modifier.size(40.dp).clickable {
                                model.telefono.avvia(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", p.numero, null)))
                            }.padding(10.dp), tint = col.accento,
                        )
                        Icon(
                            EuriseIcons.message(), stringResource(R.string.scrivi), Modifier.size(40.dp).clickable {
                                model.telefono.avvia(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.fromParts("smsto", p.numero, null)))
                            }.padding(10.dp), tint = col.accento,
                        )
                    }
                }
            }
        }
    } else if (!model.contatti) {
        item(key = "t:contatti-spenti", span = pieno) {
            Text(
                stringResource(R.string.cerca_contatti_spenti), color = col.sotto, fontSize = 12.5.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).clickable { onImpostazioni() },
            )
        }
    }
    val gruppi = listOf(
        PostazioneContract.KIND_EVENT to R.string.cerca_impegni, PostazioneContract.KIND_TASK to R.string.cerca_cose,
        PostazioneContract.KIND_NOTE to R.string.cerca_note, PostazioneContract.KIND_FILE to R.string.cerca_documenti,
    )
    gruppi.forEach { (tipo, titolo) ->
        val lista = model.trovate.filter { it.tipo == tipo }
        if (lista.isNotEmpty()) {
            item(key = "t:$tipo", span = pieno) { Titolo(titolo, col) }
            lista.forEach { t -> item(key = "$tipo:${t.id}", span = pieno) { RigaTrovata(model, t, col) } }
        }
    }
    item(key = "web", span = pieno) {
        RigaRisultato(EuriseIcons.globe(), stringResource(R.string.cerca_web, q), stringResource(R.string.cerca_web_d), col, onClick = { model.cercaSulWeb(q) })
    }
    item(key = "ai", span = pieno) { RigaAi(model, q, col) }
}

@Composable
private fun Titolo(testo: Int, col: Colori) {
    Text(stringResource(testo), color = col.sotto, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
}

@Composable
private fun RigaTrovata(model: PostazioneModel, t: Trovata, col: Colori) {
    val icona = when (t.tipo) {
        PostazioneContract.KIND_EVENT -> EuriseIcons.calendar()
        PostazioneContract.KIND_TASK -> EuriseIcons.task()
        PostazioneContract.KIND_NOTE -> EuriseIcons.note()
        else -> EuriseIcons.document()
    }
    val quando = t.quando.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val sotto = listOfNotNull(
        quando?.let { if (t.tipo == PostazioneContract.KIND_EVENT || (it.hour != 0 || it.minute != 0) && t.tipo == PostazioneContract.KIND_TASK) it.format(GiornoOra) else it.format(SoloGiorno) },
        t.dettaglio.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    RigaRisultato(icona, t.titolo, sotto, col, onClick = { model.apriNellApp(t.tipo, t.id, if (t.tipo == PostazioneContract.KIND_EVENT) t.quando else 0L) })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RigaRisultato(
    icona: androidx.compose.ui.graphics.vector.ImageVector, titolo: String, sotto: String, col: Colori,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    fine: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    Row(
        Modifier.padding(horizontal = 6.dp).fillMaxWidth().background(col.riquadro, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icona, null, Modifier.size(20.dp), tint = col.accento)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(titolo, color = col.inchiostro, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sotto.isNotBlank()) Text(sotto, color = col.sotto, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        fine()
    }
}

/**
 * «Chiedi a …»: il tocco passa la domanda all'AI preferita, tenere premuto le mostra tutte con le tacchette e la stellina
 * per scegliere la preferita. Scegliere un servizio chiuso come preferito mostra prima l'avviso.
 */
@Composable
private fun RigaAi(model: PostazioneModel, q: String, col: Colori) {
    var elenco by remember { mutableStateOf(false) }
    var avviso by remember { mutableStateOf<Ai?>(null) }
    val haptic = LocalHapticFeedback.current
    RigaRisultato(
        EuriseIcons.spark(), stringResource(R.string.chiedi_a, model.ai.nome),
        stringResource(if (model.ai.sulTelefono) R.string.chiedi_telefono else R.string.chiedi_fuori), col,
        onClick = { model.chiedi(q) },
        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); elenco = true },
    ) {
        Icon(EuriseIcons.chevronDown(), stringResource(R.string.chiedi_altre), Modifier.size(40.dp).clickable { elenco = true }.padding(11.dp), tint = col.sotto)
    }
    if (elenco) {
        AlertDialog(
            onDismissRequest = { elenco = false },
            title = { Text(stringResource(R.string.chiedi_titolo)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.chiedi_aiuto), fontSize = 12.5.sp, color = col.sotto)
                    Ai.entries.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { elenco = false; model.chiedi(q, a) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(a.nome, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                                PrivacyMeter(a.tacche, runCatching { Jurisdiction.valueOf(a.dove) }.getOrNull(), Modifier.padding(top = 3.dp))
                            }
                            val preferita = a == model.ai
                            Icon(
                                EuriseIcons.star(), stringResource(if (preferita) R.string.preferita else R.string.rendi_preferita),
                                Modifier.size(40.dp).clickable(enabled = !preferita) { if (a.chiuso) avviso = a else model.preferisciAi(a) }.padding(9.dp),
                                tint = if (preferita) col.accento else col.sotto.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton({ elenco = false }) { Text(stringResource(R.string.chiudi)) } },
        )
    }
    avviso?.let { a ->
        PrivacyConsentSheet(
            title = stringResource(R.string.ai_avviso_titolo, a.nome),
            read = stringResource(R.string.ai_avviso_letto),
            out = stringResource(R.string.ai_avviso_esce, a.nome),
            who = stringResource(R.string.ai_avviso_chi, a.nome),
            back = stringResource(R.string.ai_avviso_indietro),
            score = a.tacche,
            where = Jurisdiction.US,
            onDismiss = { avviso = null },
            onAccept = { model.preferisciAi(a); avviso = null },
        )
    }
}

/** Un sito da mettere in un gruppo: indirizzo (solo web) e nome. */
@Composable
fun NuovoSito(onAggiungi: (String, String) -> Boolean, onChiudi: () -> Unit) {
    var indirizzo by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var errore by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.sito_aggiungi)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(indirizzo, { indirizzo = it; errore = false }, label = { Text(stringResource(R.string.sito_indirizzo)) }, singleLine = true, isError = errore)
                OutlinedTextField(nome, { nome = it }, label = { Text(stringResource(R.string.sito_nome)) }, singleLine = true)
                if (errore) Text(stringResource(R.string.sito_errore), color = Color(0xFFB3261E), fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton({ if (!onAggiungi(indirizzo, nome)) errore = true }) { Text(stringResource(R.string.aggiungi)) } },
        dismissButton = { TextButton(onChiudi) { Text(stringResource(R.string.annulla)) } },
    )
}

/** Minuscole e senza accenti: «citta» trova «Città». */
private fun semplice(s: String): String =
    Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/**
 * Le impostazioni: nome, mano, accento e i tre interruttori della privacy (gli stessi del centro privacy dell'app
 * Eurise). Accendere mostra prima la spiegazione; spegnere vale subito, senza conferme.
 */
@Composable
fun Impostazioni(model: PostazioneModel, onChiudi: () -> Unit) {
    var nome by remember { mutableStateOf(model.nome) }
    var mancino by remember { mutableStateOf(model.mancino) }
    val context = LocalContext.current
    var chiedi by remember { mutableStateOf<Uso?>(null) }
    val contatti = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.aggiorna() }
    val posizione = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.aggiorna() }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.impostazioni)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nome, { nome = it }, label = { Text(stringResource(R.string.imp_nome)) }, singleLine = true)
                Text(stringResource(R.string.imp_mano), fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(!mancino, { mancino = false }); Text(stringResource(R.string.imp_destra))
                    Spacer(Modifier.width(16.dp))
                    RadioButton(mancino, { mancino = true }); Text(stringResource(R.string.imp_sinistra))
                }
                Text(stringResource(R.string.imp_accento), fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Accento.entries.forEach { a ->
                        RadioButton(model.accento == a, { model.impostaAccento(a) })
                        Text(stringResource(when (a) { Accento.Ceruleo -> R.string.acc_ceruleo; Accento.Petrolio -> R.string.acc_petrolio; Accento.Ottone -> R.string.acc_ottone }), fontSize = 13.sp)
                    }
                }
                Text(stringResource(R.string.imp_privacy), fontWeight = FontWeight.SemiBold)
                listOf(
                    Triple(Uso.Notifiche, model.ascolto, R.string.cp_notifiche to R.string.cp_notifiche_d),
                    Triple(Uso.Contatti, model.contatti, R.string.cp_contatti to R.string.cp_contatti_d),
                    Triple(Uso.Posizione, model.posizione, R.string.cp_posizione to R.string.cp_posizione_d),
                ).forEach { (uso, acceso, testi) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(stringResource(testi.first), fontSize = 14.sp)
                            Text(stringResource(testi.second), fontSize = 11.5.sp, lineHeight = 15.sp, color = col(model).sotto)
                        }
                        Switch(acceso, onCheckedChange = { on ->
                            if (on) chiedi = uso else { uso.spegni(context); model.aggiorna() }
                        })
                    }
                }
                TextButton({
                    runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text(stringResource(R.string.imp_predefinita)) }
            }
        },
        confirmButton = {
            TextButton({ model.impostaNome(nome); model.impostaMancino(mancino); onChiudi() }) { Text(stringResource(R.string.imp_fatto)) }
        },
    )
    chiedi?.let { uso ->
        val (titolo, letto, esce, chi, indietro) = when (uso) {
            Uso.Notifiche -> listOf(R.string.ascolto_titolo, R.string.ascolto_letto, R.string.ascolto_esce, R.string.ascolto_chi, R.string.ascolto_indietro)
            Uso.Contatti -> listOf(R.string.preferiti_titolo, R.string.preferiti_letto, R.string.preferiti_esce, R.string.preferiti_chi, R.string.preferiti_indietro)
            Uso.Posizione -> listOf(R.string.auto_consenso, R.string.auto_letto, R.string.auto_esce, R.string.auto_chi, R.string.auto_indietro)
        }
        PrivacyConsentSheet(
            title = stringResource(titolo), read = stringResource(letto), out = stringResource(esce),
            who = stringResource(chi), back = stringResource(indietro), score = 10, where = Jurisdiction.Phone,
            onDismiss = { chiedi = null },
            onAccept = {
                chiedi = null
                uso.accendi(context)
                when (uso) {
                    Uso.Notifiche -> if (!Ascolto.permesso(context)) Ascolto.impostazioni(context)
                    Uso.Contatti -> if (!Preferiti.concesso(context)) contatti.launch(Manifest.permission.READ_CONTACTS)
                    Uso.Posizione -> if (!Posto.concesso(context)) posizione.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                model.aggiorna()
            },
        )
    }
}

@Composable
private fun col(model: PostazioneModel) = colori(androidx.compose.foundation.isSystemInDarkTheme(), model.accento)

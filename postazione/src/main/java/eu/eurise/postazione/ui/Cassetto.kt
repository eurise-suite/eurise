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
import eu.eurise.postazione.data.Gruppo
import kotlinx.coroutines.launch
import java.text.Normalizer

/**
 * Le app. Si apre a «mezzo tiro»: quelle che servono adesso, le più usate e le recenti, con il campo di ricerca in basso
 * sotto il pollice; un altro tocco (o due lettere) e ci sono tutte dalla A alla Z, con l'indice dal lato della mano.
 * Con [scegliPer] serve a scegliere l'app, o il sito, da aggiungere a un gruppo.
 */
@Composable
fun Cassetto(
    model: PostazioneModel, col: Colori, cercaSubito: Boolean, scegliPer: Gruppo?,
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
    val mostrate = if (chiave.isEmpty()) tutte else tutte.filter { semplice(it.nome).contains(chiave) }
        .sortedBy { if (semplice(it.nome).startsWith(chiave)) 0 else 1 }
    LaunchedEffect(cercaSubito) { if (cercaSubito) runCatching { fuoco.requestFocus() } }
    val rapido = chiave.isEmpty() && !tutteAperte
    val perChiave = remember(tutte) { tutte.associateBy { it.chiave } }

    Box(Modifier.fillMaxSize().background(col.foglio)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    scegliPer?.let { stringResource(R.string.aggiungi_a, stringResource(aspetto(it).nome)) }
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
                    }
                }
                if (!model.mancino) indice()
            }
            if (chiave.isNotEmpty() && mostrate.isEmpty()) {
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
                        keyboardActions = KeyboardActions(onGo = { mostrate.firstOrNull()?.let { onScelta(it.chiave) } }),
                    )
                }
                Box(Modifier.size(42.dp).background(col.lente, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
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

@Composable
fun Impostazioni(model: PostazioneModel, onChiudi: () -> Unit) {
    var nome by remember { mutableStateOf(model.nome) }
    var mancino by remember { mutableStateOf(model.mancino) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.impostazioni)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Text(stringResource(R.string.imp_ascolto), fontWeight = FontWeight.SemiBold)
                TextButton({ Ascolto.impostazioni(context) }) {
                    Text(stringResource(if (model.ascolto) R.string.imp_acceso else R.string.imp_spento))
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
}

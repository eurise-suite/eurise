package eu.eurise.postazione.ui

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.postazione.BuildConfig
import eu.eurise.postazione.PostazioneModel
import eu.eurise.postazione.R
import eu.eurise.postazione.data.Cosa
import eu.eurise.postazione.data.Contesto
import eu.eurise.postazione.data.Siti
import eu.eurise.core.suite.PostazioneContract
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import eu.eurise.postazione.data.Impegno
import eu.eurise.postazione.data.Liberi
import eu.eurise.postazione.data.Meteo
import eu.eurise.postazione.data.Saluto
import eu.eurise.postazione.data.Scheda
import eu.eurise.postazione.data.Schede
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ora = DateTimeFormatter.ofPattern("H:mm")
private val Giorno = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

@Composable
fun PostazioneScreen(model: PostazioneModel, home: Flow<Unit>) {
    val col = colori(isSystemInDarkTheme(), model.accento)
    var ventaglio by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<String?>(null) }
    var cassetto by remember { mutableStateOf(false) }
    var cercaSubito by remember { mutableStateOf(false) }
    var sitoPer by remember { mutableStateOf<String?>(null) }
    // Dove va l'app scelta nel cassetto: l'id di una categoria della dock, o «scheda:<nome>» per le app di una scheda.
    var scegliPer by remember { mutableStateOf<String?>(null) }
    var impostazioni by remember { mutableStateOf(false) }

    // Il tasto Home chiude tutto e riporta la scheda in cima.
    var giri by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { home.collect { ventaglio = null; cassetto = false; scegliPer = null; editor = null; giri++ } }
    LaunchedEffect(model.impostazioniRichieste) { if (model.impostazioniRichieste > 0) impostazioni = true }
    LaunchedEffect(Unit) { while (true) { delay(15_000); model.tic() } }
    BackHandler(enabled = ventaglio != null) { ventaglio = null }

    val adesso = model.adesso
    val cielo = Schede.cielo(adesso)
    val suCielo = testoSulCielo(cielo)
    val scheda = model.scheda
    val apriCassetto: (Boolean) -> Unit = { cerca -> cercaSubito = cerca; scegliPer = null; cassetto = true }

    BoxWithConstraints(Modifier.fillMaxSize().background(col.fondo)) {
        CieloDiFondo(cielo)
        // Telefono: barra in basso. Tablet e schermi larghi: binario dal lato della mano e foglio a due colonne.
        val largo = maxWidth >= 840.dp
        val onGruppo: (String) -> Unit = { g ->
            val prima = model.gruppi[g]?.firstOrNull()
            if (prima == null || !model.apri(prima)) ventaglio = g
        }
        if (largo) {
            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                val binario: @Composable () -> Unit = {
                    Binario(model, col, onGruppo, { ventaglio = it }, { apriCassetto(true) }, { model.alfred() }, { apriCassetto(false) })
                }
                if (model.mancino) binario()
                Column(Modifier.weight(1f).padding(bottom = 14.dp)) {
                    Testata(model, suCielo, Modifier)
                    Linguette(model, scheda, col, suCielo)
                    Foglio(model, scheda, col, true, giri, Modifier.weight(1f)) { scegliPer = "scheda:" + scheda.name; cercaSubito = false; cassetto = true }
                }
                if (!model.mancino) binario()
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 116.dp)) {
                Testata(model, suCielo, Modifier.pointerInput(Unit) { scorriSu { apriCassetto(false) } })
                Linguette(model, scheda, col, suCielo)
                Foglio(model, scheda, col, false, giri, Modifier.weight(1f)) { scegliPer = "scheda:" + scheda.name; cercaSubito = false; cassetto = true }
            }
            // Il richiamo di «Tutte le app» sopra la barra: un tocco o uno scorrimento verso l'alto.
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 98.dp)
                    .clickable { apriCassetto(false) }.pointerInput(Unit) { scorriSu { apriCassetto(false) } },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(EuriseIcons.chevronUp(), null, Modifier.size(16.dp), tint = suCielo.copy(alpha = 0.8f))
                Text(stringResource(R.string.tutte_le_app), color = suCielo.copy(alpha = 0.85f), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Barra(
                model, col,
                onGruppo = onGruppo,
                onVentaglio = { ventaglio = it },
                onLente = { apriCassetto(true) },
                onAlfred = { model.alfred() },
                modifier = Modifier.align(Alignment.BottomCenter).pointerInput(Unit) { scorriSu { apriCassetto(false) } },
            )
        }
        ventaglio?.let { g ->
            Ventaglio(model, g, col, largo, onChiudi = { ventaglio = null }, onAggiungi = {
                ventaglio = null; scegliPer = g; cercaSubito = false; cassetto = true
            }, onModifica = { ventaglio = null; editor = g })
        }
        editor?.let { g -> EditorCategoria(model, g, onChiudi = { editor = null; ventaglio = g }) }
        if (cassetto) {
            Cassetto(
                model, col, cercaSubito, scegliPer,
                onScelta = { chiave ->
                    val g = scegliPer
                    when {
                        g == null -> if (model.apri(chiave)) cassetto = false
                        g.startsWith("scheda:") -> {
                            runCatching { Scheda.valueOf(g.removePrefix("scheda:")) }.getOrNull()?.let { model.fissaInScheda(it, chiave) }
                            cassetto = false; scegliPer = null
                        }
                        else -> { model.aggiungi(g, chiave); cassetto = false; scegliPer = null; ventaglio = g }
                    }
                },
                onSito = { sitoPer = scegliPer },
                onImpostazioni = { impostazioni = true },
                onChiudi = { cassetto = false; scegliPer = null },
            )
        }
        sitoPer?.let { g ->
            NuovoSito(
                onAggiungi = { indirizzo, nome ->
                    model.aggiungiSito(g, indirizzo, nome).also { ok ->
                        if (ok) { sitoPer = null; cassetto = false; scegliPer = null; if (!g.startsWith("scheda:")) ventaglio = g }
                    }
                },
                onChiudi = { sitoPer = null },
            )
        }
        if (impostazioni) Impostazioni(model, onChiudi = { impostazioni = false })
    }
}

/** Uno scorrimento verso l'alto di almeno 60 dp. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.scorriSu(azione: () -> Unit) {
    var tot = 0f
    detectVerticalDragGestures(
        onDragStart = { tot = 0f },
        onDragEnd = { if (tot < -60.dp.toPx()) azione() },
    ) { _, d -> tot += d }
}

@Composable
private fun Testata(model: PostazioneModel, colore: Color, modifier: Modifier) {
    val adesso = model.adesso
    val saluto = stringResource(
        when (Schede.saluto(adesso)) { Saluto.Giorno -> R.string.saluto_giorno; Saluto.Pomeriggio -> R.string.saluto_pomeriggio; Saluto.Sera -> R.string.saluto_sera },
    )
    val testo = if (model.nome.isBlank()) saluto else stringResource(R.string.saluto_nome, saluto, model.nome)
    val tel = model.telefono
    val hub = BuildConfig.HUB_PACKAGE
    Column(modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 12.dp)) {
        Row(
            Modifier.clickable { tel.apriSuiteO(hub, "eu.eurise.hub.AgendaApp", null) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(testo, color = colore, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Icon(EuriseIcons.chevronRight(), stringResource(R.string.apri_agenda), Modifier.size(14.dp), tint = colore.copy(alpha = 0.6f))
        }
        Text(
            adesso.format(Giorno).replaceFirstChar { it.titlecase(Locale.ITALIAN) },
            color = colore.copy(alpha = 0.9f), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable {
                tel.apriSuiteO(hub, "eu.eurise.hub.CalendarApp",
                    Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()))
            },
        )
        Text(
            adesso.format(Ora), color = colore, fontSize = 56.sp, fontWeight = FontWeight.Light,
            modifier = Modifier.clickable { tel.avvia(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
        )
        // Il meteo apre la sezione Meteo dell'app Eurise; senza l'app Eurise, l'app che il telefono ha.
        Row(
            Modifier.clickable { if (!model.apriSezione("weather")) tel.avviaPacchetto(hub)?.let { tel.avvia(it) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val meteo = model.giornata.meteo
            Icon(iconaCielo(meteo?.cielo), null, Modifier.size(16.dp), tint = colore)
            Text(" " + (meteo?.let { rigaMeteo(it, adesso) } ?: stringResource(R.string.apri_meteo)), color = colore, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Icon(EuriseIcons.chevronRight(), null, Modifier.size(12.dp), tint = colore.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun Linguette(model: PostazioneModel, attiva: Scheda, col: Colori, suCielo: Color) {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.Bottom) {
        Scheda.entries.forEachIndexed { i, s ->
            val nome = stringResource(when (s) { Scheda.Lavoro -> R.string.scheda_lavoro; Scheda.Personale -> R.string.scheda_personale; Scheda.Viaggio -> R.string.scheda_viaggio })
            if (s == attiva) {
                val prima = i == 0
                Box(
                    Modifier.padding(start = if (prima) 0.dp else 10.dp, end = 10.dp).height(40.dp)
                        .drawBehind {
                            // I «piedi» concavi della linguetta: il foglio e la linguetta sono un pezzo solo (schedario).
                            val r = 10.dp.toPx(); val w = size.width; val h = size.height
                            drawPath(Path().apply { moveTo(w, h - r); quadraticTo(w, h, w + r, h); lineTo(w, h); close() }, col.foglio)
                            if (!prima) drawPath(Path().apply { moveTo(0f, h - r); quadraticTo(0f, h, -r, h); lineTo(0f, h); close() }, col.foglio)
                        }
                        .background(col.foglio, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(nome, color = col.inchiostro, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            } else {
                Box(
                    Modifier.height(36.dp).clickable { model.scegli(s) }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(nome, color = suCielo.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.padding(end = 14.dp, bottom = 11.dp).clickable { model.scegli(Schede.automatica(LocalDateTime.now())) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(suCielo.copy(alpha = 0.9f), CircleShape))
            Text(
                " " + stringResource(if (model.automatica) R.string.scheda_auto else R.string.scheda_mano),
                color = suCielo.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Il foglio della scheda. In alto il «Prossimo» e la fine della giornata, poi le app di questa scheda, poi le persone
 * (da rispondere, musica, notizie, oroscopo, preferiti), poi le cose da fare. Sul telefono una sotto l'altra; sugli
 * schermi larghi due colonne.
 */
@Composable
private fun Foglio(model: PostazioneModel, scheda: Scheda, col: Colori, largo: Boolean, giri: Int, modifier: Modifier, onAggiungiApp: () -> Unit) {
    // Ogni scheda ha il suo scorrimento e si apre sempre dall'inizio.
    val stato = remember(scheda) { androidx.compose.foundation.lazy.LazyListState() }
    val statoDestra = remember(scheda) { androidx.compose.foundation.lazy.LazyListState() }
    LaunchedEffect(giri, scheda) { stato.scrollToItem(0); statoDestra.scrollToItem(0) }
    val primaAttiva = scheda == Scheda.entries.first()
    val forma = RoundedCornerShape(topStart = if (primaAttiva) 0.dp else 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    val spazi = Arrangement.spacedBy(16.dp)
    if (largo) {
        Row(modifier.fillMaxWidth().padding(horizontal = 10.dp).background(col.foglio, forma).padding(horizontal = 6.dp)) {
            LazyColumn(Modifier.weight(1f), state = stato, contentPadding = PaddingValues(14.dp), verticalArrangement = spazi) {
                alto(model, scheda, col)
                item(key = "app") { AppDellaScheda(model, scheda, col, onAggiungiApp) }
                cose(model, scheda, col)
            }
            LazyColumn(Modifier.weight(1f), state = statoDestra, contentPadding = PaddingValues(14.dp), verticalArrangement = spazi) {
                persone(model, scheda, col)
            }
        }
    } else {
        LazyColumn(
            modifier.fillMaxWidth().padding(horizontal = 10.dp).background(col.foglio, forma), state = stato,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = spazi,
        ) {
            alto(model, scheda, col)
            item(key = "app") { AppDellaScheda(model, scheda, col, onAggiungiApp) }
            persone(model, scheda, col)
            cose(model, scheda, col)
        }
    }
}

/**
 * Le app di questa scheda: quelle fissate (di partenza o scelte) e le più aperte qui. Tenere premuto un'app la fissa o la
 * toglie; l'ultimo posto aggiunge un'app scelta da te.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppDellaScheda(model: PostazioneModel, scheda: Scheda, col: Colori, onAggiungi: () -> Unit) {
    val lista = model.appDellaScheda(scheda)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var menu by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(when (scheda) { Scheda.Lavoro -> R.string.app_lavoro; Scheda.Personale -> R.string.app_personale; Scheda.Viaggio -> R.string.app_viaggio }),
            color = col.inchiostro, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        val celle = lista.map<String, String?> { it } + if (lista.size < Contesto.QUANTE) listOf(null) else emptyList()
        celle.chunked(4).forEach { riga ->
            Row(Modifier.fillMaxWidth()) {
                riga.forEach { chiave ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (chiave == null) {
                            Column(Modifier.clickable { onAggiungi() }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(46.dp).background(col.riquadro, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(EuriseIcons.plus(), stringResource(R.string.aggiungi_app), Modifier.size(20.dp), tint = col.accento)
                                }
                                Text(stringResource(R.string.aggiungi), color = col.sotto, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
                            }
                        } else {
                            Column(
                                Modifier.combinedClickable(
                                    onClick = { model.apri(chiave) },
                                    onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menu = chiave },
                                ).padding(vertical = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (Siti.eSito(chiave)) IconaSito(model.nomeDi(chiave), col.accento, 46.dp, col.notte)
                                else IconaApp(model.telefono, chiave, 46.dp, col.notte)
                                Text(
                                    model.nomeDi(chiave), color = col.inchiostro, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
                                )
                            }
                            DropdownMenu(menu == chiave, onDismissRequest = { menu = null }) {
                                if (!model.fissataInScheda(scheda, chiave)) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.app_fissa)) }, onClick = { model.fissaInScheda(scheda, chiave); menu = null })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.app_togli)) }, onClick = { model.togliDaScheda(scheda, chiave); menu = null })
                            }
                        }
                    }
                }
                repeat(4 - riga.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun apriCalendario(model: PostazioneModel): Boolean =
    model.telefono.apriSuiteO(BuildConfig.HUB_PACKAGE, "eu.eurise.hub.CalendarApp", Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()))

private fun apriAttivita(model: PostazioneModel) {
    model.telefono.apriSuiteO(BuildConfig.HUB_PACKAGE, "eu.eurise.hub.TasksApp", null)
}

private fun LazyListScope.alto(model: PostazioneModel, scheda: Scheda, col: Colori) {
    val g = model.giornata
    val tel = model.telefono
    if (!g.collegata) {
        item { Nota(stringResource(R.string.senza_eurise), col) }
    } else if (g.impegni.isEmpty()) {
        item { Riga(stringResource(R.string.nessun_impegno), stringResource(R.string.calendario), col) { model.apriSezione("calendar") || apriCalendario(model) } }
    } else {
        item {
            // Le schede che scorrono hanno tutte la stessa misura: niente gradini tra una e l'altra.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(g.impegni.size, key = { g.impegni[it].id.toString() + ":" + g.impegni[it].inizio }) { i ->
                    val e = g.impegni[i]
                    SchedaImpegno(e, i == 0, scheda == Scheda.Viaggio, model.adesso, col,
                        onNaviga = { tel.avvia(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(e.luogo)))) },
                        onRitardo = { testo, titolo ->
                            tel.avvia(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, testo), titolo))
                        },
                        onApri = { model.apriImpegno(e) },
                        onElimina = if (e.eliminabile) ({ model.eliminaImpegno(e) }) else null)
                }
            }
        }
    }
    if (scheda == Scheda.Lavoro && g.collegata && model.fineGiornata) item { FineGiornata(model, col) }
}

private fun LazyListScope.persone(model: PostazioneModel, scheda: Scheda, col: Colori) {
    when (scheda) {
        Scheda.Lavoro -> avvisi(model, col, R.string.da_rispondere)
        Scheda.Personale -> {
            if (model.pausaFino > 0L) item { RigaPausa(model, col) }
            if (model.ascolto && model.brano != null) item { SchedaMusica(model, col) }
            avvisi(model, col, R.string.messaggi)
            if (model.giornata.collegata) {
                item(key = "oroscopo") { BollaOroscopo(model, col) }
                val notizie = model.notizie
                if (notizie != null) {
                    item { Text(stringResource(R.string.notizie), color = col.inchiostro, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    if (notizie.isEmpty()) item {
                        Riga(stringResource(R.string.notizie_vuote), stringResource(R.string.centro_privacy), col) { model.apriSezione("privacy") }
                    }
                    items(notizie.size) { i -> RigaNotizia(model, notizie[i], col) }
                }
            }
        }
        Scheda.Viaggio -> {
            item { BollaPreferiti(model, col) }
            item { BollaAuto(model, col) }
            if (model.ascolto && model.brano != null) item { SchedaMusica(model, col) }
        }
    }
}

/** Le cose da fare (e i farmaci) in tutte le schede: in Lavoro sempre, nelle altre solo se ce ne sono. */
private fun LazyListScope.cose(model: PostazioneModel, scheda: Scheda, col: Colori) {
    val g = model.giornata
    if (!g.collegata) return
    val visibili = g.cose.filter { it.id !in model.toccate }
    if (scheda != Scheda.Lavoro && visibili.isEmpty()) return
    item {
        val totale = g.aperte + g.fatte
        Intestazione(
            if (g.fatte > 0) stringResource(R.string.da_fare_fatte, g.fatte, totale) else stringResource(R.string.da_fare_oggi, g.aperte),
            stringResource(R.string.attivita), col,
        ) { if (!model.apriSezione("tasks")) apriAttivita(model) }
    }
    if (scheda == Scheda.Lavoro) {
        val ora = model.adesso.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sera = model.adesso.toLocalDate().atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val spazio = if (g.aperte > 0) Liberi.primo(ora, sera, g.impegni.filter { !it.tuttoIlGiorno }.map { it.inizio to it.fine }) else null
        if (spazio != null) {
            val quante = Liberi.quante(spazio, g.cose.count { !it.farmaco })
            item { SpazioLibero(spazio, spazio.inizio == ora, quante, col, onMetti = if (quante > 0) ({ model.mettiNelloSpazio(spazio) }) else null) }
        }
    }
    if (visibili.isEmpty()) item { Text(stringResource(R.string.niente_da_fare), color = col.sotto, fontSize = 13.5.sp) }
    items(visibili.size, key = { "cosa:" + visibili[it].id }) { i -> RigaCosa(model, visibili[i], col) }
}

/** «Da rispondere»: messaggi, posta e chiamate perse ancora tra le notifiche; spento, la riga per accenderlo. */
private fun LazyListScope.avvisi(model: PostazioneModel, col: Colori, titolo: Int) {
    if (!model.ascolto) {
        item { AccendiAscolto(col) { model.aggiorna() } }
        return
    }
    val lista = model.avvisi
    item {
        Text(stringResource(titolo, lista.size), color = col.inchiostro, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    if (lista.isEmpty()) item { Text(stringResource(R.string.niente_da_rispondere), color = col.sotto, fontSize = 13.5.sp) }
    items(lista.size) { i -> RigaAvviso(model, lista[i], col) }
}

private fun iconaCielo(cielo: String?): ImageVector = when (cielo) {
    null, "Clear" -> EuriseIcons.sun()
    "Fog" -> EuriseIcons.fog()
    "Drizzle", "Rain", "Showers" -> EuriseIcons.rain()
    "Snow" -> EuriseIcons.snow()
    "Storm" -> EuriseIcons.storm()
    else -> EuriseIcons.cloud()
}

@Composable
private fun rigaMeteo(m: Meteo, adesso: LocalDateTime): String {
    val cielo = stringResource(
        when (m.cielo) {
            "Clear" -> R.string.cielo_sereno; "PartlyCloudy" -> R.string.cielo_poco_nuvoloso; "Fog" -> R.string.cielo_nebbia
            "Drizzle" -> R.string.cielo_pioviggine; "Rain" -> R.string.cielo_pioggia; "Showers" -> R.string.cielo_rovesci
            "Snow" -> R.string.cielo_neve; "Storm" -> R.string.cielo_temporale; else -> R.string.cielo_nuvoloso
        },
    )
    val base = stringResource(R.string.meteo_riga, cielo, m.gradi)
    if (m.pioggia <= 0L || m.cielo in setOf("Drizzle", "Rain", "Showers", "Storm")) return base
    val quando = Instant.ofEpochMilli(m.pioggia).atZone(ZoneId.systemDefault())
    val ora = quando.toLocalTime().format(Ora)
    return base + if (quando.toLocalDate().isAfter(adesso.toLocalDate())) stringResource(R.string.meteo_pioggia_domani, ora) else stringResource(R.string.meteo_pioggia, ora)
}

internal fun orario(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(Ora)

@Composable
private fun richiamo(e: Impegno, adesso: LocalDateTime, primo: Boolean): String {
    val ora = adesso.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val inizio = Instant.ofEpochMilli(e.inizio).atZone(ZoneId.systemDefault()).toLocalDate()
    val min = ((e.inizio - ora) / 60_000L).toInt()
    val testa = if (primo) stringResource(R.string.prossimo) else stringResource(R.string.poi)
    return when {
        ora >= e.inizio && ora < e.fine -> stringResource(R.string.adesso)
        inizio.isAfter(adesso.toLocalDate()) -> stringResource(R.string.domani)
        min < 60 -> testa + " · " + stringResource(R.string.tra_min, maxOf(min, 1))
        else -> testa + " · " + stringResource(R.string.tra_ore, min / 60)
    }
}

@Composable
private fun SchedaImpegno(
    e: Impegno, primo: Boolean, viaggio: Boolean, adesso: LocalDateTime, col: Colori,
    onNaviga: () -> Unit, onRitardo: (String, String) -> Unit, onApri: () -> Unit, onElimina: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    var conferma by remember { mutableStateOf(false) }
    Row(
        Modifier.width(282.dp).height(170.dp)
            .background(col.prossimo, RoundedCornerShape(18.dp)).clickable { onApri() },
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(if (primo) col.lente else androidx.compose.ui.graphics.SolidColor(col.riga), RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)))
        Column(Modifier.fillMaxHeight().padding(start = 13.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            val tempo = if (e.tuttoIlGiorno) stringResource(R.string.tutto_il_giorno) else orario(e.inizio) + "–" + orario(e.fine)
            // In alto il richiamo e i tre puntini, sempre su una riga; l'orario va con il luogo sotto il titolo.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    richiamo(e, adesso, primo), color = col.accento, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Box {
                    Icon(EuriseIcons.more(), stringResource(R.string.altro), Modifier.size(30.dp).clickable { menu = true }.padding(6.dp), tint = col.sotto)
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.apri_calendario)) }, onClick = { menu = false; onApri() })
                        if (e.luogo.isNotBlank()) DropdownMenuItem(text = { Text(stringResource(R.string.naviga)) }, onClick = { menu = false; onNaviga() })
                        if (onElimina != null) DropdownMenuItem(text = { Text(stringResource(R.string.elimina)) }, onClick = { menu = false; conferma = true })
                    }
                }
            }
            Text(e.titolo, color = col.inchiostro, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
            Text(
                listOf(tempo, e.luogo).filter { it.isNotBlank() }.joinToString(" · "),
                color = col.sotto, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            val ora = adesso.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val vicino = !e.tuttoIlGiorno && e.inizio - ora < 90 * 60_000L
            Row(Modifier.padding(end = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e.luogo.isNotBlank()) {
                    Box(
                        Modifier.weight(1.4f).height(38.dp).background(col.lente, RoundedCornerShape(12.dp)).clickable { onNaviga() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(if (viaggio) R.string.avvia_navigatore else R.string.naviga), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (vicino) {
                    val testo = stringResource(if (viaggio) R.string.arrivo_testo else R.string.ritardo_testo, e.titolo)
                    val titolo = stringResource(if (viaggio) R.string.arrivo_a else R.string.ritardo_a)
                    Box(
                        Modifier.weight(1f).height(38.dp).background(col.foglio, RoundedCornerShape(12.dp)).clickable { onRitardo(testo, titolo) },
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(if (viaggio) R.string.avvisa else R.string.ritardo), color = col.inchiostro, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
                if (e.luogo.isBlank() && !vicino) {
                    Box(
                        Modifier.weight(1f).height(38.dp).background(col.foglio, RoundedCornerShape(12.dp)).clickable { onApri() },
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.apri), color = col.inchiostro, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
    if (conferma && onElimina != null) {
        AlertDialog(
            onDismissRequest = { conferma = false },
            title = { Text(stringResource(R.string.elimina_impegno)) },
            text = { Text(stringResource(R.string.elimina_impegno_d, e.titolo)) },
            confirmButton = { TextButton({ conferma = false; onElimina() }) { Text(stringResource(R.string.elimina)) } },
            dismissButton = { TextButton({ conferma = false }) { Text(stringResource(R.string.annulla)) } },
        )
    }
}

@Composable
internal fun Intestazione(titolo: String, link: String, col: Colori, onLink: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(titolo, color = col.inchiostro, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Row(Modifier.clickable { onLink() }, verticalAlignment = Alignment.CenterVertically) {
            Text(link, color = col.accento, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(EuriseIcons.chevronRight(), null, Modifier.size(14.dp), tint = col.accento)
        }
    }
}

/**
 * Una cosa da fare (o la dose di un farmaco): il quadratino la fa (la dose la prende), il tocco la apre nelle Attività
 * (o nei Farmaci), i tre puntini portano a domani o la eliminano (la dose si salta).
 */
@Composable
private fun RigaCosa(model: PostazioneModel, c: Cosa, col: Colori) {
    var menu by remember { mutableStateOf(false) }
    var conferma by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { model.apriCosa(c) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clickable(onClickLabel = stringResource(if (c.farmaco) R.string.presa else R.string.fatta)) { model.azioneCosa(c, PostazioneContract.ACT_DONE) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(20.dp).drawBehind {
                drawRoundRect(col.sotto, cornerRadius = androidx.compose.ui.geometry.CornerRadius(if (c.farmaco) 10.dp.toPx() else 6.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx()))
            })
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.farmaco) Icon(EuriseIcons.medical(), null, Modifier.padding(end = 5.dp).size(14.dp), tint = Color(0xFFDC2626))
                Text(c.titolo, color = col.inchiostro, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val t = if (c.scadenza > 0) Instant.ofEpochMilli(c.scadenza).atZone(ZoneId.systemDefault()).toLocalTime() else null
            val sotto = listOfNotNull(
                t?.takeIf { it.hour != 0 || it.minute != 0 }?.format(Ora),
                if (c.farmaco) stringResource(R.string.farmaco) else null,
            ).joinToString(" · ")
            if (sotto.isNotEmpty()) Text(sotto, color = col.sotto, fontSize = 13.sp)
        }
        if (c.scaduta) Text(stringResource(R.string.scaduta), color = Color(0xFFB3261E), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Box {
            Icon(EuriseIcons.more(), stringResource(R.string.altro), Modifier.size(40.dp).clickable { menu = true }.padding(11.dp), tint = col.sotto)
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (c.farmaco) R.string.presa else R.string.fatta)) },
                    onClick = { menu = false; model.azioneCosa(c, PostazioneContract.ACT_DONE) },
                )
                if (!c.farmaco) DropdownMenuItem(text = { Text(stringResource(R.string.a_domani)) }, onClick = { menu = false; model.azioneCosa(c, PostazioneContract.ACT_TOMORROW) })
                DropdownMenuItem(text = { Text(stringResource(if (c.farmaco) R.string.apri_farmaci else R.string.apri_attivita)) }, onClick = { menu = false; model.apriCosa(c) })
                DropdownMenuItem(text = { Text(stringResource(if (c.farmaco) R.string.salta else R.string.elimina)) }, onClick = { menu = false; conferma = true })
            }
        }
    }
    if (conferma) {
        AlertDialog(
            onDismissRequest = { conferma = false },
            title = { Text(stringResource(if (c.farmaco) R.string.salta_dose else R.string.elimina_cosa)) },
            text = { Text(c.titolo) },
            confirmButton = { TextButton({ conferma = false; model.azioneCosa(c, PostazioneContract.ACT_DELETE) }) { Text(stringResource(if (c.farmaco) R.string.salta else R.string.elimina)) } },
            dismissButton = { TextButton({ conferma = false }) { Text(stringResource(R.string.annulla)) } },
        )
    }
}

@Composable
private fun Riga(testo: String, link: String, col: Colori, onLink: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(testo, color = col.inchiostro, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Text(link, color = col.accento, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onLink() })
    }
}

@Composable
internal fun Nota(testo: String, col: Colori) {
    Text(
        testo, color = col.sotto, fontSize = 13.5.sp, lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

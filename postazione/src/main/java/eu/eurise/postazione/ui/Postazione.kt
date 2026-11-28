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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import eu.eurise.postazione.data.Azioni
import eu.eurise.postazione.data.Gesto
import eu.eurise.postazione.data.Linguetta
import eu.eurise.postazione.data.Pagine
import eu.eurise.postazione.data.Tipo
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ora = DateTimeFormatter.ofPattern("H:mm")
private val Giorno = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostazioneScreen(model: PostazioneModel, home: Flow<Unit>) {
    val col = colori(isSystemInDarkTheme(), model.accento)
    var ventaglio by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<String?>(null) }
    var cassetto by remember { mutableStateOf(false) }
    var cercaSubito by remember { mutableStateOf(false) }
    var sitoPer by remember { mutableStateOf<String?>(null) }
    // Dove va l'app scelta nel cassetto: l'id di una categoria, «pagina:<id>» o «gesto:<nome>».
    var scegliPer by remember { mutableStateOf<String?>(null) }
    var impostazioni by remember { mutableStateOf(false) }
    var modificaBolle by remember { mutableStateOf(false) }
    var chiediBolla by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var aiAperta by remember { mutableStateOf(false) }
    var pagineAperte by remember { mutableStateOf(false) }
    var gestiAperti by remember { mutableStateOf(false) }

    val homeIndex = model.pagine.size
    val pager = rememberPagerState(initialPage = homeIndex) { model.pagine.size + 2 }
    val scope = rememberCoroutineScope()
    // Il tasto Home chiude tutto, torna alla Home e riporta la linguetta in cima.
    var giri by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        home.collect {
            ventaglio = null; cassetto = false; scegliPer = null; editor = null; aiAperta = false; modificaBolle = false; giri++
            pager.animateScrollToPage(model.pagine.size)
        }
    }
    LaunchedEffect(model.impostazioniRichieste) { if (model.impostazioniRichieste > 0) impostazioni = true }
    LaunchedEffect(Unit) { while (true) { delay(15_000); model.tic() } }
    BackHandler(enabled = ventaglio != null || aiAperta || modificaBolle || pager.currentPage != homeIndex) {
        when {
            ventaglio != null -> ventaglio = null
            aiAperta -> aiAperta = false
            modificaBolle -> modificaBolle = false
            else -> scope.launch { pager.animateScrollToPage(homeIndex) }
        }
    }

    val cielo = model.cielo
    val suCielo = testoSulCielo(cielo)
    val apriCassetto: (Boolean) -> Unit = { cerca -> cercaSubito = cerca; scegliPer = null; cassetto = true }
    val paginaAi: String? = model.pagine.getOrNull(pager.currentPage)?.id
    // I gesti a due e tre dita: cerca, AI e «tutte le app» li fa la schermata, il resto il modello.
    val onGesto: (Gesto) -> Unit = { g ->
        when (val azione = model.gesti[g].orEmpty()) {
            Azioni.CERCA -> apriCassetto(true)
            Azioni.AI -> aiAperta = true
            Azioni.TUTTE -> scope.launch { pager.animateScrollToPage(model.pagine.size + 1) }
            else -> model.funzione(azione)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(col.fondo).gestiDita(onGesto)) {
        CieloDiFondo(cielo)
        // Telefono: dock in basso. Tablet e schermi larghi: binario dal lato della mano.
        val largo = maxWidth >= 840.dp
        val onGruppo: (String) -> Unit = { g ->
            val prima = model.gruppi[g]?.firstOrNull()
            if (prima == null || !model.apri(prima)) ventaglio = g
        }
        val suCassetto = pager.currentPage == homeIndex + 1
        Row(Modifier.fillMaxSize()) {
            val binario: @Composable () -> Unit = {
                if (largo && !suCassetto) Binario(
                    model, col, onGruppo, { ventaglio = it }, { apriCassetto(true) }, { model.alfred() },
                    { scope.launch { pager.animateScrollToPage(homeIndex + 1) } },
                )
            }
            if (model.mancino) binario()
            HorizontalPager(pager, Modifier.weight(1f).fillMaxHeight(), beyondViewportPageCount = 1) { i ->
                when {
                    i < homeIndex -> PaginaUtente(
                        model, model.pagine[i], col, suCielo, largo,
                        onAggiungi = { scegliPer = "pagina:" + model.pagine[i].id; cercaSubito = false; cassetto = true },
                        onModifica = { pagineAperte = true },
                    )
                    i == homeIndex -> Column(
                        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = if (largo) 14.dp else AltezzaDock + 28.dp),
                    ) {
                        if (modificaBolle) BarraModifica(model, col) { modificaBolle = false } else Testata(model, suCielo, Modifier)
                        Linguette(model, col, suCielo, onBolle = { modificaBolle = true }, onAggiungiBolla = { modificaBolle = true; chiediBolla++ })
                        FoglioBolle(model, model.attiva, col, giri, modificaBolle, { modificaBolle = it }, Modifier.weight(1f), chiediBolla)
                    }
                    else -> CassettoCategorie(model, col, giri, onModifica = { editor = it })
                }
            }
            if (!model.mancino) binario()
        }
        if (!largo && !suCassetto) {
            // Lo swipe dal basso apre l'AI della pagina: il richiamo sopra la dock.
            val ai = model.aiDi(paginaAi)
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = AltezzaDock + 8.dp)
                    .clickable { aiAperta = true }.pointerInput(Unit) { scorriSu { aiAperta = true } }.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.chevronUp(), null, Modifier.size(14.dp), tint = suCielo.copy(alpha = 0.8f))
                Text(" " + ai.nome.substringBefore(" ·"), color = suCielo.copy(alpha = 0.85f), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Barra(
                model, col,
                onGruppo = onGruppo,
                onVentaglio = { ventaglio = it },
                onLente = { apriCassetto(true) },
                onAlfred = { model.alfred() },
                modifier = Modifier.align(Alignment.BottomCenter).pointerInput(Unit) { scorriSu { aiAperta = true } },
            )
        }
        ventaglio?.let { g ->
            Ventaglio(model, g, col, largo, onChiudi = { ventaglio = null }, onAggiungi = {
                ventaglio = null; scegliPer = g; cercaSubito = false; cassetto = true
            }, onModifica = { ventaglio = null; editor = g })
        }
        editor?.let { g -> EditorCategoria(model, g, onChiudi = { editor = null }) }
        if (aiAperta) AiDalBasso(model, paginaAi, col, onChiudi = { aiAperta = false })
        if (pagineAperte) GestionePagine(model, onChiudi = { pagineAperte = false }, onVai = { i -> pagineAperte = false; scope.launch { pager.animateScrollToPage(i) } })
        if (gestiAperti) GestioneGesti(model, onChiudi = { gestiAperti = false }, onScegliApp = { g -> gestiAperti = false; scegliPer = "gesto:" + g.name; cercaSubito = false; cassetto = true })
        if (cassetto) {
            Cassetto(
                model, col, cercaSubito, scegliPer,
                onScelta = { chiave ->
                    val g = scegliPer
                    when {
                        g == null -> if (model.apri(chiave)) cassetto = false
                        g.startsWith("pagina:") -> { model.aggiungiAPagina(g.removePrefix("pagina:"), chiave); cassetto = false; scegliPer = null }
                        g.startsWith("gesto:") -> {
                            Gesto.entries.firstOrNull { it.name == g.removePrefix("gesto:") }?.let { model.impostaGesto(it, Azioni.app(chiave)) }
                            cassetto = false; scegliPer = null; gestiAperti = true
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
                        if (ok) { sitoPer = null; cassetto = false; scegliPer = null; if (!g.startsWith("pagina:")) ventaglio = g }
                    }
                },
                onChiudi = { sitoPer = null },
            )
        }
        if (impostazioni) Impostazioni(
            model, onChiudi = { impostazioni = false },
            onPagine = { impostazioni = false; pagineAperte = true },
            onGesti = { impostazioni = false; gestiAperti = true },
        )
    }
}

/** Uno scorrimento verso l'alto di almeno 60 dp. */
internal suspend fun androidx.compose.ui.input.pointer.PointerInputScope.scorriSu(azione: () -> Unit) {
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
    val notte = adesso.hour >= 22 || adesso.hour < 4
    val testo0 = if (notte) stringResource(R.string.saluto_notte) else saluto
    val testo = if (model.nome.isBlank()) testo0 else stringResource(R.string.saluto_nome, testo0, model.nome)
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
            Icon(iconaCielo(meteo?.cielo, model.cielo.fase.let { it == eu.eurise.postazione.data.Fase.Notte || it == eu.eurise.postazione.data.Fase.Sera }), null, Modifier.size(16.dp), tint = colore)
            Text(" " + (meteo?.let { rigaMeteo(it, adesso) } ?: stringResource(R.string.apri_meteo)), color = colore, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Icon(EuriseIcons.chevronRight(), null, Modifier.size(12.dp), tint = colore.copy(alpha = 0.6f))
        }
    }
}

/** Il nome di una linguetta: quello scelto, o quello di partenza del suo tipo. */
@Composable
internal fun nomeLinguetta(l: Linguetta): String = l.nome.ifBlank {
    stringResource(when (l.tipo) { Tipo.Lavoro -> R.string.scheda_lavoro; Tipo.Personale -> R.string.scheda_personale; Tipo.Viaggio -> R.string.scheda_viaggio; Tipo.Libera -> R.string.scheda_libera })
}

/**
 * Le linguette: un tocco la apre, tenendola premuta Rinomina, Bolle, Quando si apre da sola, Sposta e Togli; il + ne
 * aggiunge una. Linguetta e foglio sono un pezzo solo, senza filo (tavola del 27 novembre).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Linguette(model: PostazioneModel, col: Colori, suCielo: Color, onBolle: () -> Unit, onAggiungiBolla: () -> Unit) {
    var piu by remember { mutableStateOf(false) }
    val attiva = model.attiva
    var menu by remember { mutableStateOf<Linguetta?>(null) }
    var rinomina by remember { mutableStateOf<Linguetta?>(null) }
    var tipo by remember { mutableStateOf<Linguetta?>(null) }
    var nuova by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.Bottom) {
        Row(Modifier.weight(1f).restaNellaFila().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Bottom) {
            model.linguette.forEachIndexed { i, l ->
                val nome = nomeLinguetta(l)
                val tocco = Modifier.combinedClickable(
                    onClick = { model.scegli(l) },
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = l },
                )
                Box {
                    if (l.id == attiva.id) {
                        val prima = i == 0
                        Box(
                            Modifier.padding(start = if (prima) 0.dp else 6.dp, end = 6.dp).height(40.dp)
                                .drawBehind {
                                    // I «piedi» concavi della linguetta: il foglio e la linguetta sono un pezzo solo.
                                    val r = 10.dp.toPx(); val w = size.width; val h = size.height
                                    drawPath(Path().apply { moveTo(w, h - r); quadraticTo(w, h, w + r, h); lineTo(w, h); close() }, col.foglio)
                                    if (!prima) drawPath(Path().apply { moveTo(0f, h - r); quadraticTo(0f, h, -r, h); lineTo(0f, h); close() }, col.foglio)
                                }
                                .background(col.foglio, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                .then(tocco).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(nome, color = suCielo, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    } else {
                        Box(Modifier.height(36.dp).then(tocco).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text(nome, color = suCielo.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                    DropdownMenu(menu?.id == l.id, onDismissRequest = { menu = null }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.lg_rinomina)) }, onClick = { menu = null; rinomina = l })
                        DropdownMenuItem(text = { Text(stringResource(R.string.lg_bolle)) }, onClick = { menu = null; model.scegli(l); onBolle() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.lg_quando)) }, onClick = { menu = null; tipo = l })
                        if (i > 0) DropdownMenuItem(text = { Text(stringResource(R.string.lg_sinistra)) }, onClick = { menu = null; model.spostaLinguetta(l, -1) })
                        if (i < model.linguette.size - 1) DropdownMenuItem(text = { Text(stringResource(R.string.lg_destra)) }, onClick = { menu = null; model.spostaLinguetta(l, 1) })
                        if (model.linguette.size > 1) DropdownMenuItem(
                            text = { Text(stringResource(R.string.lg_togli), color = Color(0xFFB3261E)) },
                            onClick = { menu = null; model.togliLinguetta(l) },
                        )
                    }
                }
            }
            // Il «+»: aggiungere una bolla a questa scheda, sistemare le bolle o aggiungere una scheda.
            Box {
                Box(
                    Modifier.padding(start = 2.dp, bottom = 5.dp).size(32.dp).background(col.foglio, CircleShape).clickable { piu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.plus(), stringResource(R.string.b_aggiungi), Modifier.size(15.dp), tint = suCielo) }
                DropdownMenu(piu, onDismissRequest = { piu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.b_aggiungi_a, nomeLinguetta(attiva))) },
                        leadingIcon = { Icon(EuriseIcons.plus(), null) }, onClick = { piu = false; onAggiungiBolla() },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.lg_bolle)) }, leadingIcon = { Icon(EuriseIcons.pencil(), null) }, onClick = { piu = false; onBolle() })
                    if (model.linguette.size < Pagine.MAX_LINGUETTE) DropdownMenuItem(
                        text = { Text(stringResource(R.string.lg_aggiungi)) }, onClick = { piu = false; nuova = true },
                    )
                }
            }
        }
        Row(
            Modifier.padding(start = 8.dp, end = 14.dp, bottom = 11.dp).clickable { model.tornaAutomatica() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(suCielo.copy(alpha = 0.9f), CircleShape))
            Text(
                " " + stringResource(if (model.automatica) R.string.scheda_auto else R.string.scheda_mano),
                color = suCielo.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
    rinomina?.let { l ->
        TestoDialog(stringResource(R.string.lg_rinomina), nomeLinguetta(l), onOk = { model.rinominaLinguetta(l, it) }, onChiudi = { rinomina = null })
    }
    if (nuova) TestoDialog(stringResource(R.string.lg_aggiungi), "", onOk = { if (it.isNotBlank()) model.aggiungiLinguetta(it) }, onChiudi = { nuova = false })
    tipo?.let { l -> TipoDialog(l, onScelta = { model.tipoLinguetta(l, it) }, onChiudi = { tipo = null }) }
}

internal fun iconaCielo(cielo: String?, notte: Boolean = false): ImageVector = when (cielo) {
    null, "Clear" -> if (notte) EuriseIcons.moon() else EuriseIcons.sun()
    "Fog" -> EuriseIcons.fog()
    "Drizzle", "Rain", "Showers" -> EuriseIcons.rain()
    "Snow" -> EuriseIcons.snow()
    "Storm" -> EuriseIcons.storm()
    else -> EuriseIcons.cloud()
}

@Composable
internal fun rigaMeteo(m: Meteo, adesso: LocalDateTime): String {
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
internal fun richiamo(e: Impegno, adesso: LocalDateTime, primo: Boolean): String {
    val ora = adesso.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val inizio = Instant.ofEpochMilli(e.inizio).atZone(ZoneId.systemDefault()).toLocalDate()
    val min = ((e.inizio - ora) / 60_000L).toInt()
    val testa = if (primo) stringResource(R.string.prossimo) else stringResource(R.string.poi)
    return when {
        ora >= e.inizio && ora < e.fine -> stringResource(R.string.adesso)
        inizio.isAfter(adesso.toLocalDate()) -> stringResource(R.string.domani)
        // Solo «tra 29 min»: con «Prossimo ·» davanti il nome dell'impegno restava tagliato (schermate del giro 1151).
        min < 60 -> stringResource(R.string.tra_min, maxOf(min, 1))
        min < 24 * 60 -> stringResource(R.string.tra_ore, min / 60)
        else -> testa
    }
}

/**
 * Un impegno, sempre della stessa misura e senza spazi vuoti (tavola del 27 novembre): prima riga il nome, seconda
 * orario e luogo, sotto due tasti (Naviga se c'è un luogo, altrimenti Apri; Ritardo se manca poco).
 */
@Composable
internal fun SchedaImpegno(
    e: Impegno, primo: Boolean, viaggio: Boolean, adesso: LocalDateTime, col: Colori,
    onNaviga: () -> Unit, onRitardo: (String, String) -> Unit, onApri: () -> Unit, onElimina: (() -> Unit)?,
    modifier: Modifier = Modifier.width(262.dp),
) {
    var menu by remember { mutableStateOf(false) }
    var conferma by remember { mutableStateOf(false) }
    val ora = adesso.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val vicino = !e.tuttoIlGiorno && e.inizio - ora < 90 * 60_000L
    Column(
        modifier.background(col.prossimo, RoundedCornerShape(16.dp)).clickable { onApri() }.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.titolo, color = col.inchiostro, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (primo) {
                Text(
                    richiamo(e, adesso, true), color = col.accento, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp).background(col.accento.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Box {
                Icon(EuriseIcons.more(), stringResource(R.string.altro), Modifier.size(30.dp).clickable { menu = true }.padding(6.dp), tint = col.sotto)
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.apri_calendario)) }, onClick = { menu = false; onApri() })
                    if (e.luogo.isNotBlank()) DropdownMenuItem(text = { Text(stringResource(R.string.naviga)) }, onClick = { menu = false; onNaviga() })
                    if (onElimina != null) DropdownMenuItem(text = { Text(stringResource(R.string.elimina)) }, onClick = { menu = false; conferma = true })
                }
            }
        }
        val tempo = if (e.tuttoIlGiorno) stringResource(R.string.tutto_il_giorno) else orario(e.inizio) + "–" + orario(e.fine)
        Text(
            listOf(tempo, e.luogo).filter { it.isNotBlank() }.joinToString(" · "),
            color = col.sotto, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 6.dp),
        )
        Row(Modifier.padding(top = 8.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tasto: @Composable RowScope.(String, Boolean, () -> Unit) -> Unit = { testo, pieno, azione ->
                Box(
                    Modifier.weight(1f).height(36.dp)
                        .then(if (pieno) Modifier.background(col.lente, RoundedCornerShape(11.dp)) else Modifier.background(col.bolla, RoundedCornerShape(11.dp)))
                        .clickable { azione() },
                    contentAlignment = Alignment.Center,
                ) { Text(testo, color = if (pieno) Color.White else col.inchiostro, fontSize = 13.sp, fontWeight = if (pieno) FontWeight.Bold else FontWeight.SemiBold) }
            }
            if (e.luogo.isNotBlank()) tasto(stringResource(if (viaggio) R.string.avvia_navigatore else R.string.naviga), true, onNaviga)
            else tasto(stringResource(R.string.apri), true, onApri)
            if (vicino) {
                val testo = stringResource(if (viaggio) R.string.arrivo_testo else R.string.ritardo_testo, e.titolo)
                val titolo = stringResource(if (viaggio) R.string.arrivo_a else R.string.ritardo_a)
                tasto(stringResource(if (viaggio) R.string.avvisa else R.string.ritardo), false) { onRitardo(testo, titolo) }
            } else if (e.luogo.isNotBlank()) tasto(stringResource(R.string.apri), false, onApri)
            else Spacer(Modifier.weight(1f))
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
internal fun RigaCosa(model: PostazioneModel, c: Cosa, col: Colori) {
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
internal fun Riga(testo: String, link: String, col: Colori, onLink: () -> Unit) {
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

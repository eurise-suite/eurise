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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.postazione.BuildConfig
import eu.eurise.postazione.PostazioneModel
import eu.eurise.postazione.R
import eu.eurise.postazione.data.Cosa
import eu.eurise.postazione.data.Gruppo
import eu.eurise.postazione.data.Impegno
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
    val col = colori(isSystemInDarkTheme())
    var ventaglio by remember { mutableStateOf<Gruppo?>(null) }
    var cassetto by remember { mutableStateOf(false) }
    var cercaSubito by remember { mutableStateOf(false) }
    var scegliPer by remember { mutableStateOf<Gruppo?>(null) }
    var impostazioni by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { home.collect { ventaglio = null; cassetto = false; scegliPer = null } }
    LaunchedEffect(Unit) { while (true) { delay(15_000); model.tic() } }
    BackHandler(enabled = ventaglio != null) { ventaglio = null }

    val adesso = model.adesso
    val cielo = Schede.cielo(adesso)
    val suCielo = testoSulCielo(cielo)
    val scheda = model.scheda
    val apriCassetto: (Boolean) -> Unit = { cerca -> cercaSubito = cerca; scegliPer = null; cassetto = true }

    Box(Modifier.fillMaxSize().background(col.fondo)) {
        CieloDiFondo(cielo)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 116.dp)) {
            Testata(model, suCielo, Modifier.pointerInput(Unit) { scorriSu { apriCassetto(false) } })
            Linguette(model, scheda, col, suCielo)
            Foglio(model, scheda, col, Modifier.weight(1f))
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
            model, scheda, col,
            onGruppo = { g ->
                val prima = model.gruppi[g]?.firstOrNull()
                if (prima == null || !model.telefono.apri(prima)) ventaglio = g
            },
            onVentaglio = { ventaglio = it },
            onLente = { apriCassetto(true) },
            onAlfred = { model.alfred() },
            modifier = Modifier.align(Alignment.BottomCenter).pointerInput(Unit) { scorriSu { apriCassetto(false) } },
        )
        ventaglio?.let { g ->
            Ventaglio(model, g, col, onChiudi = { ventaglio = null }, onAggiungi = {
                ventaglio = null; scegliPer = g; cercaSubito = false; cassetto = true
            })
        }
        if (cassetto) {
            Cassetto(
                model, col, cercaSubito, scegliPer,
                onScelta = { chiave ->
                    val g = scegliPer
                    if (g != null) { model.aggiungi(g, chiave); cassetto = false; scegliPer = null; ventaglio = g }
                    else if (model.telefono.apri(chiave)) cassetto = false
                },
                onImpostazioni = { impostazioni = true },
                onChiudi = { cassetto = false; scegliPer = null },
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
        Row(
            Modifier.clickable { tel.apriSuiteO(hub, "eu.eurise.hub.MainActivity", tel.avviaPacchetto(hub)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.sun(), null, Modifier.size(16.dp), tint = colore)
            Text(" " + stringResource(R.string.apri_meteo), color = colore, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
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

@Composable
private fun Foglio(model: PostazioneModel, scheda: Scheda, col: Colori, modifier: Modifier) {
    val primaAttiva = scheda == Scheda.entries.first()
    val forma = RoundedCornerShape(topStart = if (primaAttiva) 0.dp else 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    val g = model.giornata
    val tel = model.telefono
    val hub = BuildConfig.HUB_PACKAGE
    val apriCalendario = { tel.apriSuiteO(hub, "eu.eurise.hub.CalendarApp", Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())) }
    LazyColumn(
        modifier.fillMaxWidth().padding(horizontal = 10.dp).background(col.foglio, forma),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!g.collegata) {
            item { Avviso(stringResource(R.string.senza_eurise), col) }
        } else if (g.impegni.isEmpty()) {
            item {
                Riga(stringResource(R.string.nessun_impegno), stringResource(R.string.calendario), col) { apriCalendario() }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(g.impegni.size) { i ->
                        val e = g.impegni[i]
                        SchedaImpegno(e, i == 0, scheda == Scheda.Viaggio, model.adesso, col,
                            onNaviga = { tel.avvia(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(e.luogo)))) },
                            onApri = { apriCalendario() })
                    }
                }
            }
        }
        when (scheda) {
            Scheda.Lavoro -> if (g.collegata) {
                item {
                    val totale = g.aperte + g.fatte
                    Intestazione(
                        if (g.fatte > 0) stringResource(R.string.da_fare_fatte, g.fatte, totale) else stringResource(R.string.da_fare_oggi, g.aperte),
                        stringResource(R.string.attivita), col,
                    ) { tel.apriSuiteO(hub, "eu.eurise.hub.TasksApp", null) }
                }
                if (g.cose.isEmpty()) item { Text(stringResource(R.string.niente_da_fare), color = col.sotto, fontSize = 13.5.sp) }
                items(g.cose.size) { i -> RigaCosa(g.cose[i], col) { tel.apriSuiteO(hub, "eu.eurise.hub.TasksApp", null) } }
            }
            Scheda.Personale -> item { Avviso(stringResource(R.string.personale_presto), col) }
            Scheda.Viaggio -> item { Avviso(stringResource(R.string.viaggio_presto), col) }
        }
    }
}

private fun orario(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(Ora)

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
    onNaviga: () -> Unit, onApri: () -> Unit,
) {
    Row(
        Modifier.width(if (primo) 300.dp else 250.dp).height(IntrinsicSize.Min)
            .background(col.prossimo, RoundedCornerShape(18.dp)).clickable { onApri() },
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(col.lente, RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)))
        Column(Modifier.padding(start = 13.dp, end = 14.dp, top = 13.dp, bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val tempo = if (e.tuttoIlGiorno) stringResource(R.string.tutto_il_giorno) else orario(e.inizio) + "–" + orario(e.fine)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(richiamo(e, adesso, primo), color = col.accento, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(tempo, color = col.sotto, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(e.titolo, color = col.inchiostro, fontSize = if (primo) 19.sp else 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.luogo.isNotBlank()) Text(e.luogo, color = col.sotto, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (primo && e.luogo.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(40.dp).background(col.lente, RoundedCornerShape(12.dp)).clickable { onNaviga() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(if (viaggio) R.string.avvia_navigatore else R.string.naviga), color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Intestazione(titolo: String, link: String, col: Colori, onLink: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(titolo, color = col.inchiostro, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Row(Modifier.clickable { onLink() }, verticalAlignment = Alignment.CenterVertically) {
            Text(link, color = col.accento, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(EuriseIcons.chevronRight(), null, Modifier.size(14.dp), tint = col.accento)
        }
    }
}

@Composable
private fun RigaCosa(c: Cosa, col: Colori, onApri: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onApri() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(horizontal = 6.dp).size(20.dp).background(Color.Transparent, RoundedCornerShape(6.dp))
            .drawBehind { drawRoundRect(col.sotto, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx())) })
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(c.titolo, color = col.inchiostro, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.scadenza > 0) {
                val t = Instant.ofEpochMilli(c.scadenza).atZone(ZoneId.systemDefault()).toLocalTime()
                if (t.hour != 0 || t.minute != 0) Text(t.format(Ora), color = col.sotto, fontSize = 13.sp)
            }
        }
        if (c.scaduta) Text(stringResource(R.string.scaduta), color = Color(0xFFB3261E), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
private fun Avviso(testo: String, col: Colori) {
    Text(
        testo, color = col.sotto, fontSize = 13.5.sp, lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

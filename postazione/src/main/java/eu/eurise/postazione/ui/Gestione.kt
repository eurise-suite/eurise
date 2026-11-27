package eu.eurise.postazione.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.PrivacyConsentSheet
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.postazione.PostazioneModel
import eu.eurise.postazione.R
import eu.eurise.postazione.data.Ai
import eu.eurise.postazione.data.Azioni
import eu.eurise.postazione.data.Gesto
import eu.eurise.postazione.data.Linguetta
import eu.eurise.postazione.data.Pagine
import eu.eurise.postazione.data.Tipo
import eu.eurise.postazione.data.Verso
import kotlin.math.abs

/**
 * I gesti a due e tre dita: si guardano le dita prima dei figli; appena ce ne sono due o più il movimento è del gesto (la
 * pagina e le liste non scorrono). Lasciate le dita, se il centro si è spostato di almeno 70 dp, conta la direzione più netta.
 */
fun Modifier.gestiDita(onGesto: (Gesto) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        var massimo = 0
        var inizio: Offset? = null
        var ultimo = Offset.Zero
        do {
            val evento = awaitPointerEvent(PointerEventPass.Initial)
            val giu = evento.changes.filter { it.pressed }
            if (giu.size > massimo) {
                massimo = giu.size
                inizio = centro(giu.map { it.position })
            }
            if (giu.size >= 2) {
                ultimo = centro(giu.map { it.position })
                evento.changes.forEach { it.consume() }
            }
        } while (evento.changes.any { it.pressed })
        val start = inizio
        if (massimo in 2..3 && start != null) {
            val d = ultimo - start
            val soglia = 70.dp.toPx()
            val verso = when {
                abs(d.x) < soglia && abs(d.y) < soglia -> null
                abs(d.x) > abs(d.y) -> if (d.x > 0) Verso.Destra else Verso.Sinistra
                else -> if (d.y < 0) Verso.Su else Verso.Giu
            }
            verso?.let { v -> Gesto.di(massimo, v)?.let(onGesto) }
        }
    }
}

private fun centro(p: List<Offset>): Offset = Offset(p.sumOf { it.x.toDouble() }.toFloat() / p.size, p.sumOf { it.y.toDouble() }.toFloat() / p.size)

/** Una domanda di una riga (nome di una linguetta o di una pagina). */
@Composable
fun TestoDialog(titolo: String, iniziale: String, onOk: (String) -> Unit, onChiudi: () -> Unit) {
    var testo by remember { mutableStateOf(iniziale) }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(titolo) },
        text = { OutlinedTextField(testo, { testo = it.take(24) }, singleLine = true, label = { Text(stringResource(R.string.cat_nome)) }) },
        confirmButton = { TextButton({ onOk(testo.trim()); onChiudi() }) { Text(stringResource(R.string.imp_fatto)) } },
        dismissButton = { TextButton(onChiudi) { Text(stringResource(R.string.annulla)) } },
    )
}

/** Quando una linguetta si apre da sola. */
@Composable
fun TipoDialog(l: Linguetta, onScelta: (Tipo) -> Unit, onChiudi: () -> Unit) {
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.lg_quando)) },
        text = {
            Column {
                listOf(Tipo.Lavoro to R.string.lg_tipo_lavoro, Tipo.Personale to R.string.lg_tipo_personale, Tipo.Viaggio to R.string.lg_tipo_viaggio, Tipo.Libera to R.string.lg_tipo_mai)
                    .forEach { (t, testo) ->
                        Row(Modifier.fillMaxWidth().clickable { onScelta(t); onChiudi() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(l.tipo == t, { onScelta(t); onChiudi() })
                            Text(stringResource(testo), fontSize = 14.sp)
                        }
                    }
            }
        },
        confirmButton = { TextButton(onChiudi) { Text(stringResource(R.string.chiudi)) } },
    )
}

/**
 * Lo swipe dal basso: l'AI della pagina, con il campo sotto il pollice. La domanda parte solo con Invia; il pulsante in
 * alto cambia l'AI di questa pagina (le tacchette dicono dove va, i servizi chiusi passano dall'avviso).
 */
@Composable
fun AiDalBasso(model: PostazioneModel, paginaId: String?, col: Colori, onChiudi: () -> Unit) {
    val pagina = model.pagine.firstOrNull { it.id == paginaId }
    val ai = model.aiDi(paginaId)
    var testo by remember { mutableStateOf("") }
    var scelta by remember { mutableStateOf(false) }
    var avviso by remember { mutableStateOf<Ai?>(null) }
    val fuoco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fuoco.requestFocus() } }
    val cambia: (Ai) -> Unit = { a -> if (pagina != null) model.aiPagina(pagina, a) else model.preferisciAi(a) }
    val invia = { if (testo.isNotBlank()) { model.chiedi(testo, ai); onChiudi() } }
    Box(
        Modifier.fillMaxSize().background(col.fondo.copy(alpha = 0.94f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onChiudi() },
    ) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 18.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pagina?.nome?.let { stringResource(R.string.ai_pagina, it) } ?: stringResource(R.string.ai_home), color = col.sotto, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box {
                    Row(
                        Modifier.background(col.riquadro, RoundedCornerShape(17.dp)).clickable { scelta = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(EuriseIcons.spark(), null, Modifier.size(14.dp), tint = col.accento)
                        Text(" " + ai.nome + " ", color = col.inchiostro, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Icon(EuriseIcons.chevronDown(), stringResource(R.string.chiedi_altre), Modifier.size(13.dp), tint = col.sotto)
                    }
                    DropdownMenu(scelta, onDismissRequest = { scelta = false }) {
                        Ai.entries.forEach { a ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(a.nome, fontWeight = if (a == ai) FontWeight.Bold else FontWeight.Normal)
                                        PrivacyMeter(a.tacche, runCatching { Jurisdiction.valueOf(a.dove) }.getOrNull(), Modifier.padding(top = 2.dp))
                                    }
                                },
                                onClick = { scelta = false; if (a.chiuso && a != ai) avviso = a else cambia(a) },
                            )
                        }
                    }
                }
            }
            PrivacyMeter(ai.tacche, runCatching { Jurisdiction.valueOf(ai.dove) }.getOrNull())
            Text(stringResource(if (ai.sulTelefono) R.string.chiedi_telefono else R.string.ai_solo_invia), color = col.sotto, fontSize = 12.sp)
            Text(stringResource(R.string.ai_domanda), color = col.inchiostro, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                testo, { testo = it.take(2000) },
                Modifier.weight(1f).focusRequester(fuoco),
                placeholder = { Text(stringResource(R.string.chiedi_a, ai.nome.substringBefore(" ·"))) },
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = col.bolla, unfocusedContainerColor = col.bolla,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { invia() }),
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(52.dp).background(col.lente, RoundedCornerShape(18.dp)).clickable { invia() },
                contentAlignment = Alignment.Center,
            ) { Icon(EuriseIcons.send(), stringResource(R.string.ai_invia), Modifier.size(22.dp), tint = Color.White) }
        }
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
            onAccept = { cambia(a); avviso = null },
        )
    }
}

/**
 * Le pagine a sinistra della Home: nome, AI dello swipe dal basso, ordine; si aggiungono e si tolgono. La Home resta
 * sempre, con l'AI preferita; a destra c'è sempre il cassetto.
 */
@Composable
fun GestionePagine(model: PostazioneModel, onChiudi: () -> Unit, onVai: (Int) -> Unit) {
    var rinomina by remember { mutableStateOf<eu.eurise.postazione.data.Pagina?>(null) }
    var nuova by remember { mutableStateOf(false) }
    var aiPer by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.pg_titolo)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.pg_aiuto), fontSize = 12.5.sp)
                model.pagine.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.nome, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable { onVai(i) }.padding(vertical = 8.dp))
                        Box {
                            Text(p.ai.nome.substringBefore(" ·"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.background(Color(0xFFEFEDE8), RoundedCornerShape(14.dp)).clickable { aiPer = p.id }.padding(horizontal = 9.dp, vertical = 5.dp))
                            DropdownMenu(aiPer == p.id, onDismissRequest = { aiPer = null }) {
                                Ai.entries.forEach { a -> DropdownMenuItem(text = { Text(a.nome) }, onClick = { aiPer = null; model.aiPagina(p, a) }) }
                            }
                        }
                        Icon(EuriseIcons.pencil(), stringResource(R.string.lg_rinomina), Modifier.size(34.dp).clickable { rinomina = p }.padding(8.dp))
                        if (i > 0) Icon(EuriseIcons.chevronUp(), stringResource(R.string.b_prima), Modifier.size(34.dp).clickable { model.spostaPagina(p, -1) }.padding(8.dp))
                        Icon(EuriseIcons.close(), stringResource(R.string.pg_togli), Modifier.size(34.dp).clickable { model.togliPagina(p) }.padding(9.dp), tint = Color(0xFFB3261E))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pg_home), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                    Text(model.ai.nome.substringBefore(" ·"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(Color(0xFFEFEDE8), RoundedCornerShape(14.dp)).padding(horizontal = 9.dp, vertical = 5.dp))
                }
                if (model.pagine.size < Pagine.MAX_PAGINE) {
                    TextButton({ nuova = true }) { Text("+ " + stringResource(R.string.pg_aggiungi)) }
                }
            }
        },
        confirmButton = { TextButton(onChiudi) { Text(stringResource(R.string.imp_fatto)) } },
    )
    rinomina?.let { p -> TestoDialog(stringResource(R.string.lg_rinomina), p.nome, onOk = { if (it.isNotBlank()) model.rinominaPagina(p, it) }, onChiudi = { rinomina = null }) }
    if (nuova) TestoDialog(stringResource(R.string.pg_aggiungi), "", onOk = { if (it.isNotBlank()) model.nuovaPagina(it) }, onChiudi = { nuova = false })
}

@androidx.annotation.StringRes
private fun nomeAzione(a: String): Int = when (a) {
    Azioni.TELEFONO -> R.string.az_telefono; Azioni.FOTOCAMERA -> R.string.az_fotocamera; Azioni.NOTA -> R.string.az_nota
    Azioni.TORCIA -> R.string.az_torcia; Azioni.SCANNER -> R.string.az_scanner; Azioni.VOCE -> R.string.az_voce
    Azioni.CERCA -> R.string.az_cerca; Azioni.AI -> R.string.az_ai; Azioni.TUTTE -> R.string.az_tutte
    Azioni.FARMACI -> R.string.az_farmaci; else -> R.string.az_nessuna
}

/** I gesti a due e tre dita, ognuno con una funzione o un'app a scelta. */
@Composable
fun GestioneGesti(model: PostazioneModel, onChiudi: () -> Unit, onScegliApp: (Gesto) -> Unit) {
    var aperto by remember { mutableStateOf<Gesto?>(null) }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.gs_titolo)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.gs_aiuto), fontSize = 12.5.sp)
                Gesto.entries.forEach { g ->
                    if (g == Gesto.DueSu || g == Gesto.TreSu) {
                        Text(stringResource(if (g.dita == 2) R.string.gs_due else R.string.gs_tre), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    }
                    val azione = model.gesti[g].orEmpty()
                    val nome = if (Azioni.eApp(azione)) model.nomeDi(Azioni.chiave(azione)) else stringResource(nomeAzione(azione))
                    Box {
                        Row(Modifier.fillMaxWidth().clickable { aperto = g }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(freccia(g.verso), fontSize = 18.sp, modifier = Modifier.width(30.dp))
                            Text(nome, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(EuriseIcons.chevronDown(), null, Modifier.size(16.dp))
                        }
                        DropdownMenu(aperto == g, onDismissRequest = { aperto = null }) {
                            (Azioni.funzioni + Azioni.NESSUNA).forEach { a ->
                                DropdownMenuItem(text = { Text(stringResource(nomeAzione(a))) }, onClick = { aperto = null; model.impostaGesto(g, a) })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.az_app)) }, onClick = { aperto = null; onScegliApp(g) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onChiudi) { Text(stringResource(R.string.imp_fatto)) } },
    )
}

private fun freccia(v: Verso) = when (v) { Verso.Su -> "↑"; Verso.Destra -> "→"; Verso.Sinistra -> "←"; Verso.Giu -> "↓" }

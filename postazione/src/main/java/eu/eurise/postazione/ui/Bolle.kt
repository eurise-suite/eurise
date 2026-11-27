package eu.eurise.postazione.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.PrivacyConsentSheet
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.postazione.PostazioneModel
import eu.eurise.postazione.R
import eu.eurise.postazione.data.Ascolto
import eu.eurise.postazione.data.Avviso
import eu.eurise.postazione.data.Intervallo
import eu.eurise.postazione.data.Musica
import eu.eurise.postazione.data.Notizia
import eu.eurise.postazione.data.Scheda
import eu.eurise.postazione.data.Siti
import eu.eurise.postazione.data.Uso
import eu.eurise.postazione.data.Posto
import eu.eurise.postazione.data.Preferiti
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

/** Quello che sta suonando, con indietro, suona o pausa, avanti. C'è solo con l'accesso alle notifiche acceso. */
@Composable
fun SchedaMusica(model: PostazioneModel, col: Colori) {
    val b = model.brano ?: return
    val app = model.nomeDelPacchetto(b.pacchetto)
    Row(
        Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(18.dp)).padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(50.dp).background(Brush.linearGradient(listOf(Color(0xFFE07A5F), Color(0xFF8E3B46))), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.music(), null, Modifier.size(22.dp), tint = Color.White) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(b.titolo, color = col.inchiostro, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sotto = listOf(b.artista, if (b.suona) stringResource(R.string.musica_da, app) else stringResource(R.string.musica_pausa, app))
                .filter { it.isNotBlank() }.joinToString(" · ")
            Text(sotto, color = col.sotto, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(EuriseIcons.previous(), stringResource(R.string.indietro), Modifier.size(40.dp).clickable { model.musica(Musica.Comando.Indietro) }.padding(10.dp), tint = col.inchiostro)
        Box(
            Modifier.size(42.dp).background(col.lente, CircleShape).clickable { model.musica(Musica.Comando.SuonaPausa) },
            contentAlignment = Alignment.Center,
        ) { Icon(if (b.suona) EuriseIcons.pause() else EuriseIcons.play(), stringResource(R.string.suona), Modifier.size(20.dp), tint = Color.White) }
        Icon(EuriseIcons.next(), stringResource(R.string.avanti), Modifier.size(40.dp).clickable { model.musica(Musica.Comando.Avanti) }.padding(10.dp), tint = col.inchiostro)
    }
}

/** Una riga di «Da rispondere»: il tocco fa quello che farebbe il tocco sulla notifica. */
@Composable
fun RigaAvviso(model: PostazioneModel, a: Avviso, col: Colori) {
    val context = LocalContext.current
    val chiave = model.app.firstOrNull { it.pacchetto == a.pacchetto }?.chiave
    Row(
        Modifier.fillMaxWidth().clickable { if (!Ascolto.apri(context, a) && chiave != null) model.apri(chiave) }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chiave != null) IconaApp(model.telefono, chiave, 36.dp, col.notte)
        else Box(Modifier.size(36.dp).background(col.riquadro, CircleShape), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.chat(), null, Modifier.size(18.dp), tint = col.sotto)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row {
                Text(a.titolo, color = col.inchiostro, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(orario(a.quando), color = col.sotto, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Text(a.testo, color = col.sotto, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // La sera, un tocco manda «Ti rispondo domattina» con l'azione «Rispondi» della notifica: mai da sola.
        if (a.rispondi != null && (model.scheda == Scheda.Personale || model.adesso.hour >= 17)) {
            val testo = stringResource(R.string.domattina_testo)
            var mandata by remember(a.chiave) { mutableStateOf(false) }
            if (!mandata) {
                Text(
                    stringResource(R.string.domattina), color = col.accento, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClickLabel = stringResource(R.string.domattina_desc)) { mandata = Ascolto.rispondi(context, a, testo) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** «Lavoro in pausa fino alle 8:30 · 3 avvisi aspettano». */
@Composable
fun RigaPausa(model: PostazioneModel, col: Colori) {
    val attesa = model.inAttesa
    Row(
        Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EuriseIcons.bell(), null, Modifier.size(17.dp), tint = col.sotto)
        Text(
            stringResource(R.string.pausa_riga, orario(model.pausaFino)) + if (attesa > 0) pluralStringResource(R.plurals.pausa_attesa, attesa, attesa) else "",
            color = col.sotto, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Text(
            stringResource(R.string.pausa_fine), color = col.accento, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { model.finePausa() }.padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

/** Una notizia: il tocco la apre nel browser (solo indirizzi web). */
@Composable
fun RigaNotizia(model: PostazioneModel, n: Notizia, col: Colori) {
    val minuti = ((System.currentTimeMillis() - n.quando) / 60_000L).toInt().coerceAtLeast(1)
    val sotto = when {
        n.quando <= 0L -> n.fonte
        minuti < 60 -> stringResource(R.string.notizia_fa_min, n.fonte, minuti)
        minuti < 48 * 60 -> stringResource(R.string.notizia_fa_ore, n.fonte, minuti / 60)
        else -> n.fonte
    }
    Column(Modifier.fillMaxWidth().clickable { Siti.normalizza(n.link)?.let { model.telefono.apriSito(it) } }.padding(vertical = 2.dp)) {
        Text(n.titolo, color = col.inchiostro, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(sotto, color = col.sotto, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** «Dove hai lasciato l'auto»: un tocco segna il punto, «Portami lì» lo apre nelle mappe. */
@Composable
fun BollaAuto(model: PostazioneModel, col: Colori) {
    val context = LocalContext.current
    var chiedi by remember { mutableStateOf(false) }
    val permesso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.aggiorna() }
    if (!model.posizione) {
        RigaAccendi(stringResource(R.string.auto_spenta), col) { chiedi = true }
    } else {
        val auto = model.auto
        Row(
            Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.car(), null, Modifier.size(18.dp), tint = col.accento)
            if (auto == null) {
                val testo = when {
                    model.cercoAuto -> stringResource(R.string.auto_cerco)
                    model.autoNonTrovata -> stringResource(R.string.auto_niente)
                    else -> stringResource(R.string.auto_segna)
                }
                Text(
                    testo, color = if (model.cercoAuto || model.autoNonTrovata) col.sotto else col.accento, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).clickable(enabled = !model.cercoAuto) { model.segnaAuto() }.padding(horizontal = 10.dp, vertical = 12.dp),
                )
            } else {
                Text(stringResource(R.string.auto_lasciata, orario(auto.quando)), color = col.inchiostro, fontSize = 13.5.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text(
                    stringResource(R.string.auto_portami), color = col.accento, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        val punto = auto.lat.toString() + "," + auto.lon.toString()
                        model.telefono.avvia(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$punto?q=$punto")))
                    }.padding(horizontal = 10.dp, vertical = 12.dp),
                )
                Text(
                    stringResource(R.string.auto_dimentica), color = col.sotto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { model.dimenticaAuto() }.padding(horizontal = 10.dp, vertical = 12.dp),
                )
            }
        }
    }
    if (chiedi) {
        PrivacyConsentSheet(
            title = stringResource(R.string.auto_consenso),
            read = stringResource(R.string.auto_letto),
            out = stringResource(R.string.auto_esce),
            who = stringResource(R.string.auto_chi),
            back = stringResource(R.string.auto_indietro),
            score = 10,
            where = Jurisdiction.Phone,
            onDismiss = { chiedi = false },
            onAccept = {
                chiedi = false
                Uso.Posizione.accendi(context)
                if (Posto.concesso(context)) model.aggiorna()
                else permesso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
        )
    }
}

/** «Da rispondere» e musica sono spenti: una riga per accenderli, dopo la spiegazione. */
@Composable
fun AccendiAscolto(col: Colori, onAcceso: () -> Unit = {}) {
    val context = LocalContext.current
    var chiedi by remember { mutableStateOf(false) }
    RigaAccendi(stringResource(R.string.ascolto_spento), col) { chiedi = true }
    if (chiedi) {
        PrivacyConsentSheet(
            title = stringResource(R.string.ascolto_titolo),
            read = stringResource(R.string.ascolto_letto),
            out = stringResource(R.string.ascolto_esce),
            who = stringResource(R.string.ascolto_chi),
            back = stringResource(R.string.ascolto_indietro),
            score = 10,
            where = Jurisdiction.Phone,
            onDismiss = { chiedi = false },
            onAccept = {
                chiedi = false
                Uso.Notifiche.accendi(context)
                if (Ascolto.permesso(context)) onAcceso() else Ascolto.impostazioni(context)
            },
        )
    }
}

/** I preferiti della rubrica, grandi: il tocco apre il tastierino con il numero già scritto. */
@Composable
fun BollaPreferiti(model: PostazioneModel, col: Colori) {
    val context = LocalContext.current
    var chiedi by remember { mutableStateOf(false) }
    val permesso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.aggiorna() }
    if (!model.contatti) {
        RigaAccendi(stringResource(R.string.preferiti_spenti), col) { chiedi = true }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Intestazione(stringResource(R.string.preferiti), stringResource(R.string.contatti), col) {
                model.telefono.avvia(Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI))
            }
            if (model.preferiti.isEmpty()) {
                Text(stringResource(R.string.preferiti_vuoti), color = col.sotto, fontSize = 13.5.sp)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val tinte = listOf(Color(0xFFE2EAF6) to Color(0xFF284777), Color(0xFFE4EDE2) to Color(0xFF355330), Color(0xFFF2E6DC) to Color(0xFF74452A), Color(0xFFECE5F3) to Color(0xFF563C74))
                    model.preferiti.forEachIndexed { i, p ->
                        val (fondo, testo) = tinte[i % tinte.size]
                        Column(
                            Modifier.width(60.dp).clickable { model.telefono.avvia(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", p.numero, null))) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(52.dp).background(fondo, CircleShape), contentAlignment = Alignment.Center) {
                                Text(p.iniziali, color = testo, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(p.nome.substringBefore(' '), color = col.inchiostro, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
        }
    }
    if (chiedi) {
        PrivacyConsentSheet(
            title = stringResource(R.string.preferiti_titolo),
            read = stringResource(R.string.preferiti_letto),
            out = stringResource(R.string.preferiti_esce),
            who = stringResource(R.string.preferiti_chi),
            back = stringResource(R.string.preferiti_indietro),
            score = 10,
            where = Jurisdiction.Phone,
            onDismiss = { chiedi = false },
            onAccept = {
                chiedi = false
                Uso.Contatti.accendi(context)
                if (Preferiti.concesso(context)) model.aggiorna() else permesso.launch(Manifest.permission.READ_CONTACTS)
            },
        )
    }
}

@Composable
private fun RigaAccendi(testo: String, col: Colori, onAccendi: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(testo, color = col.sotto, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.accendi), color = col.accento, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onAccendi() }.padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

/** La scheda di fine giornata: le cose aperte passano a domani, il lavoro va in silenzio, si passa a Personale. */
@Composable
fun FineGiornata(model: PostazioneModel, col: Colori) {
    val aperte = model.giornata.aperte
    var rimanda by remember { mutableStateOf(true) }
    var silenzio by remember { mutableStateOf(true) }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(col.prossimo, RoundedCornerShape(18.dp))) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(col.lente, RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)))
        Column(Modifier.weight(1f).padding(start = 13.dp, end = 14.dp, top = 13.dp, bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.fine_titolo), color = col.accento, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.fine_stacca), color = col.inchiostro, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (aperte > 0) Spunta(pluralStringResource(R.plurals.fine_aperte, aperte, aperte), rimanda, col) { rimanda = !rimanda }
            else Text(stringResource(R.string.fine_tutto_fatto), color = col.sotto, fontSize = 13.5.sp)
            if (model.ascolto) Spunta(stringResource(R.string.fine_silenzio), silenzio, col) { silenzio = !silenzio }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1.5f).height(40.dp).background(col.lente, RoundedCornerShape(12.dp))
                        .clickable { model.chiudiGiornata(rimanda || aperte == 0, silenzio && model.ascolto) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.fine_chiudi), color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).height(40.dp).background(col.foglio, RoundedCornerShape(12.dp)).clickable { model.chiudiGiornata(false, false) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.fine_non_oggi), color = col.inchiostro, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun Spunta(testo: String, accesa: Boolean, col: Colori, onTocco: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onTocco() }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(20.dp).background(if (accesa) col.lente else Brush.linearGradient(listOf(col.riga, col.riga)), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { if (accesa) Icon(EuriseIcons.check(), null, Modifier.size(14.dp), tint = Color.White) }
        Text(testo, color = col.inchiostro, fontSize = 13.5.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

/** «11:00–11:50 libero: ci stanno 2 cose». */
@Composable
fun SpazioLibero(spazio: Intervallo, daAdesso: Boolean, cose: Int, col: Colori, onMetti: (() -> Unit)?) {
    val testo = (if (daAdesso) stringResource(R.string.libero_ora, orario(spazio.fine)) else stringResource(R.string.libero_tra, orario(spazio.inizio), orario(spazio.fine))) +
        if (cose > 0) pluralStringResource(R.plurals.libero_cose, cose, cose) else ""
    Row(
        Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EuriseIcons.clock(), null, Modifier.size(17.dp), tint = col.accento)
        Text(testo, color = col.inchiostro, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 10.dp, top = 10.dp, bottom = 10.dp))
        if (onMetti != null) {
            Text(
                stringResource(R.string.mettile_li), color = col.accento, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onMetti() }.padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * L'oroscopo di oggi (Personale): lo legge l'app Eurise con il servizio «Oroscopo» del centro privacy, spento di base.
 * Spento, la riga porta al centro privacy; il nome del segno si tocca per cambiarlo (lo stesso della Scrivania).
 */
@Composable
fun BollaOroscopo(model: PostazioneModel, col: Colori) {
    val o = model.oroscopo ?: return
    if (!o.acceso) {
        Row(
            Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.oroscopo_spento), color = col.sotto, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.centro_privacy), color = col.accento, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { model.apriSezione("privacy") }.padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
        return
    }
    var segni by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(18.dp))
            .clickable(enabled = o.link.isNotBlank()) { Siti.normalizza(o.link)?.let { model.telefono.apriSito(it) } }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.oroscopo), color = col.inchiostro, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box {
                Row(Modifier.clickable { segni = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(o.segni.getOrNull(o.segno).orEmpty(), color = col.accento, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Icon(EuriseIcons.chevronDown(), stringResource(R.string.oroscopo_cambia), Modifier.size(14.dp), tint = col.accento)
                }
                DropdownMenu(segni, onDismissRequest = { segni = false }) {
                    o.segni.forEachIndexed { i, nome ->
                        DropdownMenuItem(text = { Text(nome, fontWeight = if (i == o.segno) FontWeight.Bold else FontWeight.Normal) }, onClick = { segni = false; model.cambiaSegno(i) })
                    }
                }
            }
        }
        if (o.testo.isBlank()) Text(stringResource(R.string.oroscopo_vuoto), color = col.sotto, fontSize = 13.sp)
        else {
            Text(o.testo, color = col.inchiostro, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            if (o.fonte.isNotBlank()) Text(o.fonte, color = col.sotto, fontSize = 12.sp)
        }
    }
}

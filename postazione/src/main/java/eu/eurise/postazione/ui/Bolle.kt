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
        if (chiave != null) IconaApp(model.telefono, chiave, 36.dp)
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
    }
}

/** «Da rispondere» e musica sono spenti: una riga per accenderli, dopo la spiegazione. */
@Composable
fun AccendiAscolto(col: Colori) {
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
            onAccept = { chiedi = false; Ascolto.impostazioni(context) },
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
            onAccept = { chiedi = false; permesso.launch(Manifest.permission.READ_CONTACTS) },
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

/** La scheda di fine giornata: le cose aperte passano a domani e si passa a Personale. */
@Composable
fun FineGiornata(model: PostazioneModel, col: Colori) {
    val aperte = model.giornata.aperte
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(col.prossimo, RoundedCornerShape(18.dp))) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(col.lente, RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)))
        Column(Modifier.weight(1f).padding(start = 13.dp, end = 14.dp, top = 13.dp, bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.fine_titolo), color = col.accento, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.fine_stacca), color = col.inchiostro, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                if (aperte > 0) pluralStringResource(R.plurals.fine_aperte, aperte, aperte) else stringResource(R.string.fine_tutto_fatto),
                color = col.sotto, fontSize = 13.5.sp,
            )
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1.5f).height(40.dp).background(col.lente, RoundedCornerShape(12.dp)).clickable { model.chiudiGiornata(true) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.fine_chiudi), color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).height(40.dp).background(col.foglio, RoundedCornerShape(12.dp)).clickable { model.chiudiGiornata(false) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.fine_non_oggi), color = col.inchiostro, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** «11:00–11:50 libero: ci stanno 2 cose». */
@Composable
fun SpazioLibero(spazio: Intervallo, daAdesso: Boolean, cose: Int, col: Colori, onApri: () -> Unit) {
    val testo = (if (daAdesso) stringResource(R.string.libero_ora, orario(spazio.fine)) else stringResource(R.string.libero_tra, orario(spazio.inizio), orario(spazio.fine))) +
        if (cose > 0) pluralStringResource(R.plurals.libero_cose, cose, cose) else ""
    Row(
        Modifier.fillMaxWidth().background(col.riquadro, RoundedCornerShape(12.dp)).clickable { onApri() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EuriseIcons.clock(), null, Modifier.size(17.dp), tint = col.accento)
        Text(testo, color = col.inchiostro, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
    }
}

package eu.eurise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.R

/**
 * Dove sta chi riceve i dati: si mostra con un simbolo **accanto** al semaforo, e pesa sul punteggio solo di un punto.
 * UE: GDPR. Svizzera e Regno Unito: leggi riconosciute equivalenti dall'UE. USA: le autorità possono chiedere i dati
 * anche se stanno in Europa (Patriot Act, CLOUD Act, FISA 702).
 */
enum class Jurisdiction(val adjust: Int) {
    /** Nulla esce: il dato resta sul telefono. */
    Phone(0),
    /** Un server tuo (Nextcloud, WebDAV): lo controlli tu. */
    Own(3),
    EU(1),
    CH(1),
    UK(0),
    US(-1),
    /** Siti diversi scelti da te (notizie, oroscopo): dipende dal sito. */
    Various(0),
    Other(-1),
}

/** Cosa ricevono, in concreto: è questo che decide il punteggio. */
enum class DataOut(val base: Int) {
    /** Niente: tutto sul telefono. */
    None(10),
    /** Un file o un elenco uguale per tutti: il servizio vede solo l'indirizzo di rete. */
    Anonymous(8),
    /** Il minimo, senza sapere chi sei: posizione arrotondata, parole cercate, titoli seguiti. */
    Minimal(6),
    /** I tuoi contenuti in chiaro presso il servizio (file, conversazioni conservate). */
    Personal(4),
    /** Legati a te e usati per la pubblicità o per profilarti. */
    Profiled(1),
}

/**
 * Il grado di privacy da 1 a 10 (regola del 13 novembre: 10 tacchette, ovunque). Decide [out], ciò che il servizio
 * riceve davvero; la cifratura da capo a capo vale +4 (il servizio tiene i dati ma non li legge); [where] sposta di un
 * punto (tre per un server tuo). I servizi chiusi (Google, Microsoft, Apple, le AI che conservano tutto) restano a una
 * o due tacchette qualunque cosa ricevano (regola del 12 novembre).
 */
fun privacyScore(out: DataOut, where: Jurisdiction, endToEnd: Boolean = false, closed: Boolean = false, hiddenIp: Int = 0): Int {
    if (out == DataOut.None) return 10
    // Indirizzo di rete nascosto (VPN +1, Advanced Privacy con Tor +2): conta solo dove l'indirizzo era l'unica traccia,
    // cioè per i servizi che ricevono il minimo o nulla di tuo. Non per i contenuti tuoi né per i servizi chiusi, che ti
    // riconoscono dall'account.
    val ip = if (!closed && (out == DataOut.Anonymous || out == DataOut.Minimal)) hiddenIp else 0
    val score = (out.base + (if (endToEnd) 4 else 0) + where.adjust + ip).coerceIn(1, 9)
    return if (closed) score.coerceAtMost(2) else score
}

/** Le tre fasce del semaforo: verde 8–10, giallo 5–7, rosso 1–4. */
enum class PrivacyBand { Good, Fair, Poor }

fun privacyBand(score: Int): PrivacyBand = when {
    score >= 8 -> PrivacyBand.Good
    score >= 5 -> PrivacyBand.Fair
    else -> PrivacyBand.Poor
}

/** Il colore della fascia, più chiaro sul tema scuro Notte. */
@Composable
fun privacyColor(score: Int): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    return when (privacyBand(score)) {
        PrivacyBand.Good -> if (dark) Color(0xFF7FC39A) else Color(0xFF3F6E52)
        PrivacyBand.Fair -> if (dark) Color(0xFFE2B95C) else Color(0xFF8A6A1F)
        PrivacyBand.Poor -> if (dark) Color(0xFFE88A93) else Color(0xFF8C2F39)
    }
}

/**
 * Il semaforo della privacy: dieci tacchette piccole, accese fino a [score] nel colore della fascia, e accanto il
 * simbolo di [where] (UE, CH, USA…). Per lo schermo letto ad alta voce: «Privacy 7 su 10, Unione europea».
 */
@Composable
fun PrivacyMeter(score: Int, where: Jurisdiction? = null, modifier: Modifier = Modifier, boost: Int = 0, boostLabel: String? = null) {
    val s = score.coerceIn(1, 10)
    val gained = boost.coerceIn(0, s - 1)
    val color = privacyColor(s)
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val said = stringResource(R.string.ds_privacy_score, s) + (where?.let { ", " + stringResource(it.longName()) }.orEmpty()) +
        (if (gained > 0 && boostLabel != null) ", $boostLabel" else "")
    Row(
        modifier.clearAndSetSemantics { contentDescription = said },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(10) { i ->
                // Le tacchette guadagnate con la rete nascosta (VPN, Tor) sono più chiare: si vede quanto valgono.
                val tint = when {
                    i >= s -> off
                    i >= s - gained -> color.copy(alpha = 0.45f)
                    else -> color
                }
                Box(Modifier.width(3.dp).height(10.dp).clip(RoundedCornerShape(1.5.dp)).background(tint))
            }
        }
        if (where != null && where != Jurisdiction.Phone) JurisdictionBadge(where)
        if (gained > 0 && boostLabel != null) Text(boostLabel, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

/** Il simbolo del paese: una sigla in una pillola; l'UE in blu con le lettere oro. */
@Composable
fun JurisdictionBadge(where: Jurisdiction) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (where) {
        Jurisdiction.EU -> Color(0xFF1F3C88) to Color(0xFFF4CE45)
        Jurisdiction.Own -> scheme.primary.copy(alpha = 0.14f) to scheme.primary
        else -> scheme.onSurface.copy(alpha = 0.08f) to scheme.onSurfaceVariant
    }
    Text(
        stringResource(where.shortName()),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(bg).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private fun Jurisdiction.shortName() = when (this) {
    Jurisdiction.Phone -> R.string.ds_where_phone
    Jurisdiction.Own -> R.string.ds_where_own
    Jurisdiction.EU -> R.string.ds_where_eu
    Jurisdiction.CH -> R.string.ds_where_ch
    Jurisdiction.UK -> R.string.ds_where_uk
    Jurisdiction.US -> R.string.ds_where_us
    Jurisdiction.Various -> R.string.ds_where_various
    Jurisdiction.Other -> R.string.ds_where_other
}

internal fun Jurisdiction.longName() = when (this) {
    Jurisdiction.Phone -> R.string.ds_where_phone_long
    Jurisdiction.Own -> R.string.ds_where_own_long
    Jurisdiction.EU -> R.string.ds_where_eu_long
    Jurisdiction.CH -> R.string.ds_where_ch_long
    Jurisdiction.UK -> R.string.ds_where_uk_long
    Jurisdiction.US -> R.string.ds_where_us_long
    Jurisdiction.Various -> R.string.ds_where_various_long
    Jurisdiction.Other -> R.string.ds_where_other_long
}

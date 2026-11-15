package app.organicmaps.eurise.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.organicmaps.R
import app.organicmaps.eurise.EuriseReportActivity
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.bookmarks.data.Metadata
import app.organicmaps.search.SearchActivity
import app.organicmaps.util.ThemeUtils
import java.util.Locale

/**
 * La scheda del luogo aperta (tavola MapLuogoAperta, approvata): sotto gli orari di CoMaps, «Contatti e indirizzo»
 * (indirizzo e coordinate da copiare con una pressione lunga, «Aggiungi alla Rubrica»), «Nella suite» (nuovo impegno qui,
 * nota del luogo in Note) e «Altro» (cerca qui vicino, correggi su OpenStreetMap). Telefono, sito e social restano le
 * righe di CoMaps, già nei colori della suite. Aprire la scheda non manda niente in rete.
 */
object EuriseLuogoAperta {
    private val HUBS = arrayOf("eu.eurise.hub.debug", "eu.eurise.hub")

    @JvmStatic
    fun attach(view: View?) {
        val compose = view as? ComposeView ?: return
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent {
            val place = EuriseLuogo.state.value
            if (place != null && !place.isMyPosition && !place.isTrack) Open(ThemeUtils.isNightTheme(), place)
        }
    }

    internal fun coords(place: MapObject) = String.format(Locale.ROOT, "%.5f, %.5f", place.lat, place.lon)

    internal fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(text, text))
        Toast.makeText(context, R.string.eurise_lo_copied, Toast.LENGTH_SHORT).show()
    }

    /** Nuovo contatto con nome, telefono, indirizzo e posizione (nelle note): si apre la Rubrica del telefono per confermare. */
    internal fun addContact(context: Context, place: MapObject) {
        val i = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.NAME, place.title)
            .putExtra(ContactsContract.Intents.Insert.POSTAL, place.address)
            .putExtra(ContactsContract.Intents.Insert.NOTES, "geo:" + coords(place).replace(" ", ""))
        EuriseLuogo.phone(place)?.let { i.putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        runCatching { context.startActivity(i) }
    }

    /** La nota del luogo in Eurise Note («Condividi › Note» dell'app Eurise): nome, indirizzo, coordinate e link. */
    internal fun note(context: Context, place: MapObject) {
        val lat = String.format(Locale.ROOT, "%.6f", place.lat)
        val lon = String.format(Locale.ROOT, "%.6f", place.lon)
        val text = listOf(place.title, place.address, "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=18/$lat/$lon")
            .filter { !it.isNullOrBlank() }.joinToString("\n")
        for (hub in HUBS) {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                .setComponent(ComponentName(hub, "eu.eurise.hub.ShareToNotesActivity"))
            if (runCatching { context.startActivity(i) }.isSuccess) return
        }
        Toast.makeText(context, R.string.eurise_lo_no_notes, Toast.LENGTH_SHORT).show()
    }

    /** «Cerca qui vicino»: la mappa si centra sul luogo e si apre la ricerca (le categorie cercano attorno al centro). */
    internal fun searchNear(context: Context, place: MapObject) {
        val activity = context.activity() as? Activity ?: return
        runCatching { Framework.nativeSetViewportCenter(place.lat, place.lon, 16) }
        runCatching { SearchActivity.start(activity, "") }
    }

    internal fun fix(context: Context, place: MapObject) {
        val activity = context.activity() as? Activity ?: return
        EuriseReportActivity.open(activity, place.lat, place.lon, place.title)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Open(night: Boolean, place: MapObject) {
    val c = if (night) Notte else Perla
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)) {
        Heading(c, stringResource(R.string.eurise_lo_contacts))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card)) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                val address = place.address?.takeIf { it.isNotBlank() }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .combinedClickable(onClick = {}, onLongClick = {
                            EuriseLuogoAperta.copy(context, listOfNotNull(address, EuriseLuogoAperta.coords(place)).joinToString("\n"))
                        }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        BasicText(address ?: EuriseLuogoAperta.coords(place),
                            style = TextStyle(color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                        BasicText(stringResource(R.string.eurise_lo_copy_hint, EuriseLuogoAperta.coords(place)),
                            style = TextStyle(color = c.muted, fontSize = 12.sp))
                    }
                }
                Divider(c)
                Line(c, stringResource(R.string.eurise_lo_contact), stringResource(R.string.eurise_lo_contact_sub)) {
                    EuriseLuogoAperta.addContact(context, place)
                }
            }
        }
        Heading(c, stringResource(R.string.eurise_lo_suite))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card)) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Line(c, stringResource(R.string.eurise_lo_event), stringResource(R.string.eurise_lo_event_sub)) {
                    EuriseLuogo.event(context, place)
                }
                Divider(c)
                Line(c, stringResource(R.string.eurise_lo_note), stringResource(R.string.eurise_lo_note_sub)) {
                    EuriseLuogoAperta.note(context, place)
                }
            }
        }
        Heading(c, stringResource(R.string.eurise_lo_more))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(c, stringResource(R.string.eurise_lo_near), stringResource(R.string.eurise_lo_near_sub), Modifier.weight(1f)) {
                EuriseLuogoAperta.searchNear(context, place)
            }
            Tile(c, stringResource(R.string.eurise_lo_fix), stringResource(R.string.eurise_lo_fix_sub), Modifier.weight(1f)) {
                EuriseLuogoAperta.fix(context, place)
            }
        }
        BasicText(stringResource(R.string.eurise_lo_foot), Modifier.padding(start = 4.dp, top = 12.dp),
            style = TextStyle(color = c.muted, fontSize = 11.5.sp, lineHeight = 17.sp))
    }
}

@Composable
private fun Heading(c: Colors, text: String) {
    BasicText(text.uppercase(), Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        style = TextStyle(color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp))
}

@Composable
private fun Divider(c: Colors) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.ink.copy(alpha = 0.07f)))
}

@Composable
private fun Line(c: Colors, title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).semantics { role = Role.Button }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(title, style = TextStyle(color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        BasicText(sub, style = TextStyle(color = c.muted, fontSize = 12.sp))
    }
}

@Composable
private fun Tile(c: Colors, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(c.card).clickable(onClick = onClick)
            .semantics { role = Role.Button }.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(title, maxLines = 1, style = TextStyle(color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        BasicText(sub, maxLines = 1, style = TextStyle(color = c.muted, fontSize = 11.5.sp))
    }
}

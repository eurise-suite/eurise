package eu.eurise.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/** L'azzurro delle discussioni (tavola ChatCartelle). */
private val THREAD = Color(0xFF3E5C8A)

/** In cima alla discussione: indietro, il titolo (prima riga del messaggio di partenza), «discussione in «Casa al mare» · 6 risposte». */
@Composable
internal fun ThreadHeader(model: ChatModel, roomName: String, root: Event?, count: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.ch_back), flat = true) { model.thread = null }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(root?.let { Threads.title(shownText(model, it)) }?.ifBlank { null } ?: stringResource(R.string.th_title),
                fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.th_sub, roomName, pluralStringResource(R.plurals.th_replies, count, count)),
                fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Il messaggio di partenza, in una scheda: «Luca · messaggio di partenza». */
@Composable
internal fun ThreadRootCard(model: ChatModel, root: Event?) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (root == null) {
            Text(stringResource(R.string.th_loading), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            return@Column
        }
        Text(stringResource(R.string.th_root_by, model.nameOf(root.sender)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        Text(shownText(model, root), fontSize = 14.5.sp, lineHeight = 20.sp)
    }
}

/** Sotto le risposte: «Le risposte restano qui: il gruppo vede solo «6 risposte»». */
@Composable
internal fun ThreadHint(count: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.th_hint, pluralStringResource(R.plurals.th_replies, count, count)), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(scheme.onSurface.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Sotto il messaggio di partenza nella conversazione del gruppo: «6 risposte · 10:11», tocco = apri la discussione. */
@Composable
internal fun ThreadChip(info: Threads.Info, mine: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(THREAD.copy(alpha = 0.12f)).clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.chat(2.2f), null, tint = THREAD, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(pluralStringResource(R.plurals.th_replies, info.count, info.count) + " · " + shortTime(info.last),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = THREAD)
        }
    }
}

/** Nell'elenco delle chat, sotto l'anteprima: «2 discussioni · Turni pulizie (6), Bollette (3)». */
@Composable
internal fun RoomThreadsLine(model: ChatModel, roomId: String) {
    val list = Threads.summary(model.timeline(roomId))
    if (list.isEmpty()) return
    val names = list.take(2).joinToString(", ") { "${it.title.ifBlank { "…" }} (${it.count})" }
    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(EuriseIcons.chat(2.2f), null, tint = THREAD, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(pluralStringResource(R.plurals.th_threads, list.size, list.size) + " · " + names, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
            color = THREAD, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(0.dp))
}

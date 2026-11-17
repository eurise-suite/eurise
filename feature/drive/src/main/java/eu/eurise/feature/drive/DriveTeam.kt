package eu.eurise.feature.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.cloud.DavActivity
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * In cima a una cartella condivisa (tavola CollSquadra): le persone, chi modifica e chi vede, «Gestisci» (lo stesso
 * «Condividi con…»), e «Cambiato da ieri» con le frasi del server (app Attività di Nextcloud). Tocca un cambio e si apre il file.
 */
@Composable
internal fun TeamCard(team: Team, onManage: () -> Unit, onOpenPath: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).clickable(onClick = onManage)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row {
                team.people.take(4).forEachIndexed { i, p -> Box(Modifier.offset(x = (-8 * i).dp)) { Initial(p.name) } }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.drive_team_people, team.people.size), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val edit = team.people.filter { it.edit }.map { it.name.substringBefore(' ') }
                val view = team.people.filter { !it.edit }.map { it.name.substringBefore(' ') }
                val line = listOfNotNull(
                    edit.takeIf { it.isNotEmpty() }?.let { stringResource(if (it.size == 1) R.string.drive_team_edit_one else R.string.drive_team_edit, names(it)) },
                    view.takeIf { it.isNotEmpty() }?.let { stringResource(if (it.size == 1) R.string.drive_team_view_one else R.string.drive_team_view, names(it)) },
                ).joinToString(" · ")
                Text(line, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(stringResource(R.string.drive_team_manage), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
        }
        if (team.changes.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow)) {
                Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(EuriseIcons.history(), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.drive_team_changes, team.changes.size), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                team.changes.take(8).forEachIndexed { i, c ->
                    if (i > 0) Box(Modifier.padding(horizontal = 14.dp)) { CardDivider() }
                    ChangeRow(c) { onOpenPath(c.path) }
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(change: DavActivity, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = change.type != "file_deleted", onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Initial(change.user.ifBlank { "?" })
        Column(Modifier.weight(1f)) {
            Text(change.subject, fontSize = 13.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(whenText(change.at), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun names(list: List<String>): String =
    if (list.size == 1) list[0] else list.dropLast(1).joinToString(", ") + stringResource(R.string.drive_team_and) + list.last()

@Composable
private fun whenText(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val time = t.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (t.toLocalDate()) {
        LocalDate.now() -> stringResource(R.string.drive_team_today, time)
        LocalDate.now().minusDays(1) -> stringResource(R.string.drive_team_yesterday, time)
        else -> t.format(DateTimeFormatter.ofPattern("d MMM HH:mm", java.util.Locale.ITALIAN))
    }
}

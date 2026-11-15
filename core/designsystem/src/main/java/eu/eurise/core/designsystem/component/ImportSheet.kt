package eu.eurise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/** Una riga da importare: titolo e riga sotto. */
data class ImportRow(val title: String, val sub: String)

/**
 * La schermata comune di «Apri con» per i file da importare (.ics nel Calendario, .vcf nei Contatti, 15 novembre):
 * cosa c'è nel file, una spunta per riga, la destinazione ([target]) e il pulsante principale in basso, sotto il pollice.
 */
@Composable
fun ImportScreen(
    icon: ImageVector,
    title: String,
    subtitle: String,
    rows: List<ImportRow>,
    chosen: Set<Int>,
    onToggle: (Int) -> Unit,
    action: String,
    busy: Boolean,
    onAction: () -> Unit,
    onClose: () -> Unit,
    target: @Composable ColumnScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.close(), null, tint = scheme.onSurface)
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(36.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 2)
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(rows) { i, r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface)
                        .clickable(role = Role.Checkbox) { onToggle(i) }.padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = i in chosen, onCheckedChange = { onToggle(i) })
                    Column(Modifier.weight(1f)) {
                        Text(r.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (r.sub.isNotBlank()) Text(r.sub, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            target()
            Text(
                action, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(if (chosen.isEmpty() || busy) scheme.primary.copy(alpha = 0.4f) else scheme.primary)
                    .clickable(enabled = chosen.isNotEmpty() && !busy, role = Role.Button, onClick = onAction)
                    .padding(vertical = 15.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

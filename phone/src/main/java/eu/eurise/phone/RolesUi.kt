@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package eu.eurise.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.insieme.Ruoli
import eu.eurise.phone.data.Roles

@Composable
private fun RoleChip(label: String, on: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) scheme.onPrimary else scheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

/**
 * «Lavorare insieme» nella scheda di un contatto (tavola InsRuoli): chi è per te (capo, segreteria, collaboratore, collega,
 * famiglia, amici) e cosa vede di base quando condividi. Resta sul telefono; Chat ed Email lo usano per le proposte.
 */
@Composable
internal fun RoleSection(key: String, name: String, addresses: List<String>) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var person by remember(key) { mutableStateOf(Roles.load(context).let { l -> l.firstOrNull { it.key == key } ?: addresses.firstNotNullOfOrNull { Ruoli.find(l, it) } }) }
    fun save(role: Ruoli.Role?, level: Ruoli.Level?) {
        Roles.set(context, key, name, role, level, addresses)
        person = Roles.load(context).firstOrNull { it.key == key }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(eu.eurise.core.insieme.R.string.ins_roles_title), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(eu.eurise.core.insieme.R.string.ins_roles_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Ruoli.Role.entries.forEach { r ->
                RoleChip(stringResource(Ruoli.label(r)), person?.role == r) { save(if (person?.role == r) null else r, null) }
            }
        }
        person?.let { p ->
            Text(stringResource(eu.eurise.core.insieme.R.string.ins_roles_sees), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ruoli.Level.entries.forEach { l -> RoleChip(stringResource(Ruoli.label(l)), p.shown == l) { save(p.role, l) } }
            }
        }
    }
}

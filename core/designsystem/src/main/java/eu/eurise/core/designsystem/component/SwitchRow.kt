package eu.eurise.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Riga con interruttore delle impostazioni, uguale in tutte le app: tutta la riga si tocca.
 *
 * Con [hint] (o con [strong]) il titolo è in grassetto e la spiegazione sta sotto, più piccola; senza, è una riga semplice
 * di un elenco (fonti, canali, liste). [trailing] sta tra il testo e l'interruttore. Con [enabled] spento la riga resta
 * visibile ma non si tocca.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    hint: String? = null,
    strong: Boolean = hint != null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (strong) 56.dp else 48.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else muted,
            )
            if (!hint.isNullOrBlank()) Text(hint, fontSize = 12.5.sp, color = muted)
        }
        trailing?.invoke()
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

package eu.eurise.office.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.eurise.office.R
import eu.eurise.office.data.Appearance

/**
 * Accento delle icone di Office: «Come la Scrivania» (di base) oppure una delle tinte della suite. Il tratto delle icone
 * resta blu Notte; cambia il colore dei dettagli.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentChooser(tints: List<Appearance.Tint>, chosen: String, onChoose: (String) -> Unit) {
    val dark = isSystemInDarkTheme()
    Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.office_accent), fontWeight = FontWeight.SemiBold)
        val desk = chosen == Appearance.DESK
        Text(
            stringResource(R.string.office_accent_desk),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (desk) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .clickable(role = Role.RadioButton) { onChoose(Appearance.DESK) }
                .semantics { selected = desk }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            fontWeight = if (desk) FontWeight.Bold else FontWeight.Normal,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tints.forEach { tint ->
                val on = tint.name == chosen
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(if (dark) tint.dark else tint.light))
                        .border(if (on) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable(role = Role.RadioButton) { onChoose(tint.name) }
                        .semantics { contentDescription = tint.label; selected = on },
                )
            }
        }
    }
}

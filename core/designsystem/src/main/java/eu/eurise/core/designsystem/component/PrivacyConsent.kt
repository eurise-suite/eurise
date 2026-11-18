package eu.eurise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.R

/**
 * «Prima di accendere» per un servizio di un'app separata (Password, Galleria…), che non sta nell'elenco del centro
 * privacy di questa app: stesso aspetto di [ServiceConsentSheet], ma i testi li dà chi chiama. [read] cosa viene letto,
 * [out] cosa esce, [who] chi lo vede, [back] come si torna indietro; [score] e [where] la privacy con il servizio acceso.
 * Nessuna scelta viene salvata qui: «Accendi» chiama [onAccept].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyConsentSheet(
    title: String,
    read: String,
    out: String,
    who: String,
    back: String,
    score: Int,
    where: Jurisdiction,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = scheme.background) {
        Column(Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ds_on_title).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.secondary)
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                Line(stringResource(R.string.ds_pc_read), read)
                HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                Line(stringResource(R.string.ds_pc_out), out)
                HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                Line(stringResource(R.string.ds_on_who), who)
                HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                Line(stringResource(R.string.ds_on_back), back)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.ds_on_privacy), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrivacyMeter(10, Jurisdiction.Phone)
                    Text("→", fontSize = 14.sp, color = scheme.onSurfaceVariant)
                    PrivacyMeter(score, where)
                }
                Text(stringResource(R.string.ds_on_privacy_d, score), fontSize = 13.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
            }
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(stringResource(R.string.ds_on_ok), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                Text(stringResource(R.string.ds_on_no), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.5.sp, lineHeight = 19.sp)
    }
}

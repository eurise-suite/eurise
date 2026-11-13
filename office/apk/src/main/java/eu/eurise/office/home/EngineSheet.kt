package eu.eurise.office.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.office.R
import eu.eurise.office.data.EngineState

private val Ink = Color(0xFF1F2A44)
private val Green = Color(0xFF4F6B5A)

/**
 * Tavola OffMotore (approvata): il motore dei documenti si scarica una volta sola, con il Wi-Fi, dall'archivio delle
 * versioni, firmato come l'app. Con la strada A è l'APK «Eurise Office Motore»: dopo il download Android chiede di
 * confermare l'installazione. Stati non disegnati sulla tavola (rete a consumo, avanzamento, errore, pronto) restano
 * nello stesso foglio, con gli stessi due pulsanti.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSheet(
    state: EngineState,
    onWifi: Boolean,
    hasPending: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenPending: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text(stringResource(R.string.engine_once).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = Green)
            Text(
                stringResource(if (state == EngineState.Ready) R.string.engine_ready_title else R.string.engine_title),
                fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp,
            )
            if (state != EngineState.Ready) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp)) {
                    Fact(R.string.engine_what_label, R.string.engine_what, first = true)
                    Fact(R.string.engine_size_label, R.string.engine_size)
                    Fact(R.string.engine_from_label, R.string.engine_from)
                    Fact(R.string.engine_docs_label, R.string.engine_docs)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3) { Box(Modifier.width(18.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Green)) }
                    }
                    Text(stringResource(R.string.engine_privacy), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Green)
                }
            }
            val note = when (state) {
                is EngineState.Downloading -> null
                EngineState.Installing -> R.string.engine_confirm
                EngineState.Ready -> if (hasPending) R.string.engine_ready_pending else R.string.engine_ready
                is EngineState.Failed -> when (state.reason) {
                    EngineState.Reason.Network -> R.string.engine_failed_network
                    EngineState.Reason.Signature -> R.string.engine_failed_signature
                    EngineState.Reason.Install -> R.string.engine_failed_install
                    EngineState.Reason.Cancelled -> R.string.engine_failed_cancelled
                }
                EngineState.Missing -> if (onWifi) R.string.engine_meanwhile else R.string.engine_mobile
            }
            if (state is EngineState.Downloading) {
                val fraction = if (state.total > 0) state.done.toFloat() / state.total else 0f
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.total > 0) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = Ink)
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Ink)
                    Text(
                        stringResource(R.string.engine_downloading, state.done / 1_048_576, state.total / 1_048_576),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            note?.let { Text(stringResource(it), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            val (primary, primaryAction) = when (state) {
                EngineState.Missing, is EngineState.Failed ->
                    (if (onWifi) R.string.engine_download_wifi else R.string.engine_download_anyway) to onDownload
                is EngineState.Downloading, EngineState.Installing -> null to {}
                EngineState.Ready -> (if (hasPending) R.string.engine_open_pending else R.string.office_ok) to (if (hasPending) onOpenPending else onDismiss)
            }
            primary?.let {
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(CircleShape).background(Ink)
                        .clickable(role = Role.Button, onClick = primaryAction),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(it), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
            if (state != EngineState.Ready) {
                val downloading = state is EngineState.Downloading
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClick = if (downloading) onCancel else onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(if (downloading) R.string.engine_cancel else R.string.engine_later),
                        fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Fact(label: Int, text: Int, first: Boolean = false) {
    if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(text), fontSize = 14.5.sp)
    }
}

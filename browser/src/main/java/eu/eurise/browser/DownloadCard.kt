package eu.eurise.browser

import android.net.Uri
import android.text.format.Formatter
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/**
 * Lo stato del download mostrato nel riquadro in basso (richiesta del proprietario, 15 novembre: il nome cambiava
 * durante lo scaricamento e alla fine non c'era niente da toccare per aprire il file).
 * [uri] è null finché il file non è pronto.
 */
internal data class DownloadState(
    val id: Long,
    val name: String,
    val mime: String,
    val size: Long?,
    val written: Long = 0,
    val uri: Uri? = null,
    val failed: Boolean = false,
)

/**
 * Riquadro del download: durante, nome definitivo e avanzamento; alla fine, «Apri» (tutto il riquadro si tocca) e
 * «Salva in…» di Eurise File. Resta finché lo si chiude o si apre il file.
 */
@Composable
internal fun DownloadCard(
    state: DownloadState,
    onOpen: () -> Unit,
    onSaveIn: () -> Unit,
    onShowAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val done = state.uri != null
    val sub = when {
        state.failed -> stringResource(R.string.brw_download_failed)
        done -> stringResource(R.string.brw_dl_ready, Formatter.formatShortFileSize(context, state.written))
        state.size != null -> stringResource(
            R.string.brw_dl_progress,
            Formatter.formatShortFileSize(context, state.written), Formatter.formatShortFileSize(context, state.size),
        )
        else -> stringResource(R.string.brw_dl_progress_unknown, Formatter.formatShortFileSize(context, state.written))
    }
    Column(
        modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(scheme.surface)
            .clickable(enabled = done, role = Role.Button, onClick = onOpen).padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(if (done) scheme.primary else scheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(if (done) EuriseIcons.check else EuriseIcons.download, null, tint = if (done) scheme.onPrimary else scheme.primary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 12.sp, color = if (state.failed) scheme.error else scheme.onSurfaceVariant, maxLines = 1)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.close, stringResource(R.string.brw_login_close), tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        if (!done && !state.failed) {
            Spacer(Modifier.height(10.dp))
            val size = state.size
            if (size != null) {
                LinearProgressIndicator(progress = { (state.written.toFloat() / size).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(end = 6.dp))
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 6.dp))
            }
        }
        if (done) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.brw_dl_open), primary = true, onClick = onOpen)
                Pill(stringResource(R.string.brw_dl_save_in), primary = false, onClick = onSaveIn)
                Pill(stringResource(R.string.brw_dl_all), primary = false, onClick = onShowAll)
            }
        }
    }
}

@Composable
private fun Pill(label: String, primary: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (primary) scheme.onPrimary else scheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (primary) scheme.primary else scheme.primary.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

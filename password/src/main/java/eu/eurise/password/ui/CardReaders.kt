package eu.eurise.password.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.suite.CardContract
import eu.eurise.password.R
import eu.eurise.password.data.CardNfc

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * I due modi per riempire una carta senza scriverla: con la fotocamera (Eurise Leggi la legge sul telefono e
 * restituisce i campi) o avvicinandola, se è contactless (numero e scadenza dal chip). [onRead] riceve numero,
 * scadenza e intestatario; i campi vuoti non cancellano quelli già scritti.
 */
@Composable
internal fun CardReaders(onRead: (String, String, String) -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.activity() }
    var nfcOpen by remember { mutableStateOf(false) }
    var missingRead by remember { mutableStateOf(false) }
    val debug = context.packageName.endsWith(".debug")
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            onRead(data.getStringExtra(CardContract.EXTRA_NUMBER).orEmpty(), data.getStringExtra(CardContract.EXTRA_EXPIRY).orEmpty(), data.getStringExtra(CardContract.EXTRA_HOLDER).orEmpty())
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReadButton(EuriseIcons.camera(), stringResource(R.string.pwd_card_read_camera), Modifier.weight(1f)) {
            val intent = Intent(CardContract.ACTION_SCAN_CARD).setPackage(CardContract.readPackage(debug))
            if (intent.resolveActivity(context.packageManager) != null) camera.launch(intent) else missingRead = true
        }
        if (activity != null && CardNfc.available(activity)) {
            ReadButton(EuriseIcons.contactless(), stringResource(R.string.pwd_card_read_nfc), Modifier.weight(1f)) { nfcOpen = true }
        }
    }
    if (nfcOpen && activity != null) NfcSheet(activity, onClose = { nfcOpen = false }) { number, expiry, holder -> nfcOpen = false; onRead(number, expiry, holder) }
    if (missingRead) AlertDialog(
        onDismissRequest = { missingRead = false },
        title = { Text(stringResource(R.string.pwd_card_read_missing_title)) },
        text = { Text(stringResource(R.string.pwd_card_read_missing)) },
        confirmButton = { TextButton(onClick = { missingRead = false }) { Text(stringResource(R.string.pwd_card_read_ok)) } },
    )
}

@Composable
private fun ReadButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(scheme.secondaryContainer).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = scheme.onSecondaryContainer, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NfcSheet(activity: Activity, onClose: () -> Unit, onCard: (String, String, String) -> Unit) {
    var failed by remember { mutableStateOf(false) }
    val enabled = remember { CardNfc.enabled(activity) }
    DisposableEffect(activity) {
        if (enabled) CardNfc.start(activity) { card ->
            activity.runOnUiThread {
                if (card != null) onCard(card.number, card.expiry, card.holder) else failed = true
            }
        }
        onDispose { CardNfc.stop(activity) }
    }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.pwd_card_nfc_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (!enabled) {
                Text(stringResource(R.string.pwd_card_nfc_off), fontSize = 14.sp)
                TextButton(onClick = { runCatching { activity.startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS)) } }) {
                    Text(stringResource(R.string.pwd_card_nfc_settings), fontWeight = FontWeight.Bold)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    }
                    Text(stringResource(if (failed) R.string.pwd_card_nfc_retry else R.string.pwd_card_nfc_hold), fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(EuriseIcons.lock(), null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.pwd_card_nfc_privacy), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

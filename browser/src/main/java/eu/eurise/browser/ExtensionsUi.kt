package eu.eurise.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/** Cosa può leggere un'estensione, in parole (le voci più comuni; le altre si tacciono solo se innocue). */
@Composable
internal fun extensionReads(permissions: List<String>, origins: List<String>): List<String> {
    val words = mapOf(
        "all_sites" to stringResource(R.string.brw_extp_all_sites),
        "sites" to stringResource(R.string.brw_extp_sites),
        "tabs" to stringResource(R.string.brw_extp_tabs),
        "history" to stringResource(R.string.brw_extp_history),
        "bookmarks" to stringResource(R.string.brw_extp_bookmarks),
        "downloads" to stringResource(R.string.brw_extp_downloads),
        "clipboardRead" to stringResource(R.string.brw_extp_clipboard),
        "cookies" to stringResource(R.string.brw_extp_cookies),
        "webRequest" to stringResource(R.string.brw_extp_requests),
        "geolocation" to stringResource(R.string.brw_extp_location),
        "notifications" to stringResource(R.string.brw_extp_notifications),
        "privacy" to stringResource(R.string.brw_extp_privacy),
        "proxy" to stringResource(R.string.brw_extp_proxy),
        "nativeMessaging" to stringResource(R.string.brw_extp_native),
    )
    return Extensions.describe(permissions, origins) { words[it] }
}

/**
 * La scheda dei permessi prima di installare un'estensione (regola della privacy chiara): cosa può leggere, dove va, come
 * si torna indietro. Si installa solo con «Aggiungi»; le schede private restano escluse.
 */
@Composable
internal fun ExtensionPrompt(pending: Extensions.Pending) {
    val scheme = MaterialTheme.colorScheme
    val name = pending.extension.metaData.name ?: pending.extension.id
    val reads = extensionReads(pending.permissions, pending.origins)
    BottomSheet(onClose = { Extensions.answer(false) }) {
        Text(stringResource(R.string.brw_extp_title, name), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        pending.extension.metaData.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (reads.ifEmpty { listOf(stringResource(R.string.brw_extp_nothing)) }).forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.eye(2.2f), null, tint = Half, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(line, fontSize = 13.5.sp)
                }
            }
        }
        Text(stringResource(R.string.brw_extp_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(R.string.brw_cancel), EuriseIcons.close(2.2f), Modifier.weight(1f)) { Extensions.answer(false) }
            Pill(stringResource(R.string.brw_ext_add), EuriseIcons.plus(2.2f), Modifier.weight(1f), strong = true) { Extensions.answer(true) }
        }
    }
}

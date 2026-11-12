package eu.eurise.browser

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/**
 * Le bolle degli accessi, sopra la barra: «Accedi come…» (un tocco compila), «Salvo l'accesso?» e, a Password chiusa,
 * «Sblocca». Una sola alla volta, nell'ordine in cui servono.
 */
@Composable
internal fun LoginBubbles(tab: Tab, onUnlock: () -> Unit) {
    val choice = tab.loginChoice
    val save = tab.loginSave
    val host = Uri.parse(tab.url).host.orEmpty().removePrefix("www.")
    val locked = Logins.lockedDomain?.takeIf { d -> host == d || host.endsWith(".$d") }
    when {
        save != null -> Bubble(
            title = stringResource(if (save.update) R.string.brw_login_update else R.string.brw_login_save, Logins.label(save.entry), host),
            onClose = { save.dismiss(); tab.loginSave = null },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.brw_login_save_go), EuriseIcons.check(2f), Modifier.weight(1f), strong = true) { save.save(); tab.loginSave = null }
                Pill(stringResource(R.string.brw_login_not_now), EuriseIcons.close(2f), Modifier.weight(1f)) { save.dismiss(); tab.loginSave = null }
            }
        }
        choice != null -> Bubble(
            title = stringResource(R.string.brw_login_choose, host),
            onClose = { choice.dismiss(); tab.loginChoice = null },
        ) {
            choice.options.take(4).forEach { o ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable(role = Role.Button) { choice.pick(o); tab.loginChoice = null }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(EuriseIcons.key(2f), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.brw_login_as, Logins.label(o.value)), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        locked != null -> Bubble(
            title = stringResource(R.string.brw_login_locked, locked),
            onClose = { Logins.lockedDomain = null },
        ) {
            Pill(stringResource(R.string.brw_login_unlock), EuriseIcons.key(2f), Modifier.fillMaxWidth(), strong = true, onClick = onUnlock)
        }
    }
}

@Composable
private fun Bubble(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).shadow(8.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp)).background(scheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.key(2f), null, tint = scheme.primary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val close = stringResource(R.string.brw_login_close)
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = close },
                contentAlignment = Alignment.Center,
            ) { Icon(EuriseIcons.close(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp)) }
        }
        content()
    }
}

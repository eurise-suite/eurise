package eu.eurise.password.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.GroupCard
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.PrivacyConsentSheet
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.password.PasswordViewModel
import eu.eurise.password.R
import eu.eurise.password.data.AliasProvider
import eu.eurise.password.data.SyncState

/**
 * La privacy di Password a 10 tacchette (tavola PwdPrivacyTacche, approvata il 18 novembre): dove si è adesso, i servizi
 * che si possono accendere (tutti spenti di base) con «prima di accendere», ciò che resta sempre sul telefono.
 */
internal object PwdPrivacy {
    const val SYNC = 9
    const val BREACH = 9
    const val ALIAS = 7

    fun aliasWhere(p: AliasProvider) = when (p) {
        AliasProvider.SimpleLogin, AliasProvider.KMail -> Jurisdiction.CH
        AliasProvider.Addy -> Jurisdiction.UK
        else -> Jurisdiction.Phone
    }

    /** Il livello di adesso: il più basso tra i servizi accesi, 10 se nessuno. */
    fun level(sync: Boolean, breach: Boolean, alias: AliasProvider): Int = listOfNotNull(
        SYNC.takeIf { sync }, BREACH.takeIf { breach },
        ALIAS.takeIf { alias == AliasProvider.SimpleLogin || alias == AliasProvider.Addy || alias == AliasProvider.KMail },
    ).minOrNull() ?: 10
}

/** La riga in cima alle impostazioni: il livello di adesso, e un tocco apre la privacy. */
@Composable
internal fun PrivacySummaryRow(vm: PasswordViewModel, onClick: () -> Unit) {
    val sync by vm.sync.collectAsStateWithLifecycle()
    val breach by vm.breachOn.collectAsStateWithLifecycle()
    val alias by vm.alias.collectAsStateWithLifecycle()
    val level = PwdPrivacy.level(sync !is SyncState.Off, breach, alias)
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.pwd_pv_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            PrivacyMeter(level, Jurisdiction.Phone.takeIf { level == 10 })
        }
        Icon(EuriseIcons.chevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun PrivacyScreen(vm: PasswordViewModel, onBack: () -> Unit, onSettings: () -> Unit, onChooseCloud: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val sync by vm.sync.collectAsStateWithLifecycle()
    val breach by vm.breachOn.collectAsStateWithLifecycle()
    val alias by vm.alias.collectAsStateWithLifecycle()
    val syncOn = sync !is SyncState.Off
    val level = PwdPrivacy.level(syncOn, breach, alias)
    var asking by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader(stringResource(R.string.pwd_back), onBack)
        Text(stringResource(R.string.pwd_pv_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        GroupCard {
            Column(Modifier.padding(vertical = 14.dp, horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pwd_pv_now).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                PrivacyMeter(level, Jurisdiction.Phone.takeIf { level == 10 })
                Text(stringResource(if (level == 10) R.string.pwd_pv_now_all else R.string.pwd_pv_now_some), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        SectionLabel(stringResource(R.string.pwd_pv_services))
        GroupCard {
            ServiceRow(stringResource(R.string.pwd_pv_sync), stringResource(R.string.pwd_pv_sync_d), PwdPrivacy.SYNC, Jurisdiction.EU, syncOn) { on ->
                if (on) asking = "sync" else vm.disableSync()
            }
            CardDivider()
            ServiceRow(stringResource(R.string.pwd_pv_breach), stringResource(R.string.pwd_pv_breach_d), PwdPrivacy.BREACH, Jurisdiction.UK, breach) { on ->
                if (on) asking = "breach" else vm.setBreach(false)
            }
            CardDivider()
            // Gli alias si collegano dalle impostazioni (serve scegliere il servizio); qui si vede dove si è e si spegne.
            listOf(AliasProvider.Addy to R.string.pwd_pv_addy, AliasProvider.SimpleLogin to R.string.pwd_pv_simplelogin).forEachIndexed { i, (p, label) ->
                if (i > 0) CardDivider()
                ServiceRow(stringResource(label), stringResource(R.string.pwd_pv_alias_d), PwdPrivacy.ALIAS, PwdPrivacy.aliasWhere(p), alias == p) { on ->
                    if (on) onSettings() else vm.selectAlias(AliasProvider.None)
                }
            }
        }
        SectionLabel(stringResource(R.string.pwd_pv_phone))
        GroupCard {
            Column(Modifier.heightIn(min = 64.dp).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.pwd_pv_fill), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.pwd_pv_fill_d), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                PrivacyMeter(10, Jurisdiction.Phone)
            }
        }
        Text(stringResource(R.string.pwd_pv_footer), fontSize = 12.5.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(24.dp))
    }
    when (asking) {
        "breach" -> PrivacyConsentSheet(
            stringResource(R.string.pwd_pv_breach_q), stringResource(R.string.pwd_pv_breach_read), stringResource(R.string.pwd_pv_breach_out),
            stringResource(R.string.pwd_pv_breach_who), stringResource(R.string.pwd_pv_back), PwdPrivacy.BREACH, Jurisdiction.UK,
            onDismiss = { asking = null }, onAccept = { asking = null; vm.setBreach(true) },
        )
        "sync" -> PrivacyConsentSheet(
            stringResource(R.string.pwd_pv_sync_q), stringResource(R.string.pwd_pv_sync_read), stringResource(R.string.pwd_pv_sync_out),
            stringResource(R.string.pwd_pv_sync_who), stringResource(R.string.pwd_pv_sync_back), PwdPrivacy.SYNC, Jurisdiction.EU,
            onDismiss = { asking = null }, onAccept = {
                asking = null
                // Senza un cloud già scelto la scelta si fa nelle impostazioni, dove sta il resto della sincronizzazione.
                if (vm.hasSyncAccount) vm.enableSync(null) { openUrl(context, it) } else onChooseCloud()
            },
        )
    }
}

@Composable
private fun ServiceRow(title: String, detail: String, score: Int, where: Jurisdiction, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrivacyMeter(score, where)
            if (!on) Text(stringResource(R.string.pwd_pv_if_on), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

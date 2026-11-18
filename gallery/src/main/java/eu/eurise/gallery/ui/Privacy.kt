package eu.eurise.gallery.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
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
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.PrivacyConsentSheet
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.gallery.GalleryViewModel
import eu.eurise.gallery.R

/**
 * La privacy della Galleria a 10 tacchette (tavola GalPrivacyTacche, approvata il 18 novembre): Luoghi con i nomi da
 * OpenStreetMap e caricamento sul cloud, spenti di base e accesi dopo «prima di accendere»; il resto sul telefono.
 */
internal object GalPrivacy {
    const val NAMES = 7
    const val UPLOAD = 6
    fun level(names: Boolean, upload: Boolean) = listOfNotNull(NAMES.takeIf { names }, UPLOAD.takeIf { upload }).minOrNull() ?: 10
}

@Composable
internal fun PrivacyScreen(vm: GalleryViewModel, onCloud: () -> Unit, onSecure: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val settings by vm.settings.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val names = settings.placesOn && settings.namesOn
    val level = GalPrivacy.level(names, settings.uploadOn)
    var asking by remember { mutableStateOf<String?>(null) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { vm.setPlaces(true); vm.setNames(true) }
    }
    Column(Modifier.screen()) {
        ScreenBar(stringResource(R.string.gpv_title), onClose)
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.navigationBarsPadding()) {
            item(key = "now") {
                Card {
                    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.gpv_now).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                        PrivacyMeter(level, Jurisdiction.Phone.takeIf { level == 10 })
                        Text(stringResource(if (level == 10) R.string.gpv_now_all else R.string.gpv_now_some), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
            item(key = "services") {
                SectionLabel(stringResource(R.string.gpv_services), Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                Card {
                    ServiceRow(stringResource(R.string.gpv_names), stringResource(R.string.gpv_names_d), GalPrivacy.NAMES, Jurisdiction.EU, names) { on ->
                        if (on) asking = "names" else vm.setNames(false)
                    }
                    CardDivider()
                    ServiceRow(stringResource(R.string.gpv_upload), stringResource(R.string.gpv_upload_d), GalPrivacy.UPLOAD, Jurisdiction.EU, settings.uploadOn) { on ->
                        when {
                            !on -> vm.updateCloud { it.copy(uploadOn = false) }
                            // Senza un cloud collegato si comincia da lì: il collegamento ha il suo avviso.
                            accounts.none { it.id == settings.accountId } -> onCloud()
                            else -> asking = "upload"
                        }
                    }
                }
            }
            item(key = "phone") {
                SectionLabel(stringResource(R.string.gpv_phone), Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                Card {
                    InfoRow(stringResource(R.string.gpv_location), stringResource(R.string.gpv_location_d))
                    CardDivider()
                    Row(Modifier.fillMaxWidth().clickable(onClick = onSecure), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { InfoRow(stringResource(R.string.gpv_secure), stringResource(R.string.gpv_secure_d)) }
                        Icon(EuriseIcons.chevronRight(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    CardDivider()
                    InfoRow(stringResource(R.string.gpv_faces), stringResource(R.string.gpv_faces_d))
                }
            }
            item(key = "foot") {
                Text(stringResource(R.string.gpv_footer), fontSize = 12.5.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
    when (asking) {
        "names" -> PrivacyConsentSheet(
            stringResource(R.string.gpv_names_q), stringResource(R.string.gpv_names_read), stringResource(R.string.gpv_names_out),
            stringResource(R.string.gpv_names_who), stringResource(R.string.gpv_names_back), GalPrivacy.NAMES, Jurisdiction.EU,
            onDismiss = { asking = null },
            onAccept = {
                asking = null
                if (settings.placesOn) vm.setNames(true)
                else if (context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) { vm.setPlaces(true); vm.setNames(true) }
                else askLocation.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            },
        )
        "upload" -> PrivacyConsentSheet(
            stringResource(R.string.gpv_upload_q), stringResource(R.string.gpv_upload_read), stringResource(R.string.gpv_upload_out),
            stringResource(R.string.gpv_upload_who), stringResource(R.string.gpv_upload_back), GalPrivacy.UPLOAD, Jurisdiction.EU,
            onDismiss = { asking = null },
            onAccept = { asking = null; vm.updateCloud { it.copy(uploadOn = true) } },
        )
    }
}

@Composable
private fun ServiceRow(title: String, detail: String, score: Int, where: Jurisdiction, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 12.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrivacyMeter(score, where)
            if (!on) Text(stringResource(R.string.gpv_if_on), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, fontSize = 12.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrivacyMeter(10, Jurisdiction.Phone)
    }
}

package eu.eurise.feature.drive

import android.content.Context
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.designsystem.component.DataOut
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.component.privacyScore
import eu.eurise.core.designsystem.icon.EuriseIcons

/** Il nome del telefono come lo vede l'utente («Fairphone 6»), o «Telefono». */
internal fun phoneName(context: Context): String =
    runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: context.getString(R.string.drive_phone)

/** Le tacchette di un cloud: i tuoi file in chiaro presso il servizio, pesa dove sta (regola del 13 novembre). */
internal fun placePrivacy(kind: CloudKind?): Pair<Int, Jurisdiction> = when (kind) {
    CloudKind.Murena -> privacyScore(DataOut.Personal, Jurisdiction.EU) to Jurisdiction.EU
    CloudKind.KDrive -> privacyScore(DataOut.Personal, Jurisdiction.CH) to Jurisdiction.CH
    CloudKind.Nextcloud -> privacyScore(DataOut.Personal, Jurisdiction.Own) to Jurisdiction.Own
    CloudKind.WebDav, null -> privacyScore(DataOut.Personal, Jurisdiction.Other) to Jurisdiction.Other
}

/** La barra dello spazio usato: rossa oltre il 90 %. */
@Composable
internal fun SpaceBar(space: Space, modifier: Modifier = Modifier, height: Int = 8) {
    val fraction = space.total?.takeIf { it > 0 }?.let { (space.used.toFloat() / it).coerceIn(0.02f, 1f) } ?: return
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape((height / 2).dp)).background(scheme.onSurface.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxWidth(fraction).height(height.dp).clip(RoundedCornerShape((height / 2).dp))
            .background(if (fraction > 0.9f) Color(0xFF8C2F39) else scheme.primary))
    }
}

/** «86 GB su 256» con la barra e, a destra, quanto resta libero (tavole DriveCartelle e DriveCloud). */
@Composable
internal fun SpaceCard(space: Space?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (space == null) {
            Text(stringResource(R.string.drive_space_unknown), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            return@Column
        }
        val used = Formatter.formatShortFileSize(context, space.used)
        val total = space.total?.let { Formatter.formatShortFileSize(context, it) }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(used, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            if (total != null) Text(" " + stringResource(R.string.drive_space_on, total), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
            Box(Modifier.weight(1f))
            space.total?.let { t ->
                Text(stringResource(R.string.drive_space_free, Formatter.formatShortFileSize(context, (t - space.used).coerceAtLeast(0))), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
        }
        SpaceBar(space)
    }
}

/**
 * Il cassetto dei posti (tavola DriveCassetto): Scanner, recenti e tipi di file, il telefono e ogni cloud con lo spazio
 * usato su totale e le tacchette, le cartelle di altre app e quelle cifrate, «Collega un cloud».
 */
@Composable
internal fun PlacesDrawer(
    open: Boolean,
    data: DriveData,
    refresh: Int,
    current: Place?,
    onClose: () -> Unit,
    onScan: () -> Unit,
    onOverview: () -> Unit,
    onFolder: (Folder) -> Unit,
    onPlace: (Place) -> Unit,
    onCloud: () -> Unit,
    onAddTree: () -> Unit,
    onVault: (Place) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    BackHandler(enabled = open, onBack = onClose)
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(onClick = onClose))
    }
    AnimatedVisibility(open, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
        val phone by produceState<Space?>(null, refresh) { value = runCatching { data.phoneSpace() }.getOrNull() }
        val clouds by produceState(emptyMap<Place, Space?>(), refresh) {
            value = data.places.associateWith { null }
            value = data.places.associateWith { data.cloudSpace(it) }
        }
        Column(
            Modifier.fillMaxHeight().width(316.dp).clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)).background(scheme.background)
                .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.drive_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = scheme.primary, modifier = Modifier.weight(1f))
                ScannerButton { onClose(); onScan() }
            }
            DrawerRow(EuriseIcons.clock(), scheme.onSurface, stringResource(R.string.drive_recent)) { onClose(); onOverview() }
            DrawerRow(EuriseIcons.image(), Color(0xFF4F6B5A), stringResource(R.string.drive_kind_images)) { onClose(); onFolder(Folder(data.phone, Environment.DIRECTORY_PICTURES)) }
            DrawerRow(EuriseIcons.video(), Color(0xFF8C2F39), stringResource(R.string.drive_kind_video)) { onClose(); onFolder(Folder(data.phone, Environment.DIRECTORY_MOVIES)) }
            DrawerRow(EuriseIcons.music(), Color(0xFFA8742A), stringResource(R.string.drive_kind_audio)) { onClose(); onFolder(Folder(data.phone, Environment.DIRECTORY_MUSIC)) }
            DrawerRow(EuriseIcons.document(), Color(0xFF5B6FA8), stringResource(R.string.drive_documents)) { onClose(); onFolder(Folder(data.phone, Environment.DIRECTORY_DOCUMENTS)) }
            DrawerRow(EuriseIcons.download(), scheme.onSurface, stringResource(R.string.drive_downloads)) { onClose(); onFolder(Folder(data.phone, Environment.DIRECTORY_DOWNLOADS)) }
            Divider()
            PlaceRow(
                glyph = { Icon(EuriseIcons.phone(), null, tint = scheme.primary, modifier = Modifier.size(22.dp)) },
                name = phoneName(context), space = phone, privacy = null, selected = current == null,
            ) { onClose(); onOverview() }
            Divider()
            Text(stringResource(R.string.drive_cloud_section), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            data.places.forEach { p ->
                PlaceRow(
                    glyph = { Letter(p.label) },
                    name = p.label, space = clouds[p], privacy = placePrivacy(data.kindOf(p)), selected = current == p,
                ) { onClose(); onPlace(p) }
            }
            data.treePlaces.forEach { p ->
                PlaceRow(glyph = { FolderGlyph(p) }, name = p.label, space = null, privacy = null, selected = current == p) { onClose(); onPlace(p) }
            }
            data.vaultPlaces.forEach { p ->
                DrawerRow(EuriseIcons.lock(), scheme.tertiary, stringResource(R.string.drive_vault_pill, p.label)) { onClose(); onVault(p) }
            }
            DrawerRow(EuriseIcons.plus(), scheme.primary, stringResource(R.string.drive_connect_cloud)) { onClose(); onCloud() }
            DrawerRow(EuriseIcons.folder(), scheme.primary, stringResource(R.string.drive_tree_add)) { onClose(); onAddTree() }
        }
    }
}

@Composable
internal fun ScannerButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.heightIn(min = 40.dp).clip(CircleShape).background(scheme.primary).clickable(role = Role.Button, onClick = onClick).padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(EuriseIcons.scan(2.4f), null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.drive_scanner), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlaceRow(
    glyph: @Composable () -> Unit,
    name: String,
    space: Space?,
    privacy: Pair<Int, Jurisdiction>?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) { glyph() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name, fontSize = 15.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                privacy?.let { (score, where) -> PrivacyMeter(score, where) }
            }
            if (space != null) {
                SpaceBar(space, height = 6)
                val used = Formatter.formatShortFileSize(context, space.used)
                Text(
                    space.total?.let { stringResource(R.string.drive_space_of, used, Formatter.formatShortFileSize(context, it)) } ?: used,
                    fontSize = 12.sp, color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Letter(label: String) {
    val tints = listOf(Color(0xFF4F6B5A), Color(0xFF1F2A44), Color(0xFF5B6FA8), Color(0xFF6D4AA8), Color(0xFFA8742A))
    Box(Modifier.size(30.dp).clip(CircleShape).background(tints[(label.hashCode() and 0x7fffffff) % tints.size]), contentAlignment = Alignment.Center) {
        Text(label.take(1).uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
}

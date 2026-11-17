package eu.eurise.office.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.office.R
import eu.eurise.office.data.Suite

private val Gold = Color(0xFFB8923F)

/**
 * «File dal cloud» (tavola OffOggiHome): i cloud collegati in Eurise File, il preferito in evidenza con la stellina piena.
 * Toccare una riga apre la sua cartella di riferimento ([folderOf], l'ultima da cui si è aperto un documento); la stellina
 * lo rende preferito, cioè quello che si apre con il tocco breve su «Cloud» nella dock. In fondo «Da un'altra app» (il
 * selettore di Android, per i cloud che Eurise File non vede: Proton Drive, kDrive dall'app…).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSheet(
    clouds: List<Suite.Place>,
    favorite: String,
    folderOf: (String) -> String?,
    onOpen: (Suite.Place) -> Unit,
    onFavorite: (Suite.Place) -> Unit,
    onOtherApp: () -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.background,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.office_cloud_title), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
            Text(stringResource(R.string.office_cloud_hint), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp))
            if (clouds.isEmpty()) {
                Text(stringResource(R.string.office_cloud_none), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(scheme.surface).clickable(onClick = onConnect).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(stringResource(R.string.office_cloud_connect), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.primary) }
            }
            clouds.forEach { place ->
                val fav = place.id == favorite
                val folder = folderOf(place.id)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (fav) scheme.primaryContainer else scheme.surface)
                        .clickable { onOpen(place) }
                        .padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(EuriseIcons.cloud(), contentDescription = null, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(place.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val where = folder?.let { stringResource(R.string.office_cloud_reference, it) } ?: stringResource(R.string.office_cloud_root)
                        Text(where, fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onFavorite(place) }) {
                        Icon(
                            EuriseIcons.star(),
                            contentDescription = stringResource(if (fav) R.string.office_cloud_favorite else R.string.office_cloud_make_favorite),
                            tint = if (fav) Gold else Gold.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.office_cloud_other_app) + " ›",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onOtherApp),
            )
        }
    }
}

package eu.eurise.core.designsystem.component

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import eu.eurise.core.designsystem.R
import eu.eurise.core.designsystem.icon.EuriseIcons

/*
 * Tutto schermo nei documenti (25 novembre 2026): in orizzontale parte da solo, in verticale da un pulsante; spariscono
 * intestazione, barra e barre di sistema, e una maniglia dal lato della mano richiama la barra. Lo usano la vista rapida e
 * il PDF di Office e il lettore PDF di File.
 */

/** Nasconde barra di stato e di navigazione finché [hidden]; uno scorrimento dal bordo le mostra per un momento. */
@Composable
fun SystemBarsHidden(hidden: Boolean) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(hidden) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (hidden) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (hidden) controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** La maniglia a tutto schermo: piccola e velata, per non coprire il documento; il tocco richiama la barra. */
@Composable
fun FullscreenHandle(modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(R.string.ds_show_bar)
    Box(
        modifier.padding(14.dp).size(44.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(EuriseIcons.chevronUp(), contentDescription = label, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
    }
}

/** Sopra la barra richiamata: per uscire dal tutto schermo, dal lato della mano. */
@Composable
fun ExitFullscreen(leftHanded: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = if (leftHanded) Arrangement.Start else Arrangement.End) {
        Row(
            Modifier.height(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).clickable(onClick = onClick).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(EuriseIcons.fullscreenExit(), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.ds_fullscreen_exit), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

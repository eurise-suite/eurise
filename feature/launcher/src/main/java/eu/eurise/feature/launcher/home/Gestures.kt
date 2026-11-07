package eu.eurise.feature.launcher.home

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Chiude un pannello con lo scorrimento opposto a quello che l'ha aperto: [up] vero per chiudere scorrendo
 * verso l'alto (Pannello Eurise), falso per chiudere scorrendo verso il basso (ricerca, categorie).
 * Funziona sia sulle zone ferme sia in fondo (o in cima) alle liste che scorrono.
 */
fun Modifier.closeOnSwipe(up: Boolean, onClose: () -> Unit): Modifier = composed {
    val close by rememberUpdatedState(onClose)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val connection = remember(up) {
        object : NestedScrollConnection {
            var accumulated = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    val y = available.y
                    if ((up && y < 0) || (!up && y > 0)) accumulated += y else if (consumed.y != 0f) accumulated = 0f
                    if ((up && accumulated < -threshold) || (!up && accumulated > threshold)) {
                        accumulated = 0f
                        close()
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                accumulated = 0f
                return Velocity.Zero
            }
        }
    }
    this
        .nestedScroll(connection)
        .pointerInput(up) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if ((up && total < -threshold) || (!up && total > threshold)) close() },
                onVerticalDrag = { _, dy -> total += dy },
            )
        }
}

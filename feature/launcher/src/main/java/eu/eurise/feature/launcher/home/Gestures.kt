package eu.eurise.feature.launcher.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.unit.dp

/**
 * Scorrimento verticale ovunque nel contenuto: osserva il dito prima dei figli (senza rubare i tocchi) e
 * scatta a fine gesto se il movimento è soprattutto verticale e nessuna lista interna ha fatto scorrere
 * il proprio contenuto. Così funziona anche sopra liste, pulsanti e notifiche, e non disturba lo
 * scorrimento normale. I trascinamenti dopo una pressione lunga (spostare icone o schede) non contano.
 */
private fun Modifier.verticalSwipe(onUp: (() -> Unit)?, onDown: (() -> Unit)?): Modifier = composed {
    val up by rememberUpdatedState(onUp)
    val down by rememberUpdatedState(onDown)
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    val slop = with(density) { 12.dp.toPx() }
    val tracker = remember {
        object : NestedScrollConnection {
            var scrolled = false
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y != 0f) scrolled = true
                return Offset.Zero
            }
        }
    }
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                tracker.scrolled = false
                var dx = 0f
                var dy = 0f
                var longPress = false
                var moved = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.size > 1) { longPress = true }
                    val change = event.changes.firstOrNull { it.id == first.id } ?: break
                    dx += change.position.x - change.previousPosition.x
                    dy += change.position.y - change.previousPosition.y
                    if (!moved && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) {
                        moved = true
                        if (change.uptimeMillis - first.uptimeMillis > 400) longPress = true
                    }
                    if (!change.pressed) break
                }
                val vertical = kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.4f
                if (!longPress && vertical && !tracker.scrolled) {
                    when {
                        dy < -threshold -> up?.invoke()
                        dy > threshold -> down?.invoke()
                    }
                }
            }
        }
        .nestedScroll(tracker)
}

/**
 * Chiude un pannello con lo scorrimento opposto a quello che l'ha aperto: [up] vero per chiudere scorrendo
 * verso l'alto (Pannello Eurise), falso per chiudere scorrendo verso il basso (ricerca, categorie).
 * Funziona sulle zone ferme, sopra le notifiche e in fondo (o in cima) alle liste.
 */
fun Modifier.closeOnSwipe(up: Boolean, onClose: () -> Unit): Modifier =
    if (up) verticalSwipe(onUp = onClose, onDown = null) else verticalSwipe(onUp = null, onDown = onClose)

/** Da qualsiasi pagina: scorrendo in su si aprono ricerca e app, scorrendo in giù il Pannello Eurise. */
fun Modifier.openOnSwipe(onUp: () -> Unit, onDown: () -> Unit): Modifier = verticalSwipe(onUp, onDown)

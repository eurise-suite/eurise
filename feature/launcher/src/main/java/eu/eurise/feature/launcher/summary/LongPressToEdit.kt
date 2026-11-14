package eu.eurise.feature.launcher.summary

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Fuori dalla modifica: una pressione lunga su una scheda apre la modifica senza aprire la scheda. Si legge
 * nel passaggio iniziale, prima dei pulsanti della scheda, che così restano toccabili normalmente.
 */
internal fun Modifier.longPressToEdit(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val moved = event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }
                if (event.changes.none { it.pressed } || moved) break
            }
            true
        }
        if (finished == null) {
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

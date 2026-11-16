package eu.eurise.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tiene il cursore sempre in vista mentre si scrive in un campo dentro una pagina che scorre.
 *
 * Il sistema porta il cursore solo fino al bordo della tastiera: le barre che galleggiano sopra la tastiera (formattazione,
 * dock) lo coprirebbero. Qui, a ogni lettera o spostamento del cursore, la pagina scorre finché sotto il cursore restano
 * libero almeno [below] (l'altezza delle barre). Uso: `modifier = Modifier.then(caret.modifier)`,
 * `onTextLayout = caret::onLayout`, e [Follow] con il punto del cursore.
 */
@Stable
@OptIn(ExperimentalFoundationApi::class)
class CaretInView internal constructor(private val requester: BringIntoViewRequester, private val belowPx: Float) {
    private var layout: TextLayoutResult? = null

    val modifier: Modifier = Modifier.bringIntoViewRequester(requester)

    fun onLayout(result: TextLayoutResult) {
        layout = result
    }

    /** Porta in vista il cursore alla posizione [offset] del testo mostrato, con lo spazio libero sotto. */
    suspend fun show(offset: Int) {
        val l = layout ?: return
        val r = l.getCursorRect(offset.coerceIn(0, l.layoutInput.text.length))
        runCatching { requester.bringIntoView(Rect(r.left, r.top - belowPx / 4, r.right + 1f, r.bottom + belowPx)) }
    }

    /** Segue il cursore finché il campo ha il fuoco: si richiama a ogni cambio di testo o di posizione. */
    @Composable
    fun Follow(focused: Boolean, text: Any, offset: Int) {
        LaunchedEffect(focused, text, offset) {
            if (!focused) return@LaunchedEffect
            withFrameNanos { } // aspetta l'impaginazione del testo nuovo
            show(offset)
        }
    }
}

/** [below]: spazio da lasciare libero sotto il cursore (barre sopra la tastiera); 96 dp bastano per una barra satinata. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberCaretInView(below: Dp = 96.dp): CaretInView {
    val px = with(LocalDensity.current) { below.toPx() }
    return remember(px) { CaretInView(BringIntoViewRequester(), px) }
}

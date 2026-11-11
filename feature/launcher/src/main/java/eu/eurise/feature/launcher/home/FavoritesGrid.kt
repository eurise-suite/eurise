package eu.eurise.feature.launcher.home

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.eurise.feature.launcher.apps.AppEntry
import eu.eurise.feature.launcher.apps.AppIcon

/** Un preferito preso con la pressione lunga, con l'ordine provvisorio mentre lo si trascina. */
private class FavoriteDrag(val entry: AppEntry, start: Offset, order: List<String>) {
    var pointer by mutableStateOf(start)
    var travelled by mutableStateOf(0f)
    var moved by mutableStateOf(false)
    var order by mutableStateOf(order)
}

/**
 * Griglia dei preferiti della home. Tenendo premuta un'icona e trascinandola, le altre si spostano al volo e al
 * rilascio resta nel posto nuovo; rilasciata senza muoverla apre il suo menu. [content] riceve l'app, la richiesta
 * del menu, la funzione da chiamare quando il menu si chiude e una funzione che dice se l'icona è tenuta (allora il
 * rilascio non deve aprirla).
 */
@Composable
fun FavoritesGrid(
    favorites: List<AppEntry>,
    columns: Int,
    iconSize: Dp,
    dark: Boolean,
    onReorder: (List<String>) -> Unit,
    content: @Composable (AppEntry, Boolean, () -> Unit, () -> Boolean) -> Unit,
) {
    var drag by remember { mutableStateOf<FavoriteDrag?>(null) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(favorites)
    val reorder by rememberUpdatedState(onReorder)
    val byKey = favorites.associateBy { it.key }

    fun keyAt(p: Offset): String? {
        val live = current.map { it.key }.toSet()
        return bounds.entries.firstOrNull { it.key in live && it.value.contains(p + origin) }?.key
    }

    val shown = drag?.order?.mapNotNull { byKey[it] } ?: favorites
    Box(
        Modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                detectDragGesturesAfterLongPress(
                    onDragStart = { at ->
                        val key = keyAt(at)
                        val entry = current.firstOrNull { it.key == key }
                        if (entry != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            drag = FavoriteDrag(entry, at, current.map { it.key })
                        }
                    },
                    onDrag = { change, amount ->
                        val d = drag
                        if (d != null) {
                        change.consume()
                        d.pointer += amount
                        d.travelled += amount.getDistance()
                        if (d.travelled > slop) d.moved = true
                        if (d.moved) {
                            val over = keyAt(d.pointer)
                            if (over != null && over != d.entry.key) {
                                val order = d.order
                                d.order = order.toMutableList().apply { remove(d.entry.key); add(order.indexOf(over), d.entry.key) }
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        }
                    },
                    onDragEnd = {
                        val d = drag
                        drag = null
                        if (d != null) {
                            if (!d.moved) menuKey = d.entry.key else reorder(d.order)
                        }
                    },
                    onDragCancel = { drag = null },
                )
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { entry ->
                        val lifted = drag?.moved == true && drag?.entry?.key == entry.key
                        Box(
                            Modifier.weight(1f)
                                .onGloballyPositioned { bounds[entry.key] = it.boundsInRoot() }
                                .alpha(if (lifted) 0f else 1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            content(entry, menuKey == entry.key, { menuKey = null }, { drag != null })
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // L'icona che segue il dito.
        drag?.takeIf { it.moved }?.let { d ->
            val half = with(LocalDensity.current) { (iconSize * 0.55f).toPx() }
            AppIcon(
                d.entry, iconSize * 1.1f, dark,
                modifier = Modifier.graphicsLayer {
                    translationX = d.pointer.x - half
                    translationY = d.pointer.y - half
                    alpha = 0.92f
                },
            )
        }
    }
}

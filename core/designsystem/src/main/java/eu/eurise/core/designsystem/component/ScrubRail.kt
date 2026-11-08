package eu.eurise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Barra laterale per saltare in un elenco lungo. Sotto il dito le etichette si ingrandiscono e si spostano verso
 * il centro dello schermo; accanto compare [bubble], il punto dell'elenco corrispondente alla posizione del dito
 * (da 0 in alto a 1 in basso), e [onScrub] ci porta l'elenco.
 */
@Composable
fun ScrubRail(
    labels: List<String>,
    onScrub: (Float) -> Unit,
    bubble: (Float) -> String,
    description: String,
    modifier: Modifier = Modifier,
    width: Dp = 24.dp,
) {
    var heightPx by remember { mutableIntStateOf(1) }
    var bubbleHeightPx by remember { mutableIntStateOf(0) }
    var finger by remember { mutableStateOf<Float?>(null) }
    val scrub by rememberUpdatedState(onScrub)
    val density = LocalDensity.current
    val radiusPx = with(density) { 56.dp.toPx() }
    val widthPx = with(density) { width.toPx() }
    val fraction = { y: Float -> (y / heightPx).coerceIn(0f, 1f) }
    val text = finger?.let { bubble(fraction(it)) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(text) { if (text != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    Box(
        modifier
            .width(width)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    finger = down.position.y
                    scrub(fraction(down.position.y))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        finger = change.position.y
                        scrub(fraction(change.position.y))
                    }
                    finger = null
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer {
                            val at = finger
                            val near = if (at == null) 0f else {
                                val center = (i + 0.5f) / labels.size * heightPx
                                (1f - abs(center - at) / radiusPx).coerceAtLeast(0f)
                            }
                            scaleX = 1f + near
                            scaleY = 1f + near
                            translationX = -near * widthPx * 1.3f
                        },
                    )
                }
            }
        }
        if (text != null) {
            Text(
                text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val y = ((finger ?: 0f) - bubbleHeightPx / 2f).coerceAtLeast(0f)
                        IntOffset(-(widthPx * 3.2f).roundToInt(), y.roundToInt())
                    }
                    .wrapContentSize(Alignment.TopEnd, unbounded = true)
                    .onSizeChanged { bubbleHeightPx = it.height }
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

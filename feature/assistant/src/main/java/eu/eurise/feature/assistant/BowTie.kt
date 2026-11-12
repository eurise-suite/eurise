package eu.eurise.feature.assistant

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.launch

/** Che cosa sta facendo Alfred, per il suo papillon. */
enum class Mood { Hello, Thinking, Attentive, Bow }

/**
 * Il papillon di Alfred, animato: all'arrivo se lo aggiusta (un giro storto e poi dritto), mentre pensa ondeggia
 * piano, quando ha fatto fa un piccolo inchino. Solo trasformazioni di disegno: nessun costo per la batteria a riposo.
 */
@Composable
fun BowTie(mood: Mood, modifier: Modifier = Modifier, tint: Color = Color.White) {
    val turn = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val bow = remember { Animatable(0f) }
    LaunchedEffect(mood) {
        when (mood) {
            Mood.Hello -> {
                scale.snapTo(0.7f)
                launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                turn.snapTo(-18f)
                turn.animateTo(10f, tween(220, easing = FastOutSlowInEasing))
                turn.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessLow))
            }
            Mood.Thinking -> {
                turn.snapTo(-6f)
                turn.animateTo(6f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse))
            }
            Mood.Attentive -> {
                turn.animateTo(0f, tween(200))
                scale.animateTo(1.12f, tween(140))
                scale.animateTo(1f, spring(dampingRatio = 0.4f))
            }
            Mood.Bow -> {
                turn.animateTo(0f, tween(150))
                bow.animateTo(48f, tween(260, easing = FastOutSlowInEasing))
                bow.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
            }
        }
    }
    Icon(
        EuriseIcons.bowtie(1.9f), null, tint = tint,
        modifier = modifier.graphicsLayer {
            rotationZ = turn.value
            scaleX = scale.value
            scaleY = scale.value
            rotationX = bow.value
            transformOrigin = TransformOrigin(0.5f, 0.2f)
            cameraDistance = 12f * density
        },
    )
}

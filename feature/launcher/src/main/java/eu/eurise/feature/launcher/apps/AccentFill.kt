package eu.eurise.feature.launcher.apps

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/**
 * Il fondo pieno nel colore d'accento. Con un accento cangiante (Ceruleo, come il gufo del Browser, 17 novembre 2026) è
 * la sua sfumatura, dal blu più chiaro in alto a sinistra al più intenso in basso a destra; altrimenti il colore pieno.
 */
@Composable
fun Modifier.accentBackground(shape: Shape = RectangleShape): Modifier {
    val gradient = LocalAccentGradient.current
    return if (gradient != null) background(Brush.linearGradient(gradient), shape) else background(MaterialTheme.colorScheme.primary, shape)
}

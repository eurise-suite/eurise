// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import helium314.keyboard.latin.R

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val material3 = Typography()
    // Eurise: Perla e Notte come nel resto della suite, al posto dei colori del sistema (tavola TastImpostazioniFrase).
    val colorScheme = if (dark) darkColorScheme(
        primary = Color(0xFFA9C1F2), onPrimary = Color(0xFF14203A),
        primaryContainer = Color(0xFF2C3A57), onPrimaryContainer = Color(0xFFECE9E3),
        secondary = Color(0xFF93BBA0), onSecondary = Color(0xFF14171C),
        background = Color(0xFF14171C), onBackground = Color(0xFFECE9E3),
        surface = Color(0xFF14171C), onSurface = Color(0xFFECE9E3),
        surfaceVariant = Color(0xFF262B33), onSurfaceVariant = Color(0xFFA4A9B2),
        surfaceContainerLowest = Color(0xFF1C2027), surfaceContainerLow = Color(0xFF1C2027),
        surfaceContainer = Color(0xFF1C2027), surfaceContainerHigh = Color(0xFF262B33), surfaceContainerHighest = Color(0xFF323843),
        outline = Color(0xFF4A515E), outlineVariant = Color(0xFF323843),
        error = Color(0xFFE8939B),
    ) else lightColorScheme(
        primary = Color(0xFF1F2A44), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDE3F0), onPrimaryContainer = Color(0xFF1F2A44),
        secondary = Color(0xFF4F6B5A), onSecondary = Color(0xFFFFFFFF),
        background = Color(0xFFF4F4F1), onBackground = Color(0xFF1E1D1B),
        surface = Color(0xFFF4F4F1), onSurface = Color(0xFF1E1D1B),
        surfaceVariant = Color(0xFFE9E9E4), onSurfaceVariant = Color(0xFF56564F),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF1F0EB), surfaceContainerHighest = Color(0xFFE9E9E4),
        outline = Color(0xFFB9B8B0), outlineVariant = Color(0xFFDADAD3),
        error = Color(0xFFB3412E),
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            titleLarge = material3.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = material3.titleMedium.copy(fontWeight = FontWeight.Bold),
            titleSmall = material3.titleSmall.copy(fontWeight = FontWeight.Bold)
        ),
        //shapes = Shapes(),
        content = content
    )
}

const val previewDark = true

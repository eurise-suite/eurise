package eu.eurise.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.eurise.core.designsystem.theme.EurisePalette as P

private val PerlaScheme = lightColorScheme(
    primary = P.BluNotte,
    onPrimary = P.Bianco,
    primaryContainer = P.BluNotteChiaro,
    onPrimaryContainer = P.BluNotte,
    secondary = P.Salvia,
    onSecondary = P.Bianco,
    secondaryContainer = P.SalviaChiara,
    onSecondaryContainer = P.Carbone,
    tertiary = P.Ottone,
    onTertiary = P.Bianco,
    tertiaryContainer = P.OttoneChiaro,
    onTertiaryContainer = P.Carbone,
    error = P.Borgogna,
    onError = P.Bianco,
    errorContainer = P.BorgognaChiara,
    onErrorContainer = P.Borgogna,
    background = P.Perla,
    onBackground = P.Carbone,
    surface = P.Perla,
    onSurface = P.Carbone,
    surfaceVariant = P.PerlaScura,
    onSurfaceVariant = P.TestoTenue,
    surfaceContainerLowest = P.Bianco,
    surfaceContainerLow = P.Bianco,
    surfaceContainer = P.PerlaScura,
    surfaceContainerHigh = P.PerlaScura,
    surfaceContainerHighest = P.PerlaBordo,
    outline = P.TestoTenue,
    outlineVariant = P.PerlaBordo,
)

private val NotteScheme = darkColorScheme(
    primary = P.Azzurro,
    onPrimary = P.AzzurroScuro,
    primaryContainer = P.AzzurroVelo,
    onPrimaryContainer = P.Azzurro,
    secondary = P.SalviaNotte,
    onSecondary = P.NotteFondo,
    secondaryContainer = P.Salvia,
    onSecondaryContainer = P.SalviaChiara,
    tertiary = P.OttoneNotte,
    onTertiary = P.NotteFondo,
    tertiaryContainer = P.Ottone,
    onTertiaryContainer = P.OttoneChiaro,
    error = P.BorgognaNotte,
    onError = P.NotteFondo,
    errorContainer = P.Borgogna,
    onErrorContainer = P.BorgognaChiara,
    background = P.NotteFondo,
    onBackground = P.NotteTesto,
    surface = P.NotteFondo,
    onSurface = P.NotteTesto,
    surfaceVariant = P.NotteRilievo,
    onSurfaceVariant = P.NotteTestoTenue,
    surfaceContainerLowest = P.NotteFondo,
    surfaceContainerLow = P.NotteSuperficie,
    surfaceContainer = P.NotteSuperficie,
    surfaceContainerHigh = P.NotteRilievo,
    surfaceContainerHighest = P.NotteBordo,
    outline = P.NotteTestoTenue,
    outlineVariant = P.NotteBordo,
)

private val EuriseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Tema comune a tutte le app Eurise.
 *
 * @param darkTheme usa "Notte" invece di "Perla"; di base segue il sistema.
 * @param dynamicColor prende i colori dallo sfondo (Material You, Android 12+). Spento di base:
 * la suite usa la propria palette finché l'utente non sceglie diversamente.
 */
@Composable
fun EuriseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> NotteScheme
        else -> PerlaScheme
    }
    CompositionLocalProvider(LocalEuriseColors provides if (darkTheme) NotteExtra else PerlaExtra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EuriseTypography,
            shapes = EuriseShapes,
            content = content,
        )
    }
}

/** Accesso ai colori della suite che Material 3 non prevede. */
object EuriseThemeExtras {
    val colors: EuriseExtraColors
        @Composable @ReadOnlyComposable get() = LocalEuriseColors.current
}

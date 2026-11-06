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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.eurise.core.designsystem.theme.EurisePalette as P

private val Carta = lightColorScheme(
    primary = P.BluNotte,
    onPrimary = P.AvorioChiaro,
    primaryContainer = P.BluNotteChiaro,
    onPrimaryContainer = P.BluNotte,
    secondary = P.Salvia,
    onSecondary = P.AvorioChiaro,
    secondaryContainer = P.SalviaChiara,
    onSecondaryContainer = P.Carbone,
    tertiary = P.Ottone,
    onTertiary = P.AvorioChiaro,
    tertiaryContainer = P.OttoneChiaro,
    onTertiaryContainer = P.Carbone,
    error = P.Borgogna,
    onError = P.AvorioChiaro,
    errorContainer = P.BorgognaChiara,
    onErrorContainer = P.Borgogna,
    background = P.Avorio,
    onBackground = P.Carbone,
    surface = P.Avorio,
    onSurface = P.Carbone,
    surfaceVariant = P.AvorioScuro,
    onSurfaceVariant = P.CarboneMedio,
    surfaceContainerLowest = P.AvorioChiaro,
    surfaceContainerLow = P.AvorioChiaro,
    surfaceContainer = P.AvorioScuro,
    surfaceContainerHigh = P.AvorioScuro,
    surfaceContainerHighest = P.Sabbia,
    outline = P.Grafite,
    outlineVariant = P.Sabbia,
)

private val Satinato = darkColorScheme(
    primary = P.BluCielo,
    onPrimary = P.BluNotte,
    primaryContainer = P.BluNotte,
    onPrimaryContainer = P.BluNotteChiaro,
    secondary = P.SalviaPastello,
    onSecondary = P.NotteProfonda,
    secondaryContainer = P.Salvia,
    onSecondaryContainer = P.SalviaChiara,
    tertiary = P.OttonePastello,
    onTertiary = P.NotteProfonda,
    tertiaryContainer = P.Ottone,
    onTertiaryContainer = P.OttoneChiaro,
    error = P.BorgognaPastello,
    onError = P.NotteProfonda,
    errorContainer = P.Borgogna,
    onErrorContainer = P.BorgognaChiara,
    background = P.NotteProfonda,
    onBackground = P.TestoNotte,
    surface = P.NotteProfonda,
    onSurface = P.TestoNotte,
    surfaceVariant = P.NotteRilievo,
    onSurfaceVariant = P.TestoNotteTenue,
    surfaceContainerLowest = P.NotteProfonda,
    surfaceContainerLow = P.NotteSuperficie,
    surfaceContainer = P.NotteSuperficie,
    surfaceContainerHigh = P.NotteRilievo,
    surfaceContainerHighest = P.NotteBordo,
    outline = P.TestoNotteTenue,
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
 * @param darkTheme usa "Satinato" (scuro) invece di "Carta" (chiaro); di base segue il sistema.
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
        darkTheme -> Satinato
        else -> Carta
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = EuriseTypography,
        shapes = EuriseShapes,
        content = content,
    )
}

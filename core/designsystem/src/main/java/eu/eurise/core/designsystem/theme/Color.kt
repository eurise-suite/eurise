package eu.eurise.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Palette della suite. Tema chiaro "Perla": grigio caldo appena accennato con schede bianche.
 * Tema scuro "Notte": blu-grafite, superfici più chiare man mano che salgono, accento azzurro.
 * Accenti di brand: blu notte, salvia, ottone, borgogna.
 */
internal object EurisePalette {
    // Perla
    val Perla = Color(0xFFF4F4F1)
    val Bianco = Color(0xFFFFFFFF)
    val PerlaScura = Color(0xFFE9E9E4)
    val PerlaBordo = Color(0xFFDADAD3)
    val Carbone = Color(0xFF1E1D1B)
    val TestoTenue = Color(0xFF56564F)

    val BluNotte = Color(0xFF1F2A44)
    val BluNotteChiaro = Color(0xFFDDE3F0)
    val Salvia = Color(0xFF4F6B5A)
    val SalviaChiara = Color(0xFFDCE7DF)
    val Ottone = Color(0xFF85672B)
    val OttoneChiaro = Color(0xFFF2E6CC)
    val Borgogna = Color(0xFF8C2F39)
    val BorgognaChiara = Color(0xFFF6DADC)

    // Notte
    val NotteFondo = Color(0xFF14171C)
    val NotteSuperficie = Color(0xFF1C2027)
    val NotteRilievo = Color(0xFF262B33)
    val NotteBordo = Color(0xFF323843)
    val NotteTesto = Color(0xFFECE9E3)
    val NotteTestoTenue = Color(0xFFA4A9B2)
    val Azzurro = Color(0xFFA9C1F2)
    val AzzurroScuro = Color(0xFF14203A)
    val AzzurroVelo = Color(0xFF2C3A57)
    val SalviaNotte = Color(0xFF93BBA0)
    val OttoneNotte = Color(0xFFD2B06A)
    val BorgognaNotte = Color(0xFFE8939B)
}

/** Colori della suite che Material 3 non prevede: vetro, linea dell'ora attuale, veli del calendario. */
@Immutable
data class EuriseExtraColors(
    /** Tinta del vetro satinato (barra delle sezioni, testate fisse). */
    val glass: Color,
    /** Contorno del vetro: appena percettibile. */
    val glassOutline: Color,
    /** Capsula della voce attiva dentro una pillola di vetro. */
    val glassSelected: Color,
    /** Linea e punto dell'ora attuale. */
    val now: Color,
    /** Velo sulla colonna di oggi nelle griglie orarie. */
    val todayVeil: Color,
    /** Velo appena percettibile su sabato e domenica. */
    val weekendVeil: Color,
    /** Linee della griglia oraria. */
    val grid: Color,
    val isDark: Boolean,
)

internal val PerlaExtra = EuriseExtraColors(
    glass = Color(0x9EFFFFFF),
    glassOutline = Color(0x0A1E1D1B),
    glassSelected = Color(0xE6FFFFFF),
    now = EurisePalette.Borgogna,
    todayVeil = Color(0x0F1F2A44),
    weekendVeil = Color(0x0656564F),
    grid = Color(0xFFE4E4DE),
    isDark = false,
)

internal val NotteExtra = EuriseExtraColors(
    glass = Color(0x9E262C36),
    glassOutline = Color(0x0AFFFFFF),
    glassSelected = Color(0xF2262B33),
    now = EurisePalette.BorgognaNotte,
    todayVeil = Color(0x12A9C1F2),
    weekendVeil = Color(0x06A4A9B2),
    grid = Color(0xFF20252D),
    isDark = true,
)

val LocalEuriseColors = staticCompositionLocalOf { PerlaExtra }

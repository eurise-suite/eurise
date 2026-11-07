package eu.eurise.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Per ora si usa il carattere di sistema. Il font della suite (Plus Jakarta Sans, licenza OFL)
 * andrà incluso come file in res/font: niente font scaricati da servizi Google.
 */
private val Base = FontFamily.Default
private val Default = Typography()

internal val EuriseTypography = Typography(
    displaySmall = Default.displaySmall.copy(fontFamily = Base, fontWeight = FontWeight.Light, letterSpacing = (-0.02).em),
    headlineLarge = Default.headlineLarge.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    // Titoli delle sezioni (Oggi, Calendario, Note, Attività): stessa scala ovunque.
    headlineMedium = Default.headlineMedium.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.01).em),
    headlineSmall = Default.headlineSmall.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold),
    bodyLarge = Default.bodyLarge.copy(fontFamily = Base, lineHeight = 24.sp),
    bodyMedium = Default.bodyMedium.copy(fontFamily = Base),
    bodySmall = Default.bodySmall.copy(fontFamily = Base),
    labelLarge = Default.labelLarge.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(
        fontFamily = Base,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.08.em,
    ),
)

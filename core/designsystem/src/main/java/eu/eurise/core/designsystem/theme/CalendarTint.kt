package eu.eurise.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Le otto tinte della suite per i calendari. Ognuna ha un tono per il tema chiaro e uno più luminoso
 * per il tema scuro, così i blocchi restano leggibili su entrambi i fondi.
 */
enum class CalendarTint(val light: Color, val dark: Color) {
    BluNotte(Color(0xFF3E5C8A), Color(0xFF8FA9D9)),
    Salvia(Color(0xFF4F6B5A), Color(0xFF93BBA0)),
    Ottone(Color(0xFF85672B), Color(0xFFD2B06A)),
    Borgogna(Color(0xFF8C2F39), Color(0xFFDE919A)),
    Petrolio(Color(0xFF2E6E73), Color(0xFF79BCC1)),
    Prugna(Color(0xFF6B4A7A), Color(0xFFBA9BCB)),
    Terracotta(Color(0xFFA0522D), Color(0xFFDE9A72)),
    Grafite(Color(0xFF55585E), Color(0xFFA9AEB7));

    companion object {
        /** La tinta con questo nome (come la salvano note, taccuini ed elenchi); Grafite se non è nota. */
        fun named(name: String?): CalendarTint = entries.firstOrNull { it.name == name } ?: Grafite

        // Tonalità di riferimento (gradi) con cui confrontare i colori dei calendari di Murena.
        private val hues = mapOf(
            BluNotte to 215f, Petrolio to 185f, Salvia to 130f, Ottone to 40f,
            Terracotta to 20f, Borgogna to 355f, Prugna to 285f,
        )

        /**
         * La tinta della suite più vicina a un colore ARGB (per esempio quello di un calendario di Murena).
         * I colori poco saturi diventano Grafite.
         */
        fun nearest(argb: Int): CalendarTint {
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val delta = mx - mn
            val saturation = if (mx == 0f) 0f else delta / mx
            if (saturation < 0.18f || delta < 0.08f) return Grafite
            var hue = when (mx) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }
            if (hue < 0f) hue += 360f
            return hues.minBy { (_, h) -> abs(hue - h).let { d -> min(d, 360f - d) } }.key
        }
    }
}

/** Il tono della tinta nel tema attuale: quello chiaro col tema Perla, quello luminoso col tema Notte. */
@Composable
@ReadOnlyComposable
fun CalendarTint.tone(): Color = if (LocalEuriseColors.current.isDark) dark else light

/** Colori di un blocco evento per la tinta e il tema attuali. */
data class TintColors(val dot: Color, val fill: Color, val content: Color, val outline: Color)

@Composable
@ReadOnlyComposable
fun CalendarTint.colors(): TintColors {
    val extra = LocalEuriseColors.current
    return if (extra.isDark) {
        val base = EurisePalette.NotteFondo
        TintColors(dot = dark, fill = lerp(base, dark, 0.24f), content = lerp(dark, Color.White, 0.58f), outline = dark.copy(alpha = 0.22f))
    } else {
        TintColors(dot = light, fill = lerp(Color.White, light, 0.15f), content = lerp(light, Color.Black, 0.38f), outline = light.copy(alpha = 0.28f))
    }
}

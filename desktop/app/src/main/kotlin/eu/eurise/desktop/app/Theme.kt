package eu.eurise.desktop.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import java.util.concurrent.TimeUnit

/**
 * I colori della suite per il computer: Perla e Notte, gli stessi valori di `core/designsystem` (che è una libreria
 * Android e qui non si può usare). Quando il designsystem diventerà multipiattaforma, questo file sparisce.
 */
@Immutable
data class EuriseColors(
    val background: Color,
    val sidebar: Color,
    val bar: Color,
    val card: Color,
    val selected: Color,
    val text: Color,
    val soft: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val blu: Color,
    val salvia: Color,
    val ottone: Color,
    val borgogna: Color,
    val petrolio: Color,
    val prugna: Color,
    val terracotta: Color,
)

val Perla = EuriseColors(
    background = Color(0xFFF4F4F1), sidebar = Color(0xFFECECE7), bar = Color(0xFFE9E9E4), card = Color.White,
    selected = Color.White, text = Color(0xFF1E1D1B), soft = Color(0xFF56564F), line = Color(0x141E1D1B),
    accent = Color(0xFF1F2A44), onAccent = Color.White,
    blu = Color(0xFF3E5C8A), salvia = Color(0xFF4F6B5A), ottone = Color(0xFF85672B), borgogna = Color(0xFF8C2F39),
    petrolio = Color(0xFF2E6E73), prugna = Color(0xFF6B4A7A), terracotta = Color(0xFFA0522D),
)

val Notte = EuriseColors(
    background = Color(0xFF14171C), sidebar = Color(0xFF181B21), bar = Color(0xFF1C2027), card = Color(0xFF1C2027),
    selected = Color(0xFF262B33), text = Color(0xFFECE9E3), soft = Color(0xFFA4A9B2), line = Color(0x12FFFFFF),
    accent = Color(0xFFA9C1F2), onAccent = Color(0xFF14203A),
    blu = Color(0xFF8FA9D9), salvia = Color(0xFF93BBA0), ottone = Color(0xFFD2B06A), borgogna = Color(0xFFDE919A),
    petrolio = Color(0xFF79BCC1), prugna = Color(0xFFBA9BCB), terracotta = Color(0xFFDE9A72),
)

val LocalEurise = staticCompositionLocalOf { Perla }

@Composable
fun EuriseTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) Notte else Perla
    val scheme = if (dark) darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.background, surface = c.card, onSurface = c.text)
    else lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.background, surface = c.card, onSurface = c.text)
    CompositionLocalProvider(LocalEurise provides c) { MaterialTheme(colorScheme = scheme, content = content) }
}

/** Il tema scelto nel sistema: GNOME (`color-scheme`), poi KDE (schema di colori con «Dark» nel nome). */
object SystemTheme {
    fun dark(): Boolean = gnome() ?: kde() ?: false

    private fun gnome(): Boolean? = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")?.let { "dark" in it }

    private fun kde(): Boolean? = run("kreadconfig5", "--group", "General", "--key", "ColorScheme")
        ?.takeIf { it.isNotBlank() }?.let { "dark" in it.lowercase() }

    private fun run(vararg cmd: String): String? = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        if (!p.waitFor(2, TimeUnit.SECONDS)) { p.destroy(); return null }
        if (p.exitValue() != 0) null else p.inputStream.bufferedReader().readText().trim()
    }.getOrNull()
}

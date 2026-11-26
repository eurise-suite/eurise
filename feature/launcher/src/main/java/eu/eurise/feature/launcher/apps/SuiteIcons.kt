package eu.eurise.feature.launcher.apps

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import eu.eurise.core.designsystem.icon.SuiteDrawings
import eu.eurise.core.designsystem.icon.SuiteKeys
import eu.eurise.core.designsystem.icon.SuiteSeries

/**
 * Le tre serie nuove delle icone (tela «Icone della suite», pagina Icone, approvate il 16 novembre 2026), come temi in
 * più accanto ai vecchi: bolle chiare e bolle scure di vetro satinato, e colorate. Le app della suite hanno il loro
 * disegno ([SuiteDrawings]); le altre il loro simbolo nella stessa bolla. Disegni, bolla e tela stanno in
 * `core/designsystem` (li usa anche la Postazione); qui resta la scelta per tema e per voce del cassetto.
 */
internal object SuiteIcons {
    /** La serie di un tema nuovo, o null per i temi di prima. */
    fun series(theme: String): SuiteSeries? = when (theme) {
        IconThemes.GLASS_LIGHT -> SuiteSeries.Light
        IconThemes.GLASS_DARK -> SuiteSeries.Dark
        IconThemes.VIVID -> SuiteSeries.Color
        else -> null
    }

    /** Il disegno della suite per [entry]: le sezioni di Eurise per nome, le app separate per pacchetto. */
    fun drawingFor(entry: AppEntry): SuiteDrawings.Drawing? = keyFor(entry)?.let { SuiteDrawings.all[it] }

    internal fun keyFor(entry: AppEntry): String? {
        entry.euriseSection?.let { section ->
            return when (section) {
                "today" -> "Agenda"
                "calendar" -> "Calendario"
                "notes" -> "Note"
                "tasks" -> "Attività"
                "clock" -> "Orologio"
                "drive" -> "Drive"
                "scanner" -> "Scanner"
                "calculator" -> "Calcolatrice"
                "weather" -> "Meteo"
                "alfred" -> "Alfred"
                "backup" -> "Backup"
                else -> null
            }
        }
        return SuiteKeys.forComponent(entry.packageName, entry.component?.className.orEmpty())
    }

    /** I colori del fondo colorato per un'app senza disegno della suite: la sua tinta, più chiara in alto, più cupa in basso. */
    fun roundFor(tint: Color): List<Color> = listOf(lerp(tint, Color.White, 0.18f), tint, lerp(tint, Color.Black, 0.25f))
}

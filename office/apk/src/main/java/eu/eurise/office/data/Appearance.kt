package eu.eurise.office.data

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.graphics.toArgb
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.suite.AppearanceContract as C
import eu.eurise.office.home.OfficePrefs

/**
 * Colore d'accento delle icone di Office (richiesta del proprietario, 14 novembre): il tratto resta blu Notte, i dettagli
 * prendono l'accento. Di base quello della Scrivania (dall'app Eurise, [C]); si può scegliere una tinta nelle
 * impostazioni di Office. Senza l'app Eurise (o finché non offre l'aspetto) vale l'accento di base della Scrivania, Ottone.
 */
object Appearance {
    /** Una tinta tra cui scegliere: [name] come lo salva la Scrivania («suite:Ottone», «extra:Cuoio»…). */
    data class Tint(val name: String, val label: String, val light: Int, val dark: Int)

    /** Scelta salvata in Office per «come la Scrivania». */
    const val DESK = ""

    private val fallback: Tint
        get() = CalendarTint.Ottone.let { Tint("suite:Ottone", "Ottone", it.light.toArgb(), it.dark.toArgb()) }

    private fun call(context: Context, method: String): Bundle? = runCatching {
        context.contentResolver.call(Uri.parse("content://" + C.authority(Suite.hub)), method, null, null)
    }.getOrNull()

    /** Le tinte da offrire: quelle della Scrivania se l'app Eurise le dà, altrimenti le sette della suite. */
    fun palette(context: Context): List<Tint> {
        val b = call(context, C.METHOD_PALETTE)
        val names = b?.getStringArray(C.KEY_NAMES)
        val labels = b?.getStringArray(C.KEY_LABELS)
        val lights = b?.getIntArray(C.KEY_LIGHTS)
        val darks = b?.getIntArray(C.KEY_DARKS)
        if (names != null && labels != null && lights != null && darks != null && names.isNotEmpty() &&
            labels.size == names.size && lights.size == names.size && darks.size == names.size
        ) {
            return names.indices.map { Tint(names[it], labels[it], lights[it], darks[it]) }
        }
        return CalendarTint.entries.filter { it != CalendarTint.Grafite }.map {
            Tint("suite:" + it.name, label(it.name), it.light.toArgb(), it.dark.toArgb())
        }
    }

    /** L'accento della Scrivania, o Ottone se l'app Eurise non lo dà. */
    fun desk(context: Context): Tint {
        val b = call(context, C.METHOD_ACCENT) ?: return fallback
        if (!b.containsKey(C.KEY_LIGHT) || !b.containsKey(C.KEY_DARK)) return fallback
        return Tint(b.getString(C.KEY_NAME).orEmpty(), "", b.getInt(C.KEY_LIGHT), b.getInt(C.KEY_DARK))
    }

    /** L'accento da usare ora: la scelta di Office se c'è, altrimenti quello della Scrivania. */
    fun current(context: Context): Tint {
        val chosen = OfficePrefs.accent(context)
        if (chosen != DESK) palette(context).firstOrNull { it.name == chosen }?.let { return it }
        return desk(context)
    }

    /** «#rrggbb» per l'interfaccia web del motore. */
    fun hex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    /** «BluNotte» → «Blu notte». */
    private fun label(name: String): String =
        name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar { it.uppercase() }
}

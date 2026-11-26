package eu.eurise.feature.launcher.apps

import android.content.Context
import androidx.compose.ui.graphics.Color
import eu.eurise.core.designsystem.icon.AppSymbols
import eu.eurise.feature.launcher.settings.IconPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Colore delle app: legge l'icona originale, ne trova il colore dominante (i pixel più saturi pesano di più)
 * e lo porta alla tinta della suite più vicina. Spotify, verde, avrà il simbolo in uno dei nostri verdi;
 * un'icona in bianco e nero non ha colore proprio e resta col colore della categoria.
 */
object AppColors {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private const val NONE = 0

    fun cached(entry: AppEntry): Color? = cache[entry.key]?.takeIf { it != NONE }?.let { Color(it) }

    private val originals = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun cachedOriginal(entry: AppEntry): Color? = originals[entry.key]?.takeIf { it != NONE }?.let { Color(it) }

    /**
     * Il colore dominante dell'icona originale così com'è, senza portarlo alle tinte della suite (tema «Sfondo delle
     * bolle»: WhatsApp resta verde WhatsApp, Telegram azzurro). Null per le icone senza un colore proprio.
     */
    suspend fun originalTint(context: Context, entry: AppEntry): Color? = withContext(Dispatchers.Default) {
        originals[entry.key]?.let { return@withContext if (it == NONE) null else Color(it) }
        val result = runCatching { dominantHue(context, entry) }.getOrNull()?.let { (hue, sat, value) ->
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat.coerceIn(0.45f, 1f), value.coerceIn(0.45f, 0.95f))))
        }
        originals[entry.key] = result?.let { argb(it) } ?: NONE
        result
    }

    suspend fun suiteTint(context: Context, entry: AppEntry): Color? = withContext(Dispatchers.Default) {
        cache[entry.key]?.let { return@withContext if (it == NONE) null else Color(it) }
        val result = runCatching { dominantHue(context, entry) }.getOrNull()?.let { (hue, sat, value) -> nearest(hue, sat, value) }
        cache[entry.key] = result?.let { argb(it) } ?: NONE
        result
    }

    private fun argb(c: Color): Int = android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    /** Il colore dominante dell'icona di [entry] (tinta, saturazione, valore): il calcolo è in [AppSymbols.dominantHsv]. */
    private fun dominantHue(context: Context, entry: AppEntry): Triple<Float, Float, Float>? {
        val pm = context.packageManager
        val drawable = entry.shortcut?.let { WebApps.icon(context, it) }
            ?: runCatching { entry.component?.let { pm.getActivityIcon(it) } }.getOrNull()
            ?: pm.getApplicationIcon(entry.packageName)
        return AppSymbols.dominantHsv(drawable)?.let { Triple(it[0], it[1], it[2]) }
    }

    /** La tinta della suite più vicina: conta soprattutto la tinta, poi luminosità e saturazione. */
    private fun nearest(hue: Float, sat: Float, value: Float): Color {
        val hsv = FloatArray(3)
        return IconPalette.minBy { c ->
            android.graphics.Color.colorToHSV(argb(c), hsv)
            val dh = kotlin.math.abs(hue - hsv[0]).let { minOf(it, 360f - it) } / 180f
            dh * 3f + kotlin.math.abs(value - hsv[2]) * 0.6f + kotlin.math.abs(sat - hsv[1]) * 0.4f
        }
    }
}

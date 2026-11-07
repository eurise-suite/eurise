package eu.eurise.feature.launcher.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
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

    suspend fun suiteTint(context: Context, entry: AppEntry): Color? = withContext(Dispatchers.Default) {
        cache[entry.key]?.let { return@withContext if (it == NONE) null else Color(it) }
        val result = runCatching { dominantHue(context, entry) }.getOrNull()?.let { (hue, sat, value) -> nearest(hue, sat, value) }
        cache[entry.key] = result?.let { argb(it) } ?: NONE
        result
    }

    private fun argb(c: Color): Int = android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    /** Tinta media pesata per saturazione, con un istogramma di 36 settori per non mescolare colori lontani. */
    private fun dominantHue(context: Context, entry: AppEntry): Triple<Float, Float, Float>? {
        val pm = context.packageManager
        val drawable = runCatching { entry.component?.let { pm.getActivityIcon(it) } }.getOrNull()
            ?: pm.getApplicationIcon(entry.packageName)
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        val weights = FloatArray(36)
        val satSum = FloatArray(36)
        val valSum = FloatArray(36)
        val hsv = FloatArray(3)
        var colored = 0f
        var total = 0f
        for (y in 0 until size) for (x in 0 until size) {
            val p = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(p) < 160) continue
            android.graphics.Color.colorToHSV(p, hsv)
            total += 1f
            if (hsv[1] < 0.28f || hsv[2] < 0.22f) continue
            val w = hsv[1] * hsv[2]
            val bucket = ((hsv[0] / 10f).toInt()).coerceIn(0, 35)
            weights[bucket] += w
            satSum[bucket] += hsv[1] * w
            valSum[bucket] += hsv[2] * w
            colored += 1f
        }
        bitmap.recycle()
        if (total == 0f || colored / total < 0.08f) return null
        // Settore più forte, sommato ai due vicini.
        var best = 0
        var bestScore = -1f
        for (i in 0 until 36) {
            val score = weights[i] + 0.5f * (weights[(i + 35) % 36] + weights[(i + 1) % 36])
            if (score > bestScore) { bestScore = score; best = i }
        }
        val w = weights[best].takeIf { it > 0f } ?: return null
        return Triple(best * 10f + 5f, satSum[best] / w, valSum[best] / w)
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

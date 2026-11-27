package eu.eurise.core.designsystem.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.drawable.toBitmap

/**
 * L'icona originale di un'app non nostra, pronta per la bolla: [fullBleed] vuol dire che riempie il tondo (con [fill]
 * sotto, per le piastrelle di vecchio stile); altrimenti è un disegno da mettere al centro della bolla com'è.
 */
class OriginalIcon(val bitmap: ImageBitmap, val fill: Color?, val fullBleed: Boolean)

/** Il simbolo di un'app non nostra: la sagoma (conta solo l'alfa) e, se si è potuto leggere, il colore del suo disegno. */
class AppSymbol(val bitmap: ImageBitmap, val color: Color?)

/**
 * Dall'icona di un'app qualsiasi ricava ciò che serve per darle la fisionomia della suite: il simbolo da colorare e il
 * colore che la contraddistingue. Solo lettura dell'icona che Android già mostra: nessun dato dell'app.
 */
object AppSymbols {

    /** Il primo che riesce: simbolo monocromatico dell'app, sagoma del primo piano, logo staccato dal fondo. */
    fun of(drawable: Drawable, px: Int): AppSymbol? = official(drawable, px) ?: silhouette(drawable, px) ?: logo(drawable, px)

    /** Il simbolo monocromatico fornito dall'app (Android 13+). */
    fun official(drawable: Drawable, px: Int): AppSymbol? {
        if (Build.VERSION.SDK_INT < 33 || drawable !is AdaptiveIconDrawable) return null
        val mono = drawable.monochrome ?: return null
        // Il colore del disegno si legge dal primo piano a colori, che ha la stessa forma.
        val color = drawable.foreground?.let { fg ->
            val b = cropCenter(fg.toBitmap(px * 3 / 2, px * 3 / 2, Bitmap.Config.ARGB_8888))
            val pixels = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
            LogoMask.glyphColor(pixels) { (pixels[it] ushr 24) > 200 }
        }
        return AppSymbol(cropCenter(mono.toBitmap(px * 3 / 2, px * 3 / 2, Bitmap.Config.ARGB_8888)).asImageBitmap(), color?.let { Color(it) })
    }

    /**
     * Per le app senza simbolo monocromatico: se il primo piano dell'icona adattiva è un disegno su fondo
     * trasparente (e non un riquadro pieno), se ne usa la sagoma, colorata come le altre.
     */
    fun silhouette(drawable: Drawable, px: Int): AppSymbol? {
        if (drawable !is AdaptiveIconDrawable) return null
        val fg = drawable.foreground ?: return null
        val bitmap = cropCenter(fg.toBitmap(px * 3 / 2, px * 3 / 2, Bitmap.Config.ARGB_8888))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val coverage = pixels.count { (it ushr 24) > 128 }.toFloat() / pixels.size
        if (coverage < 0.04f || coverage > 0.42f) return null
        val color = LogoMask.glyphColor(pixels) { (pixels[it] ushr 24) > 200 }
        return AppSymbol(bitmap.extractAlpha().asImageBitmap(), color?.let { Color(it) })
    }

    /**
     * Per le icone a tutto riquadro (le web app hanno quasi sempre il logo su un fondo pieno): il fondo è il colore del
     * bordo; i punti che se ne staccano formano la sagoma del logo. Se il bordo non è uniforme (una foto, un disegno
     * sfumato) o la sagoma è troppo piccola o troppo grande, null: resta l'icona originale.
     */
    fun logo(drawable: Drawable, px: Int): AppSymbol? {
        val size = px * 3 / 2
        val bitmap = if (drawable is AdaptiveIconDrawable) cropCenter(drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888))
        else drawable.toBitmap(px, px, Bitmap.Config.ARGB_8888)
        val mask = LogoMask.of(bitmap) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        val alpha = IntArray(mask.width * mask.height).also { mask.getPixels(it, 0, mask.width, 0, 0, mask.width, mask.height) }
        val color = LogoMask.glyphColor(pixels) { (alpha[it] ushr 24) > 200 }
        return AppSymbol(mask.asImageBitmap(), color?.let { Color(it) })
    }

    /**
     * Il colore dominante dell'icona, come tinta, saturazione e valore: media pesata per saturazione, con un istogramma
     * di 36 settori per non mescolare colori lontani. Null per le icone senza un colore proprio (bianco e nero, grigi).
     */
    fun dominantHsv(drawable: Drawable): FloatArray? {
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
        return floatArrayOf(best * 10f + 5f, satSum[best] / w, valSum[best] / w)
    }

    /**
     * Il colore che contraddistingue l'app così com'è (WhatsApp resta verde WhatsApp, Telegram azzurro), tenuto in una
     * fascia leggibile. Null per le icone senza un colore proprio.
     */
    fun dominantColor(drawable: Drawable): Color? = dominantHsv(drawable)?.let { (hue, sat, value) ->
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat.coerceIn(0.45f, 1f), value.coerceIn(0.45f, 0.95f))))
    }

    /**
     * L'icona originale **a tutto tondo**, per la bolla della suite (27 novembre 2026, scelta del proprietario: le app non
     * nostre restano come sono, con il loro fondo e i loro colori, e prendono solo la forma e la finitura di Eurise).
     * Adattiva: fondo e primo piano disegnati senza la maschera del telefono, presi i 72 dp centrali. Vecchio stile: se
     * l'icona è una piastrella piena (quadrata, tonda, arrotondata) si allarga fino al bordo e il fondo prende il colore
     * del suo bordo; se è un disegno su trasparente, [fill] è null e il disegno va al centro della bolla com'è.
     */
    fun original(drawable: Drawable, px: Int): OriginalIcon {
        if (drawable is AdaptiveIconDrawable) {
            val size = px * 3 / 2
            val full = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(full)
            listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
                layer.setBounds(0, 0, size, size)
                layer.draw(canvas)
            }
            return OriginalIcon(cropCenter(full).asImageBitmap(), null, true)
        }
        val bitmap = drawable.toBitmap(px, px, Bitmap.Config.ARGB_8888)
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        var left = w; var top = h; var right = -1; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) if ((pixels[y * w + x] ushr 24) > 40) {
            if (x < left) left = x; if (x > right) right = x; if (y < top) top = y; if (y > bottom) bottom = y
        }
        if (right < 0) return OriginalIcon(bitmap.asImageBitmap(), null, false)
        val bw = right - left + 1
        val bh = bottom - top + 1
        // Quanto del cerchio inscritto nel riquadro è pieno: una piastrella lo riempie quasi tutto.
        val cx = left + bw / 2f; val cy = top + bh / 2f; val r = minOf(bw, bh) / 2f * 0.92f
        var dentro = 0; var pieni = 0
        for (y in top..bottom) for (x in left..right) {
            val dx = x - cx; val dy = y - cy
            if (dx * dx + dy * dy <= r * r) { dentro++; if ((pixels[y * w + x] ushr 24) > 200) pieni++ }
        }
        if (dentro == 0 || pieni.toFloat() / dentro < 0.92f || bw < w / 2 || bh < h / 2) return OriginalIcon(bitmap.asImageBitmap(), null, false)
        val tile = Bitmap.createBitmap(bitmap, left, top, bw, bh)
        // Il colore del fondo: appena dentro il bordo sinistro, a metà altezza.
        val bg = pixels[(top + bh / 2) * w + (left + maxOf(2, bw / 14)).coerceAtMost(right)]
        return OriginalIcon(tile.asImageBitmap(), Color(bg).copy(alpha = 1f), true)
    }

    /** Le icone adattive stanno in un'area di 108 dp di cui si vedono i 72 centrali. */
    private fun cropCenter(full: Bitmap): Bitmap {
        val inset = full.width / 6
        return Bitmap.createBitmap(full, inset, inset, full.width - 2 * inset, full.height - 2 * inset)
    }
}

/**
 * Colora il disegno con una sfumatura diagonale: [colors] dall'alto a sinistra al basso a destra. Il disegno sotto conta
 * solo per la forma (alfa).
 */
fun Modifier.softTint(colors: List<Color>): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height)), blendMode = BlendMode.SrcIn)
    }

/**
 * La sfumatura cangiante di un colore originale: dalla tinta più chiara a quella più intensa, con la stessa tonalità; sul
 * fondo scuro un po' più luminosa e sul fondo chiaro un po' più cupa, perché si legga.
 */
fun shimmer(color: Color, darkGlass: Boolean): List<Color> {
    val c = if (darkGlass && color.luminance() < 0.25f) lerp(color, Color.White, 0.35f) else if (!darkGlass && color.luminance() > 0.6f) lerp(color, Color.Black, 0.25f) else color
    return listOf(lerp(c, Color.White, 0.28f), c, lerp(c, Color.Black, 0.22f))
}

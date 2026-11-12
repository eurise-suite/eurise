package eu.eurise.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File

/**
 * «Cattura intera» (tavola BrwStrumenti): l'articolo della Lettura disegnato in un'immagine lunga, con i colori Perla.
 * Tutta la pagina in un'immagine sola, anche la parte che non si vede sullo schermo; il disegno resta sul telefono.
 */
object ArticleImage {
    /** Larghezza dell'immagine e altezza massima (oltre si taglia: la memoria del telefono non è infinita). */
    private const val WIDTH = 1080
    private const val MAX_HEIGHT = 16_000
    private const val PAD = 72

    fun render(context: Context, title: String, text: String, site: String, out: File): File {
        val density = context.resources.displayMetrics.scaledDensity.coerceAtLeast(2f)
        val inner = WIDTH - PAD * 2
        val ink = Color.rgb(0x1E, 0x1D, 0x1B)
        val soft = Color.rgb(0x56, 0x56, 0x4F)
        val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 24f * density; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val headPaint = TextPaint(titlePaint).apply { textSize = 19f * density }
        val bodyPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 16.5f * density; typeface = Typeface.SERIF }
        val sitePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { color = soft; textSize = 13f * density }

        fun layout(s: String, p: TextPaint, extra: Float = 1.15f) =
            StaticLayout.Builder.obtain(s, 0, s.length, p, inner).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, extra).build()

        val parts = mutableListOf(layout(site, sitePaint) to 18, layout(title, titlePaint) to 36)
        Reading.blocks(text).forEach { b ->
            parts += if (b.startsWith("# ")) layout(b.removePrefix("# "), headPaint) to 22 else layout(b, bodyPaint, 1.45f) to 28
        }
        val total = PAD * 2 + parts.sumOf { it.first.height + it.second }
        val cut = total > MAX_HEIGHT
        val height = total.coerceAtMost(MAX_HEIGHT)
        // RGB_565: metà memoria, nessuna trasparenza da tenere.
        val bmp = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.RGB_565)
        try {
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.rgb(0xFB, 0xFA, 0xF6))
            var y = PAD.toFloat()
            val ellipsis = layout("…", bodyPaint)
            for ((l, gap) in parts) {
                if (cut && y + l.height > height - PAD - ellipsis.height) {
                    canvas.save(); canvas.translate(PAD.toFloat(), y); ellipsis.draw(canvas); canvas.restore()
                    break
                }
                canvas.save(); canvas.translate(PAD.toFloat(), y); l.draw(canvas); canvas.restore()
                y += l.height + gap
            }
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bmp.recycle()
        }
        return out
    }
}

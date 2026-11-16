package eu.eurise.office.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/**
 * La copia del PDF che si mostra a schermo. Il PdfRenderer di Android disegna solo il contenuto delle pagine, non le
 * annotazioni (evidenziazioni, penna, note, firme, campi compilati): senza questa copia un'evidenziazione appena fatta
 * spariva al primo ridisegno. Qui l'aspetto di ogni annotazione visibile si stampa sulla pagina; il file vero, quello
 * che si salva e si condivide, tiene le annotazioni come tali (modificabili in ogni lettore).
 */
object PdfDisplay {
    /** Sottotipi che non hanno un aspetto da disegnare (collegamenti, finestrelle delle note). */
    private val skipped = setOf("Link", "Popup")

    /**
     * Scrive in [target] la versione da schermo di [from]. Restituisce [from] se non c'è nulla da stampare (nessuna
     * copia), oppure [target].
     */
    fun build(from: File, target: File): File {
        PDDocument.load(from, MemoryUsageSetting.setupMixed(32L * 1024 * 1024)).use { d ->
            var changed = false
            for (page in d.pages) {
                val all = runCatching { page.annotations }.getOrNull() ?: continue
                val drawn = all.filter(::drawable)
                if (drawn.isEmpty()) continue
                PDPageContentStream(d, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    drawn.forEach { a -> runCatching { draw(cs, a) } }
                }
                page.annotations = all.filterNot { it in drawn }
                changed = true
            }
            if (!changed) return from
            d.isAllSecurityToBeRemoved = true
            d.save(target)
        }
        return target
    }

    private fun drawable(a: PDAnnotation): Boolean =
        a.subtype !in skipped && !a.isHidden && !a.isNoView && a.rectangle != null && a.normalAppearanceStream != null

    /**
     * Disegna l'aspetto di [a] dentro il suo rettangolo, come prescrive la specifica PDF (12.5.5): il riquadro
     * dell'aspetto, trasformato dalla sua matrice, si adatta al rettangolo dell'annotazione.
     */
    private fun draw(cs: PDPageContentStream, a: PDAnnotation) {
        val ap: PDAppearanceStream = a.normalAppearanceStream ?: return
        val box = ap.bBox ?: return
        val rect = a.rectangle
        val t = fit(
            floatArrayOf(box.lowerLeftX, box.lowerLeftY, box.upperRightX, box.upperRightY),
            matrixOf(ap),
            floatArrayOf(rect.lowerLeftX, rect.lowerLeftY, rect.upperRightX, rect.upperRightY),
        ) ?: return
        cs.saveGraphicsState()
        cs.transform(Matrix(t[0], t[1], t[2], t[3], t[4], t[5]))
        cs.drawForm(ap)
        cs.restoreGraphicsState()
    }

    /**
     * La matrice [a b c d e f] che porta il riquadro [bbox] (x1 y1 x2 y2), già trasformato dalla matrice dell'aspetto
     * [m], sul rettangolo [rect] dell'annotazione. Null se il riquadro è vuoto.
     */
    fun fit(bbox: FloatArray, m: FloatArray, rect: FloatArray): FloatArray? {
        val corners = listOf(bbox[0] to bbox[1], bbox[2] to bbox[1], bbox[0] to bbox[3], bbox[2] to bbox[3])
        val xs = corners.map { (x, y) -> m[0] * x + m[2] * y + m[4] }
        val ys = corners.map { (x, y) -> m[1] * x + m[3] * y + m[5] }
        val w = xs.max() - xs.min()
        val h = ys.max() - ys.min()
        if (w <= 0f || h <= 0f) return null
        val sx = (rect[2] - rect[0]) / w
        val sy = (rect[3] - rect[1]) / h
        return floatArrayOf(sx, 0f, 0f, sy, rect[0] - xs.min() * sx, rect[1] - ys.min() * sy)
    }

    /** La /Matrix dell'aspetto [a b c d e f], o l'identità. */
    private fun matrixOf(ap: PDAppearanceStream): FloatArray {
        val arr = ap.cosObject.getDictionaryObject(COSName.MATRIX) as? COSArray
        if (arr == null || arr.size() != 6) return floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        return FloatArray(6) { (arr.getObject(it) as? COSNumber)?.floatValue() ?: if (it == 0 || it == 3) 1f else 0f }
    }
}

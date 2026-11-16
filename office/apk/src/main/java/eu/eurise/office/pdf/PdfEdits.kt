package eu.eurise.office.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import java.util.Calendar
import java.util.UUID

/** Colori delle annotazioni (tavola OffPdf): Sabbia, Azzurro, Salvia, Rosa; più il blu notte per penna e firma. */
enum class InkColor(val r: Float, val g: Float, val b: Float) {
    Sabbia(0.914f, 0.812f, 0.557f),
    Azzurro(0.663f, 0.749f, 0.890f),
    Salvia(0.706f, 0.804f, 0.733f),
    Rosa(0.929f, 0.718f, 0.741f),
    Notte(0.122f, 0.165f, 0.267f),
}

/** Un'annotazione della pagina come la vede Office: per toccarla, leggerla, cambiarla o toglierla. */
data class Mark(val index: Int, val subtype: String, val rect: PdfRect, val contents: String?)

/**
 * Le annotazioni che Office scrive nel PDF. Ognuna porta il suo aspetto (stream /AP) disegnato qui: così si vede uguale
 * in Office, nel lettore di Android e in ogni altro programma, senza dipendere da come ciascuno le ridisegna.
 * Le annotazioni si costruiscono a livello di dizionario PDF: è la parte più stabile di PDFBox tra le versioni.
 */
object PdfEdits {
    private const val AUTHOR = "Eurise Office"

    // Flag delle annotazioni (specifica PDF, tabella 165): in PdfBox-Android le costanti di PDAnnotation sono private.
    private const val FLAG_PRINT = 4
    private const val FLAG_NO_ZOOM = 8
    private const val FLAG_NO_ROTATE = 16

    fun highlight(doc: PDDocument, page: PDPage, lines: List<PdfRect>, color: InkColor, underline: Boolean): PDAnnotation? {
        if (lines.isEmpty()) return null
        val rect = lines.reduce { a, b -> a.union(b) }.inflate(1f)
        val quads = COSArray()
        lines.forEach { r ->
            // Ordine dei punti dei QuadPoints come lo scrive Acrobat: alto-sinistra, alto-destra, basso-sinistra, basso-destra.
            listOf(r.left, r.top, r.right, r.top, r.left, r.bottom, r.right, r.bottom).forEach { quads.add(COSFloat(it)) }
        }
        val dict = base(if (underline) "Underline" else "Highlight", rect, color)
        dict.setItem(COSName.getPDFName("QuadPoints"), quads)
        val ap = appearance(doc, rect) { cs ->
            if (underline) {
                cs.setStrokingColor(color.r * 0.55f, color.g * 0.55f, color.b * 0.55f)
                lines.forEach { r ->
                    val w = maxOf(1f, r.height * 0.07f)
                    cs.setLineWidth(w)
                    cs.moveTo(r.left, r.bottom + w)
                    cs.lineTo(r.right, r.bottom + w)
                    cs.stroke()
                }
            } else {
                val gs = PDExtendedGraphicsState()
                gs.blendMode = BlendMode.MULTIPLY
                gs.nonStrokingAlphaConstant = 1f
                cs.setGraphicsStateParameters(gs)
                cs.setNonStrokingColor(color.r, color.g, color.b)
                lines.forEach { r -> cs.addRect(r.left, r.bottom, r.width, r.height) }
                cs.fill()
            }
        }
        return add(page, dict, ap)
    }

    /** Penna e firma: una o più linee a mano libera (punti PDF). */
    fun ink(doc: PDDocument, page: PDPage, strokes: List<List<Pair<Float, Float>>>, color: InkColor, width: Float, contents: String? = null): PDAnnotation? {
        val points = strokes.flatten()
        if (points.size < 2) return null
        val rect = PdfRect(points.minOf { it.first }, points.minOf { it.second }, points.maxOf { it.first }, points.maxOf { it.second }).inflate(width + 1f)
        val dict = base("Ink", rect, color)
        val inkList = COSArray()
        strokes.forEach { s -> inkList.add(COSArray().also { a -> s.forEach { (x, y) -> a.add(COSFloat(x)); a.add(COSFloat(y)) } }) }
        dict.setItem(COSName.getPDFName("InkList"), inkList)
        dict.setItem(COSName.getPDFName("BS"), COSDictionary().also { it.setItem(COSName.W, COSFloat(width)) })
        if (contents != null) dict.setString(COSName.CONTENTS, contents)
        val ap = appearance(doc, rect) { cs ->
            cs.setStrokingColor(color.r, color.g, color.b)
            cs.setLineWidth(width)
            cs.setLineCapStyle(1)
            cs.setLineJoinStyle(1)
            strokes.filter { it.size > 1 }.forEach { s ->
                cs.moveTo(s[0].first, s[0].second)
                // Curve morbide tra i punti medi: il tratto non sembra spezzato.
                for (i in 1 until s.size - 1) {
                    val mx = (s[i].first + s[i + 1].first) / 2f
                    val my = (s[i].second + s[i + 1].second) / 2f
                    cs.curveTo(s[i].first, s[i].second, s[i].first, s[i].second, mx, my)
                }
                cs.lineTo(s.last().first, s.last().second)
                cs.stroke()
            }
        }
        return add(page, dict, ap)
    }

    /** Nota (post-it): l'icona sulla pagina, il testo nell'annotazione. */
    fun note(doc: PDDocument, page: PDPage, x: Float, y: Float, text: String, color: InkColor): PDAnnotation? {
        val size = 22f
        val rect = PdfRect(x, y - size, x + size, y)
        val dict = base("Text", rect, color)
        dict.setString(COSName.CONTENTS, text)
        dict.setName(COSName.getPDFName("Name"), "Comment")
        dict.setInt(COSName.F, FLAG_PRINT or FLAG_NO_ZOOM or FLAG_NO_ROTATE)
        val ap = appearance(doc, rect) { cs ->
            cs.setNonStrokingColor(color.r, color.g, color.b)
            cs.setStrokingColor(color.r * 0.5f, color.g * 0.5f, color.b * 0.5f)
            cs.setLineWidth(1.2f)
            val l = rect.left + 2f
            val b = rect.bottom + 5f
            val w = size - 4f
            val h = size - 8f
            cs.moveTo(l, b + h)
            cs.lineTo(l + w, b + h)
            cs.lineTo(l + w, b)
            cs.lineTo(l + w * 0.45f, b)
            cs.lineTo(l + w * 0.2f, b - 4f)
            cs.lineTo(l + w * 0.25f, b)
            cs.lineTo(l, b)
            cs.closePath()
            cs.fillAndStroke()
        }
        return add(page, dict, ap)
    }

    /** Testo libero sulla pagina (anche «Modifica › Testo» e le risposte nei moduli senza campi). */
    fun freeText(doc: PDDocument, page: PDPage, x: Float, y: Float, text: String, fontSize: Float, color: InkColor): PDAnnotation? {
        val clean = winAnsi(text)
        if (clean.isBlank()) return null
        val font = PDType1Font.HELVETICA
        val lines = clean.split('\n')
        val width = lines.maxOf { font.getStringWidth(it) / 1000f * fontSize } + 4f
        val height = lines.size * fontSize * 1.2f + 4f
        val rect = PdfRect(x, y - height, x + width, y)
        val dict = base("FreeText", rect, color)
        dict.setString(COSName.CONTENTS, clean)
        dict.setString(COSName.DA, "/Helv %.1f Tf %.3f %.3f %.3f rg".format(java.util.Locale.ROOT, fontSize, color.r, color.g, color.b))
        dict.removeItem(COSName.C)
        val ap = appearance(doc, rect) { cs ->
            cs.beginText()
            cs.setFont(font, fontSize)
            cs.setNonStrokingColor(color.r, color.g, color.b)
            cs.newLineAtOffset(rect.left + 2f, rect.top - fontSize - 1f)
            lines.forEachIndexed { i, line ->
                if (i > 0) cs.newLineAtOffset(0f, -fontSize * 1.2f)
                cs.showText(line)
            }
            cs.endText()
        }
        return add(page, dict, ap)
    }

    /** Rettangolo pieno (bianco per «Copri», nero per oscurare a vista prima della vera rimozione). */
    fun cover(doc: PDDocument, page: PDPage, r: PdfRect, white: Boolean): PDAnnotation? {
        val dict = base("Square", r, if (white) null else InkColor.Notte)
        dict.setItem(COSName.getPDFName("IC"), COSArray().also { a -> repeat(3) { a.add(COSFloat(if (white) 1f else 0f)) } })
        dict.setItem(COSName.getPDFName("BS"), COSDictionary().also { it.setItem(COSName.W, COSInteger.ZERO) })
        val ap = appearance(doc, r) { cs ->
            cs.setNonStrokingColor(if (white) 1f else 0f, if (white) 1f else 0f, if (white) 1f else 0f)
            cs.addRect(r.left, r.bottom, r.width, r.height)
            cs.fill()
        }
        return add(page, dict, ap)
    }

    /** Le annotazioni di una pagina che Office sa mostrare e togliere (non i collegamenti né i campi dei moduli). */
    fun marks(page: PDPage): List<Mark> = runCatching {
        page.annotations.mapIndexedNotNull { i, a ->
            val subtype = a.subtype ?: return@mapIndexedNotNull null
            if (subtype == "Link" || subtype == "Widget" || subtype == "Popup") return@mapIndexedNotNull null
            val r = a.rectangle ?: return@mapIndexedNotNull null
            Mark(i, subtype, PdfRect(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY), a.contents)
        }
    }.getOrDefault(emptyList())

    fun remove(page: PDPage, index: Int) {
        val list = page.annotations
        if (index in list.indices) {
            list.removeAt(index)
            page.annotations = list
        }
    }

    fun setContents(page: PDPage, index: Int, text: String) {
        page.annotations.getOrNull(index)?.contents = text
    }

    /** Testo che il carattere Helvetica standard sa scrivere (WinAnsi): accenti italiani ed euro sì, il resto diventa «?». */
    fun winAnsi(text: String): String = buildString {
        text.forEach { c ->
            append(
                when {
                    c == '\n' -> c
                    runCatching { PDType1Font.HELVETICA.encode(c.toString()) }.isSuccess -> c
                    c == '’' || c == '‘' -> '\''
                    c == '“' || c == '”' -> '"'
                    else -> '?'
                },
            )
        }
    }

    private fun base(subtype: String, rect: PdfRect, color: InkColor?): COSDictionary {
        val dict = COSDictionary()
        dict.setItem(COSName.TYPE, COSName.ANNOT)
        dict.setName(COSName.SUBTYPE, subtype)
        dict.setItem(COSName.RECT, PDRectangle(rect.left, rect.bottom, rect.width, rect.height).cosArray)
        if (color != null) dict.setItem(COSName.C, COSArray().also { a -> listOf(color.r, color.g, color.b).forEach { a.add(COSFloat(it)) } })
        dict.setInt(COSName.F, FLAG_PRINT)
        dict.setString(COSName.T, AUTHOR)
        dict.setString(COSName.NM, UUID.randomUUID().toString())
        dict.setDate(COSName.M, Calendar.getInstance())
        dict.setDate(COSName.getPDFName("CreationDate"), Calendar.getInstance())
        return dict
    }

    private fun appearance(doc: PDDocument, rect: PdfRect, draw: (PDPageContentStream) -> Unit): PDAppearanceStream {
        val stream = PDAppearanceStream(doc)
        stream.bBox = PDRectangle(rect.left, rect.bottom, rect.width, rect.height)
        stream.resources = PDResources()
        PDPageContentStream(doc, stream).use(draw)
        return stream
    }

    private fun add(page: PDPage, dict: COSDictionary, ap: PDAppearanceStream): PDAnnotation {
        val annotation = PDAnnotation.createAnnotation(dict)
        annotation.appearance = PDAppearanceDictionary().also { it.setNormalAppearance(ap) }
        val list = page.annotations
        list.add(annotation)
        page.annotations = list
        return annotation
    }

    private fun PdfRect.inflate(d: Float) = PdfRect(left - d, bottom - d, right + d, top + d)
}

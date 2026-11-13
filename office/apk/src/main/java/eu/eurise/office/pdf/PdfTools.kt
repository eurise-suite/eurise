package eu.eurise.office.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.security.SecureRandom

/** Un campo di un modulo PDF (tavola OffPdf, «Moduli»). */
data class FormField(val name: String, val label: String, val kind: Kind, val value: String, val options: List<String>) {
    enum class Kind { Text, Check, Choice }
}

/** Qualità di «Riduci il peso» (tavola OffPdfCondividi). */
enum class Weight(val maxSide: Int, val jpegQuality: Float) {
    Screen(1600, 0.7f),
    Print(2800, 0.85f),
    Pec(1200, 0.55f),
}

/** Pagine (tavola OffPdfPagine) e preparazione alla condivisione (OffPdfCondividi). Tutto sul telefono. */
object PdfTools {
    // --- Pagine ---------------------------------------------------------------------------------------------------

    fun rotate(doc: PDDocument, indices: Collection<Int>) = indices.forEach { i ->
        val page = doc.getPage(i)
        page.rotation = (page.rotation + 90) % 360
    }

    fun delete(doc: PDDocument, indices: Collection<Int>) =
        indices.sortedDescending().forEach { if (doc.numberOfPages > 1) doc.removePage(it) }

    fun duplicate(doc: PDDocument, indices: Collection<Int>) = indices.sortedDescending().forEach { i ->
        val page = doc.getPage(i)
        val copy = PDPage(COSDictionary(page.cosObject))
        doc.pages.insertAfter(copy, page)
    }

    /** Sposta la pagina [from] in posizione [to] (indici da zero). */
    fun move(doc: PDDocument, from: Int, to: Int) {
        if (from == to || from !in 0 until doc.numberOfPages) return
        val page = doc.getPage(from)
        doc.pages.remove(page)
        val target = to.coerceIn(0, doc.numberOfPages)
        if (target >= doc.numberOfPages) doc.pages.add(page) else doc.pages.insertBefore(page, doc.getPage(target))
    }

    /** Un PDF nuovo con le sole pagine [indices] (Estrai; Dividi usa due estrazioni). */
    fun extract(doc: PDDocument, indices: List<Int>, target: File) {
        PDDocument().use { out ->
            indices.sorted().forEach { out.importPage(doc.getPage(it)) }
            out.save(target)
        }
    }

    /** Inserisce dopo [after] (-1 = in testa) tutte le pagine di [other], che resta aperto finché [doc] non è salvato. */
    fun insertPdf(doc: PDDocument, other: PDDocument, after: Int) {
        var anchor = after
        for (p in other.pages.toList()) {
            val imported = doc.importPage(p)
            doc.pages.remove(imported)
            if (anchor < 0) {
                if (doc.numberOfPages == 0) doc.pages.add(imported) else doc.pages.insertBefore(imported, doc.getPage(0))
            } else {
                doc.pages.insertAfter(imported, doc.getPage(anchor))
            }
            anchor++
        }
    }

    /** Una pagina con una foto o una scansione, nella misura della pagina vicina (A4 se il documento è vuoto). */
    fun insertImage(doc: PDDocument, bitmap: Bitmap, after: Int) {
        val near = if (doc.numberOfPages > 0) doc.getPage(after.coerceIn(0, doc.numberOfPages - 1)).mediaBox else PDRectangle.A4
        val portrait = bitmap.height >= bitmap.width
        val box = if (portrait == (near.height >= near.width)) PDRectangle(near.width, near.height) else PDRectangle(near.height, near.width)
        val page = PDPage(box)
        val image = JPEGFactory.createFromImage(doc, bitmap, 0.85f)
        val scale = minOf(box.width / bitmap.width, box.height / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        PDPageContentStream(doc, page).use { it.drawImage(image, (box.width - w) / 2f, (box.height - h) / 2f, w, h) }
        if (after < 0 || doc.numberOfPages == 0) {
            if (doc.numberOfPages == 0) doc.pages.add(page) else doc.pages.insertBefore(page, doc.getPage(0))
        } else {
            doc.pages.insertAfter(page, doc.getPage(after.coerceAtMost(doc.numberOfPages - 1)))
        }
    }

    /** Una foto sulla pagina (Modifica › Immagine), larga [width] punti e centrata nel punto toccato. */
    fun drawImage(doc: PDDocument, page: PDPage, bitmap: Bitmap, x: Float, y: Float, width: Float) {
        val image = JPEGFactory.createFromImage(doc, bitmap, 0.85f)
        val height = width * bitmap.height / bitmap.width.toFloat()
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use {
            it.drawImage(image, x - width / 2f, y - height / 2f, width, height)
        }
    }

    fun insertBlank(doc: PDDocument, after: Int) {
        val near = if (doc.numberOfPages > 0) doc.getPage(after.coerceIn(0, doc.numberOfPages - 1)).mediaBox else PDRectangle.A4
        val page = PDPage(PDRectangle(near.width, near.height))
        if (after < 0) doc.pages.insertBefore(page, doc.getPage(0)) else doc.pages.insertAfter(page, doc.getPage(after))
    }

    // --- Moduli ---------------------------------------------------------------------------------------------------

    fun fields(doc: PDDocument): List<FormField> {
        val form = doc.documentCatalog.acroForm ?: return emptyList()
        return form.fieldTree.mapNotNull { f ->
            val label = f.alternateFieldName?.takeIf { it.isNotBlank() } ?: f.partialName ?: f.fullyQualifiedName
            when (f) {
                is PDTextField -> FormField(f.fullyQualifiedName, label, FormField.Kind.Text, f.value.orEmpty(), emptyList())
                is PDCheckBox -> FormField(f.fullyQualifiedName, label, FormField.Kind.Check, if (f.isChecked) "1" else "", emptyList())
                is PDChoice -> FormField(f.fullyQualifiedName, label, FormField.Kind.Choice, f.value.firstOrNull().orEmpty(), f.optionsDisplayValues)
                is PDRadioButton -> FormField(f.fullyQualifiedName, label, FormField.Kind.Choice, f.value.orEmpty(), f.onValues.toList())
                else -> null
            }
        }
    }

    fun setField(doc: PDDocument, name: String, value: String) {
        val field = doc.documentCatalog.acroForm?.getField(name) ?: return
        when (field) {
            is PDTextField -> field.value = value
            is PDCheckBox -> if (value.isNotEmpty()) field.check() else field.unCheck()
            is PDChoice -> field.setValue(value)
            is PDRadioButton -> field.value = value
        }
    }

    // --- Prima di condividere ---------------------------------------------------------------------------------------

    /** Ricomprime le foto delle pagine (le immagini con trasparenza restano com'erano). */
    fun reduceWeight(doc: PDDocument, weight: Weight) {
        for (page in doc.pages) {
            val resources = page.resources ?: continue
            for (name in resources.xObjectNames.toList()) {
                val image = runCatching { resources.getXObject(name) as? PDImageXObject }.getOrNull() ?: continue
                if (image.softMask != null || image.isStencil) continue
                val bitmap = runCatching { image.image }.getOrNull() ?: continue
                val side = maxOf(bitmap.width, bitmap.height)
                val scaled = if (side > weight.maxSide) {
                    val k = weight.maxSide.toFloat() / side
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt().coerceAtLeast(1), (bitmap.height * k).toInt().coerceAtLeast(1), true)
                } else bitmap
                val replacement = runCatching { JPEGFactory.createFromImage(doc, scaled, weight.jpegQuality) }.getOrNull() ?: continue
                resources.put(name, replacement)
            }
        }
    }

    /** Autore, programma, date e metadati XMP: via. Con [notes] anche le note e i commenti (non evidenziazioni e firme). */
    fun removeHidden(doc: PDDocument, notes: Boolean) {
        doc.documentInformation = PDDocumentInformation()
        doc.documentCatalog.metadata = null
        if (notes) {
            for (page in doc.pages) {
                val kept = page.annotations.filter { it.subtype != "Text" && it.subtype != "Popup" }
                page.annotations = kept
            }
        }
    }

    /**
     * Blocca campi e annotazioni: i campi diventano testo della pagina, le annotazioni con un aspetto vengono disegnate
     * nella pagina e tolte (restano i collegamenti). Chi riceve il file non può più cambiarli.
     */
    fun flatten(doc: PDDocument) {
        runCatching { doc.documentCatalog.acroForm?.flatten() }
        for (page in doc.pages) {
            val annotations = page.annotations
            val kept = annotations.filter { it.subtype == "Link" || it.normalAppearanceStream == null }
            val drawn = annotations.filter { it !in kept }
            if (drawn.isEmpty()) continue
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                drawn.forEach { a ->
                    val ap: PDAppearanceStream = a.normalAppearanceStream
                    val rect = a.rectangle ?: return@forEach
                    val bbox = ap.bBox ?: return@forEach
                    if (bbox.width <= 0f || bbox.height <= 0f) return@forEach
                    val sx = rect.width / bbox.width
                    val sy = rect.height / bbox.height
                    cs.saveGraphicsState()
                    cs.transform(Matrix(sx, 0f, 0f, sy, rect.lowerLeftX - bbox.lowerLeftX * sx, rect.lowerLeftY - bbox.lowerLeftY * sy))
                    cs.drawForm(ap)
                    cs.restoreGraphicsState()
                }
            }
            page.annotations = kept
        }
    }

    /**
     * Oscuramento vero (tavola OffPdfCondividi: «spariscono davvero dal file»): ogni pagina con qualcosa da oscurare
     * diventa un'immagine con i riquadri neri, senza più il testo sotto. [boxes]: per indice di pagina, i riquadri PDF.
     * [rendered] è il file da cui disegnare le pagine (lo stato attuale del documento).
     */
    fun redact(doc: PDDocument, rendered: File, boxes: Map<Int, List<PdfRect>>, dpi: Int = 200) {
        if (boxes.isEmpty()) return
        ParcelFileDescriptor.open(rendered, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                for ((index, rects) in boxes) {
                    if (rects.isEmpty() || index >= renderer.pageCount) continue
                    val old = doc.getPage(index)
                    val crop = old.cropBox
                    val geometry = PageGeometry(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, old.rotation)
                    val bitmap = renderer.openPage(index).use { p ->
                        val w = (p.width * dpi / 72f).toInt().coerceAtMost(3000)
                        val h = (p.height * w / p.width.toFloat()).toInt()
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                            b.eraseColor(Color.WHITE)
                            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        }
                    }
                    val canvas = Canvas(bitmap)
                    val paint = Paint().apply { color = Color.BLACK }
                    rects.forEach { r ->
                        val v = geometry.toViewRect(r)
                        canvas.drawRect(v[0] * bitmap.width - 2, v[1] * bitmap.height - 2, v[2] * bitmap.width + 2, v[3] * bitmap.height + 2, paint)
                    }
                    // La pagina nuova ha la misura della pagina come appare (già ruotata) e nessuna rotazione.
                    val shown = if (old.rotation == 90 || old.rotation == 270) PDRectangle(crop.height, crop.width) else PDRectangle(crop.width, crop.height)
                    val page = PDPage(shown)
                    val image = LosslessFactory.createFromImage(doc, bitmap)
                    PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, shown.width, shown.height) }
                    doc.pages.insertBefore(page, old)
                    doc.pages.remove(old)
                    bitmap.recycle()
                }
            }
        }
    }

    /** Password per aprire il file (AES a 256 bit); stampa e copia restano permesse a chi la conosce. */
    fun protect(doc: PDDocument, password: String) {
        val owner = randomPassword(24)
        val policy = StandardProtectionPolicy(owner, password, AccessPermission())
        policy.encryptionKeyLength = 256
        doc.protect(policy)
    }

    /** Password leggibile: senza lettere che si confondono (l/1, O/0). */
    fun randomPassword(length: Int = 16): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..length).map { alphabet[random.nextInt(alphabet.length)] }.chunked(4).joinToString("-") { it.joinToString("") }
    }
}

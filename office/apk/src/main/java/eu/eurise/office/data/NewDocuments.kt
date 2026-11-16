package eu.eurise.office.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Un documento appena creato: l'Uri (con lettura e scrittura) e il nome. */
data class Created(val uri: Uri, val name: String, val kind: DocKind)

/**
 * «Nuovo» della schermata iniziale: Testo, Foglio, Presentazione (ODF vuoti, il formato libero di base) e PDF (una pagina
 * A4 bianca). Vanno in Documenti › Eurise, dove li vedono Eurise File e le altre app; prima di Android 10 nella memoria di
 * Office. Il nome porta la data, come quelli dello Scanner: «2026-11-13 Documento.odt».
 */
object NewDocuments {
    suspend fun create(context: Context, kind: DocKind, title: String): Created = withContext(Dispatchers.IO) {
        val (ext, mime) = when (kind) {
            DocKind.Text -> "odt" to "application/vnd.oasis.opendocument.text"
            DocKind.Sheet -> "ods" to "application/vnd.oasis.opendocument.spreadsheet"
            DocKind.Slides -> "odp" to "application/vnd.oasis.opendocument.presentation"
            DocKind.Pdf -> "pdf" to "application/pdf"
            else -> error("tipo non creabile: $kind")
        }
        val name = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + " " + title + "." + ext
        val bytes = if (kind == DocKind.Pdf) blankPdf() else odf(kind)
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Eurise")
            }
            val target = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: error("MediaStore non ha creato il file")
            context.contentResolver.openOutputStream(target)!!.use { it.write(bytes) }
            target
        } else {
            val dir = File(context.filesDir, "documenti").apply { mkdirs() }
            val file = File(dir, name)
            file.writeBytes(bytes)
            FileProvider.getUriForFile(context, context.packageName + ".files", file)
        }
        Created(uri, name, kind)
    }

    /** Una pagina A4 bianca. */
    fun blankPdf(): ByteArray = PDDocument().use { doc ->
        doc.addPage(PDPage(PDRectangle.A4))
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    /** ODF vuoto valido: «mimetype» per primo e non compresso, poi manifest e contenuto (OpenDocument 1.3). */
    fun odf(kind: DocKind): ByteArray {
        val mime = when (kind) {
            DocKind.Text -> "application/vnd.oasis.opendocument.text"
            DocKind.Sheet -> "application/vnd.oasis.opendocument.spreadsheet"
            DocKind.Slides -> "application/vnd.oasis.opendocument.presentation"
            else -> error("non ODF: $kind")
        }
        val body = when (kind) {
            DocKind.Text -> "<office:text><text:p/></office:text>"
            DocKind.Sheet -> "<office:spreadsheet><table:table table:name=\"Foglio1\"><table:table-column/>" +
                "<table:table-row><table:table-cell/></table:table-row></table:table></office:spreadsheet>"
            else -> "<office:presentation><draw:page draw:name=\"page1\"/></office:presentation>"
        }
        val content = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" xmlns:presentation="urn:oasis:names:tc:opendocument:xmlns:presentation:1.0" office:version="1.3"><office:body>$body</office:body></office:document-content>"""
        val manifest = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.3">
 <manifest:file-entry manifest:full-path="/" manifest:media-type="$mime"/>
 <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
</manifest:manifest>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val mimeBytes = mime.toByteArray(Charsets.US_ASCII)
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                crc = CRC32().apply { update(mimeBytes) }.value
            })
            zip.write(mimeBytes)
            zip.closeEntry()
            put(zip, "META-INF/manifest.xml", manifest)
            put(zip, "content.xml", content)
        }
        return out.toByteArray()
    }

    private fun put(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}

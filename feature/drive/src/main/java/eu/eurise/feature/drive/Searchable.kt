package eu.eurise.feature.drive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import eu.eurise.core.data.docs.ReadClient
import eu.eurise.core.data.docs.SearchablePdf
import eu.eurise.core.data.trips.PdfText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * «Aggiungi il testo» a un PDF fatto di sole immagini (una scansione di un'altra app): le pagine si leggono sul
 * telefono con Eurise Leggi e accanto all'originale nasce una copia cercabile. L'originale non si tocca.
 */
internal object Searchable {
    sealed interface Outcome {
        data class Done(val name: String) : Outcome
        data object AlreadyText : Outcome
        data class NotRead(val result: ReadClient.Result) : Outcome
        data object Failed : Outcome
    }

    private const val MAX_PAGES = 40
    /** 200 punti per pollice: abbastanza per leggere, senza bitmap enormi in memoria. */
    private const val SCALE = 200f / 72f

    suspend fun make(context: Context, data: DriveData, item: DriveItem): Outcome {
        val work = File(context.cacheDir, "searchable").apply { deleteRecursively(); mkdirs() }
        try {
            val source = data.localCopy(item)
            val images = withContext(Dispatchers.IO) {
                if (PdfText.extract(source.readBytes()).isNotBlank()) return@withContext null
                render(source, work)
            } ?: return Outcome.AlreadyText
            if (images.isEmpty()) return Outcome.Failed
            val read = ReadClient.read(context, images)
            if (read !is ReadClient.Result.Pages) return Outcome.NotRead(read)
            val name = item.name.removeSuffix(".pdf").removeSuffix(".PDF") + " (con testo).pdf"
            withContext(Dispatchers.IO) {
                val pages = images.mapIndexed { i, f -> SearchablePdf.pageFromJpeg(f.readBytes(), read.pages[i].words, read.pages[i].width) ?: throw java.io.IOException() }
                val out = File(work, "out.pdf")
                out.outputStream().buffered().use { SearchablePdf.write(pages, it, item.name.removeSuffix(".pdf")) }
                data.writeBeside(item, name, out)
            }
            return Outcome.Done(name)
        } catch (e: java.io.IOException) {
            return Outcome.Failed
        } catch (e: OutOfMemoryError) {
            return Outcome.Failed
        } finally {
            work.deleteRecursively()
        }
    }

    private fun render(pdf: File, dir: File): List<File> = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
            (0 until minOf(renderer.pageCount, MAX_PAGES)).map { i ->
                renderer.openPage(i).use { page ->
                    val bmp = Bitmap.createBitmap((page.width * SCALE).toInt(), (page.height * SCALE).toInt(), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    File(dir, "p$i.jpg").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }; bmp.recycle() }
                }
            }
        }
    }
}

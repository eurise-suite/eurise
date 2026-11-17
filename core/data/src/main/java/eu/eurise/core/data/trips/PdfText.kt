package eu.eurise.core.data.trips

/**
 * Il testo di un PDF: il lettore vive in `core/pdf` (17 novembre, lo usa anche la Chat per i promemoria di Millewin);
 * qui resta il nome di sempre, così chi lo usava non cambia.
 */
object PdfText {
    fun extract(bytes: ByteArray, maxPages: Int = 20): String = eu.eurise.core.pdf.PdfText.extract(bytes, maxPages)
}

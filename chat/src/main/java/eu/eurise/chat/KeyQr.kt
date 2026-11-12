package eu.eurise.chat

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Il QR della chiave di recupero per la carta da stampare: un disegno SVG (nitido a ogni stampa, niente immagini), un
 * quadratino per modulo. Correzione d'errore media: regge un foglio un po' sciupato.
 */
object KeyQr {
    fun svg(text: String): String {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
        val path = StringBuilder()
        for (y in 0 until m.height) for (x in 0 until m.width) if (m.get(x, y)) path.append("M$x ${y}h1v1h-1z")
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${m.width} ${m.height}" shape-rendering="crispEdges">""" +
            """<rect width="100%" height="100%" fill="#fff"/><path d="$path" fill="#1E1D1B"/></svg>"""
    }
}

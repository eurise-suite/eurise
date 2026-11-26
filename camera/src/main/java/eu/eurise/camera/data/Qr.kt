package eu.eurise.camera.data

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Codici QR letti dal mirino (26 novembre 2026), con zxing (aperto, già nella suite con l'impronta). Tutto sul telefono:
 * nessuna rete. Un indirizzo web si apre solo se è http o https e se ne mostra sempre il dominio prima di aprirlo.
 */
object Qr {
    enum class Kind { Link, Wifi, Text }

    /** Il testo del primo codice QR nei pixel ARGB [px] ([w]×[h]), o null. */
    fun decode(px: IntArray, w: Int, h: Int): String? = runCatching {
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, px)))
        QRCodeReader().decode(bitmap, mapOf(DecodeHintType.CHARACTER_SET to "UTF-8")).text
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun kind(text: String): Kind = when {
        Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(text.trim()) -> Kind.Link
        text.startsWith("WIFI:", ignoreCase = true) -> Kind.Wifi
        else -> Kind.Text
    }

    /** Il dominio di un indirizzo («https://www.comune.it/x» → «www.comune.it»): è quello che si mostra. */
    fun host(text: String): String = text.trim().substringAfter("://").substringBefore('/').substringBefore('?')
        .substringAfterLast('@').lowercase()

    /** Il nome della rete di un codice Wi-Fi («WIFI:T:WPA;S:Casa;P:…;;» → «Casa»), o null. */
    fun wifiName(text: String): String? = Regex("(?:^WIFI:|;)S:((?:\\\\.|[^;])*)", RegexOption.IGNORE_CASE).find(text)
        ?.groupValues?.get(1)?.replace(Regex("\\\\(.)"), "$1")?.takeIf { it.isNotEmpty() }

    /** La password di un codice Wi-Fi, o null (si copia, non si mostra). */
    fun wifiPassword(text: String): String? = Regex("(?:^WIFI:|;)P:((?:\\\\.|[^;])*)", RegexOption.IGNORE_CASE).find(text)
        ?.groupValues?.get(1)?.replace(Regex("\\\\(.)"), "$1")?.takeIf { it.isNotEmpty() }
}

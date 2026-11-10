package eu.eurise.password.logic

import java.time.LocalDate

/**
 * Lettura delle carte contactless (EMV) avvicinandole al telefono: solo numero, scadenza e, se la carta lo dice,
 * intestatario. Nessun pagamento, nessun PIN, nessun dato che esce dal telefono; il CVV non sta nel chip.
 * Qui c'è solo la logica pura (comandi e risposte), provata con i test; il dialogo con la carta lo fa [CardNfc].
 */
object Emv {

    /** Un elemento BER-TLV: etichetta (come intero, es. 0x5F24) e valore. */
    data class Tlv(val tag: Int, val value: ByteArray, val constructed: Boolean)

    data class Card(val number: String, val expiry: String, val holder: String)

    /** Tutti gli elementi, anche quelli annidati, in ordine. */
    fun parse(data: ByteArray): List<Tlv> {
        val out = mutableListOf<Tlv>()
        parseInto(data, 0, data.size, out)
        return out
    }

    private fun parseInto(d: ByteArray, from: Int, to: Int, out: MutableList<Tlv>) {
        var i = from
        while (i < to) {
            val first = d[i].toInt() and 0xFF
            if (first == 0x00 || first == 0xFF) { i++; continue } // riempitivi
            var tag = first
            i++
            if (first and 0x1F == 0x1F) {
                while (i < to) {
                    val b = d[i].toInt() and 0xFF
                    tag = (tag shl 8) or b
                    i++
                    if (b and 0x80 == 0) break
                }
            }
            if (i >= to) return
            var len = d[i].toInt() and 0xFF
            i++
            if (len and 0x80 != 0) {
                val n = len and 0x7F
                if (n == 0 || n > 3 || i + n > to) return
                len = 0
                repeat(n) { len = (len shl 8) or (d[i].toInt() and 0xFF); i++ }
            }
            if (i + len > to) return
            val constructed = (first and 0x20) != 0
            out += Tlv(tag, d.copyOfRange(i, i + len), constructed)
            if (constructed) parseInto(d, i, i + len, out)
            i += len
        }
    }

    fun find(list: List<Tlv>, tag: Int): ByteArray? = list.firstOrNull { it.tag == tag }?.value

    // --- Comandi ---

    val SELECT_PPSE: ByteArray = select("2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII))

    fun select(aid: ByteArray): ByteArray = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)

    fun readRecord(sfi: Int, record: Int): ByteArray = byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), ((sfi shl 3) or 4).toByte(), 0x00)

    /** GET PROCESSING OPTIONS con i dati chiesti dalla carta (PDOL). */
    fun gpo(pdolData: ByteArray): ByteArray {
        val body = byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData
        return byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, body.size.toByte()) + body + byteArrayOf(0x00)
    }

    /** Le applicazioni di pagamento della carta (AID), nell'ordine di priorità che dichiara. */
    fun aids(ppse: ByteArray): List<ByteArray> {
        val list = parse(ppse)
        val apps = mutableListOf<Pair<Int, ByteArray>>()
        var current: ByteArray? = null
        for (t in list) {
            when (t.tag) {
                0x4F -> { current?.let { apps += 99 to it }; current = t.value }
                0x87 -> current?.let { apps += ((t.value.firstOrNull()?.toInt() ?: 99) and 0x0F) to it; current = null }
            }
        }
        current?.let { apps += 99 to it }
        return apps.sortedBy { it.first }.map { it.second }.distinctBy { it.toList() }
    }

    /** L'elenco PDOL (etichetta, lunghezza) dalla risposta alla selezione dell'applicazione; vuoto se non c'è. */
    fun pdol(fci: ByteArray): List<Pair<Int, Int>> {
        val raw = find(parse(fci), 0x9F38) ?: return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < raw.size) {
            var tag = raw[i].toInt() and 0xFF
            i++
            if (tag and 0x1F == 0x1F) {
                while (i < raw.size) {
                    val b = raw[i].toInt() and 0xFF
                    tag = (tag shl 8) or b
                    i++
                    if (b and 0x80 == 0) break
                }
            }
            if (i >= raw.size) break
            out += tag to (raw[i].toInt() and 0xFF)
            i++
        }
        return out
    }

    /**
     * I dati per il PDOL come li darebbe un lettore che vuole solo leggere: importo zero, Italia ed euro, data di
     * oggi, numero casuale [random]. Ciò che non serve resta a zero.
     */
    fun pdolData(pdol: List<Pair<Int, Int>>, today: LocalDate, random: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()
        for ((tag, len) in pdol) {
            val v: ByteArray = when (tag) {
                0x9F66 -> byteArrayOf(0x36, 0x00, 0x40, 0x00) // il lettore sa leggere senza contatto (qVSDC/EMV)
                0x9F1A, 0x5F2A -> if (tag == 0x9F1A) byteArrayOf(0x03, 0x80.toByte()) else byteArrayOf(0x09, 0x78)
                0x9A -> bcd("%02d%02d%02d".format(today.year % 100, today.monthValue, today.dayOfMonth))
                0x9F37 -> random
                0x9F35 -> byteArrayOf(0x22)
                0x9F33 -> byteArrayOf(0xE0.toByte(), 0x08, 0x08)
                0x9F40 -> byteArrayOf(0x8E.toByte(), 0x00, 0xB0.toByte(), 0x50, 0x05)
                else -> ByteArray(0)
            }
            repeat(len) { k -> out += if (k < v.size) v[k] else 0 }
        }
        return out.toByteArray()
    }

    /** I record da leggere (SFI, record) dall'AFL. */
    fun afl(gpoResponse: ByteArray): List<Pair<Int, Int>> {
        val list = parse(gpoResponse)
        val raw = find(list, 0x94) ?: find(list, 0x80)?.let { if (it.size > 2) it.copyOfRange(2, it.size) else null } ?: return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i + 3 < raw.size) {
            val sfi = (raw[i].toInt() and 0xFF) shr 3
            val first = raw[i + 1].toInt() and 0xFF
            val last = raw[i + 2].toInt() and 0xFF
            for (r in first..last) out += sfi to r
            i += 4
        }
        return out
    }

    /**
     * Numero, scadenza ("MM/AA") e intestatario dalle risposte raccolte. Il numero viene dal tag 5A o dalla traccia 2
     * (tag 57); la scadenza da 5F24 o dalla traccia 2. Null se il numero non c'è o non supera il controllo di Luhn.
     */
    fun card(responses: List<ByteArray>): Card? {
        val all = responses.flatMap(::parse)
        var number = find(all, 0x5A)?.let(::hex)?.trimEnd('F', 'f')
        var expiry = find(all, 0x5F24)?.let(::hex)?.takeIf { it.length >= 4 }?.let { "${it.substring(2, 4)}/${it.substring(0, 2)}" }
        find(all, 0x57)?.let(::hex)?.let { track ->
            val parts = track.split('D', 'd')
            if (parts.size >= 2) {
                if (number == null) number = parts[0]
                if (expiry == null && parts[1].length >= 4) expiry = "${parts[1].substring(2, 4)}/${parts[1].substring(0, 2)}"
            }
        }
        val holder = find(all, 0x5F20)?.toString(Charsets.ISO_8859_1)?.trim()?.trimEnd('/')?.replace('/', ' ')?.trim()
            ?.takeIf { it.isNotBlank() && it.any(Char::isLetter) }.orEmpty()
        val n = number?.takeIf { Cards.valid(it) } ?: return null
        return Card(n.chunked(4).joinToString(" "), expiry.orEmpty(), holder)
    }

    /** Vero se la risposta finisce con 90 00 (tutto bene). */
    fun ok(response: ByteArray): Boolean = response.size >= 2 && response[response.size - 2] == 0x90.toByte() && response.last() == 0x00.toByte()

    fun hex(b: ByteArray): String = b.joinToString("") { "%02X".format(it) }

    private fun bcd(digits: String): ByteArray = ByteArray(digits.length / 2) { i -> ((digits[2 * i] - '0') shl 4 or (digits[2 * i + 1] - '0')).toByte() }
}

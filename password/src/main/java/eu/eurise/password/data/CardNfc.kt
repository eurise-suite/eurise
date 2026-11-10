package eu.eurise.password.data

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import eu.eurise.password.logic.Emv
import java.security.SecureRandom
import java.time.LocalDate

/**
 * Legge numero e scadenza di una carta contactless avvicinata al telefono (lettura EMV, come un POS che non incassa).
 * Resta tutto sul telefono; il lettore NFC si accende solo mentre il foglio "Avvicina la carta" è aperto.
 */
object CardNfc {
    fun available(activity: Activity): Boolean = NfcAdapter.getDefaultAdapter(activity) != null
    fun enabled(activity: Activity): Boolean = NfcAdapter.getDefaultAdapter(activity)?.isEnabled == true

    /** Accende la lettura; [onCard] arriva da un altro thread con la carta o null se non si è riusciti a leggerla. */
    fun start(activity: Activity, onCard: (Emv.Card?) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        adapter.enableReaderMode(activity, { tag -> onCard(runCatching { read(tag) }.getOrNull()) }, flags, null)
    }

    fun stop(activity: Activity) {
        runCatching { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
    }

    private fun read(tag: Tag): Emv.Card? {
        val iso = IsoDep.get(tag) ?: return null
        iso.use { dep ->
            dep.connect()
            dep.timeout = 3000
            fun send(apdu: ByteArray): ByteArray? = dep.transceive(apdu).takeIf(Emv::ok)?.let { it.copyOf(it.size - 2) }
            val ppse = send(Emv.SELECT_PPSE) ?: return null
            for (aid in Emv.aids(ppse)) {
                val fci = send(Emv.select(aid)) ?: continue
                val random = ByteArray(4).also { SecureRandom().nextBytes(it) }
                val gpo = send(Emv.gpo(Emv.pdolData(Emv.pdol(fci), LocalDate.now(), random))) ?: continue
                val responses = mutableListOf(gpo)
                Emv.card(responses)?.let { return it } // alcune carte mettono già la traccia 2 nella risposta
                for ((sfi, record) in Emv.afl(gpo)) {
                    send(Emv.readRecord(sfi, record))?.let { responses += it }
                    Emv.card(responses)?.takeIf { it.expiry.isNotEmpty() }?.let { return it }
                }
                Emv.card(responses)?.let { return it }
            }
            return null
        }
    }
}

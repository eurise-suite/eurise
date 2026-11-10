package eu.eurise.password.data

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.os.Bundle
import eu.eurise.password.logic.Emv
import java.security.SecureRandom
import java.time.LocalDate

/**
 * Legge numero e scadenza di una carta contactless avvicinata al telefono (lettura EMV, come un POS che non incassa).
 * Resta tutto sul telefono; il lettore NFC si accende solo mentre il foglio "Avvicina la carta" è aperto.
 */
object CardNfc {
    /** Com'è andata: la carta letta, oppure il passo a cui ci si è fermati (senza dati della carta). */
    sealed interface Outcome {
        data class Read(val card: Emv.Card) : Outcome
        data class Failed(val step: String) : Outcome
    }

    fun available(activity: Activity): Boolean = NfcAdapter.getDefaultAdapter(activity) != null
    fun enabled(activity: Activity): Boolean = NfcAdapter.getDefaultAdapter(activity)?.isEnabled == true

    /** Accende la lettura; [onResult] arriva da un altro thread. */
    fun start(activity: Activity, onResult: (Outcome) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        // Il controllo di presenza di Android, ogni 125 ms di base, interrompe a metà il dialogo con molte carte
        // di pagamento: lo si dirada mentre si legge.
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000) }
        adapter.enableReaderMode(activity, { tag ->
            val outcome = try {
                read(tag)
            } catch (_: TagLostException) {
                Outcome.Failed("connessione persa")
            } catch (e: Exception) {
                Outcome.Failed(e.javaClass.simpleName)
            }
            onResult(outcome)
        }, flags, extras)
    }

    fun stop(activity: Activity) {
        runCatching { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
    }

    private fun read(tag: Tag): Outcome {
        val iso = IsoDep.get(tag) ?: return Outcome.Failed("non è una carta di pagamento")
        iso.use { dep ->
            dep.timeout = 5000
            dep.connect()
            dep.timeout = 5000
            var last = "?"
            /** Manda un comando e segue le risposte "a metà" (61xx, 6Cxx); restituisce i dati senza 90 00. */
            fun send(apdu: ByteArray): ByteArray? {
                var command = apdu
                repeat(4) {
                    val response = dep.transceive(command)
                    last = Emv.status(response)
                    if (Emv.ok(response)) return response.copyOf(response.size - 2)
                    command = Emv.followUp(command, response) ?: return null
                }
                return null
            }
            val listed = send(Emv.SELECT_PPSE)?.let(Emv::aids).orEmpty()
            val ppseStatus = last
            val tried = mutableSetOf<String>()
            var step = if (listed.isEmpty()) "elenco $ppseStatus" else "nessuna applicazione"
            for (aid in listed + Emv.KNOWN_AIDS) {
                if (!tried.add(Emv.hex(aid))) continue
                val fci = send(Emv.select(aid)) ?: continue
                val random = ByteArray(4).also { SecureRandom().nextBytes(it) }
                val gpo = send(Emv.gpo(Emv.pdolData(Emv.pdol(fci), LocalDate.now(), random)))
                if (gpo == null) { step = "avvio $last"; continue }
                val responses = mutableListOf(gpo)
                Emv.card(responses)?.takeIf { it.expiry.isNotEmpty() }?.let { return Outcome.Read(it) }
                for ((sfi, record) in Emv.afl(gpo)) {
                    send(Emv.readRecord(sfi, record))?.let { responses += it }
                    Emv.card(responses)?.takeIf { it.expiry.isNotEmpty() }?.let { return Outcome.Read(it) }
                }
                Emv.card(responses)?.let { return Outcome.Read(it) }
                step = "numero non trovato"
            }
            return Outcome.Failed(step)
        }
    }
}

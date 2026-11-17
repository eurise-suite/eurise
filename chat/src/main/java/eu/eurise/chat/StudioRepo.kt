package eu.eurise.chat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import eu.eurise.core.studio.Channel
import eu.eurise.core.studio.Patient
import eu.eurise.core.studio.PendingCode
import eu.eurise.core.studio.Queue
import eu.eurise.core.studio.StudioCodec
import eu.eurise.core.studio.StudioState
import eu.eurise.core.studio.Verification
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lo Studio sul telefono: un solo archivio per l'app (interfaccia, sincronizzazione, fornitore per Email e Messaggi),
 * cifrato con una chiave AES del Keystore che non esce dal telefono. Sono dati sanitari: niente backup di Android
 * (allowBackup è spento), nessun servizio esterno. Si legge alla prima richiesta, mai all'avvio dell'app.
 */
object StudioRepo {
    private const val KEY = "eurise-studio"
    private val lock = Any()
    private var file: File? = null
    private val mutable = MutableStateFlow(StudioState())
    private val flow get() = mutable

    /** Lo stato da osservare; vale qualcosa dopo [load] (da chiamare fuori dal thread principale). */
    val state: StateFlow<StudioState> get() = mutable

    fun current(context: Context): StudioState { load(context); return flow.value }

    fun load(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, "studio.bin")
            file = f
            if (f.exists()) runCatching { flow.value = StudioCodec.decode(decrypt(f.readBytes())) }
        }
    }

    /** Cambia lo Studio e lo salva (scrittura atomica: prima un file nuovo, poi lo scambio). */
    fun update(context: Context, change: (StudioState) -> StudioState): StudioState = synchronized(lock) {
        load(context)
        val next = change(flow.value)
        if (next != flow.value) {
            flow.value = next
            val f = file!!
            val tmp = File(f.parentFile, f.name + ".new")
            runCatching { tmp.writeBytes(encrypt(StudioCodec.encode(next))); tmp.renameTo(f) }
        }
        next
    }

    /** Un messaggio arrivato: verifica, coda o niente (vedi [Queue.receive]). */
    fun receive(context: Context, channel: Channel, address: String, text: String, time: Long, ref: String): Queue.Outcome {
        var outcome: Queue.Outcome = Queue.Outcome.Ignored
        update(context) { s ->
            if (!s.enabled) return@update s
            val (next, out) = Queue.receive(s, channel, address, text, time, ref, System.currentTimeMillis()) { UUID.randomUUID().toString() }
            outcome = out
            next
        }
        return outcome
    }

    fun setEnabled(context: Context, on: Boolean) = update(context) {
        if (on && !it.enabled) it.copy(enabled = true, enabledAt = System.currentTimeMillis()) else it.copy(enabled = on)
    }

    fun savePatient(context: Context, p: Patient) = update(context) { s ->
        if (s.patients.any { it.id == p.id }) s.copy(patients = s.patients.map { if (it.id == p.id) p else it })
        else s.copy(patients = s.patients + p)
    }

    /** Toglie il paziente con le sue richieste e i suoi codici. */
    fun removePatient(context: Context, id: String) = update(context) { s ->
        s.copy(patients = s.patients.filter { it.id != id }, requests = s.requests.filter { it.patientId != id }, codes = s.codes.filter { it.patientId != id })
    }

    /** Un codice nuovo per [patientId] (il vecchio non vale più). */
    fun newCode(context: Context, patientId: String): String {
        val code = Verification.newCode()
        val now = System.currentTimeMillis()
        update(context) { s -> s.copy(codes = Verification.alive(s.codes, now).filter { it.patientId != patientId } + PendingCode(patientId, code, now)) }
        return code
    }

    fun setDone(context: Context, requestId: String, done: Boolean) = update(context) { s ->
        s.copy(requests = s.requests.map { if (it.id == requestId) it.copy(doneAt = if (done) System.currentTimeMillis() else null) else it })
    }

    // --- Cifratura con il Keystore ---

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(text: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val body = c.doFinal(text.toByteArray())
        return byteArrayOf(c.iv.size.toByte()) + c.iv + body
    }

    private fun decrypt(data: ByteArray): String {
        val n = data[0].toInt()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 1, n))
        return String(c.doFinal(data, 1 + n, data.size - 1 - n))
    }
}

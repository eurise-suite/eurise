package eu.eurise.chat

import android.content.Context
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.crypto.DeviceLists
import org.matrix.rustcomponents.sdk.crypto.EncryptionSettings
import org.matrix.rustcomponents.sdk.crypto.EventEncryptionAlgorithm
import org.matrix.rustcomponents.sdk.crypto.HistoryVisibility
import org.matrix.rustcomponents.sdk.crypto.OlmMachine
import org.matrix.rustcomponents.sdk.crypto.OutgoingVerificationRequest
import org.matrix.rustcomponents.sdk.crypto.Request
import org.matrix.rustcomponents.sdk.crypto.RequestType
import uniffi.matrix_sdk_crypto.DecryptionSettings
import uniffi.matrix_sdk_crypto.TrustRequirement

/**
 * Cifratura end-to-end della Chat con il modulo crittografico ufficiale di Matrix (matrix-sdk-crypto, vodozemac; lo
 * stesso di Element). Il modulo non fa rete: riceve le risposte del server e dice cosa mandare; la rete resta nostra
 * ([ChatClient]). Le chiavi stanno sul telefono, in un archivio cifrato con una frase casuale custodita nel Keystore.
 * Tutte le chiamate sono bloccanti: fuori dal thread principale, una alla volta ([lock]).
 */
class ChatCrypto(context: Context, private val session: Session) {
    private val lock = Mutex()
    private val machine: OlmMachine

    init {
        val dir = File(context.filesDir, "crypto/" + session.userId.hashCode() + "_" + session.deviceId).apply { mkdirs() }
        machine = OlmMachine(session.userId, session.deviceId, dir.path, SessionStore(context).cryptoPassphrase())
    }

    /** Dalla risposta di /sync: messaggi tra dispositivi (chiavi delle stanze), dispositivi cambiati, chiavi rimaste. */
    suspend fun receiveSync(sync: JSONObject) = lock.withLock {
        val toDevice = sync.optJSONObject("to_device")?.optJSONArray("events") ?: JSONArray()
        val lists = sync.optJSONObject("device_lists")
        val counts = HashMap<String, Int>()
        sync.optJSONObject("device_one_time_keys_count")?.let { c -> c.keys().forEach { k -> counts[k] = c.optInt(k) } }
        val fallback = sync.optJSONArray("device_unused_fallback_key_types")?.let { a -> (0 until a.length()).map { a.getString(it) } }
        machine.receiveSyncChanges(
            events = JSONObject().put("events", toDevice).toString(),
            deviceChanges = DeviceLists(strings(lists?.optJSONArray("changed")), strings(lists?.optJSONArray("left"))),
            keyCounts = counts,
            unusedFallbackKeys = fallback,
            nextBatchToken = sync.optString("next_batch"),
            decryptionSettings = DecryptionSettings(TrustRequirement.UNTRUSTED),
        )
    }

    /** Manda al server ciò che il modulo chiede (chiavi del telefono, domande sulle chiavi degli altri, chiavi delle stanze). */
    suspend fun flush(client: ChatClient) {
        val requests = lock.withLock { machine.outgoingRequests() }
        requests.forEach { send(client, it) }
        // La copia cifrata delle chiavi sul server (se c'è la chiave di recupero): un pacchetto per tornata.
        lock.withLock { runCatching { if (machine.backupEnabled()) machine.backupRoomKeys() else null }.getOrNull() }?.let { send(client, it) }
    }

    /** Lavoro diretto sul modulo crittografico (chiave di recupero, verifica), uno alla volta. */
    suspend fun <T> withMachine(block: (OlmMachine) -> T): T = lock.withLock { block(machine) }

    /** Un messaggio della verifica (tra dispositivi o nella stanza) al server, poi segnato come mandato. */
    suspend fun sendVerification(client: ChatClient, r: OutgoingVerificationRequest) {
        when (r) {
            is OutgoingVerificationRequest.ToDevice -> {
                client.put("/_matrix/client/v3/sendToDevice/${ChatClient.enc(r.eventType)}/${ChatClient.enc(r.requestId)}",
                    JSONObject().put("messages", JSONObject(r.body)))
                runCatching { mark(r.requestId, RequestType.TO_DEVICE, JSONObject()) }
            }
            is OutgoingVerificationRequest.InRoom -> {
                val id = sendInRoom(client, r.roomId, r.eventType, JSONObject(r.content), r.requestId)
                runCatching { mark(r.requestId, RequestType.ROOM_MESSAGE, JSONObject().put("event_id", id)) }
            }
        }
    }

    /** Stanze già controllate: cifrate o no (m.room.encryption). */
    private val roomEncrypted = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Un evento nella stanza, cifrato se la stanza è cifrata (come i messaggi): serve alla verifica di una persona, che
     * passa dalla chat. «m.relates_to» resta in chiaro, come chiede la specifica. Restituisce l'id dell'evento.
     */
    suspend fun sendInRoom(client: ChatClient, roomId: String, type: String, content: JSONObject, txn: String): String {
        val encrypted = roomEncrypted.getOrPut(roomId) {
            runCatching { client.get("/_matrix/client/v3/rooms/${ChatClient.enc(roomId)}/state/m.room.encryption/"); true }.getOrDefault(false)
        }
        val path = "/_matrix/client/v3/rooms/${ChatClient.enc(roomId)}/send/"
        val sent = if (encrypted) {
            val out = encrypt(client, roomId, client.joinedMembers(roomId), type, content)
            content.optJSONObject("m.relates_to")?.let { if (!out.has("m.relates_to")) out.put("m.relates_to", it) }
            client.put(path + "m.room.encrypted/" + ChatClient.enc(txn), out)
        } else client.put(path + ChatClient.enc(type) + "/" + ChatClient.enc(txn), content)
        return sent.getString("event_id")
    }

    /**
     * Un evento della verifica arrivato nella chat (già decifrato): richiesta, pronto, figure, conferme. Per una
     * richiesta il modulo deve conoscere i dispositivi di chi la manda: prima si chiedono al server.
     */
    suspend fun receiveVerification(client: ChatClient, event: JSONObject, roomId: String) {
        if (isVerificationRequest(event)) {
            lock.withLock { machine.updateTrackedUsers(listOf(event.optString("sender"))) }
            flush(client)
        }
        lock.withLock { runCatching { machine.receiveVerificationEvent(event.toString(), roomId) } }
    }

    /** Firme (dispositivo verificato, chiavi di firma) al server. */
    fun uploadSignatures(client: ChatClient, body: String) {
        client.post("/_matrix/client/v3/keys/signatures/upload", JSONObject(body))
    }

    suspend fun send(client: ChatClient, r: Request) {
        try {
            when (r) {
                is Request.KeysUpload -> mark(r.requestId, RequestType.KEYS_UPLOAD, client.post("/_matrix/client/v3/keys/upload", JSONObject(r.body)))
                is Request.KeysQuery -> {
                    val devices = JSONObject()
                    r.users.forEach { devices.put(it, JSONArray()) }
                    mark(r.requestId, RequestType.KEYS_QUERY, client.post("/_matrix/client/v3/keys/query", JSONObject().put("device_keys", devices).put("timeout", 10_000)))
                }
                is Request.KeysClaim -> {
                    val keys = JSONObject()
                    r.oneTimeKeys.forEach { (user, devs) -> keys.put(user, JSONObject(devs)) }
                    mark(r.requestId, RequestType.KEYS_CLAIM, client.post("/_matrix/client/v3/keys/claim", JSONObject().put("one_time_keys", keys).put("timeout", 10_000)))
                }
                is Request.ToDevice -> {
                    client.put("/_matrix/client/v3/sendToDevice/${ChatClient.enc(r.eventType)}/${ChatClient.enc(r.requestId)}",
                        JSONObject().put("messages", JSONObject(r.body)))
                    mark(r.requestId, RequestType.TO_DEVICE, JSONObject())
                }
                is Request.SignatureUpload -> mark(r.requestId, RequestType.SIGNATURE_UPLOAD, client.post("/_matrix/client/v3/keys/signatures/upload", JSONObject(r.body)))
                is Request.RoomMessage -> {
                    val sent = client.put("/_matrix/client/v3/rooms/${ChatClient.enc(r.roomId)}/send/${ChatClient.enc(r.eventType)}/${ChatClient.enc(r.requestId)}", JSONObject(r.content))
                    mark(r.requestId, RequestType.ROOM_MESSAGE, sent)
                }
                is Request.KeysBackup -> mark(r.requestId, RequestType.KEYS_BACKUP,
                    client.put("/_matrix/client/v3/room_keys/keys?version=${ChatClient.enc(r.version)}", JSONObject().put("rooms", JSONObject(r.rooms))))
                else -> Unit
            }
        } catch (e: Exception) {
            // Una richiesta non riuscita torna nella prossima tornata di outgoingRequests.
        }
    }

    private suspend fun mark(id: String, type: RequestType, response: JSONObject) = lock.withLock {
        machine.markRequestAsSent(id, type, response.toString())
    }

    /** Il messaggio in chiaro, o null se manca ancora la chiave (si riprova quando arriva). */
    suspend fun decrypt(event: JSONObject, roomId: String): JSONObject? = lock.withLock {
        try {
            val clear = machine.decryptRoomEvent(
                event.toString(), roomId,
                handleVerificationEvents = false,
                strictShields = false,
                decryptionSettings = DecryptionSettings(TrustRequirement.UNTRUSTED),
            )
            val inner = JSONObject(clear.clearEvent)
            // L'evento in chiaro prende tipo e contenuto; il resto (id, mittente, ora) resta quello del server.
            // «m.relates_to» sta fuori dalla parte cifrata (risposte, modifiche, passi della verifica): si rimette dentro.
            val content = inner.optJSONObject("content") ?: JSONObject()
            event.optJSONObject("content")?.optJSONObject("m.relates_to")?.let { if (!content.has("m.relates_to")) content.put("m.relates_to", it) }
            JSONObject(event.toString()).put("type", inner.optString("type")).put("content", content)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cifra un messaggio per una stanza: sessioni con i dispositivi dei membri che mancano, chiave della stanza condivisa
     * con loro, poi il messaggio. Restituisce il contenuto cifrato da mandare come «m.room.encrypted».
     */
    suspend fun encrypt(client: ChatClient, roomId: String, members: List<String>, type: String, content: JSONObject): JSONObject {
        lock.withLock { machine.updateTrackedUsers(members) }
        flush(client)
        lock.withLock { machine.getMissingSessions(members) }?.let { send(client, it) }
        val settings = EncryptionSettings(
            algorithm = EventEncryptionAlgorithm.MEGOLM_V1_AES_SHA2,
            onlyAllowTrustedDevices = false,
            rotationPeriod = 604_800_000UL,
            rotationPeriodMsgs = 100UL,
            historyVisibility = HistoryVisibility.SHARED,
            errorOnVerifiedUserProblem = false,
        )
        lock.withLock { machine.shareRoomKey(roomId, members, settings) }.forEach { send(client, it) }
        return JSONObject(lock.withLock { machine.encrypt(roomId, type, content.toString()) })
    }

    private fun strings(a: JSONArray?): List<String> = a?.let { (0 until it.length()).map { i -> it.getString(i) } }.orEmpty()

    companion object {
        /** Una frase casuale per l'archivio delle chiavi (custodita cifrata dal Keystore, vedi [SessionStore]). */
        fun newPassphrase(): String {
            val b = ByteArray(32)
            SecureRandom().nextBytes(b)
            return b.joinToString("") { "%02x".format(it) }
        }

        fun txn() = UUID.randomUUID().toString()

        /** I passi della verifica che passano dalla chat (dopo la richiesta, che è un messaggio). */
        private val VERIFICATION_STEPS = setOf("ready", "start", "accept", "key", "mac", "done", "cancel").map { "m.key.verification.$it" }.toSet()

        fun isVerificationRequest(e: JSONObject) =
            e.optString("type") == "m.room.message" && e.optJSONObject("content")?.optString("msgtype") == "m.key.verification.request"

        /** Un evento della cronologia che riguarda una verifica in chat. */
        fun isVerificationEvent(e: JSONObject) = e.optString("type") in VERIFICATION_STEPS || isVerificationRequest(e)
    }
}

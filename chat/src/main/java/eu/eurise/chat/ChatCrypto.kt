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
    }

    private suspend fun send(client: ChatClient, r: Request) {
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
                is Request.KeysBackup -> Unit // Il backup delle chiavi arriva con la chiave di recupero (backup cifrato della suite).
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
            JSONObject(event.toString()).put("type", inner.optString("type")).put("content", inner.optJSONObject("content") ?: JSONObject())
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
    }
}

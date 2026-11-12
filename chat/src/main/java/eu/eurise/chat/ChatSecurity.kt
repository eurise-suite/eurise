package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.crypto.BackupRecoveryKey
import org.matrix.rustcomponents.sdk.crypto.CrossSigningKeyExport
import org.matrix.rustcomponents.sdk.crypto.ProgressListener
import org.matrix.rustcomponents.sdk.crypto.SasState

/** Lo stato della chiave di recupero e della copia delle chiavi (tavola ChatRecupero). */
data class SecurityStatus(
    /** La copia cifrata delle chiavi sul server è attiva da questo telefono. */
    val backup: Boolean,
    /** Questo telefono ha le chiavi di firma (può verificare gli altri dispositivi). */
    val signing: Boolean,
    /** L'account ha già un archivio dei segreti sul server (creato qui o con Element). */
    val serverStorage: Boolean,
    val keys: Long,
    val backedUp: Long,
)

/** Il server vuole sapere che sei tu: con la password o con l'accesso dal sito ([sso]). */
class AuthNeeded(val session: String, val password: Boolean, val sso: Boolean) : Exception("auth")

/** L'account ha già una chiave (fatta con Element o un altro telefono): si inserisce quella. */
class AlreadySetUp : Exception("exists")

/** La chiave inserita non apre l'archivio. */
class WrongKey : Exception("wrong key")

/** Una verifica di dispositivo in corso (tavola ChatVerifica). */
sealed interface Verify {
    val flowId: String
    val device: String?
    /** La persona da verificare (verifica nella chat); null per un dispositivo dell'account. */
    val user: String?
    data class Waiting(override val flowId: String, override val device: String?, override val user: String? = null) : Verify
    data class Incoming(override val flowId: String, override val device: String?, override val user: String? = null) : Verify
    data class Compare(override val flowId: String, override val device: String?, val emojis: List<Int>, override val user: String? = null) : Verify
    data class Confirmed(override val flowId: String, override val device: String?, override val user: String? = null) : Verify
    data class Done(override val flowId: String, override val device: String?, override val user: String? = null) : Verify
    data class Cancelled(override val flowId: String, override val device: String?, override val user: String? = null) : Verify
}

/** La persona non ha ancora le chiavi di firma (nessuna chiave di recupero): non si può verificare. */
class NoIdentity : Exception("no identity")

/** Un dispositivo dell'account: nome, ultimo accesso, se è verificato. */
data class ChatDevice(val id: String, val name: String, val lastSeen: Long, val verified: Boolean, val current: Boolean)

/**
 * Chiave di recupero, copia delle chiavi sul server e verifica dei dispositivi, con il modulo crittografico di Matrix e
 * l'archivio dei segreti ([SecretStorage]). Compatibile con Element: la stessa chiave apre l'account dall'una e
 * dall'altra app. Tutte le chiamate fanno rete: fuori dal thread principale.
 */
class ChatSecurity(private val client: ChatClient, private val crypto: ChatCrypto) {
    private val me get() = client.session.userId

    suspend fun status(): SecurityStatus {
        val counts = runCatching { crypto.withMachine { it.roomKeyCounts() } }.getOrNull()
        val cs = runCatching { crypto.withMachine { it.crossSigningStatus() } }.getOrNull()
        return SecurityStatus(
            backup = runCatching { crypto.withMachine { it.backupEnabled() } }.getOrDefault(false),
            signing = cs?.hasSelfSigning == true,
            serverStorage = runCatching { client.accountData(SecretStorage.DEFAULT_KEY) != null }.getOrDefault(false),
            keys = counts?.total ?: 0L,
            backedUp = counts?.backedUp ?: 0L,
        )
    }

    /**
     * La prima volta: chiavi di firma, copia cifrata delle chiavi sul server, archivio dei segreti con una chiave nuova.
     * Restituisce la chiave di recupero da mostrare. [auth]: la conferma d'identità, se il server l'ha chiesta.
     */
    suspend fun setup(auth: JSONObject? = null): String {
        if (client.accountData(SecretStorage.DEFAULT_KEY) != null) throw AlreadySetUp()
        // Chiavi di firma: se ci sono già sul server (fatte altrove) serve la chiave che le apre, non una nuova.
        val master = client.keysQuery(me).optJSONObject("master_keys")?.optJSONObject(me)
        if (master == null || !crypto.withMachine { it.crossSigningStatus().hasMaster }) {
            if (master != null) throw AlreadySetUp()
            val boot = crypto.withMachine { it.bootstrapCrossSigning() }
            boot.uploadKeysRequest?.let { crypto.send(client, it) }
            val keys = boot.uploadSigningKeysRequest
            val body = JSONObject().put("master_key", JSONObject(keys.masterKey))
                .put("self_signing_key", JSONObject(keys.selfSigningKey)).put("user_signing_key", JSONObject(keys.userSigningKey))
            uia { client.uploadSigningKeys(JSONObject(body.toString()), auth) }
            crypto.uploadSignatures(client, boot.uploadSignatureRequest.body)
        }

        // La copia delle chiavi delle conversazioni sul server, cifrata con una chiave che sta solo nell'archivio.
        val backupKey = BackupRecoveryKey()
        val pub = backupKey.megolmV1PublicKey()
        val authData = JSONObject().put("public_key", pub.publicKey)
        val signatures = crypto.withMachine { it.sign(authData.toString()) }
        val sig = JSONObject()
        signatures.forEach { (user, keys) -> sig.put(user, JSONObject(keys as Map<*, *>)) }
        authData.put("signatures", sig)
        val version = client.post("/_matrix/client/v3/room_keys/version",
            JSONObject().put("algorithm", pub.backupAlgorithm).put("auth_data", authData)).getString("version")
        crypto.withMachine {
            it.enableBackupV1(pub, version)
            it.saveRecoveryKey(backupKey, version)
        }

        // L'archivio dei segreti: chiavi di firma e chiave della copia, cifrate con la chiave di recupero.
        val key = SecretStorage.newKey()
        val keyId = SecretStorage.newKeyId()
        client.putAccountData("m.secret_storage.key.$keyId", SecretStorage.keyDescription(key, "Eurise"))
        client.putAccountData(SecretStorage.DEFAULT_KEY, JSONObject().put("key", keyId))
        val export = crypto.withMachine { it.exportCrossSigningKeys() }
        listOf(
            SecretStorage.MASTER to export?.masterKey,
            SecretStorage.SELF_SIGNING to export?.selfSigningKey,
            SecretStorage.USER_SIGNING to export?.userSigningKey,
            SecretStorage.BACKUP to backupKey.toBase64(),
        ).forEach { (name, value) -> if (value != null) storeSecret(key, keyId, name, value) }
        crypto.flush(client)
        return SecretStorage.encodeRecoveryKey(key)
    }

    private fun storeSecret(key: ByteArray, keyId: String, name: String, value: String) {
        client.putAccountData(name, JSONObject().put("encrypted", JSONObject().put(keyId, SecretStorage.encrypt(key, name, value))))
    }

    private fun readSecret(key: ByteArray, keyId: String, name: String): String? {
        val data = client.accountData(name)?.optJSONObject("encrypted")?.optJSONObject(keyId) ?: return null
        return SecretStorage.decrypt(key, name, data)
    }

    /**
     * Con una chiave che c'è già (Element, telefono vecchio, backup della suite): chiavi di firma importate, questo
     * telefono verificato, copia delle chiavi riattivata e scaricata (i messaggi vecchi si riaprono). Restituisce la
     * chiave scritta bene (a gruppi), da custodire.
     */
    suspend fun restore(text: String): String {
        val key = SecretStorage.decodeRecoveryKey(text) ?: throw WrongKey()
        val keyId = client.accountData(SecretStorage.DEFAULT_KEY)?.optString("key")?.takeIf { it.isNotBlank() } ?: throw WrongKey()
        val description = client.accountData("m.secret_storage.key.$keyId") ?: throw WrongKey()
        if (!SecretStorage.checkKey(key, description)) throw WrongKey()

        val master = readSecret(key, keyId, SecretStorage.MASTER)
        val self = readSecret(key, keyId, SecretStorage.SELF_SIGNING)
        val user = readSecret(key, keyId, SecretStorage.USER_SIGNING)
        if (master != null || self != null || user != null) {
            crypto.withMachine { it.updateTrackedUsers(listOf(me)) }
            crypto.flush(client)
            runCatching { crypto.withMachine { it.importCrossSigningKeys(CrossSigningKeyExport(master, self, user)) } }
            // Questo telefono firmato con la chiave dell'account: gli altri dispositivi lo vedono verificato.
            runCatching {
                val sig = crypto.withMachine { it.verifyDevice(me, client.session.deviceId) }
                crypto.uploadSignatures(client, sig.body)
            }
        }

        readSecret(key, keyId, SecretStorage.BACKUP)?.let { secret -> runCatching { restoreBackup(secret) } }
        crypto.flush(client)
        return SecretStorage.encodeRecoveryKey(key)
    }

    private suspend fun restoreBackup(secret: String) {
        val backupKey = BackupRecoveryKey.fromBase64(secret)
        val info = client.get("/_matrix/client/v3/room_keys/version")
        val version = info.optString("version").ifBlank { return }
        val pub = backupKey.megolmV1PublicKey()
        if (info.optJSONObject("auth_data")?.optString("public_key") != pub.publicKey) return
        crypto.withMachine {
            it.enableBackupV1(pub, version)
            it.saveRecoveryKey(backupKey, version)
        }
        val rooms = client.get("/_matrix/client/v3/room_keys/keys?version=${ChatClient.enc(version)}").optJSONObject("rooms") ?: return
        val list = JSONArray()
        rooms.keys().forEach { roomId ->
            val sessions = rooms.optJSONObject(roomId)?.optJSONObject("sessions") ?: return@forEach
            sessions.keys().forEach { sessionId ->
                val data = sessions.optJSONObject(sessionId)?.optJSONObject("session_data") ?: return@forEach
                runCatching {
                    val clear = backupKey.decryptV1(data.optString("ephemeral"), data.optString("mac"), data.optString("ciphertext"))
                    list.put(JSONObject(clear).put("room_id", roomId).put("session_id", sessionId))
                }
            }
        }
        if (list.length() == 0) return
        crypto.withMachine {
            it.importRoomKeysFromBackup(list.toString(), version, object : ProgressListener {
                override fun onProgress(progress: Int, total: Int) = Unit
            })
        }
    }

    /** Il server chiede la conferma d'identità: diventa [AuthNeeded] con i modi possibili. */
    private inline fun uia(block: () -> Unit) {
        try {
            block()
        } catch (e: MatrixException) {
            if (e.status != 401 || !e.body.has("flows")) throw e
            val flows = e.body.optJSONArray("flows") ?: JSONArray()
            val stages = (0 until flows.length()).flatMap { i ->
                val st = flows.optJSONObject(i)?.optJSONArray("stages") ?: JSONArray()
                (0 until st.length()).map { st.optString(it) }
            }
            throw AuthNeeded(e.body.optString("session"), "m.login.password" in stages, "m.login.sso" in stages)
        }
    }

    // --- Dispositivi (tavola ChatVerifica) ---

    suspend fun devices(): List<ChatDevice> {
        val list = client.devices()
        crypto.withMachine { it.updateTrackedUsers(listOf(me)) }
        runCatching { crypto.flush(client) }
        return list.map { d ->
            val id = d.optString("device_id")
            val trusted = runCatching { crypto.withMachine { it.getDevice(me, id, 0u)?.crossSigningTrusted } }.getOrNull() == true
            ChatDevice(id, d.optString("display_name").ifBlank { id }, d.optLong("last_seen_ts"), trusted, id == client.session.deviceId)
        }.sortedWith(compareByDescending<ChatDevice> { it.current }.thenByDescending { it.lastSeen })
    }

    /** Fa uscire un dispositivo; la prima volta senza [auth] può tornare [AuthNeeded]. */
    fun logoutDevice(deviceId: String, auth: JSONObject?) = uia { client.deleteDevice(deviceId, auth) }

    fun passwordAuth(session: String, password: String) = JSONObject().put("type", "m.login.password").put("session", session)
        .put("identifier", JSONObject().put("type", "m.id.user").put("user", me)).put("password", password)

    fun sessionAuth(session: String) = JSONObject().put("session", session)

    // --- Verifica con le figure (SAS) ---

    /**
     * Una richiesta di verifica arrivata e non ancora accettata: da un altro dispositivo dell'account o, nella chat, da
     * una delle persone in [peers].
     */
    suspend fun incoming(peers: Collection<String> = emptyList()): Verify.Incoming? = runCatching {
        crypto.withMachine { m ->
            (listOf(me) + peers.filter { it != me }).firstNotNullOfOrNull { u ->
                m.getVerificationRequests(u).firstOrNull { !it.weStarted() && !it.isDone() && !it.isCancelled() && !it.isReady() && !it.isPassive() }
                    ?.let { Verify.Incoming(it.flowId(), it.otherDeviceId(), u.takeIf { it != me }) }
            }
        }
    }.getOrNull()

    /** Chiede la verifica agli altri dispositivi dell'account (Element sul computer, il telefono vecchio). */
    suspend fun requestSelf(): Verify? {
        val r = crypto.withMachine { it.requestSelfVerification(listOf(SAS)) } ?: return null
        crypto.sendVerification(client, r.request)
        return Verify.Waiting(r.verification.flowId(), null)
    }

    /**
     * Verifica di una persona dentro la chat che avete insieme ([roomId]): la richiesta è un messaggio della stanza
     * (cifrato come gli altri), le risposte arrivano con /sync. [NoIdentity] se la persona non ha le chiavi di firma.
     */
    suspend fun requestUser(roomId: String, userId: String): Verify {
        crypto.withMachine { it.updateTrackedUsers(listOf(userId)) }
        crypto.flush(client)
        val content = crypto.withMachine { it.verificationRequestContent(userId, listOf(SAS)) } ?: throw NoIdentity()
        val eventId = crypto.sendInRoom(client, roomId, "m.room.message", JSONObject(content), ChatCrypto.txn())
        val req = crypto.withMachine { it.requestVerification(userId, roomId, eventId, listOf(SAS)) } ?: throw NoIdentity()
        return Verify.Waiting(req.flowId(), null, userId)
    }

    /** La persona è verificata (la sua identità firmata da noi o da te su un altro dispositivo). */
    suspend fun isVerified(userId: String): Boolean = runCatching { crypto.withMachine { it.isIdentityVerified(userId) } }.getOrDefault(false)

    suspend fun accept(flowId: String, user: String? = null) {
        val out = crypto.withMachine { it.getVerificationRequest(user ?: me, flowId)?.accept(listOf(SAS)) }
        out?.let { crypto.sendVerification(client, it) }
    }

    /**
     * Un passo della verifica: la fa avanzare (avvia o accetta le figure quando tocca a noi) e dice a che punto è.
     * Si chiama di continuo mentre la schermata è aperta; le risposte dell'altro arrivano con /sync.
     */
    suspend fun step(flowId: String, user: String? = null): Verify {
        val who = user ?: me
        val req = crypto.withMachine { it.getVerificationRequest(who, flowId) }
        val device = crypto.withMachine { req?.otherDeviceId() }
        if (req?.isCancelled() == true) return Verify.Cancelled(flowId, device, user)
        var sas = crypto.withMachine { it.getVerification(who, flowId)?.asSas() }
        if (sas == null && req?.isReady() == true && req.weStarted()) {
            val started = crypto.withMachine { req.startSasVerification() }
            started?.let { crypto.sendVerification(client, it.request); sas = it.sas }
        }
        val s = sas ?: return if (req?.isDone() == true) Verify.Done(flowId, device, user) else Verify.Waiting(flowId, device, user)
        return when (val st = crypto.withMachine { s.state() }) {
            is SasState.Started -> {
                if (!s.weStarted()) crypto.withMachine { s.accept() }?.let { crypto.sendVerification(client, it) }
                Verify.Waiting(flowId, device, user)
            }
            is SasState.KeysExchanged -> st.emojis?.let { Verify.Compare(flowId, device, it, user) } ?: Verify.Waiting(flowId, device, user)
            is SasState.Confirmed -> Verify.Confirmed(flowId, device, user)
            is SasState.Done -> Verify.Done(flowId, device, user)
            is SasState.Cancelled -> Verify.Cancelled(flowId, device, user)
            else -> Verify.Waiting(flowId, device, user)
        }
    }

    /** «Sono uguali»: conferma e firma l'altro dispositivo (o l'identità della persona). */
    suspend fun confirm(flowId: String, user: String? = null) {
        val sas = crypto.withMachine { it.getVerification(user ?: me, flowId)?.asSas() } ?: return
        val result = crypto.withMachine { sas.confirm() } ?: return
        result.requests.forEach { crypto.sendVerification(client, it) }
        result.signatureRequest?.let { crypto.uploadSignatures(client, it.body) }
    }

    /** «Non sono uguali» (o annulla): l'altro lo vede e non riceve nulla. */
    suspend fun cancel(flowId: String, mismatch: Boolean, user: String? = null) {
        val who = user ?: me
        val out = crypto.withMachine { m ->
            m.getVerification(who, flowId)?.asSas()?.cancel(if (mismatch) "m.mismatched_sas" else "m.user")
                ?: m.getVerificationRequest(who, flowId)?.cancel()
        }
        out?.let { runCatching { crypto.sendVerification(client, it) } }
    }

    companion object {
        const val SAS = "m.sas.v1"

        /** Le 64 figure della verifica di Matrix, nell'ordine della specifica, con i nomi italiani. */
        val EMOJI = listOf(
            "🐶" to "Cane", "🐱" to "Gatto", "🦁" to "Leone", "🐎" to "Cavallo", "🦄" to "Unicorno", "🐷" to "Maiale",
            "🐘" to "Elefante", "🐰" to "Coniglio", "🐼" to "Panda", "🐓" to "Gallo", "🐧" to "Pinguino", "🐢" to "Tartaruga",
            "🐟" to "Pesce", "🐙" to "Polpo", "🦋" to "Farfalla", "🌷" to "Tulipano", "🌳" to "Albero", "🌵" to "Cactus",
            "🍄" to "Fungo", "🌏" to "Globo", "🌙" to "Luna", "☁️" to "Nuvola", "🔥" to "Fuoco", "🍌" to "Banana",
            "🍎" to "Mela", "🍓" to "Fragola", "🌽" to "Mais", "🍕" to "Pizza", "🎂" to "Torta", "❤️" to "Cuore",
            "😀" to "Sorriso", "🤖" to "Robot", "🎩" to "Cappello", "👓" to "Occhiali", "🔧" to "Chiave inglese", "🎅" to "Babbo Natale",
            "👍" to "Pollice in su", "☂️" to "Ombrello", "⌛" to "Clessidra", "⏰" to "Sveglia", "🎁" to "Regalo", "💡" to "Lampadina",
            "📕" to "Libro", "✏️" to "Matita", "📎" to "Graffetta", "✂️" to "Forbici", "🔒" to "Lucchetto", "🔑" to "Chiave",
            "🔨" to "Martello", "☎️" to "Telefono", "🏁" to "Bandiera", "🚂" to "Treno", "🚲" to "Bicicletta", "✈️" to "Aereo",
            "🚀" to "Razzo", "🏆" to "Trofeo", "⚽" to "Pallone", "🎸" to "Chitarra", "🎺" to "Tromba", "🔔" to "Campana",
            "⚓" to "Ancora", "🎧" to "Cuffie", "📁" to "Cartella", "📌" to "Puntina",
        )
    }
}

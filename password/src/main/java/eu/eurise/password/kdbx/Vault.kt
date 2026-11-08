package eu.eurise.password.kdbx

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/** Una voce della cassaforte, come la mostrano le schermate. */
data class VaultEntry(
    val uuid: String,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val otp: String?,
    val created: Long,
    val modified: Long,
    val lastUsed: Long,
    val usageCount: Int,
    val historyCount: Int,
    val card: CardData? = null,
    val apps: List<String> = emptyList(),
)

/** I dati di una carta: stanno in campi aggiuntivi della voce, che KeePassXC mostra come attributi. */
data class CardData(val number: String, val holder: String, val expiry: String, val cvv: String, val type: String)

/** I campi che si modificano dalla scheda. */
data class EntryDraft(
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val otp: String?,
    val card: CardData? = null,
)

/** Esito di un'unione: [localChanged] se è cambiato qualcosa qui, [remoteBehind] se qui c'è qualcosa che là manca. */
data class MergeResult(val localChanged: Boolean, val remoteBehind: Boolean)

/** Le voci dentro il documento di un file KeePass: legge e scrive solo i campi che conosce, il resto non si tocca. */
class Vault(val document: Document) {
    private val root: Element = document.documentElement.child("Root") ?: error("Root mancante")
    private val meta: Element? = document.documentElement.child("Meta")
    private val rootGroup: Element = root.child("Group") ?: error("Gruppo principale mancante")

    /** Le voci attive: quelle nel cestino e le versioni in cronologia non compaiono. */
    fun entries(): List<VaultEntry> {
        val bin = recycleBin()
        val out = ArrayList<VaultEntry>()
        fun walk(group: Element) {
            if (group === bin) return
            group.children("Entry").forEach { out += read(it) }
            group.children("Group").forEach { walk(it) }
        }
        walk(rootGroup)
        return out
    }

    /** Crea una voce ([uuid] nullo) o la modifica, mettendo la versione precedente in cronologia. Restituisce l'uuid. */
    fun save(draft: EntryDraft, uuid: String?, now: Long = System.currentTimeMillis()): String {
        val existing = uuid?.let { findEntry(it) }
        val element = existing ?: document.createElement("Entry").also { e ->
            e.appendChild(textElement("UUID", newUuid()))
            e.appendChild(newTimes(now))
            rootGroup.appendChild(e)
        }
        if (existing != null) pushHistory(existing, snapshot(existing))
        setString(element, "Title", draft.title, false)
        setString(element, "UserName", draft.username, false)
        setString(element, "Password", draft.password, true)
        setString(element, "URL", draft.url, false)
        setString(element, "Notes", draft.notes, false)
        if (draft.otp.isNullOrBlank()) removeString(element, "otp") else setString(element, "otp", draft.otp, true)
        val card = draft.card
        if (card == null) {
            CARD_FIELDS.forEach { removeString(element, it) }
        } else {
            setString(element, F_NUMBER, card.number, true)
            setString(element, F_HOLDER, card.holder, false)
            setString(element, F_EXPIRY, card.expiry, false)
            setString(element, F_CVV, card.cvv, true)
            setString(element, F_TYPE, card.type, false)
        }
        setTime(element, "LastModificationTime", now)
        return element.child("UUID")!!.textContent
    }

    /** Sposta la voce nel cestino della cassaforte, come fa KeePassXC: da lì si può ancora recuperare. */
    fun delete(uuid: String, now: Long = System.currentTimeMillis()) {
        val entry = findEntry(uuid) ?: return
        val bin = recycleBin() ?: createRecycleBin(now)
        entry.parentNode.removeChild(entry)
        bin.appendChild(entry)
        setTime(entry, "LocationChanged", now)
        setTime(entry, "LastModificationTime", now)
    }

    /** Collega la voce a un'app installata: da quel momento le si propone in quell'app. */
    fun linkApp(uuid: String, packageName: String, now: Long = System.currentTimeMillis()) {
        val entry = findEntry(uuid) ?: return
        val used = entry.children("String").mapNotNull { it.child("Key")?.textContent }.filter { it.startsWith(F_APP) }
        val values = entry.children("String").filter { it.child("Key")?.textContent?.startsWith(F_APP) == true }.map { it.child("Value")?.textContent }
        if (packageName in values) return
        setString(entry, if (used.isEmpty()) F_APP else "$F_APP${used.size + 1}", packageName, false)
        setTime(entry, "LastModificationTime", now)
    }

    /** Registra l'uso della voce: serve all'elenco "Usate di recente". */
    fun touch(uuid: String, now: Long = System.currentTimeMillis()) {
        val entry = findEntry(uuid) ?: return
        setTime(entry, "LastAccessTime", now)
        val times = entry.child("Times") ?: return
        val usage = times.child("UsageCount") ?: document.createElement("UsageCount").also { times.appendChild(it) }
        usage.textContent = ((usage.textContent.trim().toIntOrNull() ?: 0) + 1).toString()
    }

    /**
     * Unisce un'altra copia dello stesso file, voce per voce: vince la modifica più recente e l'altra versione va in
     * cronologia, così nessun testo si perde. Le voci eliminate per davvero (DeletedObjects) restano eliminate.
     */
    fun mergeFrom(other: Vault): MergeResult {
        var localChanged = false
        var remoteBehind = false
        val deleted = deletedObjects().toMutableMap()
        other.deletedObjects().forEach { (id, time) ->
            if ((deleted[id] ?: Long.MIN_VALUE) < time) { deleted[id] = time; localChanged = true }
        }
        if (deletedObjects().any { (id, time) -> other.deletedObjects()[id] != time }) remoteBehind = true

        val localEntries = allEntries().associateByTo(HashMap()) { it.child("UUID")?.textContent.orEmpty() }
        val otherEntries = other.allEntries().associateBy { it.child("UUID")?.textContent.orEmpty() }
        val otherGroupOf = other.groupsOfEntries()

        for ((id, remote) in otherEntries) {
            val local = localEntries[id]
            val remoteTime = timeOf(remote, "LastModificationTime")
            if (local == null) {
                if ((deleted[id] ?: Long.MIN_VALUE) >= remoteTime) { remoteBehind = true; continue }
                ensureGroup(otherGroupOf[id], other).appendChild(document.importNode(remote, true))
                localChanged = true
                continue
            }
            val localTime = timeOf(local, "LastModificationTime")
            when {
                remoteTime > localTime -> {
                    val incoming = document.importNode(remote, true) as Element
                    val keep = snapshot(local)
                    val known = historyOf(incoming).children("Entry").mapTo(HashSet()) { timeOf(it, "LastModificationTime") }
                    local.child("History")?.children("Entry")?.forEach { old ->
                        if (timeOf(old, "LastModificationTime") !in known) historyOf(incoming).appendChild(old.cloneNode(true))
                    }
                    if (localTime !in known) pushHistory(incoming, keep)
                    local.parentNode.removeChild(local)
                    ensureGroup(otherGroupOf[id], other).appendChild(incoming)
                    localEntries[id] = incoming
                    localChanged = true
                }
                localTime > remoteTime -> {
                    val known = local.child("History")?.children("Entry")?.map { timeOf(it, "LastModificationTime") }.orEmpty()
                    if (remoteTime !in known) pushHistory(local, document.importNode(other.snapshot(remote), true) as Element)
                    remoteBehind = true
                }
            }
        }
        if (localEntries.keys.any { it !in otherEntries }) remoteBehind = true

        for ((id, time) in deleted) {
            val local = localEntries[id] ?: continue
            if (time >= timeOf(local, "LastModificationTime")) {
                local.parentNode.removeChild(local)
                localChanged = true
            }
        }
        writeDeletedObjects(deleted)
        return MergeResult(localChanged, remoteBehind)
    }

    private fun read(e: Element): VaultEntry {
        val strings = e.children("String").associate { s ->
            s.child("Key")?.textContent.orEmpty() to s.child("Value")?.textContent.orEmpty()
        }
        val otp = strings["otp"]?.takeIf { it.isNotBlank() }
            ?: strings["TimeOtp-Secret-Base32"]?.takeIf { it.isNotBlank() }
            ?: strings["TOTP Seed"]?.takeIf { it.isNotBlank() }
        return VaultEntry(
            uuid = e.child("UUID")?.textContent.orEmpty(),
            title = strings["Title"].orEmpty(),
            username = strings["UserName"].orEmpty(),
            password = strings["Password"].orEmpty(),
            url = strings["URL"].orEmpty(),
            notes = strings["Notes"].orEmpty(),
            otp = otp,
            created = timeOf(e, "CreationTime"),
            modified = timeOf(e, "LastModificationTime"),
            lastUsed = timeOf(e, "LastAccessTime"),
            usageCount = e.child("Times")?.child("UsageCount")?.textContent?.trim()?.toIntOrNull() ?: 0,
            historyCount = e.child("History")?.children("Entry")?.size ?: 0,
            card = strings[F_NUMBER]?.takeIf { it.isNotBlank() }?.let {
                CardData(it, strings[F_HOLDER].orEmpty(), strings[F_EXPIRY].orEmpty(), strings[F_CVV].orEmpty(), strings[F_TYPE].orEmpty())
            },
            apps = strings.filterKeys { it.startsWith(F_APP) }.values.filter { it.isNotBlank() } +
                listOfNotNull(strings["URL"]?.takeIf { it.startsWith(APP_SCHEME) }?.removePrefix(APP_SCHEME)),
        )
    }

    private fun setString(entry: Element, key: String, value: String, protect: Boolean) {
        val existing = entry.children("String").firstOrNull { it.child("Key")?.textContent == key }
        val string = existing ?: document.createElement("String").also {
            it.appendChild(textElement("Key", key))
            it.appendChild(document.createElement("Value"))
            entry.appendChild(it)
        }
        val v = string.child("Value")!!
        v.textContent = value
        if (protect) v.setAttribute("Protected", "True")
    }

    private fun removeString(entry: Element, key: String) {
        entry.children("String").firstOrNull { it.child("Key")?.textContent == key }?.let { entry.removeChild(it) }
    }

    private fun snapshot(entry: Element): Element =
        (entry.cloneNode(true) as Element).also { copy -> copy.child("History")?.let { copy.removeChild(it) } }

    private fun historyOf(entry: Element): Element =
        entry.child("History") ?: document.createElement("History").also { entry.appendChild(it) }

    private fun pushHistory(entry: Element, version: Element) {
        val history = historyOf(entry)
        history.appendChild(version)
        val versions = history.children("Entry").sortedBy { timeOf(it, "LastModificationTime") }
        versions.dropLast(MAX_HISTORY).forEach { history.removeChild(it) }
    }

    private fun findEntry(uuid: String) = allEntries().firstOrNull { it.child("UUID")?.textContent == uuid }

    private fun allEntries(): List<Element> {
        val out = ArrayList<Element>()
        fun walk(group: Element) {
            out += group.children("Entry")
            group.children("Group").forEach { walk(it) }
        }
        walk(rootGroup)
        return out
    }

    private fun allGroups(): List<Element> {
        val out = ArrayList<Element>()
        fun walk(group: Element) { out += group; group.children("Group").forEach { walk(it) } }
        walk(rootGroup)
        return out
    }

    private fun groupsOfEntries(): Map<String, Element> = allEntries().associate {
        it.child("UUID")?.textContent.orEmpty() to (it.parentNode as Element)
    }

    /** Il gruppo qui corrispondente a [remote] dell'altra copia: se manca lo crea, con i suoi genitori. */
    private fun ensureGroup(remote: Element?, other: Vault): Element {
        if (remote == null || remote === other.rootGroup) return rootGroup
        val id = remote.child("UUID")?.textContent
        allGroups().firstOrNull { it.child("UUID")?.textContent == id }?.let { return it }
        val parent = ensureGroup(remote.parentNode as? Element, other)
        val copy = document.importNode(remote, true) as Element
        copy.children("Entry").forEach { copy.removeChild(it) }
        copy.children("Group").forEach { copy.removeChild(it) }
        parent.appendChild(copy)
        if (other.meta?.child("RecycleBinUUID")?.textContent == id && recycleBin() == null) setRecycleBin(copy)
        return copy
    }

    private fun recycleBin(): Element? {
        val id = meta?.child("RecycleBinUUID")?.textContent?.trim() ?: return null
        return allGroups().firstOrNull { it.child("UUID")?.textContent?.trim() == id }
    }

    private fun createRecycleBin(now: Long): Element {
        val group = document.createElement("Group")
        group.appendChild(textElement("UUID", newUuid()))
        group.appendChild(textElement("Name", "Cestino"))
        group.appendChild(newTimes(now))
        group.appendChild(textElement("EnableSearching", "false"))
        rootGroup.appendChild(group)
        setRecycleBin(group)
        return group
    }

    private fun setRecycleBin(group: Element) {
        val m = meta ?: return
        val node = m.child("RecycleBinUUID") ?: document.createElement("RecycleBinUUID").also { m.appendChild(it) }
        node.textContent = group.child("UUID")!!.textContent
        (m.child("RecycleBinEnabled") ?: document.createElement("RecycleBinEnabled").also { m.appendChild(it) }).textContent = "True"
    }

    private fun deletedObjects(): Map<String, Long> =
        root.child("DeletedObjects")?.children("DeletedObject")?.associate {
            it.child("UUID")?.textContent.orEmpty() to parseTime(it.child("DeletionTime")?.textContent)
        }.orEmpty()

    private fun writeDeletedObjects(all: Map<String, Long>) {
        val node = root.child("DeletedObjects") ?: document.createElement("DeletedObjects").also { root.appendChild(it) }
        while (node.firstChild != null) node.removeChild(node.firstChild)
        for ((id, time) in all) {
            val d = document.createElement("DeletedObject")
            d.appendChild(textElement("UUID", id))
            d.appendChild(textElement("DeletionTime", Base64.getEncoder().encodeToString(timeBytes(time))))
            node.appendChild(d)
        }
    }

    private fun newTimes(now: Long): Element {
        val t = document.createElement("Times")
        val stamp = Base64.getEncoder().encodeToString(timeBytes(now))
        for (name in listOf("CreationTime", "LastModificationTime", "LastAccessTime", "ExpiryTime")) t.appendChild(textElement(name, stamp))
        t.appendChild(textElement("Expires", "False"))
        t.appendChild(textElement("UsageCount", "0"))
        t.appendChild(textElement("LocationChanged", stamp))
        return t
    }

    private fun timeOf(entry: Element, name: String): Long = parseTime(entry.child("Times")?.child(name)?.textContent)

    private fun setTime(entry: Element, name: String, value: Long) {
        val times = entry.child("Times") ?: newTimes(value).also { entry.appendChild(it) }
        val node = times.child(name) ?: document.createElement(name).also { times.appendChild(it) }
        node.textContent = Base64.getEncoder().encodeToString(timeBytes(value))
    }

    private fun textElement(name: String, value: String) = document.createElement(name).also { it.textContent = value }

    companion object {
        private const val MAX_HISTORY = 10
        private const val F_NUMBER = "Numero carta"
        private const val F_HOLDER = "Intestatario"
        private const val F_EXPIRY = "Scadenza"
        private const val F_CVV = "CVV"
        private const val F_TYPE = "Tipo carta"
        private val CARD_FIELDS = listOf(F_NUMBER, F_HOLDER, F_EXPIRY, F_CVV, F_TYPE)
        private const val F_APP = "AndroidApp"
        const val APP_SCHEME = "androidapp://"
        /** Secondi tra l'anno 1 e il 1970: KDBX 4 conta le date dall'anno 1. */
        private const val EPOCH_OFFSET = 62135596800L
        private val random = SecureRandom()

        fun newUuid(): String = Base64.getEncoder().encodeToString(ByteArray(16).also { random.nextBytes(it) })

        internal fun timeBytes(millis: Long): ByteArray =
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(millis / 1000 + EPOCH_OFFSET).array()

        /** Le date sono in base64 nei file KDBX 4 e in forma ISO in quelli passati da versioni più vecchie. */
        internal fun parseTime(text: String?): Long {
            val value = text?.trim().orEmpty()
            if (value.isEmpty()) return 0
            if (value.contains('-') && value.contains(':')) return runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0)
            return runCatching {
                (ByteBuffer.wrap(Base64.getDecoder().decode(value)).order(ByteOrder.LITTLE_ENDIAN).getLong() - EPOCH_OFFSET) * 1000
            }.getOrDefault(0)
        }
    }
}

private fun Element.child(name: String): Element? {
    var n = firstChild
    while (n != null) {
        if (n is Element && n.tagName == name) return n
        n = n.nextSibling
    }
    return null
}

private fun Element.children(name: String): List<Element> {
    val out = ArrayList<Element>()
    var n = firstChild
    while (n != null) {
        if (n is Element && n.tagName == name) out += n
        n = n.nextSibling
    }
    return out
}
